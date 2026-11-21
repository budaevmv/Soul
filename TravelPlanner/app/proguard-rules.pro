-dontwarn com.gemalto.jp2.**
-keep class com.tom_roush.pdfbox.** { *; }
# Справочники читаются через kotlinx.serialization
-keep class com.travelplanner.app.reference.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
