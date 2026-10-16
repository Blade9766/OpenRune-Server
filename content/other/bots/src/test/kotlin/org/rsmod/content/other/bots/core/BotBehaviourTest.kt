package org.rsmod.content.other.bots.core

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BotBehaviourTest {
    private fun BotTestHarness.gatheringBot(reaction: IntRange): Long {
        val config =
            BotTestHarness.woodcuttingConfig()
                .copy(reactionMinTicks = reaction.first, reactionMaxTicks = reaction.last)
        val bot = createBot(config)
        coordinator.editSave(bot.id) { it.copy(inv = listOf(SavedObj(0, AXE, 1))) }
        coordinator.enable(bot.id)
        coordinator.setMaster(true)
        tickUntil { state(bot.id) == BotState.Gathering }
        return bot.id
    }

    @Test
    fun `a bot with no reaction delay clicks again on the next tick`() {
        val world = BotTestHarness()
        val actor = world.actor(world.gatheringBot(0..0))
        val before = actor.gatherCalls

        world.tick(10)

        assertEquals(10, actor.gatherCalls - before)
    }

    @Test
    fun `a fixed reaction delay spaces clicks out by that many ticks`() {
        val world = BotTestHarness()
        val actor = world.actor(world.gatheringBot(2..2))
        world.tick(3)
        val before = actor.gatherCalls

        world.tick(30)

        assertEquals(10, actor.gatherCalls - before, "one click every 3 ticks: 2 waiting, 1 acting")
    }

    @Test
    fun `a bot already carrying out a click is not delayed`() {
        val world = BotTestHarness()
        val actor = world.actor(world.gatheringBot(5..5))
        actor.acting = true
        val before = actor.gatherCalls

        world.tick(6)

        assertEquals(6, actor.gatherCalls - before)
    }

    @Test
    fun `random reaction delays stay inside the configured range`() {
        val world = BotTestHarness()
        val actor = world.actor(world.gatheringBot(1..4))
        world.tick(5)
        val before = actor.gatherCalls

        world.tick(200)

        val clicks = actor.gatherCalls - before
        assertTrue(clicks in 200 / 5..200 / 2, "between one click per 5 ticks and one per 2, was $clicks")
    }

    @Test
    fun `the trace records each decision with its reason`() {
        val world = BotTestHarness()
        val id = world.gatheringBot(0..0)

        val trace = world.coordinator.trace(id)!!.decisions.map { it.text }

        assertTrue(trace.any { "TRAVEL_TO_RESOURCE -> GATHERING" in it }, trace.toString())
        assertTrue(trace.size <= 12)
    }

    @Test
    fun `an invalid reaction range is rejected`() {
        val config = BotTestHarness.woodcuttingConfig()

        assertTrue(config.copy(reactionMinTicks = 5, reactionMaxTicks = 2).validate().isNotEmpty())
        assertTrue(config.copy(reactionMinTicks = 0, reactionMaxTicks = 21).validate().isNotEmpty())
        assertTrue(config.copy(reactionMinTicks = 0, reactionMaxTicks = 0).validate().isEmpty())
    }

    @Test
    fun `targeting prefers a free resource over a slightly nearer busy one`() {
        val distances = mapOf("busy" to 1, "free" to 4)
        val crowds = mapOf("busy" to 1, "free" to 0)

        val picked = BotTargeting.pick(distances.keys.toList(), Random(1), distances::getValue, crowds::getValue)

        assertEquals("free", picked)
    }

    @Test
    fun `targeting still shares a busy resource when every free one is far away`() {
        val distances = mapOf("busy" to 1, "free" to BotTargeting.CROWD_COST + 5)
        val crowds = mapOf("busy" to 1, "free" to 0)

        val picked = BotTargeting.pick(distances.keys.toList(), Random(1), distances::getValue, crowds::getValue)

        assertEquals("busy", picked)
    }

    @Test
    fun `bots facing equal choices spread across them`() {
        val trees = listOf("a", "b", "c", "d")
        val random = Random(42)

        val picks = (1..200).map { BotTargeting.pick(trees, random, { 3 }, { 0 }) }.toSet()

        assertEquals(trees.toSet(), picks)
    }
}
