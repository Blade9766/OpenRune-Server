package org.rsmod.content.quest.area.ardougne.undergroundpass

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_BALANCE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LADDER
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_LEDGE
import org.rsmod.content.quest.area.wilderness.magearena.nearestFree
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Iban's lair: a cavern with a pit through the middle of it, crossed on what is left of the
 * walkways his people built, and the two shafts that join it to the dwarves' camp below.
 *
 * The same two cave types are spawned on copies of this map that belong to other quests, so each
 * shaft only moves anyone standing on one of the four tiles the pass actually uses.
 */
@Singleton
class IbansLair
@Inject
constructor(private val collision: CollisionFlagMap) : PluginScript() {

    override fun ScriptContext.startup() {
        for (bridge in BROKEN_BRIDGES) {
            onOpLoc1(bridge) { crossBrokenBridge(it.loc) }
        }
        onOpLoc1(SHAFT_DOWN) { useShaft(it.loc, descending = true) }
        onOpLoc1(SHAFT_UP) { useShaft(it.loc, descending = false) }
    }

    private suspend fun ProtectedAccess.crossBrokenBridge(loc: BoundLocInfo) {
        arriveDelay()
        val dest = collision.nearestFree(acrossFrom(loc), CROSS_RADIUS)
        if (dest == null) {
            mes("There is nothing left of the bridge on that side.")
            return
        }
        soundSynth(SOUND_LEDGE)
        mes("You pick your way across what is left of the bridge.")
        climbOver(dest, SEQ_BALANCE, CROSS_TICKS)
    }

    private suspend fun ProtectedAccess.useShaft(loc: BoundLocInfo, descending: Boolean) {
        arriveDelay()
        val ideal = SHAFTS[loc.coords]
        val dest = ideal?.let { collision.nearestFree(it, CROSS_RADIUS) }
        if (dest == null) {
            mes("The shaft is choked with rubble.")
            return
        }
        anim(SEQ_LADDER)
        soundSynth(CLIMB_SOUND)
        delay(2)
        telejump(dest)
        mes(if (descending) "You climb down the shaft." else "You climb up the shaft.")
    }

    private companion object {
        val BROKEN_BRIDGES = arrayOf("loc.bridgecollapsed1", "loc.bridgecollapsed2")
        const val SHAFT_DOWN = "loc.cavewalltunnel_upass_down"
        const val SHAFT_UP = "loc.cavewalltunnel_upass_up"
        const val CLIMB_SOUND = "synth.ropeclimb"
        const val CROSS_TICKS = 3
        const val CROSS_RADIUS = 3

        /** The two shafts of the pass, keyed by the tile the cave mouth itself sits on. */
        val SHAFTS =
            mapOf(
                UpassCoords.LAIR_SHAFT_SOUTH to UpassCoords.CAMP_SHAFT_SOUTH_LANDING,
                UpassCoords.CAMP_SHAFT_SOUTH to UpassCoords.LAIR_SHAFT_SOUTH_LANDING,
                UpassCoords.LAIR_SHAFT_NORTH to UpassCoords.CAMP_SHAFT_NORTH_LANDING,
                UpassCoords.CAMP_SHAFT_NORTH to UpassCoords.LAIR_SHAFT_NORTH_LANDING,
            )
    }
}
