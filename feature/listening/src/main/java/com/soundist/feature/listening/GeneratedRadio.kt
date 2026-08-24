package com.soundist.feature.listening

import kotlin.math.roundToInt

/** App.tsx radioCatalog.ts GENERATED_TIMBRES 条目。 */
data class GeneratedTimbre(
    val id: String,
    val label: String,
    val family: String,
    val engine: String,
    val description: String,
    val layerTypes: List<String>,
)

/** App.tsx radioCatalog.ts GENERATED_TIMBRES (26–51)。 */
val GENERATED_TIMBRES: List<GeneratedTimbre> = listOf(
    GeneratedTimbre("vsco-upright-piano", "立式钢琴", "真实乐器", "sample", "VSCO 2 CE 的 CC0 立式钢琴采样，保留真实琴槌起音与自然衰减。", listOf("notes")),
    GeneratedTimbre("vsco-harp", "竖琴", "真实乐器", "sample", "VSCO 2 CE 的 CC0 竖琴采样，适合稀疏琶音和明亮点缀。", listOf("notes")),
    GeneratedTimbre("vsco-flute", "长笛", "真实乐器", "sample", "VSCO 2 CE 的 CC0 长笛持续音采样，适合留白充分的长线旋律。", listOf("notes")),
    GeneratedTimbre("vsco-violin", "小提琴组", "真实乐器", "sample", "VSCO 2 CE 的 CC0 小提琴组持续音采样，用于中高音弦乐铺底。", listOf("notes", "atmosphere")),
    GeneratedTimbre("vsco-cello", "大提琴组", "真实乐器", "sample", "VSCO 2 CE 的 CC0 大提琴组持续音采样，用于低位旋律与温暖和声。", listOf("notes", "atmosphere")),
    GeneratedTimbre("soft-piano", "合成柔键", "合成音色", "synth", "程序合成琴键，不冒充真实钢琴；适合低占用的柔和点音。", listOf("notes")),
    GeneratedTimbre("electric-piano", "FM 电键", "合成音色", "synth", "FM 程序合成音色，适合稳定专注与轻节拍。", listOf("notes")),
    GeneratedTimbre("harp-pluck", "合成拨弦", "合成音色", "synth", "短包络合成拨弦，不作为真实竖琴展示。", listOf("notes")),
    GeneratedTimbre("glass-bell", "玻璃钟音", "信号", "synth", "多泛音合成钟音，适合远处信号与空间标记。", listOf("notes", "atmosphere")),
    GeneratedTimbre("bamboo-flute", "气息长音", "合成音色", "synth", "带颤音的合成长音，不作为真实竹笛展示。", listOf("notes")),
    GeneratedTimbre("warm-strings", "暖弦铺底", "合成音色", "synth", "多声部程序合成铺底；需要真实弦乐时请选择小提琴组或大提琴组。", listOf("atmosphere")),
    GeneratedTimbre("vowel-choir", "元音共鸣", "人声纹理", "synth", "共振峰合成的无语义人声纹理，不作为真人合唱展示。", listOf("voice", "atmosphere")),
    GeneratedTimbre("air-pad", "空气 Pad", "合成音色", "synth", "低亮度合成铺底，使用包络与声像形成呼吸感。", listOf("atmosphere")),
    GeneratedTimbre("deep-drone", "深海 Drone", "合成音色", "synth", "缓慢拍频的低音合成铺底，适合助眠和低刺激段落。", listOf("atmosphere")),
    GeneratedTimbre("muted-pulse", "柔性脉冲", "节奏", "synth", "无鼓皮冲击的低频脉冲，用于稳定节拍定位。", listOf("rhythm")),
    GeneratedTimbre("rail-click", "轨道轻击", "节奏", "synth", "短促滤波点击，只在事件触发时产生。", listOf("rhythm")),
    GeneratedTimbre("sonar-ping", "声呐短波", "信号", "synth", "带轻微下滑与回声的短波信号。", listOf("notes", "rhythm")),
)

