# 🎬 VAULTDROP — Android App Master Prompt
> **App Name:** VaultDrop (changeable — replace every instance of "VaultDrop" with your preferred name)
> **Platform:** Android (API 26+, target API 34)
> **Language:** Kotlin
> **Architecture:** MVVM + Clean Architecture
> **Theme:** Minimalist Dark Mode with Subtle Accents

---

## 1. OVERVIEW

Build a native Android app called **VaultDrop** that allows users to:
- Download **Instagram Reels, Posts, and Stories** by sharing a link directly from the Instagram app
- Download **YouTube videos** by sharing a link from the YouTube app
- View all downloads (active + completed) in a dedicated **Downloads tab**
- **Play downloaded videos** inside the app using a built-in video player
- Access all saved videos in a **Library tab** organized by date
- Receive the app as a **share target** — appearing alongside WhatsApp, Telegram, etc. when the user taps the share button in Instagram or YouTube

---

## 2. DESIGN SYSTEM — DARK MINIMALIST

### 2.1 Color Palette

```
Background Primary:   #0A0A0A   (near-black, main screens)
Background Surface:   #111111   (cards, bottom sheet)
Background Elevated:  #1A1A1A   (modals, dialogs)
Border Subtle:        #222222   (dividers, card borders)
Accent Primary:       #C8F04B   (muted lime-green — used SPARINGLY)
Accent Dim:           #C8F04B26 (10% opacity accent, for progress bg)
Text Primary:         #F0F0F0   (main text)
Text Secondary:       #707070   (subtitles, timestamps, metadata)
Text Tertiary:        #3A3A3A   (placeholders, disabled)
Success:              #4BF0A0   (muted teal-green, completed state)
Error:                #F04B4B   (muted red, failed state)
```

### 2.2 Typography
- **Display / App name:** `DM Serif Display` — used only for the app logo/header
- **Body & UI:** `DM Sans` — weights 300, 400, 500
- **Monospace / URLs:** `JetBrains Mono` — used for showing links in the queue

### 2.3 Shape & Spacing
- Corner radius: **16dp** (cards), **12dp** (chips/badges), **999dp** (pills/buttons)
- Elevation: **no hard shadows** — use border strokes (`#222222`, 1dp) instead
- Spacing unit: **8dp** grid (8, 16, 24, 32, 48)
- Bottom navigation: **floating pill** style, not full-width bar

### 2.4 Motion
- Page transitions: **fade + slight upward slide (12dp Y)**, duration 220ms, `FastOutSlowIn` easing
- Progress bar: **animated shimmer** on the accent background when downloading
- List items: **staggered fade-in** on first load (40ms delay per item)
- Video thumbnail reveal: crossfade from skeleton shimmer

### 2.5 Iconography
- Library: **Phosphor Icons** (outlined style, 24dp)
- No filled icons except for the active bottom nav item (filled version of same icon)

---

## 3. APP ARCHITECTURE

```
com.vaultdrop.app
├── data/
│   ├── downloader/
│   │   ├── InstagramDownloader.kt      # handles IG reels/posts/stories
│   │   ├── YouTubeDownloader.kt        # handles YT video download
│   │   └── DownloadManager.kt          # queue, retry, state management
│   ├── db/
│   │   ├── AppDatabase.kt              # Room DB
│   │   ├── DownloadEntity.kt           # table schema
│   │   └── DownloadDao.kt              # queries
│   └── repository/
│       └── DownloadRepository.kt
├── domain/
│   ├── model/
│   │   ├── DownloadItem.kt             # domain model
│   │   └── DownloadStatus.kt           # QUEUED, ACTIVE, DONE, FAILED
│   └── usecase/
│       ├── StartDownloadUseCase.kt
│       ├── GetDownloadsUseCase.kt
│       └── DeleteDownloadUseCase.kt
├── ui/
│   ├── MainActivity.kt                 # hosts bottom nav + NavHost
│   ├── share/
│   │   └── ShareReceiverActivity.kt    # handles incoming share intents
│   ├── downloads/
│   │   ├── DownloadsFragment.kt
│   │   └── DownloadsViewModel.kt
│   ├── library/
│   │   ├── LibraryFragment.kt
│   │   └── LibraryViewModel.kt
│   ├── player/
│   │   └── VideoPlayerActivity.kt      # full-screen ExoPlayer
│   └── components/
│       ├── DownloadCard.kt             # reusable download item view
│       └── ThumbnailView.kt
└── service/
    └── DownloadService.kt              # foreground service for downloads
```

