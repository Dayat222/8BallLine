# R8 / ProGuard hardening rules
-repackageclasses 'a'
-allowaccessmodification
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn kotlin.**
-dontwarn kotlinx.**
