package org.rsmod.content.areas.guilds

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Holds players outside each guild to its entry requirements. A player let through is walked
 * across and the door shuts behind them, so it is never left open for someone else to follow.
 */
class GuildEntranceScript @Inject constructor(private val passages: GenericPassageScript) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (entrance in GuildEntrances.all) {
            for (loc in entrance.locs) {
                onOpLoc1(loc) { enter(entrance, loc, it.vis, it.type) }
            }
        }
        onOpLoc1(BRASS_KEY_DOOR) { brassKeyDoor(it.vis, it.type) }
    }

    private suspend fun ProtectedAccess.enter(
        entrance: GuildEntrance,
        name: String,
        door: BoundLocInfo,
        type: ObjectServerType,
    ) {
        if (entrance.inside(coords)) {
            with(passages) { walkThrough(door, type) }
            return
        }
        if (!entrance.canEnter(player)) {
            arriveDelay()
            entrance.refuse(this, name)
            return
        }
        entrance.onEnter?.invoke(this)
        with(passages) { walkThrough(door, type) }
    }

    /** The shed west of the Cooks' Guild, over the way down into Edgeville Dungeon. */
    private suspend fun ProtectedAccess.brassKeyDoor(door: BoundLocInfo, type: ObjectServerType) {
        if (coords.z < door.coords.z && BRASS_KEY !in inv) {
            arriveDelay()
            mes("This door is locked.")
            return
        }
        with(passages) { walkThrough(door, type) }
    }

    private companion object {
        const val BRASS_KEY_DOOR = "loc.brasskeydoor"
        const val BRASS_KEY = "obj.edgevilledungeonkey"
    }
}
