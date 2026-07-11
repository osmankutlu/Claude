import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../data/favorites_repository.dart';
import '../models/word.dart';
import '../utils/chinese_text.dart';
import '../utils/pinyin_tone.dart';
import '../utils/speak.dart';

class WordDetailScreen extends StatelessWidget {
  final Word word;

  const WordDetailScreen({super.key, required this.word});

  @override
  Widget build(BuildContext context) {

    return Scaffold(
      appBar: AppBar(
        title: Text(word.meaning),
        actions: [
          Consumer<FavoritesRepository>(
            builder: (context, favorites, _) => IconButton(
              icon: Icon(
                favorites.isWordFavorite(word.id) ? Icons.star : Icons.star_border,
                color: favorites.isWordFavorite(word.id) ? Colors.amber : null,
              ),
              onPressed: () => favorites.toggleWord(word.id),
            ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Center(
            child: Column(
              children: [
                ChineseText(
                  word.hanzi,
                  pinyin: word.pinyin,
                  style: const TextStyle(fontSize: 56, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),
                TonedPinyinText(word.pinyin, style: const TextStyle(fontSize: 20)),
                const SizedBox(height: 12),
                FilledButton.icon(
                  onPressed: () => speakWithFeedback(context, word.hanzi),
                  icon: const Icon(Icons.volume_up),
                  label: const Text('Telaffuz Dinle'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          _InfoRow(label: 'Anlam', value: word.meaning),
          _InfoRow(label: 'Tür', value: word.partOfSpeech),
          _InfoRow(label: 'HSK Seviyesi', value: word.level.toString()),
          _InfoRow(label: 'Kategori', value: word.category),
          const SizedBox(height: 16),
          Text('Örnek Cümleler', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          ...word.examples.map(
            (ex) => Card(
              margin: const EdgeInsets.only(bottom: 12),
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

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;

  const _InfoRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(width: 120, child: Text(label, style: const TextStyle(color: Colors.grey))),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }
}
