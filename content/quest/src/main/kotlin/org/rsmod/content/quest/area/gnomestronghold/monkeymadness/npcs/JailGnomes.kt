package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.BUNKDO
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.CARADO
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.LUMO
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ROYAL_SEAL
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_COMPLETE
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Lumo, Bunkdo and Carado, the 10th squad foot soldiers locked in the cells beside the player's. */
class JailGnomes @Inject constructor(private val monkeyMadness: MonkeyMadnessQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(LUMO) { startDialogue(it.npc) { lumo() } }
        onOpNpc1(BUNKDO) { startDialogue(it.npc) { soldier("Bunkdo") } }
        onOpNpc1(CARADO) { startDialogue(it.npc) { soldier("Carado") } }
    }

    private suspend fun Dialogue.lumo() {
        if (monkeyMadness.stage(player) == STAGE_COMPLETE) {
            chatNpc(happy, "The hero of Ape Atoll! Bunkdo still owes you a drink.")
            return
        }
        if (monkeyMadness.metLumo.get(player)) {
            chatNpc(neutral, "Wait for the guard to walk away from your door, then pick the lock. And don't stand by the bars; Trefaji likes to hit things.")
            return
        }
        chatNpc(neutral, "So you're awake. I see you've already met the welcoming party.")
        chatPlayer(quiz, "Who are you?")
        chatNpc(neutral, "Lumo, 10th squad. Or what's left of it. Who are you, and how did a human end up in a monkey jail?")
        if (player.inv.contains(ROYAL_SEAL)) {
            chatPlayer(neutral, "King Narnode sent me to find you.")
            objbox(ROYAL_SEAL, "You show Lumo the gnome royal seal.")
            chatNpc(happy, "The King's seal! Then he hasn't forgotten us.")
        } else {
            chatPlayer(neutral, "King Narnode sent me to find you. I flew down on one of the military gliders.")
            chatNpc(happy, "A military glider? Then Daero has finally got them flying. The King hasn't forgotten us after all.")
        }
        chatPlayer(quiz, "What happened to your squad?")
        chatNpc(neutral, "The wind took us. One minute we were over the volcano, the next we were miles south with the gliders coming apart in the trees.")
        chatNpc(neutral, "We built boats and rowed to this island. The monkeys were waiting. Bunkdo, Carado and I ended up in here; the Sergeant and the rest got away.")
        chatPlayer(quiz, "How do I get out?")
        chatNpc(neutral, "The lock on your door is old. The guards, Trefaji and Aberab, are dense as bricks but they hit like them too. Wait until the one on patrol walks away from your door, then try the lock.")
        chatNpc(neutral, "And find Karam. He's out there somewhere in the grass. He'll know where the Sergeant is.")
        monkeyMadness.metLumo.set(player, true)
        monkeyMadness.syncVars(player)
    }

    private suspend fun Dialogue.soldier(name: String) {
        if (monkeyMadness.stage(player) == STAGE_COMPLETE) {
            chatNpc(happy, "Free at last! I'll never complain about the stronghold barracks again.")
            return
        }
        chatNpc(neutral, "$name, 10th squad. Talk to Lumo, he's the one with the plan. I'm the one with the bruises.")
        chatPlayer(neutral, "Keep your chin up.")
        chatNpc(neutral, "Easy for you to say. Your cell has a lock a child could pick.")
    }
}
