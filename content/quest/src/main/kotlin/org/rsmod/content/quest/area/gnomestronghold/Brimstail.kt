package org.rsmod.content.quest.area.gnomestronghold

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

/** Brimstail in his cave under the Tree Gnome Stronghold, one of the Rune Essence Mine teleporters. */
class Brimstail
@Inject
constructor(
    private val runeMysteries: RuneMysteriesQuest,
    private val teleports: RuneEssenceTeleports,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(BRIMSTAIL) { startDialogue(it.npc) { brimstailDialogue(it.npc) } }
        onOpNpc3(BRIMSTAIL) { teleport(it.npc) }
    }

    private suspend fun ProtectedAccess.teleport(npc: Npc) {
        if (!runeMysteries.isComplete(player)) {
            return
        }
        teleports.teleportToMine(this, npc, EssenceMineTeleporter.Brimstail)
    }

    private suspend fun Dialogue.brimstailDialogue(npc: Npc) {
        chatNpc(happy, "Hello adventurer, what can I do for you?")
        if (!runeMysteries.isComplete(player)) {
            chatPlayer(neutral, "Nothing, thanks.")
            return
        }
        val choice =
            choice2(
                "Can you teleport me to the Rune Essence Mine?",
                1,
                "Nothing, thanks.",
                2,
            )
        if (choice == 2) {
            chatPlayer(neutral, "Nothing, thanks.")
            return
        }
        chatPlayer(quiz, "Can you teleport me to the Rune Essence?")
        chatNpc(happy, "Okay. Hold onto your hat!")
        teleports.teleportToMine(access, npc, EssenceMineTeleporter.Brimstail)
    }

    private companion object {
        const val BRIMSTAIL = "npc.gnome_brimstail"
    }
}
