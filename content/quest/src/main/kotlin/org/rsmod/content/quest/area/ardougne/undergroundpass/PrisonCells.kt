package org.rsmod.content.quest.area.ardougne.undergroundpass

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.RAILING
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_DIG
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SPADE
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Below the Well of Iban the pass turns into a prison: rows of cells cut into the rock, a crate of
 * food left for the prisoners, and a pile of loose mud at the back of one cell that turns out to be
 * a filled-in tunnel.
 *
 * The cage round the unicorn further on has a railing lying loose on its floor, the only thing in
 * the pass long enough to lever the boulder above it with.
 */
@Singleton
class PrisonCells @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CELL_TUNNEL) { enterCells() }
        onOpLoc2(UNICORN_CAGE) { searchCage() }
        onOpLocU(LOOSE_MUD, SPADE) { digMud() }
        onOpLoc1(FOOD_CRATE) { searchFoodCrate() }
    }

    private suspend fun ProtectedAccess.enterCells() {
        arriveDelay()
        mes("You squeeze into the cell block.")
        delay(1)
        telejump(UpassCoords.CELL_TUNNEL_INSIDE)
    }

    private suspend fun ProtectedAccess.searchCage() {
        arriveDelay()
        anim(SEQ_SEARCH)
        mes("You search the cage...")
        delay(2)
        if (inv.contains(RAILING)) {
            mes("But find nothing.")
            return
        }
        if (invAdd(inv, RAILING).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        player.railingTaken = true
        mes("You find a loose railing lying on the floor.")
    }

    private suspend fun ProtectedAccess.digMud() {
        arriveDelay()
        anim(SEQ_DIG)
        mes("You dig into the pile of mud...")
        mes("...and find it's a filled in tunnel!")
        delay(2)
        player.mudDug = true
        mes("You push your way through the tunnel.")
        delay(1)
        telejump(UpassCoords.MUD_TUNNEL_EXIT)
    }

    private suspend fun ProtectedAccess.searchFoodCrate() {
        arriveDelay()
        anim(SEQ_SEARCH)
        mes("You search the crate...")
        delay(2)
        if (player.crateFood == 1) {
            mes("...but you find nothing.")
            return
        }
        UndergroundPassQuest.setVarBit(player, "varbit.upass_crate_food", 1)
        mes("...inside you find some food.")
        for (food in CRATE_FOOD) {
            invAddOrDrop(objRepo, food)
        }
    }

    private companion object {
        const val CELL_TUNNEL = "loc.cavewalltunnel_upass_tocells"
        const val UNICORN_CAGE = "loc.cave_railings3"
        const val LOOSE_MUD = "loc.upass_mud"
        const val FOOD_CRATE = "loc.cavefood1"

        val CRATE_FOOD = listOf("obj.salmon", "obj.salmon", "obj.meat_pie", "obj.meat_pie")
    }
}
