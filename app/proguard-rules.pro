# R8 / ProGuard hardening rules
-repackageclasses 'a'
-allowaccessmodification
-keep class com.dayat.eightballline.model.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn kotlin.**
-dontwarn kotlinx.**
