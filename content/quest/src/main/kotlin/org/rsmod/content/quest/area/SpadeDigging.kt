package org.rsmod.content.quest.area

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The spade's own "Dig" option. Only one script may own it, so quests that have something buried
 * register the tile here from their own `startup` instead; anywhere else the spade just turns over
 * the soil.
 */
@Singleton
class SpadeDigging @Inject constructor() : PluginScript() {
    private val sites = mutableListOf<DigSite>()

    fun register(tile: CoordGrid, radius: Int = 1, handler: suspend ProtectedAccess.() -> Unit) {
        sites += DigSite(tile, radius, handler)
    }

    override fun ScriptContext.startup() {
        onOpHeld1(SPADE) { dig() }
    }

    private suspend fun ProtectedAccess.dig() {
        val site =
            sites.firstOrNull {
                player.coords.level == it.tile.level &&
                    player.coords.chebyshevDistance(it.tile) <= it.radius
            }
        if (site == null) {
            anim(DIG_SEQ)
            soundSynth(DIG_SOUND)
            delay(1)
            mes("You dig, but find nothing of interest.")
            return
        }
        site.handler(this)
    }

    private class DigSite(
        val tile: CoordGrid,
        val radius: Int,
        val handler: suspend ProtectedAccess.() -> Unit,
    )

    private companion object {
        const val SPADE = "obj.spade"
        const val DIG_SEQ = "seq.human_dig"
        const val DIG_SOUND = "synth.digspade"
    }
}