/** App.tsx radioCatalog.ts DEFAULT_TIMBRE_BY_LAYER (53–60)。 */
val DEFAULT_TIMBRE_BY_LAYER: Map<String, String> = mapOf(
    "notes" to "vsco-upright-piano",
    "rhythm" to "muted-pulse",
    "atmosphere" to "vsco-cello",
    "ambient" to "air-pad",
    "voice" to "vowel-choir",
    "modulation" to "air-pad",
)

/** App.tsx radioCatalog.ts normalizeGeneratedTimbre (62–76)。 */
fun normalizeGeneratedTimbre(value: String?, type: String): String {
    if (GENERATED_TIMBRES.any { it.id == value && type in it.layerTypes }) return value!!
    val legacy = value ?: ""
    if (Regex("竖琴|harp", RegexOption.IGNORE_CASE).containsMatchIn(legacy) && type == "notes") return "vsco-harp"
    if (Regex("竹笛|长笛|flute", RegexOption.IGNORE_CASE).containsMatchIn(legacy) && type == "notes") return "vsco-flute"
    if (Regex("小提琴|violin", RegexOption.IGNORE_CASE).containsMatchIn(legacy) && (type == "notes" || type == "atmosphere")) return "vsco-violin"
    if (Regex("大提琴|cello", RegexOption.IGNORE_CASE).containsMatchIn(legacy) && (type == "notes" || type == "atmosphere")) return "vsco-cello"
    if (Regex("电钢|electric", RegexOption.IGNORE_CASE).containsMatchIn(legacy)) return "electric-piano"
    if (Regex("钟|铃|bell", RegexOption.IGNORE_CASE).containsMatchIn(legacy)) return "glass-bell"
    if (Regex("吟唱|人声|choir|voice", RegexOption.IGNORE_CASE).containsMatchIn(legacy)) return "vowel-choir"
    if (Regex("脉冲|pulse", RegexOption.IGNORE_CASE).containsMatchIn(legacy)) return "muted-pulse"
    if (Regex("低音|drone", RegexOption.IGNORE_CASE).containsMatchIn(legacy)) return "deep-drone"
    if (Regex("钢琴|piano", RegexOption.IGNORE_CASE).containsMatchIn(legacy) && type == "notes") return "vsco-upright-piano"
    return DEFAULT_TIMBRE_BY_LAYER[type] ?: "air-pad"
}

/** App.tsx clampPercent (2285)：0-100 百分比取整并夹取。 */
fun clampPercent(value: Int): Int = value.coerceIn(0, 100)

/** App.tsx createGeneratorScenes (2352–2369)。 */
fun createGeneratorScenes(arc: String): List<GeneratedScene> = when (arc) {
    "steady" -> listOf(
        GeneratedScene("settle", "进入", 4, 36, 34, 42, 58),
        GeneratedScene("sustain", "稳定", 18, 48, 46, 48, 54),
        GeneratedScene("breathe", "留白", 5, 30, 26, 38, 66),
    )
    "journey" -> listOf(
        GeneratedScene("arrival", "引入", 5, 28, 24, 38, 62),
        GeneratedScene("unfold", "展开", 8, 52, 50, 54, 52),
        GeneratedScene("open", "开阔", 8, 70, 62, 68, 72),
        GeneratedScene("return", "回落", 6, 34, 28, 40, 68),
    )
    else -> listOf(
        GeneratedScene("inhale", "舒展", 4, 42, 34, 48, 64),
        GeneratedScene("hold", "停驻", 3, 58, 48, 56, 54),
        GeneratedScene("exhale", "松开", 5, 30, 24, 38, 72),
    )
}

