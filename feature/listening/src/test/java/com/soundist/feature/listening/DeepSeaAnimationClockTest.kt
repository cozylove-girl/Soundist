package com.soundist.feature.listening

import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Refresh-rate consistency for the deep-sea home animation.
 *
 * The whole canvas is driven by a fixed 1/60 s step clock ([DeepSeaAnimationClock]), so the number of
 * simulation steps and the final particle state must be identical for the same wall-clock duration
 * regardless of the display refresh rate. All tests are deterministic: the clock is fed a synthetic
 * `now` Long ns timestamp starting from a fixed value and advanced by fixed per-frame intervals — no
 * real clock, no randomness beyond the seeded engine.
 *
 * Note: [DeepSeaAnimationClock.advance] treats `lastFrameNanos == 0L` as "no previous frame yet" and
 * contributes 0 time on its first call, so every scenario here primes the clock once with a fixed
 * non-zero timestamp before feeding real frames.
 */
class DeepSeaAnimationClockTest {

    /** Feed `seconds` of real time at `hz` frames/s; returns (updateCount, engine, dustCount). */
    private fun runForSeconds(hz: Long, seconds: Long, seed: Int): Triple<Long, DeepSeaParticleEngine, Int> {
        val clock = DeepSeaAnimationClock()
        val engine = DeepSeaParticleEngine(seed)
        val frameNanos = 1_000_000_000L / hz
        var now = 1_000_000_000L // fixed start timestamp (never hits the lastFrameNanos == 0L sentinel)
        clock.advance(now) // prime: records the baseline timestamp, contributes 0 steps / 0 time
        repeat((seconds * hz).toInt()) {
            now += frameNanos
            val steps = clock.advance(now)
            repeat(steps) { engine.step(true, true, .8f, false) }
        }
        return Triple(clock.updateCount, engine, engine.dust.size)
    }

    private fun assertDriftClose(a: DeepSeaParticleEngine, b: DeepSeaParticleEngine, eps: Float) {
        assertEquals(460, a.drift.size)
        assertEquals(a.drift.size, b.drift.size)
        for (i in a.drift.indices) {
            assertTrue("drift[$i].x: ${a.drift[i].x} vs ${b.drift[i].x}", abs(a.drift[i].x - b.drift[i].x) < eps)
            assertTrue("drift[$i].y: ${a.drift[i].y} vs ${b.drift[i].y}", abs(a.drift[i].y - b.drift[i].y) < eps)
        }
    }

    /** Same 10 s of wall-clock time at 60/90/120 Hz must run ≈600 fixed steps with a consistent state. */
    @Test fun update_count_is_rate_independent_over_10s() {
        val seed = 0x50A1D157
        val s60 = runForSeconds(60, 10, seed)
        val s90 = runForSeconds(90, 10, seed)
        val s120 = runForSeconds(120, 10, seed)
        for ((hz, r) in listOf(60L to s60, 90L to s90, 120L to s120)) {
            assertTrue("${hz}Hz updateCount=${r.first} should be ≈600", abs(r.first - 600L) <= 1L)
        }
        // Rate independence: the three refresh rates must land within one step of each other.
        assertTrue(abs(s60.first - s90.first) <= 1L)
        assertTrue(abs(s60.first - s120.first) <= 1L)
        // A ±1 step gap (frame-quantisation) is the only allowed difference, so drift positions are
        // within one step of motion and the dust pool (capped at 1250) is within a small tolerance.
        assertDriftClose(s60.second, s90.second, .02f)
        assertDriftClose(s60.second, s120.second, .02f)
        assertTrue(abs(s60.third - s90.third) <= 40)
        assertTrue(abs(s60.third - s120.third) <= 40)
    }

