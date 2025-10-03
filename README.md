# VoiceCamera

VoiceCamera is a university project demonstrating voice-triggered photo and video capture on Android. It uses the Pocketsphinx engine for offline keyword spotting and AndroidX CameraX for media capture.

## Features
- Wake-word spotting using Pocketsphinx.
- Voice commands to take photos and record short videos.
- Saves media to device storage and updates the gallery.
- Simple UI with flash toggle and camera flip.

## Why this project
Developed as a university assignment to explore embedded/offline speech recognition on mobile devices and integrate it with modern camera APIs.

## Key components
- Speech recognition and keyword spotting: Pocketsphinx (assets and models under the `models/` module).
- Camera capture and MediaStore integration: implemented in the app module.
- Main logic and voice handling: [`edu.cmu.pocketsphinx.app.MainActivity`](app/src/main/java/edu/cmu/pocketsphinx/app/MainActivity.java)
- Help screen: [`edu.cmu.pocketsphinx.app.HelpActivity`](app/src/main/java/edu/cmu/pocketsphinx/app/HelpActivity.java)

## Files of interest
- Pocketsphinx model assets: [models/assets.xml](models/assets.xml)
- Pocketsphinx Android AAR: [aars/pocketsphinx-android-5prealpha-release.aar](aars/pocketsphinx-android-5prealpha-release.aar)
- App module build file: [app/build.gradle](app/build.gradle)

## Build & run
- Open the project in Android Studio (recommended) or use Gradle from the command line.
- To build from the terminal:
```sh
./gradlew assembleDebug
```
- Install/run on a physical Android device (camera + microphone required).

## Permissions
The app requests:
- CAMERA
- RECORD_AUDIO
- WRITE_EXTERNAL_STORAGE (on older Android versions)

Permissions handling and startup checks are implemented in [`edu.cmu.pocketsphinx.app.MainActivity`](app/src/main/java/edu/cmu/pocketsphinx/app/MainActivity.java).

## Usage
1. Grant requested permissions when the app starts.
2. Speak the configured wake word (see keyword files in the `models/` directory).
3. On keyword detection the app emits beeps and then captures photo or video as implemented in [`edu.cmu.pocketsphinx.app.MainActivity`](app/src/main/java/edu/cmu/pocketsphinx/app/MainActivity.java).

## Notes
- Targets Android devices and uses CameraX APIs.
- Speech models and keyword files are kept in the `models/` directory and are loaded at runtime.

## License & Credits
This is a university project. It uses the Pocketsphinx project for offline recognition. See project files for acknowledgements.
