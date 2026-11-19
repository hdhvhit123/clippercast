# Readability4J và JSoup dùng phản chiếu ít, giữ lại cho an toàn.
-keep class net.dankito.readability4j.** { *; }
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-dontwarn net.dankito.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
