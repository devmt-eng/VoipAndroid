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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class io.phone.build.voiplib.repository.** { *; }
-keep interface io.phone.build.voiplib.repository.** { *; }
# Koin
-keep class org.koin.** { *; }
-keep class com.github.hieujames.** { *; }
-keepclassmembers class io.phone.build.voipsdkandroid.** { *; }

# Bảo toàn các class có thể được sử dụng thông qua reflection
-keepclasseswithmembers class * {
    @org.koin.core.annotation.KoinInternal <methods>;
}

# Bảo toàn annotations và metadata của Koin
-keepattributes RuntimeVisibleAnnotations
-keepattributes AnnotationDefault
-keepattributes InnerClasses

# Tắt optimization và shrinkage cho Koin
-dontoptimize
-dontshrink