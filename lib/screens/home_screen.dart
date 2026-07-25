import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../data/favorites_repository.dart';
import '../data/grammar_progress_repository.dart';
import 'dictionary_screen.dart';
import 'favorites_screen.dart';
import 'flashcards_screen.dart';
import 'grammar_list_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _tab = 0;

  static const _screens = [
    DictionaryScreen(),
    FlashcardsScreen(),
    GrammarListScreen(),
    FavoritesScreen(),
  ];

  @override
  void initState() {
    super.initState();
    context.read<FavoritesRepository>().load();
    context.read<GrammarProgressRepository>().load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _tab, children: _screens),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.menu_book), label: 'Sözlük'),
          NavigationDestination(icon: Icon(Icons.style), label: 'Flashcard'),
          NavigationDestination(icon: Icon(Icons.school), label: 'Gramer'),
          NavigationDestination(icon: Icon(Icons.star), label: 'Favoriler'),
        ],
      ),
    );
  }
}
