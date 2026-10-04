# 📔 Daymark — Open-Source Journaling App for Android

<div align="center">
  <img src="https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&w=1200&q=80" alt="Daymark Journaling App" width="100%" />
  
  [![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
  [![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?logo=android&logoColor=white)](https://www.android.com)
  [![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
  [![GitHub](https://img.shields.io/badge/GitHub-realximanta%2Fdaymark-181717?logo=github&logoColor=white)](https://github.com/realximanta/daymark)
  [![Made with ❤️](https://img.shields.io/badge/Made%20with-%E2%9D%A4%EF%B8%8F-red)](https://github.com/realximanta)
</div>

---

## 🎯 Overview

**Daymark** is a lightweight, privacy-first journaling application for Android that enables you to capture daily thoughts across 9 intuitive categories. Each entry is stored locally, synced securely to your GitHub repository via a dedicated backend service, and formatted with AI-assisted markdown.

Perfect for:
- 📅 Daily reflection & journaling
- 📊 Tracking habits & mood
- 🎯 Recording goals & achievements
- 🍽️ Logging meals & health
- 🌍 Documenting travels
- 💡 Capturing ideas on the go

---

## ✨ Key Features

| Feature | Details |
|---------|---------|
| **📱 Lightweight UI** | Minimal, intuitive grid-based category interface |
| **🔒 Private & Secure** | Entries stored locally first, synced via GitHub App |
| **🤖 AI Formatting** | Groq-powered markdown structuring (never alters facts) |
| **⏰ Smart Reminders** | Configurable daily reminder notifications |
| **🔄 Background Sync** | Foreground service ensures reliable syncing |
| **🚀 Zero Gradle** | Pure Kotlin, aapt2, kotlinc — no build framework bloat |
| **📦 Offline-First** | Works without internet; queues entries for sync |

---

## 📚 Supported Categories

The app provides 9 pre-configured journaling categories:

1. **Currently Doing** — What you're working on right now
2. **What I Ate** — Meal tracking & nutrition notes
3. **New Idea** — Ideas, inspiration, creative thoughts
4. **Schedule/Tasks** — Daily tasks & to-do lists
5. **Regret** — Reflections on mistakes & learnings
6. **Success of the Day** — Wins, achievements, celebrations
7. **Travelled To** — Travel logs & location notes
8. **Plan for Tomorrow** — Tomorrow's agenda & intentions
9. **Watched** — Movies, shows, educational content

---

## 🏗️ Architecture

### Android App (`daymark`)
```
src/com/ximanta/opendiary/
├── MainActivity.kt           # Grid UI + category selection
├── TasksActivity.kt          # Multi-task editor for tasks/plans
├── SyncService.kt            # Foreground sync to backend
├── EntryDbHelper.kt          # SQLite local storage
├── Config.kt                 # Configuration constants
├── AlarmScheduler.kt         # Reminder scheduling
├── BootReceiver.kt           # Boot completion handling
└── ReminderReceiver.kt       # Daily reminder trigger
```

### Backend Service (`daymark-app`)
```
daymark-app/
├── server.js                 # Express.js REST API
├── render.yaml               # Render.com deployment config
├── package.json              # Dependencies
└── README.md                 # Backend documentation
```

**Data Flow:**
```
Android App (Local DB) → HTTP POST → Backend Service → GitHub App Auth → GitHub Repo
```

---

## 🚀 Getting Started

### Part 1: Set Up the Backend (`daymark-app`)

Before building the Android app, you must deploy the backend service.

#### Prerequisites
- Node.js 18+
- GitHub account
- GitHub App credentials
- Groq API key (free at [groq.com](https://groq.com))
- Render.com account (or any Node.js hosting)

#### Backend Setup Steps

1. **Clone the backend repository:**
   ```bash
   git clone https://github.com/realximanta/daymark-app.git
   cd daymark-app
   ```

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Create a GitHub App:**
   - Go to [GitHub Developer Settings](https://github.com/settings/apps)
   - Click **New GitHub App**
   - Fill in app name: `Daymark`
   - Set **Webhook URL** to your backend domain (Render): `https://your-backend-url.onrender.com/health`
   - Uncheck **Active** for webhooks (not needed)
   - **Repository permissions:**
     - Contents: Read & write
   - Click **Create GitHub App**
   - Note your **App ID** and **Installation ID**
   - Generate & download **Private Key** (RSA format)

4. **Get a Groq API Key:**
   - Visit [Groq Console](https://console.groq.com)
   - Create an API key
   - Copy it

5. **Deploy to Render:**
   - Push the repo to GitHub
   - Connect to [Render Dashboard](https://dashboard.render.com)
   - Click **New +** → **Web Service**
   - Select your `daymark-app` repo
   - Name: `daymark-backend`
   - Build command: `npm install`
   - Start command: `npm start`
   - Plan: **Free** (sufficient for journaling)
   - Add **Environment Variables:**
     ```
     GH_APP_ID = <your-app-id>
     GH_PRIVATE_KEY = <your-private-key-with-newlines>
     GH_INSTALL_ID = <your-installation-id>
     GH_REPO_OWNER = <your-github-username>
     GH_REPO_NAME = <repo-name-for-journal-entries>
     GROQ_API_KEY = <your-groq-api-key>
     MODEL_NAME = openai/gpt-oss-20b
     NODE_VERSION = 20.11.0
     ```
   - Click **Deploy**
   - Copy the **Render URL** (e.g., `https://daymark-backend.onrender.com`)

6. **Verify backend is running:**
   ```bash
   curl https://your-backend-url.onrender.com/health
   # Expected: {"status":"alive","time":...}
   ```

**Backend Tech Stack:**
- **Express.js** — Lightweight HTTP server
- **Octokit** — GitHub API client
- **JWT** — GitHub App authentication
- **Groq API** — AI-powered entry formatting
- **Render** — Free Node.js hosting

---

### Part 2: Build & Install the Android App (`daymark`)

#### Prerequisites
- Termux (on Android device, or Linux/WSL)
- Android SDK tools (aapt2, d8, zipalign)
- Kotlin compiler (`kotlinc`)
- OpenJDK 8+
- Bash shell

#### Installation in Termux

If you don't have a build environment, set up Termux:

1. **Install Termux** from F-Droid or GitHub Releases
2. **Update & install tools:**
   ```bash
   pkg update && pkg upgrade
   pkg install git curl openssl openssl-tool
   pkg install kotlin android-tools
   pkg install openjdk-17
   ```

3. **Get Android framework JAR:**
   ```bash
   mkdir -p $HOME/android-framework
   cd $HOME/android-framework
   # Download android.jar for API level 34
   curl -L -o android.jar \
     "https://github.com/Kirlif/Android-Framework-jar/raw/main/34/android.jar"
   ```

#### Build Steps

1. **Clone the repository:**
   ```bash
   cd $HOME
   git clone https://github.com/realximanta/daymark.git
   cd daymark
   ```

2. **Configure backend URL:**
   Edit `src/com/ximanta/opendiary/Config.kt`:
   ```kotlin
   const val BACKEND_URL = "https://your-backend-url.onrender.com"
   const val SECRET = "your-shared-secret-key"
   const val DEVICE_ID = "daymark-phone-1"
   ```
   - `BACKEND_URL` → Your Render backend URL
   - `SECRET` → Optional shared secret (for request validation)
   - `DEVICE_ID` → Unique identifier for this device

3. **Run the build script:**
   ```bash
   chmod +x build.sh
   ./build.sh
   ```

   **Build Process:**
   - ✅ aapt2 compiles resources → `res.zip`
   - ✅ aapt2 links resources → `base.apk`
   - ✅ kotlinc compiles Kotlin → `.class` files
   - ✅ d8 dexes → `.dex` files
   - ✅ zipalign packs APK
   - ✅ apksigner signs with debug keystore
   - ✅ apksigner verifies

4. **Output:**
   ```
   === BUILD OK ===
   APK: /root/Daymark.apk
   ```

5. **Install on device:**
   ```bash
   # Via adb (if available)
   adb install $HOME/Daymark.apk
   
   # Or manual install:
   # - Transfer Daymark.apk to device
   # - Open file manager → tap APK → Install
   ```

---

## 📖 Build Script Details (`build.sh`)

The custom build pipeline is Termux-optimized and **Gradle-free**:

| Stage | Tool | Input | Output |
|-------|------|-------|--------|
| 1 | `aapt2 compile` | `res/` | `res.zip` |
| 2 | `aapt2 link` | `res.zip` + manifest | `base.apk` |
| 3 | `kotlinc` | `src/**/*.kt` | `.class` files |
| 4 | `javac` | `src/**/*.java` (optional) | `.class` files |
| 5 | `d8` | `.class` files | `.dex` files |
| 6 | `zip` | `base.apk` + `.dex` | `base.apk` (with dex) |
| 7 | `zipalign` | APK | Aligned APK (optional) |
| 8-9 | `apksigner` | APK + keystore | Signed & verified APK |

**Key settings:**
- Min API: 26 (Android 8.0)
- Target API: 34 (Android 14)
- Kotlin JVM target: 1.8
- Memory: -Xmx1536m -Xss4m

**Troubleshooting:**
- If `kotlin-stdlib.jar` not found: `pkg install kotlin`
- If `android.jar` not found: Download from official source (see above)
- If d8 fails: Ensure OpenJDK 8+ is installed

---

## 🔧 Configuration Guide

### Android App (`Config.kt`)
```kotlin
const val BACKEND_URL = "https://your-backend-url.onrender.com"  // ← Set this
const val SECRET = "REPLACE_WITH_YOUR_SECRET"                    // ← Optional
const val DEVICE_ID = "daymark-phone-1"                          // ← Device identifier
const val SYNC_INTERVAL_MS = 15L * 60L * 1000L                   // Every 15 min
const val DEFAULT_REMINDER_HOUR = 21                             // 9 PM reminder
const val DEFAULT_REMINDER_MIN = 0
```

### Backend (`server.js`)
- **`/entry` POST endpoint** accepts:
  ```json
  {
    "category": "currently-doing",
    "rawText": "Built the app UI",
    "timestamp": 1720000000000
  }
  ```
- Stores entries as `YYYY-MM-DD/category.md` in GitHub
- AI formatting via Groq (preserves facts)
- Appends new entries with `---` separator

---

## 📱 User Guide

### First Launch
1. Grant **POST_NOTIFICATIONS** permission
2. Select a category from the grid
3. Type your entry in the dialog
4. Tap **Save**

### Viewing Entries
- Entries sync every 15 minutes (or manually via app restart)
- Check your GitHub repo for synced entries:
  - `2026-10-04/currently-doing.md`
  - `2026-10-04/meals.md`
  - etc.

### Reminders
- Default reminder: **9 PM daily**
- Configurable via shared preferences (future UI enhancement)

---

## 📂 Project Structure

```
daymark/
├── AndroidManifest.xml           # App manifest (permissions, components)
├── build.sh                      # Custom build script
├── README.md                     # This file
├── src/
│   └── com/ximanta/opendiary/
│       ├── MainActivity.kt       # Main UI (grid categories)
│       ├── TasksActivity.kt      # Tasks/plans screen
│       ├── SyncService.kt        # Background HTTP sync
│       ├── EntryDbHelper.kt      # SQLite wrapper
│       ├── Config.kt             # Constants
│       ├── AlarmScheduler.kt     # Alarm management
│       ├── BootReceiver.kt       # Boot handler
│       ├── ReminderReceiver.kt   # Reminder trigger
│       └── Entry.kt              # Data class
└── res/
    ├── drawable/                 # Icon assets
    ├── layout/                   # XML UI layouts
    └── values/
        ├── strings.xml           # App strings
        └── colors.xml            # Color palette
```

---

## 🔐 Permissions

The app requests:
- `INTERNET` — Sync entries to backend
- `ACCESS_NETWORK_STATE` — Check connectivity
- `POST_NOTIFICATIONS` — Daily reminders
- `RECEIVE_BOOT_COMPLETED` — Start on device boot
- `WAKE_LOCK` — Keep service alive
- `FOREGROUND_SERVICE` — Background sync
- `FOREGROUND_SERVICE_DATA_SYNC` — Sync-specific foreground

---

## 💾 Data Storage

### Local Database
- **SQLite**: `daymark.db`
- **Table**: `entries` (id, category, text, timestamp, synced)
- **Retention**: Entries kept locally; synced to GitHub

### GitHub Storage
- **Path structure**: `YYYY-MM-DD/category.md`
- **Example**: `2026-10-04/currently-doing.md`
- **Append-only**: New entries add sections separated by `---`

---

## 🚀 Deployment Checklist

- [ ] Backend deployed to Render.com
- [ ] GitHub App created with repo permissions
- [ ] Groq API key obtained
- [ ] Environment variables set on Render
- [ ] Backend health check: `GET /health` ✅
- [ ] `Config.kt` updated with backend URL
- [ ] Termux build environment set up
- [ ] APK built successfully
- [ ] App installed & launched
- [ ] Test entry created & synced to GitHub

---

## 🛠️ Troubleshooting

| Issue | Solution |
|-------|----------|
| Build fails: "kotlinc not found" | `pkg install kotlin` in Termux |
| Build fails: "android.jar not found" | Download from GitHub (see above) |
| APK won't install | Enable "Install from unknown sources" in security settings |
| Entries not syncing | Check backend URL in `Config.kt`; verify backend is running |
| Sync service crashes | Check logcat: `adb logcat \| grep Daymark` |
| Backend 500 error | Verify GitHub App credentials & Groq API key |
| No reminder notification | Ensure `POST_NOTIFICATIONS` permission granted |

---

## 🌟 Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Frontend** | Kotlin | 1.9+ |
| **Platform** | Android | 26+ (API level) |
| **Database** | SQLite | Built-in |
| **Backend** | Node.js + Express | 18+, 4.19 |
| **GitHub API** | Octokit | 21.0 |
| **AI** | Groq (OpenAI-compat) | gpt-oss-20b |
| **Auth** | JWT (GitHub App) | jsonwebtoken 9.0.2 |
| **Hosting** | Render | Free tier |

---

## 📄 Licenses & Attribution

- **Daymark App**: MIT License
- **Kotlin**: Apache 2.0
- **Android SDK**: Apache 2.0
- **Express.js**: MIT
- **Octokit**: MIT
- **Groq**: API terms apply
- **Stock images**: Unsplash (free license)

---

## 🤝 Contributing

Found a bug or have a feature request?

1. Open an issue on GitHub
2. Fork the repository
3. Create a feature branch
4. Submit a pull request

---

## 📞 Support & Feedback

- **GitHub Issues**: [realximanta/daymark/issues](https://github.com/realximanta/daymark/issues)
- **GitHub Discussions**: [realximanta/daymark/discussions](https://github.com/realximanta/daymark/discussions)
- **Email**: Check your GitHub profile

---

## 🎓 Learning Resources

- [Android Manifest Guide](https://developer.android.com/guide/topics/manifest/manifest-intro)
- [Kotlin Language Docs](https://kotlinlang.org/docs/)
- [Express.js Getting Started](https://expressjs.com/starter/installing.html)
- [GitHub App Authentication](https://docs.github.com/en/developers/apps/building-github-apps/authenticating-with-github-apps)
- [Termux Documentation](https://termux.com)

---

<div align="center">
  <p>
    <strong>Daymark</strong> — Open-source journaling, one entry at a time.<br>
    <a href="https://github.com/realximanta/daymark">⭐ Star on GitHub</a> •
    <a href="https://github.com/realximanta/daymark/issues">🐛 Report Issues</a> •
    <a href="https://github.com/realximanta">👤 More Projects</a>
  </p>
  
  <img src="https://github.com/realximanta/daymark/blob/main/assets/developer.jpg?auto=format&fit=crop&w=400&q=60" alt="Contributor photo" width="50" style="border-radius: 50%" />
  
  **Made with ❤️ by [@realximanta](https://github.com/realximanta)**
</div>
