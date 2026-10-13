# AndroidJUnitRunner calls methods otherwise inlined out of the app's shared
# tracing dependency. Only rehearsal APKs retain this instrumentation boundary.
-keep class androidx.tracing.Trace { *; }
# The runner's Kotlin calls are compiled separately from the optimized app.
# Do not let shared stdlib APIs disappear before the test APK can link them.
-keep class kotlin.** { *; }
