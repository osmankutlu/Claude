import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/word.dart';
import '../services/tts_service.dart';

class WordDetailScreen extends StatelessWidget {
  final Word word;

  const WordDetailScreen({super.key, required this.word});

  @override
  Widget build(BuildContext context) {
    final tts = context.read<TtsService>();

    return Scaffold(
      appBar: AppBar(title: Text(word.meaning)),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Center(
            child: Column(
              children: [
                Text(word.hanzi, style: const TextStyle(fontSize: 56, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                Text(word.pinyin, style: TextStyle(fontSize: 20, color: Theme.of(context).colorScheme.secondary)),
                const SizedBox(height: 12),
                FilledButton.icon(
                  onPressed: () => tts.speak(word.hanzi),
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
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('Örnek Cümle', style: TextStyle(fontWeight: FontWeight.bold)),
                      IconButton(
                        icon: const Icon(Icons.volume_up),
                        onPressed: () => tts.speak(word.exampleZh),
                      ),
                    ],
                  ),
                  Text(word.exampleZh, style: const TextStyle(fontSize: 20)),
                  const SizedBox(height: 4),
                  Text(word.examplePinyin, style: TextStyle(color: Theme.of(context).colorScheme.secondary)),
                  const SizedBox(height: 4),
                  Text(word.exampleEn, style: const TextStyle(fontStyle: FontStyle.italic)),
                ],
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
