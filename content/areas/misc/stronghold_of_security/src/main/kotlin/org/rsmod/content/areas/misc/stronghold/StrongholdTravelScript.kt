package org.rsmod.content.areas.misc.stronghold

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Moving between the surface and the four levels: the entrance hole in the Barbarian Village
 * mine, the ladders joining the levels, the ropes and vines that lead back to a level's start,
 * and the shortcut portals to each treasure room.
 *
 * Landing tiles were read from the cache maps: every ladder and rope loc blocks its own tile, so
 * players land on the free tile beside it.
 */
class StrongholdTravelScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(Stronghold.ENTRANCE_LOC) { climbDown(Level.WAR.start) }

        // Vault of War: the start ladder and the treasure-room ladder both lead to the surface.
        onOpLoc1(Stronghold.WAR_LADDER_UP) { climbUp(Stronghold.SURFACE) }
        onOpLoc1(Stronghold.WAR_LADDER_DOWN) { climbDown(Level.FAMINE.start) }
        onOpLoc1(Stronghold.WAR_PORTAL) { portal(Level.WAR) }

        // Catacomb of Famine.
        onOpLoc1(Stronghold.FAMINE_LADDER_UP) { climbUp(Stronghold.WAR_LADDER_DOWN_LANDING) }
        onOpLoc1(Stronghold.FAMINE_LADDER_DOWN) { climbDown(Level.PESTILENCE.start) }
        onOpLoc1(Stronghold.FAMINE_ROPE_UP) { climbUp(Level.FAMINE.start, "You climb the rope back to the start of the level.") }
        onOpLoc1(Stronghold.FAMINE_PORTAL) { portal(Level.FAMINE) }

        // Pit of Pestilence.
        onOpLoc1(Stronghold.PESTILENCE_LADDER_UP) { climbUp(Stronghold.FAMINE_LADDER_DOWN_LANDING) }
        onOpLoc1(Stronghold.PESTILENCE_LADDER_DOWN) { climbDown(Level.DEATH.start) }
        onOpLoc1(Stronghold.PESTILENCE_VINE_UP) { climbUp(Level.PESTILENCE.start, "You climb the goo covered vine back to the start of the level.") }
        onOpLoc1(Stronghold.PESTILENCE_PORTAL) { portal(Level.PESTILENCE) }

        // Sepulchre of Death.
        onOpLoc1(Stronghold.DEATH_LADDER_UP) { climbUp(Stronghold.PESTILENCE_LADDER_DOWN_LANDING) }
        onOpLoc1(Stronghold.DEATH_ROPE_UP) { climbUp(Level.DEATH.start, "You climb the rope back to the start of the level.") }
        onOpLoc1(Stronghold.DEATH_PORTAL) { portal(Level.DEATH) }
    }

    private suspend fun ProtectedAccess.climbDown(dest: CoordGrid) {
        arriveDelay()
        anim(Stronghold.CLIMB_DOWN_ANIM)
        delay(1)
        telejump(dest)
    }

    private suspend fun ProtectedAccess.climbUp(dest: CoordGrid, message: String? = null) {
        arriveDelay()
        anim(Stronghold.CLIMB_ANIM)
        delay(1)
        telejump(dest)
        if (message != null) {
            mes(message)
        }
    }

    /**
     * The portal at a level's start skips to its treasure room for anyone who has claimed the
     * level's reward, or is experienced enough not to need the lesson.
     */
    private suspend fun ProtectedAccess.portal(level: Level) {
        arriveDelay()
        val allowed = level.isCompleted(player) || player.combatLevel >= level.portalCombatLevel
        if (!allowed) {
            mes(
                "You need a combat level of at least ${level.portalCombatLevel}, or to have " +
                    "claimed this level's reward, to use this portal."
            )
            return
        }
        mes("You enter the portal to be whisked through to the treasure room.")
        delay(1)
        telejump(level.treasure)
    }
}
