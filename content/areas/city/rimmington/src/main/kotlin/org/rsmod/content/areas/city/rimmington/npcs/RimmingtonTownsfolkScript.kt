package org.rsmod.content.areas.city.rimmington.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RimmingtonTownsfolkScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.hw18_girl") { startDialogue(it.npc) { chatNpc(shifty, "We don't talk to strangers.") } }
        onOpNpc1("npc.hw18_boy") { startDialogue(it.npc) { chatNpc(shifty, "Sorry, we don't talk to strangers.") } }
        onOpNpc1("npc.rimmington_anja") { startDialogue(it.npc) { householder(it.npc, "Eeeek!") } }
        onOpNpc1("npc.rimmington_hengel") {
            startDialogue(it.npc) { householder(it.npc, "Aaaarrgh!") }
        }
    }

    private suspend fun Dialogue.householder(npc: Npc, scream: String) {
        chatPlayer(happy, "Hello.")
        val title = if (access.isBodyTypeA()) "sir" else "madam"
        chatNpc(quiz, "Hello $title. What are you doing in my house?")
        when (
            choice3(
                "I'm just wandering around.",
                1,
                "I was hoping you'd give me some free stuff.",
                2,
                "I've come to kill you.",
                3,
            )
        ) {
            1 -> wandering()
            2 -> beg()
            else -> npc.say(scream)
        }
    }

    private suspend fun Dialogue.wandering() {
        chatPlayer(neutral, "I'm just wandering around.")
        chatNpc(quiz, "Oh dear, are you lost?")
        if (choice2("Yes, I'm lost.", true, "No, I know where I am.", false)) {
            chatPlayer(sad, "Yes, I'm lost.")
            chatNpc(
                neutral,
                "Okay, just walk north-east when you leave this house, and soon you'll reach " +
                    "the big city of Falador.",
            )
            chatPlayer(happy, "Thanks a lot.")
            return
        }
        chatPlayer(neutral, "No, I know where I am.")
        chatNpc(
            angry,
            "Oh? Well, would you mind wandering somewhere else? This is my house.",
        )
        chatPlayer(angry, "Meh!")
    }

    private suspend fun Dialogue.beg() {
        chatPlayer(happy, "I was hoping you'd give me some free stuff.")
        val reluctance =
            listOf("Do you REALLY need it?", "Er...", "I don't have much on me...", "I don't know...")
        chatNpc(confused, reluctance[access.random.of(reluctance.size)])
        val pleading =
            listOf(
                "<col=800000>(beg beg beg beg beg beg)</col>",
                "I promise I'll stop bothering you!",
                "Pleeease!",
                "Pwetty pleathe wiv thugar on top!",
            )
        chatPlayer(sad, pleading[access.random.of(pleading.size)])
        chatNpc(neutral, "Oh, alright. Here you go.")
        access.invAdd(access.inv, "obj.coins", access.random.of(1, 3))
    }
}
