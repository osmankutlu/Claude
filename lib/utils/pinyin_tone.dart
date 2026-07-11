import 'package:flutter/material.dart';

/// Colors pinyin text by tone: 1st=blue, 2nd=green, 3rd=yellow, 4th=red,
/// neutral=grey. Works by splitting each run of pinyin letters into
/// syllables (using a standard initials/finals table) and then reading the
/// tone off whichever vowel in that syllable carries a diacritic.
class PinyinTone {
  PinyinTone._();

  static const Map<int, Color> colors = {
    1: Color(0xFF1E88E5), // blue
    2: Color(0xFF43A047), // green
    3: Color(0xFFF9A825), // yellow (darkened for legibility)
    4: Color(0xFFE53935), // red
    5: Color(0xFF9E9E9E), // grey - neutral tone
  };

  static const Map<String, int> _toneOfCharLower = {
    'ā': 1, 'á': 2, 'ǎ': 3, 'à': 4,
    'ē': 1, 'é': 2, 'ě': 3, 'è': 4,
    'ī': 1, 'í': 2, 'ǐ': 3, 'ì': 4,
    'ō': 1, 'ó': 2, 'ǒ': 3, 'ò': 4,
    'ū': 1, 'ú': 2, 'ǔ': 3, 'ù': 4,
    'ǖ': 1, 'ǘ': 2, 'ǚ': 3, 'ǜ': 4,
  };

  static const Map<String, String> _plainOfCharLower = {
    'ā': 'a', 'á': 'a', 'ǎ': 'a', 'à': 'a',
    'ē': 'e', 'é': 'e', 'ě': 'e', 'è': 'e',
    'ī': 'i', 'í': 'i', 'ǐ': 'i', 'ì': 'i',
    'ō': 'o', 'ó': 'o', 'ǒ': 'o', 'ò': 'o',
    'ū': 'u', 'ú': 'u', 'ǔ': 'u', 'ù': 'u',
    'ǖ': 'v', 'ǘ': 'v', 'ǚ': 'v', 'ǜ': 'v',
    'ü': 'v', 'Ü': 'v',
  };

  // Sentence-initial pinyin syllables are often capitalized (e.g. "Àiqíng
  // hěn zhòngyào."), so every toned vowel needs an uppercase counterpart too.
  static final Map<String, int> _toneOfChar = {
    ..._toneOfCharLower,
    for (final entry in _toneOfCharLower.entries) entry.key.toUpperCase(): entry.value,
  };

  static final Map<String, String> _plainOfChar = {
    ..._plainOfCharLower,
    for (final entry in _plainOfCharLower.entries) entry.key.toUpperCase(): entry.value,
  };

  /// Replaces toned vowels with their plain ASCII form (ǚ→v, é→e), leaving
  /// other characters untouched. Note ü/ǚ map to "v".
  static String stripTones(String s) =>
      s.split('').map((c) => _plainOfChar[c] ?? c).join();

  static final List<String> _initials = (<String>[
    'zh', 'ch', 'sh',
    'b', 'p', 'm', 'f', 'd', 't', 'n', 'l',
    'g', 'k', 'h', 'j', 'q', 'x', 'r', 'z', 'c', 's', 'y', 'w',
  ])..sort((a, b) => b.length.compareTo(a.length));

  static final List<String> _finals = (<String>[
    'iang', 'iong', 'uang', 'ueng',
    'ian', 'iao', 'ing', 'ong', 'uai', 'uan', 'van',
    'ai', 'ei', 'ao', 'ou', 'an', 'en', 'ang', 'eng', 'er', 'ue',
    'ia', 'ie', 'iu', 'in', 'ua', 'uo', 'ui', 'un', 've',
    'a', 'o', 'e', 'i', 'u', 'v',
  ])..sort((a, b) => b.length.compareTo(a.length));

  static bool _isPinyinLetter(String c) {
    if (c.isEmpty) return false;
    final code = c.codeUnitAt(0);
    final isAscii = (code >= 65 && code <= 90) || (code >= 97 && code <= 122);
    return isAscii || _toneOfChar.containsKey(c) || _plainOfChar.containsKey(c);
  }

  static String _plain(String c) {
    if (_plainOfChar.containsKey(c)) return _plainOfChar[c]!;
    return c.toLowerCase();
  }

