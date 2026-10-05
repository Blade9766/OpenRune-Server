package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * The spirit tree network: which trees a friend of the gnomes can travel between, and how.
 *
 * Other content can join the network with a [Link] - a place every tree offers after the trees
 * themselves, such as a player's own house - and can carry a player from a tree of its own through
 * [travelMenu] and [lastDestination]. Travel uses the client's hotkeyed list menu, the way Jagex's
 * "Spirit Tree Locations" menu does.
 */
@Singleton
class SpiritTreeNetwork
@Inject
constructor(
    private val treeGnomeVillage: TreeGnomeVillageQuest,
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) {
    /** Somewhere else the trees can carry a player, offered as [label] when it is not null. */
    interface Link {
        fun label(player: Player): String?

        suspend fun travel(access: ProtectedAccess)
    }

    private val links = mutableListOf<Link>()

    fun register(link: Link) {
        links += link
    }

    fun canTravel(access: ProtectedAccess): Boolean {
        val player = access.player
        if (!treeGnomeVillage.quest.isQuestCompleted(player)) {
            access.mes("You need to be a friend of the gnome people before the spirit trees will carry you.")
            return false
        }
        val atStronghold = player.coords.chebyshevDistance(Destination.STRONGHOLD.arrival) <= STRONGHOLD_RADIUS
        if (atStronghold && !QuestRequirements.hasCompleted(player, GRAND_TREE)) {
            access.mes("This tree will not carry you until you have proved yourself to King Narnode.")
            return false
        }
        return true
    }

    /** The tree list, leaving out the tree the player stands at and any [exclude]d link. */
    suspend fun travelMenu(access: ProtectedAccess, exclude: Link? = null) {
        if (!canTravel(access)) {
            return
        }
        val player = access.player
        val here = Destination.entries.firstOrNull { player.coords.chebyshevDistance(it.arrival) <= HERE_RADIUS }
        val trees = Destination.entries.filter { it != here }
        val extras = links.filter { it !== exclude }.mapNotNull { link -> link.label(player)?.let { link to it } }
        val chosen = access.menu(MENU_TITLE, hotkeys = true, choices = trees.map { it.title } + extras.map { it.second })
        val tree = trees.getOrNull(chosen)
        if (tree != null) {
            travel(access, tree)
            return
        }
        extras.getOrNull(chosen - trees.size)?.first?.travel(access)
    }

    suspend fun lastDestination(access: ProtectedAccess, exclude: Link? = null) {
        if (!canTravel(access)) {
            return
        }
        val player = access.player
        val name = treeGnomeVillage.lastSpiritTree.get(player)
        val destination = Destination.entries.firstOrNull { it.name == name }
        if (destination == null) {
            access.mes("You haven't travelled by spirit tree yet.")
            travelMenu(access, exclude)
            return
        }
        if (player.coords.chebyshevDistance(destination.arrival) <= HERE_RADIUS) {
            access.mes("You are already at ${destination.title}.")
            return
        }
        travel(access, destination)
    }

    private suspend fun travel(access: ProtectedAccess, destination: Destination) {
        if (!transport(access)) {
            return
        }
        treeGnomeVillage.lastSpiritTree.set(access.player, destination.name)
        access.telejump(destination.arrival, TeleportType.Exempt)
        access.anim(TELEPORT_END_SEQ)
        access.mes("The spirit tree carries you to ${destination.title}.")
    }

    /**
     * Plays a tree's teleport out, once the player is allowed to teleport at all; the caller then
     * puts the player wherever they are going.
     */
    suspend fun transport(access: ProtectedAccess): Boolean {
        val denial = teleportValidator.validate(access.player, TeleportType.Standard, areaChecker)
        if (denial != null) {
            access.mes(denial, ChatType.Engine)
            return false
        }
        access.ifClose()
        access.anim(TELEPORT_SEQ)
        access.spotanim(TELEPORT_SPOTANIM, height = TELEPORT_SPOTANIM_HEIGHT)
        access.soundSynth(TELEPORT_SOUND)
        access.delay(TELEPORT_TICKS)
        return true
    }

    enum class Destination(val title: String, val arrival: CoordGrid) {
        VILLAGE("Tree Gnome Village", CoordGrid(2542, 3170, 0)),
        STRONGHOLD("Gnome Stronghold", CoordGrid(2461, 3444, 0)),
        BATTLEFIELD("Battlefield of Khazard", CoordGrid(2555, 3259, 0)),
        GRAND_EXCHANGE("Grand Exchange", CoordGrid(3185, 3508, 0)),
        FELDIP_HILLS("Feldip Hills", CoordGrid(2488, 2850, 0)),
    }

    private companion object {
        const val MENU_TITLE = "Spirit Tree Locations"
        const val GRAND_TREE = "quest_grandtree"
        const val TELEPORT_SOUND = "synth.teleport_all"
        const val TELEPORT_SEQ = "seq.human_castteleport"
        const val TELEPORT_END_SEQ = "seq.human_castteleport_reverse"
        const val TELEPORT_SPOTANIM = "spotanim.teleport_casting"
        const val TELEPORT_SPOTANIM_HEIGHT = 92
        const val TELEPORT_TICKS = 3
        const val HERE_RADIUS = 6
        const val STRONGHOLD_RADIUS = 6
    }
}
