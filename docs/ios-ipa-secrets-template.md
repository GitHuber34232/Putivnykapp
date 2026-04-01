# iOS IPA signing secrets

Цей шаблон відповідає workflow [ .github/workflows/ios-ipa.yml ](.github/workflows/ios-ipa.yml).

## Обов'язкові GitHub Secrets

- `IOS_CERTIFICATE_P12_BASE64`
  Значення: base64 від `.p12` сертифіката підпису.
- `IOS_CERTIFICATE_PASSWORD`
  Значення: пароль до `.p12`.
- `IOS_PROVISIONING_PROFILE_BASE64`
  Значення: base64 від `.mobileprovision`.
- `IOS_PROVISIONING_PROFILE_NAME`
  Значення: точна назва provisioning profile в Apple Developer Portal/Xcode.
- `IOS_TEAM_ID`
  Значення: Apple Developer Team ID, наприклад `ABCDE12345`.
- `IOS_BUNDLE_IDENTIFIER`
  Значення: bundle id iOS app. Для цього репозиторію: `ua.kyiv.putivnyk.ios`.
- `IOS_CODE_SIGN_IDENTITY`
  Значення: signing identity, наприклад `Apple Distribution` або `Apple Development`.

## Що можна підставити точно вже зараз

- `IOS_BUNDLE_IDENTIFIER=ua.kyiv.putivnyk.ios`
- `IOS_CODE_SIGN_IDENTITY` зазвичай буде `Apple Distribution` для `ad-hoc`/`app-store` або `Apple Development` для `development`
- `IOS_TEAM_ID`, `IOS_PROVISIONING_PROFILE_NAME`, base64-представлення `.p12` і `.mobileprovision` беруться тільки з ваших Apple signing assets

## Автоматичне налаштування з Ubuntu

Є helper script [setup-and-build-ipa-ubuntu.sh](scripts/ios/setup-and-build-ipa-ubuntu.sh), який:

1. один раз запитає пароль до `.p12`
2. сам витягне `IOS_TEAM_ID`, `IOS_PROVISIONING_PROFILE_NAME`, `IOS_BUNDLE_IDENTIFIER`, `IOS_CODE_SIGN_IDENTITY`
3. заллє всі GitHub secrets через `gh secret set`
4. за потреби зробить `git push`
5. запустить workflow `ios-ipa.yml`

Приклад:

```bash
./scripts/ios/setup-and-build-ipa-ubuntu.sh \
  --repo owner/repo \
  --p12 ~/signing/ios_distribution.p12 \
  --profile ~/signing/Putivnyk.mobileprovision \
  --export-method ad-hoc \
  --tag v1.0.0-ios
```

Потрібні пакети на Ubuntu: `git`, `gh`, `openssl`, `python3`, `base64`.

## Як отримати base64 на macOS

### `.p12`

```bash
base64 -i signing_certificate.p12 | pbcopy
```

### `.mobileprovision`

```bash
base64 -i Putivnyk.mobileprovision | pbcopy
```

## Як отримати base64 на Windows PowerShell

### `.p12`

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('signing_certificate.p12'))
```

### `.mobileprovision`

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('Putivnyk.mobileprovision'))
```

## Рекомендовані значення по export method

- development: локальне тестування на девайсах
- ad-hoc: роздача збірок поза App Store
- app-store: App Store Connect / TestFlight
- enterprise: enterprise distribution

## Мінімальний checklist

1. Сертифікат і provisioning profile мають відповідати одному Team ID.
2. `IOS_BUNDLE_IDENTIFIER` має точно збігатися з App ID у provisioning profile.
3. `IOS_CODE_SIGN_IDENTITY` має відповідати типу сертифіката.
4. Перед першим запуском workflow перевірте, що `iosApp/project.yml` має коректний bundle id або передається через `xcodebuild` overrides.