# TeenDrive Android

Native Android version of TeenDrive built with Kotlin, Jetpack Compose, Material 3, Firebase, Google Maps, CameraX, ML Kit, Room, DataStore, WorkManager, and a foreground location service.

The Android app reuses the existing TeenDrive Firebase project and keeps the Firestore model compatible with the iOS app.

## Current Features

- First launch role selection for Teen or Parent. The chosen role is locked in local account state.
- Teen drive dashboard opens first for teen accounts.
- Live drive tracking with Fused Location Provider and a foreground service notification:
  `"Teen Drive is tracking an active drive"`.
- Google Maps drive UI with current location, route polyline, event pins, map gestures, and normal/satellite toggle.
- Active drive session state tracks route points, distance, top speed, duration, speed limit when available, and alert-ready state.
- Completed drives save locally to Room as TeenTrip records.
- Reports screen shows saved trips, trip detail basics, route map, and event pins.
- Local-to-Firestore trip sync runs automatically and respects deleted-trip tombstones.
- Parent trip listener can show synced teen reports for paired parents.
- Firebase anonymous sign-in, teen/parent profile sync, family group creation, QR payload generation, CameraX + ML Kit QR scanning, and pairing token claim flow.
- FCM token storage and notification event writes for parent safety alerts.
- Profile screen includes cloud state, QR pairing, privacy language, and account controls.

## Firebase Setup

Register an Android app inside the existing Firebase project:

```text
com.vlad8110.teendrive
```

Download `google-services.json` from Firebase and place it at:

```text
app/google-services.json
```

Enable these Firebase products:

- Authentication with Anonymous sign-in.
- Cloud Firestore.
- Firebase Cloud Messaging.

Firestore paths are kept compatible with iOS:

```text
familyGroups/{familyGroupID}
familyGroups/{familyGroupID}/pairingTokens/{tokenID}
familyGroups/{familyGroupID}/teens/{teenID}
familyGroups/{familyGroupID}/teens/{teenID}/trips/{tripID}
familyGroups/{familyGroupID}/teens/{teenID}/activeDrive/current
teenProfiles/{teenID}
parentProfiles/{parentID}
notificationEvents/{eventID}
```

See `FIREBASE_ANDROID_SETUP.md` for the Firebase console checklist and Firestore rule notes.

## Android Permissions

The app declares:

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`
- `POST_NOTIFICATIONS`
- `CAMERA`
- `ACTIVITY_RECOGNITION`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`

Background tracking depends on the foreground service notification during an active drive.

## Run The App

Open this folder in Android Studio and run the `app` configuration on an emulator or Android device.

You can also build from the terminal:

```bash
./gradlew :app:assembleDebug
```

The debug APK will be created under:

```text
app/build/outputs/apk/debug/
```

## Test And Check

Run local unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Run Android lint:

```bash
./gradlew :app:lintDebug
```

Run lint, tests, and a debug build together:

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Run device/emulator instrumentation tests:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Test File Guide

- `ActiveDriveSessionTest`: live drive route, distance, duration, top speed, speed limit carry-over, and completed-trip conversion.
- `SafetyDetectorTest`: rapid acceleration, harsh stop, and speed limit alert detection.
- `DrivingLogicTest`: safety alert counting, score penalties, route bounds, and iOS-style pairing payload parsing.
- `FirestoreMapperTest`: trip, safety alert, and notification event Firestore field compatibility.
- `ActiveDriveFirestoreMapperTest`: live active-drive Firestore payload for parent views.
- `QrCodeTest`: QR pixel generation.
- `ConnectedTeenTest`: connected teen encode/decode behavior for local account state.
- `TripDeletionTest`: deleted-trip tombstone state.
- `TripSyncTest`: sync result status text.
- `ExampleInstrumentedTest`: Android Studio starter device test; useful only as a placeholder until real UI/device tests are added.

## Project Structure

```text
app/src/main/java/com/vlad8110/teendrive/
  data/       Room entities, DAO, local trip repository, DataStore account state
  firebase/   Firebase account, trip sync, active drive, parent listener, notification mappers
  location/   Fused location, active drive session store, foreground service, safety detector
  model/      Shared account, Firebase, and trip domain models
  sync/       WorkManager cloud sync scheduling
  ui/         Jetpack Compose app shell, screens, QR code, scanner
```

## Privacy Notes

Local trip history and account state are excluded from Android backup/transfer rules. Trips stay local first and sync when the cloud account state is ready.