---

## 4. SCREENS & UI SPECIFICATION

---

### 4.1 BOTTOM NAVIGATION

Floating pill navigation bar, centered, with **2 tabs only**:

| Tab | Icon (Phosphor) | Label |
|-----|-----------------|-------|
| Downloads | `DownloadSimple` | Downloads |
| Library | `FolderOpen` | Library |

- Background: `#1A1A1A`, border: 1dp `#2A2A2A`, corner radius: 999dp
- Floating 16dp above the screen bottom
- Active tab: icon switches to filled variant, label visible, accent color `#C8F04B`
- Inactive tab: icon outlined, no label, color `#505050`
- Shadow: none — use subtle top border `#222222`

---

### 4.2 DOWNLOADS TAB

**Header:**
- Title: `"Downloads"` — `DM Serif Display`, 28sp, `#F0F0F0`
- Subtitle: `"2 active · 14 completed"` — `DM Sans 300`, 13sp, `#707070`

**Two sections stacked vertically with section labels:**

#### Section A — Active Downloads
- Label: `"IN PROGRESS"` — uppercase, 10sp, letter-spacing 0.12, `#505050`
- Each active download shows a **DownloadCard** (see 4.4)
- If none: show an inline empty state chip: `"Nothing downloading right now"`

#### Section B — Completed Downloads
- Label: `"COMPLETED"` — same style as above
- List of completed DownloadCards
- Each card shows thumbnail + title + source (IG or YT badge) + file size + duration
- If none: full empty state (see 4.5)

---

### 4.3 LIBRARY TAB

**Header:**
- Title: `"Library"` — same typography as Downloads
- Sort/filter row: two pill chips — `"Most Recent"` (active, accent bg) | `"By Source"` (inactive)

**Content:**
- **Staggered 2-column grid** of video thumbnails
- Each cell: rounded thumbnail (12dp radius), duration badge (bottom-right, dark pill), source icon (top-left, tiny IG or YT logo 16dp)
- Tapping a video opens **VideoPlayerActivity**
- Long-press shows contextual menu: `Share`, `Delete`, `Copy Link`

---

### 4.4 DOWNLOAD CARD COMPONENT

```
┌──────────────────────────────────────────────────────┐
│  [Thumb]  Title of the video or reel here             │
│  16x16    instagram.com · 24.6 MB · 0:45             │
│           ▓▓▓▓▓▓▓▓▓░░░░░░░░░░  68%   ↓ 2.1 MB/s    │
└──────────────────────────────────────────────────────┘
```

- Background: `#111111`, border: 1dp `#222222`, radius: 16dp
- Thumbnail: 56x56dp, radius: 8dp, skeleton shimmer until loaded
- Title: 2 lines max, `DM Sans 400`, 14sp, `#F0F0F0`
- Meta row: `DM Sans 300`, 12sp, `#707070` — source · size · duration
- Progress bar: 4dp tall, bg `#1E2A0A` (dim accent), fill `#C8F04B`, animated shimmer
- Speed badge: right-aligned, `JetBrains Mono 300`, 11sp, `#707070`
- Status states:
  - **QUEUED:** show `"Queued"` pill badge in `#2A2A2A`, no progress bar
  - **ACTIVE:** show progress bar + speed
  - **DONE:** show `"✓ Saved"` in `#4BF0A0`, no progress bar, thumbnail visible
  - **FAILED:** show `"Retry"` button (pill, `#F04B4B` tint) + error reason in small text

---

### 4.5 EMPTY STATE

Used in Downloads tab (completed section) and Library when no videos exist:

```
         ⬇
    [Icon: DownloadSimple, 48dp, #2A2A2A]

    Nothing here yet
    [#707070, DM Sans 300, 15sp]

    Share any Instagram or YouTube link
    to this app to start downloading
    [#3A3A3A, DM Sans 300, 13sp, centered]
```

