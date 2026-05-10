# AndroidClaw

AI-powered Android assistant that fully controls your phone. A smarter replacement for Google Assistant with 115+ device actions, powered by Claude or local on-device LLMs.

## Features

### AI Providers
- **Claude (Cloud)** - Most powerful. Full tool calling, web search, 115+ actions
- **On-Device (Private)** - Runs locally on your phone via MediaPipe. No internet needed after model download
- **Custom Server (Pro)** - Connect to Ollama, LM Studio, or any OpenAI-compatible endpoint

### 115+ Device Actions

**Device Settings** - Wi-Fi, Bluetooth, flashlight, brightness, volume, ringer mode (silent/vibrate/normal), speakerphone, screen timeout, dark mode, battery saver, auto-rotate, DND

**Communication** - Search/add contacts, send/read SMS, make/answer/reject calls, call log, messaging via 50+ apps (WhatsApp, Telegram, Signal, Viber, Messenger, Instagram, Snapchat, Discord, Slack, Teams, Gmail, etc.)

**Media** - Play/pause, next, previous, stop. Spotify search & play, YouTube search, song identification (Shazam)

**Navigation** - Google Maps directions, go home/back/recents, split screen, power menu

**System** - Screenshot, screen record, quick settings, notifications, open any settings page (20+ pages), DND, translate, QR scan

**Camera** - Open camera, take photo, scan QR codes

**Personal Data** - Calendar events (view/create), reminders, location, contacts, notes (Google Keep)

**App Management** - Launch, search, list apps, uninstall, force stop, app info, clear data, default apps

**Device Info** - Battery (%, temp, health), storage, network (Wi-Fi name, IP, speed), Bluetooth devices, SIM/carrier, RAM, uptime

**Utility** - Alarms, timers, stopwatch, calculator, date/time, coin flip, dice roll, random number, countdown, web search, read webpage, code execution, voice recording, speed test, cast screen, incognito browsing, find my phone, read aloud (TTS), flashlight SOS, email, wallpaper, font size

### Additional Features
- **Animated cat mascot** - Pixel art cat that walks when thinking, runs when executing tools
- **Sound effects** - Audio feedback for sending messages, tool completion, task done
- **Voice input** - Android SpeechRecognizer for voice commands
- **Auto-send messages** - Accessibility service to automatically tap send in WhatsApp/Telegram/Signal
- **Firebase Crashlytics** - Crash reporting and error tracking
- **Home screen widget** - Quick access to start conversations

## Tech Stack

- **Kotlin Multiplatform** (KMP) - Shared business logic for Android & iOS
- **Jetpack Compose** - Modern declarative UI
- **Ktor** - HTTP client for API communication
- **SQLDelight** - Local database for conversations
- **Koin** - Dependency injection
- **MediaPipe** - On-device LLM inference
- **Firebase** - Crashlytics & Analytics

## Project Structure

```
AndroidClaw/
├── shared/                          # KMP shared module
│   ├── commonMain/
│   │   ├── agent/                   # AgentLoop, AgentConfig, ContextManager
│   │   ├── llm/                     # ClaudeStreamingClient, LocalLlmStreamingClient, models
│   │   ├── logging/                 # KMP Logger (expect/actual)
│   │   ├── tools/                   # 15 tool classes + DeviceActionBridge interface
│   │   ├── memory/                  # ConversationRepository
│   │   └── di/                      # Koin modules
│   ├── androidMain/                 # Android Logger (android.util.Log)
│   └── iosMain/                     # iOS Logger (NSLog) + bridge stubs
├── androidApp/                      # Android application
│   ├── src/androidMain/
│   │   └── AndroidManifest.xml      # 48 permissions, services, receivers
│   └── src/main/kotlin/
│       ├── platform/                # AndroidDeviceActionBridge (2600+ lines)
│       ├── ui/chat/                 # ChatScreen, ChatViewModel
│       ├── ui/settings/             # SettingsScreen with provider picker
│       ├── ui/onboarding/           # 4-page onboarding with provider selection
│       ├── ui/components/           # CatMascot animated sprite
│       ├── voice/                   # SpeechRecognizer, VoicePipeline
│       ├── audio/                   # SoundManager
│       ├── llm/                     # OnDeviceLlmEngine, ModelDownloadManager
│       ├── service/                 # AutoSendAccessibilityService
│       └── admin/                   # DeviceAdminReceiver
└── backend/                         # Optional Ktor backend server
```

