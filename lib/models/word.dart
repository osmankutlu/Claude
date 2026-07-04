class Word {
  final String id;
  final String hanzi;
  final String pinyin;
  final String meaning;
  final String partOfSpeech;
  final int level;
  final String category;
  final String exampleZh;
  final String examplePinyin;
  final String exampleEn;

  const Word({
    required this.id,
    required this.hanzi,
    required this.pinyin,
    required this.meaning,
    required this.partOfSpeech,
    required this.level,
    required this.category,
    required this.exampleZh,
    required this.examplePinyin,
    required this.exampleEn,
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
      exampleZh: json['exampleZh'] as String,
      examplePinyin: json['examplePinyin'] as String,
      exampleEn: json['exampleEn'] as String,
    );
  }
}
