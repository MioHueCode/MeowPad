plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.miohue.notepad"
    compileSdk = 34
    android.buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.miohue.notepad"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "1.9.0"
        // 体积优化：应用只提供中文/英文两套文案，但依赖库（Compose、media3、Coil 等）
        // 自带数十种语言资源，会把 resources.arsc 撑大。这里只保留实际用到的语言。
        resourceConfigurations += listOf("zh", "en")
    }

    buildTypes {
        release {
            // 性能/体积优化：R8 代码压缩混淆 + 资源压缩（原为 false，release 包未做任何裁剪）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            // 版本号不再带 -debug 后缀（用户要求显示为 1.9.0）
            versionNameSuffix = ""
            // 体积优化：material-icons-extended 带入 8528×2 个图标类（classes.dex 解压后约 42MB），
            // 实际仅用 137 个。R8 shrink 可精确剔除。此前卡死原因是堆过小导致 GC 抖动
            // （-Xmx2048m 处理 42MB dex，30s 仅耗 0.04s CPU），已上调至 -Xmx4096m。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.0")
    // 视频播放：media3/ExoPlayer，直连系统 MediaCodec，自带原生 PlayerView 控制条。
    // 此前 WebView 的 HTML5 <video> 在本机（H.264 High@5.0 + yuvj420p）解不出画面，
    // 而系统 MediaCodec 解码正常（TextureView 版曾成功出图），故改用 media3。
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
}
