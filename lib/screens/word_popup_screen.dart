import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../data/dictionary_repository.dart';
import '../data/favorites_repository.dart';
import '../data/grammar_repository.dart';
import '../models/grammar_topic.dart';
import '../models/word.dart';
import '../services/tts_service.dart';
import '../utils/pinyin_tone.dart';

/// The Flutter side of [WordPopupActivity]: renders as a floating dialog
/// over whatever was on screen (the native Activity's window is fully
/// transparent) instead of navigating into the main app. Reads which
/// word/grammar topic to show via the "com.osmankutlu.zh_en_dict/popup"
/// platform channel, populated from the tapped widget card's extras.
class WordPopupScreen extends StatefulWidget {
  const WordPopupScreen({super.key});

  @override
  State<WordPopupScreen> createState() => _WordPopupScreenState();
}

class _WordPopupScreenState extends State<WordPopupScreen> {
  static const _channel = MethodChannel('com.osmankutlu.zh_en_dict/popup');

  Word? _word;
  GrammarTopic? _topic;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final args = await _channel.invokeMapMethod<String, dynamic>('getArgs');
    final mode = args?['mode'] as String?;
    final itemId = args?['itemId'] as String?;
    if (!mounted || itemId == null) {
      setState(() => _loading = false);
      return;
    }

    if (mode == 'grammar') {
      final topic = await context.read<GrammarRepository>().findById(itemId);
      if (mounted) setState(() { _topic = topic; _loading = false; });
    } else {
      final word = await context.read<DictionaryRepository>().findById(itemId);
      if (mounted) setState(() { _word = word; _loading = false; });
    }
  }

  void _dismiss() => SystemNavigator.pop();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black54,
      body: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: _dismiss,
        child: SafeArea(
          child: Center(
            child: GestureDetector(
              // Absorb taps so tapping the card itself doesn't dismiss.
              onTap: () {},
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  maxWidth: 440,
                  maxHeight: MediaQuery.of(context).size.height * 0.82,
                ),
                child: Material(
                  color: Theme.of(context).colorScheme.surface,
                  borderRadius: BorderRadius.circular(20),
                  clipBehavior: Clip.antiAlias,
                  child: _buildBody(context),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildBody(BuildContext context) {
    if (_loading) {
      return const SizedBox(
        height: 200,
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (_word != null) return _WordPopupContent(word: _word!, onClose: _dismiss);
    if (_topic != null) return _GrammarPopupContent(topic: _topic!, onClose: _dismiss);
    return SizedBox(
      height: 160,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Bulunamadı.'),
            const SizedBox(height: 12),
            TextButton(onPressed: _dismiss, child: const Text('Kapat')),
          ],
        ),
      ),
    );
  }
}

class _WordPopupContent extends StatelessWidget {
  final Word word;
  final VoidCallback onClose;

  const _WordPopupContent({required this.word, required this.onClose});

  @override
  Widget build(BuildContext context) {
    final tts = context.read<TtsService>();
    return ListView(
      shrinkWrap: true,
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
      children: [
        _PopupHeader(
          label: 'HSK ${word.level}',
          onClose: onClose,
          favorite: Consumer<FavoritesRepository>(
            builder: (context, favorites, _) => IconButton(
              icon: Icon(
                favorites.isWordFavorite(word.id) ? Icons.star : Icons.star_border,
                color: favorites.isWordFavorite(word.id) ? Colors.amber : null,
              ),
              onPressed: () => favorites.toggleWord(word.id),
            ),
          ),
        ),
        Center(
          child: Column(
            children: [
              Text(word.hanzi, style: const TextStyle(fontSize: 48, fontWeight: FontWeight.bold)),
              const SizedBox(height: 6),
              TonedPinyinText(word.pinyin, style: const TextStyle(fontSize: 18)),
              const SizedBox(height: 10),
              FilledButton.icon(
                onPressed: () => tts.speak(word.hanzi),
                icon: const Icon(Icons.volume_up),
                label: const Text('Telaffuz Dinle'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        Text(word.meaning, style: const TextStyle(fontSize: 16)),
        const SizedBox(height: 4),
        Text(word.partOfSpeech, style: TextStyle(color: Theme.of(context).colorScheme.secondary)),
        const SizedBox(height: 16),
        Text('Örnek Cümleler', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        ...word.examples.map(
          (ex) => Card(
            margin: const EdgeInsets.only(bottom: 12),
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(ex.zh, style: const TextStyle(fontSize: 18)),
                        const SizedBox(height: 4),
                        TonedPinyinText(ex.pinyin),
                        const SizedBox(height: 4),
                        Text(ex.en, style: const TextStyle(fontStyle: FontStyle.italic)),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.volume_up),
                    onPressed: () => tts.speak(ex.zh),
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _GrammarPopupContent extends StatelessWidget {
  final GrammarTopic topic;
  final VoidCallback onClose;

  const _GrammarPopupContent({required this.topic, required this.onClose});

  @override
  Widget build(BuildContext context) {
    final tts = context.read<TtsService>();
    return ListView(
      shrinkWrap: true,
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
      children: [
        _PopupHeader(
          label: 'HSK ${topic.level}',
          onClose: onClose,
          favorite: Consumer<FavoritesRepository>(
            builder: (context, favorites, _) => IconButton(
              icon: Icon(
                favorites.isGrammarFavorite(topic.id) ? Icons.star : Icons.star_border,
                color: favorites.isGrammarFavorite(topic.id) ? Colors.amber : null,
              ),
              onPressed: () => favorites.toggleGrammar(topic.id),
            ),
          ),
        ),
        Text(topic.title, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        Text(topic.summary, style: TextStyle(color: Theme.of(context).colorScheme.secondary)),
        const Divider(height: 28),
        Text(topic.explanation, style: const TextStyle(fontSize: 16, height: 1.5)),
        const SizedBox(height: 20),
        Text('Örnek Cümleler', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        ...topic.examples.map(
          (ex) => Card(
            margin: const EdgeInsets.only(bottom: 12),
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(ex.zh, style: const TextStyle(fontSize: 18)),
                        const SizedBox(height: 4),
                        TonedPinyinText(ex.pinyin),
                        const SizedBox(height: 4),
                        Text(ex.en, style: const TextStyle(fontStyle: FontStyle.italic)),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.volume_up),
                    onPressed: () => tts.speak(ex.zh),
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _PopupHeader extends StatelessWidget {
  final String label;
  final Widget favorite;
  final VoidCallback onClose;

  const _PopupHeader({required this.label, required this.favorite, required this.onClose});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(label, style: TextStyle(fontWeight: FontWeight.bold, color: Theme.of(context).colorScheme.primary)),
        const Spacer(),
        favorite,
        IconButton(icon: const Icon(Icons.close), onPressed: onClose),
      ],
    );
  }
}
