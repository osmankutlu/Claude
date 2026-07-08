import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:zh_en_dict/utils/pinyin_tone.dart';

void main() {
  test('tone coloring reconstructs every pinyin string in the dataset', () {
    final words = jsonDecode(File('assets/data/words.json').readAsStringSync()) as List<dynamic>;
    final grammar = jsonDecode(File('assets/data/grammar.json').readAsStringSync()) as List<dynamic>;

    var checked = 0;
    final mismatches = <String>[];
    final suspicious = <String>[];

    void check(String pinyin, String context) {
      checked++;
      final spans = PinyinTone.spans(pinyin, baseStyle: const TextStyle());
      final reconstructed = spans.whereType<TextSpan>().map((s) => s.text).join();
      if (reconstructed != pinyin) {
        mismatches.add('$context: "$pinyin" -> "$reconstructed"');
      }
      final letterRun = RegExp(r'^[a-zA-ZüÜāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]+$');
      for (final span in spans.whereType<TextSpan>()) {
        final t = span.text!;
        if (t.length == 1 && letterRun.hasMatch(t) && !['a', 'e', 'o', 'n', 'm'].contains(t.toLowerCase())) {
          suspicious.add('$context ("$pinyin"): suspicious single-char syllable "$t"');
        }
      }
    }

    for (final w in words) {
      check(w['pinyin'] as String, 'word ${w['id']} ${w['hanzi']}');
      for (final ex in (w['examples'] as List<dynamic>)) {
        check(ex['pinyin'] as String, 'word ${w['id']} example');
      }
    }

    for (final g in grammar) {
      for (final ex in (g['examples'] as List<dynamic>)) {
        check(ex['pinyin'] as String, 'grammar ${g['id']} example');
      }
    }

    // ignore: avoid_print
    print('Checked $checked pinyin strings.');
    if (mismatches.isNotEmpty) {
      // ignore: avoid_print
      print('${mismatches.length} MISMATCHES:\n${mismatches.take(30).join('\n')}');
    }
    if (suspicious.isNotEmpty) {
      // ignore: avoid_print
      print('${suspicious.length} SUSPICIOUS:\n${suspicious.take(30).join('\n')}');
    }

    expect(mismatches, isEmpty);
  });
}
