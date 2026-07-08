import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:zh_en_dict/utils/pinyin_tone.dart';

void main() {
  List<(String, int)> tonesOf(String pinyin) {
    final spans = PinyinTone.spans(pinyin, baseStyle: const TextStyle());
    return spans
        .whereType<TextSpan>()
        .map((s) => (s.text!, PinyinTone.colors.entries.firstWhere((e) => e.value == s.style!.color).key))
        .toList();
  }

  String reconstruct(String pinyin) {
    final spans = PinyinTone.spans(pinyin, baseStyle: const TextStyle());
    return spans.whereType<TextSpan>().map((s) => s.text).join();
  }

  test('reconstructs original text exactly', () {
    for (final s in [
      'bàba',
      'gōngzuò',
      'wǒmen',
      'xǐhuan',
      'Zhōngguó',
      'Wǒ shì xuésheng.',
      'Tā gēn wǒ yìqǐ qù.',
      'lǜshī',
      'nǚ\'ér',
    ]) {
      expect(reconstruct(s), s, reason: 'failed to reconstruct "$s"');
    }
  });

  test('splits multi-syllable words into the right syllables with correct tones', () {
    expect(tonesOf('bàba'), [('bà', 4), ('ba', 5)]);
    expect(tonesOf('gōngzuò'), [('gōng', 1), ('zuò', 4)]);
    expect(tonesOf('wǒmen'), [('wǒ', 3), ('men', 5)]);
    expect(tonesOf('xǐhuan'), [('xǐ', 3), ('huan', 5)]);
    expect(tonesOf('Zhōngguó'), [('Zhōng', 1), ('guó', 2)]);
    expect(tonesOf('lǜshī'), [('lǜ', 4), ('shī', 1)]);
  });
}
