# Putivnyk

Путівник по Києву у форматі Android-додатка. Основна ідея проста: відкрити карту, знайти місця, зібрати свій маршрут і не залежати повністю від мережі.

У репозиторії зараз дві частини:

- `app/` — сам Android-додаток
- `backend/` — невеликий Node.js сервіс для подій

Що вже є в застосунку:

- офлайн-карта (без мережі карта сама перемикається на вбудовану) і завантаження детальних офлайн-районів Києва на вибір
- збережені маршрути з навігацією, офлайн-маршрутизація через GraphHopper
- навігація з вимкненим екраном: прогрес маршруту в сповіщенні, на Android 16+ — Live Update (сегментований прогрес по зупинках на екрані блокування)
- місця з категоріями, пошуком і фільтрами
- вибране, історія відвідувань і автоматична позначка «відвідано», коли підходиш до місця (за бажанням — навіть із закритим застосунком, через геозони)
- AR-камера: пам'ятки розпізнаються не лише за GPS і компасом, а й за зображенням (порівняння з фото місця + ML Kit, усе на пристрої); під час маршруту в камері видно стрічку зі стрілками і наступну зупинку
- аудіогід: озвучення описів місць мовою інтерфейсу, міні-плеєр, керування з екрана блокування; у фоні працює через foreground service типу mediaPlayback (як вимагає Android 17)
- віджети на головний екран: «Поруч зі мною» і «Маршрут дня»
- обмін маршрутами посиланням `putivnyk://route?d=…` або QR-кодом, вбудований сканер QR
- статистика прогулянок (кілометри, серії днів, тиждень у графіку) і 25 досягнень
- події з backend, які показуються й офлайн (останній завантажений список)
- світла/темна тема та кольори шпалер (Material You)
- офлайн-переклад через ML Kit

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

Проєкт націлений на Android 17: `compileSdk` і `targetSdk` = 37, `minSdk` = 29. Збирається Android Gradle Plugin 9.1.1 на Gradle 9.3.1 і JDK 21. На Linux `./gradlew` сам доставить «Android SDK Platform 37» і build-tools 36.0.0; на Windows поставте Android SDK Platform 37 через SDK Manager в Android Studio.

Контрольних сум частини нових залежностей (AGP, CameraX, ML Kit object detection та image labeling, core-splashscreen, play-services-location) ще немає в `gradle/verification-metadata.xml` — для ZXing вони вже додані. Один раз виконайте:

```bash
./gradlew --write-verification-metadata sha256 build
```

і закомітьте оновлений `gradle/verification-metadata.xml` разом зі схемами Room `app/schemas/ua.kyiv.putivnyk.data.local.PutivnykDatabase/9.json` і `10.json` (статистика прогулянок і досягнення), які створить ця ж збірка.

Дозволи, які з'явилися разом із новими функціями: сповіщення (Android 13+), foreground service типів `location` і `mediaPlayback`, `POST_PROMOTED_NOTIFICATIONS` для Live Updates. Геолокація «Дозволити завжди» (`ACCESS_BACKGROUND_LOCATION`) запитується лише якщо користувач сам увімкне в налаштуваннях позначки з закритим застосунком — перед запитом показується пояснення. Для Google Play це означає заповнити декларацію про фонову геолокацію і типи foreground service.

Для release-збірки можна закріпити сертифікат backend подій (certificate pinning):

```bash
./gradlew bundleRelease -PeventsCertificatePins=sha256/<pin1>,sha256/<pin2>
```

Без цього параметра з'єднання перевіряється звичайним системним сховищем сертифікатів і Certificate Transparency.

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
