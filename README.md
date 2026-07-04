# Çince-İngilizce Sözlük (zh_en_dict)

Android için Çince-İngilizce sözlük, flashcard ile çalışma modu ve gramer
konuları içeren bir Flutter uygulaması. Aynı kod tabanı hem Android'e hem de
tarayıcıya (web) derlenebiliyor; bu sayede geliştirme sırasında PC'de
tarayıcıda görsel olarak test edebilirsin.

## Neler var?

- **Sözlük** (`lib/screens/dictionary_screen.dart`): arama kutusu + kelime
  listesi + kelime detay sayfası (hanzi, pinyin, anlam, örnek cümle, sesli
  telaffuz butonu).
- **Flashcard** (`lib/screens/flashcards_screen.dart`): kartı çevirerek
  çalışma, "Biliyorum / Bilmiyorum" ile basit bir tekrar (Leitner kutu)
  sistemi. İlerleme cihazda (`shared_preferences`) saklanır; bilmediğin
  kelimeler bir sonraki oturumda öne alınır.
- **Gramer** (`lib/screens/grammar_list_screen.dart`): konu listesi + detay
  sayfası, her örnek cümlede sesli okuma butonu.
- **Ses**: `flutter_tts` paketiyle cihazın Çince metinden sese (TTS) motorunu
  kullanıyor; ayrı ses dosyası indirmeye/depolamaya gerek yok.
- **Veri**: `assets/data/words.json` ve `assets/data/grammar.json` içinde;
  yeni kelime/konu eklemek için bu dosyaları düzenlemen yeterli.

## Gereksinimler

- [Flutter SDK](https://docs.flutter.dev/get-started/install) (stable kanal)
- Web'de test için: Chrome (ya da herhangi bir Chromium tabanlı tarayıcı)
- Android'de test için: Android Studio + bir emülatör ya da USB ile bağlı
  gerçek bir telefon

Kurulumdan sonra bağımlılıkları indir:

```bash
flutter pub get
```

## PC'de tarayıcıda çalıştırma (geliştirme sırasında hızlı görsel test)

```bash
flutter run -d chrome
```

Bu komut uygulamayı Chrome'da açar ve hot-reload ile kod değişikliklerini
anında görürsün. Ayrı bir Android cihaz/emülatör kurmadan arayüzü, flashcard
akışını ve gramer sayfalarını buradan test edebilirsin.

## Android'de çalıştırma

```bash
flutter run -d <cihaz-id>
```

Bağlı cihazları görmek için: `flutter devices`. Android Studio'dan bir
emülatör açtıktan sonra `flutter run` komutu otomatik olarak onu seçer.

APK üretmek için:

```bash
flutter build apk --release
```

## Yeni kelime/gramer konusu eklerken dikkat

`assets/data/words.json` içine yeni bir kelime eklerken şu alanları doldur:

```json
{
  "id": "w076",
  "hanzi": "...",
  "pinyin": "...",
  "meaning": "...",
  "partOfSpeech": "...",
  "level": 1,
  "category": "...",
  "exampleZh": "...",
  "examplePinyin": "...",
  "exampleEn": "..."
}
```

Uygulama, Çince karakterleri küçük boyutlu bir yazı tipiyle
(`assets/fonts/NotoSansSC-Subset.ttf`) gösteriyor. Bu yazı tipi sadece şu an
kullanılan karakterleri içeriyor (dosya boyutunu küçük tutmak için). **Yeni
bir kelime eklediğinde, eğer o kelimede daha önce hiç kullanılmamış bir Çince
karakter varsa**, o karakter ekranda boş bir kutu (□) olarak görünür. Bunu
düzeltmek için yazı tipini yeniden üretmen yeterli:

```bash
python3 tool/regenerate_font_subset.py
```

Bu script internet bağlantısı ve `pip install fonttools` gerektirir; projede
kullanılan tüm karakterleri tarayıp Google Fonts üzerinden güncel bir alt küme
(subset) yazı tipi indirir.

## Proje yapısı

```
lib/
  models/       Word, GrammarTopic veri modelleri
  data/         JSON verilerini okuyan repository sınıfları + ilerleme kaydı
  services/     TtsService (metinden sese)
  screens/      Ekranlar (sözlük, flashcard, gramer, ana sayfa)
  widgets/      Paylaşılan küçük bileşenler
assets/
  data/         words.json, grammar.json
  fonts/        NotoSansSC-Subset.ttf
tool/
  regenerate_font_subset.py   Yazı tipini yeniden üretme script'i
```
