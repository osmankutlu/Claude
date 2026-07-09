import 'package:flutter_test/flutter_test.dart';
import 'package:zh_en_dict/utils/pinyin_tone.dart';

void main() {
  test('syllables: one entry per hanzi character for a plain sentence', () {
    // 他是我阿姨。 = 5 hanzi characters
    final syllables = PinyinTone.syllables('Tā shì wǒ āyí.');
    expect(syllables.length, 5);
    expect(syllables.map((s) => s.$2).toList(), [1, 4, 3, 1, 2]);
  });

  test('syllables: erhua keeps the trailing r attached (one syllable for 玩儿)', () {
    final syllables = PinyinTone.syllables('wánr');
    expect(syllables.length, 1);
    expect(syllables.single.$1, 'wánr');
    expect(syllables.single.$2, 2);
  });

  test('syllables: neutral tone reported as 5', () {
    final syllables = PinyinTone.syllables('māma');
    expect(syllables.length, 2);
    expect(syllables[0].$2, 1);
    expect(syllables[1].$2, 5);
  });
}
