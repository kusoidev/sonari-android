package com.kusoi.sonari.music.ViewModels

import com.kusoi.sonari.music.Services.YouTubeExtractor
import com.kusoi.sonari.music.Services.YouTubeSearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Search state holder. Mirrors the Swift SearchViewModel. */
object SearchViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<YouTubeSearchResult>>(emptyList())
    val results: StateFlow<List<YouTubeSearchResult>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setQuery(q: String) {
        _query.value = q
    }

    fun search() {
        val q = _query.value.trim()
        if (q.isEmpty()) return

        scope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            runCatching {
                YouTubeExtractor.search(q)
            }.onSuccess { results ->
                _results.value = results
                if (results.isEmpty()) _errorMessage.value = "No results found."
            }.onFailure {
                _errorMessage.value = it.message ?: "Search failed"
                _results.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    fun clear() {
        _query.value = ""
        _results.value = emptyList()
        _errorMessage.value = null
    }
}