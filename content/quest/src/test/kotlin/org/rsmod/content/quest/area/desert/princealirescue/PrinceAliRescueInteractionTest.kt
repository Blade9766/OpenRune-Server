package org.rsmod.content.quest.area.desert.princealirescue

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.ASHES
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.BALL_OF_WOOL
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.BEER
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.BLOND_WIG
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.BRONZE_BAR
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.BUCKET_OF_WATER
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.COINS
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.HASSAN
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.JOE
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.KEY
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.KEY_GIVEN
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.KEY_MADE
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.KEY_NONE
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.KEY_PRINT
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.LADY_KELI
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.LEELA
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.OSMAN
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.PINK_SKIRT
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.POT_OF_FLOUR
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.PRINCE_ALI_CELL
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.PRINCE_ALI_PALACE
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.QUEST_KEY
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.REDBERRIES
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.ROPE
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.SKIN_PASTE
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.SOFT_CLAY
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_ALI_ESCAPED
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_JOE_DRUNK
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_KELI_TIED
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_PREPARED
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.WIG
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.YELLOW_DYE
import org.rsmod.content.quest.area.desert.princealirescue.npcs.Hassan
import org.rsmod.content.quest.area.desert.princealirescue.npcs.Joe
import org.rsmod.content.quest.area.desert.princealirescue.npcs.LadyKeli
import org.rsmod.content.quest.area.desert.princealirescue.npcs.Leela
import org.rsmod.content.quest.area.desert.princealirescue.npcs.Osman
import org.rsmod.content.quest.area.desert.princealirescue.npcs.PrinceAli
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.npcs.Aggie
import org.rsmod.content.quest.manager.QUEST_STAGE_MAP_ATTR
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Drives the quest's real scripts through the event bus: the whole rescue from Hassan to the
 * reward, every refusal along the way (no clay, no bronze bar, too few beers, tying Keli up too
 * early, a Prince without his disguise), the lost-key replacement, save and reload and reset.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PrinceAliRescueInteractionTest {

    @Test fun `the full rescue completes the quest and pays out once`() = respectingProgress {
        val f = Fixture()
        assertFalse(QuestRequirements.hasCompleted(f.player, QUEST_KEY))

        f.choose(HASSAN_HELP, 1)
        f.talk(HASSAN)
        assertEquals(STAGE_STARTED, f.stage())
        assertTrue(f.journal().contains("Osman"))

        f.choose(OSMAN_LEAVE)
        f.talk(OSMAN)
        assertEquals(STAGE_BRIEFED, f.stage())
        assertTrue(f.said("abandoned jail just east of Draynor Village"))

        f.imprintKey()
        assertEquals(1, f.count(KEY_PRINT))
        assertEquals(0, f.count(SOFT_CLAY))
        assertTrue(f.journal().contains("imprint"))

        f.give(BRONZE_BAR)
        f.choose(OSMAN_LEAVE)
        f.talk(OSMAN)
        assertEquals(KEY_MADE, f.quest.keyState(f.player))
        assertEquals(0, f.count(KEY_PRINT))
        assertEquals(0, f.count(BRONZE_BAR))

        f.choose(LEELA_LEAVE)
        f.talk(LEELA)
        assertEquals(KEY_GIVEN, f.quest.keyState(f.player))
        assertEquals(1, f.count(KEY))
        assertEquals(STAGE_BRIEFED, f.stage())

        f.makeDisguise()
        assertEquals(1, f.count(BLOND_WIG))
        assertEquals(1, f.count(SKIN_PASTE))

        f.talk(LEELA)
        assertEquals(STAGE_PREPARED, f.stage())
        assertTrue(f.said("deal with his personal guard"))

        f.give(BEER, 3)
        f.choose(1)
        f.talk(JOE)
        assertEquals(STAGE_JOE_DRUNK, f.stage())
        assertEquals(0, f.count(BEER))

        f.give(ROPE)
        f.npcU(LADY_KELI, ROPE)
        assertEquals(STAGE_KELI_TIED, f.stage())
        assertEquals(0, f.count(ROPE))
        assertTrue(f.said("tie her up"))

        f.talk(PRINCE_ALI_CELL)
        assertEquals(STAGE_ALI_ESCAPED, f.stage())
        assertEquals(0, f.count(BLOND_WIG))
        assertEquals(0, f.count(SKIN_PASTE))
        assertEquals(0, f.count(PINK_SKIRT))

        f.talk(HASSAN)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(3, f.player.vars["varp.qp"])
        assertEquals(700, f.count(COINS))
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        assertTrue(QuestRequirements.hasCompleted(f.player, QUEST_KEY))

        f.talk(HASSAN)
        assertEquals(700, f.count(COINS))
        assertEquals(3, f.player.vars["varp.qp"])
        f.talk(PRINCE_ALI_PALACE)
        assertTrue(f.said("forever in your debt"))
    }

    @Test fun `declining Hassan leaves the quest unstarted`() {
        val f = Fixture()
        f.choose(HASSAN_HELP, 2)
        f.talk(HASSAN)
        assertEquals(0, f.stage())
        f.choose(4)
        f.talk(HASSAN)
        assertEquals(0, f.stage())
    }

    @Test fun `the quest npcs keep to themselves before the briefing`() {
        val f = Fixture(STAGE_STARTED)
        f.give(SOFT_CLAY)
        f.talk(LADY_KELI)
        assertTrue(f.said("Clear off then."))
        f.talk(LEELA)
        assertTrue(f.said("That is no concern of yours"))
        assertEquals(1, f.count(SOFT_CLAY))
        assertEquals(STAGE_STARTED, f.stage())
    }

    @Test fun `keli shows the key but no imprint is taken without soft clay`() = respectingProgress {
        val f = Fixture(STAGE_BRIEFED)
        f.choose(1, 1, 2, 1)
        f.talk(LADY_KELI)
        assertTrue(f.said("Keli shows you a small key"))
        assertEquals(0, f.count(KEY_PRINT))
        assertTrue(f.quest.keliRecruit(f.player))
        f.give(SOFT_CLAY)
        f.choose(2, 1, 1)
        f.talk(LADY_KELI)
        assertTrue(f.said("Hello again!"))
        assertEquals(1, f.count(KEY_PRINT))
    }

    @Test fun `osman keeps the imprint until a bronze bar comes with it`() {
        val f = Fixture(STAGE_BRIEFED)
        f.give(KEY_PRINT)
        f.choose(OSMAN_LEAVE)
        f.talk(OSMAN)
        assertTrue(f.said("Bring me a bronze bar"))
        assertEquals(1, f.count(KEY_PRINT))
        assertEquals(KEY_NONE, f.quest.keyState(f.player))
    }

    @Test fun `leela waits for the whole disguise and a dyed wig`() {
        val f = Fixture(STAGE_BRIEFED)
        f.quest.setMetLeela(f.player)
        f.quest.setKeyState(f.player, KEY_GIVEN)
        f.give(KEY)
        f.give(WIG)
        f.give(SKIN_PASTE)
        f.give(PINK_SKIRT)
        f.choose(LEELA_LEAVE)
        f.talk(LEELA)
        assertEquals(STAGE_BRIEFED, f.stage())
        f.give(YELLOW_DYE)
        f.use(YELLOW_DYE, WIG)
        assertEquals(1, f.count(BLOND_WIG))
        f.talk(LEELA)
        assertEquals(STAGE_PREPARED, f.stage())
    }

    @Test fun `a lost key is replaced for fifteen coins`() {
        val f = Fixture(STAGE_PREPARED)
        f.quest.setMetLeela(f.player)
        f.quest.setKeyState(f.player, KEY_GIVEN)
        f.talk(LEELA)
        assertTrue(f.said("I haven't got that much."))
        assertEquals(0, f.count(KEY))
        f.give(COINS, 20)
        f.talk(LEELA)
        assertEquals(1, f.count(KEY))
        assertEquals(5, f.count(COINS))
        f.talk(LEELA)
        assertEquals(1, f.count(KEY))
        assertEquals(5, f.count(COINS))
    }

    @Test fun `one beer is not enough to get joe drunk`() {
        val f = Fixture(STAGE_PREPARED)
        f.give(BEER, 2)
        f.choose(1)
        f.talk(JOE)
        assertEquals(STAGE_PREPARED, f.stage())
        assertEquals(1, f.count(BEER))
        assertTrue(f.said("at least two more"))
    }

    @Test fun `keli cannot be tied up before joe is drunk or without the disguise`() {
        val f = Fixture(STAGE_PREPARED)
        f.give(ROPE)
        f.npcU(LADY_KELI, ROPE)
        assertEquals(STAGE_PREPARED, f.stage())
        assertEquals(1, f.count(ROPE))
        assertTrue(f.said("You cannot tie Keli up"))

        f.jump(STAGE_JOE_DRUNK)
        f.npcU(LADY_KELI, ROPE)
        assertEquals(STAGE_JOE_DRUNK, f.stage())
        assertEquals(1, f.count(ROPE))

        f.giveDisguiseAndKey()
        f.talk(LADY_KELI)
        assertTrue(f.said("I'm here to tie you up!"))
        assertEquals(STAGE_KELI_TIED, f.stage())
        assertEquals(0, f.count(ROPE))
    }

    @Test fun `the prince stays put without his disguise`() {
        val f = Fixture(STAGE_KELI_TIED)
        f.give(KEY)
        f.give(BLOND_WIG)
        f.talk(PRINCE_ALI_CELL)
        assertEquals(STAGE_KELI_TIED, f.stage())
        assertEquals(1, f.count(BLOND_WIG))
        assertTrue(f.said("I'll be back once I have it."))
    }

    @Test fun `aggie and ned only make the disguise during the quest`() {
        val f = Fixture()
        assertFalse(f.makers.offers(f.player))
        f.jump(STAGE_BRIEFED)
        assertTrue(f.makers.offers(f.player))
        f.jump(STAGE_ALI_ESCAPED)
        assertFalse(f.makers.offers(f.player))
    }

    @Test fun `aggie lists the paste ingredients until she has them all`() {
        val f = Fixture(STAGE_BRIEFED)
        f.give(ASHES)
        f.give(POT_OF_FLOUR)
        f.choose(2)
        f.talk(AGGIE)
        assertTrue(f.said("ash, flour and water"))
        assertEquals(0, f.count(SKIN_PASTE))
        assertEquals(1, f.count(ASHES))
    }

    @Test fun `save and reload keeps the key and the stage`() {
        val f = Fixture(STAGE_BRIEFED)
        f.quest.setKeyState(f.player, KEY_MADE)
        f.quest.setMetLeela(f.player)
        val loaded = f.saveAndReload()
        assertEquals(STAGE_BRIEFED, f.quest.stage(loaded))
        assertEquals(KEY_MADE, f.quest.keyState(loaded))
        assertTrue(f.quest.metLeela(loaded))
        assertEquals(STAGE_BRIEFED, loaded.vars["varp.princequest"])
    }

    @Test fun `resetting the quest clears its flags`() {
        val f = Fixture(STAGE_BRIEFED)
        f.quest.setKeyState(f.player, KEY_GIVEN)
        f.quest.setMetLeela(f.player)
        f.quest.setKeliRecruit(f.player)
        f.quest.quest.resetQuest(f.player)
        assertEquals(0, f.stage())
        assertEquals(KEY_NONE, f.quest.keyState(f.player))
        assertFalse(f.quest.metLeela(f.player))
        assertFalse(f.quest.keliRecruit(f.player))
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val coroutine = GameCoroutine("prince-ali-test")
        private var result: Result<Unit>? = null
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getNpcInteractions = { NpcInteractions(events) },
        )
        private val clock = MapClock(100)
        val npcRepo: NpcRepository
        private val picks = ArrayDeque<Int>()

        @OptIn(InternalApi::class)
        val player = Player().apply {
            this.client = this@Fixture.client
            uuid = 7171L
            observerUUID = 7171L
            slotId = 1
            assignUid()
            coords = TILE
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
        }

        val quest = PrinceAliRescueQuest()
        val makers = DisguiseMakers(quest)

        init {
            val updates = ZoneUpdateMap()
            val storage = LocZoneStorage()
            val normal = LocRegistryNormal(updates, collision, storage)
            val npcRegistry = NpcRegistry(npcs, collision, events)
            RegionRegistry(RegionListSmall(), RegionListLarge(), RegionListWorldEntity(),
                normal, collision, storage, npcRegistry,
                ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
            npcRepo = NpcRepository(clock, npcRegistry, npcs)
            for (x in TILE.x - 8..TILE.x + 16 step 8) {
                for (z in TILE.z - 8..TILE.z + 16 step 8) collision.allocateIfAbsent(x, z, 0)
            }
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(quest) { scripts.startup() }
            with(Hassan(quest)) { scripts.startup() }
            with(Osman(quest)) { scripts.startup() }
            with(Leela(quest)) { scripts.startup() }
            with(LadyKeli(quest)) { scripts.startup() }
            with(Joe(quest)) { scripts.startup() }
            with(PrinceAli(quest)) { scripts.startup() }
            with(Aggie(GoblinDiplomacyQuest(), makers)) { scripts.startup() }
            if (stage > 0) quest.quest.jumpToStage(player, stage)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = quest.stage(player)

        fun jump(stage: Int) = quest.quest.jumpToStage(player, stage)

        fun journal(): String = quest.questLog(access())

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun give(obj: String, count: Int = 1) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            if (type.stackable) {
                val slot = player.inv.indexOfFirst { it?.id == type.id }
                if (slot >= 0) {
                    player.inv[slot] = InvObj(obj, checkNotNull(player.inv[slot]).count + count)
                    return
                }
                player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, count)
                return
            }
            repeat(count) { player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, 1) }
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun said(text: String): Boolean = output().contains(text)

        fun giveDisguiseAndKey() {
            give(KEY)
            give(BLOND_WIG)
            give(SKIN_PASTE)
            give(PINK_SKIRT)
        }

        fun imprintKey() {
            give(SOFT_CLAY)
            choose(1, 1, 2, 1, 1)
            talk(LADY_KELI)
        }

        fun makeDisguise() {
            give(BALL_OF_WOOL, 3)
            choose(2, 1)
            val ned = Npc(NED, player.coords.translateX(1))
            npcRepo.add(ned, Int.MAX_VALUE)
            dispatch { startDialogue(ned) { with(makers) { nedOtherThings() } } }
            assertEquals(1, count(WIG))
            assertEquals(0, count(BALL_OF_WOOL))
            give(YELLOW_DYE)
            use(YELLOW_DYE, WIG)
            give(ASHES)
            give(POT_OF_FLOUR)
            give(BUCKET_OF_WATER)
            give(REDBERRIES)
            choose(2, 1)
            talk(AGGIE)
            assertEquals(0, count(ASHES))
            assertEquals(0, count(POT_OF_FLOUR))
            assertEquals(0, count(BUCKET_OF_WATER))
            assertEquals(0, count(REDBERRIES))
            give(PINK_SKIRT)
        }

        fun talk(type: String) {
            val npc = Npc(type, player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch { assertTrue(events.publish(this, NpcEvents.Op1(npc))) }
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun npcU(type: String, obj: String) {
            val npc = Npc(type, player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val npcType = checkNotNull(ServerCacheManager.getNpc(type.asRSCM()))
            dispatch { assertTrue(events.publish(this, NpcUEvents.Op(npc, slot, objType, npcType))) }
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun use(first: String, second: String) {
            val a = player.inv.indexOfFirst { it?.id == first.asRSCM() }
            val b = player.inv.indexOfFirst { it?.id == second.asRSCM() }
            val typeA = checkNotNull(ServerCacheManager.getItem(first.asRSCM()))
            val typeB = checkNotNull(ServerCacheManager.getItem(second.asRSCM()))
            dispatch { assertTrue(events.publish(this, HeldUEvents.Type(typeA, a, typeB, b))) }
        }

        fun saveAndReload(): Player {
            val loaded = Player()
            loaded.vars.backing.putAll(player.vars.backing)
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            quest.quest.syncState(loaded)
            return loaded
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
        val TILE = CoordGrid(3120, 3250, 0)

        const val NED = "npc.ned"
        const val AGGIE = "npc.aggie"

        const val HASSAN_HELP = 1
        const val OSMAN_LEAVE = 3
        const val LEELA_LEAVE = 3

        private val restored = mutableListOf<() -> Unit>()

        private fun respectingProgress(block: () -> Unit) {
            val previous = QuestRequirements.activePolicy()
            QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
            try {
                block()
            } finally {
                QuestRequirements.install(previous)
            }
        }

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
