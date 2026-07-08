import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:home_widget/home_widget.dart';

/// Stores favorited word/grammar ids using the same shared storage the
/// `home_widget` plugin exposes to native code, so a word/grammar starred
/// from the home-screen widget shows up here too, and vice versa.
class FavoritesRepository extends ChangeNotifier {
  static const _wordsKey = 'favorite_word_ids';
  static const _grammarKey = 'favorite_grammar_ids';

  Set<String> _favoriteWordIds = {};
  Set<String> _favoriteGrammarIds = {};
  bool _loaded = false;

  Set<String> get favoriteWordIds => _favoriteWordIds;
  Set<String> get favoriteGrammarIds => _favoriteGrammarIds;

  bool isWordFavorite(String id) => _favoriteWordIds.contains(id);
  bool isGrammarFavorite(String id) => _favoriteGrammarIds.contains(id);

  Future<void> load() async {
    if (_loaded) return;
    _favoriteWordIds = await _readIds(_wordsKey);
    _favoriteGrammarIds = await _readIds(_grammarKey);
    _loaded = true;
    notifyListeners();
  }

  Future<Set<String>> _readIds(String key) async {
    final raw = await HomeWidget.getWidgetData<String>(key);
    if (raw == null) return {};
    return (jsonDecode(raw) as List<dynamic>).cast<String>().toSet();
  }

  Future<void> toggleWord(String id) async {
    if (!_favoriteWordIds.remove(id)) _favoriteWordIds.add(id);
    notifyListeners();
    await HomeWidget.saveWidgetData(_wordsKey, jsonEncode(_favoriteWordIds.toList()));
    await HomeWidget.updateWidget(androidName: 'WordWidgetProvider');
  }

  Future<void> toggleGrammar(String id) async {
    if (!_favoriteGrammarIds.remove(id)) _favoriteGrammarIds.add(id);
    notifyListeners();
    await HomeWidget.saveWidgetData(_grammarKey, jsonEncode(_favoriteGrammarIds.toList()));
    await HomeWidget.updateWidget(androidName: 'WordWidgetProvider');
  }
}
