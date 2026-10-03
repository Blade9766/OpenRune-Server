package org.rsmod.content.quest.area.burthorpe.eadgarsruse

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.DRIED_THISTLE
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.GROUND_THISTLE
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.HERBLORE_REQ
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.PESTLE_AND_MORTAR
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.RANARR_UNF
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_MAKE_POTION
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.THISTLE
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.THISTLE_NPC
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.TROLL_POTION
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The troll truth potion: a thistle picked from the grass near Eadgar's cave, dried over a fire,
 * ground with a pestle and mortar and added to a ranarr potion (unf).
 *
 * The ranarr potion carries Herblore's own item-on-item default, which the dispatcher tries before
 * the reversed pair, so every combination here is registered with the potion as the first item.
 */
class TrollThistle @Inject constructor(private val eadgarsRuse: EadgarsRuseQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(THISTLE_NPC) { pick() }
        for (fire in FIRES) {
            onOpLocU(fire, THISTLE) { dry() }
        }
        onOpHeldU(PESTLE_AND_MORTAR, DRIED_THISTLE) { grind() }
        onOpHeldU(RANARR_UNF, THISTLE) { mes("I need to dry it over a fire first.") }
        onOpHeldU(RANARR_UNF, DRIED_THISTLE) { mes("It's too big to fit in the vial.") }
        onOpHeldU(RANARR_UNF, GROUND_THISTLE) { mix() }
    }

    private suspend fun ProtectedAccess.pick() {
        arriveDelay()
        val needed =
            eadgarsRuse.stage(player) == STAGE_MAKE_POTION &&
                THISTLE_STAGES.none { player.ownsAnywhere(it) }
        if (!needed) {
            mes("You have no reason to pick that.")
            return
        }
        if (inv.freeSpace() < 1) {
            mes("You don't have enough room to carry that.")
            return
        }
        anim(PICK_SEQ)
        invAdd(inv, THISTLE)
        mes("You pick the Troll Thistle.")
    }

    private suspend fun ProtectedAccess.dry() {
        arriveDelay()
        anim(COOK_SEQ)
        delay(DRY_TICKS)
        invDel(inv, THISTLE)
        invAdd(inv, DRIED_THISTLE)
        mes("You dry the troll thistle over the fire.")
    }

    private fun ProtectedAccess.grind() {
        invDel(inv, DRIED_THISTLE)
        invAdd(inv, GROUND_THISTLE)
        mes("You grind the Troll Thistle.")
    }

    private fun ProtectedAccess.mix() {
        if (player.herbloreLvl < HERBLORE_REQ) {
            mes("You need a Herblore level of at least $HERBLORE_REQ to make this potion.")
            return
        }
        invDel(inv, GROUND_THISTLE)
        invDel(inv, RANARR_UNF)
        invAdd(inv, TROLL_POTION)
        mes("You add the ground Troll Thistle to the potion.")
    }

    private companion object {
        val FIRES = listOf("loc.fire", "loc.forestry_fire", "loc.troll_stronghold_camp_fire")
        val THISTLE_STAGES = listOf(THISTLE, DRIED_THISTLE, GROUND_THISTLE, TROLL_POTION)
        const val PICK_SEQ = "seq.human_pickupfloor"
        const val COOK_SEQ = "seq.human_cooking"
        const val DRY_TICKS = 2
    }
}