No background illustration — text + single icon only. Generous vertical padding (64dp top).

---

### 4.6 VIDEO PLAYER SCREEN

- **Full-screen** `VideoPlayerActivity` using **ExoPlayer / Media3**
- Background: pure `#000000`
- Controls auto-hide after 3 seconds of inactivity
- Controls overlay (semi-transparent `#00000099`):
  - Top bar: back arrow + video title (truncated)
  - Center: ⏮ Rewind 10s | ▶/⏸ Play/Pause | ⏭ Forward 10s
  - Bottom: scrubber (accent colored), current time / total time
  - Bottom-right: fullscreen toggle, share button, delete button
- Scrubber thumb: 12dp circle, `#C8F04B`
- Buffering indicator: minimal spinner in accent color
- Gesture support: horizontal swipe to scrub, vertical swipe (left side) for brightness, vertical swipe (right side) for volume

---

## 5. SHARE RECEIVER

### 5.1 How it works
When a user taps **Share** on an Instagram Reel, Post, Story, or a YouTube video, **VaultDrop** must appear in the Android share sheet as an option.

### 5.2 AndroidManifest.xml — ShareReceiverActivity
```xml
<activity
    android:name=".ui.share.ShareReceiverActivity"
    android:theme="@style/Theme.VaultDrop.BottomSheet"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

### 5.3 ShareReceiverActivity UI
This appears as a **bottom sheet**, NOT a full activity:
- Background: `#111111`, top radius 20dp
- Drag handle pill at top: 36x4dp, `#2A2A2A`
- App name + icon row
- Detected URL displayed in `JetBrains Mono`, 12sp, `#707070`, single line truncated
- Platform chip auto-detected: either `Instagram` or `YouTube` pill with logo icon
- Quality selector (YouTube only): pills — `"1080p"` | `"720p"` | `"480p"` (selected = accent bg)
- Large **Download** button: full-width, radius 999dp, bg `#C8F04B`, text `#0A0A0A`, `DM Sans 500`, 16sp, label `"Add to Queue"`
- After tapping: sheet closes with a toast — `"Added to queue ↓"` — and returns user to the source app

### 5.4 Link Parsing Logic
```kotlin
fun detectPlatform(url: String): Platform {
    return when {
        url.contains("instagram.com") -> Platform.INSTAGRAM
        url.contains("youtu.be") || url.contains("youtube.com") -> Platform.YOUTUBE
        else -> Platform.UNSUPPORTED
    }
}
```

If unsupported: show `"This link isn't supported yet"` with a subtle error state in the sheet.

---

## 6. DOWNLOADING LOGIC

### 6.1 Instagram Downloads
- Use **yt-dlp** wrapped via a bundled binary executed through `Runtime.exec()` OR use a Java/Kotlin HTTP-based scraper
- Support: `/reel/`, `/p/` (posts), `/stories/` URLs
- Extract the highest-quality MP4 available
- If login is required for Stories, provide a **Cookies input** settings screen (see Section 8)
- Save to: `Android/data/com.vaultdrop.app/files/Downloads/Instagram/`

### 6.2 YouTube Downloads
- Use **yt-dlp** bundled binary (arm64-v8a + x86_64 ABIs) executed via ProcessBuilder
- Quality selection: 1080p / 720p / 480p — default 720p
- Merge audio+video if needed using bundled **FFmpeg** (ffmpeg-kit-android)
- Save to: `Android/data/com.vaultdrop.app/files/Downloads/YouTube/`

### 6.3 Download Service
- Run as a **Foreground Service** with a persistent notification
- Notification shows: app icon, active download title, progress percentage, speed
- Notification channel: `"VaultDrop Downloads"`, importance HIGH
- Queue up to **3 concurrent downloads**, rest are QUEUED
- On completion: fire a local notification `"✓ [Title] downloaded"` with thumbnail

### 6.4 Permissions Required
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" /> <!-- API 33+ -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

---

## 7. DATABASE SCHEMA (Room)

