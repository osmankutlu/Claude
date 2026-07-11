import 'package:flutter/material.dart';

/// A highlighted box showing a grammar point's structural pattern, e.g.
/// "Subj. + 把 + Obj. + Verb + 了". The Chinese pieces (the actual grammar
/// words) are emphasized in the accent color so they stand out from the
/// English placeholders like "Subj." / "Verb".
class PatternBox extends StatelessWidget {
  final String pattern;

  const PatternBox(this.pattern, {super.key});

  static bool _isCjk(int c) =>
      (c >= 0x4E00 && c <= 0x9FFF) ||
      (c >= 0x3400 && c <= 0x4DBF) ||
      (c >= 0xFF00 && c <= 0xFFEF); // full-width punctuation (，？ etc.)

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final base = TextStyle(
      fontSize: 16,
      height: 1.4,
      color: scheme.onSurfaceVariant,
      fontWeight: FontWeight.w500,
    );

    // Split into runs of Chinese vs. non-Chinese so the Chinese grammar
    // words can be colored/bolded distinctly.
    final spans = <TextSpan>[];
    final buf = StringBuffer();
    bool? bufCjk;
    void flush() {
      if (buf.isEmpty) return;
      final isCjk = bufCjk == true;
      spans.add(TextSpan(
        text: buf.toString(),
        style: isCjk
            ? base.copyWith(color: scheme.primary, fontWeight: FontWeight.bold)
            : base,
      ));
      buf.clear();
    }

    for (final ch in pattern.characters) {
      final cjk = ch.length == 1 && _isCjk(ch.codeUnitAt(0));
      if (bufCjk != null && cjk != bufCjk) flush();
      bufCjk = cjk;
      buf.write(ch);
    }
    flush();

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: scheme.outlineVariant),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(right: 10, top: 2),
            child: Text('KALIP',
                style: TextStyle(
                    fontSize: 11, fontWeight: FontWeight.bold, color: scheme.outline)),
          ),
          Expanded(child: Text.rich(TextSpan(children: spans))),
        ],
      ),
    );
  }
}