/** App.tsx ambientTrackFromSound (2392–2405)。source sound.volume 为 0-100 百分比，Kotlin AmbientSound.volume 为 0-1。 */
fun ambientTrackFromSound(sound: AmbientSound): GeneratedAmbientTrack {
    val volumePercent = (if (sound.volume > 0f) sound.volume * 100f else 42f).roundToInt().coerceIn(0, 100)
    return GeneratedAmbientTrack(
        soundId = sound.id,
        name = sound.name,
        category = sound.category.label(),
        enabled = sound.active,
        volume = volumePercent,
        pan = 0,
        entryDelaySeconds = 0f,
        durationMinutes = 0,
        probability = 100,
        fadeInSeconds = 3f,
        fadeOutSeconds = 4f,
        relationship = "independent",
    )
}

/** App.tsx GENERATOR_OPTION_LABELS (2407–2428)。 */
val GENERATOR_OPTION_LABELS: Map<String, String> = mapOf(
    "steady" to "平稳",
    "breathing" to "呼吸",
    "journey" to "旅程",
    "free" to "自由节拍",
    "whole" to "全拍",
    "half" to "二分拍",
    "quarter" to "四分拍",
    "eighth" to "八分拍",
    "low" to "低音区",
    "middle" to "中音区",
    "high" to "高音区",
    "wide" to "宽音域",
    "pentatonic" to "五声音阶",
    "major" to "大调",
    "minor" to "小调",
    "dorian" to "多利亚调式",
    "independent" to "独立",
    "alternate" to "交替",
    "avoid" to "避让",
    "follow" to "跟随",
)

/** App.tsx generatorLayerTypes (5699–5701)：编排层类型显示与新增按钮标签。 */
val GENERATOR_LAYER_TYPES: List<Pair<String, String>> = listOf(
    "notes" to "旋律", "rhythm" to "节奏", "atmosphere" to "铺底", "voice" to "人声",
)

/** App.tsx 5966 generatorLayerTypes.find 显示标签。 */
fun generatorLayerTypeLabel(type: String): String? = GENERATOR_LAYER_TYPES.firstOrNull { it.first == type }?.second

/** App.tsx addGeneratorLayer labels (4668–4675)。 */
val GENERATOR_LAYER_LABELS: Map<String, Pair<String, String>> = mapOf(
    "notes" to ("旋律层" to "vsco-upright-piano"),
    "rhythm" to ("节奏层" to "muted-pulse"),
    "atmosphere" to ("氛围层" to "vsco-cello"),
    "ambient" to ("氛围层" to "air-pad"),
    "voice" to ("人声纹理层" to "vowel-choir"),
    "modulation" to ("氛围层" to "air-pad"),
)

/** App.tsx addGeneratorLayer (4667–4705) 新建层默认参数。 */
fun newGeneratorLayer(type: String, id: String): GeneratedLayer {
    val (name, timbre) = GENERATOR_LAYER_LABELS[type] ?: ("氛围层" to "air-pad")
    return GeneratedLayer(
        id = id,
        name = name,
        type = type,
        timbre = normalizeGeneratedTimbre(timbre, type),
        enabled = true,
        volume = if (type == "rhythm") 30 else 42,
        pan = 0,
        entryDelaySeconds = if (type == "atmosphere") 0f else 4f,
        durationSeconds = if (type == "rhythm") 1.2f else 6f,
        probability = if (type == "atmosphere") 86 else 58,
        density = if (type == "rhythm") 48 else 32,
        rhythm = if (type == "rhythm") "quarter" else "free",
        register = if (type == "atmosphere") "wide" else "middle",
        scale = "pentatonic",
        fadeInSeconds = if (type == "atmosphere") 4f else 1.2f,
        fadeOutSeconds = if (type == "atmosphere") 5f else 1.8f,
        ambientResponse = if (type == "ambient") 82 else 38,
        relationship = if (type == "rhythm") "follow" else "independent",
    )
}

