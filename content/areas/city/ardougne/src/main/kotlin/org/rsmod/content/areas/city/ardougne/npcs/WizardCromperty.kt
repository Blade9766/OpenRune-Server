package org.rsmod.content.areas.city.ardougne.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.skills.runecrafting.essence.EssenceMineTeleporter
import org.rsmod.content.skills.runecrafting.essence.RuneEssenceTeleports
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Wizard Cromperty in north-east Ardougne, one of the Rune Essence Mine teleporters. Unlike the
 * other teleporters he is not a multinpc, so his Teleport op is always shown and the Rune Mysteries
 * check has to explain itself.
 */
class WizardCromperty
@Inject
constructor(
    private val runeMysteries: RuneMysteriesQuest,
    private val teleports: RuneEssenceTeleports,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (cromperty in CROMPERTY) {
            onOpNpc1(cromperty) { startDialogue(it.npc) { crompertyDialogue(it.npc) } }
            onOpNpc3(cromperty) { teleport(it.npc) }
        }
    }

    private suspend fun ProtectedAccess.teleport(npc: Npc) {
        if (!runeMysteries.isUnlocked(player)) {
            mes("You need to have completed Rune Mysteries to use this teleport.")
            return
        }
        teleports.teleportToMine(this, npc, EssenceMineTeleporter.Cromperty)
    }

    private suspend fun Dialogue.crompertyDialogue(npc: Npc) {
        chatNpc(happy, "Hello there. My name is Cromperty. I am a Wizard, and an inventor.")
        chatNpc(
            happy,
            "You must be ${player.displayName}. My good friend Sedridor has told me about you. " +
                "As both wizard and inventor, he has aided me in my great invention!",
        )
        val choice =
            if (runeMysteries.isUnlocked(player)) {
                choice3(
                    "Two jobs? That's got to be tough.",
                    1,
                    "So what have you invented?",
                    2,
                    "Can you teleport me to the Rune Essence?",
                    3,
                )
            } else {
                choice2("Two jobs? That's got to be tough.", 1, "So what have you invented?", 2)
            }
        when (choice) {
            1 -> twoJobs()
            2 -> invention()
            3 -> {
                chatPlayer(quiz, "Can you teleport me to the Rune Essence?")
                teleports.teleportToMine(access, npc, EssenceMineTeleporter.Cromperty)
            }
        }
    }

    private suspend fun Dialogue.twoJobs() {
        chatPlayer(quiz, "Two jobs? That's got to be tough.")
        chatNpc(happy, "Not when you combine them it isn't! I invent MAGIC things!")
        val choice =
            choice2("So what have you invented?", 1, "Well, I shall leave you to your inventing.", 2)
        if (choice == 1) {
            invention()
            return
        }
        chatPlayer(neutral, "Well, I shall leave you to your inventing.")
        chatNpc(happy, "Thanks for dropping by! Stop again anytime!")
    }

    private suspend fun Dialogue.invention() {
        chatPlayer(quiz, "So what have you invented?")
        chatNpc(
            happy,
            "Ah! My latest invention is my patent pending teleportation block! It emits a low " +
                "level magical signal, that will allow me to locate it anywhere in the world, and " +
                "teleport anything",
        )
        chatNpc(
            happy,
            "directly to it! I hope to revolutionise the entire teleportation system! Don't you " +
                "think I'm great? Uh, I mean it's great?",
        )
        val choice =
            choice3(
                "So where is the other block?",
                1,
                "Can I be teleported please?",
                2,
                "Well done, that's very clever.",
                3,
            )
        when (choice) {
            1 -> {
                chatPlayer(quiz, "So where is the other block?")
                chatNpc(
                    neutral,
                    "Well... Hmm. I would guess somewhere between here and the Wizards' Tower in " +
                        "Misthalin. All I know is that it hasn't got there yet as the wizards " +
                        "there would have contacted me.",
                )
                chatNpc(
                    neutral,
                    "I'm using the GPDT for delivery. They assured me it would be delivered " +
                        "promptly.",
                )
            }
            2 -> blockTeleport()
            3 -> {
                chatPlayer(happy, "Well done, that's very clever.")
                chatNpc(
                    happy,
                    "Yes it is isn't it? Forgive me for feeling a little smug, this is a major " +
                        "breakthrough in the field of teleportation!",
                )
            }
        }
    }

    private suspend fun Dialogue.blockTeleport() {
        chatPlayer(quiz, "Can I be teleported please?")
        chatNpc(
            happy,
            "By all means! I'm afraid I can't give you any specifics as to where you will come " +
                "out however. Presumably wherever the other block is located.",
        )
        val choice =
            choice2(
                "Yes, that sounds good. Teleport me!",
                1,
                "That sounds dangerous. Leave me here.",
                2,
            )
        if (choice == 2) {
            chatPlayer(worried, "That sounds dangerous. Leave me here.")
            chatNpc(neutral, "As you wish.")
            return
        }
        chatPlayer(happy, "Yes, that sounds good. Teleport me!")
        chatNpc(happy, "Okey dokey! Ready?")
        chatNpc(confused, "Hmmm.... that's odd... I can't seem to get a signal...")
        chatPlayer(neutral, "Oh well, never mind.")
    }

    private companion object {
        val CROMPERTY = listOf("npc.cromperty_pre_diary", "npc.cromperty_post_diary")
    }
}
