# AgentOS for Android

Native Kotlin / Jetpack Compose port of `frontend/`. All app code is in one file:
`app/src/main/java/com/agentos/app/AgentOS.kt`. The Gradle and manifest files are the minimum Android needs to build it.

1. Start the backend (`scripts/start.ps1`) so it listens on port 8000. Bind to `0.0.0.0` for a physical phone.
2. Open `android/` in Android Studio and run the `app` configuration, or run `gradle :app:assembleDebug`.
3. On the sign-in screen set the server address: `http://10.0.2.2:8000` for the emulator, or `http://<your-PC-LAN-IP>:8000` for a phone.
   Demo login: `demo@agentos.local` / `AgentOS2026!`
