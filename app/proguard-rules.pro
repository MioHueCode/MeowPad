# MeowPad (notepad-app) R8 混淆规则
# Compose / OkHttp / Coil 均自带 consumer rules，这里只补充本项目需要的部分。

# 保留数据类的 toString/hashCode 语义（JSON 手写序列化依赖字段名反射较少，但保留更安全）
-keepclassmembers class com.miohue.notepad.data.** {
    <fields>;
    <init>(...);
}

# org.json 由 Android 平台提供，避免被当作库裁剪
-keep class org.json.** { *; }
-dontwarn org.json.**

# Kotlin 协程内部诊断信息
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# 关闭混淆日志噪音，保留行号便于崩溃定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
