import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../data/favorites_repository.dart';
import '../models/word.dart';
import '../services/tts_service.dart';
import '../utils/chinese_text.dart';
import '../utils/pinyin_tone.dart';

/// Opens [word] in a floating dialog (meaning + examples + favorite star),
/// so a word tapped inside an example sentence can be inspected and
/// favorited without leaving the current screen. Words inside the dialog's
/// own example sentences are tappable too, chaining further dialogs.
Future<void> showWordDialog(BuildContext context, Word word) {
  return showDialog(
    context: context,
    builder: (_) => Dialog(
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: 440,
          maxHeight: MediaQuery.of(context).size.height * 0.82,
        ),
        child: Builder(
          builder: (dialogContext) => WordContentView(
            word: word,
            onClose: () => Navigator.of(dialogContext).pop(),
          ),
        ),
      ),
    ),
  );
}

/// The full "one word" card: header with HSK level/favorite/close, big
/// hanzi, pinyin, TTS button, meaning, and tappable example sentences.
/// Shared between the widget-tap popup activity and [showWordDialog].
class WordContentView extends StatelessWidget {
  final Word word;
  final VoidCallback onClose;

  const WordContentView({super.key, required this.word, required this.onClose});

  @override
  Widget build(BuildContext context) {
    final tts = context.read<TtsService>();
    return ListView(
      shrinkWrap: true,
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
      children: [
        Row(
          children: [
            Text(
              'HSK ${word.level}',
              style: TextStyle(fontWeight: FontWeight.bold, color: Theme.of(context).colorScheme.primary),
            ),
            const Spacer(),
            Consumer<FavoritesRepository>(
              builder: (context, favorites, _) => IconButton(
                icon: Icon(
                  favorites.isWordFavorite(word.id) ? Icons.star : Icons.star_border,
                  color: favorites.isWordFavorite(word.id) ? Colors.amber : null,
                ),
                onPressed: () => favorites.toggleWord(word.id),
              ),
            ),
            IconButton(icon: const Icon(Icons.close), onPressed: onClose),
          ],
        ),
        Center(
          child: Column(
            children: [
              ChineseText(
                word.hanzi,
                pinyin: word.pinyin,
                style: const TextStyle(fontSize: 48, fontWeight: FontWeight.bold),
              ),
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
                        ChineseText(
                          ex.zh,
                          pinyin: ex.pinyin,
                          style: const TextStyle(fontSize: 18),
                          tappableWords: true,
                        ),
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
