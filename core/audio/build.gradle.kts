plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace="com.soundist.core.audio"; compileSdk=36; buildToolsVersion="36.0.0"; defaultConfig { minSdk=26 } }
dependencies { api(project(":core:model")); implementation(libs.androidx.core); implementation("androidx.media:media:1.7.0"); implementation(libs.androidx.media3.exoplayer); implementation(libs.androidx.media3.exoplayer.hls); implementation(libs.androidx.media3.session); implementation(libs.kotlinx.coroutines); implementation(libs.guava); testImplementation(libs.junit) }