/** App.tsx makeLayer (121–147)。 */
private fun makeLayer(
    id: String,
    name: String,
    type: String,
    timbre: String,
    volume: Int? = null,
    probability: Int? = null,
    density: Int? = null,
    durationSeconds: Float? = null,
    register: String? = null,
    rhythm: String? = null,
    fadeInSeconds: Float? = null,
    fadeOutSeconds: Float? = null,
    relationship: String? = null,
    ambientResponse: Int? = null,
): GeneratedLayer = GeneratedLayer(
    id = id,
    name = name,
    type = type,
    timbre = timbre,
    enabled = true,
    volume = volume ?: if (type == "atmosphere") 42 else if (type == "rhythm") 30 else 52,
    pan = 0,
    entryDelaySeconds = if (type == "atmosphere") 0f else 2f,
    durationSeconds = durationSeconds ?: if (type == "atmosphere") 12f else if (type == "rhythm") 0.8f else 3.4f,
    probability = probability ?: if (type == "atmosphere") 68 else if (type == "rhythm") 56 else 48,
    density = density ?: if (type == "atmosphere") 24 else if (type == "rhythm") 54 else 38,
    rhythm = rhythm ?: if (type == "rhythm") "quarter" else "free",
    register = register ?: if (type == "atmosphere") "low" else "middle",
    scale = "pentatonic",
    fadeInSeconds = fadeInSeconds ?: if (type == "atmosphere") 3.5f else 0.12f,
    fadeOutSeconds = fadeOutSeconds ?: if (type == "atmosphere") 4.5f else 2.2f,
    ambientResponse = ambientResponse ?: 64,
    relationship = relationship ?: if (type == "rhythm") "follow" else "independent",
)

