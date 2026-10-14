-keep class com.github.eka2l1.emu.** { *; }
-keepclassmembers class * implements com.github.eka2l1.emu.Emulator$ArchivePlan { *; }
-keepclassmembers class * implements com.github.eka2l1.emu.Emulator$ArchiveSink { *; }
# Optional router/library providers, ViewModel construction and JNI-facing host code.
-keep class io.github.eriark.abyssme.symbian.** { *; }
