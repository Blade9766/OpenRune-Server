package org.rsmod.content.areas.guilds

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
 * Wizard Distentor, head of the Wizards' Guild and a Rune Essence Mine teleporter. He stands inside
 * the guild, so the 66 Magic door is the only thing between players and his teleport.
 */
class WizardDistentor
@Inject
constructor(
    private val runeMysteries: RuneMysteriesQuest,
    private val teleports: RuneEssenceTeleports,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(DISTENTOR) { startDialogue(it.npc) { distentorDialogue(it.npc) } }
        onOpNpc3(DISTENTOR) { teleport(it.npc) }
    }

    private suspend fun ProtectedAccess.teleport(npc: Npc) {
        if (!runeMysteries.isUnlocked(player)) {
            return
        }
        teleports.teleportToMine(this, npc, EssenceMineTeleporter.Distentor)
    }

    private suspend fun Dialogue.distentorDialogue(npc: Npc) {
        chatNpc(happy, "Welcome to the Magicians' Guild!")
        chatPlayer(happy, "Hello there.")
        chatNpc(quiz, "What can I do for you?")
        if (!runeMysteries.isUnlocked(player)) {
            justLooking()
            return
        }
        val choice =
            choice2(
                "Nothing thanks, I'm just looking around.",
                1,
                "Can you teleport me to the Rune Essence?",
                2,
            )
        if (choice == 1) {
            justLooking()
            return
        }
        chatPlayer(quiz, "Can you teleport me to the Rune Essence?")
        teleports.teleportToMine(access, npc, EssenceMineTeleporter.Distentor)
    }

    private suspend fun Dialogue.justLooking() {
        chatPlayer(neutral, "Nothing thanks, I'm just looking around.")
        chatNpc(neutral, "That's fine with me.")
    }

    private companion object {
        const val DISTENTOR = "npc.guild_wizard"
    }
}