/** App.tsx PRESET_ARRANGEMENTS (149–200)。 */
private fun presetLayers(preset: String): List<GeneratedLayer> = when (preset) {
    "deep-sea" -> listOf(
        makeLayer("deep-sea-floor", "深海低音", "atmosphere", "deep-drone", volume = 54, durationSeconds = 18f, probability = 76, density = 18, register = "low", fadeInSeconds = 5f, fadeOutSeconds = 7f),
        makeLayer("deep-sea-sonar", "远处声呐", "notes", "sonar-ping", volume = 38, probability = 28, density = 20, durationSeconds = 2.8f, register = "wide", relationship = "avoid", ambientResponse = 86),
        makeLayer("deep-sea-bell", "水下泛音", "notes", "glass-bell", volume = 30, probability = 34, density = 25, durationSeconds = 5.5f, register = "high", relationship = "alternate"),
    )
    "rain-piano" -> listOf(
        makeLayer("rain-piano-notes", "雨窗钢琴", "notes", "vsco-upright-piano", volume = 58, probability = 60, density = 42, durationSeconds = 4.2f, ambientResponse = 92),
        makeLayer("rain-piano-strings", "湿润弦光", "atmosphere", "vsco-cello", volume = 30, probability = 54, density = 16, durationSeconds = 14f, relationship = "avoid"),
        makeLayer("rain-piano-bell", "窗沿水滴", "notes", "glass-bell", volume = 24, probability = 25, density = 22, durationSeconds = 3.4f, register = "high", relationship = "alternate"),
    )
    "morning-mist" -> listOf(
        makeLayer("mist-flute", "晨雾长笛", "notes", "vsco-flute", volume = 42, probability = 38, density = 30, durationSeconds = 5.8f, register = "high", ambientResponse = 90),
        makeLayer("mist-harp", "露水竖琴", "notes", "vsco-harp", volume = 44, probability = 54, density = 36, durationSeconds = 2.4f, relationship = "alternate"),
        makeLayer("mist-air", "薄雾铺底", "atmosphere", "air-pad", volume = 32, probability = 58, density = 16, durationSeconds = 15f, register = "wide"),
    )
    "endless-focus" -> listOf(
        makeLayer("focus-electric", "稳定电钢", "notes", "electric-piano", volume = 48, probability = 46, density = 42, durationSeconds = 3.2f, rhythm = "half"),
        makeLayer("focus-pulse", "专注脉冲", "rhythm", "muted-pulse", volume = 28, probability = 76, density = 62, durationSeconds = 0.65f, rhythm = "quarter", relationship = "follow"),
        makeLayer("focus-strings", "低位大提琴", "atmosphere", "vsco-cello", volume = 26, probability = 54, density = 14, durationSeconds = 16f, register = "low"),
    )
    "night-train" -> listOf(
        makeLayer("train-drone", "车窗低频", "atmosphere", "deep-drone", volume = 38, probability = 70, density = 18, durationSeconds = 16f, register = "low", ambientResponse = 82),
        makeLayer("train-rail", "轨道节律", "rhythm", "rail-click", volume = 34, probability = 82, density = 68, durationSeconds = 0.45f, rhythm = "eighth", ambientResponse = 96),
        makeLayer("train-signal", "远站信号", "notes", "sonar-ping", volume = 30, probability = 24, density = 18, durationSeconds = 2.4f, register = "high", relationship = "avoid"),
    )
    "warm-study" -> listOf(
        makeLayer("study-ep", "暖色电钢", "notes", "electric-piano", volume = 54, probability = 58, density = 44, durationSeconds = 3.6f, ambientResponse = 74),
        makeLayer("study-harp", "纸页竖琴", "notes", "vsco-harp", volume = 30, probability = 34, density = 24, durationSeconds = 2.1f, relationship = "alternate"),
        makeLayer("study-pulse", "低速节拍", "rhythm", "muted-pulse", volume = 22, probability = 62, density = 50, rhythm = "quarter", relationship = "follow"),
    )
    "moon-tide" -> listOf(
        makeLayer("moon-drone", "月潮低音", "atmosphere", "deep-drone", volume = 50, probability = 78, density = 14, durationSeconds = 22f, fadeInSeconds = 7f, fadeOutSeconds = 8f, ambientResponse = 92),
        makeLayer("moon-choir", "远景人声", "voice", "vowel-choir", volume = 24, probability = 32, density = 15, durationSeconds = 10f, register = "wide", relationship = "avoid"),
        makeLayer("moon-bell", "月面泛音", "notes", "glass-bell", volume = 24, probability = 22, density = 16, durationSeconds = 6f, register = "high", relationship = "avoid"),
    )
    "signal-garden" -> listOf(
        makeLayer("signal-bell", "电子花粉", "notes", "glass-bell", volume = 46, probability = 62, density = 54, durationSeconds = 2.8f, register = "wide"),
        makeLayer("signal-harp", "枝叶拨弦", "notes", "harp-pluck", volume = 32, probability = 48, density = 40, durationSeconds = 1.8f, relationship = "alternate"),
        makeLayer("signal-pulse", "细粒脉冲", "rhythm", "muted-pulse", volume = 20, probability = 58, density = 48, rhythm = "eighth", relationship = "follow"),
    )
    "bamboo-strings" -> listOf(
        makeLayer("bamboo-flute", "竹影长笛", "notes", "vsco-flute", volume = 50, probability = 44, density = 30, durationSeconds = 6.5f, register = "high", ambientResponse = 92),
        makeLayer("bamboo-strings", "低声大提琴", "atmosphere", "vsco-cello", volume = 34, probability = 62, density = 18, durationSeconds = 16f, register = "low", relationship = "avoid"),
        makeLayer("bamboo-harp", "山涧竖琴", "notes", "vsco-harp", volume = 28, probability = 32, density = 24, durationSeconds = 2.2f, relationship = "alternate"),
    )
    else -> listOf(
        makeLayer("lab-piano", "主旋律", "notes", "vsco-upright-piano", volume = 52, probability = 52, density = 38),
        makeLayer("lab-pad", "氛围铺底", "atmosphere", "air-pad", volume = 34, probability = 62, density = 18, durationSeconds = 14f),
        makeLayer("lab-pulse", "节拍骨架", "rhythm", "muted-pulse", volume = 22, probability = 58, density = 46, rhythm = "quarter"),
    )
}

