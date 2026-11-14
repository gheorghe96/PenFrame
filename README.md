# PenFrame

A native Android app for designing app screens on phones and tablets, built for the S Pen.
Place real device screens on a canvas, drop in Material 3 components, and style them.

* Kotlin + Jetpack Compose, minSdk 28, compileSdk 37
* Projects are JSON in app storage (`data/ProjectRepository.kt`), autosaved
* Everything on the canvas is drawn by one renderer (`render/`), so the canvas, thumbnails,
  previews and PNG export match

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` points `sdk.dir` at your Android SDK.
