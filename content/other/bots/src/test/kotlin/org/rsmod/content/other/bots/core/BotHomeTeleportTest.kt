package org.rsmod.content.other.bots.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BotHomeTeleportTest {
    private fun BotTestHarness.startSeller(): Long {
        val id = createBot(BotTestHarness.woodcuttingConfig()).id
        coordinator.editSave(id) {
            it.copy(inv = listOf(SavedObj(0, AXE, 1)), bank = listOf(SavedObj(0, LOGS, 1000)))
        }
        coordinator.enable(id)
        coordinator.setMaster(true)
        return id
    }

    @Test
    fun `a held exchange trip keeps the bot gathering and banking until it lifts`() {
        val world = BotTestHarness()
        val id = world.startSeller()
        world.tick()
        world.actor(id).exchangeHold = "Home Teleport recharges in 20 min"
        val gatheredBefore = world.record(id).stats.gathered

        world.tick(600)

        assertTrue(world.offers(id).isEmpty(), "no listing while the trip is held")
        assertTrue(world.record(id).stats.gathered > gatheredBefore, "kept working")
        assertFalse(world.state(id) == BotState.Error, world.log.lines.takeLast(10).toString())
        assertTrue(world.coordinator.trace(id)!!.decisions.any { "Sale waits: Home Teleport recharges in 20 min" in it.text })

        world.actor(id).exchangeHold = null
        world.tickUntil { world.offers(id).isNotEmpty() }
    }

    @Test
    fun `waiting on the way to the exchange never times out or fails`() {
        val world = BotTestHarness()
        val id = world.startSeller()
        world.tick()
        world.actor(id).waitingOn = BotDestination.Exchange
        world.tickUntil { world.state(id) == BotState.TravelToExchange }

        world.tick(BotBrain.TRAVEL_BUDGET_TICKS * 3)

        assertEquals(BotState.TravelToExchange, world.state(id))
        assertEquals("waiting to travel to Exchange", world.record(id).activity)
        assertFalse(world.log.lines.any { "failed" in it }, world.log.lines.takeLast(10).toString())

        world.actor(id).waitingOn = null
        world.tickUntil { world.offers(id).isNotEmpty() }
    }
}
