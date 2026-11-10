# ScreenTextCopier 📱✂️

**ScreenTextCopier** to lekkie, natywne narzędzie na Androida dla programistów — szczególnie przydatne podczas **vibe codingu** i pracy z asystentami AI. Jednym dotknięciem pływającego przycisku kopiuje tekst udostępniony przez aktualnie widoczny interfejs do schowka, aby szybko przenieść **komendy, logi, komunikaty błędów i fragmenty kodu** z terminala (np. Termuxa) do edytora lub rozmowy z AI.

**Priorytetem jest wierność tekstu źródłowego, zwłaszcza w przypadku logów i poleceń powłoki.** Aplikacja odczytuje tekst bezpośrednio przez Android Accessibility, zamiast rozpoznawać znaki ze zrzutów ekranu. Dzięki temu nie wprowadza typowych błędów OCR, np. zamiany `0` na `O` czy `1` na `l`. Kopiowanie zależy jednak od tego, jaki tekst dana aplikacja udostępnia przez Accessibility — nie obejmuje automatycznie napisów osadzonych w obrazach ani historii terminala poza ekranem.

Aplikacja działa w architekturze **Zero Network**: nie żąda uprawnienia `INTERNET` i nie korzysta z OCR.

Aplikacja operuje bezpośrednio na drzewie widoków ułatwień dostępu (`AccessibilityNodeInfo`), wyciągając tekst i opisy interfejsu. **Nie wykonuje OCR** (rozpoznawania tekstu z pikseli graficznych) i nie wymaga żadnych ciężkich silników rozpoznawania obrazu.

---

## 🚀 Główne Cechy

1. **1-Klik Kopiowanie Tekstu (On-Demand Extraction)**:
   - Jedno tapnięcie w mały, półprzezroczysty pływający przycisk skanuje aktualnie widoczne drzewo widoków (`AccessibilityNodeInfo`).
   - Ekstrahuje widoczne teksty (`node.text`) oraz etykiety ułatwień dostępu (`node.contentDescription`).
   - Automatycznie usuwa duplikaty z zachowaniem kolejności i scala linie tekstu do schowka systemowego (`ClipboardManager`).
   - Wyświetla krótki Toast z informacją o liczbie skopiowanych linii.

2. **Bezpieczeństwo i Prywatność (Zero Network)**:
   - W manifeście kategorycznie **brak uprawnienia `android.permission.INTERNET`**.
   - Zero bibliotek sieciowych, analitycznych lub telemetrycznych — czysty Android SDK i AndroidX.
   - Subskrypcja zdarzeń ułatwień dostępu jest zminimalizowana do zera (`eventTypes = 0`, puste `onAccessibilityEvent`) — aplikacja nie monitoruje działań użytkownika w tle, a ekran jest skanowany wyłącznie w chwili fizycznego kliknięcia w przycisk.
   - Na Androidzie 13+ (API 33+) schowek jest oznaczany flagą `ClipDescription.EXTRA_IS_SENSITIVE`, co zapobiega wyciekom wrażliwych danych w systemowych nakładkach schowka.
   - Wbudowany w aplikacji podgląd schowka domyślnie maskuje treść i wymaga świadomego kliknięcia, aby odsłonić dane.

3. **Pływający Przycisk (`TYPE_ACCESSIBILITY_OVERLAY`)**:
   - Nie wymaga inwazyjnego uprawnienia `SYSTEM_ALERT_WINDOW` — wykorzystuje nakładkę ułatwień dostępu `TYPE_ACCESSIBILITY_OVERLAY` i `FLAG_NOT_FOCUSABLE`.
   - Obsługa gestów:
     - **Tap**: natychmiastowe skopiowanie tekstu z ekranu.
     - **Drag**: płynne przesuwanie przycisku z ograniczeniem do widocznego obszaru ekranu (zabezpieczenie przed ucieczką poza ekran).
     - **Snap-to-edge**: automatyczne przyciąganie do lewej lub prawej krawędzi po zakończeniu przeciągania.
     - **Long Press**: szybkie przejście do ekranu głównego aplikacji (`MainActivity`), aby zmienić opcje lub ukryć przycisk.

4. **Wygodna Kontrola (`MainActivity`)**:
   - Przycisk szybkiego przejścia do ustawień systemowych ułatwień dostępu Androida (`Settings.ACTION_ACCESSIBILITY_SETTINGS`).
   - Przełącznik (Switch zapisywany w `SharedPreferences` i nasłuchiwany w czasie rzeczywistym) pozwalający ukryć pływający przycisk bez wyłączania całej usługi w systemie.
   - Dedykowany moduł pomocniczy dla urządzeń z Androidem 13 i 14 (Restricted Settings).
   - Bezpieczna inspekcja schowka z maskowaniem wrażliwych danych.

---

## 🛡️ Android 13/14+ Restricted Settings (Odblokowanie ułatwień dostępu)

