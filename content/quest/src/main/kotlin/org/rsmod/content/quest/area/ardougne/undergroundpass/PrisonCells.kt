package org.rsmod.content.quest.area.ardougne.undergroundpass

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.RAILING
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_DIG
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SPADE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_BRIDGE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_WELL
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Below the Well of Iban the pass stops being a cave and starts being a prison: rows of cells cut
 * into the rock, a crate of food gone hard as stone, and a wall of loose mud at the back of one of
 * them that is the only way out of the block.
 *
 * The cages further on are where the railing comes from, and that railing is the only thing in the
 * pass long enough to lever a boulder with.
 */
@Singleton
class PrisonCells
@Inject
constructor(private val quest: UndergroundPassQuest, private val random: GameRandom) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(WELL) { climbDownWell() }
        onOpLoc1(CELL_TUNNEL) { enterCells() }
        onOpLoc2(CAGE) { searchCage() }
        onOpLocU(LOOSE_MUD, SPADE) { digMud() }
        onOpLoc1(LOOSE_MUD) { probeMud() }
        onOpLoc1(FOOD_CRATE) { searchFoodCrate() }
    }

    private suspend fun ProtectedAccess.climbDownWell() {
        arriveDelay()
        if (quest.stage(player) < STAGE_BRIDGE || player.orbsBurnt < UndergroundPassQuest.ORB_COUNT) {
            mesbox(
                "The shaft goes down further than the light does, and something down there is " +
                    "still awake. While the orbs are burning there is no getting past it.",
            )
            return
        }
        mes("You lower yourself into the well.")
        anim(UndergroundPassQuest.SEQ_LADDER)
        soundSynth(CLIMB_SOUND)
        delay(2)
        telejump(UpassCoords.WELL_LANDING)
        quest.advanceTo(this, STAGE_WELL)
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
        delay(1)
        if (player.railingTaken && !invContains(inv, RAILING)) {
            mes("The rest of the bars are set fast in the stone.")
            return
        }
        if (player.railingTaken) {
            mes("You already have a length of railing.")
            return
        }
        if (invAdd(inv, RAILING).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        player.railingTaken = true
        mesbox(
            "One of the bars has rusted through at the foot. It comes away in your hands: a good " +
                "three feet of iron, and heavy enough to lever with.",
        )
    }

    private suspend fun ProtectedAccess.probeMud() {
        arriveDelay()
        mes("The back of the cell is packed mud rather than stone. A spade would go through it.")
    }

    private suspend fun ProtectedAccess.digMud() {
        arriveDelay()
        anim(SEQ_DIG)
        soundSynth(DIG_SOUND)
        delay(DIG_TICKS)
        if (!player.mudDug) {
            player.mudDug = true
            mesbox("You dig through the mud and out into a passage on the far side.")
        }
        telejump(UpassCoords.MUD_TUNNEL_EXIT)
    }

    private suspend fun ProtectedAccess.searchFoodCrate() {
        arriveDelay()
        anim(SEQ_SEARCH)
        delay(1)
        if (player.crateFood == 1) {
            mes("There is nothing left in the crate but mould.")
            return
        }
        val food = CRATE_FOOD[random.of(0, CRATE_FOOD.size - 1)]
        if (invAdd(inv, food).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        UndergroundPassQuest.setVarBit(player, "varbit.upass_crate_food", 1)
        mes("Whoever kept the prisoners fed left some of it behind.")
    }

    private companion object {
        const val WELL = "loc.cave_well"
        const val CELL_TUNNEL = "loc.cavewalltunnel_upass_tocells"
        const val CAGE = "loc.cave_railings3"
        const val LOOSE_MUD = "loc.upass_mud"
        const val FOOD_CRATE = "loc.cavefood1"

        const val CLIMB_SOUND = "synth.ropeclimb"
        const val DIG_SOUND = "synth.digspade"
        const val DIG_TICKS = 4

        val CRATE_FOOD = arrayOf("obj.bread", "obj.cooked_meat", "obj.stew")
    }
}
