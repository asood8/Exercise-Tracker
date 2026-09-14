# AI Exercise Tracker

[![CI](https://github.com/asood8/Exercise-Tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/asood8/Exercise-Tracker/actions/workflows/ci.yml)

An Android app that counts your reps through the phone's camera. Prop your phone up, start moving, and it tracks push-ups, squats, curls and five other exercises, scores your form on every rep, and estimates how many calories you burned. There's nothing to log by hand.

Pose detection runs entirely on the device with [MediaPipe](https://developers.google.com/mediapipe), so no video ever leaves your phone.

<p align="center">
  <img src="docs/screenshots/login.png" width="240" alt="Login screen with email sign-in and a guest option">
  &nbsp;
  <img src="docs/screenshots/home.png" width="240" alt="Workout settings screen with lifetime stats, username and body measurements">
  &nbsp;
  <img src="docs/screenshots/history.png" width="240" alt="Progress screen with level, muscle group radar chart and workout streak">
</p>

## Features

- Rep counting for push-ups, squats, sit-ups, lunges, bicep curls, overhead press and jumping jacks, plus a hold timer for planks. Reps are counted out loud as you go.
- A form score out of 100 for each rep and each plank hold, with live cues like "Go lower!" or "Keep chest up!" and optional spoken coaching
- Calorie estimates based on how your body actually moves, adjusted for your weight, height, age and sex
- Pick an exercise before you start, and switch to another one mid-workout without ending the session. Tracking starts with a 3-2-1 countdown once you're in frame, and you can pause at any point.
- Optional set goals like 3 × 15, with a progress ring, a rest timer between sets, and a suggested next goal based on how your last session went
- Routines that string exercises together, like three rounds of push-ups, sit-ups and a plank, and move on to the next exercise by themselves. A few come built in, and you can make your own.
- A rep-by-rep breakdown after each workout, with the option to fix the count if the camera missed or double-counted something. Corrected workouts are saved, but kept off the leaderboard.
- Saving works without a connection. The workout is kept on the phone and uploads once you're back online.
- Email/password accounts with email verification and password reset, or a guest mode if you just want to try it. A guest account can be turned into a full account later without losing any workouts.
- A progress screen with lifetime totals, workout time, an XP and level system, current and best streaks, weekly charts, personal records, and a radar chart showing which muscle groups you've trained most
- Achievements for milestones like your first 1,000 reps, a 7-day streak or a 2-minute plank
- An optional evening reminder if you're about to break your streak
- Pounds and feet, or kilograms and centimetres
- An opt-in public leaderboard ranked by total reps, where you show up under a username you pick
- Download everything the app has stored about you as a JSON file, or delete your account and all of its data, from the Home screen

## How it works

Frames from the camera are downscaled and passed to MediaPipe's Pose Landmarker (the bundled `pose_landmarker_lite` model), which returns 33 body landmarks per frame.

Each exercise has its own tracker class that turns those landmarks into joint angles and runs a small state machine. A squat, for example, goes standing → descending → bottom → ascending, and the rep counts when you're back up. At the end of each rep the tracker checks a few form rules (depth, torso lean, knee tracking and so on) and takes points off for each one you missed.

Before the trackers see anything, the landmarks are smoothed over the last few frames, and a rep that finishes faster than a person realistically could is thrown out. That keeps ordinary camera jitter from turning into phantom reps.

You pick the exercise before you start, and only that exercise's tracker runs. That keeps one movement from counting as two different exercises (a deep lunge can look a lot like a squat). You can switch exercises mid-workout, and everything counted so far stays in the session. A routine does the switching for you: once you hit the target for one exercise, it gives you a rest and then sets up the next.

Calories come from a simple physics model rather than a fixed calories-per-minute table. The app estimates your center of mass from the landmarks, weighting each body segment by its typical share of body mass. How fast that point moves is mapped to a MET value, which is converted to calories using your body weight. It's still an estimate, but it reacts to how hard you're actually working.

## Tech stack

- Kotlin with XML layouts
- CameraX for the camera feed
- MediaPipe Tasks Vision (Pose Landmarker) for on-device pose detection
- Firebase Authentication and Cloud Firestore for accounts, workout history and the leaderboard
- Gradle with the Kotlin DSL (AGP 8.3, min SDK 24, target SDK 34)

## Getting started

You'll need a recent version of Android Studio and an Android phone running Android 7.0 or later. A real device is strongly recommended: the app only ships ARM native libraries for MediaPipe, and pose tracking needs a decent camera feed anyway.

1. Clone the repo and open it in Android Studio.

   ```
   git clone https://github.com/asood8/Exercise-Tracker.git
   ```

2. Set up Firebase. The app won't build without a `google-services.json`, which isn't checked in.
   - Create a project in the [Firebase console](https://console.firebase.google.com/) and add an Android app with the package name `com.example.exercisetracker`.
   - Download `google-services.json` and put it in the `app/` folder.
   - Under **Authentication**, enable the **Email/Password** and **Anonymous** sign-in providers.
   - Create a **Cloud Firestore** database and publish these rules. Each user can only see and delete their own workouts. Leaderboard entries are readable by anyone signed in, but only contain the fields the app writes, can't jump by more than 2,000 reps in one save, and must use a username the owner has claimed (usernames are unique).

     ```
     rules_version = '2';
     service cloud.firestore {
       match /databases/{database}/documents {

         function isOwner(userId) {
           return request.auth != null && request.auth.uid == userId;
         }

         // A leaderboard name must be claimed by the writer in /usernames
         function ownsUsername(name) {
           return get(/databases/$(database)/documents/usernames/$(name.lower())).data.uid == request.auth.uid;
         }

         function validEntry() {
           let d = request.resource.data;
           return d.keys().hasOnly(['username', 'totalReps', 'totalCalories', 'lastActive', 'level'])
             && (!d.keys().hasAny(['username'])
                 || (d.username is string && d.username.size() <= 20 && ownsUsername(d.username)))
             && d.get('totalReps', 0) is int && d.get('totalReps', 0) >= 0
             && d.get('totalCalories', 0) is number
             && d.get('level', 1) is int && d.get('level', 1) <= 100;
         }

         match /workouts/{workoutId} {
           allow read, delete: if request.auth != null && resource.data.userId == request.auth.uid;
           allow create: if request.auth != null && request.resource.data.userId == request.auth.uid;
         }

         match /users/{userId} {
           allow read: if request.auth != null;
           allow delete: if isOwner(userId);
           allow create: if isOwner(userId) && validEntry()
             && request.resource.data.get('totalReps', 0) <= 2000;
           allow update: if isOwner(userId) && validEntry()
             && request.resource.data.get('totalReps', 0) - resource.data.get('totalReps', 0) <= 2000;
         }

         // Unique usernames: the doc id is the lowercase name, owned by one account
         match /usernames/{name} {
           allow read: if request.auth != null;
           allow create: if request.auth != null
             && request.resource.data.keys().hasOnly(['uid'])
             && request.resource.data.uid == request.auth.uid
             && name == name.lower() && name.size() >= 3 && name.size() <= 20;
           allow delete: if request.auth != null && resource.data.uid == request.auth.uid;
         }
       }
     }
     ```

3. Sync Gradle and run the app on your phone. Grant camera access when it asks.

4. Once the app is published on Google Play, turn on **App Check** with Play Integrity in the Firebase console, so only your app can use your Firebase project. Don't enforce it before then, because Play Integrity only passes for copies installed from Play. Debug builds use App Check's debug provider instead: on first launch it logs a debug token (search logcat for `DebugAppCheckProvider`), which you add under **App Check → Manage debug tokens**.

To build from the command line instead, run `./gradlew assembleDebug` (or `gradlew.bat assembleDebug` on Windows). Gradle needs a JDK, so if `JAVA_HOME` isn't set, point it at the one bundled with Android Studio.

Run the unit tests with `./gradlew test`. They cover the logic that doesn't need a camera: goal suggestions, routines, streaks (including daylight saving changes), levels, achievements, and the username and password rules. GitHub Actions runs them on every push, along with lint and a debug build, using a placeholder Firebase config.

Release builds are minified with R8. To sign them, add `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` to your user-level `~/.gradle/gradle.properties`, which lives outside the repo so the key and passwords are never committed. Without them the release APK is left unsigned.

## Getting good tracking

- Put the phone far enough away that your whole body is in frame. Most trackers need to see everything from your shoulders down to your ankles.
- The workout screen tells you where to put the phone for the exercise you picked. In general, face the camera for curls, overhead press, squats and jumping jacks, and set it up side-on for push-ups, sit-ups, planks and lunges.
- For the side-on exercises, turn the phone sideways. The workout screen switches to landscape (tap the rotate button if auto-rotate is off), and your body fills much more of the frame.
- Good, even lighting helps a lot. Backlighting from a window is the most common reason tracking drops out.
- The status bar at the top turns green once the app has found you.

## Project structure

```
app/src/main/
├── assets/pose_landmarker_lite.task     MediaPipe pose model
├── java/com/example/exercisetracker/
│   ├── MainActivity.kt                  camera, pose detection and the live workout screen
│   ├── *Tracker.kt                      one rep counter per exercise
│   ├── AngleUtils.kt                    joint angle math shared by the trackers
│   ├── CalorieEstimator.kt              center-of-mass calorie model
│   ├── LevelingUtils.kt                 XP, levels and muscle chart scaling
│   ├── Routine.kt, GoalSuggestions.kt   routines, set goals and suggested goals
│   ├── AccountData.kt                   data export and account deletion
│   ├── LoginActivity.kt, HomeActivity.kt, SummaryActivity.kt,
│   │   HistoryActivity.kt, LeaderboardActivity.kt
│   └── OverlayView.kt, MuscleStatsView.kt    custom views (skeleton overlay, radar chart)
└── res/layout/                          XML layouts
```

## Privacy

Camera frames are processed on the phone and are never recorded or uploaded. When you save a workout, the app stores your rep counts, calories, form scores, workout length, any goal or routine you followed and a timestamp in Firestore under your account. The streak reminder runs entirely on your phone. You only appear on the leaderboard if you turn on "Appear on Global Rankings" when saving, and it shows the username you set on the home screen, never your email. At the bottom of the home screen you can download all of your data, or delete your account along with everything in it.

## Roadmap

- Unit tests for the rep trackers
- Publish on the Google Play Store

## License

Released under the [MIT License](LICENSE).
