# SQLCipher is called from native code; keep its classes intact.
-keep class net.zetetic.database.** { *; }

# Backup file payload (backup/BackupModels.kt) is read back on a different install, possibly
# a different app version, so its JSON shape must survive R8 untouched.
-keep @kotlinx.serialization.Serializable class com.bolohisab.data.backup.** { *; }
-keepclassmembers class com.bolohisab.data.backup.** {
    *** Companion;
}
-keepclasseswithmembers class com.bolohisab.data.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
