import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:zh_en_dict/main.dart';

void main() {
  testWidgets('App launches and shows the dictionary tab', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp(initialRoute: '/'));
    // Avoid pumpAndSettle: the loading spinners are indeterminate animations
    // that never "settle", so pump a fixed number of frames instead.
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }

    expect(find.text('Çince-Türkçe Sözlük'), findsOneWidget);
    expect(find.byIcon(Icons.menu_book), findsOneWidget);
    expect(find.byIcon(Icons.style), findsOneWidget);
    expect(find.byIcon(Icons.school), findsOneWidget);
  });
}
