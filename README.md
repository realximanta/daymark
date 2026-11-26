# 📔 Daymark — Open-Source Journaling App for Android

<div align="center">
  <img src="https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&w=1200&q=80" alt="Daymark Journaling App" width="100%" />
  
  [![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
  [![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?logo=android&logoColor=white)](https://www.android.com)
  [![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
  [![GitHub](https://img.shields.io/badge/GitHub-realximanta%2Fdaymark-181717?logo=github&logoColor=white)](https://github.com/realximanta/daymark)
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
| **🔐 Secure Sync** | App opens freely, but syncing requires a secret passcode set in Preferences |
| **🌙 Dynamic Theming** | Beautiful Light & Dark modes (System, Light, or Dark) with smooth transitions |
| **📱 Modern UI** | Custom drawer navigation, hero panels, and rounded category cards |
| **🔒 Private & Secure** | Entries stored locally first, synced via GitHub App |
| **🤖 AI Formatting** | Groq-powered markdown structuring (never alters facts) |
| **⏰ Smart Reminders** | Configurable daily reminder notifications |
| **🔄 Background Sync** | Foreground service ensures reliable syncing |
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
```text
app/src/main/kotlin/com/ximanta/opendiary/
├── MainActivity.kt           # Dashboard UI + drawer + category grid
├── PasscodeActivity.kt       # Secure passcode entry & storage
├── SettingsActivity.kt       # Theme, reminders, passcode setup
├── TasksActivity.kt          # Multi-task editor for tasks/plans
├── SyncService.kt            # Foreground sync to backend
├── EntryDbHelper.kt          # SQLite local storage
├── Config.kt                 # Configuration constants
├── AlarmScheduler.kt         # Reminder scheduling
└── DaymarkPolish.kt          # UI animations & dynamic styling
```

### Backend Service (`daymark-app`)
```text
daymark-app/
├── server.js                 # Express.js REST API
├── render.yaml               # Render.com deployment config
├── package.json              # Dependencies
└── README.md                 # Backend documentation
```

**Data Flow:**
```text
Android App (Local DB) → HTTP POST (Bearer Token) → Backend Service → GitHub App Auth → GitHub Repo
```

---

## 🚀 Getting Started

### Part 1: Set Up the Backend (`daymark-app`)

#### Prerequisites
- Node.js 20+
- GitHub account
- GitHub App credentials
- Groq API key (free at [groq.com](https://groq.com))
- Render.com account (or any Node.js hosting)

#### Backend Setup Steps

1. **Create a GitHub App:** 
   - Go to [GitHub Developer Settings](https://github.com/settings/apps)
   - Grant **Contents: Read & write** on your private journal repo.
   - Note the **App ID**, **Installation ID**, and generate a **Private Key**.

2. **Deploy to Render:**
   - Connect your `daymark-app` repo to Render.
   - Add the following **Environment Variables**:
     ```
     DAYMARK_PASSCODE = <your-strong-secret-passcode>
     GH_APP_ID = <your-app-id>
     GH_PRIVATE_KEY = <your-private-key-with-newlines>
     GH_INSTALL_ID = <your-installation-id>
     GH_REPO_OWNER = <your-github-username>
     GH_REPO_NAME = <repo-name-for-journal-entries>
     GROQ_API_KEY = <your-groq-api-key>
     MODEL_NAME = openai/gpt-oss-20b
     ```

3. **Verify backend:**
   ```bash
   curl https://your-backend-url.onrender.com/health
   # Expected: {"status":"alive","time":...}
   ```

---

### Part 2: Build & Install the Android App (`daymark`)

#### Prerequisites
- Android Studio or Termux (with Android SDK tools)
- JDK 17+

#### Building via Android Studio (Recommended)
1. Clone the repo and open it in Android Studio.
2. Update `Config.kt`:
   ```kotlin
   const val BACKEND_URL = "https://your-backend-url.onrender.com"
   ```
3. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

#### Building via Termux (Gradle-free)
1. Follow the standard setup for Termux (`pkg install kotlin android-tools openjdk-17`).
2. Run the custom build script:
   ```bash
   chmod +x build.sh
   ./build.sh
   ```

> **⚠️ Package Conflict Warning:** If you switch between local builds (Termux/Android Studio) and CI builds (GitHub Actions), Android will reject the update because the APKs are signed with different debug keys. You must uninstall the previous app before installing the new one, or ensure your CI pipeline uses your local `debug.keystore`.

---

## 🔐 Configuration & Passcode

The app uses a two-tier access model:
1. **Open UI:** Anyone can open the app and type entries locally.
2. **Secured Sync:** To push entries to GitHub, the passcode must be set.

To set the passcode:
1. Open the app → Tap the hamburger menu (☰) → **Preferences**.
2. Scroll to the **Security** section → Tap **Set / Change passcode**.
3. Enter the exact string you configured in the `DAYMARK_PASSCODE` environment variable on Render.

Entries will now sync silently in the background using the `Authorization: Bearer <passcode>` header.

---

## 📱 User Guide

### First Launch
1. Grant **POST_NOTIFICATIONS** permission.
2. Set your passcode in Preferences (required for sync).
3. Select a category from the grid.
4. Type your entry and tap **Save**.

### Viewing Entries
- Entries sync every 15 minutes.
- Check your GitHub repo for synced entries:
  - `2026-10-19/currently-doing.md`
  - `2026-10-19/meals.md`

---

## 🛠️ Troubleshooting

| Issue | Solution |
|-------|----------|
| **"App not installed as package conflicts"** | You are trying to install an APK signed with a different key. Uninstall the old app first. |
| **Sync fails with 401 Unauthorized** | The passcode in Preferences does not match `DAYMARK_PASSCODE` on Render. |
| **Theme doesn't change** | Restart the app. The theme is applied in `onCreate()`. |
| **Entries not syncing** | Check your internet connection and verify the backend URL in `Config.kt`. |

---

## 🌟 Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Frontend** | Kotlin | 1.9+ |
| **Platform** | Android | 26+ (API level) |
| **Database** | SQLite | Built-in |
| **Backend** | Node.js + Express | 20+, 4.19 |
| **GitHub API** | Octokit | 21.0 |
| **AI** | Groq (OpenAI-compat) | gpt-oss-20b |

---

<div align="center">
  <p>
    <strong>Daymark</strong> — Open-source journaling, one entry at a time.<br>
    <a href="https://github.com/realximanta/daymark">⭐ Star on GitHub</a>
  </p>
  <p>Made with ❤️ by <a href="https://github.com/realximanta">@realximanta</a></p>
</div>
