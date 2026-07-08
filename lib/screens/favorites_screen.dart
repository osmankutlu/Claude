import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../data/dictionary_repository.dart';
import '../data/favorites_repository.dart';
import '../data/grammar_repository.dart';
import '../models/grammar_topic.dart';
import '../models/word.dart';
import '../utils/pinyin_tone.dart';
import 'grammar_detail_screen.dart';
import 'word_detail_screen.dart';

class FavoritesScreen extends StatefulWidget {
  const FavoritesScreen({super.key});

  @override
  State<FavoritesScreen> createState() => _FavoritesScreenState();
}

class _FavoritesScreenState extends State<FavoritesScreen> {
  late final Future<(List<Word>, List<GrammarTopic>)> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<(List<Word>, List<GrammarTopic>)> _load() async {
    final dictionaryRepo = context.read<DictionaryRepository>();
    final grammarRepo = context.read<GrammarRepository>();
    final words = await dictionaryRepo.loadWords();
    final topics = await grammarRepo.loadTopics();
    return (words, topics);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Favoriler')),
      body: FutureBuilder<(List<Word>, List<GrammarTopic>)>(
        future: _future,
        builder: (context, snapshot) {
          if (!snapshot.hasData) {
            return const Center(child: CircularProgressIndicator());
          }
          final (words, topics) = snapshot.data!;
          return Consumer<FavoritesRepository>(
            builder: (context, favorites, _) {
              final favWords = words.where((w) => favorites.isWordFavorite(w.id)).toList();
              final favTopics = topics.where((t) => favorites.isGrammarFavorite(t.id)).toList();

              if (favWords.isEmpty && favTopics.isEmpty) {
                return const Center(
                  child: Padding(
                    padding: EdgeInsets.all(24),
                    child: Text(
                      'Henüz favori eklemedin.\n\nBir kelimenin veya gramer konusunun '
                      'detayında sağ üstteki yıldıza dokun, ya da ana ekran '
                      "widget'ından yıldızla.",
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.grey),
                    ),
                  ),
                );
              }

              return ListView(
                children: [
                  if (favWords.isNotEmpty) ...[
                    const _SectionHeader('Kelimeler'),
                    ...favWords.map(
                      (w) => ListTile(
                        leading: CircleAvatar(child: Text('HSK${w.level}', style: const TextStyle(fontSize: 10))),
                        title: Text(w.hanzi, style: const TextStyle(fontSize: 18)),
                        subtitle: RichText(
                          text: TextSpan(
                            style: DefaultTextStyle.of(context).style,
                            children: [
                              ...PinyinTone.spans(w.pinyin, baseStyle: DefaultTextStyle.of(context).style),
                              TextSpan(text: ' · ${w.meaning}'),
                            ],
                          ),
                        ),
                        trailing: IconButton(
                          icon: const Icon(Icons.star, color: Colors.amber),
                          onPressed: () => favorites.toggleWord(w.id),
                        ),
                        onTap: () => Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => WordDetailScreen(word: w)),
                        ),
                      ),
                    ),
                  ],
                  if (favTopics.isNotEmpty) ...[
                    const _SectionHeader('Gramer Konuları'),
                    ...favTopics.map(
                      (t) => ListTile(
                        leading: CircleAvatar(child: Text('HSK${t.level}', style: const TextStyle(fontSize: 10))),
                        title: Text(t.title),
                        subtitle: Text(t.summary, maxLines: 1, overflow: TextOverflow.ellipsis),
                        trailing: IconButton(
                          icon: const Icon(Icons.star, color: Colors.amber),
                          onPressed: () => favorites.toggleGrammar(t.id),
                        ),
                        onTap: () => Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => GrammarDetailScreen(topic: t)),
                        ),
                      ),
                    ),
                  ],
                ],
              );
            },
          );
        },
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader(this.title);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Text(title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
    );
  }
}
