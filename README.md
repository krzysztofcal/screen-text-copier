# ScreenTextCopier 📱✂️

Natywna, lekka aplikacja na Androida w języku **Kotlin** z architekturą **Zero Network**, umożliwiająca natychmiastowe kopiowanie całego tekstu z aktywnego ekranu do schowka za pomocą jednego kliknięcia w pływający przycisk.

---

## 🚀 Główne Cechy

1. **1-Klik Kopiowanie Tekstu (On-Demand Extraction)**:
   - Jedno tapnięcie w mały, półprzezroczysty pływający przycisk skanuje aktualnie widoczne drzewo widoków (`AccessibilityNodeInfo`).
   - Ekstrahuje widoczne teksty (`node.text`) oraz opisy ułatwień dostępu (`node.contentDescription`).
   - Automatycznie usuwa duplikaty i scala linie tekstu do schowka systemowego (`ClipboardManager`).
   - Wyświetla krótki Toast z informacją o liczbie skopiowanych linii.

2. **Bezpieczeństwo i Prywatność (Zero Network)**:
   - W manifeście kategorycznie **brak uprawnienia `android.permission.INTERNET`**.
   - Zero bibliotek sieciowych, analitycznych lub telemetrycznych — czysty Android SDK i AndroidX.
   - Metoda `onAccessibilityEvent` w serwisie jest **całkowicie pusta** — aplikacja nie monitoruje działań użytkownika w tle, a ekran jest skanowany wyłącznie w chwili fizycznego kliknięcia w przycisk.

3. **Pływający Przycisk (`TYPE_ACCESSIBILITY_OVERLAY`)**:
   - Nie wymaga inwazyjnego uprawnienia `SYSTEM_ALERT_WINDOW` (wyświetlanie nad innymi aplikacjami) — wykorzystuje wbudowaną nakładkę ułatwień dostępu `TYPE_ACCESSIBILITY_OVERLAY` i `FLAG_NOT_FOCUSABLE`.
   - Obsługa gestów:
     - **Tap**: natychmiastowe skopiowanie tekstu z ekranu.
     - **Drag**: płynne przesuwanie przycisku z automatycznym przyciąganiem do najbliższej krawędzi ekranu.
     - **Long Press**: szybkie przejście do ekranu głównego aplikacji (`MainActivity`), aby wyłączyć lub zmienić opcje.

4. **Wygodna Kontrola (`MainActivity`)**:
   - Przycisk szybkiego przejścia do ustawień systemowych ułatwień dostępu Androida (`Settings.ACTION_ACCESSIBILITY_SETTINGS`).
   - Przełącznik (Switch zapisywany w `SharedPreferences` i nasłuchiwany w czasie rzeczywistym) pozwalający ukryć pływający przycisk bez wyłączania całej usługi w systemie.
   - Podgląd i weryfikacja zawartości schowka na żywo.

---

## 🛠️ Architektura i Wymagania

- **Język**: Kotlin
- **Compile SDK**: 34 (Android 14)
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Java**: JDK 17
- **Gradle**: 8.7 (Kotlin DSL `build.gradle.kts`)
- **CI/CD**: Automatyczny build debug i release APK przez GitHub Actions (`gradle/actions/setup-gradle@v3`).

---

## 📦 Pobieranie i Instalacja

Gotowy plik `.apk` jest budowany automatycznie przy każdym pushu przez GitHub Actions i dostępny w zakładce **Actions** oraz **Releases**.
Zarówno wersja `debug`, jak i `release` są wstępnie podpisane, dzięki czemu plik `.apk` można zainstalować bezpośrednio na telefonie bez dodatkowych konfiguracji.
