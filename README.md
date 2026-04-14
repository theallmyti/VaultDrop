# VaultDrop

![CI](https://img.shields.io/badge/CI-passing-brightgreen)
![License: Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-green)
![Android](https://img.shields.io/badge/Android-26%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2D7DF6?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room-Database-6C63FF)
![Hilt](https://img.shields.io/badge/Hilt-DI-0F9D58)
![Media3](https://img.shields.io/badge/Media3-ExoPlayer-FF6F00)
![Convex](https://img.shields.io/badge/Backend-Convex-2C2C2C)

VaultDrop is an Android app for saving and downloading Instagram and YouTube content with a local-first experience, cloud bookmark sync, and in-app preview playback.

## Download

- [Latest APK](https://github.com/theallmyti/VaultDrop/releases/download/app/VaultDrop-arm64-v8a-debug-1.0.0.apk)
- [Project Website](https://theallmyti.github.io/VaultDrop/)

## Highlights

- Fast share-to-save flow (instant local save, background enrichment)
- Download queue with progress and final media in Library
- Bookmarks with tags, preview, and cloud sync
- Cloud backup account flow (password + OTP verification)
- Password reset via OTP
- Instagram session capture page for stronger extraction
- Resend OTP cooldown on UI and backend

## Tech Stack

- Kotlin + Jetpack Compose
- MVVM + Repository
- Room (local data)
- Hilt (dependency injection)
- Retrofit + OkHttp
- Media3 / ExoPlayer
- Convex backend (`convex.site` HTTP routes)
- Resend / SMTP mail path for OTP delivery

## Project Structure

```text
app/src/main/java/com/adityaprasad/vaultdrop/
  data/
    api/
    db/
    downloader/
    repository/
  domain/
    model/
    usecase/
  ui/
    auth/
    bookmarks/
    components/
    downloads/
    home/
    library/
    player/
    settings/
    share/
convex/
  auth.ts
  bookmarks.ts
  email.ts
  http.ts
  schema.ts
```

## App Flow Chart

```mermaid
flowchart TD
    A[User shares Instagram/YouTube link] --> B[ShareReceiverActivity]
    B --> C{Choose action}

    C -->|Download| D[Create download item]
    D --> E[DownloadService + platform downloader]
    E --> F[Downloads tab progress]
    F --> G[Library tab]
    G --> H[Video/Image player]

    C -->|Save Link| I[Instant local bookmark save]
    I --> J[Background metadata enrichment]
    J --> K[Optional cloud sync]
    K --> L[Bookmarks tab]

    L --> M{Bookmark actions}
    M -->|Preview| N[BookmarkPreviewActivity]
    M -->|Delete local| O[Room delete]
    M -->|Delete cloud| P[Convex bookmarks/delete]
    M -->|Open external| Q[Browser]

    R[Settings] --> S[Cloud Backup Auth]
    S --> T[Sign in / Sign up]
    T --> U[Password account creation]
    U --> V[OTP verification page]
    V --> W[Resend OTP with 60s cooldown]
    W --> X[Verify OTP]
    X --> Y[Session token stored locally]

    R --> Z[Instagram Session Page]
    Z --> AA[WebView login]
    AA --> AB[Cookie/session capture]
    AB --> AC[Authenticated Instagram extraction path]
```

## Auth and Cloud Backup Flow

- Sign in uses email + password.
- Sign up is a separate page.
- After create account, user moves to OTP verification page.
- OTP page shows entered email and an Edit action to return to sign-up form.
- Resend OTP is available with 60-second cooldown.
- Password reset also uses OTP.
- Successful auth stores token in shared preferences and enables cloud bookmark sync.

## Build and Run

1. Clone repo
2. Open in Android Studio or VS Code + Android tooling
3. Ensure Android SDK is installed
4. Set `convex.baseUrl` in `local.properties` if needed

```bash
gradlew.bat assembleDebug
```

For Kotlin compile check:

```bash
gradlew.bat :app:compileDebugKotlin --no-daemon
```

## Convex Backend Setup

1. Install dependencies

```bash
npm install
```

2. Configure deployment (`.env.local` should include `CONVEX_DEPLOYMENT`)
3. Push functions

```bash
npx convex dev --once
```

### OTP Mail Options

- SMTP (recommended for personal inbox sender):
  - `SMTP_USER`
  - `SMTP_PASS` (App Password)
  - `SMTP_FROM`
  - Optional: `SMTP_HOST`, `SMTP_PORT`, `SMTP_SECURE`
- Resend fallback:
  - `RESEND_API_KEY`
  - Optional: `RESEND_FROM`

## Screenshots

### Home
<p align="center"><img src="UI-screenshots/HomePage.png" width="280" alt="Home" /></p>

### Downloads
<p align="center"><img src="UI-screenshots/DownloadPage.png" width="280" alt="Downloads" /></p>

### Library
<p align="center"><img src="UI-screenshots/LibraryPage.png" width="280" alt="Library" /></p>

### Bookmarks
<p align="center"><img src="UI-screenshots/BookmarkPage.png" width="280" alt="Bookmarks" /></p>

### Settings
<p align="center"><img src="UI-screenshots/SettingPage1.png" width="280" alt="Settings 1" /></p>

## Notes

- Instagram behavior can vary by region, login state, and link privacy.
- If OTP fails, check SMTP/Resend env vars and redeploy Convex functions.
- For best OTP security, rotate app passwords when exposed.

## License

Apache 2.0
