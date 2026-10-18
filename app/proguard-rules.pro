# MeowPad (notepad-app) R8 混淆规则
# Compose / OkHttp / Coil 均自带 consumer rules，这里只补充本项目需要的部分。

# 跳过 R8 优化阶段：只做 shrink（剔除未引用代码）+ obfuscate。
# 原因：本项目 material-icons-extended 引入了 8528×2 个图标类，optimize
# 阶段在 ARM64 沙箱上会长时间无进展（此前 assembleRelease 曾卡死），
# 而 shrink 已足够剔除未使用图标，达到减小体积的目的。
-dontoptimize

# 保留数据类的 toString/hashCode 语义（JSON 手写序列化依赖字段名反射较少，但保留更安全）
-keepclassmembers class com.miohue.notepad.data.** {
    <fields>;
    <init>(...);
}

# 保留 media3 播放器的回调接口（ExoPlayer 内部大量使用接口回调，避免被裁）
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# org.json 由 Android 平台提供，避免被当作库裁剪
-keep class org.json.** { *; }
-dontwarn org.json.**

# Kotlin 协程内部诊断信息
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# 关闭混淆日志噪音，保留行号便于崩溃定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
