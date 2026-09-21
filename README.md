# Pantry-Grocery

Native Android app prototype combining pantry inventory and a grocery list.

## Current milestone

- Kotlin + Jetpack Compose + Material 3 UI
- Room database entities, DAOs, database singleton, repository, and ViewModels
- Home screen navigation to Pantry and Grocery screens
- Add/delete pantry placeholder flow
- Add/check grocery placeholder flow
- Low-stock query in the Room DAO (`quantity <= threshold`)
- Reproducible Gradle wrapper

Barcode scanning, restock confirmation, suggestions UI, reminders, export, and sync remain next milestones.

## Build

```bash
./gradlew :app:assembleDebug
```

The verified APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected Android device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The project uses the Android SDK configured in `local.properties` on this machine. Do not commit that file; it is ignored by Git.
