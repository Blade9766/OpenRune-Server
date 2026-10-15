package org.rsmod.content.quest.area.taverley.druidicritual

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest.Companion.SUIT_OF_ARMOUR
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The prison door in front of the Cauldron of Thunder, and the two suits of armour standing
 * either side of it.
 *
 * Anyone who tries the door from the corridor wakes one suit instead of opening it: the statue
 * steps out of its alcove as a level 19 guard and sets about the player. Only once both are awake
 * does the door itself swing. From inside the room the door always opens, so a player who is done
 * with the cauldron is never locked in.
 */
class PrisonDoor
@Inject
constructor(
    private val passages: GenericPassageScript,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    private val armourStatue: ObjectServerType by lazy {
        ServerCacheManager.getObject(ARMOUR_STATUE.asRSCM(RSCMType.LOC))
            ?: error("Missing loc: $ARMOUR_STATUE")
    }

    override fun ScriptContext.startup() {
        onOpLoc1(DOOR_RIGHT) { door(it.loc, it.type) }
        onOpLoc1(DOOR_LEFT) { door(it.loc, it.type) }
    }

    private suspend fun ProtectedAccess.door(loc: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        faceLoc(loc)
        if (coords.x < loc.coords.x && wakeGuard()) {
            return
        }
        with(passages) { passage(loc, type, opIndex = 0) }
    }

    /** Wakes the first statue still standing, if any; true when one of them came to life. */
    private fun ProtectedAccess.wakeGuard(): Boolean {
        val statue = STATUE_TILES.firstNotNullOfOrNull { locRepo.findExact(it, armourStatue) }
        if (statue == null) {
            return false
        }
        locRepo.del(statue, AWAKE_TICKS)
        mes("Suddenly the suit of armour comes to life!")
        val type =
            ServerCacheManager.getNpc(SUIT_OF_ARMOUR.asRSCM(RSCMType.NPC))
                ?: error("Missing npc: $SUIT_OF_ARMOUR")
        val guard = Npc(type, statue.coords)
        npcRepo.add(guard, AWAKE_TICKS)
        guard.opPlayer2(player, aiInteractions)
        return true
    }

    private companion object {
        const val DOOR_RIGHT = "loc.cauldrondoor"
        const val DOOR_LEFT = "loc.cauldrondoor_l"
        const val ARMOUR_STATUE = "loc.suitofarmour_darkknight"

        /** The alcoves either side of the doorway, north one first. */
        val STATUE_TILES = listOf(CoordGrid(2887, 9832, 0), CoordGrid(2887, 9829, 0))

        const val AWAKE_TICKS = 500
    }
}
