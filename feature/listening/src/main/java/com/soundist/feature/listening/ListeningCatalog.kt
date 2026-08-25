package com.soundist.feature.listening

object SoundCatalog {
    private val categoryNames = mapOf(
        SoundFilter.NATURE to "河流:river,海浪:waves,篝火:campfire,微风:wind,呼啸的风:howling-wind,林间风声:wind-in-trees,瀑布:waterfall,雪地脚步:walk-in-snow,落叶脚步:walk-on-leaves,碎石脚步:walk-on-gravel,水滴:droplets,丛林:jungle",
        SoundFilter.RAIN to "小雨:light-rain,大雨:heavy-rain,雷声:thunder,雨打窗户:rain-on-window,雨打车顶:rain-on-car-roof,雨打伞面:rain-on-umbrella,雨打帐篷:rain-on-tent,雨打树叶:rain-on-leaves",
        SoundFilter.ANIMALS to "鸟鸣:birds,海鸥:seagulls,蟋蟀:crickets,狼:wolf,猫头鹰:owl,青蛙:frog,狗叫:dog-barking,马蹄声:horse-gallop,猫呼噜:cat-purring,乌鸦:crows,鲸鱼:whale,蜂巢:beehive,啄木鸟:woodpecker,鸡群:chickens,牛群:cows,羊群:sheep",
        SoundFilter.URBAN to "高速公路:highway,道路:road,救护车警笛:ambulance-siren,繁忙街道:busy-street,人群:crowd,交通车流:traffic,烟花:fireworks",
        SoundFilter.PLACES to "咖啡馆:cafe,机场:airport,教堂:church,寺庙:temple,施工现场:construction-site,水下:underwater,拥挤酒吧:crowded-bar,夜晚村庄:night-village,地铁站:subway-station,办公室:office,超市:supermarket,旋转木马:carousel,实验室:laboratory,洗衣房:laundry-room,餐厅:restaurant,图书馆:library",
        SoundFilter.TRANSPORT to "火车:train,火车车厢内:inside-a-train,飞机:airplane,潜水艇:submarine,帆船:sailboat,划艇:rowing-boat",
        SoundFilter.THINGS to "键盘:keyboard,打字机:typewriter,纸张:paper,时钟:clock,风铃:wind-chimes,颂钵:singing-bowl,吊扇:ceiling-fan,烘干机:dryer,幻灯机:slide-projector,沸水:boiling-water,气泡:bubbles,调频收音机:tuning-radio,摩斯电码:morse-code,洗衣机:washing-machine,黑胶效果:vinyl-effect,雨刷器:windshield-wipers",
        SoundFilter.NOISE to "白噪音:white-noise,粉噪音:pink-noise,棕噪音:brown-noise",
    )

    val items: List<AmbientSound> = categoryNames.flatMap { (category, encoded) ->
        encoded.split(',').map { entry ->
            val (name, id) = entry.split(':')
            // App.tsx ALL_SOUNDS: every sound starts volume=0, active=false.
            val initial = emptyMap<String, Float>()
            AmbientSound(id = id, name = name, category = category, volume = initial[id] ?: 0f, active = id in initial, favorite = id in setOf("waves", "wind-in-trees", "rain-on-window", "traffic", "cafe", "train", "keyboard"))
        }
    }

    val builtInPresets = listOf(
        SoundPreset("p1", "夜雨森林", mapOf("light-rain" to .65f, "campfire" to .42f, "birds" to .32f, "white-noise" to .18f), true, "助眠"),
        SoundPreset("p2", "移动城市", mapOf("highway" to .38f, "crowd" to .26f, "cafe" to .48f, "keyboard" to .28f), true, "自由"),
        SoundPreset("p3", "深海呼吸", mapOf("waves" to .58f, "underwater" to .46f, "white-noise" to .20f), true, "自由"),
        SoundPreset("p4", "晨间咖啡馆", mapOf("cafe" to .50f, "keyboard" to .30f, "birds" to .22f, "light-rain" to .34f), true, "自由"),
        SoundPreset("p5", "火车入眠", mapOf("train" to .52f, "light-rain" to .36f, "white-noise" to .22f), true, "助眠"),
        SoundPreset("p6", "寺庙晨钟", mapOf("temple" to .42f, "wind-chimes" to .25f, "birds" to .18f), true, "自由"),
    )
}

object RadioCatalog {
    private const val PUBLIC_DOMAIN_URL = "https://creativecommons.org/public-domain/pdm/"
    private const val CC0_URL = "https://creativecommons.org/publicdomain/zero/1.0/"
    private const val BY_SA_2_URL = "https://creativecommons.org/licenses/by-sa/2.0/"
    private const val BY_SA_2_DE_URL = "https://creativecommons.org/licenses/by-sa/2.0/de/"
    private const val BY_SA_3_URL = "https://creativecommons.org/licenses/by-sa/3.0/"
    private const val BY_SA_4_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
    private const val BY_1_URL = "https://creativecommons.org/licenses/by/1.0/"
    private const val BY_3_URL = "https://creativecommons.org/licenses/by/3.0/"
    private const val BY_4_URL = "https://creativecommons.org/licenses/by/4.0/"

    private fun licenseUrl(name: String): String = when (name) {
        "Public Domain" -> PUBLIC_DOMAIN_URL
        "CC0 1.0" -> CC0_URL
        "CC BY 1.0" -> BY_1_URL
        "CC BY 3.0" -> BY_3_URL
        "CC BY 4.0" -> BY_4_URL
        "CC BY-SA 2.0" -> BY_SA_2_URL
        "CC BY-SA 2.0 DE" -> BY_SA_2_DE_URL
        "CC BY-SA 3.0" -> BY_SA_3_URL
        "CC BY-SA 4.0" -> BY_SA_4_URL
        else -> CC0_URL
    }
    private fun license(name: String, author: String, sourceName: String, sourcePage: String, attribution: String = "") = TrackLicense(
        name = name, author = author, sourceName = sourceName, sourcePage = sourcePage,
        licenseUrl = licenseUrl(name), attributionText = attribution,
    )

    private fun track(
        id: String, title: String, artist: String, durationLabel: String = "",
        mediaUrl: String = "", sourcePage: String = "",
        licenseName: String, sourceName: String, attribution: String = "",
        instruments: List<String> = emptyList(), era: String = "", focusFit: String = "",
    ) = RadioTrack(
        id = id, title = title, artist = artist, durationLabel = durationLabel,
        mediaUrl = mediaUrl, sourcePage = sourcePage,
        // 全量内置：所有曲目都打包进基础 APK（assets/radio/），由 radioAsset(id) 返回真实本地地址。
        localAssetUri = radioAsset(id),
        released = radioAsset(id) != null,
        license = license(licenseName, artist, sourceName, sourcePage, attribution),
        instruments = instruments, era = era, focusFit = focusFit,
    )
    private fun commonsTrack(id: String, title: String, artist: String, fileName: String, durationLabel: String = "", licenseName: String = "Public Domain", attribution: String = "", instruments: List<String> = emptyList(), era: String = "", focusFit: String = "listen") =
        track(id, title, artist, durationLabel, commonsAudio(fileName), commonsPage(fileName), licenseName, "Wikimedia Commons", attribution, instruments, era, focusFit)
    private fun ogaTrack(id: String, title: String, artist: String, url: String, sourcePage: String, durationLabel: String = "", licenseName: String = "CC0 1.0", attribution: String = "", instruments: List<String> = emptyList(), era: String = "", focusFit: String = "light") =
        track(id, title, artist, durationLabel, url, sourcePage, licenseName, "OpenGameArt", attribution, instruments, era, focusFit)
    private fun incompetechTrack(id: String, title: String, url: String, sourcePage: String, durationLabel: String, attribution: String, instruments: List<String> = emptyList(), focusFit: String = "deep") =
        track(id, title, "Kevin MacLeod", durationLabel, url, sourcePage, "CC BY 4.0", "Incompetech", attribution, instruments, focusFit = focusFit)
    /** radioCatalog.ts parseKnownDuration (652–664). */
    private fun parseKnownDuration(label: String): Int? {
        if (label.isBlank()) return null
        val parts = label.trim().split(":")
        if (parts.size != 2 && parts.size != 3) return null
        if (parts.any { it.toIntOrNull() == null }) return null
        val values = parts.map { it.toInt() }
        return if (parts.size == 2) {
            val (minutes, seconds) = values
            if (seconds < 60) minutes * 60 + seconds else null
        } else {
            val (hours, minutes, seconds) = values
            if (minutes < 60 && seconds < 60) hours * 3600 + minutes * 60 + seconds else null
        }
    }