Na systemach Android 13 i 14 usługi ułatwień dostępu dla aplikacji instalowanych poza oficjalnym sklepem są domyślnie blokowane przez wbudowany mechanizm bezpieczeństwa systemu (*Restricted Settings*).

Aby odblokować działanie usługi:
1. Po standardowej instalacji pakietu `.apk` przejdź do: **Ustawienia telefonu** $\to$ **Aplikacje** $\to$ **ScreenTextCopier** (lub kliknij dedykowany przycisk *„Otwórz informacje o aplikacji”* w `MainActivity`).
2. Kliknij **menu trzech kropek (⋮)** w prawym górnym rogu ekranu Informacji o aplikacji.
3. Wybierz opcję **„Zezwalaj na ograniczone ustawienia”** (*Allow restricted settings*) i potwierdź tożsamość odciskiem palca lub kodem PIN.
4. Przejdź do *Ułatwień dostępu* i włącz usługę ScreenTextCopier — pływający przycisk pojawi się na ekranie.

---

## 🧪 Plan Smoke Testów na Rzeczywistym Androidzie

Przed wdrożeniem produkcyjnym przeprowadź weryfikację na fizycznym urządzeniu wg poniższego scenariusza:

| Krok | Akcja testera | Oczekiwany rezultat |
| :--- | :--- | :--- |
| **ST-01** | Instalacja `ScreenTextCopier-debug.apk` lub podpisanej wersji release. | Instalator pakietów kończy instalację z sukcesem, ikona aplikacji wyświetla się poprawnie w launcherze. |
| **ST-02** | Pierwsze uruchomienie `MainActivity`. | Stan usługi wskazuje *Usługa nieaktywna* (czerwony badge). Na Androidzie 13+ widoczna jest sekcja z instrukcją Restricted Settings. |
| **ST-03** | Kliknięcie *„Włącz usługę w systemie”* i aktywacja w Ułatwieniach dostępu. | Po włączeniu usługi na ekranie natychmiast pojawia się mały, półprzezroczysty pływający przycisk. W `MainActivity` stan zmienia się na zielony (*Usługa aktywna*). |
| **ST-04** | Przeciąganie pływającego przycisku po ekranie. | Przycisk płynnie porusza się za palcem. Nie można go przeciągnąć poza krawędzie ekranu ani pod pasek stanu. Po puszczeniu przycisk płynnie dokuje się do najbliższej krawędzi (snap-to-edge). |
| **ST-05** | Ekstrakcja tekstu (1-tap) w przeglądarce / aplikacji z tekstem. | Krótkie tapnięcie w przycisk wyświetla Toast (np. *„Skopiowano X linii tekstu do schowka”*). |
| **ST-06** | Weryfikacja schowka w edytorze notatek. | Wklejenie ze schowka wkleja odczytany tekst, bez zduplikowanych linii, rozdzielony znakami nowej linii. |
| **ST-07** | Bezpieczny podgląd w `MainActivity`. | Kliknięcie *„Pobierz stan schowka”* wyświetla bezpieczne podsumowanie liczby linii i znaków (treść zamaskowana). Kliknięcie *„Pokaż jawną treść”* odsłania tekst. Kliknięcie *„Wyczyść podgląd”* usuwa go z ekranu. |
| **ST-08** | Długie przytrzymanie (Long Press) przycisku. | Przytrzymanie przez 0.5 s wywołuje delikatną animację i natychmiast otwiera `MainActivity`. |
| **ST-09** | Wyłączenie przełącznika *„Pływający przycisk na ekranie”* w `MainActivity`. | Przycisk natychmiast znika z ekranu bez konieczności wyłączania usługi w ustawieniach systemowych. Ponowne włączenie przełącznika przywraca przycisk na zapamiętaną pozycję. |

---

## 🛠️ Konfiguracja Podpisywania i CI/CD

- **Budowanie w CI**:
  Workflow [`.github/workflows/build.yml`](.github/workflows/build.yml) działa z uprawnieniem `contents: read`, uruchamia testy jednostkowe `testDebugUnitTest` oraz kompiluje pakiety APK.
- **Bezpieczne podpisywanie Release**:
  Projekt nie przechowuje prywatnych kluczy w repozytorium. Konfiguracja podpisu produkcyjnego w `app/build.gradle.kts` pobiera ścieżki i hasła ze zmiennych środowiskowych:
  - `RELEASE_KEYSTORE_PATH`
  - `RELEASE_KEYSTORE_PASSWORD`
  - `RELEASE_KEY_ALIAS`
  - `RELEASE_KEY_PASSWORD`
  W przypadku braku tych zmiennych w środowisku deweloperskim, wersja release pozostaje niepodpisana (lub używana jest wersja debug).
- **Publikacja Release**:
  Oddzielne zadanie `publish-release` (z uprawnieniem `contents: write`) uruchamiane jest wyłącznie przy wypchnięciu taga wersji (np. `git tag v1.0.1 && git push origin v1.0.1`), a nie przy zwykłym pushu do gałęzi `main`.
