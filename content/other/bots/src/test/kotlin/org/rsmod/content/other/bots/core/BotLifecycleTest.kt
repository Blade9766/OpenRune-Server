package org.rsmod.content.other.bots.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BotLifecycleTest {
    private fun BotTestHarness.readyBot(config: BotConfig = BotTestHarness.woodcuttingConfig()): Long {
        val bot = createBot(config)
        coordinator.editSave(bot.id) { it.copy(inv = listOf(SavedObj(0, AXE, 1))) }
        return bot.id
    }

    @Test
    fun `bots stay out of the world until both the bot and the master toggle are on`() {
        val world = BotTestHarness()
        val id = world.readyBot()

        world.tick(3)
        assertFalse(world.coordinator.isLive(id))

        world.coordinator.enable(id)
        world.tick(3)
        assertFalse(world.coordinator.isLive(id), "master toggle is still off")

        world.coordinator.setMaster(true)
        world.tick()
        assertTrue(world.coordinator.isLive(id))
        assertEquals(BotState.TravelToResource, world.state(id))
    }

    @Test
    fun `disabling waits for the current action and keeps every item`() {
        val world = BotTestHarness()
        val id = world.readyBot()
        world.coordinator.setMaster(true)
        world.coordinator.enable(id)
        world.tickUntil { world.live(id) { it.held(LOGS) >= 5 } }
        val actor = world.actor(id)
        val logs = actor.total(LOGS)

        actor.busy = true
        world.coordinator.disable(id)
        world.tick(3)
        assertTrue(world.coordinator.isLive(id), "must not despawn mid-action")
        assertTrue(actor.stopped > 0)

        actor.busy = false
        world.tick()
        assertFalse(world.coordinator.isLive(id))
        assertEquals(BotState.Disabled, world.state(id))
        val saved = world.store.bots.getValue(id).save
        assertEquals(logs, saved.inv.filter { it.id == LOGS }.sumOf { it.count.toLong() })
        assertTrue(saved.inv.any { it.id == AXE })
    }

    @Test
    fun `a bot re-enabled with unnoted gathered items keeps gathering instead of listing them`() {
        val world = BotTestHarness()
        val id = world.readyBot()
        world.coordinator.setMaster(true)
        world.coordinator.enable(id)
        world.tickUntil { world.live(id) { it.held(LOGS) >= 5 } }
        world.coordinator.disable(id)
        world.tickUntil { !world.coordinator.isLive(id) }

        world.coordinator.enable(id)
        world.tickUntil { world.coordinator.isLive(id) }
        world.tick(20)

        assertTrue(world.state(id) in setOf(BotState.TravelToResource, BotState.Gathering))
        assertTrue(world.offers(id).isEmpty())
        assertTrue(world.actor(id).held(LOGS) >= 5)
    }

    @Test
    fun `a bot without a task visits its source, works the task, and comes back for another`() {
        val world = BotTestHarness(actorFactory = { FakeActor(taskSize = 5) })
        val id = world.readyBot()
        world.coordinator.setMaster(true)
        world.coordinator.enable(id)

        world.tickUntil { world.live(id) { it.sourceVisits >= 2 } }
        assertTrue(world.record(id).stats.gathered >= 5, "worked the first task before asking for another")
        assertTrue(world.state(id) != BotState.Error)
    }

    @Test
    fun `master toggle off removes all bots and the active cap is respected`() {
        val world = BotTestHarness()
        val ids = List(3) { world.readyBot() }
        ids.forEach { world.coordinator.enable(it) }
        world.coordinator.setMaxActive(2)
        world.coordinator.setMaster(true)
        world.tick()
        assertEquals(2, ids.count { world.coordinator.isLive(it) })

        world.coordinator.setMaster(false)
        world.tick()
        assertEquals(0, ids.count { world.coordinator.isLive(it) })
        assertTrue(ids.all { world.store.bots.getValue(it).enabled }, "enabled flags survive")
    }

    @Test
    fun `settings and enabled state survive a restart`() {
        val world = BotTestHarness()
        val id = world.readyBot()
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)
        world.coordinator.setMaxActive(5)
        world.tick()

        world.restart()
        assertEquals(BotGlobalSettings(masterEnabled = true, maxActiveBots = 5), world.coordinator.settings)
        world.tick()
        assertTrue(world.coordinator.isLive(id))
    }

    @Test
    fun `a full inventory is banked, tools are kept, and gathering resumes`() {
        val world = BotTestHarness()
        val id = world.readyBot()
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)

        world.tickUntil { world.state(id) == BotState.Banking }
        val actor = world.actor(id)
        assertEquals(27, actor.held(LOGS), "27 logs plus the axe fill the inventory")

        world.tickUntil { world.state(id) == BotState.TravelToResource }
        assertEquals(0, actor.held(LOGS))
        assertEquals(27, actor.bankCount(LOGS))
        assertEquals(1, actor.inv[AXE])
        assertEquals(27, world.coordinator.find("$id")!!.stats.deposited)

        world.tickUntil { world.state(id) == BotState.Gathering }
        world.tick(3)
        assertTrue(actor.held(LOGS) > 0)
    }

    @Test
    fun `missing tools park the bot in error instead of looping`() {
        val world = BotTestHarness()
        val id = world.createBot().id
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)
        world.tick()
        world.actor(id).missingTool = true
        world.tick(5)
        assertEquals(BotState.Error, world.state(id))
        world.tick(2000)
        assertEquals(BotState.Error, world.state(id), "non-recoverable errors need an admin")
    }

    @Test
    fun `a blocked path retries a bounded number of times then errors`() {
        val world = BotTestHarness()
        val id = world.readyBot()
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)
        world.tick()
        world.actor(id).blocked = setOf(BotDestination.Resource)

        world.tickUntil(limit = 100) { world.state(id) == BotState.Error }
        val recoveries = BotBrain.MAX_ERROR_RECOVERIES
        world.now += BotBrain.ERROR_COOLDOWN_MS * (recoveries + 2)
        world.tick(500)
        assertEquals(BotState.Error, world.state(id))
        val failures = world.log.lines.count { "TRAVEL_TO_RESOURCE failed" in it }
        assertTrue(failures <= BotBrain.MAX_STATE_FAILURES * (recoveries + 1), "failures=$failures")
    }

    @Test
    fun `depleted resources wait for respawn without failing`() {
        val world = BotTestHarness()
        val id = world.readyBot()
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)
        world.tickUntil { world.state(id) == BotState.Gathering }
        world.actor(id).resourcesAvailable = false
        world.tick(BotBrain.NO_RESOURCE_TICKS / 2)
        assertEquals(BotState.Gathering, world.state(id))
        world.actor(id).resourcesAvailable = true
        world.tick(5)
        assertEquals(BotState.Gathering, world.state(id))
    }

    @Test
    fun `hourly gathering limit pauses gathering`() {
        val world = BotTestHarness()
        val id = world.readyBot(BotTestHarness.woodcuttingConfig().copy(maxGatheredPerHour = 10))
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)
        world.tick(300)
        assertEquals(10, world.actor(id).held(LOGS))
        assertEquals(BotState.Idle, world.state(id))
    }

    @Test
    fun `runecrafting waits for supplies and resumes when stocked`() {
        val world =
            BotTestHarness(actorFactory = { FakeActor(gatheredItem = 556, tools = emptySet(), supplyItem = 1436, stackable = setOf(COINS, 556)) })
        val config =
            BotConfig(
                role = BotRoleType.Runecrafting,
                site = "air_altar",
                essenceSource = EssenceSource.Bank,
                sale = SaleConfig(enabled = false),
            )
        val id = world.createBot(config).id
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)

        world.tickUntil { world.state(id) == BotState.WaitingForSupplies }
        world.tick(BotBrain.SUPPLY_RECHECK_TICKS * 2)
        assertEquals(BotState.WaitingForSupplies, world.state(id))

        world.actor(id).bank[1436] = 50
        world.tickUntil(limit = 1000) { world.live(id) { it.bankCount(556) > 0 } }
        assertTrue(world.log.lines.any { "paused: no supplies" in it })
    }

    @Test
    fun `runecrafting can mine its own essence`() {
        val world =
            BotTestHarness(actorFactory = { FakeActor(gatheredItem = 556, tools = emptySet(), supplyItem = 1436, stackable = setOf(COINS, 556)) })
        val config =
            BotConfig(
                role = BotRoleType.Runecrafting,
                site = "air_altar",
                essenceSource = EssenceSource.Mine,
                sale = SaleConfig(enabled = false),
            )
        val id = world.createBot(config).id
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)
        world.tickUntil(limit = 2000) { world.live(id) { it.bankCount(556) >= 28 } }
        assertNotNull(world.actor(id))
    }

    @Test
    fun `restart resets in-flight work but keeps items, stats and errors`() {
        val world = BotTestHarness()
        val id = world.readyBot()
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)
        world.tickUntil { world.live(id) { it.bankCount(LOGS) > 0 } && world.state(id) == BotState.Gathering }
        world.tick(10)
        val persisted = world.store.bots.getValue(id)
        val persistedLogs = (persisted.save.inv + persisted.save.bank).filter { it.id == LOGS }.sumOf { it.count }

        world.restart()
        val loaded = world.coordinator.find("$id")!!
        assertEquals(BotState.Disabled, loaded.state)
        world.tick()
        assertTrue(world.coordinator.isLive(id))
        assertEquals(BotState.TravelToResource, world.state(id))
        assertEquals(persistedLogs.toLong(), world.actor(id).total(LOGS))
        assertEquals(persisted.stats, world.coordinator.find("$id")!!.stats)
    }

    @Test
    fun `config validation enforces the sale threshold range`() {
        val world = BotTestHarness()
        assertTrue(world.coordinator.create(BotTestHarness.woodcuttingConfig(threshold = 999), null, emptySet()).isFailure)
        assertTrue(world.coordinator.create(BotTestHarness.woodcuttingConfig(threshold = 2001), null, emptySet()).isFailure)
        val ok = world.createBot(BotTestHarness.woodcuttingConfig(threshold = 2000, quantity = 1500))
        assertTrue(world.coordinator.updateConfig(ok.id) { it.copy(sale = it.sale.copy(threshold = 500)) } != null)
        assertEquals(2000, world.coordinator.find("${ok.id}")!!.config.sale.threshold)
    }

    @Test
    fun `generated names are funny, short and never end in bot`() {
        val taken = HashSet<String>()
        repeat(200) {
            val name = BotNames.generate(taken)
            assertTrue(name.length <= 12, name)
            assertFalse(name.lowercase().endsWith("bot"), name)
            assertTrue(taken.add(name), "duplicate $name")
        }
        assertFalse(BotNames.isAcceptable("WoodBot"))
        assertFalse(BotNames.isAcceptable("Lumber bot"))
    }

    @Test
    fun `a bot that cannot spawn backs off instead of retrying every tick`() {
        val world = BotTestHarness()
        world.spawner.slotsAvailable = 0
        val id = world.readyBot()
        world.coordinator.enable(id)
        world.coordinator.setMaster(true)
        world.tick(150)
        assertFalse(world.coordinator.isLive(id))
        assertEquals(2, world.log.lines.count { "could not spawn" in it })
    }
}
