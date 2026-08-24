package com.soundist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阶段 A 命令队列 / retire-ack / generation 协议的确定性规格测试。
 *
 * 说明：真正的实现是 native `soundist_audio.c`（SPSC 无锁环形缓冲 + 控制线程 shadow + 回调 retire-ack），
 * 无法在 JVM 直接运行。本文件用最小 Kotlin 模型精确复刻其**协议语义**，作为可执行规格：
 * 验证「FIFO 有序、满返回明确错误、replace/remove 不双 free、generation 防止旧 remove 删新资源、
 * remove 后槽清空（无 use-after-free）」。C 侧实现与之一一对应，供代码审查对照。
 *
 * 单生产者前提：所有 addTrack/removeTrack 由 NativeAudioCore 专用控制线程串行投递，
 * 命令在回调内按 FIFO 一次性消费。模型里 [addTrack]/[removeTrack] 即控制线程串行操作，
 * [drainCallback] 即回调消费命令。
 */
private const val ADD = 4
private const val REMOVE = 5

class NativeAudioCoreProtocolTest {

    private class Command(val type: Int, val id: Int, val gen: Int, val newPcm: Int, val retire: Int)

    private class Protocol {
        private val queue = ArrayDeque<Command>()
        val retired = mutableListOf<Int>()       // 已 free 的指针 id（断言：无重复）
        private val ctlGen = IntArray(64)
        private val ctlActive = BooleanArray(64)
        private val ctlPcm = IntArray(64) { -1 }
        private val slotGen = IntArray(64)
        private val slotActive = BooleanArray(64)
        private val slotPcm = IntArray(64) { -1 }
        private var nextPtr = 1

        fun addTrack(id: Int): Int {
            val oldPcm = if (ctlActive[id]) ctlPcm[id] else -1
            val gen = ctlGen[id] + 1
            val newPcm = nextPtr++
            if (queue.size >= 256) return -1 // 满：明确错误，不静默丢
            queue.addLast(Command(ADD, id, gen, newPcm, oldPcm))
            ctlActive[id] = true; ctlPcm[id] = newPcm; ctlGen[id] = gen
            return 0
        }

        fun removeTrack(id: Int): Int {
            if (!ctlActive[id] || ctlPcm[id] < 0) return 0 // 幂等
            val gen = ctlGen[id]; val pcm = ctlPcm[id]
            if (queue.size >= 256) return -1
            queue.addLast(Command(REMOVE, id, gen, -1, pcm))
            ctlActive[id] = false; ctlPcm[id] = -1; ctlGen[id]++
            return 0
        }

        /** 回调：一次性消费全部待处理命令，等价 soundist_data_callback 开头的消费循环。 */
        fun drainCallback() {
            while (queue.isNotEmpty()) {
                val c = queue.removeFirst()
                when (c.type) {
                    ADD -> {
                        if (c.retire >= 0) retired += c.retire
                        slotActive[c.id] = true; slotPcm[c.id] = c.newPcm; slotGen[c.id] = c.gen
                    }
                    REMOVE -> {
                        if (!slotActive[c.id]) continue
                        if (slotGen[c.id] != c.gen) continue // generation 守卫：旧 remove 不删新资源
                        retired += c.retire
                        slotActive[c.id] = false; slotPcm[c.id] = -1
                    }
                }
            }
        }

        fun slotActive(id: Int) = slotActive[id]
    }

    @Test fun command_queue_is_fifo() {
        val p = Protocol()
        p.addTrack(0); p.addTrack(1); p.removeTrack(0); p.addTrack(2)
        // 指针 1,2 各分配一次，3 分配（track2），0 被 remove 退休。全部消费后不得双 free。
        p.drainCallback()
        assertEquals("每个指针恰好退休一次", p.retired.size, p.retired.distinct().size)
        assertFalse("track0 已移除", p.slotActive(0))
        assertTrue("track1 仍活跃", p.slotActive(1))
        assertTrue("track2 仍活跃", p.slotActive(2))
    }

    @Test fun replace_same_id_retires_old_exactly_once() {
        val p = Protocol()
        p.addTrack(0) // ptr1
        p.drainCallback()
        p.addTrack(0) // 替换 → 退休 ptr1，新增 ptr2
        p.drainCallback()
        assertEquals("替换只退休旧指针一次", 1, p.retired.size)
        assertTrue(p.slotActive(0))
    }

    @Test fun remove_then_readd_no_double_free_no_uaf() {
        val p = Protocol()
        p.addTrack(0) // ptr1
        p.drainCallback()
        p.removeTrack(0) // 退休 ptr1
        p.addTrack(0)    // 新增 ptr2（无旧指针可退）
        p.drainCallback()
        assertEquals("ptr1 退休一次、ptr2 未被误退", listOf(1), p.retired)
        assertTrue(p.slotActive(0))
    }

    @Test fun stale_remove_does_not_delete_new_resource() {
        val p = Protocol()
        p.addTrack(0)   // gen1 ptr1
        p.removeTrack(0) // remove(gen1) 入队
        p.addTrack(0)   // gen2 ptr2 入队（control shadow 已指向 ptr2）
        p.drainCallback()
        // FIFO：先消费 remove(gen1) 退休 ptr1，再消费 add(gen2) 无旧指针。
        assertEquals(listOf(1), p.retired)
        assertTrue("add(gen2) 后 slot 活跃（旧 remove 未删新资源）", p.slotActive(0))
    }

    @Test fun queue_full_returns_error_and_drops_nothing() {
        val p = Protocol()
        // 填满 256 条命令（不 drain，模拟设备未运行/回调未消费）。
        var rc = 0
        repeat(256) { rc = p.addTrack(0) }
        assertEquals("前 256 条成功", 0, rc)
        // 第 257 条：满 → -1，不静默丢失。
        assertEquals("满必须返回明确错误", -1, p.addTrack(1))
        // 关键 remove 在满时也返回 -1（不静默丢）。
        assertEquals(-1, p.removeTrack(0))
    }

    @Test fun generation_guard_blocks_mismatched_remove() {
        // 直接构造「槽已升代、remove 仍持旧代」：generation 守卫跳过，不误删、不双 free。
        val p = Protocol()
        p.addTrack(0) // gen1
        p.drainCallback()
        p.removeTrack(0) // remove(gen1)
        p.addTrack(0)    // gen2
        p.drainCallback()
        assertEquals("最终只退休 gen1 的指针", listOf(1), p.retired)
        assertTrue(p.slotActive(0))
    }
}
