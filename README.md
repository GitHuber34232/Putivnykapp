# Putivnyk

Путівник по Києву у форматі Android-додатка. Основна ідея проста: відкрити карту, знайти місця, зібрати свій маршрут і не залежати повністю від мережі.

У репозиторії зараз дві частини:

- `app/` — сам Android-додаток
- `backend/` — невеликий Node.js сервіс для подій

Що вже є в застосунку:

- офлайн-карта
- збережені маршрути
- місця з категоріями, пошуком і фільтрами
- вибране та історія відвідувань
- офлайн-переклад через ML Kit
- backend для списку подій

## Чим це зібрано

- Kotlin + Jetpack Compose
- Hilt, Room, WorkManager
- MapLibre
- Retrofit / OkHttp
- трохи C++ для геометрії та відстаней

## Як зібрати Android

На Linux:

```bash
./gradlew assembleDebug
```

На Windows:

```bat
gradlew.bat assembleDebug
```

Готовий debug APK після збірки лежить тут:

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Для release bundle:

```bash
./gradlew bundleRelease
```

Кілька нормальних практичних приміток, без сюрпризів:

- якщо це перший запуск на Linux, `./gradlew` може довго щось качати — це не зависання, він підтягне Android command-line tools, SDK, CMake і NDK
- `./gradlew.sh` теж залишився і працює, але основна команда для Linux тепер `./gradlew`
- за замовчуванням SDK для Linux ставиться в `~/Android/Sdk`
- якщо треба інший каталог SDK, можна задати `PUTIVNYK_ANDROID_SDK_DIR=/шлях/до/sdk`
- якщо автодокачування не потрібне, задайте `PUTIVNYK_BOOTSTRAP_ANDROID_SDK=false`

Поточна збірка перевірялась на JDK 21 і Android SDK 35.

## Backend

Backend живе окремо в `backend/`.

Локальний запуск:

```bash
cd backend
npm install
npm run dev
```

За замовчуванням він піднімається на `http://localhost:3000`.

## Що варто знати

- у проєкті є dependency verification Gradle, тому нові залежності інколи треба явно підтверджувати в `gradle/verification-metadata.xml`
- частина маршрутної логіки вміє працювати офлайн, але точність ETA залежить від того, чи вдалося взяти нормальний роут із мережі або локального графа
- якщо змінюєте нативну геометрію в `app/src/main/cpp`, не забудьте після цього прогнати звичайну Android-збірку, а не тільки unit-тести
