package org.rsmod.content.skills.farming.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.skills.farming.data.FarmingPatch
import org.rsmod.content.skills.farming.data.FarmingPatches
import org.rsmod.content.skills.farming.data.PatchKind
import org.rsmod.content.skills.farming.state.FarmingStore
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Pushes patch state to the client.
 *
 * Old School reuses a handful of `farming_transmit_*` varbits across the whole map, so the server
 * only ever sets the ones belonging to the scene the player is standing in. Every varbit the
 * registry touches is rewritten on each refresh - the ones no longer backing a visible patch get
 * reset - otherwise a value left behind in one region would redecorate whatever patch inherits that
 * varbit in the next.
 */
class FarmingTransmitScript @Inject constructor(private val store: FarmingStore) : PluginScript() {
    private val transmitVarbits: Set<String> = FarmingPatches.ALL.mapTo(HashSet(), FarmingPatch::varbit)

    override fun ScriptContext.startup() {
        onPlayerLogin { player.refresh() }
        onPlayerCoordsChanged {
            if (player.coords.zoneChangedFrom(lastKnownCoords)) {
                player.refresh()
            }
        }
    }

    private fun Player.refresh() {
        val states = store.refreshAll(this)
        val visible = FarmingPatches.visibleFrom(coords)
        val written = HashSet<String>(visible.size)
        for (patch in visible) {
            val value = states[patch.loc]?.varbitValue() ?: PatchKind.WEEDS_HEAVY
            VarPlayerIntMapSetter.set(this, patch.varbit, value)
            written += patch.varbit
        }
        for (varbit in transmitVarbits) {
            if (varbit !in written && vars[varbit] != 0) {
                VarPlayerIntMapSetter.set(this, varbit, 0)
            }
        }
    }

    private fun CoordGrid.zoneChangedFrom(other: CoordGrid): Boolean =
        level != other.level || (x shr 3) != (other.x shr 3) || (z shr 3) != (other.z shr 3)
}
