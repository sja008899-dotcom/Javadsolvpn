# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Xray-core / libv2ray JNI keep rules
-keep class libv2ray.** { *; }
-keep interface libv2ray.** { *; }
-keep class go.** { *; }
-keep interface go.** { *; }
-dontwarn libv2ray.**
-dontwarn go.**
