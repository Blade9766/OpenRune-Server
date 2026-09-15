package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.CARANOCK
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ROYAL_SEAL
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_CARANOCK_MET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** G.L.O. Caranock, Glough's Gnome Liaison Officer at the Karamja shipyard. */
class Caranock @Inject constructor(private val monkeyMadness: MonkeyMadnessQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(CARANOCK) { startDialogue(it.npc) { talk() } }
    }

    private suspend fun Dialogue.talk() {
        when (monkeyMadness.stage(player)) {
            0 -> {
                chatNpc(neutral, "Yes? I'm the Gnome Liaison Officer here. If you have shipyard business, take it up with the foreman.")
                chatPlayer(neutral, "I was just looking around.")
                chatNpc(neutral, "Then look somewhere else. This is a working yard.")
            }
            STAGE_STARTED -> if (player.inv.contains(ROYAL_SEAL)) squadEnquiry() else noSeal()
            STAGE_CARANOCK_MET -> {
                chatNpc(neutral, "I told you everything I know. The winds took them. Please, report back to King Narnode.")
                chatPlayer(neutral, "I'm going.")
            }
            STAGE_COMPLETE -> {
                chatNpc(worried, "You! What do you want?")
                chatPlayer(angry, "The King knows all about you and Glough, Caranock. I'd start running if I were you.")
                chatNpc(worried, "I... I don't know what you're talking about.")
            }
            else -> {
                chatNpc(neutral, "Still here? I have a shipyard to decommission. Good day.")
            }
        }
    }

    private suspend fun Dialogue.noSeal() {
        chatPlayer(neutral, "King Narnode sent me. I'm looking for the 10th squad.")
        chatNpc(neutral, "Anyone can claim to speak for the King. Without his seal I have nothing to say to you.")
    }

    private suspend fun Dialogue.squadEnquiry() {
        chatPlayer(neutral, "I'm here on King Narnode's business. He sent his 10th squad to close down this shipyard.")
        chatNpc(neutral, "The King's business, is it? Let me see that seal.")
        objbox(ROYAL_SEAL, "You show Caranock the gnome royal seal.")
        chatNpc(shocked, "That is the King's seal! Forgive me. I haven't seen one of those since Glough last visited.")
        chatPlayer(neutral, "Glough has been arrested. He is no longer head tree guardian, and this yard is to be shut down.")
        chatNpc(shocked, "Arrested? Glough? I... I had no idea. That explains why I have heard nothing from the stronghold.")
        chatPlayer(quiz, "The 10th squad were sent here weeks ago. Did they ever arrive?")
        chatNpc(neutral, "No. No, they never came. Nobody has landed at this yard for months.")
        chatNpc(neutral, "If they were flying south they would have crossed the volcano. The winds there have been terrible this season. Strong southerly winds, day after day.")
        chatNpc(neutral, "I would guess their gliders were blown off course. Far off course, perhaps. Those little gliders are not built for storms.")
        chatPlayer(quiz, "You seem very sure of that.")
        chatNpc(neutral, "It is the only explanation. Now, there is no need for you to trouble yourself further. I will see to the decommissioning of the yard myself.")
        chatNpc(neutral, "Please, return to the King at once and tell him what I have told you. He should not be kept waiting.")
        chatPlayer(neutral, "I'll do that.")
        monkeyMadness.advanceTo(access, STAGE_CARANOCK_MET)
        monkeyMadness.syncVars(player)
    }
}
