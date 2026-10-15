@file:OptIn(InternalApi::class)

package org.rsmod.content.skills.magic.lunar

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InventoryServerType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.CastResult
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.enumVarBit
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.autocast.MagicSpellbookManager
import org.rsmod.content.skills.construction.data.PlankType
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Plank Make and the hunter kit move items, so these prove that every log, coin and kit is
 * accounted for whether the cast succeeds or fails; plus Plank Make's prices and Spellbook Swap
 * handing the Lunar book back after one borrowed spell.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class LunarDreamSpellsTest {

    @Test fun `plank make swaps one log and 70 percent of the sawmill price for a plank`() {
        assertEquals(listOf(70, 175, 350, 1050), PlankType.entries.map { PlankMakeScript.cost(it) })
        val f = Fixture()
        f.give("obj.oak_logs", 2)
        f.give(COINS, 400)
        f.plankMake("obj.oak_logs")
        assertEquals(1, f.count("obj.oak_logs"))
        assertEquals(1, f.count("obj.plank_oak"))
        assertEquals(225, f.count(COINS))
    }

    @Test fun `plank make takes nothing when the coins, the runes or the log are missing`() {
        val f = Fixture()
        f.give("obj.mahogany_logs")
        f.give(COINS, 1049)
        f.plankMake("obj.mahogany_logs")
        assertEquals(1, f.count("obj.mahogany_logs"))
        assertEquals(1049, f.count(COINS))
        verify(exactly = 0) { f.runes.attemptCast(any(), any(), any()) }

        f.give(COINS, 1)
        f.castFails = true
        f.plankMake("obj.mahogany_logs")
        assertEquals(1, f.count("obj.mahogany_logs"), "no runes, no plank")
        assertEquals(1050, f.count(COINS))
        assertEquals(0, f.count("obj.plank_mahogany"))

        f.castFails = false
        f.give("obj.yew_logs")
        f.plankMake("obj.yew_logs")
        assertEquals(1, f.count("obj.yew_logs"), "the sawmill won't take yew")
        assertEquals(1050, f.count(COINS))
    }

    @Test fun `a log that is gone from its slot by the time the spell lands is not replaced`() {
        val f = Fixture()
        f.give("obj.logs")
        f.give(COINS, 70)
        val slot = f.slotOf("obj.logs")
        f.player.inv[slot] = null
        f.dispatch { with(f.plank) { plankMake(slot, f.type("obj.logs")) } }
        assertEquals(0, f.count("obj.woodplank"))
        assertEquals(70, f.count(COINS))
    }

    @Test fun `plank make works in a full inventory and can't be cast twice in one tick`() {
        val f = Fixture()
        f.give(COINS, 1000)
        f.give("obj.logs", 27)
        f.plankMake("obj.logs")
        f.plankMake("obj.logs")
        assertEquals(26, f.count("obj.logs"), "the second cast is still on cooldown")
        assertEquals(1, f.count("obj.woodplank"))
        assertEquals(930, f.count(COINS))
        assertEquals(27, f.count("obj.logs") + f.count("obj.woodplank"), "every log is accounted for")
    }

    @Test fun `the hunter kit opens only with room for everything inside`() {
        val f = Fixture()
        f.give(KIT)
        f.give("obj.bronze_sword", 21)
        f.heldOp1(KIT)
        assertEquals(1, f.count(KIT), "six free spaces are not enough")
        assertEquals(0, f.count("obj.noose_wand"))
        f.drop("obj.bronze_sword")
        f.give("obj.bronze_sword", 20)
        f.heldOp1(KIT)
        assertEquals(0, f.count(KIT))
        assertEquals(8, KIT_CONTENTS.sumOf { f.count(it) })
        assertTrue(f.player.inv.isFull())
    }

    @Test fun `humidify fills every vessel at once but needs one to cast`() {
        val f = Fixture()
        f.cast(LunarDreamSpell.Humidify)
        verify(exactly = 0) { f.runes.attemptCast(any(), any(), any()) }
        f.give("obj.vial_empty", 3)
        f.give("obj.bucket_empty")
        f.give("obj.clay")
        f.give("obj.watering_can_2")
        f.cast(LunarDreamSpell.Humidify)
        assertEquals(3, f.count("obj.vial_water"))
        assertEquals(1, f.count("obj.bucket_water"))
        assertEquals(1, f.count("obj.softclay"))
        assertEquals(1, f.count("obj.watering_can_8"))
        assertEquals(0, f.count("obj.vial_empty") + f.count("obj.clay"))
    }

    @Test fun `spellbook swap lends a book for one spell from it`() {
        val f = Fixture()
        f.player.spellbook = Spellbook.Lunars
        val swap = SpellbookSwap(MagicSpellbookManager(mockk(relaxed = true)))
        swap.lend(f.player, Spellbook.Standard)
        assertEquals(Spellbook.Standard, f.player.spellbook)
        swap.onCast(f.player, spellOf(Spellbook.Lunars))
        assertEquals(Spellbook.Standard, f.player.spellbook, "a Lunar spell doesn't use up the loan")
        swap.onCast(f.player, spellOf(Spellbook.Standard))
        assertEquals(Spellbook.Lunars, f.player.spellbook)
        assertFalse(swap.isSwapped(f.player))
        swap.lend(f.player, Spellbook.Ancients)
        swap.returnBook(f.player)
        assertEquals(Spellbook.Lunars, f.player.spellbook, "the timer and logout return it too")
    }

    private class Fixture {
        val events = EventBus()
        private val coroutine = GameCoroutine("lunar-spells-test")
        private val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events })
        var castFails = false
        val runes = mockk<MagicRuneManager>(relaxed = true)
        private val spells = mockk<MagicSpellRegistry>()
        val plank: PlankMakeScript
        private val dreamSpells: LunarDreamSpellsScript

        val player = Player().apply {
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            currentMapClock = 100
        }

        init {
            every { spells.getObjSpell(any()) } returns spellOf(Spellbook.Lunars)
            every { runes.canCastSpell(any(), any(), any()) } answers { !castFails }
            every { runes.attemptCast(any(), any(), any()) } answers {
                if (castFails) CastResult.Failure.NotEnoughInvObj else CastResult.Success.AllUnlimited
            }
            plank = PlankMakeScript(events, mockk(relaxed = true), spells, runes)
            dreamSpells = LunarDreamSpellsScript(spells, runes)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(dreamSpells) { scripts.startup() }
            with(plank) { scripts.startup() }
            with(SpellbookSwapScript(mockk(relaxed = true), spells, runes, emptySet())) { scripts.startup() }
        }

        fun type(obj: String) = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))

        fun give(obj: String, count: Int = 1) {
            if (type(obj).stackable) {
                val slot = slotOf(obj).takeIf { it >= 0 }
                val existing = slot?.let { player.inv[it]?.count } ?: 0
                player.inv[slot ?: player.inv.indexOfFirst { it == null }] = InvObj(obj, existing + count)
            } else {
                repeat(count) { player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, 1) }
            }
        }

        fun drop(obj: String) {
            for (slot in player.inv.objs.indices) {
                if (player.inv[slot]?.id == obj.asRSCM(RSCMType.OBJ)) player.inv[slot] = null
            }
        }

        fun slotOf(obj: String): Int = player.inv.indexOfFirst { it?.id == obj.asRSCM(RSCMType.OBJ) }

        fun count(obj: String): Int = player.inv.count(obj)

        fun plankMake(obj: String) {
            val slot = slotOf(obj)
            dispatch { with(plank) { plankMake(slot, type(obj)) } }
        }

        fun cast(spell: LunarDreamSpell) {
            val component = spell.component
            dispatch {
                val event = org.rsmod.api.player.ui.IfOverlayButton(
                    checkNotNull(ServerCacheManager.fromComponent(component.asRSCM(RSCMType.COMPONENT))),
                    -1, null, dev.openrune.types.aconverted.interf.IfButtonOp.Op1,
                )
                assertTrue(events.publish(this, event))
            }
        }

        fun heldOp1(obj: String) {
            val slot = slotOf(obj)
            dispatch {
                assertTrue(events.publish(this, org.rsmod.api.player.events.interact.HeldObjEvents.Op1(slot, checkNotNull(inv[slot]), type(obj), inv)))
            }
        }

        fun dispatch(block: suspend ProtectedAccess.() -> Unit) {
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, context)
            var result: Result<Unit>? = null
            val start: suspend () -> Unit = { access.block() }
            start.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { result.getOrThrow() }
            })
            repeat(10) {
                if (coroutine.isIdle) return
                coroutine.advance()
            }
        }
    }

    companion object {
        const val COINS = "obj.coins"
        const val KIT = "obj.dream_hunter_box"
        val KIT_CONTENTS =
            listOf(
                "obj.noose_wand", "obj.hunting_butterfly_net", "obj.hunting_ojibway_bird_snare", "obj.hunting_snare",
                "obj.hunting_teasing_stick", "obj.torch_unlit", "obj.hunting_box_trap", "obj.ii_impling_jar",
            )

        private var Player.spellbook by enumVarBit<Spellbook>("varbit.spellbook")

        fun spellOf(book: Spellbook): MagicSpell = mockk(relaxed = true) {
            every { spellbook } returns book
            every { castXp } returns 0.0
        }

        private val restored = mutableListOf<() -> Unit>()

        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(241).close()
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
