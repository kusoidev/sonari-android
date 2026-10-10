package com.kusoi.sonari.music

import com.ryanheise.audioservice.AudioServiceActivity

// audio_service requires the host activity to provide the shared FlutterEngine
// to its service. Extending AudioServiceActivity wires that up; a plain
// FlutterActivity makes JustAudioBackground.init throw.
class MainActivity : AudioServiceActivity()