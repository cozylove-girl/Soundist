package com.soundist.feature.listening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * task1 三.A.6：遍历所有内置曲目，确认每个 `asset:///radio/...` 资源键都能找到真实文件。
 * 反向保护「严禁 Catalog 写了 asset:///... 但 APK 中没有对应文件」。
 *
 * 测试 cwd 为模块目录（feature/listening），`../../app/src/main/assets/radio` 指向 app 模块的 assets/radio。
 */
class BaseApkAssetsTest {

    private fun realRadioFiles(): Set<String> {
        val dir = File("../../app/src/main/assets/radio")
        assertTrue("assets/radio 目录不存在：${dir.absolutePath}", dir.isDirectory)
        return dir.listFiles()!!.filter { it.isFile }.map { it.name }.toSet()
    }

    /**
     * 环境声 84 轨：core/model `SoundCatalog` 生成的 `asset:///sounds/<folder>/<id>.<ext>`
     * 必须全部映射到 assets/sounds 下的真实文件（Media3ListeningAudioController 正是用它把 soundId
     * → assetUri 交给引擎经 DefaultDataSource 播放），防止「Catalog 写了 asset:///sounds/... 但
     * assets 里没有对应文件」的假路径。
     */
    @Test fun every_ambient_sound_maps_to_a_real_asset_file() {
        val sounds = com.soundist.core.model.SoundCatalog.sounds
        assertTrue("环境声目录必须是恰好 84 个声音，实际=${sounds.size}", sounds.size == 84)
        val soundsDir = File("../../app/src/main/assets/sounds")
        assertTrue("assets/sounds 目录不存在：${soundsDir.absolutePath}", soundsDir.isDirectory)
        val realFiles = soundsDir.walkTopDown().filter { it.isFile }.map { it.relativeTo(soundsDir).invariantSeparatorsPath }.toSet()
        sounds.forEach { sound ->
            val uri = sound.assetUri
            assertTrue("环境声「${sound.id}」必须走 asset:///sounds/，实际=$uri", uri.startsWith("asset:///sounds/"))
            val rel = uri.removePrefix("asset:///sounds/")
            assertTrue("环境声「${sound.id}」的 asset 路径不得是假路径（须含分类子目录与扩展名）：$rel", rel.contains('/') && rel.contains('.'))
            assertTrue("环境声「${sound.id}」缺少真实文件：$rel", rel in realFiles)
        }
    }

    /** 反向交叉校验：feature 展示目录与 core/model 播放目录的 84 个声音 id 完全一致，不出现两处漂移。 */
    @Test fun feature_and_core_catalogs_agree_on_all_84_sounds() {
        val coreIds = com.soundist.core.model.SoundCatalog.sounds.map { it.id }.toSet()
        val featureIds = SoundCatalog.items.map { it.id }.toSet()
        assertEquals("feature 声音目录必须是 84 个", 84, featureIds.size)
        assertTrue("feature 与 core 声音目录不一致，core 有而 feature 缺：${coreIds - featureIds}", featureIds.containsAll(coreIds))
    }

    @Test fun every_bundled_asset_key_maps_to_a_real_file() {
        val realFiles = realRadioFiles()
        val official = RadioCatalog.initial.filter { it.group == RadioGroup.OFFICIAL }
        val bundled = official.flatMap { it.tracks }.filter { it.localAssetUri != null }

        // 基础 APK 内置曲目必须有真实文件，且总数与 assets/radio 音频文件一致。
        assertTrue("未找到任何内置曲目", bundled.isNotEmpty())
        bundled.forEach { track ->
            val uri = track.localAssetUri!!
            assertTrue("内置曲目必须走 asset:///radio/，实际=$uri", uri.startsWith("asset:///radio/"))
            val fileName = uri.removePrefix("asset:///radio/")
            assertTrue("内置曲目「${track.id}」缺少真实文件：$fileName", fileName in realFiles)
        }
    }

    @Test fun every_real_radio_file_is_referenced_by_the_catalog() {
        // 反向：assets/radio 中的每个音频文件（排除 ASSET-MANIFEST.md）都必须被 Catalog 引用，禁止孤儿文件。
        val realFiles = realRadioFiles().filter { it != "ASSET-MANIFEST.md" }.toSet()
        val official = RadioCatalog.initial.filter { it.group == RadioGroup.OFFICIAL }
        val referenced = official.flatMap { it.tracks }.mapNotNull { it.localAssetUri?.removePrefix("asset:///radio/") }.toSet()

        val orphans = realFiles - referenced
        assertTrue("assets/radio 存在未被 Catalog 引用的孤儿文件：$orphans", orphans.isEmpty())
        assertEquals(realFiles.size, referenced.size)
    }
}
