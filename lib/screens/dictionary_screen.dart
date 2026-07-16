import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../data/dictionary_repository.dart';
import '../models/word.dart';
import '../widgets/word_list_tile.dart';
import 'word_detail_screen.dart';

class DictionaryScreen extends StatefulWidget {
  const DictionaryScreen({super.key});

  @override
  State<DictionaryScreen> createState() => _DictionaryScreenState();
}

class _DictionaryScreenState extends State<DictionaryScreen> {
  late Future<List<Word>> _future;
  String _query = '';

  @override
  void initState() {
    super.initState();
    _future = context.read<DictionaryRepository>().loadWords();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Çince-Türkçe Sözlük')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: TextField(
              decoration: InputDecoration(
                hintText: 'Kelime ara (hanzi, pinyin veya Türkçe)',
                prefixIcon: const Icon(Icons.search),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
              ),
              onChanged: (v) => setState(() => _query = v.trim().toLowerCase()),
            ),
          ),
          Expanded(
            child: FutureBuilder<List<Word>>(
              future: _future,
              builder: (context, snapshot) {
                if (!snapshot.hasData) {
                  return const Center(child: CircularProgressIndicator());
                }
                final words = snapshot.data!.where((w) {
                  if (_query.isEmpty) return true;
                  return w.hanzi.contains(_query) ||
                      w.pinyin.toLowerCase().contains(_query) ||
                      w.meaning.toLowerCase().contains(_query);
                }).toList();

                if (words.isEmpty) {
                  return const Center(child: Text('Sonuç bulunamadı'));
                }

                return ListView.builder(
                  itemCount: words.length,
                  itemBuilder: (context, index) {
                    final word = words[index];
                    return WordListTile(
                      word: word,
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(builder: (_) => WordDetailScreen(word: word)),
                      ),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
