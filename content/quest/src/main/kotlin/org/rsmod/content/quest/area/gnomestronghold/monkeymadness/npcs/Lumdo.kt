package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.ChapterCards
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadness
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.LUMDO
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ROYAL_SEAL
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_APE_ATOLL
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_CRASH_ISLAND
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.mmFadeTeleport
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lumdo, the 10th squad's boatman. `npc.mm_lumdo` is a varbit-multi that grows a Travel option
 * once Waydar has pulled rank; one spawn minds the boats on Crash Island, the other waits on the
 * Ape Atoll beach for the trip back.
 */
class Lumdo @Inject constructor(private val monkeyMadness: MonkeyMadnessQuest, private val cards: ChapterCards) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(LUMDO) { startDialogue(it.npc) { talk(it.npc) } }
        onOpNpc3(LUMDO) { travel(it.npc) }
    }

    private fun Npc.onApeAtoll(): Boolean = coords.x < APE_ATOLL_MAX_X

    private suspend fun Dialogue.talk(npc: Npc) {
        if (npc.onApeAtoll()) {
            chatNpc(neutral, "Ready to go back to Crash Island? The Sergeant will want to know how you got on.")
            when (choice2("Yes, take me back.", 1, "Not yet.", 2)) {
                1 -> access.sail(toApeAtoll = false)
                2 -> chatPlayer(neutral, "Not yet.")
            }
            return
        }
        when {
            monkeyMadness.stage(player) < STAGE_CRASH_ISLAND -> chatNpc(shocked, "A human! How did you get here?")
            !monkeyMadness.lumdoOrdered.get(player) -> refusal()
            monkeyMadness.stage(player) < STAGE_APE_ATOLL -> {
                chatNpc(sad, "All right, all right. Waydar outranks the Sergeant, so I'll row you over. Get in the boat.")
                when (choice2("Let's go.", 1, "Give me a moment.", 2)) {
                    1 -> access.sail(toApeAtoll = true)
                    2 -> chatPlayer(neutral, "Give me a moment.")
                }
            }
            else -> {
                chatNpc(neutral, "Want to go back over to the atoll?")
                when (choice2("Yes.", 1, "No.", 2)) {
                    1 -> access.sail(toApeAtoll = true)
                    2 -> chatPlayer(neutral, "No.")
                }
            }
        }
    }

    private suspend fun Dialogue.refusal() {
        chatNpc(quiz, "Who are you? You're no gnome.")
        chatPlayer(neutral, "King Narnode sent me to find the 10th squad.")
        if (player.inv.contains(ROYAL_SEAL)) {
            objbox(ROYAL_SEAL, "You show Lumdo the gnome royal seal.")
            chatNpc(neutral, "The King's seal! Then I suppose you're all right. I'm Lumdo, 10th squad.")
        } else {
            chatNpc(neutral, "Well, you came on a military glider, so I suppose you're all right. I'm Lumdo, 10th squad.")
        }
        chatPlayer(quiz, "Where are the rest of the squad?")
        chatNpc(neutral, "Sergeant Garkor took the big boat and the others west, to that atoll you can see from the beach. I'm to stay here and guard the gliders until they come back.")
        chatPlayer(neutral, "Then take me across to the atoll.")
        chatNpc(neutral, "Can't do that. The Sergeant's orders were to stay put, and I don't leave the beach without an order from someone who outranks him.")
        chatPlayer(quiz, "Does Waydar outrank him?")
        chatNpc(neutral, "Flight Commander Waydar? He does, as it happens. But he'd have to tell me himself.")
        monkeyMadness.lumdoRefused.set(player, true)
    }

    private suspend fun ProtectedAccess.travel(npc: Npc) {
        when {
            npc.onApeAtoll() -> sail(toApeAtoll = false)
            !monkeyMadness.lumdoOrdered.get(player) -> mes("Lumdo won't leave the beach without an order from Waydar.")
            else -> sail(toApeAtoll = true)
        }
    }

    private suspend fun ProtectedAccess.sail(toApeAtoll: Boolean) {
        if (toApeAtoll) {
            mesbox("Lumdo rows you across the strait. The atoll grows larger and larger until a beach appears beneath its cliffs.")
            val first = !monkeyMadness.reachedApeAtoll.get(player)
            mmFadeTeleport(MonkeyMadness.APE_ATOLL_LANDING)
            monkeyMadness.advanceTo(this, STAGE_APE_ATOLL)
            monkeyMadness.reachedApeAtoll.set(player, true)
            monkeyMadness.syncVars(player)
            if (first) {
                with(cards) { show(ChapterCards.CHAPTER_TWO) }
            } else {
                mes("Lumdo runs the boat up onto the beach of Ape Atoll.")
            }
        } else {
            mesbox("Lumdo rows you back across the strait to Crash Island.")
            mmFadeTeleport(MonkeyMadness.CRASH_ISLAND_BOAT)
        }
    }

    private companion object {
        const val APE_ATOLL_MAX_X = 2850
    }
}
