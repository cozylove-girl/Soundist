# Soundist 原生音频核心 —— CMake / NDK 构建（阶段 1）
# 产物：libsoundist_audio.so（arm64-v8a + x86_64）

cmake_minimum_required(VERSION 3.22.1)

project(soundist-native-audio LANGUAGES C)

# miniaudio 是纯 C 单头库；本工程阶段 1 也无 C++。用 C11（NDK clang 默认即 gnu11+）。
# 刻意不设 CMAKE_C_STANDARD，沿用 NDK 工具链默认值，避免与 AGP 传入的 -std 冲突。

add_library(soundist_audio SHARED
    miniaudio_impl.c
    soundist_audio.c
    soundist_generative.c
)

target_include_directories(soundist_audio PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/miniaudio
)

# 混音用 sinf/sqrtf 等数学函数。
target_link_libraries(soundist_audio PRIVATE m)

# ---------------------------------------------------------------------------
# 16KB 页面对齐（Android 15+ / 16KB 页面设备必须）：
#   把 ELF LOAD 段对齐从默认 4KB 提到 16KB（0x4000）。16KB 页面设备要求所有
#   原生 .so 的 LOAD 段都 16KB 对齐，否则 dlopen/mmap 失败（libloader 崩溃）。
#   等价参数：-Wl,-z,max-page-size=16384（lld 支持）。
# 验证方法：用 NDK 自带 llvm-readelf 检查每个 LOAD 段的 Align 列 == 0x4000：
#   $ANDROID_NDK/toolchains/llvm/prebuilt/<host>/bin/llvm-readelf -l libsoundist_audio.so
# ---------------------------------------------------------------------------
target_link_options(soundist_audio PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

# 阶段 1 骨架阶段的额外告警（不升级为错误，避免干扰后续阶段）。
target_compile_options(soundist_audio PRIVATE -Wall -Wextra)
