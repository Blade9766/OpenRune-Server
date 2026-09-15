package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.Greegree
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadness
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AMULET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ELDER_GUARD_1
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ELDER_GUARD_2
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.KRUK
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_MONKEY
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.mmFadeTeleport
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Elder Guards at the palace gate and Kruk at the foot of the eastern watchtower. Only a
 * monkey wearing the M'speak amulet gets more than a grunt out of any of them; Kruk is the one
 * who takes a stranger in to see Awowogei.
 */
class PalaceGuards @Inject constructor(private val monkeyMadness: MonkeyMadnessQuest, private val greegree: Greegree) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ELDER_GUARD_1) { elderGuard(it.npc) }
        onOpNpc1(ELDER_GUARD_2) { elderGuard(it.npc) }
        onOpNpc1(KRUK) { kruk(it.npc) }
    }

    private suspend fun ProtectedAccess.grunt(npc: Npc): Boolean {
        if (!greegree.isMonkey(player)) {
            startDialogue(npc) {
                chatNpc(angry, "OOK! Ook ook!")
                chatPlayer(worried, "I think that means 'go away'.")
            }
            return true
        }
        if (!player.worn.contains(AMULET)) {
            mes("You try to speak, but all that comes out is 'Ook'. You need the M'speak amulet on.")
            return true
        }
        return false
    }

    private suspend fun ProtectedAccess.elderGuard(npc: Npc) {
        if (grunt(npc)) {
            return
        }
        startDialogue(npc) {
            when {
                monkeyMadness.stage(player) < STAGE_MONKEY -> chatNpc(neutral, "Move along. Nothing for you here.")
                monkeyMadness.awowogeiStage.get(player) >= MonkeyMadnessQuest.AWOWOGEI_TASK_GIVEN -> {
                    chatNpc(neutral, "The King is expecting you. Go on in.")
                    access.mmFadeTeleport(MonkeyMadness.THRONE_ROOM_ENTRY)
                }
                else -> {
                    chatPlayer(neutral, "I wish to speak with King Awowogei.")
                    chatNpc(neutral, "So does every monkey on this island. The King is busy. Only his advisers may go in unannounced.")
                    chatPlayer(quiz, "Then who can announce me?")
                    chatNpc(neutral, "Kruk, the captain of the guard, if he takes to you. You'll find him by the watchtowers at the west of the town, past the gate.")
                    monkeyMadness.elderGuardSpoken.set(player, true)
                }
            }
        }
    }

    private suspend fun ProtectedAccess.kruk(npc: Npc) {
        if (grunt(npc)) {
            return
        }
        startDialogue(npc) {
            when {
                monkeyMadness.stage(player) < STAGE_MONKEY -> chatNpc(neutral, "I don't know you. Get back to your post, whatever it is.")
                monkeyMadness.awowogeiStage.get(player) == MonkeyMadnessQuest.AWOWOGEI_NOT_MET -> firstAudience()
                else -> {
                    chatNpc(neutral, "The King has seen you once. Want to see him again?")
                    when (choice2("Yes.", 1, "No.", 2)) {
                        1 -> access.mmFadeTeleport(MonkeyMadness.THRONE_ROOM)
                        2 -> chatPlayer(neutral, "No.")
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.firstAudience() {
        chatPlayer(neutral, "Are you Kruk? The Elder Guards said you could get me in to see the King.")
        chatNpc(neutral, "I am Kruk. And who are you? I know every monkey in Marim, and I don't know you.")
        chatPlayer(neutral, "I've come from Karamja. I bring word from the monkeys there, and an offer for King Awowogei.")
        chatNpc(neutral, "Karamja... The King has wondered about our cousins across the water. Very well. Follow me, and speak only when he speaks to you.")
        access.mmFadeTeleport(MonkeyMadness.THRONE_ROOM)
        mesbox("Kruk leads you through the palace to the throne room.")
    }
}
