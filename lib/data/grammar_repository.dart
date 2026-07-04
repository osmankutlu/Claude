import 'dart:convert';
import 'package:flutter/services.dart' show rootBundle;

import '../models/grammar_topic.dart';

class GrammarRepository {
  List<GrammarTopic>? _cache;

  Future<List<GrammarTopic>> loadTopics() async {
    if (_cache != null) return _cache!;
    final raw = await rootBundle.loadString('assets/data/grammar.json');
    final list = jsonDecode(raw) as List<dynamic>;
    _cache = list.map((e) => GrammarTopic.fromJson(e as Map<String, dynamic>)).toList();
    return _cache!;
  }
}
