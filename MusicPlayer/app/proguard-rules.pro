# youtubedl-android: usa reflexão/Jackson internamente
-keep class com.yausername.** { *; }
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.**
