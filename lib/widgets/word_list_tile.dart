import 'package:flutter/material.dart';

import '../models/word.dart';
import '../utils/pinyin_tone.dart';

class WordListTile extends StatelessWidget {
  final Word word;
  final VoidCallback onTap;

  const WordListTile({super.key, required this.word, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      onTap: onTap,
      leading: CircleAvatar(
        backgroundColor: Theme.of(context).colorScheme.primaryContainer,
        child: Text(
          'HSK${word.level}',
          style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold),
        ),
      ),
      title: Row(
        crossAxisAlignment: CrossAxisAlignment.baseline,
        textBaseline: TextBaseline.alphabetic,
        children: [
          Text(word.hanzi, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w600)),
          const SizedBox(width: 8),
          TonedPinyinText(word.pinyin),
        ],
      ),
      subtitle: Text(word.meaning),
      trailing: const Icon(Icons.chevron_right),
    );
  }
}
