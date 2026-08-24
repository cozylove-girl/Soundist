/*
 * miniaudio_impl.c
 *
 * miniaudio 单头库（third_party/miniaudio/miniaudio.h）的实现翻译单元。
 * 只有在包含 MINIAUDIO_IMPLEMENTATION 的这个 TU 里才会生成函数实现，
 * 其余 TU 只 #include "miniaudio.h" 拿到声明，避免重复定义。
 *
 * 阶段 1 裁剪：
 *   - MA_NO_ENCODING：不需要写音频文件（编码器），减小体积与编译时间。
 *   - 解码（ma_decoder）与生成（ma_waveform/ma_noise 等）保留，阶段 2 环境声混音可能用到。
 */
#define MINIAUDIO_IMPLEMENTATION
#define MA_NO_ENCODING
#include "miniaudio.h"
