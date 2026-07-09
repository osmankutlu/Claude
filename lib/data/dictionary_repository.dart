import 'dart:convert';
import 'package:flutter/services.dart' show rootBundle;

import '../models/word.dart';

class DictionaryRepository {
  List<Word>? _cache;

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
}
