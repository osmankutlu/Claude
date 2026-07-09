import 'dart:convert';
import 'package:flutter/services.dart' show rootBundle;

import '../models/word.dart';

class DictionaryRepository {
  List<Word>? _cache;
  Map<String, Word>? _hanziIndex;
  int _maxHanziLength = 1;

  Future<List<Word>> loadWords() async {
    if (_cache != null) return _cache!;
    final raw = await rootBundle.loadString('assets/data/words.json');
    final list = jsonDecode(raw) as List<dynamic>;
    _cache = list.map((e) => Word.fromJson(e as Map<String, dynamic>)).toList();
    return _cache!;
  }

  Future<Word?> findById(String id) async {
    final words = await loadWords();
    for (final word in words) {
      if (word.id == id) return word;
    }
    return null;
  }

  /// hanzi → entry lookup used to find dictionary words inside example
  /// sentences (see ChineseText's tappable-word segmentation).
  Future<Map<String, Word>> hanziIndex() async {
    if (_hanziIndex != null) return _hanziIndex!;
    final words = await loadWords();
    final index = <String, Word>{};
    for (final word in words) {
      index[word.hanzi] = word;
      if (word.hanzi.length > _maxHanziLength) _maxHanziLength = word.hanzi.length;
    }
    _hanziIndex = index;
    return index;
  }

  /// Already-built index, or null if [hanziIndex] hasn't completed yet.
  Map<String, Word>? get hanziIndexOrNull => _hanziIndex;

  /// Longest hanzi key in the index (valid once [hanziIndex] has completed).
  int get maxHanziLength => _maxHanziLength;
}
