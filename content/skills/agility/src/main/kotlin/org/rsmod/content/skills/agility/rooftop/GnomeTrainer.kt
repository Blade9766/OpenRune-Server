package org.rsmod.content.skills.agility.rooftop

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The drill-sergeant gnomes of the Gnome Stronghold agility course. */
class GnomeTrainer : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.gnometrainer") { startDialogue(it.npc) { trainer() } }
    }

    private suspend fun Dialogue.trainer() {
        when (access.random.of(4)) {
            0 -> {
                chatPlayer(happy, "Hello how are you?")
                chatNpc(
                    angry,
                    "I'm amazed by how much humans chat. The sign over there says training area, " +
                        "not pointless conversation area.",
                )
            }
            1 -> {
                chatPlayer(quiz, "Hello, what is this place?")
                chatNpc(
                    neutral,
                    "This, my friend, is where we train. Here we improve our agility. It's an " +
                        "essential skill.",
                )
                chatPlayer(neutral, "It looks easy enough.")
                chatNpc(
                    neutral,
                    "If you complete the course in order from the slippery log to the end, your " +
                        "agility will increase much faster than by repeating just one obstacle.",
                )
            }
            2 -> {
                chatPlayer(happy, "This is fun!")
                chatNpc(angry, "This is training soldier. If you want fun go make some cocktails.")
            }
            else -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(
                    angry,
                    "This isn't a grannies' tea party, let's see some sweat human. Go! Go! Go!",
                )
            }
        }
    }
}
