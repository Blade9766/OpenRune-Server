package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.ChapterCards
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.Greegree
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AMULET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AWOWOGEI_HEAD
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.GARKOR
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.HANGAR_GLOUGH
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.SIGIL
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_ALLIANCE
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_MONKEY
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sergeant Garkor, hiding between the palace and the white house on the south-east edge of
 * Marim. `npc.mm_garkor` is a multi on Monkey Madness II's progress, so ops arrive on the base type.
 */
class Garkor @Inject constructor(private val monkeyMadness: MonkeyMadnessQuest, private val greegree: Greegree, private val cards: ChapterCards) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(GARKOR) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        if (greegree.isMonkey(player) && !player.worn.contains(AMULET)) {
            mes("You try to speak, but all that comes out is 'Ook'. You need the M'speak amulet on.")
            return
        }
        startDialogue(npc) {
            when {
                monkeyMadness.stage(player) < MonkeyMadnessQuest.STAGE_APE_ATOLL -> chatNpc(shocked, "A human! Keep your voice down, the whole island is crawling with monkeys.")
                !monkeyMadness.metGarkor.get(player) -> briefing()
                monkeyMadness.stage(player) == STAGE_COMPLETE -> chatNpc(happy, "Good to see you again. The squad owes you its lives; the drinks are on us at the Blurberry Bar for life.")
                monkeyMadness.stage(player) == STAGE_ALLIANCE && monkeyMadness.demonSlain.get(player) -> chatNpc(happy, "The demon is dead and the squad is alive. Get back to the King and tell him everything.")
                monkeyMadness.stage(player) == STAGE_ALLIANCE -> if (monkeyMadness.sigilGiven.get(player)) sigilReminder() else chapterFour()
                monkeyMadness.stage(player) == STAGE_MONKEY -> asMonkey()
                else -> humanReminder()
            }
        }
    }

    private suspend fun Dialogue.briefing() {
        chatNpc(shocked, "A human! Get down before they see you!")
        chatPlayer(neutral, "Sergeant Garkor? King Narnode sent me to find the 10th squad.")
        chatNpc(neutral, "Then he sent you to the right place, more or less. I'm Garkor. What's left of the squad is scattered across this island.")
        chatPlayer(quiz, "What happened to you?")
        chatNpc(neutral, "We were blown clean off course on the way to Karamja; a wind like nothing I've flown in. The gliders came down in the trees of the island east of here.")
        chatNpc(neutral, "We built boats and rowed across to see what this place was. We found monkeys, hundreds of them, and not the sort you find on Karamja: armed, armoured and drilled like soldiers.")
        chatNpc(sad, "They took Lumo, Bunkdo and Carado. Zooknock, Bunkwicket, Waymottin and I got away. Karam is somewhere in the town; nobody catches Karam.")
        chatPlayer(quiz, "So what's the plan?")
        chatNpc(neutral, "The sappers are tunnelling under the south of the island so we can move without being seen. Zooknock is at the far end of the tunnel with a plan of his own.")
        chatNpc(neutral, "He wants to turn you into a monkey.")
        chatPlayer(shocked, "A monkey?!")
        chatNpc(neutral, "It doesn't work on gnomes; we're too small and too magical, he says. But a human is close enough to a monkey for his spells to hold. That's why the King sent you and not a regiment.")
        chatNpc(neutral, "Go down the ladder near the beach on the south coast and follow the tunnel to Zooknock. Mind the traps, and mind the dead things. Then come back to me looking like one of them.")
        monkeyMadness.metGarkor.set(player, true)
        monkeyMadness.syncVars(player)
    }

    private suspend fun Dialogue.humanReminder() {
        chatNpc(neutral, "Still human, I see. Zooknock is at the end of the tunnel under the south of the island; the ladder is near the beach.")
        if (monkeyMadness.zooknockStage.get(player) >= MonkeyMadnessQuest.ZOOKNOCK_AMULET_EXPLAINED) {
            chatNpc(neutral, "He'll need a monkey talisman and some monkey bones for the transformation. I hear the children of this town play with talismans like toys.")
        }
    }

    private suspend fun Dialogue.asMonkey() {
        if (!greegree.isMonkey(player)) {
            chatNpc(neutral, "Put the greegree on and the amulet round your neck before you go anywhere near the palace. You're no use to me in a cell.")
            return
        }
        when (monkeyMadness.awowogeiStage.get(player)) {
            MonkeyMadnessQuest.AWOWOGEI_NOT_MET -> {
                chatNpc(laugh, "Ha! I'd never have believed it. You look like you were born here.")
                chatNpc(neutral, "Now for the real work. These monkeys have a king, Awowogei, in the palace to the north. I want to know what he intends before we make our move.")
                chatNpc(neutral, "Get an audience with him. His Elder Guards stand at the palace gate; try them first.")
            }
            MonkeyMadnessQuest.AWOWOGEI_TASK_GIVEN -> {
                chatNpc(neutral, "A monkey from the Ardougne Zoo? Whatever he asks, do it. We need his trust more than that zoo needs one more monkey.")
                chatNpc(neutral, "Walk back here with it. If you teleport, the poor creature will bolt.")
            }
            else -> chatNpc(neutral, "Awowogei is satisfied? Then we should talk.")
        }
    }

    private suspend fun Dialogue.chapterFour() {
        chatPlayer(neutral, "Awowogei accepted the monkey. He says he'll consider an alliance.")
        chatNpc(neutral, "I'm sure he will. While you were away, Karam got into the palace roof. Listen to what he heard.")
        access.plot()
        chatNpc(angry, "Glough. It was Glough all along. He and Awowogei have raised a demon, and they mean to send it against the Grand Tree.")
        chatPlayer(shocked, "Then we have to stop it here!")
        chatNpc(neutral, "We will. Zooknock has traced the demon to a cavern under the island. He can teleport the whole squad there, but he needs an anchor for the spell.")
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "Make room in your pack; I have something for you.")
            return
        }
        access.invAdd(player.inv, SIGIL)
        monkeyMadness.sigilGiven.set(player, true)
        objbox(SIGIL, "Garkor hands you the 10th squad sigil.")
        chatNpc(neutral, "That is the 10th squad sigil. Wear it when you are ready to fight, wherever you are, and Zooknock's spell will pull you and the squad into the cavern.")
        chatNpc(neutral, "Do not put it on lightly. Bring food, prayer and your best weapons; the demon will not be alone against seven gnomes and a human.")
        with(cards) { access.show(ChapterCards.CHAPTER_FOUR) }
    }

    /** Karam's account of the palace: Awowogei and the voice of Glough. */
    private suspend fun ProtectedAccess.plot() {
        if (monkeyMadness.seenPlot.get(player)) {
            return
        }
        monkeyMadness.seenPlot.set(player, true)
        player.midiJingle(ChapterCards.MEANWHILE_JINGLE)
        mesbox("Meanwhile, in the palace of Awowogei...")
        startDialogue {
            awowogei(neutral, "The human has done as I asked. My people trust him; the gnomes trust him. He suspects nothing.")
            glough(laugh, "Good. Then it is time. The demon is ready, and the 10th squad will lead it straight to Narnode's precious tree.")
            awowogei(neutral, "And when the gnomes are gone, Ape Atoll will have the stronghold, as you promised.")
            glough(laugh, "Of course, your majesty. Of course.")
        }
    }

    private suspend fun Dialogue.awowogei(mood: MesAnimType, text: String) {
        chatNpcSpecific("Awowogei", AWOWOGEI_HEAD, mood, text)
    }

    private suspend fun Dialogue.glough(mood: MesAnimType, text: String) {
        chatNpcSpecific("Glough", HANGAR_GLOUGH, mood, text)
    }

    private suspend fun Dialogue.sigilReminder() {
        if (player.inv.contains(SIGIL) || player.worn.contains(SIGIL)) {
            chatNpc(neutral, "Wear the sigil when you are ready. Zooknock will do the rest.")
        } else {
            chatNpc(neutral, "Lost the sigil? Waymottin keeps the spares, at Zooknock's end of the tunnel.")
        }
    }
}
