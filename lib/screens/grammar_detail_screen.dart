import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../data/favorites_repository.dart';
import '../models/grammar_topic.dart';
import '../services/tts_service.dart';
import '../utils/chinese_text.dart';
import '../utils/pinyin_tone.dart';

class GrammarDetailScreen extends StatelessWidget {
  final GrammarTopic topic;

  const GrammarDetailScreen({super.key, required this.topic});

  @override
  Widget build(BuildContext context) {
    final tts = context.read<TtsService>();

    return Scaffold(
      appBar: AppBar(
        title: Text('HSK ${topic.level}'),
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
          const SizedBox(height: 8),
          Text(topic.summary, style: TextStyle(color: Theme.of(context).colorScheme.secondary)),
          const Divider(height: 32),
          Text(topic.explanation, style: const TextStyle(fontSize: 16, height: 1.5)),
          const SizedBox(height: 24),
          const Text('Örnek Cümleler', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
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
                      onPressed: () => tts.speak(ex.zh),
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