```kotlin
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,              // UUID
    val url: String,                          // original share URL
    val title: String,                        // video title
    val platform: String,                     // "INSTAGRAM" | "YOUTUBE"
    val status: String,                       // "QUEUED" | "ACTIVE" | "DONE" | "FAILED"
    val filePath: String?,                    // local file path after download
    val thumbnailPath: String?,              // local thumbnail path
    val fileSize: Long,                       // bytes
    val durationMs: Long,                     // milliseconds
    val progressPercent: Int,                 // 0–100
    val speedBps: Long,                       // bytes per second (during download)
    val errorMessage: String?,               // if FAILED
    val createdAt: Long,                      // epoch ms
    val completedAt: Long?                    // epoch ms, nullable
)
```

---

## 8. SETTINGS SCREEN

Accessible via a gear icon (`⚙`) in the top-right of the Downloads tab header.

**Settings list (grouped):**

**Downloads**
- Default Quality — `Low (480p)` / `Medium (720p)` / `High (1080p)`
- Download Location — show current path, tap to change
- Concurrent Downloads — 1 / 2 / 3 (segmented control)

**Instagram**
- Cookie / Session — text field for pasting browser cookies (needed for private stories)
- Info chip: `"Required for Stories and private content"`

**Storage**
- Storage Used — `"1.24 GB used"` with a subtle mini bar
- Clear Cache — button
- Delete All Downloads — destructive button, requires confirmation dialog

**About**
- App Version
- Credits / Licenses

---

## 9. ONBOARDING (First Launch Only)

3-step onboarding shown once using a **ViewPager2** with dot indicators:

**Step 1 — Share to Download**
Icon: ShareNetwork (Phosphor), 64dp, `#C8F04B`
Title: `"Just share the link"`
Body: `"Open any Instagram Reel or YouTube video and tap Share — choose VaultDrop from the list"`

**Step 2 — Watch Your Queue**
Icon: DownloadSimple, 64dp
Title: `"Track everything"`
Body: `"See live download progress, speed, and file size — all in one place"`

**Step 3 — Play Anytime`
Icon: Play, 64dp
Title: `"Your personal vault"`
Body: `"All your saved videos in a clean library, ready to play offline anytime"`

**CTA Button (last step only):** `"Let's Go"` — full-width pill, accent bg

---

## 10. TECH STACK SUMMARY

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 1.9+ |
| UI | Jetpack Compose (Material 3 with custom tokens) |
| Navigation | Navigation Compose |
| DI | Hilt |
| Database | Room |
| Networking | OkHttp + Retrofit |
| Video Player | Media3 / ExoPlayer |
| Download Engine | yt-dlp (bundled binary) + FFmpegKit |
| Image Loading | Coil |
| Concurrency | Kotlin Coroutines + Flow |
| Background Work | Foreground Service + WorkManager |
| Icons | Phosphor Icons (SVG → VectorDrawable) |
| Fonts | Google Fonts (DM Serif Display, DM Sans, JetBrains Mono) |

---

## 11. FILE NAMING CONVENTION

Downloaded files should be named:
```
[PLATFORM]_[YYYYMMDD]_[HHmm]_[sanitized-title-max-40-chars].mp4
```
Example:
```
INSTAGRAM_20250112_1430_my-amazing-reel.mp4
YOUTUBE_20250112_1502_how-to-cook-pasta-at-home.mp4
```

---

## 12. ERROR HANDLING

| Scenario | User-facing message |
|----------|---------------------|
| Invalid/unsupported URL | `"This link isn't supported"` |
| Network error | `"No connection — tap to retry"` |
| Private content (IG) | `"Login required — add cookies in Settings"` |
| Video unavailable (YT) | `"This video can't be downloaded"` |
| Storage full | `"Not enough storage — free up space"` |
| Download failed (generic) | `"Download failed — tap to retry"` |

---

## 13. CUSTOMIZATION NOTES

- **App name:** Replace all instances of `"VaultDrop"` with your chosen name. Update: `strings.xml`, `AndroidManifest.xml`, package name, Notification channel name, download folder name.
- **Accent color:** All accent uses reference a single Compose token `AccentPrimary`. Change `#C8F04B` to any color and it will propagate everywhere.
- **Logo:** Provide a 1024x1024 icon. Use a simple geometric mark — avoid complex detail as it doesn't work at small sizes in dark mode.

---

*End of VaultDrop Master Prompt — Version 1.0*
