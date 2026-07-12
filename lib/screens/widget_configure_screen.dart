import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:home_widget/home_widget.dart';
import 'package:provider/provider.dart';

import '../data/dictionary_repository.dart';
import '../data/grammar_repository.dart';

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
  String _textScale = 'large'; // 'small' | 'medium' | 'large' | 'xlarge'
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
    final levelSpec = _levels.isEmpty ? '0' : (_levels.toList()..sort()).join(',');

    // The widget's card stack is built natively from mode+level+display, so
    // this only needs to check the combination isn't empty before saving —
    // nothing here needs to actually pick an item.
    final bool hasAny;
    if (_mode == 'grammar') {
      final topics = await context.read<GrammarRepository>().loadTopics();
      hasAny = topics.any((t) => _matchesLevel(t.level));
    } else {
      final words = await context.read<DictionaryRepository>().loadWords();
      hasAny = words.any((w) => _matchesLevel(w.level));
    }
    if (!hasAny) {
      setState(() => _saving = false);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(_mode == 'grammar'
                ? 'Seçili seviyelerde gramer konusu yok. Farklı bir seviye seç.'
                : 'Seçili seviyelerde kelime yok. Farklı bir seviye seç.'),
          ),
        );
      }
      return;
    }

    await HomeWidget.saveWidgetData('widget_mode_$id', _mode);
    await HomeWidget.saveWidgetData('widget_level_$id', levelSpec);
    await HomeWidget.saveWidgetData('widget_display_$id', _display);
    await HomeWidget.saveWidgetData('widget_textscale_$id', _textScale);
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
                label: Text(lvl == 5 ? 'HSK5' : 'HSK$lvl'),
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
          const SizedBox(height: 28),
          const Text('Yazı Boyutu', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          const SizedBox(height: 4),
          const Text(
            'Widget küçükse "Küçük", büyük ve okunaklı istiyorsan "Çok Büyük" seç.',
            style: TextStyle(fontSize: 12, color: Colors.grey),
          ),
          const SizedBox(height: 12),
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(value: 'small', label: Text('Küçük')),
              ButtonSegment(value: 'medium', label: Text('Orta')),
              ButtonSegment(value: 'large', label: Text('Büyük')),
              ButtonSegment(value: 'xlarge', label: Text('Çok Büyük')),
            ],
            selected: {_textScale},
            onSelectionChanged: (s) => setState(() => _textScale = s.first),
          ),
          const SizedBox(height: 24),
          const Text(
            "İpucu: Widget'taki oklarla kelime/konu değiştirebilir, ortasına dokunarak anlamını ve örneklerini açabilirsin. Karakterler tonlarına göre renklidir (1. ton mavi, 2. yeşil, 3. sarı, 4. kırmızı, hafif ton gri).",
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