private fun ambient(
    id: String,
    volume: Int,
    pan: Int = 0,
    entry: Float = 0f,
    durationMinutes: Int = 0,
    probability: Int = 100,
    fadeIn: Float = 2f,
    fadeOut: Float = 3f,
    relationship: String = "independent",
): GeneratedAmbientTrack {
    val sound = SoundCatalog.items.first { it.id == id }
    return GeneratedAmbientTrack(
        soundId = id,
        name = sound.name,
        category = sound.category.label(),
        volume = volume,
        pan = pan,
        entryDelaySeconds = entry,
        durationMinutes = durationMinutes,
        probability = probability,
        fadeInSeconds = fadeIn,
        fadeOutSeconds = fadeOut,
        relationship = relationship,
    )
}

/** 每个官方持续声场都有自己的环境身份；核心轨必现，点缀轨使用确定性概率编排。 */
private fun presetAmbientTracks(preset: String): List<GeneratedAmbientTrack> = when (preset) {
    "deep-sea" -> listOf(
        ambient("underwater", 52, pan = -8, fadeIn = .8f, fadeOut = 5f),
        ambient("submarine", 25, pan = 12, entry = 3f, fadeIn = 5f, relationship = "avoid"),
        ambient("whale", 17, pan = 24, entry = 18f, probability = 55, fadeIn = 4f, fadeOut = 6f, relationship = "alternate"),
    )
    "rain-piano" -> listOf(
        ambient("rain-on-window", 55, pan = -10, fadeIn = .55f, fadeOut = 3f),
        ambient("light-rain", 27, pan = 14, entry = 1f, fadeIn = 3f, relationship = "follow"),
        ambient("droplets", 16, pan = 28, entry = 8f, probability = 72, fadeIn = 1.5f, fadeOut = 2.5f, relationship = "alternate"),
    )
    "morning-mist" -> listOf(
        ambient("wind-in-trees", 38, pan = -12, fadeIn = .8f, fadeOut = 4f),
        ambient("birds", 23, pan = 20, entry = 5f, probability = 84, fadeIn = 3f, relationship = "follow"),
        ambient("river", 20, pan = -22, entry = 3f, fadeIn = 4f, relationship = "avoid"),
    )
    "endless-focus" -> listOf(
        ambient("brown-noise", 24, fadeIn = .5f, fadeOut = 2f),
        ambient("library", 20, pan = -10, entry = 2f, fadeIn = 3f, relationship = "avoid"),
        ambient("keyboard", 12, pan = 22, entry = 12f, probability = 62, fadeIn = 2f, relationship = "alternate"),
    )
    "night-train" -> listOf(
        ambient("inside-a-train", 49, pan = -4, fadeIn = .7f, fadeOut = 4f),
        ambient("train", 27, pan = 18, entry = 2f, fadeIn = 4f, relationship = "follow"),
        ambient("light-rain", 15, pan = -24, entry = 15f, probability = 58, fadeIn = 5f, relationship = "avoid"),
    )
    "warm-study" -> listOf(
        ambient("cafe", 35, pan = -8, fadeIn = .65f, fadeOut = 3f),
        ambient("paper", 14, pan = 20, entry = 7f, probability = 66, fadeIn = 2f, relationship = "alternate"),
        ambient("keyboard", 16, pan = -20, entry = 4f, probability = 74, fadeIn = 2f, relationship = "follow"),
    )
    "moon-tide" -> listOf(
        ambient("waves", 43, pan = -12, fadeIn = .9f, fadeOut = 7f),
        ambient("underwater", 31, pan = 10, entry = 2f, fadeIn = 6f, relationship = "avoid"),
        ambient("wind", 13, pan = 26, entry = 15f, probability = 58, fadeIn = 6f, fadeOut = 7f, relationship = "alternate"),
    )
    "signal-garden" -> listOf(
        ambient("laboratory", 26, pan = -12, fadeIn = .65f, fadeOut = 3f),
        ambient("tuning-radio", 18, pan = 20, entry = 6f, probability = 78, fadeIn = 2f, relationship = "alternate"),
        ambient("morse-code", 12, pan = -26, entry = 12f, probability = 54, fadeIn = 1f, fadeOut = 2f, relationship = "follow"),
    )
    "bamboo-strings" -> listOf(
        ambient("wind-in-trees", 36, pan = -14, fadeIn = .8f, fadeOut = 5f),
        ambient("river", 24, pan = 14, entry = 2f, fadeIn = 4f, relationship = "avoid"),
        ambient("wind-chimes", 12, pan = 28, entry = 14f, probability = 50, fadeIn = 2f, fadeOut = 3f, relationship = "alternate"),
    )
    else -> emptyList()
}