    /** radioCatalog.ts formatKnownStationDuration (666–676). */
    private fun formatKnownStationDuration(tracks: List<RadioTrack>): String {
        val durations = tracks.map { parseKnownDuration(it.durationLabel) }
        if (durations.any { it == null }) return ""
        val totalSeconds = durations.filterNotNull().sum()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        else "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    /** radioCatalog.ts channel() (678–693)：官方站台聚合。catalogGroup 与 durationLabel 按前端推导。 */
    private val radioPurposeFilters = listOf("专注", "阅读", "放松", "助眠", "创作", "鉴赏")
    private val purposeAliases = mapOf(
        "专注" to setOf("专注", "深度工作", "学习", "编码", "整理", "冲刺", "重复任务", "轻任务"),
        "阅读" to setOf("阅读", "夜读", "晨间"),
        "放松" to setOf("放松", "休息", "冥想", "沉浸", "通勤", "夜间"),
        "助眠" to setOf("助眠"),
        "创作" to setOf("创作", "写作"),
        "鉴赏" to setOf("鉴赏"),
    )
    private fun normalizePurposes(purposes: List<String>): List<String> {
        if ("自定义" in purposes) return listOf("自定义")
        return radioPurposeFilters.filter { filter -> purposeAliases.getValue(filter).any { it in purposes } }.ifEmpty { listOf("放松") }
    }

    private fun channel(
        id: String, name: String, desc: String, genre: String, tracks: List<RadioTrack>, purposes: List<String>,
        transitionMode: String, transitionSeconds: Double, era: String = "", catalogGroup: String = "",
    ): RadioStation {
        // 只保留具备真实本地资产的曲目，防止目录生成无法播放的地址。
        val released = tracks.filter { it.released }
        return RadioStation(
            id = id, name = name, description = desc, group = RadioGroup.OFFICIAL, source = "开放精选", url = "",
            genre = genre, sourceKind = RadioSourceKind.OFFICIAL, purposes = normalizePurposes(purposes),
            durationLabel = formatKnownStationDuration(released), tracks = released,
            catalogGroup = catalogGroup.ifBlank {
                when {
                    genre.startsWith("古典") -> "古典"
                    genre.startsWith("器乐") -> "器乐"
                    genre.contains("人声") || genre.contains("合唱") -> "人声"
                    genre.contains("氛围") -> "氛围"
                    else -> "节拍"
                }
            },
            license = released.firstOrNull()?.license,
            transitionMode = transitionMode, transitionSeconds = transitionSeconds,
        )
    }

    /** radioCatalog.ts generated() (585–597) + GENERATED_RADIO_STATIONS。站台 id 带 generated- 前缀，preset id 不带。 */
    private fun generated(stationId: String, preset: String, name: String, desc: String, genre: String, purposes: List<String>): RadioStation {
        val arrangement = createDefaultGeneratedArrangement(preset)
        return RadioStation(
            id = stationId, name = name, description = desc, group = RadioGroup.GENERATED, source = "本机生成", url = "generated://$preset",
            genre = genre, sourceKind = RadioSourceKind.GENERATED, purposes = normalizePurposes(purposes), durationLabel = "无限",
            layers = arrangement.layers, generatorPresetId = preset, generatorSettings = GeneratorSettings(),
            // 对齐 generativeRadio.ts：官方生成站默认编排不含 scenes（Web 的 generated() 工厂没有 scenes 字段，
            // applyTimelineScene 对空 scenes 直接返回原状态）。给官方站强行塞入低值 scenes 会把启动态
            // energy/density/eventGate 再压低，使 deep-sea 等稀疏预设静默更久（signal-garden 前 13 分钟无声）。
            // 用户打开编排器后仍会经 normalizeGeneratedArrangement 补默认 scenes，自定义编排不受影响。
            generatorArrangement = arrangement,
            catalogGroup = "生成",
        )
    }

    // ── radioCatalog.ts 曲库分组 ──
    private val focus = listOf("专注", "阅读")
    private val relax = listOf("放松", "助眠")

    private val classical = listOf(
        commonsTrack("bach-air", "G弦上的咏叹调", "J. S. Bach / Joel Belov", "Air (Bach).ogg", "4:20", focusFit = "deep", instruments = listOf("小提琴", "钢琴"), era = "巴洛克"),
        commonsTrack("bach-sonata-adagio", "第一小提琴奏鸣曲：柔板", "J. S. Bach / 演奏者未注明", "J. S. Bach – Violin Sonata No.1 in G minor, BWV 1001, I. Adagio.ogg", licenseName = "CC0 1.0", attribution = "作曲：J. S. Bach；演奏者未注明；原始录音来源：Internet Archive；CC0 1.0", focusFit = "deep", instruments = listOf("小提琴"), era = "巴洛克"),
        commonsTrack("bach-partita-preludio", "第三小提琴组曲：前奏曲", "J. S. Bach / Musopen", "Johann Sebastian Bach - partita no. 3 in e major, bwv 1006 - 1. preludio.ogg", focusFit = "listen", instruments = listOf("小提琴"), era = "巴洛克"),
        commonsTrack("bach-chaconne", "恰空舞曲", "J. S. Bach / Ben Goldstein", "Johann Sebastian Bach - Chaconne for violin alone.ogg", "12:30", "CC BY-SA 3.0", "演奏：Ben Goldstein；来源：Pandora Music；CC BY-SA 3.0", listOf("小提琴"), "巴洛克", "listen"),
        commonsTrack("vivaldi-double-one", "D小调双小提琴协奏曲 I", "Vivaldi / Advent Chamber Orchestra", "Antonio Vivaldi - Concerto for Two Violins in D minor Op. 3 No. 11 - 1. Allegro - Adagio e spiccato - Allegro.ogg", "3:57", "CC BY-SA 2.0", "Advent Chamber Orchestra, CC BY-SA 2.0", listOf("小提琴", "室内乐"), "巴洛克", "listen"),
        commonsTrack("vivaldi-double-two", "D小调双小提琴协奏曲 II", "Vivaldi / Advent Chamber Orchestra", "Antonio Vivaldi - Concerto for Two Violins in D minor Op. 3 No. 11 - 2. Largo e spiccato.ogg", "2:14", "CC BY-SA 2.0", "Advent Chamber Orchestra, CC BY-SA 2.0", listOf("小提琴", "室内乐"), "巴洛克", "deep"),
        commonsTrack("vivaldi-double-three", "D小调双小提琴协奏曲 III", "Vivaldi / Advent Chamber Orchestra", "Antonio Vivaldi - Concerto for Two Violins in D minor Op. 3 No. 11 - 3. Allegro.ogg", "2:26", "CC BY-SA 2.0", "Advent Chamber Orchestra, CC BY-SA 2.0", listOf("小提琴", "室内乐"), "巴洛克", "light"),
        commonsTrack("mozart-flute-k313", "G大调长笛协奏曲 K.313 I", "W. A. Mozart / Musopen", "Wolfgang Amadeus Mozart - flute concerto in g major, k. 313 - i. allegro maestoso.ogg", "10:44", focusFit = "listen", instruments = listOf("长笛", "管弦乐"), era = "古典主义"),
        commonsTrack("beethoven-minuet", "G大调小步舞曲", "L. van Beethoven / Musopen", "Minuet in G (Beethoven), piano.ogg", focusFit = "light", instruments = listOf("钢琴"), era = "古典主义"),
        commonsTrack("beethoven-concerto-largo", "第一钢琴协奏曲：广板", "L. van Beethoven / Musopen", "Ludwig van Beethoven - piano concerto no. 1 in c major, op. 15 - ii. largo.ogg", focusFit = "deep", instruments = listOf("钢琴", "管弦乐"), era = "古典主义"),
        commonsTrack("chopin-nocturne-55", "降E大调夜曲 Op.55 No.2", "Frédéric Chopin / Musopen", "Frederic Chopin - nocturne no. 2 in e flat major, op. 55.ogg", focusFit = "deep", instruments = listOf("钢琴"), era = "浪漫主义"),
        commonsTrack("chopin-nocturne-62", "降E大调夜曲 Op.62 No.2", "Frédéric Chopin / Musopen", "Frederic Chopin - nocturne no. 2 in e flat major, op. 62.ogg", focusFit = "deep", instruments = listOf("钢琴"), era = "浪漫主义"),
        commonsTrack("chopin-etude-10-1", "C大调练习曲 Op.10 No.1", "Frédéric Chopin / Musopen", "Frederic Chopin - etude no. 1, op. 10 in c major.ogg", focusFit = "listen", instruments = listOf("钢琴"), era = "浪漫主义"),
        commonsTrack("chopin-mazurka-17-4", "A小调玛祖卡 Op.17 No.4", "Frédéric Chopin / Musopen", "Frederic Chopin - mazurka no. 4 in a minor, op. 17.ogg", focusFit = "deep", instruments = listOf("钢琴"), era = "浪漫主义"),
        commonsTrack("bach-wtc-prelude", "平均律第一卷：降E小调前奏曲", "J. S. Bach / Musopen", "Johann Sebastian Bach - book i- prelude and fugue no. 8 in e flat minor, bwv 853 - prelude.ogg", focusFit = "deep", instruments = listOf("钢琴"), era = "巴洛克"),
        commonsTrack("dvorak-larghetto", "弦乐小夜曲：小广板", "Antonín Dvořák / Pandora Music", "Dvorak - Serenade for Strings Op. 22 - Larghetto.ogg", "5:43", "CC BY-SA 2.0", "Performance from the Pandora Music collection, CC BY-SA 2.0", listOf("弦乐"), "浪漫主义", "deep"),
        commonsTrack("debussy-syrinx", "牧神笛", "Debussy / Sarah Bassingthwaite", "Debussy - Syrinx.ogg", "2:26", "CC BY-SA 2.0", "Sarah Bassingthwaite, CC BY-SA 2.0", listOf("长笛"), "印象主义", "listen"),
        commonsTrack("andersen-etude", "长笛练习曲 Op.15 No.3", "Joachim Andersen / Scott Goff", "Joachim Andersen - Etude 3 for Solo Flute.oga", "2:02", "CC BY-SA 2.0", "Scott Goff, CC BY-SA 2.0", listOf("长笛"), "浪漫主义", "listen"),
        commonsTrack("vivaldi-notte-one", "夜之协奏曲 I", "Antonio Vivaldi / 演奏者未注明", "Antonio Vivaldi - La Notte - 1.ogg", "1:11", "CC BY-SA 2.0", "演奏者未注明；上传者自有录音；CC BY-SA 2.0", listOf("长笛", "室内乐"), "巴洛克", "light"),
        commonsTrack("vivaldi-notte-two", "夜之协奏曲 II", "Antonio Vivaldi / 演奏者未注明", "Antonio Vivaldi - La Notte - 2.ogg", "0:47", "CC BY-SA 3.0", "演奏者未注明；上传者自有录音；CC BY-SA 3.0", listOf("长笛", "室内乐"), "巴洛克", "deep"),
        commonsTrack("vivaldi-notte-four", "夜之协奏曲 IV", "Antonio Vivaldi / 演奏者未注明", "Antonio Vivaldi - La Notte - 4.ogg", "0:57", "CC BY-SA 2.0", "演奏者未注明；上传者自有录音；CC BY-SA 2.0", listOf("长笛", "室内乐"), "巴洛克", "light"),
    )

    private val classicalExpanded = listOf(
        commonsTrack("bach-bwv147-chorale", "耶稣，世人仰望的喜悦", "J. S. Bach / Orchestra Gli Armonici", "Bach, BWV 147, 10. Jesus bleibet meine Freude.ogg", "3:18", "CC0 1.0", instruments = listOf("室内乐", "合唱旋律"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("bach-goldberg-aria", "哥德堡变奏曲：咏叹调", "J. S. Bach / Musopen", "Bach, Goldberg Variations, Aria (Musopen version).ogg", "4:44", "CC0 1.0", instruments = listOf("钢琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("albinoni-oboe-one", "D小调双簧管协奏曲 I", "Tomaso Albinoni / Musopen", "Albinoni, Concerto for Oboe and Strings No. 2 in D minor, Op. 9, I. Allegro e con presto.ogg", licenseName = "CC0 1.0", instruments = listOf("双簧管", "弦乐"), era = "巴洛克", focusFit = "light"),
        commonsTrack("albinoni-oboe-two", "D小调双簧管协奏曲 II：柔板", "Tomaso Albinoni / Musopen", "Albinoni, Concerto for Oboe and Strings No. 2 in D minor, Op. 9, II. Adagio.ogg", licenseName = "CC0 1.0", instruments = listOf("双簧管", "弦乐"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("albinoni-oboe-three", "D小调双簧管协奏曲 III", "Tomaso Albinoni / Musopen", "Albinoni, Concerto for Oboe and Strings No. 2 in D minor, Op. 9, III. Allegro.ogg", licenseName = "CC0 1.0", instruments = listOf("双簧管", "弦乐"), era = "巴洛克", focusFit = "light"),
        commonsTrack("beethoven-sonata-28-one", "第二十八钢琴奏鸣曲 I", "L. van Beethoven / Musopen", "Beethoven - Piano Sonata No. 28 in A Major, Op. 101 - I. Etwas lebhaft, und mit der innigsten Empfindung.ogg", focusFit = "deep", instruments = listOf("钢琴"), era = "古典主义"),
        commonsTrack("beethoven-sonata-28-three", "第二十八钢琴奏鸣曲 III", "L. van Beethoven / Musopen", "Beethoven - Piano Sonata No. 28 in A Major, Op. 101 - III. Langsam und sehnsuchtsvoll.ogg", focusFit = "deep", instruments = listOf("钢琴"), era = "古典主义"),
        commonsTrack("beethoven-hammerklavier-adagio", "槌子键琴奏鸣曲：柔板", "L. van Beethoven / Musopen", "Beethoven, Piano Sonata No. 29 in B-flat Major, Op. 106 Hammerklavier - III. Adagio sostenuto.ogg", licenseName = "CC0 1.0", instruments = listOf("钢琴"), era = "古典主义", focusFit = "deep"),
        commonsTrack("beethoven-quartet-six-adagio", "第六弦乐四重奏 II：柔板", "L. van Beethoven / Musopen String Quartet", "Beethoven - String Quartet No. 6 in B flat major, Op. 18 No. 6 - II. Adagio, ma non troppo (Musopen String Quartet).flac", focusFit = "deep", instruments = listOf("弦乐四重奏"), era = "古典主义"),
        commonsTrack("beethoven-quartet-six-malinconia", "第六弦乐四重奏 IV：忧郁", "L. van Beethoven / Musopen String Quartet", "Beethoven - String Quartet No. 6 in B flat major, Op. 18 No. 6 - IV. La malinconia (Musopen String Quartet).flac", focusFit = "listen", instruments = listOf("弦乐四重奏"), era = "古典主义"),
        commonsTrack("dvorak-new-world-largo", "自新大陆交响曲 II：广板", "Antonín Dvořák / Musopen", "Antonin Dvorak - symphony no. 9 in e minor 'from the new world', op. 95 - ii. largo.ogg", focusFit = "deep", instruments = listOf("管弦乐"), era = "浪漫主义"),
        commonsTrack("smetana-moldau", "我的祖国：沃尔塔瓦河", "Bedřich Smetana / Musopen", "Bedrich Smetana - ma vlast - i. vltava 'the moldau'.ogg", focusFit = "listen", instruments = listOf("管弦乐"), era = "浪漫主义"),
        commonsTrack("glazunov-menestrel", "吟游诗人之歌 Op.71", "Alexander Glazunov / Musopen", "Alexander Glazunov - chant du menestrel, op. 71.ogg", focusFit = "deep", instruments = listOf("大提琴", "钢琴"), era = "浪漫主义"),
        commonsTrack("scriabin-prelude-67-1", "前奏曲 Op.67 No.1", "Alexander Scriabin / Musopen", "Alexander Scriabin - prelude no. 1, op. 67.ogg", focusFit = "deep", instruments = listOf("钢琴"), era = "现代主义"),
        commonsTrack("borodin-steppes", "在中亚细亚草原上", "Alexander Borodin / Musopen Symphony Orchestra", "Alexander Borodin - In The Steppes Of Central Asia.ogg", licenseName = "CC0 1.0", instruments = listOf("管弦乐"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("mendelssohn-nocturne", "仲夏夜之梦：夜曲", "Felix Mendelssohn / European Archive", "A Midsummer Night's Dream, Op. 61 - Nocturne.ogg", focusFit = "deep", instruments = listOf("管弦乐"), era = "浪漫主义"),
        commonsTrack("chopin-ballade-two", "第二叙事曲 Op.38", "Frédéric Chopin / Musopen", "Ballade no. 2 - Op. 38.mp3", licenseName = "CC0 1.0", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "listen"),
        commonsTrack("chopin-ballade-three", "第三叙事曲 Op.47", "Frédéric Chopin / Musopen", "Ballade no. 3 - Op. 47.mp3", licenseName = "CC0 1.0", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "listen"),
        commonsTrack("chopin-barcarolle", "船歌 Op.60", "Frédéric Chopin / Olga Gurevich", "Barcarolle-op-60.ogg", licenseName = "CC BY-SA 4.0", attribution = "Olga Gurevich，CC BY-SA 4.0", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "listen"),
    )

    private val calmClassics = listOf(
        commonsTrack("pachelbel-canon-piano-galloway", "D大调卡农：钢琴版", "Johann Pachelbel / Lee Galloway", "Pachelbel's Canon.ogg", "2:51", "CC BY-SA 3.0", "Pachelbel's Canon，钢琴演奏：Lee Galloway，CC BY-SA 3.0", listOf("钢琴"), "巴洛克改编", "deep"),
        commonsTrack("pachelbel-canon-kevin-macleod", "D大调卡农：室内乐版", "Johann Pachelbel / Kevin MacLeod", "Kevin MacLeod - Canon in D Major.ogg", "5:56", "CC BY 3.0", "Canon in D Major，演奏与录音：Kevin MacLeod，CC BY 3.0", listOf("弦乐", "室内乐"), "巴洛克改编", "deep"),
        commonsTrack("debussy-clair-de-lune-goedhart", "月光", "Claude Debussy / Laurens Goedhart", "Clair de lune (Claude Debussy) Suite bergamasque.ogg", "5:04", "CC BY 3.0", "Clair de lune，钢琴演奏：Laurens Goedhart，CC BY 3.0", listOf("钢琴"), "印象主义", "deep"),
        commonsTrack("debussy-arabesque-one-prati", "第一阿拉伯风格曲", "Claude Debussy / Patrizia Prati", "Claude Debussy - Première Arabesque - Patrizia Prati.ogg", "4:53", "CC BY-SA 4.0", "Première Arabesque，钢琴演奏：Patrizia Prati，CC BY-SA 4.0", listOf("钢琴"), "印象主义", "deep"),
        commonsTrack("satie-gymnopedie-one-macleod", "第一号裸体舞曲", "Erik Satie / Kevin MacLeod", "Gymnopedie No. 1 (ISRC USUAN1100787).mp3", "3:07", "CC BY 3.0", "Gymnopedie No. 1，编曲与演奏：Kevin MacLeod，CC BY 3.0", listOf("钢琴"), "现代主义", "deep"),
        commonsTrack("mendelssohn-venetian-gondola-30-6", "威尼斯船歌 Op.30 No.6", "Felix Mendelssohn / Membeth", "Mendelssohn.Venetianisches.Gondellied.opus.30.6.ogg", "3:06", "CC0 1.0", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("grieg-piano-concerto-adagio", "A小调钢琴协奏曲 II：柔板", "Edvard Grieg / Skidmore College Orchestra", "Edvard Grieg - piano concerto in a minor, op. 16 - ii. adagio.ogg", "6:21", attribution = "Public-domain recording from Musopen; Skidmore College Orchestra", focusFit = "deep", instruments = listOf("钢琴", "管弦乐"), era = "浪漫主义"),
    )

    private val easternTraditional = listOf(
        commonsTrack("erhu-erquan", "二泉映月", "阿炳 / 演奏：张沛坚", "二泉映月.ogg", "4:28", "CC BY-SA 4.0", "二泉映月，演奏：张沛坚，CC BY-SA 4.0", listOf("二胡"), "中国传统器乐", "listen"),
        commonsTrack("erhu-river", "江河水", "民间乐曲 / 演奏：张沛坚", "江河水.ogg", "5:02", "CC BY-SA 4.0", "江河水，演奏：张沛坚，CC BY-SA 4.0", listOf("二胡"), "中国传统器乐", "listen"),
        commonsTrack("gaohu-linked-buckles", "连环扣", "严老烈 / 演奏：张沛坚", "连环扣.ogg", "3:45", "CC BY-SA 4.0", "连环扣，演奏：张沛坚，CC BY-SA 4.0", listOf("高胡"), "广东音乐", "light"),
        commonsTrack("gaohu-rain-banana", "雨打芭蕉", "广东音乐 / 演奏：张沛坚", "雨打芭蕉.ogg", "4:15", "CC BY-SA 4.0", "雨打芭蕉，演奏：张沛坚，CC BY-SA 4.0", listOf("高胡"), "广东音乐", "light"),
        commonsTrack("guqin-yangguan-sandie", "阳关三叠", "古曲 / Charlie Huang", "Guqin-Yangguan Sandie.ogg", "5:50", "CC BY-SA 3.0", "阳关三叠，古琴演奏与录音：Charlie Huang，CC BY-SA 3.0", listOf("古琴"), "中国传统器乐", "deep"),
        commonsTrack("guqin-zuiyu-changwan", "醉渔唱晚", "古曲 / Charlie Huang", "Guqin-Zuiyu Changwan.ogg", "4:47", "CC BY-SA 3.0", "醉渔唱晚，古琴演奏与录音：Charlie Huang，CC BY-SA 3.0", listOf("古琴"), "中国传统器乐", "deep"),
    )

    private val celloTracks = listOf(
        commonsTrack("cello-bach-cc0-prelude", "第一无伴奏大提琴组曲：前奏曲", "J. S. Bach / Fopseh", "Bach Cello Suite 1 Prelude (BWV 1007) Played by Chris.ogg", "2:35", "CC0 1.0", instruments = listOf("大提琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("cello-bach-casals-prelude", "第一无伴奏大提琴组曲：前奏曲（卡萨尔斯）", "J. S. Bach / Pablo Casals", "Bach - Cello Suite no. 1 in G major, BWV 1007 - I. Prélude.ogg", "2:30", focusFit = "listen", instruments = listOf("大提琴"), era = "巴洛克"),
        commonsTrack("cello-bach-allemande", "第一无伴奏大提琴组曲：阿勒曼德", "J. S. Bach / Pablo Casals", "Bach - Cello Suite no. 1 in G major, BWV 1007 - II. Allemande.ogg", "3:42", focusFit = "deep", instruments = listOf("大提琴"), era = "巴洛克"),
        commonsTrack("cello-bach-sarabande", "第一无伴奏大提琴组曲：萨拉班德", "J. S. Bach / Pablo Casals", "Bach - Cello Suite no. 1 in G major, BWV 1007 - IV. Sarabande.ogg", "2:24", focusFit = "deep", instruments = listOf("大提琴"), era = "巴洛克"),
        commonsTrack("cello-vivaldi-largo", "G大调大提琴协奏曲：广板", "Vivaldi / Stephen Balderston 与 Advent Chamber Orchestra", "Vivaldi - Cello Concerto Gmaj - 2. Largo.ogg", "3:58", "CC BY-SA 2.0", "Stephen Balderston 与 Advent Chamber Orchestra，CC BY-SA 2.0", listOf("大提琴", "室内乐"), "巴洛克", "deep"),
    )

    private val organTracks = listOf(
        commonsTrack("organ-buxtehude-toccata", "F大调托卡塔 BuxWV 161", "Dieterich Buxtehude / Martin Wadsack", "Buxtehude - Toccata in F-Dur BuxWV 161.ogg", "7:34", "CC BY-SA 3.0", "Martin Wadsack，CC BY-SA 3.0", listOf("管风琴"), "巴洛克", "listen"),
        commonsTrack("organ-pachelbel-ciacona", "F小调恰空", "Johann Pachelbel / Burghard Fischer", "Johann Pachelbel Ciacona in f-Moll.ogg", "7:25", "CC BY-SA 3.0", "Burghard Fischer，CC BY-SA 3.0", listOf("管风琴"), "巴洛克", "deep"),
        commonsTrack("organ-pachelbel-chorale", "来吧，异教徒的救主", "Johann Pachelbel / Burghard Fischer", "Johann Pachelbel Nun komm der Heiden Heiland.ogg", "3:42", "CC BY-SA 3.0", "Burghard Fischer，CC BY-SA 3.0", listOf("管风琴"), "巴洛克", "deep"),
    )

    private val choralTracks = listOf(
        commonsTrack("choral-racine", "拉辛雅歌", "Gabriel Fauré / Les Petits Chanteurs de Passy", "Petits Chanteurs de Passy - Cantique de Racine de Gabriel Faure.ogg", "5:20", "CC BY-SA 3.0", "Les Petits Chanteurs de Passy，CC BY-SA 3.0", listOf("合唱", "管风琴"), "浪漫主义", "listen"),
        commonsTrack("choral-pavane", "帕凡舞曲", "Thoinot Arbeau / Les Petits Chanteurs de Passy", "Petits Chanteurs de Passy - Pavane de Thoinot Arbeau.ogg", "2:17", "CC BY-SA 3.0", "Les Petits Chanteurs de Passy，CC BY-SA 3.0", listOf("阿卡贝拉", "合唱"), "文艺复兴", "light"),
        commonsTrack("choral-salve-regina", "万福母后", "Hermann of Reichenau / Les Petits Chanteurs de Passy", "Petits Chanteurs de Passy - Salve Regina de Hermann Contract.ogg", "3:10", "CC BY-SA 3.0", "Les Petits Chanteurs de Passy，CC BY-SA 3.0", listOf("阿卡贝拉", "格里高利圣咏"), "中世纪", "listen"),
        commonsTrack("choral-tollite", "Tollite Hostias", "Camille Saint-Saëns / Les Petits Chanteurs de Passy", "Petits Chanteurs de Passy - Tollite Hostias de Saint-Saens.ogg", "1:58", "CC BY-SA 3.0", "Les Petits Chanteurs de Passy，CC BY-SA 3.0", listOf("阿卡贝拉", "合唱"), "浪漫主义", "listen"),
        commonsTrack("choral-resonet-laudibus", "同声欢颂", "Orlande de Lassus / Cipoo participants", "ResonetInLaudibus.ogg", "3:25", instruments = listOf("合唱", "阿卡贝拉"), era = "文艺复兴", focusFit = "listen"),
        commonsTrack("choral-handel-glory", "上帝的荣光", "G. F. Handel / MIT Concert Choir", "Handel - messiah - 04 and the glory of the lord.ogg", "2:37", "CC BY-SA 2.0", "Handel, And the Glory of the Lord，MIT Concert Choir，CC BY-SA 2.0", listOf("合唱", "管弦乐"), "巴洛克", "listen"),
        commonsTrack("choral-handel-word", "主发命令", "G. F. Handel / MIT Concert Choir", "Handel - messiah - 37 the lord gave the word.ogg", "1:18", "CC BY-SA 2.0", "Handel, The Lord Gave the Word，MIT Concert Choir，CC BY-SA 2.0", listOf("合唱", "管弦乐"), "巴洛克", "listen"),
    )

    private val verifiedInstrumental = listOf(
        commonsTrack("guitar-anonymous-romance", "爱的罗曼史", "佚名 / Jim Greeninger", "Romance Anónimo (Jeux interdits).ogg", "2:47", "CC0 1.0", instruments = listOf("古典吉他"), era = "传统吉他", focusFit = "deep"),
        commonsTrack("guitar-el-noi", "母亲之子", "加泰罗尼亚民歌 / Jujutacular", "El Noi de la Mare (guitar).ogg", "2:13", "CC BY-SA 3.0", "El Noi de la Mare，演奏与录音：Jujutacular，CC BY-SA 3.0", listOf("古典吉他"), "传统吉他", "deep"),
        commonsTrack("guitar-sor-op31-1", "索尔作品 31：第一首", "Fernando Sor / Jujutacular", "Sor Op 31 No 1 Rec 2.ogg", "1:21", "CC BY-SA 3.0", "Fernando Sor Op.31 No.1，演奏与录音：Jujutacular，CC BY-SA 3.0", listOf("古典吉他"), "古典主义", "deep"),
        commonsTrack("guitar-pachelbel-canon", "D大调卡农：吉他版", "Johann Pachelbel / Aitua", "Pachelbel - Canon in D major, P. 37 (Guitar).ogg", "3:21", "CC BY-SA 4.0", "Pachelbel Canon in D major (Guitar)，演奏：Aitua，CC BY-SA 4.0", listOf("古典吉他"), "巴洛克改编", "light"),
        commonsTrack("zither-strauss-woods", "维也纳森林故事：齐特琴独奏", "Johann Strauss II / Musopen", "Zither solo from G'schichten aus dem Wienerwald Op.325.ogg", "1:04", "CC0 1.0", instruments = listOf("齐特琴"), era = "浪漫主义", focusFit = "light"),
        commonsTrack("mozart-flute-harp-andantino", "长笛与竖琴协奏曲 II：行板", "W. A. Mozart / Alexander Murray、Ann Yeung", "Mozart - Concerto for Flute and Harp - 2. Andantino.ogg", "9:39", "CC BY-SA 2.0", "Alexander Murray（长笛）、Ann Yeung（竖琴）、Sinfonia da Camera，CC BY-SA 2.0", listOf("长笛", "竖琴", "室内乐"), "古典主义", "deep"),
        commonsTrack("mozart-clarinet-adagio", "单簧管协奏曲 II：柔板", "W. A. Mozart / Markus Krumpöck", "Mozart Clarinet Concert - 2. Adagio.ogg", "7:15", "CC BY-SA 3.0", "Markus Krumpöck（单簧管）、Merkur Orchester Wiener Neustadt，CC BY-SA 3.0", listOf("单簧管", "管弦乐"), "古典主义", "deep"),
        commonsTrack("brahms-clarinet-quintet-adagio", "单簧管五重奏 II：慢板", "Johannes Brahms / William McColl、Orford String Quartet", "Brahms - Clarinet Quintet - 2. Adagio.ogg", "11:46", "CC BY-SA 2.0", "William McColl 与 Orford String Quartet，CC BY-SA 2.0", listOf("单簧管", "弦乐四重奏"), "浪漫主义", "deep"),
        commonsTrack("weber-grand-duo-andante", "大二重奏 II：流动的行板", "Carl Maria von Weber / William McColl、Joseph Levine", "Weber - Grand Duo Concertant for clarinet and piano - 2. Andante con moto.ogg", "6:23", "CC BY-SA 2.0", "William McColl（单簧管）、Joseph Levine（钢琴），CC BY-SA 2.0", listOf("单簧管", "钢琴"), "浪漫主义", "light"),
        commonsTrack("beethoven-cello-sonata-three-two", "第三大提琴奏鸣曲 II", "Ludwig van Beethoven / Hielko Ubel", "Ludwig van Beethoven — Cello Sonata No. 3 (2nd movement).ogg", "3:30", "CC0 1.0", instruments = listOf("大提琴", "钢琴"), era = "古典主义", focusFit = "light"),
        commonsTrack("guitar-tarrega-gran-vals", "大圆舞曲", "Francisco Tárrega / Joni Ikäläinen", "Francisco Tárrega - Gran Vals.ogg", "3:02", "CC0 1.0", instruments = listOf("古典吉他"), era = "浪漫主义", focusFit = "light"),
        commonsTrack("mozart-flute-harp-allegro", "长笛与竖琴协奏曲 I：快板", "W. A. Mozart / Alexander Murray、Ann Yeung", "Mozart - Concerto for Flute and Harp - 1. Allegro.ogg", "12:08", "CC BY-SA 2.0", "Alexander Murray（长笛）、Ann Yeung（竖琴）、Sinfonia da Camera，CC BY-SA 2.0", listOf("长笛", "竖琴", "室内乐"), "古典主义", "listen"),
        commonsTrack("mozart-flute-harp-rondeau", "长笛与竖琴协奏曲 III：回旋快板", "W. A. Mozart / Alexander Murray、Ann Yeung", "Mozart - Concerto for Flute and Harp - 3. Rondeau Allegro.ogg", "12:02", "CC BY-SA 2.0", "Alexander Murray（长笛）、Ann Yeung（竖琴）、Sinfonia da Camera，CC BY-SA 2.0", listOf("长笛", "竖琴", "室内乐"), "古典主义", "listen"),
        ogaTrack("harp-meadow-thoughts", "Meadow Thoughts", "Écrivain", "https://opengameart.org/sites/default/files/Meadow%20Thoughts.ogg", "https://opengameart.org/content/meadow-thoughts", "2:29", focusFit = "deep", instruments = listOf("竖琴"), era = "开放录音"),
    )

    private val open = listOf(
        commonsTrack("chill-beat", "Chill Beat", "Maddy", "Chill Beat.ogg", "1:36", "CC0 1.0", instruments = listOf("Lo-fi"), focusFit = "light"),
        commonsTrack("rhapsody-blue", "Rhapsody in Blue：钢琴独奏", "George Gershwin / hmscomp", "George Gershwin's \"Rhapsody in Blue\" piano solo.ogg", "15:54", "CC0 1.0", "作曲：George Gershwin；钢琴演奏：hmscomp；CC0 1.0", instruments = listOf("钢琴"), focusFit = "light"),
        commonsTrack("jazz-park", "Jazz at the Park", "Manwithmetalpig", "Jazz at the park.ogg", "3:26", "CC0 1.0", instruments = listOf("爵士"), focusFit = "light"),
        commonsTrack("guitar-solo", "F♯小调吉他独奏", "Leechfoot", "Wikipedia guitar solo.ogg", "0:42", "CC0 1.0", instruments = listOf("吉他"), focusFit = "light"),
        commonsTrack("synth-hiphop", "Friendly Evil Synth Hip-hop", "Mesostic", "Friendly Evil Gangsta Synth Hip Hop.ogg", "6:15", "CC0 1.0", instruments = listOf("Hip-hop"), focusFit = "light"),
        commonsTrack("game-bgm", "Game BGM", "Yuyuyunoyuusuke1", "GameBGM.ogg", "3:57", "CC0 1.0", instruments = listOf("电子"), focusFit = "light"),
        ogaTrack("lofi-again", "Lofi Again", "omfgdude", "https://opengameart.org/sites/default/files/lofiagain.ogg", "https://opengameart.org/content/lofi-again", focusFit = "deep", instruments = listOf("Lo-fi")),
        ogaTrack("chill-lofi", "Chill Lofi", "omfgdude", "https://opengameart.org/sites/default/files/ChillLofi.ogg", "https://opengameart.org/content/chill-lofi-inspired", focusFit = "light", instruments = listOf("Lo-fi", "钢琴")),
        ogaTrack("lofi-hiphop", "Lofi Hip-hop", "omfgdude", "https://opengameart.org/sites/default/files/lofihiphop.ogg", "https://opengameart.org/content/lofi-hip-hop", focusFit = "light", instruments = listOf("Hip-hop")),
        ogaTrack("jazz-simple", "Jazz No.2", "Spring Spring", "https://opengameart.org/sites/default/files/jazz_2.ogg", "https://opengameart.org/content/jazz-1", focusFit = "light", instruments = listOf("爵士")),
        ogaTrack("emotional-piano", "Emotional Piano Solo", "Centurion_of_war", "https://opengameart.org/sites/default/files/emotional_piano_solo_0.ogg", "https://opengameart.org/content/emotional-piano-0", focusFit = "deep", instruments = listOf("钢琴")),
        ogaTrack("fairies-talking", "Fairies Talking", "KiluaBoy", "https://opengameart.org/sites/default/files/FairiesTalking.ogg", "https://opengameart.org/content/sci-fi-adventure-eastern-quiet-piano-loop", focusFit = "sleep", instruments = listOf("钢琴", "人声纹理")),
        ogaTrack("into-stars", "Into the Stars", "KiluaBoy", "https://opengameart.org/sites/default/files/IntoTheStars.ogg", "https://opengameart.org/content/sci-fi-adventure-eastern-quiet-piano-loop", focusFit = "deep", instruments = listOf("电子")),
        ogaTrack("not-that-east", "Not That East", "KiluaBoy", "https://opengameart.org/sites/default/files/NotThatEast.ogg", "https://opengameart.org/content/sci-fi-adventure-eastern-quiet-piano-loop", focusFit = "light", instruments = listOf("东方器乐")),
        ogaTrack("exploration", "Exploration", "tcarisland", "https://opengameart.org/sites/default/files/exploration_0.mp3", "https://opengameart.org/content/exploration", licenseName = "CC BY 4.0", attribution = "Exploration by tcarisland, CC BY 4.0", instruments = listOf("合成器", "管弦"), focusFit = "deep"),
        ogaTrack("tiny-movement", "Tiny Movement", "Scott Clarke", "https://opengameart.org/sites/default/files/tiny_movement.mp3", "https://opengameart.org/content/tiny-movement", licenseName = "CC BY 4.0", attribution = "Tiny Movement © Scott Clarke, CC BY 4.0", instruments = listOf("钢琴"), focusFit = "deep"),
        ogaTrack("electronic-piano", "Electronic Piano", "burabotti", "https://opengameart.org/sites/default/files/electronic.ogg", "https://opengameart.org/content/electronic-piano", licenseName = "CC BY 3.0", attribution = "Music by Burabotti, CC BY 3.0", instruments = listOf("电钢", "电子"), focusFit = "light"),
        ogaTrack("chilled-lofi", "Chilled Lo-fi Beat", "Bogart VGM", "https://opengameart.org/sites/default/files/lofi_chill_beat_0.mp3", "https://opengameart.org/content/chilled-lofi-beat", licenseName = "CC BY 3.0", attribution = "Music by Bogart VGM, CC BY 3.0", instruments = listOf("Lo-fi"), focusFit = "deep"),
        ogaTrack("calm-bgm", "Calm BGM", "syncopika", "https://opengameart.org/sites/default/files/041415calmbgm_0.ogg", "https://opengameart.org/content/calm-bgm", licenseName = "CC BY 3.0", attribution = "Music by syncopika, CC BY 3.0", instruments = listOf("钢琴", "吉他"), focusFit = "deep"),
    )

    private val contemporaryOpen = listOf(
        ogaTrack("yoiyami-deep-blue", "Deep Blue Ambient Piano", "Yoiyami", "https://opengameart.org/sites/default/files/yoiyami_core_theme_0.wav", "https://opengameart.org/content/yoiyami-core-theme-%E2%80%93-deep-blue-ambient-piano", focusFit = "deep", instruments = listOf("钢琴", "氛围", "尺八音色")),
        ogaTrack("yoiyami-first-light", "First Light Particles", "Yoiyami", "https://opengameart.org/sites/default/files/first_light_particles_0.wav", "https://opengameart.org/node/182244", focusFit = "deep", instruments = listOf("钢琴", "氛围 Pad")),
        ogaTrack("calm-fireplace-guitar", "Calm Fireplace Guitar", "ShggothSlave", "https://opengameart.org/sites/default/files/calm_fireplace_guitar_song_0.mp3", "https://opengameart.org/content/calm-fireplace-guitar-song", focusFit = "light", instruments = listOf("原声吉他")),
        ogaTrack("small-fire-guitar-loop", "A Small Fire Will Do", "Cal McEachern", "https://opengameart.org/sites/default/files/a_small_fire_will_do.wav", "https://opengameart.org/content/a-small-fire-will-do-calming-loop", focusFit = "light", instruments = listOf("原声吉他")),
        ogaTrack("ambient-guitar-dust", "Dust", "Tri-Tachyon", "https://opengameart.org/sites/default/files/Dust_1.mp3", "https://opengameart.org/content/soundscape-dust-ambient-guitar", licenseName = "CC BY 4.0", attribution = "Music by Tri-Tachyon - https://soundcloud.com/tri-tachyon/albums，CC BY 4.0", instruments = listOf("环境吉他", "氛围"), focusFit = "deep"),
        ogaTrack("joth-contemplation", "Contemplation", "Joth", "https://opengameart.org/sites/default/files/Contemplation.mp3", "https://opengameart.org/content/contemplation-0", focusFit = "deep", instruments = listOf("氛围")),
        ogaTrack("indieteur-revelation", "Revelation", "Indieteur", "https://opengameart.org/sites/default/files/Revelation_0.mp3", "https://opengameart.org/content/revelation", focusFit = "deep", instruments = listOf("原声器乐", "氛围")),
        ogaTrack("yd-searching", "Searching", "yd", "https://opengameart.org/sites/default/files/Searching.ogg", "https://opengameart.org/content/searching", focusFit = "deep", instruments = listOf("暖色 Drone", "电子氛围")),
        ogaTrack("end-of-hope", "At the End of Hope", "Macro", "https://opengameart.org/sites/default/files/at%20the%20end%20of%20hope.mp3", "https://opengameart.org/content/at-the-end-of-hope", focusFit = "sleep", instruments = listOf("钢琴", "氛围")),
        ogaTrack("background-music-one", "Background Music 1", "Tozan", "https://opengameart.org/sites/default/files/bgmusic1.ogg", "https://opengameart.org/content/background-music-1", focusFit = "light", instruments = listOf("钢琴", "合成器")),
        ogaTrack("solo-piano-four", "Solo Piano 4", "Joth", "https://opengameart.org/sites/default/files/solopiano4.ogg", "https://opengameart.org/content/solo-piano-4", focusFit = "deep", instruments = listOf("钢琴")),
        incompetechTrack("meditation-impromptu-one", "Meditation Impromptu 01", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Meditation%20Impromptu%2001.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100163", "3:32", "Meditation Impromptu 01 by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("钢琴")),
        incompetechTrack("meditation-impromptu-two", "Meditation Impromptu 02", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Meditation%20Impromptu%2002.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100162", "4:09", "Meditation Impromptu 02 by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("钢琴")),
        incompetechTrack("meditation-impromptu-three", "Meditation Impromptu 03", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Meditation%20Impromptu%2003.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100161", "4:15", "Meditation Impromptu 03 by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("钢琴")),
        incompetechTrack("dream-culture", "Dream Culture", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Dream%20Culture.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1300046", "3:34", "Dream Culture by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("钢琴", "节拍"), "light"),
        incompetechTrack("incompetech-starry", "Starry", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Starry.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100062", "3:18", "Starry by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("钢琴")),
        incompetechTrack("white-lotus", "White Lotus", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/White%20Lotus.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1300044", "34:32", "White Lotus by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("合成器", "钢琴", "金属音色")),
        incompetechTrack("atlantean-twilight", "Atlantean Twilight", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Atlantean%20Twilight.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100322", "2:51", "Atlantean Twilight by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("钢琴", "氛围"), "light"),
        incompetechTrack("just-as-soon", "Just As Soon", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Just%20As%20Soon.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100185", "3:48", "Just As Soon by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("吉他", "爵士四重奏"), "listen"),
        incompetechTrack("night-docks-trumpet", "Night on the Docks", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Night%20on%20the%20Docks%20-%20Trumpet.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100136", "2:54", "Night on the Docks - Trumpet by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("小号", "电钢"), "listen"),
        incompetechTrack("kumasi-groove", "Kumasi Groove", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Kumasi%20Groove.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100183", "3:42", "Kumasi Groove by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("马林巴", "打击乐"), "light"),
    )

    private val replacementCc0 = listOf(
        ogaTrack("cc0-etirwer", "Etirwer", "Kistol", "https://opengameart.org/sites/default/files/Etirwer%20%28Looped%29_0.ogg", "https://opengameart.org/content/etirwer", "2:10", attribution = "Music by Kistol（署名非强制）", instruments = listOf("尼龙弦吉他"), era = "开放录音", focusFit = "deep"),
        ogaTrack("cc0-serenade-guitar", "Serenade Guitar", "Alex McCulloch", "https://opengameart.org/sites/default/files/Serenade.wav", "https://opengameart.org/content/serenade-guitar", "2:09", attribution = "Music by Alex McCulloch（署名非强制）", instruments = listOf("爵士吉他"), era = "开放录音", focusFit = "light"),
        ogaTrack("cc0-frets", "Frets", "Alex McCulloch", "https://opengameart.org/sites/default/files/Frets.mp3", "https://opengameart.org/content/frets", "3:46", attribution = "Music by Alex McCulloch（署名非强制）", instruments = listOf("爵士吉他"), era = "开放录音", focusFit = "light"),
        ogaTrack("cc0-middle-nowhere-remix", "In the Middle of Nowhere Remix", "Reemax / Centurion_of_war", "https://opengameart.org/sites/default/files/in_the_middle_of_nowhere_remix_0.ogg", "https://opengameart.org/content/in-the-middle-of-nowhere-remix", "2:52", attribution = "Original by Centurion_of_war; remix by Reemax（署名非强制）", instruments = listOf("吉他", "氛围"), era = "开放录音", focusFit = "deep"),
        ogaTrack("cc0-sunset-plains", "Sunset Plains", "Yoiyami", "https://opengameart.org/sites/default/files/sunset_plains.wav", "https://opengameart.org/content/sunset-plains", "5:30", instruments = listOf("原声吉他", "氛围 Pad"), era = "开放录音", focusFit = "deep"),
        ogaTrack("cc0-jazzy-blues", "Jazzy Blues", "LushoGames", "https://opengameart.org/sites/default/files/blues_0.mp3", "https://opengameart.org/content/jazzy-blues", "0:53", instruments = listOf("爵士", "蓝调", "轻放克"), era = "开放录音", focusFit = "light"),
        ogaTrack("cc0-blue-intermission", "Blue Intermission", "congusbongus", "https://opengameart.org/sites/default/files/blue_intermission_1.ogg", "https://opengameart.org/content/blue-intermission", "3:57", instruments = listOf("Rhodes 电钢", "爵士蓝调"), era = "开放录音", focusFit = "deep"),
        ogaTrack("cc0-fusion-jazz", "(Basically not) Fusion Jazz", "Spring Spring", "https://opengameart.org/sites/default/files/fusion%20jazz_0.ogg", "https://opengameart.org/content/fusion-jazz", "4:06", attribution = "Music by Spring Spring（署名非强制）", instruments = listOf("电钢", "贝斯", "轻 Swing"), era = "开放录音", focusFit = "light"),
        ogaTrack("cc0-one-step", "One Step at a Time", "Alex McCulloch", "https://opengameart.org/sites/default/files/OneStepAtATIme.wav", "https://opengameart.org/content/one-step-at-a-time", "2:35", attribution = "Music by Alex McCulloch（署名非强制）", instruments = listOf("钢弦吉他", "爵士电声"), era = "开放录音", focusFit = "light"),
        ogaTrack("cc0-catchy-swing", "Catchy Swing", "Doge", "https://opengameart.org/sites/default/files/catchyswing.ogg", "https://opengameart.org/content/catchy-swing", "1:00", instruments = listOf("萨克斯", "贝斯", "鼓", "Swing"), era = "开放录音", focusFit = "light"),
    )

    private val quietOpenLoops = listOf(
        ogaTrack("ambient-sunset-walk", "Sunset Walk", "KiluaBoy", "https://opengameart.org/sites/default/files/SunsetWalk.ogg", "https://opengameart.org/content/sunset-walk-ambient-quiet-sweet-loop", focusFit = "light", instruments = listOf("氛围", "键盘"), era = "开放录音"),
        ogaTrack("ambient-relax-background-one", "Quiet Background", "joaquinton", "https://opengameart.org/sites/default/files/relax_background1_0.ogg", "https://opengameart.org/content/relaxbackground1", focusFit = "deep", instruments = listOf("氛围", "循环乐段"), era = "开放录音"),
    )

    private val ambientPads = listOf("i", "ii", "iv", "v", "vi", "vii", "viii", "ix", "x").mapIndexed { index, suffix ->
        ogaTrack(
            id = "ambient-pad-$suffix", title = "Ambient Pad ${suffix.uppercase()}", artist = "Gregor Quendel",
            url = "https://opengameart.org/sites/default/files/gregor_quendel_-_ambient_pad_-_$suffix.mp3",
            sourcePage = "https://opengameart.org/content/ambient-vol-1",
            licenseName = "CC BY 4.0", attribution = "Ambient Pad ${suffix.uppercase()} by Gregor Quendel, CC BY 4.0",
            instruments = listOf("氛围 Pad"), focusFit = if (index == 3 || index == 7) "sleep" else "deep",
        )
    }

    private val lyricalTracks = listOf(
        commonsTrack("lyrics-auld-lang-syne", "Auld Lang Syne", "Frank C. Stanley", "Auld Lang Syne.ogg", "2:22", focusFit = "listen", instruments = listOf("男声", "旧唱片"), era = "近代历史录音"),
        commonsTrack("lyrics-old-folks", "Old Folks at Home", "Ernestine Schumann-Heink", "Foster - Schumann-Heink - Old Folks at Home (rec. 1918).ogg", "4:02", focusFit = "listen", instruments = listOf("女中音", "旧唱片"), era = "近代历史录音"),
        commonsTrack("lyrics-perfect-day", "A Perfect Day", "The McKee Trio", "APerfectDay1915.ogg", "2:46", focusFit = "listen", instruments = listOf("声乐三重唱", "旧唱片"), era = "近代历史录音"),
        commonsTrack("lyrics-frankie-johnny", "Frankie and Johnny", "Gene Autry", "FrankieandJohnny.ogg", "1:46", focusFit = "listen", instruments = listOf("男声", "民谣"), era = "传统民歌"),
        commonsTrack("lyrics-kentucky-home", "My Old Kentucky Home", "Grant Raymond Barrett", "11 Old Kentucky Home.ogg", "1:50", "CC BY 3.0", "My Old Kentucky Home, arranged and recorded by Grant Raymond Barrett, CC BY 3.0", listOf("男声", "民谣"), "现代自由录音", "listen"),
        commonsTrack("lyrics-old-black-joe", "Old Black Joe", "Alma Gluck 与男声四重唱", "Victor-74442-c16082.ogg", "3:38", focusFit = "listen", instruments = listOf("女高音", "合唱", "旧唱片"), era = "近代历史录音"),
    )

    private fun byId(list: List<RadioTrack>) = list.associateBy { it.id }
    private val classicalById = byId(classical)
    private val expandedById = byId(classicalExpanded)
    private val calmById = byId(calmClassics)
    private val verifiedById = byId(verifiedInstrumental)
    private val openById = byId(open)
    private val contemporaryById = byId(contemporaryOpen)
    private val replacementCc0ById = byId(replacementCc0)
    private val easternById = byId(easternTraditional)
    private val quietById = byId(quietOpenLoops)

    // radioCatalog.ts 的新增开放精选专题。曲目仍走同一许可证、来源页与本地包键路径，
    // 因此不会把外链当作生产播放地址。
    private val webKeyboardCounterpoint = buildList {
        add(commonsTrack("bach-goldberg-aria-cc0", "哥德堡变奏曲：咏叹调", "J. S. Bach / Bradley Lehman 与 Dave Grossman", "988-aria.lehman1.ogg", licenseName = "Public Domain", instruments = listOf("羽管键琴"), era = "巴洛克", focusFit = "deep"))
        (1..11).plus(listOf(12, 13, 14, 15, 16, 27, 28, 29, 30)).distinct().forEach { number ->
            val padded = number.toString().padStart(2, '0')
            add(commonsTrack("bach-goldberg-cc0-$padded", "哥德堡变奏曲：第${number}变奏", "J. S. Bach / Bradley Lehman 与 Dave Grossman", "988-v$padded.lehman1.ogg", licenseName = "CC0 1.0", instruments = listOf("羽管键琴"), era = "巴洛克", focusFit = if (number % 3 == 0) "light" else "deep"))
        }
    }
    private val webClassicalArchitecture = listOf(
        Triple("05-07", "05-06-07", "5–7"), Triple("08-10", "08-09-10", "8–10"), Triple("11-13", "11-12-13", "11–13"), Triple("14", "14", "14"), Triple("15-17", "15-16-17", "15–17"), Triple("18-19", "18-19", "18–19"), Triple("20-23", "20-21-22-23", "20–23"), Triple("24", "24", "24"), Triple("25-29", "25-26-27-28-29", "25–29"), Triple("30", "30", "30"), Triple("31", "31", "31"), Triple("32", "32", "32"), Triple("33", "33", "33")
    ).mapIndexed { index, (idPart, filePart, titlePart) ->
        commonsTrack("beethoven-diabelli-$idPart", "迪亚贝利变奏曲 $titlePart", "L. van Beethoven / Neal O'Doan", "Beethoven - Diabelli Variations - $filePart.ogg", licenseName = "CC BY-SA 2.0", instruments = listOf("钢琴"), era = "古典主义", focusFit = if (index == 8 || index == 11) "listen" else "deep")
    } + listOf(
        commonsTrack("beethoven-nine-one", "第九交响曲 I", "L. van Beethoven / Jascha Horenstein", "01 Horenstein 9. Beethoven Pantheon XP 2250 - 1. Satz.flac", licenseName = "Public Domain", instruments = listOf("管弦乐"), era = "古典主义", focusFit = "listen"),
        commonsTrack("beethoven-nine-two", "第九交响曲 II", "L. van Beethoven / Jascha Horenstein", "02 Horenstein 9. Beethoven Pantheon XP 2250 - 2. Satz.flac", licenseName = "Public Domain", instruments = listOf("管弦乐"), era = "古典主义", focusFit = "listen"),
        commonsTrack("beethoven-nine-three", "第九交响曲 III：柔板", "L. van Beethoven / Jascha Horenstein", "03 Horenstein 9. Beethoven Pantheon XP 2250 - 3. Satz.flac", licenseName = "Public Domain", instruments = listOf("管弦乐"), era = "古典主义", focusFit = "deep"),
        commonsTrack("beethoven-nine-four", "第九交响曲 IV", "L. van Beethoven / Jascha Horenstein", "04 Horenstein 9. Beethoven Pantheon XP 2550 - 4. satz.flac", licenseName = "Public Domain", instruments = listOf("管弦乐", "合唱"), era = "古典主义", focusFit = "listen"),
    )
    private val webRomanticPianoDiary = listOf(
        "january" to "一月：炉边", "february" to "二月：狂欢节", "march" to "三月：云雀之歌", "april" to "四月：松雪草", "may" to "五月：白夜", "june" to "六月：船歌", "july" to "七月：割草人之歌", "august" to "八月：收获", "september" to "九月：狩猎", "october" to "十月：秋之歌", "november" to "十一月：雪橇", "december" to "十二月：圣诞"
    ).mapIndexed { index, (month, title) ->
        commonsTrack("tchaikovsky-seasons-$month", "四季·$title", "Pyotr Ilyich Tchaikovsky / Bernd Krueger", "Tchaikovsky the Seasons ${month.replaceFirstChar { it.uppercase() }}.ogg", licenseName = "CC BY-SA 2.0 DE", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = if (index in setOf(1, 7, 8, 11)) "light" else "deep")
    } + listOf(
        commonsTrack("schumann-kinderszenen-one", "童年情景 I：异国情景", "Robert Schumann / Musopen recording", "Robert Schumann - scenes from childhood, op. 15 - i. of foreign lands and peoples.ogg", licenseName = "Public Domain", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("schumann-kinderszenen-seven", "童年情景 VII：梦幻曲", "Robert Schumann / Musopen recording", "Robert Schumann - scenes from childhood, op. 15 - vii. dreaming.ogg", licenseName = "Public Domain", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("schumann-kinderszenen-ten", "童年情景 X：几乎太认真", "Robert Schumann / Musopen recording", "Robert Schumann - scenes from childhood, op. 15 - x. almost too serious.ogg", licenseName = "Public Domain", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("brahms-op118-two", "间奏曲 Op.118 No.2", "Johannes Brahms / Carlos Gardels", "Johannes Brahms - klavierstucke, op. 118 - ii. intermezzo.ogg", licenseName = "Public Domain", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("brahms-op117-two", "间奏曲 Op.117 No.2", "Johannes Brahms / La Pianista", "Brahms - Intermezzo, Op. 117, No. 2.ogg", licenseName = "CC BY-SA 3.0", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("brahms-op116-four", "间奏曲 Op.116 No.4", "Johannes Brahms / Paul Cantrell", "Brahms Intermezzo 116-4.ogg", licenseName = "CC BY 3.0", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("schubert-impromptu-d935-three", "降B大调即兴曲 D.935 No.3", "Franz Schubert / Randolph Hokanson", "Schubert- Impromptu B-flat.ogg", licenseName = "CC BY-SA 2.0", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("faure-berceuse-op56", "摇篮曲 Op.56 No.1", "Gabriel Fauré / Brian M. Jones", "Berceuse by Gabriel Fauré op56 no1.ogg", licenseName = "CC BY 3.0", instruments = listOf("钢琴"), era = "法国浪漫主义", focusFit = "deep"),
    )

    /** radioCatalog.ts expandedInstruments / expandedModernOpen: source, credit and local-pack key stay per track. */
    private val webInstrumentExpansion = listOf(
        commonsTrack("organ-pachelbel-fugue-13", "第八调式圣母颂赋格 No.13", "Johann Pachelbel / historical recording", "Johann Pachelbel - Magnificat Octavi Toni Fugue 13.ogg", licenseName = "Public Domain", instruments = listOf("管风琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("organ-pachelbel-toccata-f", "F大调托卡塔", "Johann Pachelbel / Burghard Fischer", "Johann Pachelbel Toccata F-Dur.ogg", licenseName = "CC BY-SA 3.0", instruments = listOf("管风琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("organ-pachelbel-toccata-e", "E小调托卡塔", "Johann Pachelbel / Burghard Fischer", "Johann Pachelbel Toccata e-Moll.ogg", licenseName = "CC BY-SA 3.0", instruments = listOf("管风琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("organ-bach-543-prelude", "A小调前奏曲 BWV 543", "J. S. Bach / Robert Köbler", "Johann Sebastian Bach Prelude in A minor BWV 543 Robert Köbler Silbermann-Organ.mp3", licenseName = "CC BY 1.0", instruments = listOf("管风琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("organ-bach-543-fugue", "A小调赋格 BWV 543", "J. S. Bach / Robert Köbler", "Johann Sebastian Bach Fugue in A minor BWV 543 Robert Köbler Silbermann-Organ.mp3", licenseName = "CC BY 1.0", instruments = listOf("管风琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("organ-bach-529-one", "C大调管风琴奏鸣曲 BWV 529 I", "J. S. Bach / Hans Otto", "Johann Sebastian Bach first movement Organ Sonata in C major BWV 529 Hans Otto Silbermann-Organ.mp3", licenseName = "CC BY 1.0", instruments = listOf("管风琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("organ-bach-526-one", "C小调管风琴奏鸣曲 BWV 526 I", "J. S. Bach / Hans Otto", "Johann Sebastian Bach first movement Organ Sonata in C minor BWV 526 Hans Otto Silbermann-Organ.mp3", licenseName = "CC BY 1.0", instruments = listOf("管风琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("cello-vivaldi-allegro-one", "G大调大提琴协奏曲 I", "Vivaldi / Stephen Balderston 与 Advent Chamber Orchestra", "Vivaldi - Cello Concerto Gmaj - 1. Allegro.ogg", licenseName = "CC BY-SA 2.0", instruments = listOf("大提琴", "室内乐"), era = "巴洛克", focusFit = "light"),
        commonsTrack("cello-vivaldi-allegro-three", "G大调大提琴协奏曲 III", "Vivaldi / Stephen Balderston 与 Advent Chamber Orchestra", "Vivaldi - Cello Concerto Gmaj - 3. Allegro.ogg", licenseName = "CC BY-SA 2.0", instruments = listOf("大提琴", "室内乐"), era = "巴洛克", focusFit = "light"),
        commonsTrack("cello-debussy-beau-soir", "美丽的夜：大提琴版", "Claude Debussy / John Michel", "CELLO LIVE PERFORMANCES JOHN MICHEL-DEBUSSY BEAU SOIR.ogg", licenseName = "CC BY-SA 3.0", instruments = listOf("大提琴", "钢琴"), era = "印象主义", focusFit = "deep"),
        commonsTrack("cello-bach-gigue", "第一无伴奏大提琴组曲：吉格", "J. S. Bach / Pablo Casals", "Bach - Cello Suite no. 1 in G major, BWV 1007 - VI. Gigue.ogg", licenseName = "Public Domain", instruments = listOf("大提琴"), era = "巴洛克", focusFit = "light"),
        commonsTrack("cello-bach-allemande-john-michel", "第一无伴奏大提琴组曲：阿勒曼德", "J. S. Bach / John Michel", "JOHN MICHEL CELLO-J S BACH CELLO SUITE 1 in G Allemande.ogg", licenseName = "CC BY-SA 3.0", instruments = listOf("大提琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("cello-faure-elegie", "悲歌 Op.24", "Gabriel Fauré / Hans Goldstein、Eli Kalman", "Faure - Elegie.ogg", licenseName = "CC BY-SA 2.0", instruments = listOf("大提琴", "钢琴"), era = "法国浪漫主义", focusFit = "listen"),
        commonsTrack("cello-saint-saens-swan", "天鹅", "Camille Saint-Saëns / Alisa Weilerstein、Jason Yoder", "20091104 Alisa Weilerstein and Jason Yoder - Saint Saëns' The Swan.ogg", licenseName = "CC BY 3.0", attribution = "演奏：Alisa Weilerstein、Jason Yoder；白宫古典音乐之夜录音；表演 CC BY 3.0", instruments = listOf("大提琴", "马林巴"), era = "法国浪漫主义", focusFit = "deep"),
        commonsTrack("ambient-india-zeropage", "Ambient India", "Zeropage", "Ambient India by Zeropage.ogg", licenseName = "CC BY 3.0", instruments = listOf("印度器乐", "电子氛围"), era = "现代开放录音", focusFit = "light"),
        commonsTrack("ambient-dance-zeropage", "Ambient Dance", "Zeropage", "Ambient Dance by Zeropage.ogg", licenseName = "CC BY 3.0", instruments = listOf("跨界器乐", "电子氛围"), era = "现代开放录音", focusFit = "light"),
    )
    private val webModernExpansion = listOf(
        commonsTrack("jazz-avant", "Avant Jazz", "Kevin MacLeod", "Avant Jazz (ISRC USUAN1100319).mp3", licenseName = "CC BY 3.0", instruments = listOf("爵士小编制"), era = "现代开放录音", focusFit = "listen"),
        commonsTrack("jazz-brunch", "Jazz Brunch", "Kevin MacLeod", "Jazz Brunch (ISRC USUAN1700074).mp3", licenseName = "CC BY 3.0", instruments = listOf("爵士小编制"), era = "现代开放录音", focusFit = "light"),
        commonsTrack("jazz-samba", "Modern Jazz Samba", "Kevin MacLeod", "Modern Jazz Samba (ISRC USUAN1100153).mp3", licenseName = "CC BY 3.0", instruments = listOf("爵士", "拉丁节奏"), era = "现代开放录音", focusFit = "light"),
        commonsTrack("jazz-memphis-blues", "Memphis Blues", "Ted Lewis Jazz Band", "Memphis Blues-Columbia A3813-Ted Lewis Jazz Band.mp3", licenseName = "Public Domain", instruments = listOf("历史爵士"), era = "1920年代", focusFit = "listen"),
        commonsTrack("jazz-dont-care-blues", "Don't Care Blues", "Mamie Smith and Her Jazz Hounds", "Don't Care Blues (10 inch) - Mamie Smith and Her Jazz Hounds.ogg", licenseName = "Public Domain", instruments = listOf("历史爵士", "人声"), era = "1920年代", focusFit = "listen"),
        commonsTrack("lofi-caden-currie", "Lofi", "Caden Currie", "Lofi by Caden Currie.mp3", licenseName = "CC BY 3.0", instruments = listOf("Lo-fi", "节拍"), era = "现代开放录音", focusFit = "deep"),
        commonsTrack("lofi-music-001", "Lofi Music 001", "Commons contributor", "Lofi music 001.wav", licenseName = "CC0 1.0", instruments = listOf("Lo-fi", "节拍"), era = "现代开放录音", focusFit = "deep"),
        commonsTrack("lofi-upbeat-raspberry", "Lofi Hip Hop Upbeat", "Raspberrymusic", "Raspberrymusic - Lofi Hip Hop Upbeat.ogg", licenseName = "CC BY 4.0", instruments = listOf("Lo-fi", "Hip-hop"), era = "现代开放录音", focusFit = "light"),
        commonsTrack("lofi-perspective", "Perspective", "Sappheiros", "Sappheiros - Perspective (Lofi Hip Hop).ogg", licenseName = "CC BY 3.0", instruments = listOf("Lo-fi", "Hip-hop"), era = "现代开放录音", focusFit = "deep"),
        commonsTrack("lofi-de", "De-LoFi", "De / Commons contributor", "De-LoFi.ogg", licenseName = "CC BY-SA 4.0", instruments = listOf("Lo-fi", "电子"), era = "现代开放录音", focusFit = "deep"),
        incompetechTrack("beats-funkorama", "Funkorama", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Funkorama.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100474", "", "Funkorama by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("放克", "节拍"), "light"),
        incompetechTrack("beats-groove-grove", "Groove Grove", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Groove%20Grove.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1200054", "", "Groove Grove by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("电子", "节拍"), "light"),
        incompetechTrack("beats-electro-cabello", "Electro Cabello", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Electro%20Cabello.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1400048", "", "Electro Cabello by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("电子", "节拍"), "light"),
        incompetechTrack("beats-faster-does-it", "Faster Does It", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Faster%20Does%20It.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100794", "", "Faster Does It by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("爵士鼓", "节拍"), "light"),
        incompetechTrack("beats-disco-lounge", "Disco Lounge", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Disco%20Lounge.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100602", "", "Disco Lounge by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("电子", "放克"), "light"),
        commonsTrack("ambient-voyager", "Ambient Voyager", "Zeropage", "Ambient Voyager by Zeropage.ogg", licenseName = "CC BY 3.0", instruments = listOf("电子氛围"), era = "现代开放录音", focusFit = "deep"),
        commonsTrack("ambient-zero-point", "Zero Point", "Dreamstate Logic", "Dreamstate Logic - Zero Point (space ambient, dark ambient).ogg", licenseName = "CC BY 3.0", instruments = listOf("深空氛围", "Drone"), era = "现代开放录音", focusFit = "sleep"),
        commonsTrack("ambient-brenticus", "Ambient", "Brenticus", "Brenticus - Ambient.ogg", licenseName = "CC BY 3.0", instruments = listOf("电子氛围"), era = "现代开放录音", focusFit = "deep"),
        incompetechTrack("modern-piano-at-rest", "At Rest", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/At%20Rest.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100748", "", "At Rest by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("钢琴")),
        incompetechTrack("modern-piano-almost-f", "Almost in F", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Almost%20in%20F.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100394", "32:42", "Almost in F by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("钢琴"), "deep"),
        incompetechTrack("modern-piano-clean-soul", "Clean Soul", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Clean%20Soul.mp3", "https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1300033", "", "Clean Soul by Kevin MacLeod (incompetech.com), CC BY 4.0", listOf("钢琴")),
        commonsTrack("choral-vivaldi-misericordia", "Et Misericordia", "Antonio Vivaldi / Commons choir", "03 - Et Misericordia.ogg", licenseName = "CC BY-SA 3.0", instruments = listOf("合唱", "室内乐"), era = "巴洛克", focusFit = "listen"),
        commonsTrack("choral-vivaldi-esurientes", "Esurientes", "Antonio Vivaldi / Commons choir", "06 - Esurientes.ogg", licenseName = "CC BY-SA 3.0", instruments = listOf("合唱", "室内乐"), era = "巴洛克", focusFit = "listen"),
        commonsTrack("choral-vivaldi-gloria", "Gloria", "Antonio Vivaldi / Commons choir", "09 - Gloria.ogg", licenseName = "CC BY-SA 3.0", instruments = listOf("合唱", "室内乐"), era = "巴洛克", focusFit = "listen"),
        commonsTrack("lyrics-mozart-luise", "Als Luise die Briefe ihres ungetreuen Liebhabers verbrannte", "W. A. Mozart / historical recording", "Als Luise (Mozart).ogg", licenseName = "Public Domain", instruments = listOf("独唱", "钢琴"), era = "古典主义", focusFit = "listen"),
        commonsTrack("lyrics-mozart-violet", "紫罗兰", "W. A. Mozart / Elen Ap Robert", "Das Veilchen - Elen Ap Robert.ogg", licenseName = "CC BY-SA 3.0", instruments = listOf("独唱", "钢琴"), era = "古典主义", focusFit = "listen"),
        commonsTrack("lyrics-debussy-ariettes-two", "被遗忘的咏叹调 II", "Claude Debussy / Giorgi Latsabidze", "Giorgi Latsabidze Ariettes Oubliées2.ogg", licenseName = "Public Domain", instruments = listOf("独唱", "钢琴"), era = "印象主义", focusFit = "listen"),
        commonsTrack("lyrics-debussy-ariettes-four", "被遗忘的咏叹调 IV", "Claude Debussy / Giorgi Latsabidze", "Giorgi Latsabidze Ariettes Oubliées 4.ogg", licenseName = "Public Domain", instruments = listOf("独唱", "钢琴"), era = "印象主义", focusFit = "listen"),
    )

    /** radioCatalog.ts expandedOpenClassics：巴洛克、古典、室内乐、浪漫、印象、夜曲与变奏（6a1cb2a 曲目增加）。 */
    private val webOpenClassics = listOf(
        commonsTrack("bach-passacaglia-bwv582", "C小调帕萨卡利亚与赋格 BWV 582", "J. S. Bach / Awadagin Pratt", "20091104 Awadagin Pratt - Bach's Passacaglia and Fugue in C minor, BWV 582.ogg", licenseName = "Public Domain", instruments = listOf("管风琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("bach-goldberg-var8", "哥德堡变奏曲：第八变奏", "J. S. Bach / Commons recording", "11 Goldberg Variation 8.ogg", licenseName = "CC BY-SA 3.0", instruments = listOf("钢琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("vivaldi-winter-largo", "冬 II：广板", "Antonio Vivaldi / John Harrison", "11 - Vivaldi Winter mvt 2 Largo - John Harrison violin.ogg", licenseName = "CC BY-SA 4.0", instruments = listOf("小提琴", "室内乐"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("vivaldi-winter-allegro", "冬 III：快板", "Antonio Vivaldi / John Harrison", "12 - Vivaldi Winter mvt 3 Allegro - John Harrison violin.ogg", licenseName = "CC BY-SA 4.0", instruments = listOf("小提琴", "室内乐"), era = "巴洛克", focusFit = "light"),
        commonsTrack("vivaldi-recorder-two", "竖笛协奏曲 II", "Antonio Vivaldi / 演奏者未注明", "Antonio Vivaldi - concerto for recorder - 2.ogg", licenseName = "CC BY-SA 3.0", attribution = "演奏者未注明；上传者自有录音；CC BY-SA 3.0", instruments = listOf("竖笛", "室内乐"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("vivaldi-recorder-three", "竖笛协奏曲 III", "Antonio Vivaldi / 演奏者未注明", "Antonio Vivaldi - concerto for recorder - 3.ogg", licenseName = "CC BY-SA 3.0", attribution = "演奏者未注明；上传者自有录音；CC BY-SA 3.0", instruments = listOf("竖笛", "室内乐"), era = "巴洛克", focusFit = "light"),
        commonsTrack("vivaldi-notte-three", "夜之协奏曲 III", "Antonio Vivaldi / 演奏者未注明", "Antonio Vivaldi - La Notte - 3.ogg", licenseName = "CC BY-SA 3.0", attribution = "演奏者未注明；上传者自有录音；CC BY-SA 3.0", instruments = listOf("长笛", "室内乐"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("scarlatti-k159", "C大调奏鸣曲 K.159", "Domenico Scarlatti / Veronica van der Knaap", "D-Scarlatti-Sonata-K159-C.ogg", licenseName = "Public Domain", instruments = listOf("羽管键琴"), era = "巴洛克", focusFit = "light"),
        commonsTrack("scarlatti-k466", "F小调奏鸣曲 K.466", "Domenico Scarlatti / Membeth", "Domenico.Scarlatti.Sonata.f.minor.Kirkpatrick.466.ogg", licenseName = "CC0 1.0", instruments = listOf("钢琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("corelli-christmas-one", "圣诞大协奏曲：第一组段落", "Arcangelo Corelli / Advent Chamber Orchestra", "Corelli - Concerto Grosso in G minor - Christmas Concerto - part 1.ogg", licenseName = "CC BY-SA 2.0", instruments = listOf("弦乐", "室内乐"), era = "巴洛克", focusFit = "light"),
        commonsTrack("corelli-christmas-two", "圣诞大协奏曲：第二组段落", "Arcangelo Corelli / Advent Chamber Orchestra", "Corelli - Concerto Grosso in G minor - Christmas Concerto - part 2.ogg", licenseName = "CC BY-SA 2.0", instruments = listOf("弦乐", "室内乐"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("corelli-op6-no4-one", "大协奏曲 Op.6 No.4 I", "Arcangelo Corelli / Advent Chamber Orchestra", "Corelli - Concerto Grosso Op. 6 No. 4 - 1. Adagio - Allegro.ogg", licenseName = "CC BY-SA 2.0", instruments = listOf("弦乐", "室内乐"), era = "巴洛克", focusFit = "light"),
        commonsTrack("couperin-barricades", "神秘的屏障", "François Couperin / Commons performer", "François Couperin - Les baricades-mistérieuses.ogg", licenseName = "Public Domain", instruments = listOf("羽管键琴"), era = "法国巴洛克", focusFit = "deep"),
        commonsTrack("rameau-gavotte-doubles", "加沃特与六段变奏", "Jean-Philippe Rameau / Marcelle Meyer", "Rameau- Nouvelles Suites de pièces de clavecin- Suite en la mineur - Gavotte et six doubles.flac", licenseName = "Public Domain", instruments = listOf("羽管键琴"), era = "法国巴洛克", focusFit = "deep"),
        commonsTrack("handel-fitzwilliam-three", "菲茨威廉奏鸣曲第三首", "G. F. Handel / Alex Murray、Martha Goldstein", "Handel - Fitzwilliam Sonata 3.ogg", licenseName = "CC BY-SA 2.0", instruments = listOf("长笛", "羽管键琴"), era = "巴洛克", focusFit = "deep"),
        commonsTrack("purcell-abdelazer-suite", "阿布德拉泽组曲", "Henry Purcell / Commons ensemble", "Purcell Henry - Abdelazer Suite.ogg", licenseName = "CC BY 3.0", instruments = listOf("弦乐", "羽管键琴", "组曲"), era = "英国巴洛克", focusFit = "light"),
        commonsTrack("mozart-k421-one", "D小调弦乐四重奏 K.421 I", "W. A. Mozart / historical recording", "02 Mozart KV 421 1.ogg", licenseName = "Public Domain", instruments = listOf("弦乐四重奏"), era = "古典主义", focusFit = "listen"),
        commonsTrack("mozart-k421-two", "D小调弦乐四重奏 K.421 II", "W. A. Mozart / historical recording", "03 Mozart KV 421 2.flac", licenseName = "Public Domain", instruments = listOf("弦乐四重奏"), era = "古典主义", focusFit = "deep"),
        commonsTrack("mozart-k421-three", "D小调弦乐四重奏 K.421 III", "W. A. Mozart / historical recording", "04 Mozart KV 421 3.flac", licenseName = "Public Domain", instruments = listOf("弦乐四重奏"), era = "古典主义", focusFit = "light"),
        commonsTrack("mozart-k421-four", "D小调弦乐四重奏 K.421 IV", "W. A. Mozart / historical recording", "06 Mozart KV 421 4.ogg", licenseName = "Public Domain", instruments = listOf("弦乐四重奏"), era = "古典主义", focusFit = "listen"),
        commonsTrack("mozart-rondo-k511", "A小调回旋曲 K.511", "W. A. Mozart / David H. Porter", "David H Porter - Mozarts Rondo No 3 in A Minor K 511.ogg", licenseName = "CC0 1.0", instruments = listOf("钢琴"), era = "古典主义", focusFit = "deep"),
        commonsTrack("mozart-divertimento-113-two", "降E大调嬉游曲 K.113 II", "W. A. Mozart / Commons ensemble", "Divertimento in E-flat major - KV 113 - 2nd movement.oga", licenseName = "CC BY 3.0", instruments = listOf("室内乐"), era = "古典主义", focusFit = "deep"),
        commonsTrack("mozart-divertimento-113-three", "降E大调嬉游曲 K.113 III", "W. A. Mozart / Commons ensemble", "Divertimento in E-flat major - KV 113 - 3rd movement.oga", licenseName = "CC BY 3.0", instruments = listOf("室内乐"), era = "古典主义", focusFit = "light"),
        commonsTrack("mozart-k421-andante-musopen", "D小调弦乐四重奏 K.421 II：行板", "W. A. Mozart / Musopen String Quartet", "Mozart - String Quartet No. 15 in D minor, K421 - II. Andante (Musopen String Quartet).flac", licenseName = "Public Domain", instruments = listOf("弦乐四重奏"), era = "古典主义", focusFit = "deep"),
        commonsTrack("mozart-k465-adagio-allegro", "不协和音四重奏 K.465 I", "W. A. Mozart / Musopen String Quartet", "Mozart - String Quartet No. 19 in C major, K465 'Dissonance' - I. Adagio - Allegro (Musopen String Quartet).flac", licenseName = "Public Domain", instruments = listOf("弦乐四重奏"), era = "古典主义", focusFit = "listen"),
        commonsTrack("haydn-lark-one", "云雀四重奏 I：中庸的快板", "Joseph Haydn / Musopen String Quartet", "Haydn StringQuartetInDMajorOp.64 JosephHaydn-StringQuartetInDOp.645H363Lark-01-AllegroModerato.ogg", licenseName = "CC0 1.0", instruments = listOf("弦乐四重奏"), era = "古典主义", focusFit = "light"),
        commonsTrack("haydn-lark-three", "云雀四重奏 III：小步舞曲", "Joseph Haydn / Musopen String Quartet", "Haydn StringQuartetInDMajorOp.64 JosephHaydn-StringQuartetInDOp.645H363Lark-03-MenuettoAllegretto.ogg", licenseName = "CC0 1.0", instruments = listOf("弦乐四重奏"), era = "古典主义", focusFit = "light"),
        commonsTrack("mendelssohn-scottish-adagio", "苏格兰交响曲 III：柔板", "Felix Mendelssohn / The Musopen Symphony Orchestra", "The Musopen Symphony Orchestra - Mendelssohn's Symphony No. 3 in A minor, Op. 56, MWV N 18 - III. Adagio.ogg", licenseName = "Public Domain", instruments = listOf("管弦乐"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("chopin-allegro-concert", "音乐会快板 Op.46", "Frédéric Chopin / Commons recording", "Allegro de Concert Op. 46 in A Major.mp3", licenseName = "CC0 1.0", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "listen"),
        commonsTrack("chopin-barcarolle-cc0", "船歌 Op.60：开放录音", "Frédéric Chopin / Commons recording", "Barcarolle - Chopin.ogg", licenseName = "CC0 1.0", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("chopin-canon-f-minor", "F小调卡农", "Frédéric Chopin / Commons recording", "Canon in F minor.mp3", licenseName = "CC0 1.0", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("chopin-concerto-quartet-one", "第一钢琴协奏曲：室内乐版 I", "Frédéric Chopin / Commons ensemble", "Chopin - Piano Concerto no. 1, Op. 11 (string quartet)-1.ogg", licenseName = "CC0 1.0", instruments = listOf("钢琴", "弦乐四重奏"), era = "浪漫主义", focusFit = "listen"),
        commonsTrack("chopin-concerto-quartet-two", "第一钢琴协奏曲：室内乐版 II", "Frédéric Chopin / Commons ensemble", "Chopin - Piano Concerto no. 1, Op. 11 (string quartet)-2.ogg", licenseName = "CC0 1.0", instruments = listOf("钢琴", "弦乐四重奏"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("chopin-concerto-quartet-three", "第一钢琴协奏曲：室内乐版 III", "Frédéric Chopin / Commons ensemble", "Chopin - Piano Concerto no. 1, Op. 11 (string quartet)-3.ogg", licenseName = "CC0 1.0", instruments = listOf("钢琴", "弦乐四重奏"), era = "浪漫主义", focusFit = "light"),
        commonsTrack("faure-elegie", "悲歌 Op.24", "Gabriel Fauré / Hans Goldstein、Eli Kalman", "Faure - Elegie.ogg", licenseName = "CC BY-SA 2.0", instruments = listOf("大提琴", "钢琴"), era = "法国浪漫主义", focusFit = "listen"),
        commonsTrack("saint-saens-swan", "天鹅", "Camille Saint-Saëns / Alisa Weilerstein、Jason Yoder", "20091104 Alisa Weilerstein and Jason Yoder - Saint Saëns' The Swan.ogg", licenseName = "CC BY 3.0", instruments = listOf("大提琴", "马林巴"), era = "法国浪漫主义", focusFit = "deep"),
        commonsTrack("debussy-doctor-gradus", "博士练习曲", "Claude Debussy / eldüendesüarez", "Debussy - Doctor Gradys ad Parnassum (Children's Corner) (Piano Performance by eldüendesüarez).wav", licenseName = "CC BY 4.0", instruments = listOf("钢琴"), era = "印象主义", focusFit = "light"),
        commonsTrack("debussy-sketchbook", "素描簿一页", "Claude Debussy / Eunmi Ko", "Claude Debussy - D´un cahier d´esquisses - Eunmi Ko.ogg", licenseName = "CC BY-SA 4.0", instruments = listOf("钢琴"), era = "印象主义", focusFit = "deep"),
        commonsTrack("debussy-arabesque-two", "第二阿拉伯风格曲", "Claude Debussy / Patrizia Prati", "Claude Debussy - Deuxième Arabesque - Patrizia Prati.ogg", licenseName = "CC BY-SA 4.0", instruments = listOf("钢琴"), era = "印象主义", focusFit = "light"),
        commonsTrack("debussy-clair-brass", "月光：铜管改编", "Claude Debussy / U.S. Air Force Band of Flight", "Clair de Lune - Wright Brass - United States Air Force Band of Flight.mp3", licenseName = "Public Domain", instruments = listOf("铜管重奏"), era = "印象主义改编", focusFit = "listen"),
        commonsTrack("chopin-berceuse-pd", "摇篮曲 Op.57", "Frédéric Chopin / historical recording", "Chopin-Berceuse.ogg", licenseName = "Public Domain", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "deep"),
        commonsTrack("ravel-pavane-pd", "悼念公主的帕凡舞曲", "Maurice Ravel / Pracchia-78", "Maurice Ravel - Pavane pour une infante défunte.ogg", licenseName = "Public Domain", instruments = listOf("钢琴"), era = "印象主义", focusFit = "deep"),
        commonsTrack("respighi-intermezzo-serenata", "间奏曲：小夜曲", "Ottorino Respighi / Musopen recording", "6-Pieces-for-Piano-VI.-Intermezzo-Serenata.ogg", licenseName = "Public Domain", instruments = listOf("钢琴"), era = "二十世纪早期", focusFit = "deep"),
        commonsTrack("chopin-fantaisie-op49", "F小调幻想曲 Op.49", "Frédéric Chopin / historical recording", "Chopin Fantaisie in F minor, Op.49.wav", licenseName = "Public Domain", instruments = listOf("钢琴"), era = "浪漫主义", focusFit = "listen"),
        commonsTrack("beethoven-32-variations", "C小调三十二段变奏 WoO 80", "L. van Beethoven / historical recording", "Beethoven - 32 Variations in C Minor, WoO 80.ogg", licenseName = "Public Domain", instruments = listOf("钢琴"), era = "古典主义", focusFit = "listen"),
        commonsTrack("beethoven-diabelli-theme", "迪亚贝利变奏曲：主题", "L. van Beethoven / Neal O'Doan", "Beethoven - Diabelli Variations - 00.ogg", licenseName = "CC BY-SA 2.0", instruments = listOf("钢琴"), era = "古典主义", focusFit = "deep"),
        commonsTrack("beethoven-diabelli-01-02", "迪亚贝利变奏曲 1–2", "L. van Beethoven / Neal O'Doan", "Beethoven - Diabelli Variations - 01-02.ogg", licenseName = "CC BY-SA 2.0", instruments = listOf("钢琴"), era = "古典主义", focusFit = "deep"),
        commonsTrack("beethoven-diabelli-03-04", "迪亚贝利变奏曲 3–4", "L. van Beethoven / Neal O'Doan", "Beethoven - Diabelli Variations - 03-04.ogg", licenseName = "CC BY-SA 2.0", instruments = listOf("钢琴"), era = "古典主义", focusFit = "deep"),
    )
    private fun Map<String, RadioTrack>.ids(vararg ids: String): List<RadioTrack> = ids.map(::getValue)
    private val webInstrumentById = byId(webInstrumentExpansion)
    private val webModernById = byId(webModernExpansion)
    private val openClassicsById = byId(webOpenClassics)
    private val architectureById = byId(webClassicalArchitecture)

    // ── radioCatalog.ts OFFICIAL_RADIO_STATIONS（10 生成 + 25 开放 = 35）──
    val initial = listOf(
        // 生成站台（generated- 前缀，preset 无前缀）
        generated("generated-deep-sea", "deep-sea", "深海电台", "低频潮汐、缓慢泛音与稀疏信号；会随海浪、水声类环境声调整空间感。", "氛围", listOf("沉浸", "助眠")),
        generated("generated-rain-piano", "rain-piano", "雨窗书页", "真实钢琴采样会根据当前雨声密度改变留白、音区和落点。", "琴键生成", listOf("阅读", "写作")),
        generated("generated-morning-mist", "morning-mist", "清晨薄雾", "长笛与竖琴采样保持克制明亮，风声与鸟鸣会带来更长的呼吸。", "轻氛围", listOf("晨间", "轻任务")),
        generated("generated-endless-focus", "endless-focus", "无尽专注", "稳定脉冲与低密度和声维持节奏，不抢占语言注意力。", "专注", listOf("编码", "深度工作")),
        generated("generated-night-train", "night-train", "夜行慢车", "远处钟声与机械律动会对交通类环境声作出节奏响应。", "节拍", listOf("重复任务", "夜间")),
        generated("generated-warm-study", "warm-study", "暖灯自习", "暖色电键与低密度和弦，适合低音量叠加咖啡馆、翻书或键盘声。", "轻节拍", listOf("学习", "阅读")),
        generated("generated-moon-tide", "moon-tide", "月下潮汐", "更慢的呼吸型低音与水波泛音，随当前声场缓慢改变。", "助眠", listOf("放松", "助眠")),
        generated("generated-signal-garden", "signal-garden", "信号花园", "细小电子音粒在安静底层中随机生长，适合短时创意工作。", "电子", listOf("创作", "轻任务")),
        generated("generated-bamboo-strings", "bamboo-strings", "竹影与弓弦", "长笛、大提琴与竖琴采样彼此留白，山风与水声会改变它们的远近。", "东方生成", listOf("阅读", "冥想")),
        generated("generated-custom-lab", "custom-lab", "声场实验室", "从旋律、节奏、铺底、人声与环境声开始，编排一段只属于你的持续声场。", "声场 DIY", listOf("自定义")),
        // 开放站台（radioCatalog.ts OPEN_RADIO_STATIONS）
        channel("channel-baroque", "巴洛克秩序", "复调、协奏曲与连续低音构成长时专注序列，快慢乐章交替但不过度刺激。", "古典·巴洛克",
            listOf(classicalById.getValue("bach-air"), expandedById.getValue("bach-bwv147-chorale"), classicalById.getValue("vivaldi-double-one"), classicalById.getValue("vivaldi-double-two"), classicalById.getValue("vivaldi-double-three")) + openClassicsById.ids("bach-passacaglia-bwv582", "bach-goldberg-var8", "vivaldi-winter-largo", "vivaldi-winter-allegro", "vivaldi-recorder-two", "vivaldi-recorder-three", "vivaldi-notte-three", "scarlatti-k159", "scarlatti-k466", "corelli-christmas-one", "corelli-christmas-two", "corelli-op6-no4-one", "couperin-barricades", "rameau-gavotte-doubles", "handel-fitzwilliam-three", "purcell-abdelazer-suite"),
            focus, "natural", 1.8, "巴洛克"),
        channel("channel-keyboard-counterpoint", "复调键盘长桌", "咏叹调与连续变奏保持稳定脉络，清晰的声部关系适合长时阅读和专注。", "古典·键盘复调",
            webKeyboardCounterpoint, listOf("专注", "阅读", "鉴赏"), "natural", 1.6, "巴洛克"),
        channel("channel-classical", "古典主义清澈", "古典时期的协奏曲、四重奏与室内乐保持均衡句法，适合晨间阅读和结构化工作。", "古典·古典主义",
            listOf(classicalById.getValue("mozart-flute-k313"), classicalById.getValue("beethoven-minuet"), classicalById.getValue("beethoven-concerto-largo"), expandedById.getValue("beethoven-quartet-six-adagio"), expandedById.getValue("beethoven-quartet-six-malinconia")) + openClassicsById.ids("mozart-k421-one", "mozart-k421-two", "mozart-k421-three", "mozart-k421-four", "mozart-rondo-k511", "mozart-divertimento-113-two", "mozart-divertimento-113-three", "mozart-k421-andante-musopen", "mozart-k465-adagio-allegro", "haydn-lark-one", "haydn-lark-three", "mendelssohn-scottish-adagio"),
            listOf("晨间", "阅读"), "natural", 2.0, "古典主义"),
        channel("channel-classical-architecture", "古典结构长卷", "由精密的键盘变奏过渡到更开阔的交响段落，兼顾结构化工作与完整聆听。", "古典·结构与交响",
            webClassicalArchitecture, listOf("专注", "阅读", "鉴赏"), "natural", 2.4, "古典主义"),
        channel("channel-romantic", "浪漫派远景", "管弦、钢琴与室内乐兼具歌唱性和空间感，保留抒情主线并减少高刺激炫技段落。", "古典·浪漫主义",
            listOf(classicalById.getValue("dvorak-larghetto"), expandedById.getValue("dvorak-new-world-largo"), expandedById.getValue("smetana-moldau"), expandedById.getValue("glazunov-menestrel"), expandedById.getValue("borodin-steppes"), expandedById.getValue("mendelssohn-nocturne")) + openClassicsById.ids("chopin-allegro-concert", "chopin-barcarolle-cc0", "chopin-canon-f-minor", "chopin-concerto-quartet-one", "chopin-concerto-quartet-two", "chopin-concerto-quartet-three", "faure-elegie", "saint-saens-swan"),
            listOf("夜读", "写作", "鉴赏"), "natural", 2.6, "浪漫主义"),
        channel("channel-romantic-piano-diary", "浪漫钢琴札记", "以十二个月的季节短曲为主轴，串联梦幻曲、间奏曲与即兴曲；起伏克制，适合整段阅读与安静创作。", "古典·浪漫钢琴",
            webRomanticPianoDiary, listOf("阅读", "放松", "创作", "鉴赏"), "natural", 2.6, "浪漫主义"),
        channel("channel-canon-variations", "卡农、恰空与变奏", "从持续低音、卡农到性格变奏，避免多个近似卡农版本的简单堆叠。", "古典·变奏专题",
            listOf(calmById.getValue("pachelbel-canon-piano-galloway"), calmById.getValue("pachelbel-canon-kevin-macleod"), expandedById.getValue("bach-goldberg-aria"), openClassicsById.getValue("bach-goldberg-var8"), openClassicsById.getValue("beethoven-32-variations"), openClassicsById.getValue("beethoven-diabelli-theme"), openClassicsById.getValue("beethoven-diabelli-01-02"), openClassicsById.getValue("beethoven-diabelli-03-04"), openClassicsById.getValue("chopin-canon-f-minor"), organTracks[1]),
            listOf("阅读", "放松", "鉴赏"), "natural", 2.8, "巴洛克至浪漫主义"),
        channel("channel-moonlight-impression", "月光与印象", "德彪西、萨蒂与晚期浪漫钢琴的低饱和光影，兼顾夜读、安静创作与纯粹聆听。", "古典·印象主义",
            listOf(calmById.getValue("debussy-clair-de-lune-goedhart"), calmById.getValue("debussy-arabesque-one-prati"), calmById.getValue("satie-gymnopedie-one-macleod"), classicalById.getValue("debussy-syrinx"), expandedById.getValue("scriabin-prelude-67-1")) + openClassicsById.ids("debussy-doctor-gradus", "debussy-sketchbook", "debussy-arabesque-two", "debussy-clair-brass", "chopin-berceuse-pd", "ravel-pavane-pd", "respighi-intermezzo-serenata"),
            listOf("夜读", "写作", "放松"), "natural", 3.0, "印象主义与现代主义"),
        channel("channel-lyric-night", "无词歌与夜读", "夜曲、摇篮曲、幻想曲与慢乐章组成夜间书架，旋律清楚但不抢占阅读注意力。", "古典·抒情小品",
            listOf(calmById.getValue("mendelssohn-venetian-gondola-30-6"), classicalById.getValue("chopin-nocturne-55"), classicalById.getValue("chopin-nocturne-62"), calmById.getValue("grieg-piano-concerto-adagio"), expandedById.getValue("glazunov-menestrel"), expandedById.getValue("mendelssohn-nocturne"), openClassicsById.getValue("chopin-berceuse-pd"), openClassicsById.getValue("mozart-rondo-k511"), openClassicsById.getValue("chopin-fantaisie-op49"), architectureById.getValue("beethoven-nine-three")),
            listOf("夜读", "写作", "沉浸"), "natural", 2.8, "浪漫主义"),
        channel("channel-piano", "钢琴独奏室", "从巴赫、贝多芬到肖邦与开放录音，按安静程度组织成长时钢琴序列。", "器乐·钢琴",
            listOf(classicalById.getValue("bach-wtc-prelude"), expandedById.getValue("bach-goldberg-aria"), expandedById.getValue("beethoven-sonata-28-one"), expandedById.getValue("beethoven-sonata-28-three"), expandedById.getValue("beethoven-hammerklavier-adagio"), classicalById.getValue("chopin-nocturne-55"), classicalById.getValue("chopin-nocturne-62"), classicalById.getValue("chopin-etude-10-1"), classicalById.getValue("chopin-mazurka-17-4"), expandedById.getValue("chopin-ballade-two"), expandedById.getValue("chopin-ballade-three"), expandedById.getValue("chopin-barcarolle"), openById.getValue("rhapsody-blue")),
            focus, "natural", 2.4),
        channel("channel-violin", "小提琴长廊", "从巴赫独奏到维瓦尔第协奏曲和室内乐，快慢段落交替，避免连续高动态。", "器乐·小提琴",
            listOf(classicalById.getValue("bach-sonata-adagio"), classicalById.getValue("bach-partita-preludio"), classicalById.getValue("bach-chaconne"), classicalById.getValue("vivaldi-double-two"), classicalById.getValue("bach-air"), openClassicsById.getValue("vivaldi-winter-largo"), openClassicsById.getValue("vivaldi-winter-allegro"), openClassicsById.getValue("chopin-concerto-quartet-one"), openClassicsById.getValue("chopin-concerto-quartet-two"), openClassicsById.getValue("chopin-concerto-quartet-three")),
            listOf("阅读", "鉴赏"), "natural", 2.0, "巴洛克"),
        channel("channel-woodwind", "木管晨光", "长笛、竖琴、单簧管与双簧管在小编制中交替；慢章适合阅读，快章保留给晨间与鉴赏。", "器乐·木管",
            listOf(verifiedById.getValue("mozart-flute-harp-andantino"), verifiedById.getValue("mozart-flute-harp-allegro"), verifiedById.getValue("mozart-flute-harp-rondeau"), verifiedById.getValue("mozart-clarinet-adagio"), verifiedById.getValue("brahms-clarinet-quintet-adagio"), verifiedById.getValue("weber-grand-duo-andante"), classicalById.getValue("debussy-syrinx"), classicalById.getValue("andersen-etude"), classicalById.getValue("vivaldi-notte-one"), classicalById.getValue("vivaldi-notte-two"), classicalById.getValue("vivaldi-notte-four"), expandedById.getValue("albinoni-oboe-one"), expandedById.getValue("albinoni-oboe-two"), expandedById.getValue("albinoni-oboe-three")),
            listOf("晨间", "阅读", "鉴赏"), "natural", 1.8, "巴洛克"),
        channel("channel-cello", "大提琴静室", "无伴奏组曲、协奏曲、奏鸣曲与抒情改编形成低位而清晰的连续聆听。", "器乐·大提琴",
            celloTracks + verifiedById.getValue("beethoven-cello-sonata-three-two") + webInstrumentById.ids("cello-vivaldi-allegro-one", "cello-vivaldi-allegro-three", "cello-debussy-beau-soir", "cello-bach-gigue", "cello-bach-allemande-john-michel", "cello-faure-elegie", "cello-saint-saens-swan"),
            listOf("阅读", "沉浸", "鉴赏"), "natural", 2.3),
        channel("channel-organ", "穹顶管风琴", "帕赫贝尔与巴赫的托卡塔、赋格和奏鸣曲，扩展为可连续聆听的完整专题。", "器乐·管风琴",
            organTracks + webInstrumentById.ids("organ-pachelbel-fugue-13", "organ-pachelbel-toccata-f", "organ-pachelbel-toccata-e", "organ-bach-543-prelude", "organ-bach-543-fugue", "organ-bach-529-one", "organ-bach-526-one"), listOf("阅读", "沉浸", "鉴赏"), "natural", 2.8, "巴洛克"),
        channel("channel-eastern-strings", "东方弦管选", "古琴、二胡与高胡保持主体，现代开放器乐和跨界氛围只作少量过渡。", "器乐·东方",
            listOf(easternById.getValue("guqin-yangguan-sandie"), easternById.getValue("guqin-zuiyu-changwan"), easternById.getValue("erhu-erquan"), easternById.getValue("erhu-river"), easternById.getValue("gaohu-linked-buckles"), easternById.getValue("gaohu-rain-banana"), openById.getValue("not-that-east"), openById.getValue("fairies-talking")) + webInstrumentById.ids("ambient-india-zeropage", "ambient-dance-zeropage"),
            listOf("阅读", "鉴赏", "放松"), "natural", 2.4, "东方"),
        channel("channel-plucked", "弦拨与木色", "古典吉他、竖琴与齐特琴的木质余韵，适合低压任务、阅读和创作间歇。", "器乐·弦拨",
            listOf(verifiedById.getValue("guitar-anonymous-romance"), verifiedById.getValue("guitar-el-noi"), verifiedById.getValue("guitar-sor-op31-1"), verifiedById.getValue("guitar-tarrega-gran-vals"), verifiedById.getValue("guitar-pachelbel-canon"), verifiedById.getValue("harp-meadow-thoughts"), verifiedById.getValue("zither-strauss-woods"), contemporaryById.getValue("calm-fireplace-guitar"), contemporaryById.getValue("small-fire-guitar-loop"), openById.getValue("guitar-solo"), openById.getValue("calm-bgm"), contemporaryById.getValue("just-as-soon")) + replacementCc0ById.ids("cc0-etirwer", "cc0-serenade-guitar", "cc0-frets", "cc0-middle-nowhere-remix", "cc0-sunset-plains"),
            listOf("轻任务", "阅读", "放松"), "crossfade", 3.0),
        channel("channel-vintage-jazz", "爵士闲窗", "Rhodes 电钢、吉他、蓝调与轻 Swing 组成的开放小编制，适合休息、创作和轻任务。", "器乐·爵士",
            listOf(openById.getValue("jazz-park"), openById.getValue("jazz-simple"), contemporaryById.getValue("night-docks-trumpet")) + webModernById.ids("jazz-avant", "jazz-brunch", "jazz-samba", "jazz-memphis-blues", "jazz-dont-care-blues") + replacementCc0ById.ids("cc0-jazzy-blues", "cc0-blue-intermission", "cc0-fusion-jazz", "cc0-one-step", "cc0-catchy-swing"),
            listOf("休息", "创作", "通勤"), "crossfade", 2.6),
        channel("channel-lofi", "Lo-fi 专注架", "从低密度鼓点到轻快 Hip-hop 的开放节拍序列，柔和衔接并避免单一循环疲劳。", "Lo-fi",
            listOf(openById.getValue("chill-beat"), openById.getValue("lofi-again"), openById.getValue("chill-lofi"), openById.getValue("lofi-hiphop"), openById.getValue("chilled-lofi")) + webModernById.ids("lofi-caden-currie", "lofi-music-001", "lofi-upbeat-raspberry", "lofi-perspective", "lofi-de"),
            listOf("学习", "编码"), "crossfade", 3.6),
        channel("channel-beats", "节拍工作流", "Hip-hop、电子、放克与低密度节拍分段排列，适合整理、重复任务和短时冲刺。", "节拍",
            listOf(openById.getValue("synth-hiphop"), openById.getValue("game-bgm"), openById.getValue("electronic-piano"), contemporaryById.getValue("dream-culture"), contemporaryById.getValue("kumasi-groove")) + webModernById.ids("beats-funkorama", "beats-groove-grove", "beats-electro-cabello", "beats-faster-does-it", "beats-disco-lounge"),
            listOf("整理", "冲刺"), "crossfade", 2.8),
        channel("channel-ambient", "深空氛围卷", "开放 Pad、Drone 与深空织体以长淡化保持连续，适合叠加环境声和长时播放。", "氛围",
            ambientPads + webModernById.ids("ambient-voyager", "ambient-zero-point", "ambient-brenticus"), listOf("深度工作", "助眠"), "crossfade", 5.5),
        channel("channel-modern-piano", "开放钢琴夜", "现代开放钢琴与低密度旋律，增加更多写作、夜读和冥想取向的独立作品。", "器乐·现代钢琴",
            listOf(openById.getValue("emotional-piano"), openById.getValue("tiny-movement"), contemporaryById.getValue("yoiyami-deep-blue"), contemporaryById.getValue("yoiyami-first-light"), contemporaryById.getValue("solo-piano-four"), contemporaryById.getValue("meditation-impromptu-one"), contemporaryById.getValue("meditation-impromptu-two"), contemporaryById.getValue("meditation-impromptu-three"), contemporaryById.getValue("incompetech-starry")) + webModernById.ids("modern-piano-at-rest", "modern-piano-almost-f", "modern-piano-clean-soul"),
            listOf("阅读", "写作", "冥想"), "crossfade", 3.8),
        channel("channel-open-calm", "开放静心选", "原声冥想、无缝循环与缓慢电子织体，优先挑选不含突发动态的作品。", "氛围·静心",
            listOf(openById.getValue("exploration"), openById.getValue("into-stars"), contemporaryById.getValue("indieteur-revelation"), contemporaryById.getValue("yd-searching"), contemporaryById.getValue("ambient-guitar-dust"), quietById.getValue("ambient-sunset-walk"), quietById.getValue("ambient-relax-background-one"), contemporaryById.getValue("joth-contemplation"), contemporaryById.getValue("end-of-hope"), contemporaryById.getValue("background-music-one"), contemporaryById.getValue("white-lotus"), contemporaryById.getValue("atlantean-twilight")),
            listOf("深度工作", "冥想", "助眠"), "crossfade", 5.0),
        channel("channel-choral", "穹顶合唱", "阿卡贝拉、圣咏、巴洛克与浪漫合唱组成更完整的人声专题。", "人声·合唱",
            choralTracks + webModernById.ids("choral-vivaldi-misericordia", "choral-vivaldi-esurientes", "choral-vivaldi-gloria"), listOf("休息", "冥想", "鉴赏"), "natural", 2.5),
        channel("channel-lyrics", "有人声的休息站", "公版与开放许可的艺术歌曲、民谣和历史录音，适合休息与鉴赏。", "人声·歌曲",
            lyricalTracks + webModernById.ids("lyrics-mozart-luise", "lyrics-mozart-violet", "lyrics-debussy-ariettes-two", "lyrics-debussy-ariettes-four"), listOf("休息", "通勤", "鉴赏"), "natural", 2.5),
    )
}

private fun commonsAudio(fileName: String): String =
    "https://commons.wikimedia.org/wiki/Special:Redirect/file/" + java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
private fun commonsPage(fileName: String): String =
    "https://commons.wikimedia.org/wiki/File:" + java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
/** 全量内置曲目（assets/radio/，共 299 首）的 trackId → 扩展名。
 *  所有打包录音统一为 Ogg Opus，来源与原始格式记录保留在许可清单中。 */
private val baseApkAssets = mapOf(
    "albinoni-oboe-one" to "opus",
    "albinoni-oboe-three" to "opus",
    "albinoni-oboe-two" to "opus",
    "ambient-brenticus" to "opus",
    "ambient-dance-zeropage" to "opus",
    "ambient-guitar-dust" to "opus",
    "ambient-india-zeropage" to "opus",
    "ambient-pad-i" to "opus",
    "ambient-pad-ii" to "opus",
    "ambient-pad-iv" to "opus",
    "ambient-pad-ix" to "opus",
    "ambient-pad-v" to "opus",
    "ambient-pad-vi" to "opus",
    "ambient-pad-vii" to "opus",
    "ambient-pad-viii" to "opus",
    "ambient-pad-x" to "opus",
    "ambient-relax-background-one" to "opus",
    "ambient-sunset-walk" to "opus",
    "ambient-voyager" to "opus",
    "ambient-zero-point" to "opus",
    "andersen-etude" to "opus",
    "atlantean-twilight" to "opus",
    "bach-air" to "opus",
    "bach-bwv147-chorale" to "opus",
    "bach-chaconne" to "opus",
    "bach-goldberg-aria" to "opus",
    "bach-goldberg-aria-cc0" to "opus",
    "bach-goldberg-cc0-01" to "opus",
    "bach-goldberg-cc0-02" to "opus",
    "bach-goldberg-cc0-03" to "opus",
    "bach-goldberg-cc0-04" to "opus",
    "bach-goldberg-cc0-05" to "opus",
    "bach-goldberg-cc0-06" to "opus",
    "bach-goldberg-cc0-07" to "opus",
    "bach-goldberg-cc0-08" to "opus",
    "bach-goldberg-cc0-09" to "opus",
    "bach-goldberg-cc0-10" to "opus",
    "bach-goldberg-cc0-11" to "opus",
    "bach-goldberg-cc0-12" to "opus",
    "bach-goldberg-cc0-13" to "opus",
    "bach-goldberg-cc0-14" to "opus",
    "bach-goldberg-cc0-15" to "opus",
    "bach-goldberg-cc0-16" to "opus",
    "bach-goldberg-cc0-27" to "opus",
    "bach-goldberg-cc0-28" to "opus",
    "bach-goldberg-cc0-29" to "opus",
    "bach-goldberg-cc0-30" to "opus",
    "bach-goldberg-var8" to "opus",
    "bach-partita-preludio" to "opus",
    "bach-passacaglia-bwv582" to "opus",
    "bach-sonata-adagio" to "opus",
    "bach-wtc-prelude" to "opus",
    "background-music-one" to "opus",
    "beats-disco-lounge" to "opus",
    "beats-electro-cabello" to "opus",
    "beats-faster-does-it" to "opus",
    "beats-funkorama" to "opus",
    "beats-groove-grove" to "opus",
    "beethoven-32-variations" to "opus",
    "beethoven-cello-sonata-three-two" to "opus",
    "beethoven-concerto-largo" to "opus",
    "beethoven-diabelli-01-02" to "opus",
    "beethoven-diabelli-03-04" to "opus",
    "beethoven-diabelli-05-07" to "opus",
    "beethoven-diabelli-08-10" to "opus",
    "beethoven-diabelli-11-13" to "opus",
    "beethoven-diabelli-14" to "opus",
    "beethoven-diabelli-15-17" to "opus",
    "beethoven-diabelli-18-19" to "opus",
    "beethoven-diabelli-20-23" to "opus",
    "beethoven-diabelli-24" to "opus",
    "beethoven-diabelli-25-29" to "opus",
    "beethoven-diabelli-30" to "opus",
    "beethoven-diabelli-31" to "opus",
    "beethoven-diabelli-32" to "opus",
    "beethoven-diabelli-33" to "opus",
    "beethoven-diabelli-theme" to "opus",
    "beethoven-hammerklavier-adagio" to "opus",
    "beethoven-minuet" to "opus",
    "beethoven-nine-four" to "opus",
    "beethoven-nine-one" to "opus",
    "beethoven-nine-three" to "opus",
    "beethoven-nine-two" to "opus",
    "beethoven-quartet-six-adagio" to "opus",
    "beethoven-quartet-six-malinconia" to "opus",
    "beethoven-sonata-28-one" to "opus",
    "beethoven-sonata-28-three" to "opus",
    "borodin-steppes" to "opus",
    "brahms-clarinet-quintet-adagio" to "opus",
    "brahms-op116-four" to "opus",
    "brahms-op117-two" to "opus",
    "brahms-op118-two" to "opus",
    "calm-bgm" to "opus",
    "calm-fireplace-guitar" to "opus",
    "cc0-blue-intermission" to "opus",
    "cc0-catchy-swing" to "opus",
    "cc0-etirwer" to "opus",
    "cc0-frets" to "opus",
    "cc0-fusion-jazz" to "opus",
    "cc0-jazzy-blues" to "opus",
    "cc0-middle-nowhere-remix" to "opus",
    "cc0-one-step" to "opus",
    "cc0-serenade-guitar" to "opus",
    "cc0-sunset-plains" to "opus",
    "cello-bach-allemande" to "opus",
    "cello-bach-allemande-john-michel" to "opus",
    "cello-bach-casals-prelude" to "opus",
    "cello-bach-cc0-prelude" to "opus",
    "cello-bach-gigue" to "opus",
    "cello-bach-sarabande" to "opus",
    "cello-debussy-beau-soir" to "opus",
    "cello-faure-elegie" to "opus",
    "cello-saint-saens-swan" to "opus",
    "cello-vivaldi-allegro-one" to "opus",
    "cello-vivaldi-allegro-three" to "opus",
    "cello-vivaldi-largo" to "opus",
    "chill-beat" to "opus",
    "chill-lofi" to "opus",
    "chilled-lofi" to "opus",
    "chopin-allegro-concert" to "opus",
    "chopin-ballade-three" to "opus",
    "chopin-ballade-two" to "opus",
    "chopin-barcarolle" to "opus",
    "chopin-barcarolle-cc0" to "opus",
    "chopin-berceuse-pd" to "opus",
    "chopin-canon-f-minor" to "opus",
    "chopin-concerto-quartet-one" to "opus",
    "chopin-concerto-quartet-three" to "opus",
    "chopin-concerto-quartet-two" to "opus",
    "chopin-etude-10-1" to "opus",
    "chopin-fantaisie-op49" to "opus",
    "chopin-mazurka-17-4" to "opus",
    "chopin-nocturne-55" to "opus",
    "chopin-nocturne-62" to "opus",
    "choral-handel-glory" to "opus",
    "choral-handel-word" to "opus",
    "choral-pavane" to "opus",
    "choral-racine" to "opus",
    "choral-resonet-laudibus" to "opus",
    "choral-salve-regina" to "opus",
    "choral-tollite" to "opus",
    "choral-vivaldi-esurientes" to "opus",
    "choral-vivaldi-gloria" to "opus",
    "choral-vivaldi-misericordia" to "opus",
    "corelli-christmas-one" to "opus",
    "corelli-christmas-two" to "opus",
    "corelli-op6-no4-one" to "opus",
    "couperin-barricades" to "opus",
    "debussy-arabesque-one-prati" to "opus",
    "debussy-arabesque-two" to "opus",
    "debussy-clair-brass" to "opus",
    "debussy-clair-de-lune-goedhart" to "opus",
    "debussy-doctor-gradus" to "opus",
    "debussy-sketchbook" to "opus",
    "debussy-syrinx" to "opus",
    "dream-culture" to "opus",
    "dvorak-larghetto" to "opus",
    "dvorak-new-world-largo" to "opus",
    "electronic-piano" to "opus",
    "emotional-piano" to "opus",
    "end-of-hope" to "opus",
    "erhu-erquan" to "opus",
    "erhu-river" to "opus",
    "exploration" to "opus",
    "fairies-talking" to "opus",
    "faure-berceuse-op56" to "opus",
    "faure-elegie" to "opus",
    "game-bgm" to "opus",
    "gaohu-linked-buckles" to "opus",
    "gaohu-rain-banana" to "opus",
    "glazunov-menestrel" to "opus",
    "grieg-piano-concerto-adagio" to "opus",
    "guitar-anonymous-romance" to "opus",
    "guitar-el-noi" to "opus",
    "guitar-pachelbel-canon" to "opus",
    "guitar-solo" to "opus",
    "guitar-sor-op31-1" to "opus",
    "guitar-tarrega-gran-vals" to "opus",
    "guqin-yangguan-sandie" to "opus",
    "guqin-zuiyu-changwan" to "opus",
    "handel-fitzwilliam-three" to "opus",
    "harp-meadow-thoughts" to "opus",
    "haydn-lark-one" to "opus",
    "haydn-lark-three" to "opus",
    "incompetech-starry" to "opus",
    "indieteur-revelation" to "opus",
    "into-stars" to "opus",
    "jazz-avant" to "opus",
    "jazz-brunch" to "opus",
    "jazz-dont-care-blues" to "opus",
    "jazz-memphis-blues" to "opus",
    "jazz-park" to "opus",
    "jazz-samba" to "opus",
    "jazz-simple" to "opus",
    "joth-contemplation" to "opus",
    "just-as-soon" to "opus",
    "kumasi-groove" to "opus",
    "lofi-again" to "opus",
    "lofi-caden-currie" to "opus",
    "lofi-de" to "opus",
    "lofi-hiphop" to "opus",
    "lofi-music-001" to "opus",
    "lofi-perspective" to "opus",
    "lofi-upbeat-raspberry" to "opus",
    "lyrics-auld-lang-syne" to "opus",
    "lyrics-debussy-ariettes-four" to "opus",
    "lyrics-debussy-ariettes-two" to "opus",
    "lyrics-frankie-johnny" to "opus",
    "lyrics-kentucky-home" to "opus",
    "lyrics-mozart-luise" to "opus",
    "lyrics-mozart-violet" to "opus",
    "lyrics-old-black-joe" to "opus",
    "lyrics-old-folks" to "opus",
    "lyrics-perfect-day" to "opus",
    "meditation-impromptu-one" to "opus",
    "meditation-impromptu-three" to "opus",
    "meditation-impromptu-two" to "opus",
    "mendelssohn-nocturne" to "opus",
    "mendelssohn-scottish-adagio" to "opus",
    "mendelssohn-venetian-gondola-30-6" to "opus",
    "modern-piano-almost-f" to "opus",
    "modern-piano-at-rest" to "opus",
    "modern-piano-clean-soul" to "opus",
    "mozart-clarinet-adagio" to "opus",
    "mozart-divertimento-113-three" to "opus",
    "mozart-divertimento-113-two" to "opus",
    "mozart-flute-harp-allegro" to "opus",
    "mozart-flute-harp-andantino" to "opus",
    "mozart-flute-harp-rondeau" to "opus",
    "mozart-flute-k313" to "opus",
    "mozart-k421-andante-musopen" to "opus",
    "mozart-k421-four" to "opus",
    "mozart-k421-one" to "opus",
    "mozart-k421-three" to "opus",
    "mozart-k421-two" to "opus",
    "mozart-k465-adagio-allegro" to "opus",
    "mozart-rondo-k511" to "opus",
    "night-docks-trumpet" to "opus",
    "not-that-east" to "opus",
    "organ-bach-526-one" to "opus",
    "organ-bach-529-one" to "opus",
    "organ-bach-543-fugue" to "opus",
    "organ-bach-543-prelude" to "opus",
    "organ-buxtehude-toccata" to "opus",
    "organ-pachelbel-chorale" to "opus",
    "organ-pachelbel-ciacona" to "opus",
    "organ-pachelbel-fugue-13" to "opus",
    "organ-pachelbel-toccata-e" to "opus",
    "organ-pachelbel-toccata-f" to "opus",
    "pachelbel-canon-kevin-macleod" to "opus",
    "pachelbel-canon-piano-galloway" to "opus",
    "purcell-abdelazer-suite" to "opus",
    "rameau-gavotte-doubles" to "opus",
    "ravel-pavane-pd" to "opus",
    "respighi-intermezzo-serenata" to "opus",
    "rhapsody-blue" to "opus",
    "saint-saens-swan" to "opus",
    "satie-gymnopedie-one-macleod" to "opus",
    "scarlatti-k159" to "opus",
    "scarlatti-k466" to "opus",
    "schubert-impromptu-d935-three" to "opus",
    "schumann-kinderszenen-one" to "opus",
    "schumann-kinderszenen-seven" to "opus",
    "schumann-kinderszenen-ten" to "opus",
    "scriabin-prelude-67-1" to "opus",
    "small-fire-guitar-loop" to "opus",
    "smetana-moldau" to "opus",
    "solo-piano-four" to "opus",
    "synth-hiphop" to "opus",
    "tchaikovsky-seasons-april" to "opus",
    "tchaikovsky-seasons-august" to "opus",
    "tchaikovsky-seasons-december" to "opus",
    "tchaikovsky-seasons-february" to "opus",
    "tchaikovsky-seasons-january" to "opus",
    "tchaikovsky-seasons-july" to "opus",
    "tchaikovsky-seasons-june" to "opus",
    "tchaikovsky-seasons-march" to "opus",
    "tchaikovsky-seasons-may" to "opus",
    "tchaikovsky-seasons-november" to "opus",
    "tchaikovsky-seasons-october" to "opus",
    "tchaikovsky-seasons-september" to "opus",
    "tiny-movement" to "opus",
    "vivaldi-double-one" to "opus",
    "vivaldi-double-three" to "opus",
    "vivaldi-double-two" to "opus",
    "vivaldi-notte-four" to "opus",
    "vivaldi-notte-one" to "opus",
    "vivaldi-notte-three" to "opus",
    "vivaldi-notte-two" to "opus",
    "vivaldi-recorder-three" to "opus",
    "vivaldi-recorder-two" to "opus",
    "vivaldi-winter-allegro" to "opus",
    "vivaldi-winter-largo" to "opus",
    "weber-grand-duo-andante" to "opus",
    "white-lotus" to "opus",
    "yd-searching" to "opus",
    "yoiyami-deep-blue" to "opus",
    "yoiyami-first-light" to "opus",
    "zither-strauss-woods" to "opus",
)

/** 返回基础 APK 内置曲目的本地资产地址；不在基础 APK 的曲目返回 null（不得生成假地址）。 */
private fun radioAsset(id: String): String? = baseApkAssets[id]?.let { "asset:///radio/$id.$it" }

fun SoundFilter.label(): String = when (this) {
    SoundFilter.ALL -> "全部"; SoundFilter.CURRENT -> "当前"; SoundFilter.FAVORITES -> "收藏"
    SoundFilter.NATURE -> "自然"; SoundFilter.RAIN -> "雨声"; SoundFilter.ANIMALS -> "动物"
    SoundFilter.URBAN -> "城市"; SoundFilter.PLACES -> "场景"; SoundFilter.TRANSPORT -> "交通"
    SoundFilter.THINGS -> "物件"; SoundFilter.NOISE -> "噪音"
}

fun RadioGroup.label(): String = when (this) {
    RadioGroup.OFFICIAL -> "官方"; RadioGroup.GENERATED -> "生成"; RadioGroup.CUSTOM -> "自定义"
}
