package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The world's spirit trees. Every spirit tree loc shares the same handful of types: the one-op forms
 * only talk, the two-op forms also Travel and remember the last destination. The village tree
 * (`loc.ent`), the battlefield, Grand Exchange and Feldip Hills trees (`loc.spirittree_small`) and
 * the stronghold tree (`loc.stronghold_ent`) are multilocs that grow their travelling forms as the
 * gnome quests end. Where they can go is [SpiritTreeNetwork]'s.
 */
class SpiritTrees @Inject constructor(
    private val treeGnomeVillage: TreeGnomeVillageQuest,
    private val network: SpiritTreeNetwork,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (type in TALK_ONLY_TYPES + TRAVEL_TYPES) {
            onOpLoc2(type) { talk() }
        }
        for (type in TRAVEL_TYPES) {
            onOpLoc1(type) { network.travelMenu(this) }
            onOpLoc3(type) { network.lastDestination(this) }
        }
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
                    network.travelMenu(access)
                }
                2 -> chatPlayer(neutral, "No thanks.")
            }
        }
    }

    private companion object {
        const val TREE_NAME = "Spirit tree"
        const val TREE_HEAD = "npc.treevillage_spirittree"

        val TALK_ONLY_TYPES = listOf("loc.spirittree_big_1op", "loc.spirittree_small_1op")
        val TRAVEL_TYPES =
            listOf(
                "loc.spirittree_big_2ops",
                "loc.spirittree_big_2ops_orbs",
                "loc.spirittree_small_2ops",
            )
    }
}
