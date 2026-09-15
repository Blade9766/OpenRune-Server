package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.Greegree
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.Marim
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadness
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AMULET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.BANANA
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.MONKEYS_AUNT
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.MONKEY_CHILD
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.TALISMAN
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Monkey Child in the banana plantation west of the jail, his patrolling aunt, and the banana
 * trees around them. The child lends his talisman, a toy his aunt bought him, to anyone who
 * claims to be his uncle and brings him bananas; if it is lost he cries until a new one turns up.
 */
class MonkeyChild
@Inject
constructor(
    private val monkeyMadness: MonkeyMadnessQuest,
    private val greegree: Greegree,
    private val marim: Marim,
    private val locRepo: LocRepository,
) : PluginScript() {

    private val treeStages = TREE_STAGES.map { ServerCacheManager.getObject(it.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $it") }

    override fun ScriptContext.startup() {
        onOpNpc1(MONKEY_CHILD) { child(it.npc) }
        onOpNpcU(MONKEY_CHILD) { giveBanana(it.npc, it.objType.internalName) }
        onOpNpc1(MONKEYS_AUNT) { aunt(it.npc) }
        for ((index, stage) in TREE_STAGES.withIndex()) {
            onOpLoc1(stage) { searchTree(it.loc, index) }
        }
    }

    private fun ProtectedAccess.canTalkMonkey(): Boolean = player.worn.contains(AMULET)

    private suspend fun ProtectedAccess.child(npc: Npc) {
        if (!canTalkMonkey()) {
            startDialogue(npc) {
                chatNpc(happy, "Ook ook! Eek eek ook!")
                chatPlayer(confused, "I don't understand a word of that.")
            }
            return
        }
        startDialogue(npc) { childTalk() }
    }

    private suspend fun Dialogue.childTalk() {
        val stage = monkeyMadness.zooknockStage.get(player)
        when {
            monkeyMadness.talismanLost.get(player) -> {
                chatNpc(sad, "Sniff... Auntie got me a new one! But you can't have it. You lost the last one.")
                chatPlayer(sad, "I'm very sorry. I'll take better care of this one, I promise.")
                chatNpc(neutral, "Well... all right, uncle. But only because you're family.")
                lendTalisman()
            }
            monkeyMadness.talismanLent.get(player) -> {
                if (player.inv.contains(TALISMAN) || monkeyMadness.hasGreegree(player) || stage >= MonkeyMadnessQuest.ZOOKNOCK_GREEGREE_MADE) {
                    chatNpc(happy, "Hello, uncle! Are you having fun with my toy?")
                    chatPlayer(happy, "Lots of fun. Thank you.")
                    if (!player.inv.contains(TALISMAN)) {
                        chatNpc(quiz, "Will I get it back one day?")
                        when (choice2("Of course you will.", 1, "I've lost it, I'm afraid.", 2)) {
                            1 -> chatPlayer(happy, "Of course you will.")
                            2 -> {
                                chatPlayer(sad, "I've lost it, I'm afraid.")
                                chatNpc(sad, "You LOST it? Waaaaah!")
                                monkeyMadness.talismanLost.set(player, true)
                                mesbox("The monkey child bursts into tears. Perhaps his aunt will buy him another toy.")
                            }
                        }
                    }
                } else {
                    chatNpc(happy, "Hello, uncle!")
                }
            }
            monkeyMadness.bananasGiven.get(player) >= BANANAS_NEEDED -> {
                chatNpc(happy, "Uncle! Auntie gave me my new toy! Look!")
                chatPlayer(quiz, "That's a fine toy. Could I borrow it for a little while?")
                chatNpc(neutral, "Hmm... you did bring me all those bananas. All right, but you have to give it back!")
                lendTalisman()
            }
            monkeyMadness.bananasGiven.get(player) > 0 -> {
                chatNpc(quiz, "Have you got the rest of my bananas, uncle? I need twenty!")
                chatPlayer(neutral, "I'm working on it.")
            }
            else -> {
                chatNpc(quiz, "Who are you? I don't know you. Are you a friend of Auntie's?")
                when (choice3("I'm your uncle.", 1, "I'm a friend of your aunt's.", 2, "Never mind.", 3)) {
                    1 -> {
                        chatPlayer(happy, "I'm your uncle!")
                        chatNpc(happy, "Uncle! I didn't know I had an uncle. Auntie never said.")
                        chatNpc(neutral, "Uncle, will you help me? Auntie said she'd get me a new toy from the magic shop if I picked twenty bananas, but I keep eating them.")
                        chatPlayer(neutral, "I'll see what I can do.")
                        chatNpc(happy, "Thank you, uncle! The trees are all around us. Just give me the bananas when you have them.")
                        mesbox("The monkey child cannot count very well. Five bananas should satisfy him.")
                        monkeyMadness.bananasGiven.set(player, 0)
                        childAsked.set(player, true)
                    }
                    2 -> {
                        chatPlayer(neutral, "I'm a friend of your aunt's.")
                        chatNpc(neutral, "Auntie doesn't have any friends. She says so all the time.")
                    }
                    3 -> chatPlayer(neutral, "Never mind.")
                }
            }
        }
    }

    private val childAsked = monkeyMadness.quest.attribute(name = "CHILD_ASKED", default = false)

    private suspend fun Dialogue.lendTalisman() {
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "Your hands are full, uncle. Come back when you can carry it.")
            return
        }
        access.invAdd(player.inv, TALISMAN)
        monkeyMadness.talismanLent.set(player, true)
        monkeyMadness.talismanLost.set(player, false)
        monkeyMadness.bananasGiven.set(player, 0)
        objbox(TALISMAN, "The monkey child hands you his toy: a monkey talisman.")
    }

    private suspend fun ProtectedAccess.giveBanana(npc: Npc, obj: String) {
        if (obj != BANANA) {
            startDialogue(npc) { chatNpc(neutral, "Ook? I don't want that.") }
            return
        }
        if (!canTalkMonkey() || !childAsked.get(player)) {
            startDialogue(npc) { chatNpc(happy, "Ook ook!") }
            mesbox("The monkey child eats the banana without a word of thanks.")
            invDel(player.inv, BANANA)
            return
        }
        if (monkeyMadness.bananasGiven.get(player) >= BANANAS_NEEDED) {
            startDialogue(npc) { chatNpc(happy, "That's enough bananas, uncle! Auntie will get me my toy now.") }
            return
        }
        invDel(player.inv, BANANA)
        val given = monkeyMadness.bananasGiven.get(player) + 1
        monkeyMadness.bananasGiven.set(player, given)
        if (given >= BANANAS_NEEDED) {
            startDialogue(npc) {
                chatNpc(happy, "One, two, three... lots! That's twenty, uncle! Thank you!")
                chatNpc(happy, "Auntie will bring my toy when she comes round. Come back in a moment and I'll show you.")
            }
        } else {
            mes("You give the monkey child a banana. He has counted to $given.")
        }
    }

    private suspend fun ProtectedAccess.aunt(npc: Npc) {
        if (greegree.isMonkey(player)) {
            startDialogue(npc) {
                chatNpc(angry, "Ook! Stay away from my nephew, you hear? I know your sort.")
                chatPlayer(neutral, "Ook.")
            }
            return
        }
        npc.facePlayer(player)
        with(marim) { capture(Marim.CaptureReason.AUNT) }
    }

    private suspend fun ProtectedAccess.searchTree(tree: BoundLocInfo, index: Int) {
        anim(MonkeyMadness.SEARCH_SEQ)
        delay(1)
        if (index >= TREE_STAGES.lastIndex) {
            mes("There are no bananas left on this tree.")
            return
        }
        if (player.inv.freeSpace() < 1) {
            mes("You need a free inventory space to pick a banana.")
            return
        }
        soundSynth(MonkeyMadness.SOUND_PICK_BANANA)
        invAdd(player.inv, BANANA)
        mes("You pick a banana.")
        locRepo.change(tree, treeStages[index + 1], REGROW_TICKS)
    }

    companion object {
        const val BANANAS_NEEDED = 5
        const val REGROW_TICKS = 100
        val TREE_STAGES =
            listOf(
                "loc.mm_bananatreefull",
                "loc.mm_bananatreefour",
                "loc.mm_bananatreethree",
                "loc.mm_bananatreetwo",
                "loc.mm_bananatreeone",
                "loc.mm_bananatreeempty",
            )
    }
}
