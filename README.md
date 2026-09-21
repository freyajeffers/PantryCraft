# Pantry-Grocery

Native Android prototype: combined Pantry inventory + Grocery list.

Features included in this scaffold:
- Jetpack Compose UI skeleton
- Room entities & DAOs for PantryItem and GroceryItem
- MainActivity + Compose screens (Pantry, Grocery)
- Material3 theming
- Barcode support left as stretch (ML Kit integration notes in README)

How to build
1. Open this folder in Android Studio (Arctic Fox or later).
2. Let Android Studio upgrade Gradle/Gradle wrapper if needed and install SDK components.
3. Run the app on an emulator or device.

Notes
- This is a scaffold with core Kotlin source files and a basic Gradle setup. If you want I can add ML Kit barcode sample integration next.
- APK build may require adding the Android Gradle Plugin and the correct Gradle wrapper via Android Studio.

Paths of interest:
- app/src/main/java/com/freya/pantrygrocery — source code
- app/src/main/res — resources (theme previews stubbed)

Next steps I can take now (pick any):
- Add ML Kit barcode scanning integration (camera + scanner) and sample UI.
- Implement Room migrations and sample seed data.
- Try an automated debug build here (may fail if Android SDK not installed).