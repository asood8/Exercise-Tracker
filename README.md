\# AI Exercise Tracker



A computer-vision fitness app that tracks your workouts automatically — no manual logging. Point your camera at yourself, start exercising, and the app counts your reps, estimates calories burned, and tracks your progress over time.



\## Features



\- \*\*Camera-based rep counting\*\* — Uses MediaPipe pose detection to track your body in real time and automatically count reps across a wide range of exercises, including pushups, squats, and many more.

\- \*\*Physics-based calorie tracking\*\* — Estimates calories burned per exercise using a physics-based model rather than static lookup tables, personalized to your body metrics.

\- \*\*Personalized profiles\*\* — Onboarding captures bodyweight, height, gender, and age to tailor calorie and progress calculations to each user.

\- \*\*Flexible sign-in\*\* — Sign in with email and password, or jump in as a guest.

\- \*\*Customizable display\*\* — Choose what stats and info you want visible during a workout.

\- \*\*Progress tracking\*\* — A dedicated history view shows:

&#x20; - A level system based on overall workout volume

&#x20; - A spiderweb/radar graph visualizing how toned each muscle group is

&#x20; - Current and past workout streaks

&#x20; - Lifetime totals

&#x20; - A log of recent workouts

\- \*\*Public leaderboard\*\* — Compete with other users, ranked by workout volume.



\## How It Works



The app uses \[MediaPipe Pose](https://developers.google.com/mediapipe) to extract body landmark coordinates from the live camera feed. These landmarks are analyzed frame-by-frame to detect exercise-specific motion patterns (e.g., the up/down cycle of a pushup or squat), which drives the rep counter. Calorie estimates come from a physics-based model that factors in body weight, movement, and exercise type, rather than relying on generic calories-per-minute tables.



\_\[Optional: expand this section with more detail — e.g., how the app distinguishes between exercise types, specifics of the physics model, any performance optimizations for real-time tracking]\_



\## Tech Stack



\_\[Fill in: language/framework (e.g., Kotlin + Android MediaPipe SDK, Swift + iOS MediaPipe SDK, React Native, Flutter), backend/auth provider, database]\_



\## Screenshots / Demo



\_\[Add screenshots or a short demo GIF/video here — for an app like this, visuals will sell it better than any text]\_



\## Getting Started



\_\[Fill in setup/installation instructions once the stack above is confirmed — e.g., clone the repo, install dependencies, build/run steps]\_



\## Roadmap



\- \[ ] Publish to the Google Play Store



\---



\*Adjust tone and section order as you like — this is meant as a solid starting skeleton, not a final draft.\*

