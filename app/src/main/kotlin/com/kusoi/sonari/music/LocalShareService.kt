package com.kusoi.sonari.music

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

/**
 * Port of Sonari/Services/LocalShareService.swift. Same Bonjour service type and
 * the same newline-delimited JSON handshake, so an iPhone and an Android on the
 * same Wi-Fi can hand tracks to each other.
 *
 * Wire, sender to receiver:
 *   {"type":"offer","name":"...","items":[{"track":{...},"lyrics":[...],"size":N}],"size":T}\n
 *   <- {"type":"accept"}\n or {"type":"decline"}\n
 *   T raw audio bytes, item order
 */
object LocalShareService {

    const val SERVICE_TYPE = "_sonari-share._tcp"

    data class Peer(val name: String, val host: String, val port: Int)

    data class Item(val track: Track, val lyrics: List<LyricLine>?, val size: Long)

    data class Offer(
        val id: String,
        val peerName: String,
        val items: List<Item>,
        val sizeBytes: Long,
        val socket: Socket
    ) {
        val summary: String
            get() = if (items.size == 1) items.first().track.title else "${items.size} tracks"
    }

    sealed class Transfer {
        data object Idle : Transfer()
        data object Preparing : Transfer()
        data class Sending(val fraction: Double) : Transfer()
        data class Receiving(val fraction: Double) : Transfer()
        data object Finished : Transfer()
        data class Failed(val reason: String) : Transfer()

        val label: String
            get() = when (this) {
                is Idle -> "Ready"
                is Preparing -> "Preparing"
                is Sending -> "Sending ${(fraction * 100).toInt()}%"
                is Receiving -> "Receiving ${(fraction * 100).toInt()}%"
                is Finished -> "Done"
                is Failed -> reason
            }

        val isActive: Boolean
            get() = this !is Idle && this !is Finished

        val isFailed: Boolean
            get() = this is Failed
    }

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _offers = MutableStateFlow<List<Offer>>(emptyList())
    val offers: StateFlow<List<Offer>> = _offers.asStateFlow()

    private val _transfer = MutableStateFlow<Transfer>(Transfer.Idle)
    val transfer: StateFlow<Transfer> = _transfer.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _lastReceived = MutableStateFlow<List<Track>>(emptyList())
    val lastReceived: StateFlow<List<Track>> = _lastReceived.asStateFlow()

    private var nsd: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var serverSocket: ServerSocket? = null

    /** Resolved peers keyed by service name, because NsdManager resolves async. */
    private val resolved = mutableMapOf<String, Peer>()

    val deviceName: String = android.os.Build.MODEL ?: "Android"

    fun start(context: Context) {
        if (nsd == null) nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        if (serverSocket == null) startServer()
        startAdvertising()
        startBrowsing()
    }

    fun stop() {
        registrationListener?.let { runCatching { nsd?.unregisterService(it) } }
        registrationListener = null
        discoveryListener?.let { runCatching { nsd?.stopServiceDiscovery(it) } }
        discoveryListener = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        resolved.clear()
        _peers.value = emptyList()
        _isAdvertising.value = false
    }

    // MARK: - Advertise

