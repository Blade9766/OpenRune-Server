package org.rsmod.content.skills.construction.scripts

import dev.openrune.ParamReferences.param
import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.aliases.ParamObj
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.enumVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.skills.crafting.interfaces.MakeQuantityColumn
import org.rsmod.content.skills.crafting.interfaces.makeQuantity
import org.rsmod.content.skills.crafting.interfaces.selectMakeQuantity
import org.rsmod.content.skills.crafting.interfaces.setMakeQuantity
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Making magic tablets at a study lectern.
 *
 * The interface is the real `teletabs_craft_if`, drawn entirely by its clientscripts from two vars:
 * [Player.teletabEnum] names the list of tablets the lectern offers and [Player.tabletToMake] the
 * selected row. Each tablet points at its spell through `spell_parent_obj`, so the level, runes,
 * spellbook and experience all come from the spell itself.
 */
class LecternScript
@Inject
constructor(private val spells: MagicSpellRegistry, private val runes: MagicRuneManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for ((lectern, tablets) in LECTERNS) {
            onOpLoc1(lectern) { study(tablets) }
            onOpLoc2(lectern) { createLast(tablets) }
        }
        for (row in 1..MAX_ROWS) {
            onIfModalButton(tabComponent(row)) { player.tabletToMake = row }
        }
        for (quantity in MAKE_COLUMN.buttons) {
            onIfModalButton(MAKE_COLUMN.component(quantity.button)) {
                selectMakeQuantity(MAKE_COLUMN, quantity)
            }
        }
        onIfModalButton(MAKE_COLUMN.someButton) {}
        onIfModalButton(CONFIRM) { confirm() }
        onPlayerQueueWithArgs<TabletTask>(MAKE_QUEUE) { makeNext(it.args) }
    }

    private fun ProtectedAccess.study(tablets: String) {
        player.teletabEnum = tablets.asRSCM(RSCMType.ENUM)
        setMakeQuantity(inv.count(SOFT_CLAY))
        ifOpenMainModal(INTERFACE)
        for (row in 1..MAX_ROWS) {
            ifSetEvents(tabComponent(row), -1..-1, IfEvent.Op1)
        }
        ifSetEvents(CONFIRM, -1..-1, IfEvent.Op1)
    }

    private fun ProtectedAccess.confirm() {
        val tablet = tabletsOf(player.teletabEnum).getOrNull(player.tabletToMake - 1) ?: return
        ifClose()
        start(tablet, makeQuantity())
    }

    private fun ProtectedAccess.createLast(tablets: String) {
        val last = player.lastTablet
        val tablet = tabletsOf(tablets.asRSCM(RSCMType.ENUM)).firstOrNull { it.id == last }
        if (tablet == null) {
            mes("You haven't made any tablets at this lectern yet.")
            return
        }
        start(tablet, player.lastTabletAmount.coerceAtLeast(1))
    }

    private fun ProtectedAccess.start(tablet: ItemServerType, amount: Int) {
        val spell = spellOf(tablet)
        if (spell == null) {
            mes("You can't make that tablet yet.")
            return
        }
        if (!canMake(spell)) {
            return
        }
        player.lastTablet = tablet.id
        player.lastTabletAmount = amount
        anim(ANIM)
        strongQueue(MAKE_QUEUE, MAKE_CYCLE, TabletTask(tablet.id, amount))
    }

    private fun ProtectedAccess.makeNext(task: TabletTask) {
        val tablet = ServerCacheManager.getItem(task.tablet) ?: return
        val spell = spellOf(tablet) ?: return
        if (!make(tablet, spell)) {
            resetAnim()
            return
        }
        val remaining = task.remaining - 1
        if (remaining <= 0 || !canMake(spell)) {
            return
        }
        anim(ANIM)
        weakQueue(MAKE_QUEUE, MAKE_CYCLE, task.copy(remaining = remaining))
    }

    private fun ProtectedAccess.canMake(spell: MagicSpell): Boolean {
        if (SOFT_CLAY !in inv) {
            mes("You need some soft clay to make a magic tablet.")
            return false
        }
        if (spell.spellbook != player.spellbook) {
            mes("You need to be using the spellbook this tablet's spell is from to make it.")
            return false
        }
        return runes.canCastSpell(player, spell)
    }

    private fun ProtectedAccess.make(tablet: ItemServerType, spell: MagicSpell): Boolean {
        if (SOFT_CLAY !in inv) {
            mes("You need some soft clay to make a magic tablet.")
            return false
        }
        if (runes.attemptCast(player, spell).isFailure()) {
            return false
        }
        invDel(inv, SOFT_CLAY, strict = true)
        invAdd(inv, RSCM.getReverseMapping(RSCMType.OBJ, tablet.id))
        statAdvance(MAGIC, spell.castXp)
        return true
    }

    private fun spellOf(tablet: ItemServerType): MagicSpell? {
        val parent = tablet.paramOrNull(SPELL_PARENT) ?: return null
        return spells.getObjSpell(parent)
    }

    private data class TabletTask(val tablet: Int, val remaining: Int)

    internal companion object {
        const val INTERFACE = "interface.teletabs_craft_if"
        const val CONFIRM = "component.teletabs_craft_if:confirm"
        const val MAKE_QUEUE = "queue.poh_lectern_make"
        const val ANIM = "seq.poh_create_magic_tablet"
        const val SOFT_CLAY = "obj.softclay"
        const val MAGIC = "stat.magic"

        /** Ticks one tablet takes. */
        const val MAKE_CYCLE = 4

        /** Rows the interface has room for. */
        const val MAX_ROWS = 21

        val SPELL_PARENT: ParamObj = param("spell_parent_obj")

        val LECTERNS: Map<String, String> =
            mapOf(
                "loc.poh_lectern_1" to "enum.poh_lectern_oak",
                "loc.poh_lectern_2" to "enum.poh_lectern_eagle",
                "loc.poh_lectern_3" to "enum.poh_lectern_demon",
                "loc.poh_lectern_4" to "enum.poh_lectern_teak_eagle",
                "loc.poh_lectern_5" to "enum.poh_lectern_teak_demon",
                "loc.poh_lectern_6" to "enum.poh_lectern_mahogany_eagle",
                "loc.poh_lectern_7" to "enum.poh_lectern_mahogany_demon",
            )

        val MAKE_COLUMN =
            MakeQuantityColumn(
                component = { "component.teletabs_craft_if:$it" },
                countedObj = SOFT_CLAY,
                stepX = 0,
                stepY = 40,
            )

        fun tabletsOf(enum: Int): List<ItemServerType> {
            val values = ServerCacheManager.getEnum(enum)?.values ?: return emptyList()
            return values.entries
                .sortedBy { it.key }
                .mapNotNull { ServerCacheManager.getItem((it.value as Number).toInt()) }
        }

        fun tabComponent(row: Int): String = "component.teletabs_craft_if:tab_$row"

        private var Player.teletabEnum by intVarp("varp.teletab_enum")
        private var Player.tabletToMake by intVarBit("varbit.tablet_to_make")
        private var Player.lastTablet by intVarp("varp.teletab_last_crafted")
        private var Player.lastTabletAmount by intVarp("varp.teletab_last_crafted_amount")
        private val Player.spellbook by enumVarBit<Spellbook>("varbit.spellbook")
    }
}
