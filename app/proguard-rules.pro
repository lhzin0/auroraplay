# Keep Retrofit/Gson model classes
-keep class com.auroraplay.iptv.data.api.dto.** { *; }
-keep class com.auroraplay.iptv.data.database.entity.** { *; }
-keep class com.auroraplay.iptv.data.backup.BackupSnapshot { *; }
-keep class com.auroraplay.iptv.domain.repository.AppSettings { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
