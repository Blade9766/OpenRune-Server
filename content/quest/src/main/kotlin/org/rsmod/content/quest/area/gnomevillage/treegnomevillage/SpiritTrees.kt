package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The spirit tree network the quest unlocks. Every spirit tree loc in the world shares the same
 * handful of types: the one-op forms only talk, the two-op forms also Travel and remember the
 * last destination. The village tree (`loc.ent`), the battlefield, Grand Exchange and Feldip
 * Hills trees (`loc.spirittree_small`) and the stronghold tree (`loc.stronghold_ent`) are
 * multilocs that grow their travelling forms as the gnome quests end. Travel uses the client's
 * hotkeyed list menu, the way Jagex's "Spirit Tree Locations" menu does.
 */
class SpiritTrees @Inject constructor(
    private val treeGnomeVillage: TreeGnomeVillageQuest,
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (type in TALK_ONLY_TYPES + TRAVEL_TYPES) {
            onOpLoc2(type) { talk() }
        }
        for (type in TRAVEL_TYPES) {
            onOpLoc1(type) { travelMenu() }
            onOpLoc3(type) { lastDestination() }
        }
    }

    private fun ProtectedAccess.canTravel(): Boolean {
        if (!treeGnomeVillage.quest.isQuestCompleted(player)) {
            mes("You need to be a friend of the gnome people before the spirit trees will carry you.")
            return false
        }
        if (player.coords.chebyshevDistance(Destination.STRONGHOLD.arrival) <= STRONGHOLD_RADIUS && !QuestRequirements.hasCompleted(player, GRAND_TREE)) {
            mes("This tree will not carry you until you have proved yourself to King Narnode.")
            return false
        }
        return true
    }

    private suspend fun ProtectedAccess.talk() {
        if (!treeGnomeVillage.quest.isQuestCompleted(player)) {
            startDialogue {
                chatPlayer(happy, "Hello tree.")
                chatNpcSpecific(TREE_NAME, TREE_HEAD, neutral, "Hello, young one. I am a spirit tree, grown from the seeds of an ancient line. My roots run deep and my branches reach far.")
                chatPlayer(quiz, "Can you take me somewhere?")
                chatNpcSpecific(TREE_NAME, TREE_HEAD, neutral, "Only friends of the gnome people may travel by my branches. Prove yourself to them first.")
            }
            return
        }
        startDialogue {
            chatPlayer(happy, "Hello tree.")
            chatNpcSpecific(TREE_NAME, TREE_HEAD, happy, "Hello, friend of the gnomes. Would you like to travel by my branches?")
            when (choice2("Yes please.", 1, "No thanks.", 2)) {
                1 -> {
                    chatPlayer(happy, "Yes please.")
                    access.travelMenu()
                }
                2 -> chatPlayer(neutral, "No thanks.")
            }
        }
    }

    private suspend fun ProtectedAccess.travelMenu() {
        if (!canTravel()) {
            return
        }
        val here = Destination.entries.firstOrNull { player.coords.chebyshevDistance(it.arrival) <= HERE_RADIUS }
        val options = Destination.entries.filter { it != here }
        val chosen = menu(MENU_TITLE, hotkeys = true, choices = options.map { it.title })
        val destination = options.getOrNull(chosen) ?: return
        travel(destination)
    }

    private suspend fun ProtectedAccess.lastDestination() {
        if (!canTravel()) {
            return
        }
        val name = treeGnomeVillage.lastSpiritTree.get(player)
        val destination = Destination.entries.firstOrNull { it.name == name }
        if (destination == null) {
            mes("You haven't travelled by spirit tree yet.")
            travelMenu()
            return
        }
        if (player.coords.chebyshevDistance(destination.arrival) <= HERE_RADIUS) {
            mes("You are already at ${destination.title}.")
            return
        }
        travel(destination)
    }

    private suspend fun ProtectedAccess.travel(destination: Destination) {
        val denial = teleportValidator.validate(player, TeleportType.Standard, areaChecker)
        if (denial != null) {
            mes(denial, ChatType.Engine)
            return
        }
        ifClose()
        treeGnomeVillage.lastSpiritTree.set(player, destination.name)
        anim(TELEPORT_SEQ)
        spotanim(TELEPORT_SPOTANIM, height = TELEPORT_SPOTANIM_HEIGHT)
        soundSynth(TELEPORT_SOUND)
        delay(TELEPORT_TICKS)
        telejump(destination.arrival, TeleportType.Exempt)
        anim(TELEPORT_END_SEQ)
        mes("The spirit tree carries you to ${destination.title}.")
    }

    enum class Destination(val title: String, val arrival: CoordGrid) {
        VILLAGE("Tree Gnome Village", CoordGrid(2542, 3170, 0)),
        STRONGHOLD("Gnome Stronghold", CoordGrid(2461, 3444, 0)),
        BATTLEFIELD("Battlefield of Khazard", CoordGrid(2555, 3259, 0)),
        GRAND_EXCHANGE("Grand Exchange", CoordGrid(3185, 3508, 0)),
        FELDIP_HILLS("Feldip Hills", CoordGrid(2488, 2850, 0)),
    }

    private companion object {
        const val TREE_NAME = "Spirit tree"
        const val TREE_HEAD = "npc.treevillage_spirittree"
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

        val TALK_ONLY_TYPES = listOf("loc.spirittree_big_1op", "loc.spirittree_small_1op")
        val TRAVEL_TYPES =
            listOf(
                "loc.spirittree_big_2ops",
                "loc.spirittree_big_2ops_orbs",
                "loc.spirittree_small_2ops",
            )
    }
}
