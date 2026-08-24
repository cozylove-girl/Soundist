import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigningProperties = Properties().apply {
    val file = rootProject.file("signing.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

android {
    namespace = "com.soundist.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    // 使用本机已安装的 NDK，避免 AGP 默认拉取并下载 27.0.12077973。
    ndkVersion = "27.2.12479018"
    defaultConfig {
        applicationId = "com.soundist.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"${providers.gradleProperty("SOUNDIST_SUPABASE_URL").orNull ?: ""}\"")
        // 环境声 miniaudio 后端开关（阶段 B）：默认 false=Media3 安全回退；真机验收前保持关闭。
        // Debug 可通过 MiniaudioFeatureFlags 运行时覆盖开启（会话内固定）。
        buildConfigField("boolean", "USE_MINIAUDIO_AMBIENT", "false")
        // 生成电台 native 引擎后端（阶段 E）。默认开启；出问题可翻 false 快速回退到 Kotlin 渲染器。
        buildConfigField("boolean", "USE_NATIVE_GENERATIVE", "true")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${providers.gradleProperty("SOUNDIST_SUPABASE_ANON_KEY").orNull ?: ""}\"")
        ndk {
            // 16KB 页面设备强制 arm64-v8a；x86_64 用于模拟器/开发机。
            abiFilters += setOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                // 阶段 1 为纯 C 工程，不链接 C++ STL；后续阶段若引入 C++ 再调整。
                arguments("-DANDROID_STL=none")
            }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    signingConfigs {
        create("releaseLocal") {
            val storePath = releaseSigningProperties.getProperty("storeFile")
            if (!storePath.isNullOrBlank()) {
                storeFile = rootProject.file(storePath)
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            signingConfig = signingConfigs.getByName("releaseLocal").takeIf { it.storeFile?.isFile == true }
                ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:audio"))
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:listening"))
    implementation(project(":feature:productivity"))
    implementation(project(":feature:notes"))
    implementation(project(":feature:records"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.work)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
