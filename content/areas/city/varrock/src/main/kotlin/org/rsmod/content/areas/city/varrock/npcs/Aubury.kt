package org.rsmod.content.areas.city.varrock.npcs

import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest.Companion.RESEARCH_NOTES
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest.Companion.RESEARCH_PACKAGE
import org.rsmod.content.quest.area.lumbridge.rmNotes
import org.rsmod.content.quest.area.lumbridge.rmNotesGiven
import org.rsmod.content.skills.runecrafting.essence.EssenceMineTeleporter
import org.rsmod.content.skills.runecrafting.essence.RuneEssenceTeleports
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Aubury
@Inject
constructor(
    private val shops: Shops,
    private val runeMysteries: RuneMysteriesQuest,
    private val teleports: RuneEssenceTeleports,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.aubury") { startDialogue(it.npc) { auburyDialogue(it.npc) } }
        onOpNpc3("npc.aubury") { player.openAuburyShop(it.npc) }
        onOpNpc4("npc.aubury") { teleport(it.npc) }
    }

    private suspend fun ProtectedAccess.teleport(npc: Npc) {
        if (!runeMysteries.isComplete(player)) {
            return
        }
        teleports.teleportToMine(this, npc, EssenceMineTeleporter.Aubury)
    }

    private suspend fun Dialogue.auburyDialogue(npc: Npc) {
        when (runeMysteries.stage(player)) {
            RuneMysteriesQuest.STAGE_PACKAGE -> packageDelivery(npc)
            RuneMysteriesQuest.STAGE_PACKAGE_DELIVERED -> packageDelivered(npc)
            RuneMysteriesQuest.STAGE_NOTES -> notesFollowUp(npc)
            RuneMysteriesQuest.STAGE_COMPLETE -> shopDialogue(npc, teleport = true)
            else -> shopDialogue(npc, teleport = false)
        }
    }

    private suspend fun Dialogue.shopDialogue(npc: Npc, teleport: Boolean) {
        chatNpc(happy, "Do you want to buy some runes?")
        val choice =
            if (teleport) {
                choice3(
                    "Yes please!",
                    1,
                    "Oh, it's a rune shop. No thank you, then.",
                    2,
                    "Can you teleport me to the Rune Essence?",
                    3,
                )
            } else {
                choice2("Yes please!", 1, "Oh, it's a rune shop. No thank you, then.", 2)
            }
        when (choice) {
            1 -> player.openAuburyShop(npc)
            2 -> declineShop("Oh, it's a rune shop. No thank you, then.")
            3 -> {
                chatPlayer(quiz, "Can you teleport me to the Rune Essence?")
                chatNpc(
                    happy,
                    "Of course. By the way, if you end up making any runes from the essence you " +
                        "mine, I'll happily buy them from you.",
                )
                teleports.teleportToMine(access, npc, EssenceMineTeleporter.Aubury)
            }
        }
    }

    private suspend fun Dialogue.declineShop(text: String) {
        chatPlayer(neutral, text)
        chatNpc(neutral, "Well, if you find someone who does want runes, please send them my way.")
    }

    private suspend fun Dialogue.packageDelivery(npc: Npc) {
        chatNpc(happy, "Do you want to buy some runes?")
        val choice =
            choice3(
                "Yes please!",
                1,
                "I've been sent here with a package for you.",
                2,
                "Oh, it's a rune shop. No thank you, then.",
                3,
            )
        when (choice) {
            1 -> player.openAuburyShop(npc)
            2 -> deliverPackage()
            3 -> declineShop("Oh, it's a rune shop. No thank you, then.")
        }
    }

    private suspend fun Dialogue.deliverPackage() {
        chatPlayer(neutral, "I've been sent here with a package for you.")
        chatNpc(quiz, "A package? From who?")
        chatPlayer(neutral, "From Sedridor at the Wizards' Tower.")
        chatNpc(
            shocked,
            "From Sedridor? But... surely, he can't have? Please, let me have it. It must be " +
                "extremely important for him to have sent a stranger.",
        )
        if (access.invDel(access.inv, RESEARCH_PACKAGE).failure) {
            chatPlayer(sad, "Uh... yeah... about that... I kind of don't have it with me...")
            chatNpc(
                angry,
                "What kind of person says they have a delivery for me, but not with them? " +
                    "Honestly.",
            )
            chatNpc(neutral, "Come back when you have it.")
            return
        }
        runeMysteries.advanceTo(access, RuneMysteriesQuest.STAGE_PACKAGE_DELIVERED)
        objbox(RESEARCH_PACKAGE, "You hand the package to Aubury.")
        chatNpc(neutral, "Now, let's have a look...")
        readPackage()
    }

    private suspend fun Dialogue.packageDelivered(npc: Npc) {
        chatNpc(happy, "Do you want to buy some runes?")
        val choice =
            choice3(
                "Anything useful in that package I gave you?",
                1,
                "Yes please!",
                2,
                "No thank you.",
                3,
            )
        when (choice) {
            1 -> {
                chatPlayer(quiz, "Anything useful in that package I gave you?")
                chatNpc(neutral, "Well, let's have a look...")
                readPackage()
            }
            2 -> player.openAuburyShop(npc)
            3 -> declineShop("No thank you.")
        }
    }

    private suspend fun Dialogue.readPackage() {
        objbox(RESEARCH_PACKAGE, "Aubury goes through the package of research notes.")
        chatNpc(shocked, "This... this is incredible.")
        chatNpc(
            happy,
            "My gratitude to you adventurer for bringing me these research notes. Thanks to " +
                "you, I think we finally have it.",
        )
        chatPlayer(quiz, "You mean the incantation?")
        chatNpc(
            neutral,
            "Well when we combine my own research with this latest discovery, I think we might " +
                "just...",
        )
        chatNpc(
            neutral,
            "No, no, I'm getting ahead of myself. The signs are promising, but let's not jump to " +
                "any conclusions just yet.",
        )
        runeMysteries.advanceTo(access, RuneMysteriesQuest.STAGE_NOTES)
        chatNpc(
            neutral,
            "Here, take these notes back to Sedridor. They should hopefully give him everything " +
                "he needs.",
        )
        handOverNotes(offerTea = true)
    }

    private suspend fun Dialogue.notesFollowUp(npc: Npc) {
        if (player.rmNotesGiven) {
            shopDialogue(npc, teleport = false)
            return
        }
        if (!player.rmNotes) {
            chatNpc(
                neutral,
                "Here, take these notes back to Sedridor. They should hopefully give him " +
                    "everything he needs.",
            )
            chatPlayer(neutral, "Okay.")
            handOverNotes(offerTea = true)
            return
        }
        chatNpc(quiz, "Hello. Did you take those notes back to Sedridor?")
        if (RESEARCH_NOTES !in player.inv) {
            chatPlayer(sad, "Sorry, but I lost them.")
            chatNpc(
                neutral,
                "Well, luckily I have duplicates. It's a good thing they are written in code. I " +
                    "wouldn't want the wrong kind of person to get access to the information " +
                    "contained within.",
            )
            val banked = access.bank.count(RESEARCH_NOTES)
            if (banked > 0) {
                access.invDel(access.bank, RESEARCH_NOTES, banked)
            }
            handOverNotes(offerTea = false)
            return
        }
        chatPlayer(neutral, "I'm still working on it.")
        chatNpc(
            neutral,
            "Don't take too long. He'll be eager to see if this is indeed the breakthrough we " +
                "were hoping for.",
        )
        chatNpc(quiz, "Now, did you want to buy some runes?")
        when (choice2("Yes please!", 1, "No thank you.", 2)) {
            1 -> player.openAuburyShop(npc)
            2 -> declineShop("No thank you.")
        }
    }

    private suspend fun Dialogue.handOverNotes(offerTea: Boolean) {
        if (access.invAdd(access.inv, RESEARCH_NOTES).failure) {
            player.rmNotes = false
            mesbox("You don't have enough inventory space to take the notes.")
            return
        }
        player.rmNotes = true
        objbox(RESEARCH_NOTES, "Aubury hands you some research notes.")
        if (!offerTea) {
            return
        }
        chatNpc(happy, "Before you leave, why not have a cup of tea?")
        when (choice2("I'd love a cup of tea.", 1, "No, thank you.", 2)) {
            1 -> {
                chatPlayer(happy, "I'd love a cup of tea.")
                access.ifClose()
                player.runEnergy = Constants.run_max_energy
                UpdateRun.energy(player, player.runEnergy)
                player.say("Aaah, nothing like a nice cuppa tea!")
            }
            2 -> chatPlayer(neutral, "No, thank you.")
        }
    }

    private fun Player.openAuburyShop(npc: Npc) {
        shops.open(this, npc, "Aubury's Rune Shop.", "inv.runeshop")
    }
}
