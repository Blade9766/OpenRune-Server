package org.rsmod.content.quest.area.ardougne.undergroundpass

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LADDER
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LONGJUMP
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_JUMP
import org.rsmod.content.quest.area.wilderness.magearena.nearestFree
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Iban's lair: platforms over a deep pit, joined by what is left of the bridges his people built,
 * and the two shafts down to the dwarves' camp below.
 *
 * Each broken bridge is a jump. A slip drops the player all the way down into the caverns under
 * the lair, which is a long fall.
 */
@Singleton
class IbansLair
@Inject
constructor(private val collision: CollisionFlagMap, private val random: GameRandom) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (bridge in BROKEN_BRIDGES) {
            onOpLoc1(bridge) { crossBrokenBridge(it.loc) }
        }
        onOpLoc1(SHAFT_DOWN) { useShaft(it.loc) }
        onOpLoc1(SHAFT_UP) { useShaft(it.loc) }
    }

    private suspend fun ProtectedAccess.crossBrokenBridge(bridge: BoundLocInfo) {
        arriveDelay()
        mes("You attempt to walk over the remaining bridge...")
        delay(1)
        if (!statRandom("stat.agility", JUMP_LOW, JUMP_HIGH, 0)) {
            telejump(UpassCoords.LAIR_FALLS[random.of(0, 1)], TeleportType.Exempt)
            mes("... but you slip and tumble into the darkness.")
            say("Ouch!")
            takeInstantHit(HitType.Typeless, stat("stat.hitpoints") * 25 / 100 + 4)
            return
        }
        val dest = collision.nearestFree(acrossFrom(bridge), LANDING_RADIUS)
        if (dest == null) {
            mes("There is nothing left of the bridge on that side.")
            return
        }
        soundSynth(SOUND_JUMP)
        climbOver(dest, SEQ_LONGJUMP, ticks = 2, startDelay = JUMP_START_CYCLES)
        mes("... you manage to cross safely.")
    }

    private suspend fun ProtectedAccess.useShaft(shaft: BoundLocInfo) {
        arriveDelay()
        val dest = SHAFTS[shaft.coords]
        if (dest == null) {
            mes("The shaft is choked with rubble.")
            return
        }
        anim(SEQ_LADDER)
        delay(1)
        telejump(dest)
    }

    private companion object {
        val BROKEN_BRIDGES = arrayOf("loc.bridgecollapsed1", "loc.bridgecollapsed2")
        const val SHAFT_DOWN = "loc.cavewalltunnel_upass_down"
        const val SHAFT_UP = "loc.cavewalltunnel_upass_up"
        const val JUMP_LOW = 90
        const val JUMP_HIGH = 300
        const val JUMP_START_CYCLES = 20
        const val LANDING_RADIUS = 3

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
