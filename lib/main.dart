import 'package:flutter/material.dart';
import 'package:just_audio_background/just_audio_background.dart';

import 'screens/root_shell.dart';
import 'services/library_manager.dart';
import 'state/player_controller.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await JustAudioBackground.init(
    androidNotificationChannelId: 'com.kusoi.sonari.music.channel.audio',
    androidNotificationChannelName: 'Sonari playback',
    androidNotificationOngoing: true,
  );

  await LibraryManager.instance.load();
  await LyricsMode.init(LibraryManager.instance.docsPath);
  await PlayerController.instance.init();

  runApp(const SonariApp());
}

class SonariApp extends StatelessWidget {
  const SonariApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Sonari',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF08080A),
        colorScheme: const ColorScheme.dark(
          surface: Color(0xFF0E0E12),
        ),
        splashFactory: InkSparkle.splashFactory,
      ),
      home: const RootShell(),
    );
  }
}