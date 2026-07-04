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
  final String title;
  final int level;
  final String summary;
  final String explanation;
  final List<GrammarExample> examples;

  const GrammarTopic({
    required this.id,
    required this.title,
    required this.level,
    required this.summary,
    required this.explanation,
    required this.examples,
  });

  factory GrammarTopic.fromJson(Map<String, dynamic> json) {
    return GrammarTopic(
      id: json['id'] as String,
      title: json['title'] as String,
      level: json['level'] as int,
      summary: json['summary'] as String,
      explanation: json['explanation'] as String,
      examples: (json['examples'] as List<dynamic>)
          .map((e) => GrammarExample.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}