## Setup

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 35

### Build & Run

1. Clone the repo:
   ```bash
   git clone https://github.com/xxqxpxx/AndroidClaw.git
   cd AndroidClaw
   ```

2. Create `local.properties` with your config:
   ```properties
   sdk.dir=/path/to/android/sdk
   anthropic.api.key=your-api-key-here
   ```

3. Add `google-services.json` from [Firebase Console](https://console.firebase.google.com) to `androidApp/` (optional, for crash reporting)

4. Build and install:
   ```bash
   ./gradlew :androidApp:installRelease
   ```

### Initial Setup

On first launch, the app will:
1. Request all required permissions (grant them all)
2. Show onboarding to choose an AI provider
3. Download models if using on-device inference

### Permissions Explained
- **Contacts/Phone/SMS** — For messaging and calling actions
- **Location** — For map navigation
- **Calendar** — For viewing/creating events
- **Microphone** — For voice input
- **Camera** — For taking photos and scanning QR codes
- **Accessibility** — For auto-sending messages (opt-in)
- **Device Admin** — For advanced control (opt-in)

## LLM Provider Configuration

### Claude (Cloud)
Enter your Anthropic API key in Settings or during onboarding. Get one at [console.anthropic.com](https://console.anthropic.com).

### On-Device (Private)
Download a Gemma 2B model (~1.4 GB) for fully offline, privacy-first inference. No data leaves your phone.

### Custom Server (Pro)
Connect to any OpenAI-compatible endpoint:
- **Ollama**: `http://YOUR_PC_IP:11434`, model: `llama3.2`
- **LM Studio**: `http://YOUR_PC_IP:1234`, model: auto-detected
- **llama.cpp server**: `http://YOUR_PC_IP:8080`

## Google Play Compliance
- Targets Android 15 (API 35)
- 16 KB page size compatible (AGP 8.5.2, `jniLibs.useLegacyPackaging = false`)
- Adaptive launcher icon

## Troubleshooting

### App crashes on startup
- Check `Logcat` in Android Studio for detailed errors
- Ensure you have the correct API key configured
- Try clearing app data: Settings > Apps > AndroidClaw > Storage > Clear Data

### Tools not working
- Verify all required permissions are granted
- For SMS/calls: ensure Phone and Contacts permissions are enabled
- For messaging apps: enable Accessibility Service in Settings

### On-device LLM runs slowly
- Reduce context window or max tokens in Settings
- Close background apps to free RAM
- Use a model optimized for your device (Gemma 2B recommended)

### Model download stuck
- Check internet connection and free storage (min 2 GB)
- Try again—downloads resume from last completed chunk
- Use custom server for faster testing

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Quick Start for Contributors
1. Read [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
2. Fork the repo and create a feature branch
3. Follow the code style and add tests
4. Submit a pull request with a clear description

## Support

- **Questions?** Open a [GitHub Discussion](../../discussions)
- **Bug report?** File a [GitHub Issue](../../issues)
- **Security concern?** Please email maintainers privately

## Roadmap

- [ ] iOS app (Compose Multiplatform support)
- [ ] Desktop companion app (web UI for management)
- [ ] Plugin system for custom device actions
- [ ] Cloud sync for multi-device orchestration
- [ ] Improved model quantization for faster on-device inference

## License

This project is licensed under the **MIT License** — see [LICENSE](LICENSE) for details.

---

**Maintained by** [Ahmed Abdul Fatah](https://github.com/xxqxpxx) and contributors ✨
