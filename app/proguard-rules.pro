# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

#-keep class com.boggyb.androidmirror.InputManager

-keepclassmembers class com.boggyb.androidmirror.InputManager{
    public *;
}

-keep class org.webrtc.** { *; }

-keep class com.jcraft.jsch.DH** {*;}
-keep class com.jcraft.jsch.jce.** {*;}
-keep class com.jcraft.jsch.UserAuth** {*;}
-keep class com.jcraft.jsch.CipherNone {*;}
#-keep class com.jcraft.jsch.jcraft.Compression {*;}
#-keep class com.jcraft.jsch.jgss.GSSContextKrb5 {*;}

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

-assumenosideeffects class * implements org.slf4j.Logger {
    public *** trace(...);
    public *** debug(...);
    public *** info(...);
    public *** warn(...);
    public *** error(...);
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
