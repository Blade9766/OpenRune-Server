package org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.ORB
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_BREACHED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_HAS_ORB
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_ORB_RETURNED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_WARLORD_SLAIN
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The gnomes of the village whose only part in the quest is commentary: Remsai, Kalron, the Local Gnomes and Bolkoy. */
class Villagers
@Inject
constructor(
    private val treeGnomeVillage: TreeGnomeVillageQuest,
    private val shops: Shops,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(REMSAI) { startDialogue(it.npc) { remsai() } }
        onOpNpc1(KALRON) { startDialogue(it.npc) { kalron() } }
        onOpNpc1(LOCAL_GNOME) { startDialogue(it.npc) { localGnome() } }
        onOpNpc1(BOLKOY) { startDialogue(it.npc) { bolkoy(it.npc) } }
        onOpNpc3(BOLKOY) { player.openShop(it.npc) }
    }

    private fun Player.openShop(npc: Npc) {
        shops.open(this, npc, "Bolkoy's Village Shop", "inv.gnomeshop")
    }

    private fun stage(player: Player): Int = treeGnomeVillage.stage(player)

    private suspend fun Dialogue.remsai() {
        when (stage(player)) {
            0 -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(neutral, "Hello traveller. Not many make it through the maze; the king will want to see you.")
            }
            in 1 until STAGE_BREACHED -> {
                chatNpc(worried, "Oh my, oh my!")
                chatPlayer(quiz, "What's wrong?")
                chatNpc(worried, "The orb, they have the orb. It must be returned or we're doomed.")
            }
            STAGE_BREACHED, STAGE_HAS_ORB -> {
                chatPlayer(happy, "Hello Remsai.")
                chatNpc(quiz, "Hello, did you find the orb?")
                if (player.inv.contains(ORB)) {
                    chatPlayer(happy, "I have it here.")
                    chatNpc(happy, "You're our saviour.")
                } else {
                    chatPlayer(sad, "No, I'm afraid not.")
                    chatNpc(worried, "Please, we must have the orb if we are to survive.")
                }
            }
            STAGE_ORB_RETURNED -> {
                chatPlayer(quiz, "Are you ok?")
                chatNpc(sad, "Khazard's men came. Without the orb we were defenceless. They killed many and then took our last hope, the other orbs.")
                chatNpc(sad, "Now surely we're all doomed. Without them the spirit tree is useless.")
            }
            STAGE_WARLORD_SLAIN -> {
                chatPlayer(happy, "I've returned.")
                chatNpc(happy, "You're back, well done brave adventurer. Now the orbs are safe we can perform the ritual for the spirit tree. We can live in peace once again.")
            }
            else -> {
                chatPlayer(happy, "Hello Remsai.")
                chatNpc(happy, "Hello friend. The spirit tree watches over us again, thanks to you.")
            }
        }
    }

    private suspend fun Dialogue.kalron() {
        when (stage(player)) {
            in 1 until STAGE_ORB_RETURNED -> {
                chatPlayer(happy, "Hello, how are you?")
                chatNpc(worried, "Oh my. I'll never find my way back before Khazard's men come and hunt me down.")
            }
            STAGE_ORB_RETURNED -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(sad, "Oh my, oh my, the village has been pillaged and I'm still lost. Oh dear.")
            }
            STAGE_WARLORD_SLAIN -> {
                chatPlayer(happy, "Hello little man.")
                chatNpc(sad, "Hello. I hope they come out and find me soon. It's getting cold.")
            }
            else -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(sad, "Hello. I've been wandering this maze for days. Which way is the village?")
                chatPlayer(neutral, "I'm afraid I couldn't tell you.")
            }
        }
    }

    private suspend fun Dialogue.localGnome() {
        when (stage(player)) {
            STAGE_BREACHED, STAGE_HAS_ORB -> {
                chatPlayer(happy, "Hello little man.")
                chatNpc(laugh, "Little man stronger than big man. Hee hee, lardi dee, lardi da.")
            }
            STAGE_ORB_RETURNED -> {
                chatPlayer(happy, "Hi.")
                chatNpc(happy, "Must save the orbs and kill the Khazard warlord. That will be fun, hee hee.")
            }
            STAGE_WARLORD_SLAIN -> {
                chatPlayer(happy, "Hello gnome.")
                chatNpc(laugh, "Soon we're gonna have the sacred ceremony and boy am I going to party. Lock up your daughters. Hee hee.")
            }
            STAGE_COMPLETE -> {
                chatPlayer(happy, "Hello gnome.")
                chatNpc(laugh, "The orbs are back, the tree is happy and I danced all night. Hee hee.")
            }
            else -> {
                chatPlayer(happy, "Hello little man.")
                chatNpc(happy, "Hee hee, big man in the village. Lardi dee, lardi da.")
            }
        }
    }

    private suspend fun Dialogue.bolkoy(npc: Npc) {
        when (stage(player)) {
            STAGE_BREACHED, STAGE_HAS_ORB -> {
                chatPlayer(happy, "Hello.")
                chatNpc(happy, "Amazing, you recovered the orb.")
                chatNpc(happy, "Well I am impressed. Would you like to buy something?")
            }
            STAGE_ORB_RETURNED -> {
                chatPlayer(happy, "Hi.")
                chatNpc(sad, "Oh, hello there. Have you heard? They took the other orbs, it's terrible. I suppose the show must go on.")
                chatNpc(neutral, "Would you like to buy something?")
            }
            STAGE_WARLORD_SLAIN -> {
                chatPlayer(happy, "Hello.")
                chatNpc(happy, "Hello there. You're that hero who saved the orbs. Soon we will perform the ritual and the village will be safe again.")
                chatNpc(happy, "Anyway, would you like anything from my shop?")
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(happy, "Hello there. Welcome to my shop. Would you like to buy something?")
            }
        }
        when (choice2("What have you got?", 1, "No thank you.", 2)) {
            1 -> {
                chatPlayer(quiz, "What have you got?")
                player.openShop(npc)
            }
            2 -> chatPlayer(neutral, "No thank you.")
        }
    }

    private companion object {
        const val REMSAI = "npc.remsai"
        const val KALRON = "npc.lostgnome"
        const val LOCAL_GNOME = "npc.chantergnome"
        const val BOLKOY = "npc.treevillage_shopkeeper1"
    }
}
