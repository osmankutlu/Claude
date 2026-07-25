import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../data/grammar_progress_repository.dart';
import '../data/grammar_repository.dart';
import '../models/grammar_topic.dart';
import 'grammar_detail_screen.dart';

class GrammarListScreen extends StatefulWidget {
  const GrammarListScreen({super.key});

  @override
  State<GrammarListScreen> createState() => _GrammarListScreenState();
}

class _GrammarListScreenState extends State<GrammarListScreen> {
  late Future<List<GrammarTopic>> _future;
  int _level = 0; // 0 = all; 6 = the "5-6" advanced section
  String _query = '';

  @override
  void initState() {
    super.initState();
    _future = context.read<GrammarRepository>().loadTopics();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Gramer Konuları')),
      body: FutureBuilder<List<GrammarTopic>>(
        future: _future,
        builder: (context, snapshot) {
          if (!snapshot.hasData) {
            return const Center(child: CircularProgressIndicator());
          }
          final all = snapshot.data!;
          final topics = all.where((t) {
            if (_level != 0 && t.level != _level) return false;
            if (_query.isEmpty) return true;
            final q = _query;
            return t.title.toLowerCase().contains(q) ||
                t.titleEn.toLowerCase().contains(q) ||
                t.pattern.toLowerCase().contains(q) ||
                t.summary.toLowerCase().contains(q);
          }).toList();

          return Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                child: TextField(
                  decoration: InputDecoration(
                    hintText: 'Konu ara (başlık, kalıp, açıklama)',
                    prefixIcon: const Icon(Icons.search),
                    isDense: true,
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  onChanged: (v) => setState(() => _query = v.trim().toLowerCase()),
                ),
              ),
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                child: Row(
                  children: [
                    _levelChip('Hepsi', 0),
                    for (final lvl in [1, 2, 3, 4]) _levelChip('HSK$lvl', lvl),
                    _levelChip('HSK5-6', 5),
                  ],
                ),
              ),
              Expanded(
                child: topics.isEmpty
                    ? const Center(child: Text('Konu bulunamadı'))
                    : ListView.separated(
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        itemCount: topics.length,
                        separatorBuilder: (_, _) => const Divider(height: 1),
                        itemBuilder: (context, index) {
                          final topic = topics[index];
                          return Consumer<GrammarProgressRepository>(
                            builder: (context, progress, _) {
                              final studied = progress.isStudied(topic.id);
                              return ListTile(
                                tileColor: studied ? Colors.green.shade50 : null,
                                leading: CircleAvatar(
                                  backgroundColor: Theme.of(context).colorScheme.secondaryContainer,
                                  child: Text(topic.levelLabel,
                                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                                ),
                                title: Text(topic.pattern.isNotEmpty ? topic.pattern : topic.title),
                                trailing: studied
                                    ? Icon(Icons.check_circle, color: Colors.green.shade700, size: 20)
                                    : const Icon(Icons.chevron_right),
                                onTap: () => Navigator.of(context).push(
                                  MaterialPageRoute(builder: (_) => GrammarDetailScreen(topic: topic)),
                                ),
                              );
                            },
                          );
                        },
                      ),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _levelChip(String label, int level) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: ChoiceChip(
        label: Text(label),
        selected: _level == level,
        onSelected: (_) => setState(() => _level = level),
      ),
    );
  }
}