    private fun startAdvertising() {
        if (registrationListener != null) return
        val info = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = SERVICE_TYPE
            port = serverSocket?.localPort ?: return
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                _isAdvertising.value = true
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                _transfer.value = Transfer.Failed("Could not advertise ($errorCode)")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                _isAdvertising.value = false
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        runCatching { nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    // MARK: - Discover

    private fun startBrowsing() {
        if (discoveryListener != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) {
                resolved.remove(info.serviceName)
                publishPeers()
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName == deviceName) return
                resolve(info)
            }
        }
        discoveryListener = listener
        runCatching { nsd?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        if (resolved.containsKey(info.serviceName)) return
        runCatching {
            nsd?.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress ?: return
                    resolved[info.serviceName] = Peer(info.serviceName, host, info.port)
                    publishPeers()
                }
            })
        }
    }

    private fun publishPeers() {
        _peers.value = resolved.values.sortedBy { it.name.lowercase() }
    }

    // MARK: - Receive

    private fun startServer() {
        val socket = ServerSocket(0)
        serverSocket = socket
        Thread {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                Thread { handleIncoming(client) }.start()
            }
        }.apply { isDaemon = true; name = "sonari-share-server" }.start()
    }

    private fun handleIncoming(client: Socket) {
        runCatching {
            val reader = BufferedInputStream(client.getInputStream())
            val line = readLine(reader) ?: return@runCatching
            val wire = JSONObject(line)
            if (wire.optString("type") != "offer") return@runCatching

            val items = ArrayList<Item>()
            val arr = wire.optJSONArray("items") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val track = Json.trackFromShare(o.getJSONObject("track"))
                val lyrics = o.optJSONArray("lyrics")?.let { Json.lyricsFromJson(it.toString()) }
                items.add(Item(track, lyrics, o.optLong("size", 0)))
            }

            val offer = Offer(
                id = UUID.randomUUID().toString(),
                peerName = wire.optString("name", "Unknown"),
                items = items,
                sizeBytes = wire.optLong("size", 0),
                socket = client
            )
            _offers.value = _offers.value + offer
        }
    }

    suspend fun accept(offer: Offer) = withContext(Dispatchers.IO) {
        _offers.value = _offers.value.filterNot { it.id == offer.id }
        _transfer.value = Transfer.Receiving(0.0)
        runCatching {
            val out = offer.socket.getOutputStream()
            out.write("{\"type\":\"accept\"}\n".toByteArray())
            out.flush()

            val input = BufferedInputStream(offer.socket.getInputStream())
            var received = 0L
            val landed = ArrayList<Track>()

            for (item in offer.items) {
                val name = item.track.fileName ?: "${item.track.youtubeID}.m4a"
                val dest = File(AppPaths.dataDirectory, name)
                if (dest.exists()) dest.delete()
                dest.parentFile?.mkdirs()

                dest.outputStream().use { fileOut ->
                    var written = 0L
                    val buffer = ByteArray(256 * 1024)
                    while (written < item.size) {
                        val want = minOf(buffer.size.toLong(), item.size - written).toInt()
                        val read = input.read(buffer, 0, want)
                        if (read <= 0) break
                        fileOut.write(buffer, 0, read)
                        written += read
                        received += read
                        if (offer.sizeBytes > 0) {
                            _transfer.value = Transfer.Receiving(
                                (received.toDouble() / offer.sizeBytes).coerceIn(0.0, 1.0)
                            )
                        }
                    }
                    if (written != item.size) throw IllegalStateException("${item.track.title} ended early")
                }

                val track = item.track.copy(fileName = name, fileSize = item.size)
                LibraryStore.addTrack(track)
                landed.add(track)
                item.lyrics?.takeIf { it.isNotEmpty() }?.let { LyricsService.saveCached(it, track.youtubeID) }
            }

            _lastReceived.value = landed
            _transfer.value = Transfer.Finished
        }.onFailure { _transfer.value = Transfer.Failed("Receive failed: ${it.message}") }
        offer.socket.close()
    }

    fun decline(offer: Offer) {
        _offers.value = _offers.value.filterNot { it.id == offer.id }
        Thread {
            runCatching {
                val out = offer.socket.getOutputStream()
                out.write("{\"type\":\"decline\"}\n".toByteArray())
                out.flush()
            }
            runCatching { offer.socket.close() }
        }.start()
    }

    // MARK: - Send

    suspend fun share(tracks: List<Track>, peer: Peer) = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) {
            _transfer.value = Transfer.Failed("Nothing selected")
            return@withContext
        }
        _transfer.value = Transfer.Preparing

        val items = ArrayList<Item>()
        for (track in tracks) {
            val file = track.localFileURL
            if (file == null) {
                _transfer.value = Transfer.Failed("${track.title} has no downloaded file")
                return@withContext
            }
            val lyrics = LyricsService.loadCached(track.youtubeID)
            items.add(Item(track, lyrics, file.length()))
        }

        val total = items.sumOf { it.size }
        var socket: Socket? = null

        runCatching {
            socket = Socket().apply {
                connect(InetSocketAddress(peer.host, peer.port), 8000)
            }
            val out = socket!!.getOutputStream()

            val wire = JSONObject().apply {
                put("type", "offer")
                put("name", deviceName)
                put("size", total)
                put("items", JSONArray().apply {
                    for (item in items) {
                        put(JSONObject().apply {
                            put("track", Json.trackToShare(item.track))
                            item.lyrics?.let { put("lyrics", JSONArray(Json.lyricsToJson(it))) }
                            put("size", item.size)
                        })
                    }
                })
            }
            out.write((wire.toString() + "\n").toByteArray())
            out.flush()

            val reader = BufferedInputStream(socket!!.getInputStream())
            val reply = readLine(reader) ?: throw IllegalStateException("No reply")
            if (JSONObject(reply).optString("type") != "accept") {
                _transfer.value = Transfer.Failed("${peer.name} declined")
                return@runCatching
            }

            _transfer.value = Transfer.Sending(0.0)
            var sent = 0L
            for (item in items) {
                val file = item.track.localFileURL ?: continue
                streamFile(file, out) { delta ->
                    sent += delta
                    if (total > 0) {
                        _transfer.value = Transfer.Sending((sent.toDouble() / total).coerceIn(0.0, 1.0))
                    }
                }
            }
            _transfer.value = Transfer.Finished
        }.onFailure { _transfer.value = Transfer.Failed("Transfer failed: ${it.message}") }

        runCatching { socket?.close() }
    }

    private fun streamFile(file: File, out: OutputStream, progress: (Long) -> Unit) {
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
                progress(read.toLong())
            }
        }
        out.flush()
    }

    // MARK: - Wire helpers

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString()
            sb.append(b.toChar())
        }
    }
}
