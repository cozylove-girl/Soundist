package com.soundist.app

import com.soundist.feature.listening.GENERATED_TIMBRES
import com.soundist.feature.listening.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGeneratedAudioRendererTest {
    @Test fun all_frontend_presets_are_supported() {
        assertEquals(setOf("deep-sea","rain-piano","morning-mist","endless-focus","night-train","warm-study","moon-tide","signal-garden","bamboo-strings","custom-lab"), NativeGeneratedAudioRenderer.supportedPresetIds())
    }

    @Test fun scheduler_is_seeded_and_golden_stable() {
        val first = NativeGeneratedAudioRenderer.schedulerGolden("rain-piano", 256)
        assertEquals(first, NativeGeneratedAudioRenderer.schedulerGolden("rain-piano", 256))
        assertTrue(first.isNotEmpty())
        assertTrue(first.any { it.contains("rain-piano-notes") })
    }

    /** 同 seed 确定性：10 个预设各自 golden 稳定（固定 seed 永远产出同一调度）。 */
    @Test fun scheduler_golden_is_deterministic_for_every_preset() {
        val PRESETS = listOf("deep-sea","rain-piano","morning-mist","endless-focus","night-train","warm-study","moon-tide","signal-garden","bamboo-strings","custom-lab")
        PRESETS.forEach { preset ->
            val golden = NativeGeneratedAudioRenderer.schedulerGolden(preset, 512)
            assertEquals("$preset 同 seed 必须稳定", golden, NativeGeneratedAudioRenderer.schedulerGolden(preset, 512))
            assertTrue("$preset 事件必须带 tick:layerId 格式", golden.all { Regex("\\d+:.+").matches(it) })
        }
    }

    /** 调度闸门整体不失效：每个预设都必须在足够长的窗口产出事件（稀疏预设只是慢，不是永不触发）。 */
    @Test fun busy_presets_produce_events_in_a_long_window() {
        val PRESETS = listOf("rain-piano", "night-train", "endless-focus", "signal-garden", "custom-lab")
        PRESETS.forEach { preset ->
            assertTrue("$preset 在 4096 tick 窗口应产出事件", NativeGeneratedAudioRenderer.schedulerGolden(preset, 4096).isNotEmpty())
        }
    }

    @Test fun pcm_chain_is_audible_and_bounded() {
        val pcm = NativeGeneratedAudioRenderer.pcmProbe()
        assertTrue(pcm.any { it.toInt() != 0 })
        assertTrue(pcm.maxOf { kotlin.math.abs(it.toInt()) } < Short.MAX_VALUE)
    }

    /**
     * 完整编排（10 个预设）在各自有限窗口内真实产出非静音 PCM（对齐 Web 稀疏算法，无 25% 地板、无 5 秒超时）。
     *
     * 修复前（根因）：renderLoop 的 5 秒启动静音超时（STARTUP_SILENCE_FRAMES）在稀疏预设的首个可调度 tick
     * 之前就发 ERROR 并停掉 worker —— deep-sea 的 atmosphere 层（deep-sea-floor）首个可调度 tick 是
     * plan.atmosphere=[0,32] 的 tick 32（t≈8.9s，tick 0 被 entryDelay 挡掉），t=5s 时调度器一个 Voice 都没建
     * （实测 created=0, overallPeak=0），于是完整编排整段全 0 PCM；单音色试听无概率闸门从 t=0 直接出声，
     * 所以只有单音色有声。
     *
     * 修复后：不设启动超时，等真实 PCM 出现才发 PLAYING。窗口取各预设确定性 seed 实测首次出声之后。
     */
    @Test fun every_full_arrangement_produces_audible_pcm_in_finite_ticks() {
        val windows = mapOf(
            "deep-sea" to 60.0, "rain-piano" to 60.0, "morning-mist" to 120.0, "endless-focus" to 120.0,
            "night-train" to 120.0, "warm-study" to 60.0, "moon-tide" to 150.0, "signal-garden" to 60.0,
            "bamboo-strings" to 60.0, "custom-lab" to 60.0,
        )
        windows.forEach { (preset, seconds) ->
            val pcm = NativeGeneratedAudioRenderer.renderPresetPcm(preset, (seconds * 44100).toInt())
            val peak = NativeGeneratedAudioRenderer.pcmPeak(pcm)
            assertTrue("$preset 完整编排 ${seconds}s 内必须产出可听 PCM，实际峰值 $peak", peak >= 0.001)
        }
    }

    /** deep-sea / rain-piano 两个预设为硬性要求，单独断言（90 秒有限窗口内非静音）。 */
    @Test fun deep_sea_and_rain_piano_produce_audible_pcm() {
        listOf("deep-sea", "rain-piano").forEach { preset ->
            val pcm = NativeGeneratedAudioRenderer.renderPresetPcm(preset, (90 * 44100).toInt())
            assertTrue("$preset 必须真实出声", NativeGeneratedAudioRenderer.pcmPeak(pcm) >= 0.001)
        }
    }

    /**
     * 对齐 Web 的纯概率闸门：非 intro 层没有「首次强制触发」（无 neverFired、无概率地板）。
     * tick 0 只有显式 intro 层能触发（绕过 entryDelay）；其余层仍受 settings.entryDelay + 概率闸门约束。
     */
    @Test fun pure_web_gate_does_not_force_non_intro_first_event() {
        val golden = NativeGeneratedAudioRenderer.schedulerGolden("rain-piano", 96)
        // rain-piano 唯一 intro 是 rain-piano-notes；tick 0 只应出现它，非 intro 层（strings/bell）不得被提前触发。
        assertEquals(listOf("0:rain-piano-notes"), golden.filter { it.startsWith("0:") })
    }

    /** 全禁用图层的静音编排：调度器不创建任何 Voice → 整段全 0 PCM。renderLoop 启动可听决策保持 null（LOADING），
     *  不假发 PLAYING、也不因「静默开场」误杀为 ERROR（ERROR 只来自 write() 失败/采样异常）。 */
    @Test fun disabled_arrangement_is_silent_and_stays_loading() {
        listOf("deep-sea", "rain-piano").forEach { preset ->
            val pcm = NativeGeneratedAudioRenderer.renderPresetPcm(preset, (10 * 44100).toInt(), disableLayers = true)
            assertEquals("$preset 全禁用图层必须全 0 PCM", 0, pcm.maxOf { kotlin.math.abs(it.toInt()) })
            // 全静默 → 保持 LOADING（null），不发假 PLAYING，也不被超时误杀成 ERROR。
            assertEquals(null, NativeGeneratedAudioRenderer.startupAudibleDecision(wroteAudio = false, blockPeak = 0.0))
        }
    }

    /** 已出声门槛：检测到真实可听 PCM → AUDIBLE；低于门槛保持 null；已出声后不再干预。
     *  阶段 2b 起 PLAYING 不再由峰值决定（= 音频时钟启动），该决策只产出辅助状态 AUDIBLE。 */
    @Test fun startup_audible_gate_maps_silence_and_audio_correctly() {
        // 检测到可听峰值 → AUDIBLE（不是 PLAYING：PLAYING 由音频时钟启动决定）。
        assertEquals(PlaybackState.AUDIBLE, NativeGeneratedAudioRenderer.startupAudibleDecision(wroteAudio = false, blockPeak = 200.0))
        // 峰值低于门槛 → null（未出声）。
        assertEquals(null, NativeGeneratedAudioRenderer.startupAudibleDecision(wroteAudio = false, blockPeak = 0.0))
        assertEquals(null, NativeGeneratedAudioRenderer.startupAudibleDecision(wroteAudio = false, blockPeak = 8.0))
        // 已发出 AUDIBLE 后，后续块不再做启动决策。
        assertEquals(null, NativeGeneratedAudioRenderer.startupAudibleDecision(wroteAudio = true, blockPeak = 0.0))
    }

    /** 开场层（intro，替代全局隐藏 anchor-drone）：每个官方频道的配方里显式声明 intro 层，在首个可调度 tick
     *  （tick 0）保证触发一次，用频道自己的音色做即时反馈；DIY 删除所有音乐层时不产生任何隐藏声音。 */
    @Test fun intro_layer_fires_at_first_tick_with_preset_timbre() {
        val introLayerIds = mapOf(
            "deep-sea" to "deep-sea-floor",
            "rain-piano" to "rain-piano-notes",
            "morning-mist" to "mist-flute",
            "endless-focus" to "focus-electric",
            "night-train" to "train-drone",
            "warm-study" to "study-ep",
            "moon-tide" to "moon-drone",
            "signal-garden" to "signal-bell",
            "bamboo-strings" to "bamboo-flute",
            "custom-lab" to "lab-piano",
        )
        introLayerIds.forEach { (preset, layerId) ->
            val golden = NativeGeneratedAudioRenderer.schedulerGolden(preset, 4)
            assertTrue("$preset 的 intro 层 $layerId 应在首个可调度 tick（0~3，早于其 entryDelay）触发，实际：$golden", golden.any { it.endsWith(":$layerId") })
        }
    }

    /**
     * 诊断测试（renderer 测试路径，无 scenes，与 Web 官方站一致）：打印调度痕迹，验证
     * 1. sectionState 启动值与每层 chance（Web 纯公式，无地板）；
     * 2. 是否创建 Voice；
     * 3. 混音后是否真实出声（wroteAudio=true，峰值 ≥ AUDIBLE_FLOOR）。
     */
    @Test fun diagnostic_trace_reports_section_chance_voices_and_peaks() {
        val windows = mapOf("deep-sea" to 90.0, "rain-piano" to 90.0)
        windows.forEach { (preset, seconds) ->
            val trace = NativeGeneratedAudioRenderer.diagnosticTrace(preset, seconds)
            println("===== $preset diagnosticTrace @${seconds}s =====")
            println(trace)
            assertTrue("$preset 痕迹必须含 section 状态", trace.contains("section@t="))
            assertTrue("$preset 痕迹必须含 chance 值", trace.contains("chance="))
            assertTrue("$preset 必须创建过 Voice，实际：\n$trace", trace.contains("→ CREATED"))
            assertTrue("$preset 必须真实出声（wroteAudio=true），实际：\n$trace", trace.contains("wroteAudio=true"))
        }
    }

    /** 每个音色都真实产出非静音、不削波、无 NaN/∞ 的 PCM（合成路径，离线确定性）。 */
    @Test fun every_timbre_produces_audible_and_bounded_pcm() {
        val timbres = GENERATED_TIMBRES.map { it.id }
        assertTrue(timbres.isNotEmpty())
        timbres.forEach { timbre ->
            val pcm = NativeGeneratedAudioRenderer.pcmProbeForTimbre(timbre)
            assertTrue("$timbre 应产出非静音 PCM", pcm.any { it.toInt() != 0 })
            assertTrue("$timbre 不得削波，峰值 ${pcm.maxOf { kotlin.math.abs(it.toInt()) }}", pcm.maxOf { kotlin.math.abs(it.toInt()) } < Short.MAX_VALUE)
        }
    }

    /** 立式钢琴音色级校准（阶段 2e/2g）：gainTrimDb=+9dB 使同级响度与其它乐器一致，不再比同级异常低十几 dB。 */
    @Test fun upright_piano_gain_trim_closes_loudness_gap() {
        val piano = NativeGeneratedAudioRenderer.pcmProbeForTimbre("vsco-upright-piano")
        val baseline = NativeGeneratedAudioRenderer.pcmProbeForTimbre("soft-piano")
        val pianoPeak = piano.maxOf { kotlin.math.abs(it.toInt()) }.toDouble()
        val baselinePeak = baseline.maxOf { kotlin.math.abs(it.toInt()) }.toDouble()
        // +9dB ≈ 2.82x：校准后立式钢琴峰值应显著高于未校准基线，且不削波。
        assertTrue("立式钢琴经 gainTrimDb 校准后峰值($pianoPeak)应明显高于软键基线($baselinePeak)", pianoPeak > baselinePeak * 1.8)
        assertTrue("校准后不得削波，峰值 $pianoPeak", pianoPeak < Short.MAX_VALUE)
    }

    /**
     * 离线测量 10 个官方频道的 EBU R128 响度（自写简化测量，渲染 90s + 合成路径）。
     * 注意：这只是任务六之前的「自写简化测量」，不作为最终 EBU R128 结论——任务六要求改用
     * libebur128/FFmpeg 对真实采样、实际立体声、代表性 seed 测量，并按「专注/休息/助眠」分档写入
     * 版本化 manifest。此处仅校验：非静音、不削波，并打印实测值供后续校准参考。
     */
    @Test fun loudness_measurement_reports_all_presets() {
        val presets = listOf("rain-piano","custom-lab","bamboo-strings","morning-mist","warm-study","signal-garden","endless-focus","deep-sea","moon-tide","night-train")
        val reports = presets.associateWith { NativeGeneratedAudioRenderer.measurePresetLoudness(it, 90) }
        reports.forEach { (preset, report) -> println("LOUDNESS $preset -> $report") }
        reports.forEach { (preset, report) ->
            assertTrue("$preset 必须非静音（Integrated LUFS 有限）", report.integratedLufs.isFinite())
            assertTrue("$preset 不得削波（True Peak ≤ 0 dBTP），实际 ${report.truePeakDbTp}", report.truePeakDbTp <= 0.0)
        }
    }
}
