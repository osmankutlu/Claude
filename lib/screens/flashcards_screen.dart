import 'dart:math';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../data/dictionary_repository.dart';
import '../data/progress_repository.dart';
import '../models/word.dart';
import '../services/tts_service.dart';
import '../utils/pinyin_tone.dart';

class FlashcardsScreen extends StatefulWidget {
  const FlashcardsScreen({super.key});

  @override
  State<FlashcardsScreen> createState() => _FlashcardsScreenState();
}

class _FlashcardsScreenState extends State<FlashcardsScreen> {
  final _progressRepo = ProgressRepository();

  List<Word> _deck = [];
  Map<String, int> _boxes = {};
  int _index = 0;
  bool _showBack = false;
  int _correct = 0;
  int _wrong = 0;
  bool _loading = true;
  bool _finished = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final words = await context.read<DictionaryRepository>().loadWords();
    final boxes = await _progressRepo.loadBoxes();
    final deck = List<Word>.from(words)..shuffle(Random());
    // Weakest words (lowest box) first, so the session focuses on what you don't know yet.
    deck.sort((a, b) => (boxes[a.id] ?? 0).compareTo(boxes[b.id] ?? 0));

    setState(() {
      _deck = deck;
      _boxes = boxes;
      _index = 0;
      _showBack = false;
      _correct = 0;
      _wrong = 0;
      _finished = false;
      _loading = false;
    });
  }

  void _answer(bool known) {
    final word = _deck[_index];
    final currentBox = _boxes[word.id] ?? 0;
    _boxes[word.id] = known ? min(currentBox + 1, 4) : 0;
    _progressRepo.saveBoxes(_boxes);

    setState(() {
      if (known) {
        _correct++;
      } else {
        _wrong++;
      }
      if (_index + 1 >= _deck.length) {
        _finished = true;
      } else {
        _index++;
        _showBack = false;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Flashcard Çalışma')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _finished
              ? _buildSummary()
              : _buildCard(),
    );
  }

  Widget _buildSummary() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.emoji_events, size: 64, color: Colors.amber),
            const SizedBox(height: 16),
            const Text('Oturum tamamlandı!', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            Text('Doğru: $_correct   •   Tekrar gerekiyor: $_wrong'),
            const SizedBox(height: 24),
            FilledButton.icon(
              onPressed: () {
                setState(() => _loading = true);
                _load();
              },
              icon: const Icon(Icons.refresh),
              label: const Text('Yeniden Başla'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCard() {
    final word = _deck[_index];
    final tts = context.read<TtsService>();
    final progress = (_index) / _deck.length;

    return Padding(
      padding: const EdgeInsets.all(20),
      child: Column(
        children: [
          LinearProgressIndicator(value: progress),
          const SizedBox(height: 8),
          Text('${_index + 1} / ${_deck.length}'),
          const SizedBox(height: 20),
          Expanded(
            child: GestureDetector(
              onTap: () => setState(() => _showBack = !_showBack),
              child: Card(
                elevation: 4,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(24),
                  child: _showBack ? _buildBack(word, tts) : _buildFront(word, tts),
                ),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            _showBack ? 'Değerlendir:' : 'Çevirmek için karta dokun',
            style: const TextStyle(color: Colors.grey),
          ),
          const SizedBox(height: 12),
          if (_showBack)
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => _answer(false),
                    icon: const Icon(Icons.close, color: Colors.red),
                    label: const Text('Bilmiyorum'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: () => _answer(true),
                    icon: const Icon(Icons.check),
                    label: const Text('Biliyorum'),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }

  Widget _buildFront(Word word, TtsService tts) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(word.hanzi, style: const TextStyle(fontSize: 64, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          IconButton.filledTonal(
            onPressed: () => tts.speak(word.hanzi),
            icon: const Icon(Icons.volume_up),
          ),
        ],
      ),
    );
  }

  Widget _buildBack(Word word, TtsService tts) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(word.hanzi, style: const TextStyle(fontSize: 36, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          TonedPinyinText(word.pinyin, style: const TextStyle(fontSize: 20)),
          const SizedBox(height: 12),
          Text(word.meaning, style: const TextStyle(fontSize: 22)),
          if (word.examples.isNotEmpty) ...[
            const SizedBox(height: 16),
            Text(word.examples.first.zh, textAlign: TextAlign.center),
            TonedPinyinText(word.examples.first.pinyin, textAlign: TextAlign.center),
            Text(word.examples.first.en, style: const TextStyle(fontStyle: FontStyle.italic), textAlign: TextAlign.center),
          ],
        ],
      ),
    );
  }
}
