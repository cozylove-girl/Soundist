plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.ksp) }
android { namespace = "com.soundist.core.database"; compileSdk = 36; buildToolsVersion = "36.0.0"; defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }; ksp { arg("room.schemaLocation", "$projectDir/schemas") } }
dependencies {
    api(project(":core:model")); api(project(":core:network")); api(libs.androidx.room.runtime); implementation(libs.androidx.room.ktx); implementation(libs.kotlinx.serialization)
    ksp(libs.androidx.room.compiler); testImplementation(libs.junit)
    // Room 收藏/播种「写库重启恢复」测试依赖真实 SQLite，Robolectric 原生 SQLite 在本机 Windows/JVM 上
    // 会崩在 SQLiteConnectionNatives.nativeOpen（DEP 违规，见 hs_err），无法在 JVM 单测覆盖 →
    // 该测试放在 src/androidTest 仪器化运行（需真机/模拟器），见 RoomSoundPersistenceTest。
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
