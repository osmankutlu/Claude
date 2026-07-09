import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'data/dictionary_repository.dart';
import 'data/favorites_repository.dart';
import 'data/grammar_repository.dart';
import 'screens/home_screen.dart';
import 'screens/widget_configure_screen.dart';
import 'screens/word_popup_screen.dart';
import 'services/tts_service.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  final initialRoute = WidgetsBinding.instance.platformDispatcher.defaultRouteName;
  runApp(MyApp(initialRoute: initialRoute));
}

class MyApp extends StatelessWidget {
  final String initialRoute;

  const MyApp({super.key, required this.initialRoute});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        Provider(create: (_) => DictionaryRepository()),
        Provider(create: (_) => GrammarRepository()),
        Provider(create: (_) => TtsService()),
        ChangeNotifierProvider(create: (_) => FavoritesRepository()),
      ],
      child: MaterialApp(
        title: '中文词典 Çince Sözlük',
        theme: ThemeData(
          colorSchemeSeed: Colors.red,
          useMaterial3: true,
          fontFamily: 'NotoSansSC',
        ),
        initialRoute: initialRoute,
        routes: {
          '/': (_) => const HomeScreen(),
          '/widgetConfigure': (_) => const WidgetConfigureScreen(),
          '/wordPopup': (_) => const WordPopupScreen(),
        },
      ),
    );
  }
}
