plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.serialization) }
android { namespace = "com.soundist.core.model"; compileSdk = 36; buildToolsVersion = "36.0.0"; defaultConfig { minSdk = 26 } }
dependencies { implementation(libs.kotlinx.coroutines); api(libs.kotlinx.serialization); testImplementation(libs.junit) }
