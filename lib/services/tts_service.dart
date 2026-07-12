import 'package:flutter/foundation.dart';
import 'package:flutter_tts/flutter_tts.dart';

/// Wraps flutter_tts configured for Mandarin Chinese pronunciation.
///
/// [speak] returns a human-readable Turkish error string when something
/// goes wrong (no Chinese voice installed, engine failure, …) and null on
/// success, so the UI can surface *why* there was no sound instead of the
/// call just failing silently — the most common real-device cause is that
/// the phone has no zh-CN voice data downloaded, which the app can't fix
/// but can at least tell the user about.
class TtsService {
  final FlutterTts _tts = FlutterTts();
  bool _initialized = false;
  String? _lastError;

  Future<void> _ensureInitialized() async {
    if (_initialized) return;

    _tts.setErrorHandler((msg) {
      _lastError = msg?.toString();
    });

    // Block speak() until the utterance actually finishes/errors, so a
    // failure is observable rather than fire-and-forget.
    await _tts.awaitSpeakCompletion(true);
    // Prefer Google's TTS engine when it's installed. Many phones (Samsung in
    // particular) default to a vendor engine whose Mandarin voice is poor or
    // missing; Google's neural zh-CN voice pronounces tones far more
    // accurately, so switch to it before configuring language/voice.
    await _selectGoogleEngine();
    await _tts.setSpeechRate(0.45);
    await _tts.setPitch(1.0);
    await _tts.setLanguage('zh-CN');
    await _selectBestChineseVoice();
    _initialized = true;
  }

  /// Switches to Google Text-to-speech (com.google.android.tts) when it's one
  /// of the installed engines. No-op on platforms/devices where it isn't
  /// available, or where engine enumeration isn't supported.
  Future<void> _selectGoogleEngine() async {
    try {
      final engines = await _tts.getEngines;
      if (engines is! List) return;
      const google = 'com.google.android.tts';
      if (engines.any((e) => e?.toString() == google)) {
        await _tts.setEngine(google);
      }
    } catch (_) {
      // Engine listing/switching isn't supported everywhere (e.g. web/iOS);
      // fall back silently to the system default engine.
    }
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

  /// Speaks [text]. Returns null on success, or a Turkish error message
  /// describing why it couldn't (for the UI to show).
  Future<String?> speak(String text) async {
    try {
      await _ensureInitialized();

      // A Mandarin voice being unavailable is the usual real-device cause
      // of silence — report it specifically so the user knows to install
      // the Chinese voice pack rather than assuming the app is broken.
      final available = await _tts.isLanguageAvailable('zh-CN');
      if (available == false) {
        return 'Cihazında Çince (zh-CN) sesi yüklü değil. Ayarlar → Sistem → '
            'Diller ve giriş → Metin okuma çıkışı bölümünden Çince ses '
            'paketini indirmen gerekiyor.';
      }

      _lastError = null;
      await _tts.stop();
      final result = await _tts.speak(text);
      // flutter_tts returns 1 on a successful queue on Android; 0 means the
      // engine rejected it.
      if (result == 0) {
        return _lastError != null
            ? 'Telaffuz çalınamadı: $_lastError'
            : 'Telaffuz çalınamadı. Cihazının metin okuma motorunu kontrol et.';
      }
      if (_lastError != null) {
        return 'Telaffuz hatası: $_lastError';
      }
      return null;
    } catch (e) {
      debugPrint('TTS speak failed: $e');
      return 'Telaffuz sırasında bir hata oluştu: $e';
    }
  }

  Future<void> stop() => _tts.stop();
}
