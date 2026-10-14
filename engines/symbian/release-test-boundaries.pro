# Rehearsal only: fixtures use archive writers and direct app APIs that normal
# application reachability does not retain. Production optimization is unchanged.
-keep class org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream { *; }
-keep class org.apache.commons.compress.archivers.zip.ZipArchiveEntry { *; }
-keepclassmembers class org.apache.commons.compress.archivers.zip.ZipFile {
    public org.apache.commons.compress.archivers.zip.ZipArchiveEntry getEntry(java.lang.String);
}
-keep class org.apache.commons.compress.archivers.sevenz.SevenZOutputFile { *; }
-keep class org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry { *; }
-keep class ru.playsoftware.j2meloader.R$* { *; }
-keep class ru.playsoftware.j2meloader.config.ProfileModel { *; }
-keep class ru.playsoftware.j2meloader.config.ProfileModel$* { *; }
-keep class ru.playsoftware.j2meloader.input.GameMenuDialog { *; }
-keep class ru.playsoftware.j2meloader.input.GameMenuPages { *; }
-keep class ru.playsoftware.j2meloader.input.GameMenuPages$* { *; }
