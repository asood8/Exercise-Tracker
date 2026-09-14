# R8 rules for release builds (minified and shrunk; see build.gradle.kts).

# Keep file and line numbers so crash stack traces are readable
-keepattributes SourceFile,LineNumberTable

# MediaPipe calls into its Java classes from native code and reads protobuf-lite messages by
# reflection, so R8 must not rename or strip them
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**

# MediaPipe logs through Flogger, which finds the logging class by looking for its own class names on
# the call stack. If R8 renames them, starting the pose detector crashes with "no caller found on
# the stack".
-keep class com.google.common.flogger.** { *; }

# A dependency ships the AutoValue annotation processor at runtime. It references compiler-only
# classes that don't exist on Android and are never used by the app.
-dontwarn javax.lang.model.**

# Firestore model classes (Workout, UserStats) are protected with @Keep in the source, since
# Firestore fills them in by reflection.
