import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Tracks which grammar topics the user has marked as studied/learned, so
/// the topic list can highlight them.
class GrammarProgressRepository extends ChangeNotifier {
  static const _storageKey = 'grammar_studied_ids';

  Set<String> _studiedIds = {};
  bool _loaded = false;

  bool isStudied(String id) => _studiedIds.contains(id);

  Future<void> load() async {
    if (_loaded) return;
    final prefs = await SharedPreferences.getInstance();
    _studiedIds = (prefs.getStringList(_storageKey) ?? []).toSet();
    _loaded = true;
    notifyListeners();
  }

  Future<void> toggleStudied(String id) async {
    if (!_studiedIds.remove(id)) _studiedIds.add(id);
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(_storageKey, _studiedIds.toList());
  }
}
