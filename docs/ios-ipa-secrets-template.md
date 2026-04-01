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

1. сам поставить `gh`, `openssl`, `python3`, `coreutils`, `git` через `apt-get`, якщо чогось бракує
2. якщо треба, запустить `gh auth login`
3. працює без прапорців і без prompt-ів по шляхах
4. сам витягне `IOS_TEAM_ID`, `IOS_PROVISIONING_PROFILE_NAME`, `IOS_BUNDLE_IDENTIFIER`, `IOS_CODE_SIGN_IDENTITY`
5. заллє всі GitHub secrets через `gh secret set`
6. оновить `origin` на чистий `https://github.com/owner/repo.git`, щоб не впертися в старий PAT без `workflow` scope
7. зробить `git push`, створить tag і запустить workflow `ios-ipa.yml`

Запуск без параметрів:

```bash
./scripts/ios/setup-and-build-ipa-ubuntu.sh
```

Дефолтні шляхи:

- `.p12`: `~/ios/signing_certificate.p12`
- `.mobileprovision`: `~/ios/Putivnyk.mobileprovision`
- repo: `GitHuber34232/Putivnykapp`
- tag: `v<MARKETING_VERSION>-ios`, зараз це `v1.0.0-ios`
- export method: `ad-hoc`

Обов'язково перед запуском:

```bash
export IOS_P12_PASSWORD='ваш_пароль_до_p12'
```

Якщо потрібно, дефолти можна перевизначити через environment variables: `GITHUB_REPOSITORY`, `IOS_P12_PATH`, `IOS_PROFILE_PATH`, `IOS_P12_PASSWORD`, `IOS_GIT_TAG`, `IPA_EXPORT_METHOD`.

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