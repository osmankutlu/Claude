import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../data/dictionary_repository.dart';
import '../models/word.dart';
import '../widgets/word_dialog.dart';
import 'pinyin_tone.dart';

/// Chinese text with per-character tone coloring (same palette as
/// [PinyinTone]) and, optionally, tap-to-look-up on every dictionary word
/// found in it.
///
/// Tone coloring works by pairing the text with its [pinyin]: the Nth CJK
/// character takes the Nth pinyin syllable's tone color. If the two can't
/// be aligned (missing pinyin, syllable count mismatch), characters keep
/// the base color rather than risking wrong colors.
///
/// With [tappableWords], the text is segmented greedily (longest dictionary
/// match first) and each matched word becomes tappable, opening that word
/// in a popup dialog (or calling [onWordTap] if given) — so unknown words
/// inside example sentences can be looked up and favorited in place.
class ChineseText extends StatefulWidget {
  final String text;
  final String? pinyin;
  final TextStyle? style;
  final TextAlign? textAlign;
  final bool tappableWords;
  final void Function(Word word)? onWordTap;

  const ChineseText(
    this.text, {
    super.key,
    this.pinyin,
    this.style,
    this.textAlign,
    this.tappableWords = false,
    this.onWordTap,
  });

  @override
  State<ChineseText> createState() => _ChineseTextState();
}

class _ChineseTextState extends State<ChineseText> {
  final List<TapGestureRecognizer> _recognizers = [];
  Map<String, Word>? _index;
  int _maxLen = 1;

  @override
  void initState() {
    super.initState();
    if (widget.tappableWords) _loadIndex();
  }

  Future<void> _loadIndex() async {
    final repo = context.read<DictionaryRepository>();
    final cached = repo.hanziIndexOrNull;
    if (cached != null) {
      _index = cached;
      _maxLen = repo.maxHanziLength;
      return;
    }
    final index = await repo.hanziIndex();
    if (mounted) {
      setState(() {
        _index = index;
        _maxLen = repo.maxHanziLength;
      });
    }
  }

  @override
  void dispose() {
    for (final r in _recognizers) {
      r.dispose();
    }
    super.dispose();
  }

  static bool _isCjk(int codeUnit) =>
      (codeUnit >= 0x4E00 && codeUnit <= 0x9FFF) ||
      (codeUnit >= 0x3400 && codeUnit <= 0x4DBF);

  /// Color per character of [widget.text], aligned against the pinyin's
  /// syllables; null entries mean "use the base color".
  List<Color?> _charColors() {
    final chars = widget.text.characters.toList();
    final result = List<Color?>.filled(chars.length, null);
    final pinyin = widget.pinyin;
    if (pinyin == null || pinyin.isEmpty) return result;

    final syllables = PinyinTone.syllables(pinyin);
    var s = 0;
    var lastWasErhua = false;
    Color? lastColor;
    for (var i = 0; i < chars.length; i++) {
      final c = chars[i];
      if (c.length != 1 || !_isCjk(c.codeUnitAt(0))) continue;

      // 儿化: a syllable like "wánr" spans two characters (玩 + 儿), so 儿
      // reuses the previous syllable's color instead of consuming a new one.
      if (c == '儿' && lastWasErhua) {
        result[i] = lastColor;
        lastWasErhua = false;
        continue;
      }

      if (s >= syllables.length) break; // misaligned — leave the rest uncolored
      final (text, tone) = syllables[s];
      result[i] = PinyinTone.colors[tone];
      lastColor = result[i];
      final plain = text.toLowerCase();
      lastWasErhua = plain.length > 1 && plain.endsWith('r') && plain != 'er';
      s++;
    }
    return result;
  }

  /// Greedy longest-match segmentation into (text, Word?) runs.
  List<(String, Word?)> _segments() {
    final chars = widget.text.characters.toList();
    final index = _index;
    if (!widget.tappableWords || index == null) {
      return [(widget.text, null)];
    }

    final result = <(String, Word?)>[];
    final plain = StringBuffer();
    var i = 0;
    while (i < chars.length) {
      Word? matched;
      var matchedLen = 0;
      final maxLen = _maxLen.clamp(1, chars.length - i);
      for (var len = maxLen; len >= 1; len--) {
        final candidate = chars.sublist(i, i + len).join();
        final word = index[candidate];
        if (word != null) {
          matched = word;
          matchedLen = len;
          break;
        }
      }
      if (matched != null) {
        if (plain.isNotEmpty) {
          result.add((plain.toString(), null));
          plain.clear();
        }
        result.add((chars.sublist(i, i + matchedLen).join(), matched));
        i += matchedLen;
      } else {
        plain.write(chars[i]);
        i++;
      }
    }
    if (plain.isNotEmpty) result.add((plain.toString(), null));
    return result;
  }

  void _handleTap(Word word) {
    final onTap = widget.onWordTap;
    if (onTap != null) {
      onTap(word);
    } else {
      showWordDialog(context, word);
    }
  }

  @override
  Widget build(BuildContext context) {
    final baseStyle = widget.style ?? DefaultTextStyle.of(context).style;
    final colors = _charColors();

    for (final r in _recognizers) {
      r.dispose();
    }
    _recognizers.clear();

    final spans = <InlineSpan>[];
    var charPos = 0;
    for (final (segText, word) in _segments()) {
      final segChars = segText.characters.toList();
      TapGestureRecognizer? recognizer;
      if (word != null) {
        recognizer = TapGestureRecognizer()..onTap = () => _handleTap(word);
        _recognizers.add(recognizer);
      }
      // One span per character so each keeps its own tone color; the whole
      // word's spans share one recognizer so tapping anywhere on it works.
      for (var k = 0; k < segChars.length; k++) {
        spans.add(TextSpan(
          text: segChars[k],
          style: baseStyle.copyWith(color: colors[charPos + k] ?? baseStyle.color),
          recognizer: recognizer,
        ));
      }
      charPos += segChars.length;
    }

    return RichText(
      textAlign: widget.textAlign ?? TextAlign.start,
      text: TextSpan(children: spans),
    );
  }
}
