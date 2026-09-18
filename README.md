# AI Exercise Tracker

[![CI](https://github.com/asood8/Exercise-Tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/asood8/Exercise-Tracker/actions/workflows/ci.yml)

An Android app that uses your phone's camera to count your reps and check your form. You prop the phone up, pick an exercise and start working out. It counts reps for 8 exercises, gives each rep a form score, and estimates how many calories you burned.

Pose detection runs on the phone with [MediaPipe](https://developers.google.com/mediapipe), so the camera video is never uploaded anywhere.

<p align="center">
  <img src="docs/screenshots/login.png" width="240" alt="Login screen with email sign-in and a guest option">
  &nbsp;
  <img src="docs/screenshots/home.png" width="240" alt="Workout settings screen with lifetime stats, username and body measurements">
  &nbsp;
  <img src="docs/screenshots/history.png" width="240" alt="Progress screen with level, muscle group radar chart and workout streak">
</p>

## Features

- Counts reps for push-ups, squats, sit-ups, lunges, bicep curls, overhead press and jumping jacks, and times planks. It reads the count out loud as you go.
- Scores your form out of 100 on every rep and plank hold, with live tips like "Go lower!" or "Keep chest up!"
- Calorie estimates based on how you're actually moving, using your weight and height
- A 3-2-1 countdown once you're in frame, pause and resume, and switching exercises mid-workout
- Set goals (like 3 sets of 15) with a progress ring and a rest timer. The app also suggests your next goal based on your last workout.
- Routines that chain exercises together, like 3 rounds of push-ups, sit-ups and a plank. There are a few built in and you can make your own.
- A rep-by-rep breakdown after each workout. If the camera missed a rep or counted one twice, you can fix the count before saving (edited workouts don't go on the leaderboard).
- Works offline. Workouts saved without internet upload later.
- Email/password accounts with email verification and password reset, or a guest mode. Guest accounts can be upgraded later and keep their workouts.
- A progress screen with your totals, level and XP, streaks, weekly charts, personal records, and a radar chart of which muscle groups you've worked the most
- Achievements, like doing 1,000 reps, a 7-day streak or a 2-minute plank
- An optional evening reminder if your streak is about to end
- Imperial or metric units
- An opt-in leaderboard ranked by total reps, using a username you pick
- You can download all your data as a JSON file or delete your account from the home screen

## How it works

Camera frames get scaled down and sent to MediaPipe's Pose Landmarker (using the `pose_landmarker_lite` model), which finds 33 points on your body in each frame.

Each exercise has its own tracker class. It turns those points into joint angles and runs a small state machine. For a squat that's standing, going down, bottom, coming up, and the rep counts once you're standing again. When a rep finishes, the tracker checks a few form rules (depth, leaning forward, knee position, etc.) and takes points off for anything you missed.

Camera tracking is jittery, so the points are smoothed over a few frames first, and any rep that happens faster than a person could actually do one gets ignored. That stops fake reps from showing up.

Pose detection runs on a background thread while the timers and buttons run on the main thread, so all the tracker updates go through one lock. When you end a workout, the app stops taking new frames before it reads the counts, so a rep can't get cut off halfway in the summary.

Only the exercise you picked is tracked. Otherwise one movement could count for two exercises (a deep lunge looks a lot like a squat). You can switch exercises during a workout and keep everything you've done so far. Routines do the switching for you: after you hit the target for one exercise, you get a rest and then the next one starts.

For calories, I didn't want to just use a fixed calories-per-minute number. The app estimates your center of mass from the body points, weighting each body part by how much of your body weight it usually is. How fast that point moves gets turned into a MET value, and that plus your weight gives calories. It's still an estimate, but it goes up when you work harder.

### Accounts and data

Accounts use Firebase Authentication. You can sign up with email and password (with email verification and password reset) or use a guest account, which can be turned into a real account later. Workouts are saved to Cloud Firestore. Firestore caches writes offline, so a workout saved with no internet uploads on its own later.

The security rules are in [`firestore.rules`](firestore.rules). They make sure that:

- only you can read or delete your workouts
- leaderboard entries only have the fields the app writes, can't go up by more than 2,000 reps at once, and have to use a username you own
- usernames are unique (not case sensitive). Claiming one happens in a transaction, so two people can't grab the same name.

App Check (Play Integrity in release builds) stops other apps from using the Firebase project. Deleting your account removes your workouts, leaderboard entry, username and login all at once.

## Tech stack

- Kotlin with XML layouts
- CameraX for the camera
- MediaPipe Tasks Vision (Pose Landmarker) for pose detection on the phone
- Firebase Authentication, Cloud Firestore and App Check
- WorkManager for the streak reminder
- Gradle (Kotlin DSL), AGP 8.3, min SDK 24, target SDK 34
- R8 for minifying release builds
- JUnit tests, with GitHub Actions running the tests, lint and a build on every push

## Getting good tracking

- Stand far enough away that your whole body fits in the frame. Most exercises need to see you from your shoulders to your ankles.
- The workout screen tells you where to put the phone. Usually that's facing the camera for curls, overhead press, squats and jumping jacks, and side-on for push-ups, sit-ups, planks and lunges.
- For side-on exercises, turn your phone sideways. The screen switches to landscape (or tap the rotate button if auto-rotate is off) and you'll take up a lot more of the frame.
- Lighting matters a lot. A bright window behind you is the most common reason tracking cuts out.
- The bar at the top turns green once the app can see you.

## Project structure

```
app/src/
├── main/
│   ├── assets/pose_landmarker_lite.task     MediaPipe pose model
│   ├── java/com/example/exercisetracker/
│   │   ├── MainActivity.kt                  camera, pose detection and the workout screen
│   │   ├── *Tracker.kt                      one rep counter per exercise
│   │   ├── AngleUtils.kt                    joint angle math used by the trackers
│   │   ├── CalorieEstimator.kt              center of mass calorie model
│   │   ├── LevelingUtils.kt                 XP, levels and the muscle chart
│   │   ├── Routine.kt, GoalSuggestions.kt   routines, goals and goal suggestions
│   │   ├── AccountData.kt                   data export and account deletion
│   │   ├── LoginActivity.kt, HomeActivity.kt, SummaryActivity.kt,
│   │   │   HistoryActivity.kt, LeaderboardActivity.kt
│   │   └── OverlayView.kt, MuscleStatsView.kt    custom views (skeleton overlay, radar chart)
│   └── res/layout/                          XML layouts
└── test/                                    unit tests
```

The `google-services.json` file isn't included since it's tied to my Firebase project, so this repo is for reading the code rather than building it.

## Privacy

The camera video is processed on your phone and never saved or uploaded. When you save a workout, the app stores your rep counts, calories, form scores, how long the workout took, any goal or routine you used, and the date and time, all under your account. The streak reminder runs completely on your phone. You're only on the leaderboard if you turn on "Appear on Global Rankings" when saving, and it shows your username, never your email. You can download all your data or delete your account at the bottom of the home screen.

## License

MIT, see [LICENSE](LICENSE).
