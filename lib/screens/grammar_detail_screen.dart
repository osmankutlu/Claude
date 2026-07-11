import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../data/favorites_repository.dart';
import '../models/grammar_topic.dart';
import '../utils/chinese_text.dart';
import '../utils/pinyin_tone.dart';
import '../utils/speak.dart';
import '../widgets/pattern_box.dart';

class GrammarDetailScreen extends StatelessWidget {
  final GrammarTopic topic;

  const GrammarDetailScreen({super.key, required this.topic});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('HSK ${topic.levelLabel}'),
        actions: [
          Consumer<FavoritesRepository>(
            builder: (context, favorites, _) => IconButton(
              icon: Icon(
                favorites.isGrammarFavorite(topic.id) ? Icons.star : Icons.star_border,
                color: favorites.isGrammarFavorite(topic.id) ? Colors.amber : null,
              ),
              onPressed: () => favorites.toggleGrammar(topic.id),
            ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(topic.title, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
          if (topic.titleEn.isNotEmpty) ...[
            const SizedBox(height: 2),
            Text(
              topic.titleEn,
              style: TextStyle(fontSize: 13, color: Theme.of(context).colorScheme.outline),
            ),
          ],
          const SizedBox(height: 12),
          Text(topic.summary, style: TextStyle(color: Theme.of(context).colorScheme.secondary)),
          if (topic.pattern.isNotEmpty) ...[
            const SizedBox(height: 16),
            PatternBox(topic.pattern),
          ],
          const Divider(height: 32),
          Text(topic.explanation, style: const TextStyle(fontSize: 16, height: 1.5)),
          const SizedBox(height: 24),
          Text('Örnek Cümleler (${topic.examples.length})',
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
          const SizedBox(height: 8),
          ...topic.examples.map(
            (ex) => Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          ChineseText(
                            ex.zh,
                            pinyin: ex.pinyin,
                            style: const TextStyle(fontSize: 20),
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
                      onPressed: () => speakWithFeedback(context, ex.zh),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
