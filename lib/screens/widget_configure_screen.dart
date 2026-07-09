import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:home_widget/home_widget.dart';
import 'package:provider/provider.dart';

import '../data/dictionary_repository.dart';
import '../data/grammar_repository.dart';
import '../models/word.dart';

/// Shown by [WidgetConfigureActivity] (native) when the user drags the
/// widget onto their home screen. Lets them pick word/grammar mode, one or
/// more HSK levels to mix together, and a display mode, then hands control
/// back to the native activity so it can finish with RESULT_OK and let the
/// widget actually get placed.
class WidgetConfigureScreen extends StatefulWidget {
  const WidgetConfigureScreen({super.key});

  @override
  State<WidgetConfigureScreen> createState() => _WidgetConfigureScreenState();
}

class _WidgetConfigureScreenState extends State<WidgetConfigureScreen> {
  static const _channel = MethodChannel('com.osmankutlu.zh_en_dict/widget_configure');

  String _mode = 'word';
  final Set<int> _levels = {}; // empty means "all levels"
  String _display = 'both'; // 'both' | 'hanzi' | 'pinyin'
  int? _appWidgetId;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _loadWidgetId();
  }

  Future<void> _loadWidgetId() async {
    final id = await _channel.invokeMethod<int>('getAppWidgetId');
    if (mounted) setState(() => _appWidgetId = id);
  }

  bool _matchesLevel(int level) => _levels.isEmpty || _levels.contains(level);

  Future<void> _save() async {
    if (_appWidgetId == null || _saving) return;
    setState(() => _saving = true);

    final id = _appWidgetId!;
    final grammarRepo = context.read<GrammarRepository>();
    final dictionaryRepo = context.read<DictionaryRepository>();
    final levelSpec = _levels.isEmpty ? '0' : (_levels.toList()..sort()).join(',');
    await HomeWidget.saveWidgetData('widget_mode_$id', _mode);
    await HomeWidget.saveWidgetData('widget_level_$id', levelSpec);
    await HomeWidget.saveWidgetData('widget_display_$id', _display);

    final random = Random();
    final item = <String, dynamic>{'mode': _mode};

    if (_mode == 'grammar') {
      final topics = await grammarRepo.loadTopics();
      final filtered = topics.where((t) => _matchesLevel(t.level)).toList();
      final picked = filtered[random.nextInt(filtered.length)];
      item.addAll({
        'itemId': picked.id,
        'title': picked.title,
        'summary': picked.summary,
        'level': picked.level,
      });
    } else {
      final words = await dictionaryRepo.loadWords();
      final filtered = words.where((w) => _matchesLevel(w.level)).toList();
      final Word picked = filtered[random.nextInt(filtered.length)];
      item.addAll({
        'itemId': picked.id,
        'hanzi': picked.hanzi,
        'pinyin': picked.pinyin,
        'meaning': picked.meaning,
        'level': picked.level,
        'examples': picked.examples
            .map((e) => {'zh': e.zh, 'pinyin': e.pinyin, 'en': e.en})
            .toList(),
        'exampleIndex': 0,
      });
    }

    await HomeWidget.saveWidgetData('widget_item_$id', jsonEncode(item));
    await HomeWidget.updateWidget(androidName: 'WordWidgetProvider');
    await _channel.invokeMethod('finishConfigure');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Widget Ayarları')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text('Widget ne göstersin?', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          const SizedBox(height: 12),
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(value: 'word', label: Text('Kelime'), icon: Icon(Icons.menu_book)),
              ButtonSegment(value: 'grammar', label: Text('Gramer'), icon: Icon(Icons.school)),
            ],
            selected: {_mode},
            onSelectionChanged: (s) => setState(() => _mode = s.first),
          ),
          const SizedBox(height: 28),
          const Text('HSK Seviyesi', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          const SizedBox(height: 4),
          const Text(
            'Birden fazla seviye seçebilirsin, karışık gösterilir. Hiçbiri seçili değilse tüm seviyeler gösterilir.',
            style: TextStyle(fontSize: 12, color: Colors.grey),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [1, 2, 3, 4, 5].map((lvl) {
              return FilterChip(
                label: Text('HSK$lvl'),
                selected: _levels.contains(lvl),
                onSelected: (selected) => setState(() {
                  if (selected) {
                    _levels.add(lvl);
                  } else {
                    _levels.remove(lvl);
                  }
                }),
              );
            }).toList(),
          ),
          const SizedBox(height: 28),
          const Text('Gösterim', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          const SizedBox(height: 12),
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(value: 'both', label: Text('Hanzi + Pinyin')),
              ButtonSegment(value: 'hanzi', label: Text('Sadece Hanzi')),
              ButtonSegment(value: 'pinyin', label: Text('Sadece Pinyin')),
            ],
            selected: {_display},
            onSelectionChanged: (s) => setState(() => _display = s.first),
          ),
          const SizedBox(height: 24),
          const Text(
            "İpucu: Widget'ı ana ekrana ekledikten sonra köşelerinden sürükleyerek boyutunu değiştirebilirsin; içerik boyuta göre otomatik uyum sağlar.",
            style: TextStyle(fontSize: 12, color: Colors.grey),
          ),
          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            height: 48,
            child: FilledButton(
              onPressed: _appWidgetId == null || _saving ? null : _save,
              child: _saving
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : const Text("Widget'ı Ekle"),
            ),
          ),
        ],
      ),
    );
  }
}
