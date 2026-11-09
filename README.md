# Masquerade

Experimental Android interface for AI Dungeon. Search and local bookmarks use native screens. Sign-in and gameplay use an embedded AI Dungeon page.

Build with JDK 17, Gradle 8.9, and Android SDK 35.

```sh
gradle -p android assembleDebug
```

The debug APK is generated under `android/app/build/outputs/apk/debug/`. See [DESIGN.md](DESIGN.md) for the prototype and integration notes.

## UI updates

The APK checks the signed configuration in `updates/android-ui.json` on launch and applies a newer verified version on the next launch. The first schema supports discovery labels. Layout, gameplay, and browser-bridge changes still require an APK rebuild.

To publish, increment the payload version and run `node tools/sign-ui.cjs sign <private-key.pem> <payload.json> updates/android-ui.json`. Keep the signing key out of Git. The public key is pinned in `UiUpdate.java`.
