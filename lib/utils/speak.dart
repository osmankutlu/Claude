import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../services/tts_service.dart';

/// Speaks [text] and, if the TTS engine couldn't play it, shows the reason
/// as a SnackBar — so "no sound" always comes with an explanation (usually
/// a missing Chinese voice pack) instead of a dead button.
Future<void> speakWithFeedback(BuildContext context, String text) async {
  final tts = context.read<TtsService>();
  final messenger = ScaffoldMessenger.maybeOf(context);
  final error = await tts.speak(text);
  if (error != null && messenger != null) {
    messenger.showSnackBar(
      SnackBar(content: Text(error), duration: const Duration(seconds: 5)),
    );
  }
}
