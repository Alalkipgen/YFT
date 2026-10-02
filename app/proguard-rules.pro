# Hilt, Room and Compose publish consumer rules. Add YFT-specific release rules here.

# The player-script host page calls its bridge by method name, so bridge methods keep their names.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
