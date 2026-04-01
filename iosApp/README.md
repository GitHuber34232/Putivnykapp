# iOS host app

Цей каталог містить SwiftUI host-app для інтеграції shared KMP-модуля через XcodeGen.

## Локальна генерація Xcode-проєкту

1. Встановити `xcodegen` на macOS: `brew install xcodegen`
2. Згенерувати проєкт: `cd iosApp && xcodegen generate`
3. Відкрити `iosApp/PutivnykIOS.xcodeproj`
4. Під час build pre-build script автоматично збере `PutivnykShared.xcframework`

Або з кореня репозиторію:

```bash
make generate-ios
make build-ios-sim
```

## Що тут налаштовано

- SwiftUI application target `PutivnykIOS`
- pre-build script, який збирає debug/release XCFramework із `:shared`
- імпорт `PutivnykShared` у Swift-коді для перевірки реальної інтеграції

## IPA

Signed IPA локально вимагає macOS + Xcode + Apple signing assets.
Якщо працюєте з Windows, використовуйте GitHub Actions workflow `iOS IPA`.
Для Ubuntu використовуйте `./scripts/ios/setup-and-build-ipa-ubuntu.sh`.

### Remote IPA dispatch

macOS/Linux:

```bash
export GITHUB_REPOSITORY=owner/repo
export IPA_EXPORT_METHOD=ad-hoc
./scripts/ios/dispatch-ipa.sh
```

Windows PowerShell:

```powershell
$env:GITHUB_REPOSITORY = 'owner/repo'
$env:IPA_EXPORT_METHOD = 'ad-hoc'
.\scripts\ios\Dispatch-Ipa.ps1
```

Точний список secrets і формат значень описано в [docs/ios-ipa-secrets-template.md](docs/ios-ipa-secrets-template.md).