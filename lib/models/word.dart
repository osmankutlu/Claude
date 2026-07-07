class WordExample {
  final String zh;
  final String pinyin;
  final String en;

  const WordExample({required this.zh, required this.pinyin, required this.en});

  factory WordExample.fromJson(Map<String, dynamic> json) {
    return WordExample(
      zh: json['zh'] as String,
      pinyin: json['pinyin'] as String,
      en: json['en'] as String,
    );
  }
}

class Word {
  final String id;
  final String hanzi;
  final String pinyin;
  final String meaning;
  final String partOfSpeech;
  final int level;
  final String category;
  final List<WordExample> examples;

  const Word({
    required this.id,
    required this.hanzi,
    required this.pinyin,
    required this.meaning,
    required this.partOfSpeech,
    required this.level,
    required this.category,
    required this.examples,
  });

  factory Word.fromJson(Map<String, dynamic> json) {
    return Word(
      id: json['id'] as String,
      hanzi: json['hanzi'] as String,
      pinyin: json['pinyin'] as String,
      meaning: json['meaning'] as String,
      partOfSpeech: json['partOfSpeech'] as String,
      level: json['level'] as int,
      category: json['category'] as String,
      examples: (json['examples'] as List<dynamic>)
          .map((e) => WordExample.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}
