import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../data/dictionary_repository.dart';
import '../data/favorite_groups_repository.dart';
import '../data/favorites_repository.dart';
import '../data/grammar_repository.dart';
import '../models/favorite_group.dart';
import '../models/grammar_topic.dart';
import '../models/word.dart';
import '../utils/chinese_text.dart';
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
  String? _selectedGroupId; // null = "Tümü" (all favorites)

  @override
  void initState() {
    super.initState();
    _future = _load();
    context.read<FavoriteGroupsRepository>().load();
  }

  Future<(List<Word>, List<GrammarTopic>)> _load() async {
    final dictionaryRepo = context.read<DictionaryRepository>();
    final grammarRepo = context.read<GrammarRepository>();
    final words = await dictionaryRepo.loadWords();
    final topics = await grammarRepo.loadTopics();
    return (words, topics);
  }

  Future<void> _createGroup() async {
    final name = await _promptGroupName(context, title: 'Yeni Grup');
    if (name == null || name.trim().isEmpty) return;
    final groups = context.read<FavoriteGroupsRepository>();
    final group = await groups.createGroup(name.trim());
    if (mounted) setState(() => _selectedGroupId = group.id);
  }

  Future<void> _renameGroup(FavoriteGroup group) async {
    final name = await _promptGroupName(context, title: 'Grubu Yeniden Adlandır', initial: group.name);
    if (name == null || name.trim().isEmpty) return;
    await context.read<FavoriteGroupsRepository>().renameGroup(group.id, name.trim());
  }

  Future<void> _deleteGroup(FavoriteGroup group) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Grubu Sil'),
        content: Text('"${group.name}" grubunu silmek istediğine emin misin? Kelimeler favorilerden çıkmaz.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Vazgeç')),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Sil')),
        ],
      ),
    );
    if (confirmed != true) return;
    await context.read<FavoriteGroupsRepository>().deleteGroup(group.id);
    if (mounted && _selectedGroupId == group.id) setState(() => _selectedGroupId = null);
  }

  Future<String?> _promptGroupName(BuildContext context, {required String title, String? initial}) {
    final controller = TextEditingController(text: initial);
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(hintText: 'Grup adı (ör. Seyahat, İş)'),
          onSubmitted: (v) => Navigator.pop(ctx, v),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Vazgeç')),
          TextButton(onPressed: () => Navigator.pop(ctx, controller.text), child: const Text('Kaydet')),
        ],
      ),
    );
  }

  Future<void> _editWordGroups(Word word) async {
    final groupsRepo = context.read<FavoriteGroupsRepository>();
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Gruplara Ekle'),
        content: Consumer<FavoriteGroupsRepository>(
          builder: (context, groups, _) {
            if (groups.groups.isEmpty) {
              return const Text('Henüz grup yok. Önce bir grup oluştur.');
            }
            return SizedBox(
              width: double.maxFinite,
              child: ListView(
                shrinkWrap: true,
                children: groups.groups.map((g) {
                  return CheckboxListTile(
                    title: Text(g.name),
                    value: g.wordIds.contains(word.id),
                    onChanged: (_) => groupsRepo.toggleWordInGroup(g.id, word.id),
                  );
                }).toList(),
              ),
            );
          },
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              _createGroup();
            },
            child: const Text('+ Yeni Grup'),
          ),
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Kapat')),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Favoriler'),
        actions: [
          IconButton(
            icon: const Icon(Icons.create_new_folder_outlined),
            tooltip: 'Yeni grup oluştur',
            onPressed: _createGroup,
          ),
        ],
      ),
      body: FutureBuilder<(List<Word>, List<GrammarTopic>)>(
        future: _future,
        builder: (context, snapshot) {
          if (!snapshot.hasData) {
            return const Center(child: CircularProgressIndicator());
          }
          final (words, topics) = snapshot.data!;
          return Consumer2<FavoritesRepository, FavoriteGroupsRepository>(
            builder: (context, favorites, groupsRepo, _) {
              var favWords = words.where((w) => favorites.isWordFavorite(w.id)).toList();
              final favTopics = topics.where((t) => favorites.isGrammarFavorite(t.id)).toList();

              FavoriteGroup? selectedGroup;
              if (_selectedGroupId != null) {
                for (final g in groupsRepo.groups) {
                  if (g.id == _selectedGroupId) {
                    selectedGroup = g;
                    break;
                  }
                }
              }
              final group = selectedGroup;
              if (group != null) {
                // Intersect with current favorites, not just the group's
                // stored ids — a word un-favorited elsewhere (including
                // from the native widget's own ★ button, a separate code
                // path that doesn't know about groups) shouldn't linger.
                favWords = favWords.where((w) => group.wordIds.contains(w.id)).toList();
              } else if (_selectedGroupId != null) {
                // The selected group was deleted out from under us.
                _selectedGroupId = null;
              }

              if (favWords.isEmpty && favTopics.isEmpty && groupsRepo.groups.isEmpty) {
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
                  if (groupsRepo.groups.isNotEmpty) ...[
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                      child: Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          ChoiceChip(
                            label: const Text('Tümü'),
                            selected: _selectedGroupId == null,
                            onSelected: (_) => setState(() => _selectedGroupId = null),
                          ),
                          ...groupsRepo.groups.map(
                            (g) => ChoiceChip(
                              label: Text(g.name),
                              selected: _selectedGroupId == g.id,
                              onSelected: (_) => setState(() => _selectedGroupId = g.id),
                            ),
                          ),
                        ],
                      ),
                    ),
                    if (group != null)
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 0, 16, 4),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.end,
                          children: [
                            TextButton.icon(
                              onPressed: () => _renameGroup(group),
                              icon: const Icon(Icons.edit_outlined, size: 16),
                              label: const Text('Adını değiştir'),
                            ),
                            TextButton.icon(
                              onPressed: () => _deleteGroup(group),
                              icon: const Icon(Icons.delete_outline, size: 16),
                              label: const Text('Sil'),
                            ),
                          ],
                        ),
                      ),
                  ],
                  if (favWords.isNotEmpty) ...[
                    _SectionHeader(group?.name ?? 'Kelimeler'),
                    ...favWords.map(
                      (w) => ListTile(
                        leading: CircleAvatar(child: Text('HSK${w.level}', style: const TextStyle(fontSize: 10))),
                        title: ChineseText(w.hanzi, pinyin: w.pinyin, style: const TextStyle(fontSize: 18)),
                        subtitle: RichText(
                          text: TextSpan(
                            style: DefaultTextStyle.of(context).style,
                            children: [
                              ...PinyinTone.spans(w.pinyin, baseStyle: DefaultTextStyle.of(context).style),
                              TextSpan(text: ' · ${w.meaning}'),
                            ],
                          ),
                        ),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            IconButton(
                              icon: const Icon(Icons.folder_outlined),
                              tooltip: 'Gruplara ekle',
                              onPressed: () => _editWordGroups(w),
                            ),
                            IconButton(
                              icon: const Icon(Icons.star, color: Colors.amber),
                              onPressed: () async {
                                await favorites.toggleWord(w.id);
                                if (!favorites.isWordFavorite(w.id)) {
                                  await groupsRepo.removeWordFromAllGroups(w.id);
                                }
                              },
                            ),
                          ],
                        ),
                        onTap: () => Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => WordDetailScreen(word: w)),
                        ),
                      ),
                    ),
                  ],
                  if (group == null && favTopics.isNotEmpty) ...[
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
