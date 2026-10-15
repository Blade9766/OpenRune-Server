package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.content.areas.wilderness.locs.WildernessObelisks
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.SpiritTreeNetwork
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseStore
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The superior garden's spirit tree and obelisk.
 *
 * The house spirit tree, and the spiritual fairy tree's Tree op, carry a friend of the gnomes to any
 * tree in the [SpiritTreeNetwork]; in return every other spirit tree offers "Your house" to a
 * player whose house has a spirit tree, which brings them home. Anyone may use the tree.
 *
 * The obelisk sends whoever touches it, on their own and at once, to one of the Wilderness
 * obelisks: at random, or to a site of their choosing once they have finished the hard Wilderness
 * diary. Nothing comes the other way.
 */
class HouseTravelScript
@Inject
constructor(
    private val network: SpiritTreeNetwork,
    private val houses: HouseAccess,
    private val store: HouseStore,
) : PluginScript() {
    private val homeLink =
        object : SpiritTreeNetwork.Link {
            override fun label(player: Player): String? = if (hasSpiritTree(player)) HOME_LABEL else null

            override suspend fun travel(access: ProtectedAccess) {
                if (!network.transport(access)) {
                    return
                }
                houses.enter(access, buildMode = false)
            }
        }

    override fun ScriptContext.startup() {
        network.register(homeLink)
        onOpLoc1(SPIRIT_TREE) { network.travelMenu(this, exclude = homeLink) }
        onOpLoc3(SPIRIT_TREE) { network.lastDestination(this, exclude = homeLink) }
        onOpLoc1(SPIRIT_RING) { network.travelMenu(this, exclude = homeLink) }

        onOpLoc1(OBELISK) { activate(chosen = false) }
        onOpLoc2(OBELISK) { activate(chosen = true) }
        onOpLoc3(OBELISK) { activate(chosen = true) }
    }

    private fun hasSpiritTree(player: Player): Boolean {
        val state = store.state(player)
        if (!state.owned) {
            return false
        }
        return state.rooms.values.any { room ->
            room.type == RoomType.SUPERIOR_GARDEN && room.furniture[TELEPORT_SPACE] in SPIRIT_TREE_OPTIONS
        }
    }

    private suspend fun ProtectedAccess.activate(chosen: Boolean) {
        val destination =
            if (chosen) {
                if (!WildernessObelisks.canChoose(player)) {
                    mes("You need to complete the hard Wilderness diary to choose where the obelisk sends you.")
                    return
                }
                WildernessObelisks.choose(this) ?: return
            } else {
                val proceed =
                    choice2(
                        "Yes, I'm brave.",
                        true,
                        "No thanks.",
                        false,
                        title = "Warning! The obelisk will teleport you into the Wilderness.",
                    )
                if (!proceed) {
                    return
                }
                WildernessObelisks.Site.entries.random()
            }
        anim(TOUCH_SEQ)
        houses.leave(this, to = destination.centre)
        mes("Ancient magic teleports you somewhere in the Wilderness.")
    }

    private companion object {
        const val SPIRIT_TREE = "loc.poh_spirit_tree"
        const val SPIRIT_RING = "loc.poh_spirit_ring"
        const val OBELISK = "loc.poh_wilderness_obelisk"
        const val HOME_LABEL = "Your house"
        const val TELEPORT_SPACE = "teleport"
        const val TOUCH_SEQ = "seq.human_pickuptable"

        /** The superior garden's teleport space options that are spirit trees: the tree and the ring. */
        val SPIRIT_TREE_OPTIONS = setOf(0, 3)
    }
}
