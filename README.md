# Masquerade

Experimental Android interface for AI Dungeon. Search and local bookmarks use native screens. Sign-in and gameplay use an embedded AI Dungeon page.

Build with JDK 17, Gradle 8.9, and Android SDK 35.

```sh
gradle -p android assembleDebug
```

The debug APK is generated under `android/app/build/outputs/apk/debug/`. See [DESIGN.md](DESIGN.md) for the prototype and integration notes.

The GitHub-hosted UI updater is planned.
