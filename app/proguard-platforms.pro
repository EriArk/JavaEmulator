# Providers are created by name in ExternalLibrary.packageProvider().
-keep class * implements ru.playsoftware.j2meloader.catalog.ExternalLibrary$PackageProvider { public <init>(); }
# Upstream SDKs and native engines look up Java callbacks by name.
-keep class org.libsdl.app.** { *; }
-keep class com.parkjeongseop.wipi.** { *; }
-keep class io.github.eriark.abyssme.iphone.** { *; }
-keep class com.nokia.mid.ui.** { *; }
