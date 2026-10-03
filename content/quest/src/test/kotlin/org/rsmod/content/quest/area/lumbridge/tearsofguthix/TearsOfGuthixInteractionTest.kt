package org.rsmod.content.quest.area.lumbridge.tearsofguthix

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.util.Wearpos
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.random.Random
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.HeldUInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DttdScenes
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.ZanikRevival
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.LIT_LANTERN
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.MAGIC_STONE
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.STONE_BOWL
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.UNLIT_LANTERN
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class TearsOfGuthixInteractionTest {
    @Test fun `Juna will not hear a story from an adventurer below 43 quest points`() {
        val f = Fixture(qp = 42)
        f.talkToJuna(1)
        assertEquals(0, f.stage())
        assertTrue(f.said("You need 43 quest points"), f.output())
    }

    @Test fun `telling Juna a story starts the quest and sets the multis' varbit`() {
        val f = Fixture()
        f.talkToJuna(1, 1)
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(STAGE_STARTED, f.player.vars["varbit.tog_juna_bowl"])
        assertTrue(f.said("Mine some stone from that cave"), f.output())
    }

    @Test fun `declining the quest leaves it unstarted`() {
        val f = Fixture()
        f.talkToJuna(1, 2)
        assertEquals(0, f.stage())
        assertTrue(f.said("Maybe you should come back when you do."), f.output())
    }

    @Test fun `a lit sapphire lantern lets a light creature carry the player across the chasm`() {
        val f = Fixture(stage = STAGE_STARTED)
        f.player.coords = LuxGrotto.NORTH_LANDING
        f.attract()
        assertEquals(LuxGrotto.NORTH_LANDING, f.player.coords, "no lantern, no ride")
        f.give(LIT_LANTERN)
        f.attract()
        assertEquals(LuxGrotto.SOUTH_LANDING, f.player.coords)
        f.attract()
        assertEquals(LuxGrotto.NORTH_LANDING, f.player.coords, "and back again")
    }

    @Test fun `the magical rocks need a pickaxe and 20 Mining`() {
        val f = Fixture(stage = STAGE_STARTED)
        f.player.coords = CoordGrid(3220, 9497, 2)
        f.mine()
        assertEquals(0, f.count(MAGIC_STONE))
        assertTrue(f.said("You need a Mining level of 20"), f.output())
        f.level("stat.mining", 20)
        f.mine()
        assertEquals(0, f.count(MAGIC_STONE))
        assertTrue(f.said("You need a pickaxe"), f.output())
        f.give("obj.bronze_pickaxe")
        f.mine()
        assertEquals(1, f.count(MAGIC_STONE), f.output())
    }

    @Test fun `the stone only becomes a bowl once Juna has heard a story`() {
        val f = Fixture()
        f.give("obj.chisel")
        f.give(MAGIC_STONE)
        f.use("obj.chisel", MAGIC_STONE)
        assertEquals(0, f.count(STONE_BOWL))
        f.jump(STAGE_STARTED)
        f.use("obj.chisel", MAGIC_STONE)
        assertEquals(1, f.count(STONE_BOWL))
        assertEquals(0, f.count(MAGIC_STONE))
    }

    @Test fun `using the magic stone on Juna gets a polite refusal`() {
        val f = Fixture(stage = STAGE_STARTED)
        f.give(MAGIC_STONE)
        f.useOnJuna(MAGIC_STONE)
        assertTrue(f.said("rather than with my face"), f.output())
    }

    @Test fun `giving Juna the bowl completes the quest once`() {
        val f = Fixture(stage = STAGE_STARTED)
        f.give(STONE_BOWL)
        f.talkToJuna()
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(0, f.count(STONE_BOWL))
        assertEquals(1000, f.player.statMap.getXP("stat.crafting"))
        assertEquals(44, f.player.vars["varp.qp"])
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
    }

    @Test fun `asking about the cave tells the light creature story`() {
        val f = Fixture(stage = STAGE_STARTED)
        f.talkToJuna(1)
        assertTrue(f.said("each drawn to its own colour"), f.output())
        assertEquals(STAGE_STARTED, f.stage())
    }

    @Test fun `a sapphire turns a bullseye lantern into a sapphire lantern that lights at 49 Firemaking`() {
        val f = Fixture()
        f.give("obj.bullseye_lantern_unlit")
        f.give("obj.sapphire")
        f.level("stat.crafting", 20)
        f.use("obj.sapphire", "obj.bullseye_lantern_unlit")
        assertEquals(1, f.count(UNLIT_LANTERN))
        assertEquals(1, f.count("obj.bullseye_lantern_lens"))
        f.give("obj.tinderbox")
        f.use("obj.tinderbox", UNLIT_LANTERN)
        assertEquals(1, f.count(UNLIT_LANTERN))
        f.level("stat.firemaking", 49)
        f.use("obj.tinderbox", UNLIT_LANTERN)
        assertEquals(1, f.count(LIT_LANTERN))
    }

    @Test fun `a finished player with free hands is let into the cave for a cycle per quest point`() {
        val f = Fixture(stage = STAGE_COMPLETE, qp = 50)
        f.talkToJuna(1)
        assertTrue(f.player.togCollecting, f.output())
        assertEquals(50, f.player.togCountdown)
        assertEquals(TearsCave.CHAMBER_ENTRY, f.player.coords)
        assertEquals(STONE_BOWL.asRSCM(), f.player.worn[Wearpos.RightHand.slot]?.id)
    }

    @Test fun `Juna asks for empty hands before letting the player in`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.player.worn[Wearpos.RightHand.slot] = InvObj("obj.bronze_sword", 1)
        f.talkToJuna(1)
        assertFalse(f.player.togCollecting)
        assertTrue(f.said("Perhaps you should empty your hands"), f.output())
    }

    @Test fun `blue streams add a tear and green streams take one away`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.talkToJuna(1)
        f.collect(CoordGrid(3258, 9520, 2))
        assertEquals(1, f.player.togTears, f.output())
        f.player.togTears = 3
        f.collect(CoordGrid(3258, 9514, 2))
        assertEquals(2, f.player.togTears)
        f.collect(CoordGrid(3261, 9517, 2))
        assertEquals(2, f.player.togTears, "dry walls give nothing")
    }

    @Test fun `the countdown runs out into a drink that rewards the weakest skill`() {
        val f = Fixture(stage = STAGE_COMPLETE, qp = 50)
        f.talkToJuna(1)
        f.player.togTears = 10
        repeat(50) { f.cave.tick(f.player) }
        assertEquals(0, f.player.togCountdown)
        f.drink()
        assertFalse(f.player.togCollecting)
        assertEquals(TearsCave.CAVE_EXIT, f.player.coords)
        assertEquals(100, f.player.statMap.getXP("stat.attack"), f.output())
        assertTrue(f.said("You feel a brief surge of aggression!"), f.output())
        assertNull(f.player.worn[Wearpos.RightHand.slot])
        assertEquals(10, f.player.togBestTears)
        assertEquals(TODAY, f.player.togLastVisitDay)
        assertEquals(50, f.player.togQpAtLastVisit)
    }

    @Test fun `leaving the cave early pays nothing and keeps the visit open`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.talkToJuna(1)
        f.player.togTears = 10
        f.player.coords = TearsCave.CAVE_EXIT
        f.cave.tick(f.player)
        f.drink()
        assertFalse(f.player.togCollecting)
        assertEquals(0, f.player.statMap.getXP("stat.attack"))
        assertEquals(0, f.player.togLastVisitDay)
        assertNull(f.player.worn[Wearpos.RightHand.slot])
    }

    @Test fun `Juna waits a week and for new adventures between visits`() {
        val f = Fixture(stage = STAGE_COMPLETE, qp = 50)
        f.rules.recordVisit(f.player)
        f.rules.today = { TODAY + 3 }
        f.talkToJuna(1)
        assertFalse(f.player.togCollecting)
        assertTrue(f.said("it is a poor sort of adventurer"), f.output())
        assertTrue(f.said("one quest point or 100,000 total XP"), f.output())
        f.story()
        assertTrue(f.said("Come again in 4 days if you have had more adventures by then."), f.output())
        VarPlayerIntMapSetter.set(f.player, "varp.qp", 51)
        f.story()
        assertTrue(f.said("It has not been long since your last visit. Come back in 4 days."), f.output())
        f.rules.today = { TODAY + 6 }
        f.story()
        assertTrue(f.said("Come back tomorrow."), f.output())
        f.rules.today = { TODAY + 7 }
        f.story()
        assertTrue(f.player.togCollecting, f.output())
    }

    @Test fun `100,000 experience counts as new adventures`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.rules.recordVisit(f.player)
        f.rules.today = { TODAY + 7 }
        assertFalse(f.rules.isEligible(f.player))
        f.player.statMap.setXP("stat.fishing", 99_999)
        assertFalse(f.rules.isEligible(f.player))
        f.player.statMap.setXP("stat.fishing", 100_000)
        assertTrue(f.rules.isEligible(f.player))
    }

    @Test fun `logging in mid-visit strips the bowl and ends the visit`() {
        val f = Fixture(stage = STAGE_COMPLETE)
        f.talkToJuna(1)
        f.cave.reset(f.player)
        assertFalse(f.player.togCollecting)
        assertNull(f.player.worn[Wearpos.RightHand.slot])
    }

    @Test fun `the reward skips skills the player cannot train yet`() = respectingProgress {
        val f = Fixture(stage = STAGE_COMPLETE)
        for (skill in TearsOfGuthixRules.SKILLS) f.player.statMap.setXP(skill.stat, 5_000)
        f.player.statMap.setXP("stat.herblore", 0)
        f.player.statMap.setXP("stat.runecrafting", 0)
        f.player.statMap.setXP("stat.construction", 0)
        f.player.statMap.setXP("stat.sailing", 0)
        f.player.statMap.setXP("stat.hunter", 100)
        assertEquals(TearsOfGuthixRules.Skill.Hunter, f.rules.rewardSkill(f.player))
    }

    @Test fun `experience per tear grows from 10 to 60 with the skill`() {
        assertEquals(10.0, TearsOfGuthixRules.xpPerTear(0))
        assertEquals(20.0, TearsOfGuthixRules.xpPerTear(2_700))
        assertEquals(60.0, TearsOfGuthixRules.xpPerTear(13_500))
        assertEquals(60.0, TearsOfGuthixRules.xpPerTear(5_000_000))
    }

    @Test fun `the streams keep three blue and three green on separate walls and move on a 16 cycle beat`() {
        val f = Fixture()
        val streams = f.streams
        var changes = 0
        streams.advance()
        var last = TearStreams.WALLS.map { streams.colourAt(it.coords) }
        repeat(160) {
            f.clock.tick()
            streams.advance()
            val now = TearStreams.WALLS.map { streams.colourAt(it.coords) }
            assertEquals(3, now.count { it == TearStreams.Colour.Blue })
            assertEquals(3, now.count { it == TearStreams.Colour.Green })
            changes += now.indices.count { now[it] != last[it] }
            last = now
        }
        assertEquals(60 * 2, changes)
        assertNotEquals(0, changes)
    }

    private fun respectingProgress(block: () -> Unit) {
        val old = QuestRequirements.activePolicy()
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
        try {
            block()
        } finally {
            QuestRequirements.install(old)
        }
    }

    private class Fixture(stage: Int = 0, qp: Int = 43) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val coroutine = GameCoroutine("tears-of-guthix-test")
        private var result: Result<Unit>? = null
        private val random = DefaultGameRandom(Random(5))
        val clock = MapClock(100)
        private val picks = ArrayDeque<Int>()
        private lateinit var regions: RegionRegistry
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getNpcInteractions = { NpcInteractions(events) },
            getLocInteractions = { LocInteractions(BoundValidator(collision), events) },
            getRandom = { random },
            getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
            getAreaChecker = { AreaChecker(regions, AreaIndex()) },
        )
        private val heldU = HeldUInteractions(events)
        private val locU = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)
        private val npcRepo: NpcRepository
        private val locRepo: LocRepository

        val rules = TearsOfGuthixRules().apply { today = { TODAY } }
        val quest = TearsOfGuthixQuest(rules)
        val streams: TearStreams
        val cave: TearsCave
        private val grotto = LuxGrotto(quest)

        @OptIn(InternalApi::class)
        val player = Player().apply {
            this.client = this@Fixture.client
            uuid = 4545L
            observerUUID = 4545L
            slotId = 1
            assignUid()
            coords = CoordGrid(3250, 9516, 2)
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
        }

        init {
            val updates = ZoneUpdateMap()
            val storage = LocZoneStorage()
            val normal = LocRegistryNormal(updates, collision, storage)
            val npcRegistry = NpcRegistry(npcs, collision, events)
            regions = RegionRegistry(RegionListSmall(), RegionListLarge(), RegionListWorldEntity(),
                normal, collision, storage, npcRegistry,
                ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
            locRepo = LocRepository(clock, LocRegistry(storage, normal,
                LocRegistryRegion(updates, collision, storage, regions)), regions)
            npcRepo = NpcRepository(clock, npcRegistry, npcs)
            for (x in 3200 until 3264 step 8) for (z in 9472 until 9536 step 8) collision.allocateIfAbsent(x, z, 2)
            streams = TearStreams(locRepo, random, clock)
            cave = TearsCave(rules, streams)
            val juna = Juna(quest, rules, cave)
            val revival = ZanikRevival(DeathToTheDorgeshuunQuest(), unsafe(), juna, cave)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(revival) { scripts.startup() }
            with(grotto) { scripts.startup() }
            with(TearsOfGuthixScript(quest, rules, juna, cave)) { scripts.startup() }
            VarPlayerIntMapSetter.set(player, "varp.qp", qp)
            if (stage > 0) jump(stage)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = quest.stage(player)

        fun jump(stage: Int) = quest.quest.jumpToStage(player, stage)

        fun give(obj: String) {
            player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, 1)
        }

        fun count(obj: String): Int = player.inv.count(obj)

        @OptIn(InternalApi::class)
        fun level(stat: String, level: Int) {
            player.statMap.setBaseLevel(stat, level.toByte())
            player.statMap.setCurrentLevel(stat, level.toByte())
        }

        fun said(text: String): Boolean = output().contains(text)

        fun talkToJuna(vararg options: Int) {
            picks += options.toList()
            locOp(TearsOfGuthixQuest.JUNA, JUNA_TILE, InteractionOp.Op1)
        }

        fun story(vararg options: Int) {
            picks += options.toList()
            locOp(TearsOfGuthixQuest.JUNA, JUNA_TILE, InteractionOp.Op2)
        }

        fun useOnJuna(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val juna = bound(TearsOfGuthixQuest.JUNA, JUNA_TILE)
            val locType = checkNotNull(ServerCacheManager.getObject(TearsOfGuthixQuest.JUNA.asRSCM()))
            val event = with(locU) { access().opTrigger(juna, juna, locType, type, slot) }
            val trigger = checkNotNull(event) { "No $obj handler on Juna" }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        fun attract() {
            val npc = Npc(LuxGrotto.LIGHT_CREATURE, player.coords.translateZ(-1))
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch { assertTrue(events.publish(this, NpcEvents.Op1(npc))) }
        }

        fun mine() = locOp(LuxGrotto.MAGICAL_ROCKS.first(), CoordGrid(3221, 9498, 2), InteractionOp.Op1)

        fun collect(wall: CoordGrid) = locOp("loc.tog_weepingwall", wall, InteractionOp.Op1)

        fun drink() = dispatch { with(cave) { drink() } }

        fun use(first: String, second: String) {
            val a = player.inv.indexOfFirst { it?.id == first.asRSCM() }
            val b = player.inv.indexOfLast { it?.id == second.asRSCM() }
            check(a >= 0 && b >= 0) { "missing $first or $second" }
            val typeA = checkNotNull(ServerCacheManager.getItem(first.asRSCM()))
            val typeB = checkNotNull(ServerCacheManager.getItem(second.asRSCM()))
            dispatch { heldU.interact(this, inv, typeA, a, typeB, b) }
        }

        private fun locOp(symbol: String, coords: CoordGrid, op: InteractionOp) {
            val loc = bound(symbol, coords)
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, op)
            val trigger = checkNotNull(event) { "No $op handler for $symbol" }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        private fun bound(symbol: String, coords: CoordGrid): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            return BoundLocInfo(LocInfo(coords.level, coords, LocEntity(type.id, 10, 1)), type)
        }

        private fun dispatch(block: suspend ProtectedAccess.() -> Unit) {
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val access = access()
            val start: suspend () -> Unit = { access.block() }
            start.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
            repeat(600) {
                if (coroutine.isIdle) {
                    picks.clear()
                    return
                }
                step()
                result?.getOrThrow()
            }
            fail<Unit>("Interaction did not finish: ${output()}")
        }

        private fun step() {
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val parent = listOf("chat_left", "chat_right", "messagebox", "chatmenu", "objectbox", "objectbox_double")
                    .firstOrNull { player.ui.containsModal("interface.$it") }
                    ?: error("Unknown dialogue: ${output()}")
                val input = when (parent) {
                    "chatmenu" -> ResumePauseButtonInput("component.chatmenu:options", picks.removeFirstOrNull() ?: 1)
                    "objectbox" -> ResumePauseButtonInput("component.objectbox:universe", -1)
                    "objectbox_double" -> ResumePauseButtonInput("component.objectbox_double:pausebutton", -1)
                    else -> ResumePauseButtonInput("component.$parent:continue", -1)
                }
                coroutine.resumeWith(input)
            } else {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                player.pendingSequence = EntitySeq.NULL
                player.pendingFaceAngle = EntityFaceAngle.NULL
                coroutine.advance()
            }
        }

        fun output() = client.messages.joinToString("\n").replace("<br>", " ")

        private inline fun <reified T> unsafe(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        const val TODAY = 20_000
        val JUNA_TILE = CoordGrid(3252, 9516, 2)

        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(240).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val old = field.get(null)
                restored += { field.set(null, old) }
            }
            val oldStorage = InvVirtualStorageHolder.instance
            restored += { InvVirtualStorageHolder.instance = oldStorage }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic @AfterAll fun restore() {
            restored.asReversed().forEach { it() }
            restored.clear()
        }
    }
}
