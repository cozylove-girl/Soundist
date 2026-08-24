plugins { id("com.android.library"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace = "com.soundist.feature.notes"; compileSdk = 36; buildToolsVersion = "36.0.0"; defaultConfig { minSdk = 26 }; buildFeatures { compose = true } }
dependencies {
    implementation(project(":core:designsystem")); implementation(project(":core:model")); implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core)
    implementation("androidx.compose.foundation:foundation"); implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended"); implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"); debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
