import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import 'data/dictionary_repository.dart';
import 'data/favorites_repository.dart';
import 'data/grammar_repository.dart';
import 'screens/home_screen.dart';
import 'screens/widget_configure_screen.dart';
import 'screens/word_detail_screen.dart';
import 'services/tts_service.dart';

/// Lets [MyApp] push routes (e.g. a word detail screen opened from a widget
/// tap) without needing a BuildContext from deep inside the widget tree.
final navigatorKey = GlobalKey<NavigatorState>();

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  final initialRoute = WidgetsBinding.instance.platformDispatcher.defaultRouteName;
  runApp(MyApp(initialRoute: initialRoute));
}

class MyApp extends StatefulWidget {
  final String initialRoute;

  const MyApp({super.key, required this.initialRoute});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  static const _deepLinkChannel = MethodChannel('com.osmankutlu.zh_en_dict/deep_link');
  final _dictionaryRepo = DictionaryRepository();

  @override
  void initState() {
    super.initState();
    // WidgetConfigureActivity runs this same Dart entrypoint in its own
    // Flutter engine, which never registers this channel natively — skip
    // wiring it up there to avoid a MissingPluginException.
    if (widget.initialRoute == '/widgetConfigure') return;

    _deepLinkChannel.setMethodCallHandler((call) async {
      if (call.method == 'openWord') {
        await _openWord(call.arguments as String);
      }
    });
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final pendingWordId = await _deepLinkChannel.invokeMethod<String>('getPendingWordId');
      if (pendingWordId != null) await _openWord(pendingWordId);
    });
  }

  Future<void> _openWord(String id) async {
    final word = await _dictionaryRepo.findById(id);
    final nav = navigatorKey.currentState;
    if (word == null || nav == null) return;
    nav.push(MaterialPageRoute(builder: (_) => WordDetailScreen(word: word)));
  }

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        Provider.value(value: _dictionaryRepo),
        Provider(create: (_) => GrammarRepository()),
        Provider(create: (_) => TtsService()),
        ChangeNotifierProvider(create: (_) => FavoritesRepository()),
      ],
      child: MaterialApp(
        navigatorKey: navigatorKey,
        title: '中文词典 Çince Sözlük',
        theme: ThemeData(
          colorSchemeSeed: Colors.red,
          useMaterial3: true,
          fontFamily: 'NotoSansSC',
        ),
        initialRoute: widget.initialRoute,
        routes: {
          '/': (_) => const HomeScreen(),
          '/widgetConfigure': (_) => const WidgetConfigureScreen(),
        },
      ),
    );
  }
}
