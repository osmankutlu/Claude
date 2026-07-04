import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

/// Tracks a simple Leitner-style "box" (0-4) per word id.
/// Box 0 = just missed / never seen, box 4 = well known.
class ProgressRepository {
  static const _storageKey = 'flashcard_boxes';

  Future<Map<String, int>> loadBoxes() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_storageKey);
    if (raw == null) return {};
    final map = jsonDecode(raw) as Map<String, dynamic>;
    return map.map((key, value) => MapEntry(key, value as int));
  }

  Future<void> saveBoxes(Map<String, int> boxes) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_storageKey, jsonEncode(boxes));
  }
}
