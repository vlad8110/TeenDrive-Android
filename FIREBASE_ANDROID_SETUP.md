# Firebase Android setup

Register the Android app in the existing Firebase project with this package name:

```text
com.vlad8110.teendrive
```

Download the generated `google-services.json` file from Firebase and place it here:

```text
app/google-services.json
```

Enable these Firebase products in the same project:

- Authentication: enable Anonymous sign-in.
- Cloud Firestore: create or reuse the database used by the web app.
- Cloud Messaging: no extra Android code is needed after the app is registered, but push delivery requires notification permission on Android 13+.

The Android app writes a lightweight presence document to:

```text
androidClients/{uid}
```

If your existing Firestore rules only allow the web collections, add an Android-safe rule for anonymous users, for example:

```text
match /androidClients/{userId} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
}
```

Cloud Functions can keep using the same Firebase project. Adjust functions only if they validate platform-specific collection names or notification payload shapes.
