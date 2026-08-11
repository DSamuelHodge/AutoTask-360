# Add project specific ProGuard rules here.

# ---------------------------------------------------------------------------
# Room (#12): keep entities, DAOs, and the Room database implementation.
# Room generates member accessors at compile time; R8 must not strip them.
# ---------------------------------------------------------------------------
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep interface androidx.room.Dao { *; }
-keep interface com.example.data.*Dao { *; }
-keep class com.example.data.AutomationProfile { *; }
-keep class com.example.data.ExecutionLog { *; }
-keep abstract class com.example.data.AutoTaskDatabase { *; }
-keep class com.example.data.AutoTaskDatabase_Impl { *; }
-keep class com.example.data.AutoTaskRepository { *; }

# ---------------------------------------------------------------------------
# Engine (#12): preserve action/execution classes, event model, capabilities.
# These are reached reflectively via JSON action dispatch and the provider.
# ---------------------------------------------------------------------------
-keep class com.example.engine.ActionExecutor { *; }
-keep class com.example.engine.AutoTaskEngine { *; }
-keep class com.example.engine.CapabilityProvider { *; }
-keep class com.example.engine.SchemaProvider { *; }
-keep class com.example.engine.AutomationEvent { *; }
-keep class com.example.engine.StepResult { *; }
-keep class com.example.provider.AutoTaskContentProvider { *; }
-keep class com.example.provider.AutoTaskContentProvider$** { *; }

# ---------------------------------------------------------------------------
# JSON / model serialization (#12): keep kotlinx + moshi model classes.
# ---------------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keep class com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Keep all app data/engine/model value classes to avoid JSON field stripping
-keep class com.example.data.** { *; }
-keep class com.example.engine.** { *; }
-keep class com.example.server.** { *; }

# Enums used in dispatch / status must survive obfuscation
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Preserve line numbers for crash diagnostics in release builds
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
