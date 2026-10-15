package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc4
import org.rsmod.content.skills.construction.data.Topiary
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Clipping the superior garden's topiary bush.
 *
 * The owner, holding secateurs, clips the bush into the shape of any boss on the wiki's list they
 * have killed at least once - by that boss's kill count - or back into a plain bush. The shape is
 * kept with the owner and the house is put back together showing it.
 */
class TopiaryScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val houses: HouseAccess,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (loc in Topiary.LOCS) {
            onOpLoc4(loc) { clip() }
        }
    }

    private suspend fun ProtectedAccess.clip() {
        val house = registry.houseAt(player.coords) ?: return
        if (house.owner !== player) {
            mes("Only the owner of this house can clip the topiary.")
            return
        }
        if (SECATEURS.none { inv.contains(it) }) {
            mes("You need a pair of secateurs to clip the bush.")
            return
        }
        val current = player.vars[Topiary.VARP]
        val shapes = Topiary.Shape.entries.filter { player.vars[it.kills] > 0 && it.ordinal + 1 != current }
        val options = shapes.map { it.label to it.ordinal + 1 } + if (current != 0) listOf(PLAIN to 0) else emptyList()
        if (options.isEmpty()) {
            mes("You need to have defeated a boss before you can clip the bush into its shape.")
            return
        }
        val shape = pick(options) ?: return
        anim(CLIP_SEQ)
        delay(CLIP_TICKS)
        VarPlayerIntMapSetter.set(player, Topiary.VARP, shape)
        mes(if (shape == 0) "You clip the bush back into shape." else "You clip the bush into a new shape.")
        houses.rebuild(this)
    }

    /** A paged chat menu over [options]; More... cycles the pages and Cancel gives up. */
    private suspend fun ProtectedAccess.pick(options: List<Pair<String, Int>>): Int? {
        val pages = options.chunked(PAGE_SIZE)
        var page = 0
        while (true) {
            val shown = pages[page]
            val labels = shown.map { it.first } + if (pages.size > 1) MORE else CANCEL
            val index =
                when (labels.size) {
                    2 -> choice2(labels[0], 0, labels[1], 1, title = TITLE)
                    3 -> choice3(labels[0], 0, labels[1], 1, labels[2], 2, title = TITLE)
                    4 -> choice4(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, title = TITLE)
                    else -> choice5(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, labels[4], 4, title = TITLE)
                }
            if (index < shown.size) {
                return shown[index].second
            }
            if (pages.size == 1) {
                return null
            }
            page = (page + 1) % pages.size
        }
    }

    private companion object {
        val SECATEURS = listOf("obj.secateurs", "obj.fairy_enchanted_secateurs")
        const val CLIP_SEQ = "seq.farming_plant_cure"
        const val CLIP_TICKS = 3
        const val TITLE = "Clip the bush into which shape?"
        const val PLAIN = "A plain bush"
        const val PAGE_SIZE = 4
        const val MORE = "More..."
        const val CANCEL = "Cancel"
    }
}