/** App.tsx createDefaultGeneratedArrangement (202–217)。 */
fun createDefaultGeneratedArrangement(preset: String): GeneratedArrangement {
    val layers = presetLayers(preset)
    return GeneratedArrangement(
        seed = "$preset-soundist-02",
        sectionMinutes = if (preset == "moon-tide" || preset == "deep-sea") 8 else if (preset == "night-train") 4 else 5,
        arc = if (preset == "endless-focus" || preset == "warm-study") "steady" else if (preset == "night-train" || preset == "signal-garden") "journey" else "breathing",
        layers = layers.mapIndexed { index, layer ->
            layer.copy(
                relationshipTargetId = if (layer.relationship == "independent") null
                else layers.subList(0, index).reversed().firstOrNull { it.type != layer.type }?.id ?: layers[0].id,
            )
        },
        ambientTracks = presetAmbientTracks(preset),
        ambientMode = if (preset == "custom-lab") "current" else "preset",
    )
}

/** App.tsx normalizeGeneratedArrangement (2321–2350)。 */
fun normalizeGeneratedArrangement(arrangement: GeneratedArrangement?, preset: String): GeneratedArrangement {
    val fallback = createDefaultGeneratedArrangement(preset)
    if (arrangement == null) return fallback
    val normalizedLayers = arrangement.layers
        .filter { it.type != "ambient" && it.type != "modulation" }
        .map { it.copy(timbre = normalizeGeneratedTimbre(it.timbre, it.type)) }
    val usableLayers = normalizedLayers.mapIndexed { index, layer ->
        layer.copy(
            relationshipTargetId = when {
                layer.relationship == "independent" -> null
                normalizedLayers.any { it.id == layer.relationshipTargetId && it.id != layer.id } -> layer.relationshipTargetId
                else -> normalizedLayers.subList(0, index).reversed().firstOrNull { it.id != layer.id }?.id
                    ?: normalizedLayers.firstOrNull { it.id != layer.id }?.id
            },
        )
    }
    return fallback.copy(
        seed = arrangement.seed,
        sectionMinutes = arrangement.sectionMinutes,
        arc = arrangement.arc,
        layers = if (usableLayers.isNotEmpty()) usableLayers else fallback.layers,
        // 旧版内置频道会从数据库恢复一个空 ambientTracks。对官方预设补回当前版本的
        // 独立环境配方；自定义频道和明确选择“保留我的环境声”的编排仍尊重用户数据。
        ambientTracks = if (
            arrangement.ambientTracks.isNotEmpty() ||
            arrangement.ambientMode == "current" ||
            preset == "custom-lab"
        ) arrangement.ambientTracks else fallback.ambientTracks,
        scenes = if (arrangement.scenes.isNotEmpty()) arrangement.scenes else createGeneratorScenes(arrangement.arc.ifBlank { fallback.arc }),
        ambientMode = arrangement.ambientMode.takeIf { it == "preset" || it == "current" } ?: fallback.ambientMode,
    )
}

/** App.tsx timbreLabel (5710)。 */
fun timbreLabel(timbre: String): String = GENERATED_TIMBRES.firstOrNull { it.id == timbre }?.label ?: timbre

/** App.tsx DEFAULT_GENERATED_RADIO_SETTINGS (generativeRadio.ts 74–83)。 */
val DEFAULT_GENERATED_RADIO_SETTINGS = GeneratorSettings(
    tempo = 54,
    density = 42,
    variation = 48,
    entryDelaySeconds = 1.4f,
    eventDurationSeconds = 3.2f,
    gain = 72,
    ambientResponse = 70,
    adaptive = true,
)
