plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.serialization) }
android { namespace="com.soundist.core.network"; compileSdk=36; buildToolsVersion="36.0.0"; defaultConfig { minSdk=26 } }
dependencies { api(project(":core:model")); implementation(libs.ktor.client.core); implementation(libs.ktor.client.okhttp); implementation(libs.ktor.client.content); implementation(libs.ktor.serialization.json); implementation(libs.kotlinx.serialization); testImplementation(libs.junit); testImplementation(libs.kotlinx.coroutines) }
