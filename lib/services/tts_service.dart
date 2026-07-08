import 'package:flutter_tts/flutter_tts.dart';

/// Wraps flutter_tts configured for Mandarin Chinese pronunciation.
class TtsService {
  final FlutterTts _tts = FlutterTts();
  bool _initialized = false;

  Future<void> _ensureInitialized() async {
    if (_initialized) return;
    await _tts.setLanguage('zh-CN');
    await _tts.setSpeechRate(0.45);
    await _tts.setPitch(1.0);
    await _selectBestChineseVoice();
    _initialized = true;
  }

  /// The system TTS engine (Google TTS on most Android phones) can have
  /// several installed Mandarin voices of differing quality once the user
  /// has downloaded the "network"/enhanced voice packs in Android settings.
  /// Local, low-quality voices are picked by default; this prefers a
  /// network or otherwise higher-quality zh-CN voice when one is available,
  /// which sounds noticeably more natural than the default robotic voice.
  Future<void> _selectBestChineseVoice() async {
    try {
      final voices = await _tts.getVoices;
      if (voices is! List) return;
      final zhVoices = voices
          .whereType<Map>()
          .where((v) => (v['locale'] as String? ?? '')
              .toLowerCase()
              .replaceAll('_', '-')
              .startsWith('zh-cn'))
          .toList();
      if (zhVoices.isEmpty) return;

      Map? best;
      for (final v in zhVoices) {
        final name = (v['name'] as String? ?? '').toLowerCase();
        if (name.contains('network') || name.contains('enhanced')) {
          best = v;
          break;
        }
      }
      best ??= zhVoices.first;
      await _tts.setVoice({
        'name': best['name'] as String,
        'locale': best['locale'] as String,
      });
    } catch (_) {
      // Voice enumeration isn't supported on every platform (e.g. web);
      // fall back silently to the language-only configuration above.
    }
  }

  Future<void> speak(String text) async {
    await _ensureInitialized();
    await _tts.stop();
    await _tts.speak(text);
  }

  Future<void> stop() => _tts.stop();
}