    /** A 5 s pause arriving as one huge delta must not be chased. */
    @Test fun pause_jump_is_bounded_and_not_chased() {
        val clock = DeepSeaAnimationClock()
        var now = 1_000_000_000L
        clock.advance(now) // prime
        val frameNanos = 1_000_000_000L / 60
        repeat(600) { now += frameNanos; clock.advance(now) }
        val beforeSteps = clock.updateCount
        val beforeElapsed = clock.elapsedSeconds
        now += 5_000_000_000L // 5 s pause = one huge delta
        val steps = clock.advance(now)
        assertTrue("steps=$steps must be ≤ maxStepsPerFrame=5", steps <= 5)
        assertTrue(clock.updateCount - beforeSteps <= 5)
        // Only the capped, accepted steps enter elapsedSeconds — the pause itself is not counted
        // (5 s of time would be ~300 steps; we accept ≪ 1 s instead).
        val gained = clock.elapsedSeconds - beforeElapsed
        assertTrue("elapsed gained $gained must be ≪ 5s", gained < 1.0)
        assertTrue(gained >= 0.0)
        assertTrue(clock.interpolationAlpha in 0f..1f)
    }

    /** A huge dt (beyond maxDtSeconds) drops the whole-step backlog but keeps the sub-step remainder. */
    @Test fun huge_backlog_discards_whole_steps_keeps_sub_step_remainder() {
        val clock = DeepSeaAnimationClock()
        var now = 1_000_000_000L
        clock.advance(now) // prime
        now += 10_000_000L // 0.01 s — a sub-step remainder below fixedStepSeconds
        assertEquals(0, clock.advance(now))
        assertTrue("accumulator ${clock.accumulator} should hold the 0.01s remainder", clock.accumulator > 0.0)
        assertTrue(clock.accumulator < clock.fixedStepSeconds)
        now += 10_000_000_000L // 10 s jump; dt clamps to maxDtSeconds = 0.25
        val steps = clock.advance(now)
        assertTrue("steps=$steps must be ≤ maxStepsPerFrame=5", steps <= 5)
        // Whole-step backlog is discarded; the <1-step remainder survives for interpolation.
        assertTrue("accumulator ${clock.accumulator} must be < fixedStep", clock.accumulator < clock.fixedStepSeconds)
        assertTrue(clock.interpolationAlpha in 0f..1f)
    }

    /** The calibrated real-device motion keeps a deterministic 60 Hz visual baseline. */
    @Test fun step_at_1_over_60_matches_calibrated_golden_fingerprint() {
        val engine = DeepSeaParticleEngine(0x50A1D157)
        repeat(600) { engine.step(true, true, .8f, false) } // default dt = 1/60 s
        assertEquals(239.14285751909483, engine.drift.sumOf { it.x.toDouble() }, 0.0)
        assertEquals(222.92434577044332, engine.drift.sumOf { it.y.toDouble() }, 0.0)
        assertEquals(0.31482565f, engine.drift[0].x, 0f)
        assertEquals(0.41718385f, engine.drift[0].y, 0f)
        assertEquals(5.784327E-4f, engine.drift[0].vx, 0f)
        assertEquals(-4.7983846E-4f, engine.drift[0].vy, 0f)
        assertEquals(0.5881558f, engine.drift[459].x, 0f)
        assertEquals(0.3135032f, engine.drift[459].y, 0f)
        assertEquals(117.32845f, engine.dust[0].x, 0f)
        assertEquals(31.266008f, engine.dust[0].y, 0f)
        assertEquals(54.09112f, engine.dust[0].life, 0f)
        assertEquals(1116, engine.dust.size)
    }

    /** Numeric assumptions underpinning the 60fps bit-identity guarantee of the step rewrite. */
    @Test fun dt_steps_multiplier_is_exactly_one_at_60fps() {
        assertEquals(1f, (1f / 60f) * 60f, 0f)
        assertEquals(0.982f, 0.982f.pow(1f), 0f)
        assertEquals(0.988f, 0.988f.pow(1f), 0f)
        assertEquals(0.44f, 0.44f * 1f, 0f)
        assertEquals(0.24f, 0.24f * 1f, 0f)
        assertEquals(64, (64f * 1f).toInt())
    }
}
