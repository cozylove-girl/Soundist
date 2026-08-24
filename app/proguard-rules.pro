-keepattributes *Annotation*
-dontwarn javax.naming.**

# 保留 JNI 原生方法：NativeAudioCore 的 external fun 对应 libsoundist_audio.so 的 JNI 符号
# （Java_com_soundist_app_NativeAudioCore_*）。R8 默认会重命名/裁剪，缺失会 UnsatisfiedLinkError。
-keepclasseswithmembernames class com.soundist.app.NativeAudioCore {
    native <methods>;
}

# 保留 Room 实体字段名：备份导入导出按字段名序列化，R8 混淆会把 id/notebookId 等字段名改成 a/b/c，
# 导致导入时所有 id 变成空串（"存在空 ID"）。保留整个 core.database 包的类与成员名。
-keep class com.soundist.core.database.** { *; }

