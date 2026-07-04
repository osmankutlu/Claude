import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

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
          final topics = snapshot.data!;
          return ListView.separated(
            padding: const EdgeInsets.symmetric(vertical: 8),
            itemCount: topics.length,
            separatorBuilder: (_, _) => const Divider(height: 1),
            itemBuilder: (context, index) {
              final topic = topics[index];
              return ListTile(
                leading: CircleAvatar(
                  backgroundColor: Theme.of(context).colorScheme.secondaryContainer,
                  child: Text('${topic.level}'),
                ),
                title: Text(topic.title),
                subtitle: Text(topic.summary, maxLines: 2, overflow: TextOverflow.ellipsis),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => GrammarDetailScreen(topic: topic)),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
