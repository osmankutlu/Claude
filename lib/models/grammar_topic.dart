class GrammarExample {
  final String zh;
  final String pinyin;
  final String en;

  const GrammarExample({required this.zh, required this.pinyin, required this.en});

  factory GrammarExample.fromJson(Map<String, dynamic> json) {
    return GrammarExample(
      zh: json['zh'] as String,
      pinyin: json['pinyin'] as String,
      en: json['en'] as String,
    );
  }
}

class GrammarTopic {
  final String id;

  /// Turkish title shown to the user.
  final String title;

  /// The original English grammar-point name (from the Grammar Wiki source),
  /// shown under the Turkish title for learners used to English references.
  final String titleEn;

  /// HSK level 1-5. The source's advanced section (which mixes HSK 5 and 6
  /// material) is stored as 5 and shown as "5-6" via [levelLabel], keeping
  /// grammar levels aligned with the 1-5 range used everywhere else.
  final int level;

  /// The structural pattern, e.g. "Subj. + 把 + Obj. + Verb + 了", shown in a
  /// highlighted box.
  final String pattern;

  final String summary;
  final String explanation;
  final List<GrammarExample> examples;

  const GrammarTopic({
    required this.id,
    required this.title,
    required this.titleEn,
    required this.level,
    required this.pattern,
    required this.summary,
    required this.explanation,
    required this.examples,
  });

  /// Display label for [level]; the advanced section (stored as 5) spans
  /// HSK 5 and 6, so it shows as "5-6".
  String get levelLabel => level == 5 ? '5-6' : '$level';

  factory GrammarTopic.fromJson(Map<String, dynamic> json) {
    return GrammarTopic(
      id: json['id'] as String,
      title: json['title'] as String,
      titleEn: json['titleEn'] as String? ?? '',
      level: json['level'] as int,
      pattern: json['pattern'] as String? ?? '',
      summary: json['summary'] as String,
      explanation: json['explanation'] as String,
      examples: (json['examples'] as List<dynamic>)
          .map((e) => GrammarExample.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}
