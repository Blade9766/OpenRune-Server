package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.grandtree.landNear
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.Greegree
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.Greegree.Companion.inZoo
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadness
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AMULET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AWOWOGEI_TASK_GIVEN
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.MONKEY_IN_BACKPACK
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.MONKEY_MINDER
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ZOO_MONKEY
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.mmFadeTeleport
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Ardougne Zoo: the Monkey Minder who puts any stray monkey into the pen and lets a confused
 * human out of it, and the caged monkeys, one of whom will climb into a fellow monkey's backpack.
 */
class MonkeyMinder @Inject constructor(private val monkeyMadness: MonkeyMadnessQuest, private val greegree: Greegree) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(MONKEY_MINDER) { minder(it.npc) }
        onOpNpc1(ZOO_MONKEY) { zooMonkey(it.npc) }
    }

    private suspend fun ProtectedAccess.minder(npc: Npc) {
        val inPen = player.coords.inZoo() && player.coords.x >= PEN_MIN_X
        when {
            greegree.isMonkey(player) -> {
                startDialogue(npc) {
                    chatNpc(shocked, "How did you get out of the pen? Back you go, you little scamp!")
                }
                mesbox("The Monkey Minder picks you up by the scruff of the neck and drops you in the monkey pen.")
                mmFadeTeleport(landNear(MonkeyMadness.ZOO_PEN))
            }
            inPen -> {
                startDialogue(npc) {
                    chatNpc(confused, "What... how did you get in there? You're not a monkey!")
                    chatPlayer(neutral, "Er, no. Could you let me out?")
                    chatNpc(confused, "I... yes. Yes, of course. I could have sworn I put a monkey in there.")
                }
                mmFadeTeleport(landNear(MonkeyMadness.ZOO_OUTSIDE))
            }
            else -> startDialogue(npc) {
                chatNpc(neutral, "Welcome to the Ardougne Zoo monkey enclosure. Please don't feed the monkeys, and please don't climb the fence.")
                chatPlayer(quiz, "Where do the monkeys come from?")
                chatNpc(neutral, "Karamja, mostly. A few came from further south, though nobody seems to know quite where.")
            }
        }
    }

    private suspend fun ProtectedAccess.zooMonkey(npc: Npc) {
        if (!greegree.isMonkey(player) || !player.worn.contains(AMULET)) {
            startDialogue(npc) {
                chatNpc(neutral, "Ook ook eek!")
                chatPlayer(confused, "The monkey chatters at you.")
            }
            return
        }
        startDialogue(npc) {
            if (monkeyMadness.awowogeiStage.get(player) != AWOWOGEI_TASK_GIVEN || player.inv.contains(MONKEY_IN_BACKPACK)) {
                chatNpc(neutral, "A monkey from outside! How is it out there? Are the bananas better?")
                chatPlayer(neutral, "Much better.")
                chatNpc(sad, "I thought so.")
                return@startDialogue
            }
            chatPlayer(neutral, "Psst. I've come from Ape Atoll. King Awowogei sent me to take one of you home.")
            chatNpc(happy, "Home? Ape Atoll? Take me! Take me!")
            chatPlayer(neutral, "Climb into my backpack and keep quiet.")
            if (player.inv.freeSpace() < 1) {
                chatNpc(sad, "There's no room in there! Empty something out and come back.")
                return@startDialogue
            }
            access.invAdd(player.inv, MONKEY_IN_BACKPACK)
            access.soundSynth(MonkeyMadness.SOUND_MONKEY_ESCAPE)
            objbox(MONKEY_IN_BACKPACK, "The monkey scrambles into your backpack and curls up at the bottom.")
            mesbox("The Monkey Minder won't let a monkey out of the pen. You will have to look human again to leave, and walk all the way back: a frightened monkey won't sit through a teleport.")
        }
    }

    private companion object {
        const val PEN_MIN_X = 2598
    }
}
