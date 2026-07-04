import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'data/dictionary_repository.dart';
import 'data/grammar_repository.dart';
import 'screens/home_screen.dart';
import 'services/tts_service.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        Provider(create: (_) => DictionaryRepository()),
        Provider(create: (_) => GrammarRepository()),
        Provider(create: (_) => TtsService()),
      ],
      child: MaterialApp(
        title: '中文词典 Çince Sözlük',
        theme: ThemeData(
          colorSchemeSeed: Colors.red,
          useMaterial3: true,
          fontFamily: 'NotoSansSC',
        ),
        home: const HomeScreen(),
      ),
    );
  }
}
