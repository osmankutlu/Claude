import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:home_widget/home_widget.dart';

import '../models/favorite_group.dart';

/// Stores user-created favorite word groups using the same shared storage
/// the `home_widget` plugin exposes to native code, so a widget can be
/// configured to show a specific group (see WidgetDataStore.kt on the
/// native side, which reads the same key to filter words for the widget).
class FavoriteGroupsRepository extends ChangeNotifier {
  static const _key = 'favorite_groups';

  List<FavoriteGroup> _groups = [];
  bool _loaded = false;

  List<FavoriteGroup> get groups => List.unmodifiable(_groups);

  Future<void> load() async {
    if (_loaded) return;
    final raw = await HomeWidget.getWidgetData<String>(_key);
    if (raw != null) {
      try {
        _groups = (jsonDecode(raw) as List<dynamic>)
            .map((e) => FavoriteGroup.fromJson(e as Map<String, dynamic>))
            .toList();
      } catch (e) {
        _groups = [];
      }
    }
    _loaded = true;
    notifyListeners();
  }

  Future<void> _persist() async {
    await HomeWidget.saveWidgetData(_key, jsonEncode(_groups.map((g) => g.toJson()).toList()));
    await HomeWidget.updateWidget(androidName: 'WordWidgetProvider');
  }

  bool isWordInGroup(String groupId, String wordId) =>
      _groups.firstWhere((g) => g.id == groupId, orElse: () => const FavoriteGroup(id: '', name: '', wordIds: {}))
          .wordIds
          .contains(wordId);

  Future<FavoriteGroup> createGroup(String name) async {
    final group = FavoriteGroup(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      name: name,
      wordIds: const {},
    );
    _groups = [..._groups, group];
    notifyListeners();
    await _persist();
    return group;
  }

  Future<void> renameGroup(String id, String name) async {
    _groups = _groups.map((g) => g.id == id ? g.copyWith(name: name) : g).toList();
    notifyListeners();
    await _persist();
  }

  Future<void> deleteGroup(String id) async {
    _groups = _groups.where((g) => g.id != id).toList();
    notifyListeners();
    await _persist();
  }

  Future<void> toggleWordInGroup(String groupId, String wordId) async {
    _groups = _groups.map((g) {
      if (g.id != groupId) return g;
      final ids = {...g.wordIds};
      if (!ids.remove(wordId)) ids.add(wordId);
      return g.copyWith(wordIds: ids);
    }).toList();
    notifyListeners();
    await _persist();
  }

  /// Drops [wordId] from every group — used when a word is un-favorited,
  /// since a group should only ever contain currently-favorited words.
  Future<void> removeWordFromAllGroups(String wordId) async {
    var changed = false;
    _groups = _groups.map((g) {
      if (!g.wordIds.contains(wordId)) return g;
      changed = true;
      return g.copyWith(wordIds: {...g.wordIds}..remove(wordId));
    }).toList();
    if (changed) {
      notifyListeners();
      await _persist();
    }
  }
}
