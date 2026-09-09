package org.rsmod.content.quest.area.wilderness.magearena.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.shops.Shops
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lundail, who sells runes in the bank cave beneath the arena. His free daily runes are a
 * Wilderness Diary reward, and the diaries are not part of this server yet, so he turns everyone
 * away from that option.
 */
class Lundail @Inject constructor(private val shops: Shops) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(LUNDAIL) { startDialogue(it.npc) { lundail(it.npc) } }
        onOpNpc3(LUNDAIL) { player.openRuneShop(it.npc) }
        onOpNpc4(LUNDAIL) { startDialogue(it.npc) { claimRunes() } }
    }

    private suspend fun Dialogue.lundail(npc: Npc) {
        chatNpc(happy, "Hello there. How can I help you, brave adventurer?")
        when (
            choice3(
                "What are you selling?", 1,
                "What's that big old building above us?", 2,
                "Claim free runes.", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "What are you selling?")
                chatNpc(happy, "I sell rune stones. I've got some good stuff, some really powerful little rocks. Take a look.")
                player.openRuneShop(npc)
            }
            2 -> {
                chatPlayer(quiz, "What's that big old building above us?")
                chatNpc(
                    neutral,
                    "That, my friend, is the mage battle arena. Top mages come from all over Gielinor " +
                        "to compete in the arena.",
                )
                chatPlayer(happy, "Wow.")
                chatNpc(neutral, "Few return, most get fried, hence the smell.")
                chatPlayer(confused, "Hmmm.. I did notice.")
            }
            3 -> claimRunes()
        }
    }

    private suspend fun Dialogue.claimRunes() {
        chatNpc(neutral, "You'll need to have completed the Easy Wilderness Achievement Diary to qualify for free runes.")
    }

    private fun Player.openRuneShop(npc: Npc) {
        shops.open(this, npc, "Lundail's Arena-side Rune Shop.", RUNE_SHOP)
    }

    private companion object {
        const val LUNDAIL = "npc.magearena_runeshop"
        const val RUNE_SHOP = "inv.magearena_runeshop"
    }
}

/** Gundai, the only banker in the Wilderness. */
class Gundai : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(GUNDAI) { startDialogue(it.npc) { gundai() } }
        onOpNpc3(GUNDAI) { openBank() }
        onOpNpc4(GUNDAI) { openCollectionBox() }
    }

    private suspend fun Dialogue.gundai() {
        chatPlayer(quiz, "Hello, what are you doing out here?")
        chatNpc(neutral, "I'm a banker, the only one around these dangerous parts.")
        when (
            choice4(
                "Cool, I'd like to access my bank account please.", 1,
                "Right, so can I check my PIN settings?", 2,
                "I'd like to collect items.", 3,
                "Well, now I know.", 4,
            )
        ) {
            1 -> access.openBank()
            2 -> access.ifOpenMainModal("interface.bankpin_settings")
            3 -> access.openCollectionBox()
            4 -> {
                chatPlayer(neutral, "Well, now I know.")
                chatNpc(happy, "Knowledge is power my friend.")
            }
        }
    }

    private fun ProtectedAccess.openBank() {
        tryOpenBank()
    }

    private fun ProtectedAccess.openCollectionBox() {
        ifOpenMainModal("interface.ge_collect")
    }

    private companion object {
        const val GUNDAI = "npc.magearena_banker"
    }
}
