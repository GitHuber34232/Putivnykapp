# Putivnyk

Android-додаток для самостійних прогулянок Києвом.

## Можливості
- Офлайн-карта Києва
- Маршрути, локації, рекомендації
- Пошук, фільтри, збереження улюбленого
- Офлайн-переклад (ML Kit, 28 мов)
- Синхронізація з сервером (Node.js backend)
- Підтримка темної теми

## Технології
- Kotlin, Jetpack Compose, Hilt, Room, WorkManager
- MapLibre GL, Retrofit, OkHttp, ML Kit
- C++ (JNI) для гео-обчислень
- Node.js backend (Express, Cheerio)

## Збірка
- Android Studio Hedgehog+ (AGP 9, Kotlin 2.3)
- `./gradlew assembleDebug` — debug APK
- `./gradlew bundleRelease` + bundletool — release APK

## Автор
Розроблено з любов'ю до Києва. Всі права захищено.
