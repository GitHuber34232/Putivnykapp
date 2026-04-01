# Putivnyk

Kotlin Multiplatform-проєкт для самостійних прогулянок Києвом з Android-застосунком і shared-ядром, готовим до інтеграції з iOS.

## Можливості
- Офлайн-карта Києва
- Маршрути, локації, рекомендації
- Пошук, фільтри, збереження улюбленого
- Офлайн-переклад (ML Kit, 28 мов)
- Синхронізація з сервером (Node.js backend)
- Підтримка темної теми

## Технології
- Kotlin Multiplatform, Jetpack Compose, Hilt, Room, WorkManager
- MapLibre GL, Retrofit, OkHttp, ML Kit
- C++ (JNI) для гео-обчислень
- Node.js backend (Express, Cheerio)

## Kotlin Multiplatform
- `shared/` містить спільні моделі, гео-обчислення, побудову маршрутів, рекомендації та API `PutivnykSharedApi` для iOS/Android.
- Android-модуль `app/` тепер споживає shared-код як залежність `:shared`.
- Для iOS вже налаштовані таргети `iosX64`, `iosArm64`, `iosSimulatorArm64` і генерація framework `PutivnykShared`.
- Room, WorkManager, Hilt, ML Kit, MapLibre UI та Android-specific cache/sync поки лишаються у платформеному Android-шарі.

## Що вже працює для iOS
- Спільні моделі домену: локації, маршрути, bookmarks, sync/preferences, локалізовані рядки.
- Спільні алгоритми: рекомендації, оптимізація маршрутів, метрики маршруту, підтримувані мови.
- Спільний geospatial API через `expect/actual` з Android JNI-реалізацією та iOS fallback-реалізацією.
- Готовий `PutivnykSharedApi` для виклику зі Swift/Objective-C після збірки framework.

## Збірка
- Android Studio Hedgehog+ / Koala+ (AGP 9, Kotlin 2.3)
- `./gradlew :app:assembleDebug` — debug APK Android
- `./gradlew :shared:compileCommonMainKotlinMetadata :shared:compileKotlinAndroid` — перевірка shared KMP-модуля
- `./gradlew :shared:assemblePutivnykSharedDebugXCFramework` — debug XCFramework для iOS
- `./gradlew :shared:assemblePutivnykSharedReleaseXCFramework` — release XCFramework для iOS
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — iOS framework для симулятора
- `./gradlew :shared:linkReleaseFrameworkIosArm64` — iOS framework для device build

Примітка: shared iOS framework/XCFramework можна зібрати й із Windows через Kotlin/Native toolchain, але Xcode build, codesign, archive та IPA все одно вимагають macOS.

## CI
- У репозиторії додано GitHub Actions pipeline для backend, Android/shared і iOS simulator build.
- Android CI перевіряє `compileDebugKotlin`, `testDebugUnitTest` і `bundleRelease`.
- iOS CI на macOS генерує Xcode-проєкт через XcodeGen і збирає SwiftUI host-app із `PutivnykShared.xcframework`.

## Xcode інтеграція
- Каталог `iosApp/` містить SwiftUI host-app, який підключає shared KMP-модуль.
- Локально на macOS: `brew install xcodegen`, далі `cd iosApp && xcodegen generate`.
- Pre-build script автоматично викликає Gradle та підкладає `PutivnykShared.xcframework` у host-app.

## IPA з Windows
- Локально на Windows підписаний IPA зібрати неможливо: для цього потрібні Xcode та Apple signing tools.
- У репозиторії додано workflow `iOS IPA`, який можна запускати з GitHub UI або API з Windows-машини.
- Додано workflow `iOS Simulator` без signing для швидкої перевірки Swift/Xcode збірки на macOS runner.
- Workflow `iOS IPA` також запускається автоматично по тегах `v*`, `ios-v*` і по published release.
- Для signed IPA потрібно додати secrets: `IOS_CERTIFICATE_P12_BASE64`, `IOS_CERTIFICATE_PASSWORD`, `IOS_PROVISIONING_PROFILE_BASE64`, `IOS_PROVISIONING_PROFILE_NAME`, `IOS_TEAM_ID`, `IOS_BUNDLE_IDENTIFIER`, `IOS_CODE_SIGN_IDENTITY`.
- Після запуску workflow готовий `.ipa` буде прикріплений як GitHub Actions artifact.
- На Ubuntu достатньо покласти `~/ios/signing_certificate.p12` і `~/ios/Putivnyk.mobileprovision`, експортувати `IOS_P12_PASSWORD`, після чого запустити `./scripts/ios/setup-and-build-ipa-ubuntu.sh` без параметрів.

Додатково:
- Готовий шаблон secrets лежить у [docs/ios-ipa-secrets-template.md](docs/ios-ipa-secrets-template.md).
- Для macOS-friendly onboarding додано `Makefile` і helper scripts у [scripts/ios](scripts/ios).
- Для dispatch IPA workflow з Windows додано [scripts/ios/Dispatch-Ipa.ps1](scripts/ios/Dispatch-Ipa.ps1).
- Для Ubuntu додано [scripts/ios/setup-and-build-ipa-ubuntu.sh](scripts/ios/setup-and-build-ipa-ubuntu.sh), який сам ставить залежності, виконує `gh auth login`, готує secrets, пушить branch/tag і запускає IPA build без CLI-параметрів.

## Автор
Розроблено з любов'ю до Києва. Всі права захищено.
