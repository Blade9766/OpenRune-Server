package org.rsmod.content.quest.area.lumbridge.losttribe.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.KAZGAR
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.MINES_ARRIVAL
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Kazgar, posted by Mistag just inside the tunnel from the castle cellar once the silverware goes
 * missing, to guide surface-dwellers safely through the maze to the Dorgeshuun mines.
 */
class Kazgar @Inject constructor(private val lostTribe: LostTribeQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(KAZGAR) { startDialogue(it.npc) { kazgar() } }
        onOpNpc3(KAZGAR) { lostTribe.run { guideThroughTunnels("Kazgar", MINES_ARRIVAL) } }
    }

    private suspend fun Dialogue.kazgar() {
        chatNpc(neutral, "Hello, surface-dweller.")
        when (
            choice3(
                "Who are you?",
                1,
                "Can you show me the way to the mines?",
                2,
                "Good bye, cave-dweller.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Who are you?")
                chatNpc(
                    neutral,
                    "Mistag posted me here when surface-dwellers started visiting the mine. If you have " +
                        "business in the Dorgeshuun mines I will guide you through the tunnels.",
                )
                when (choice2("Can you show me the way to the mines?", 1, "Maybe some other time.", 2)) {
                    1 -> guide()
                    2 -> chatPlayer(neutral, "Maybe some other time.")
                }
            }
            2 -> guide()
            3 -> chatPlayer(neutral, "Good bye, cave-dweller.")
        }
    }

    private suspend fun Dialogue.guide() {
        chatPlayer(quiz, "Can you show me the way to the mines?")
        chatNpc(happy, "All right. Follow me.")
        lostTribe.run { access.guideThroughTunnels("Kazgar", MINES_ARRIVAL) }
    }
}
