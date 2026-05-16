# 🎬 YouTube (No Ads) — CloudStream Plugin

A CloudStream 3 extension that plays YouTube videos **completely ad-free** by routing streams through [Invidious](https://invidious.io/) open-source instances. Falls back to a privacy-enhanced YouTube embed WebView when Invidious is unavailable.

---

## ✨ Features

| Feature | Detail |
|---|---|
| **Zero ads** | Direct MP4/WebM streams from Invidious — no YouTube ad pipeline at all |
| **Multi-quality** | 144p → 2160p (4K) where available |
| **Multi-instance** | Automatically tries 5 Invidious instances, picks the fastest |
| **WebView fallback** | Uses `youtube-nocookie.com` embed if every Invidious instance is down |
| **Home page** | Trending (Default / Music / Gaming / Movies) |
| **Search** | Full YouTube search via Invidious API |
| **No API key** | No Google account or API key required |

---

## 🚀 Install (End Users)

1. Open **CloudStream 3** → **Settings** → **Extensions** → **Add Repository**
2. Paste this URL:
   ```
   https://raw.githubusercontent.com/YOUR_GITHUB_USERNAME/YouTubeCSPlugin/builds/CS.json
   ```
3. Tap **Download**, then install the **YouTube (No Ads)** plugin from the repo.
4. Search or browse the Trending home page — done!

---

## 🔧 Build From Source (Developers)

### Prerequisites
- JDK 17 or later  
- Android SDK (API 35)  
- Git

### Steps

```bash
# 1. Clone the repo
git clone https://github.com/YOUR_GITHUB_USERNAME/YouTubeCSPlugin
cd YouTubeCSPlugin

# 2. Build the plugin
#    Linux / Mac:
./gradlew YouTubeProvider:make

#    Windows:
gradlew.bat YouTubeProvider:make

# 3. The compiled .cs3 file appears at:
#    YouTubeProvider/build/YouTubeProvider.cs3

# 4. Deploy directly to a connected Android device (optional):
./gradlew YouTubeProvider:deployWithAdb
```

> **First run on Android 11+**: Grant CloudStream "All Files Access" so it can load local plugins:
> ```
> adb shell appops set --uid com.lagradost.cloudstream3 MANAGE_EXTERNAL_STORAGE allow
> ```

---

## ☁️ Auto-Deploy via GitHub Actions

Push to `master` / `main` and the workflow in `.github/workflows/build.yml` will:
1. Compile the plugin
2. Hash the `.cs3` file and write `plugins.json`
3. Push everything to the `builds` branch
4. The `CS.json` repo URL then works for all users

### Required GitHub repository setting
Go to **Settings → Actions → General → Workflow permissions** and enable **"Read and write permissions"**.

---

## 📁 Project Structure

```
YouTubeCSPlugin/
├── .github/workflows/build.yml          # CI: build → publish to builds branch
├── YouTubeProvider/
│   ├── build.gradle.kts                 # Plugin metadata (name, author, icon…)
│   └── src/main/kotlin/com/yourplugin/youtube/
│       └── YouTubeProvider.kt           # All plugin logic
├── CS.json                              # Repo manifest (users paste this URL)
├── plugins.json                         # Plugin list template (overwritten by CI)
├── build.gradle.kts                     # Root Gradle config
├── settings.gradle.kts
├── gradlew / gradlew.bat
└── gradle/wrapper/gradle-wrapper.properties
```

---

## ⚙️ Customisation

### Change your name / repo in `YouTubeProvider/build.gradle.kts`
```kotlin
authors    = listOf("YourName")
setRepo(System.getenv("GITHUB_REPOSITORY") ?: "YourName/YouTubeCSPlugin")
```

### Add more Invidious instances in `YouTubeProvider.kt`
```kotlin
private val instances = listOf(
    "https://inv.nadeko.net",
    "https://your-preferred-instance.tld",
    // …
)
```

A list of public Invidious instances: https://api.invidious.io/

---

## ❓ FAQ

**Q: Will this get banned by YouTube?**  
A: The plugin only reads public data via Invidious, which uses the same requests a browser would. It doesn't violate CloudStream's terms.

**Q: Why can't I play some videos?**  
A: Age-restricted or region-locked videos require a logged-in Invidious instance. The WebView fallback should still work for most.

**Q: The stream quality is lower than expected.**  
A: Select a higher quality in CloudStream's built-in quality picker (three-dot menu while playing). The plugin exposes all available formats.

**Q: Invidious is down / slow.**  
A: The plugin automatically fails over to the next instance in the list. If all are down, it falls back to the youtube-nocookie.com embed WebView.

---

## 📜 License

Released into the public domain. Use freely.