  /// Splits one contiguous run of pinyin letters (already known to contain
  /// no spaces/punctuation) into (syllableText, toneNumber) pairs.
  static List<(String, int)> _splitRun(String run) {
    final plain = run.split('').map(_plain).join();
    final result = <(String, int)>[];
    var pos = 0;

    while (pos < run.length) {
      String matchedInitial = '';
      for (final init in _initials) {
        if (plain.startsWith(init, pos)) {
          matchedInitial = init;
          break;
        }
      }
      final afterInitial = pos + matchedInitial.length;

      String matchedFinal = '';
      for (final fin in _finals) {
        if (plain.startsWith(fin, afterInitial)) {
          matchedFinal = fin;
          break;
        }
      }

      if (matchedFinal.isEmpty) {
        final chunk = run.substring(pos, pos + 1);
        // A lone trailing "r" after a full syllable is the 儿化 (erhua)
        // suffix (e.g. "nǎr", "wánr") — it's part of the previous
        // syllable's sound, not a syllable of its own, so keep it attached
        // rather than coloring it separately as a fake neutral-tone blip.
        if ((chunk == 'r' || chunk == 'R') && result.isNotEmpty) {
          final last = result.removeLast();
          result.add(('${last.$1}$chunk', last.$2));
        } else {
          // Unexpected input we couldn't segment — fall back to a single
          // character so we always make forward progress.
          result.add((chunk, _toneOf(chunk)));
        }
        pos += 1;
        continue;
      }

      final end = afterInitial + matchedFinal.length;
      final syllable = run.substring(pos, end);
      result.add((syllable, _toneOf(syllable)));
      pos = end;
    }

    return result;
  }

  static int _toneOf(String syllable) {
    for (final c in syllable.split('')) {
      final tone = _toneOfChar[c];
      if (tone != null) return tone;
    }
    return 5; // neutral
  }

  /// Flat, in-order list of every syllable in [pinyin] as
  /// (syllableText, tone) pairs, skipping spaces/punctuation. Used to
  /// tone-color hanzi: the Nth CJK character of the paired Chinese text
  /// corresponds to the Nth syllable here (with 儿化 handled by the caller,
  /// since an erhua syllable like "wánr" covers two characters: 玩儿).
  static List<(String, int)> syllables(String pinyin) {
    final result = <(String, int)>[];
    var i = 0;
    while (i < pinyin.length) {
      if (_isPinyinLetter(pinyin[i])) {
        var j = i + 1;
        while (j < pinyin.length && _isPinyinLetter(pinyin[j])) {
          j++;
        }
        result.addAll(_splitRun(pinyin.substring(i, j)));
        i = j;
      } else {
        i++;
      }
    }
    return result;
  }

  /// Builds a list of [TextSpan]s for [pinyin], coloring each syllable by
  /// its tone while leaving spaces/punctuation in the default color.
  static List<InlineSpan> spans(String pinyin, {required TextStyle baseStyle}) {
    final spans = <InlineSpan>[];
    var i = 0;
    while (i < pinyin.length) {
      final c = pinyin[i];
      if (_isPinyinLetter(c)) {
        var j = i + 1;
        while (j < pinyin.length && _isPinyinLetter(pinyin[j])) {
          j++;
        }
        final run = pinyin.substring(i, j);
        for (final (syllable, tone) in _splitRun(run)) {
          spans.add(TextSpan(text: syllable, style: baseStyle.copyWith(color: colors[tone])));
        }
        i = j;
      } else {
        var j = i + 1;
        while (j < pinyin.length && !_isPinyinLetter(pinyin[j])) {
          j++;
        }
        spans.add(TextSpan(text: pinyin.substring(i, j), style: baseStyle));
        i = j;
      }
    }
    return spans;
  }
}

/// Convenience widget: renders [pinyin] with per-syllable tone coloring.
class TonedPinyinText extends StatelessWidget {
  final String pinyin;
  final TextStyle? style;
  final TextAlign? textAlign;

  const TonedPinyinText(this.pinyin, {super.key, this.style, this.textAlign});

  @override
  Widget build(BuildContext context) {
    final baseStyle = style ?? DefaultTextStyle.of(context).style;
    return RichText(
      textAlign: textAlign ?? TextAlign.start,
      text: TextSpan(children: PinyinTone.spans(pinyin, baseStyle: baseStyle)),
    );
  }
}
