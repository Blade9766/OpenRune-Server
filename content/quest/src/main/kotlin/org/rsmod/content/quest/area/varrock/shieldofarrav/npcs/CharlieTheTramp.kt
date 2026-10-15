package org.rsmod.content.quest.area.varrock.shieldofarrav.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BLACKARM_TASKED
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BLACKARM_TOLD_BY_CHARLIE
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.COINS
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_TOLD_BY_RELDO
import org.rsmod.content.quest.area.varrock.shieldofarrav.blackArmGang
import org.rsmod.content.quest.area.varrock.shieldofarrav.charlieHalfPaid
import org.rsmod.content.quest.area.varrock.shieldofarrav.charlieMet
import org.rsmod.content.quest.area.varrock.shieldofarrav.charliePaid
import org.rsmod.content.quest.area.varrock.shieldofarrav.phoenixGang
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Charlie the Tramp, begging at the mouth of the Black Arm Gang's alley by Varrock's south gate.
 * He points Shield of Arrav players at Katrine and, once she has set them their task, sells them
 * the way to the Phoenix Gang's weapon store for ten coins.
 */
class CharlieTheTramp @Inject constructor(private val arrav: ShieldOfArravQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(CHARLIE) { startDialogue(it.npc) { charlie() } }
    }

    private suspend fun Dialogue.charlie() {
        chatNpc(sad, "Spare some change, guv?")
        while (true) {
            val options = mutableListOf<Pair<String, Topic>>()
            options += "Who are you?" to Topic.WhoAreYou
            val questOption =
                when {
                    player.blackArmGang == BLACKARM_TASKED -> Topic.PhoenixGang
                    arrav.isInProgress(player) && player.phoenixGang >= PHOENIX_TOLD_BY_RELDO ->
                        Topic.BlackArmGang
                    else -> Topic.Alleyway
                }
            options += questOption.option to questOption
            options += "Sorry, I haven't got any." to Topic.NoChange
            options += "Go get a job!" to Topic.GetAJob
            if (questOption != Topic.PhoenixGang) {
                options += "Ok. Here you go." to Topic.GiveChange
            }
            val topic = choose(options)
            if (topic != Topic.WhoAreYou) {
                answer(topic)
                return
            }
            chatPlayer(quiz, "Who are you?")
            chatNpc(
                neutral,
                "Charles. Charles E. Trampin', at your service. Now, about that change you were " +
                    "about to give me...",
            )
        }
    }

    private suspend fun Dialogue.answer(topic: Topic) {
        when (topic) {
            Topic.NoChange -> {
                chatPlayer(sad, "Sorry, I haven't got any.")
                chatNpc(sad, "Thanks anyway.")
            }
            Topic.GetAJob -> {
                chatPlayer(angry, "Go get a job!")
                chatNpc(angry, "Oh, starting something, are we? I hope your nose falls off!")
            }
            Topic.GiveChange -> giveChange()
            Topic.Alleyway -> alleyway()
            Topic.BlackArmGang -> blackArmGang()
            Topic.PhoenixGang -> phoenixGang()
            Topic.WhoAreYou -> Unit
        }
    }

    private suspend fun Dialogue.giveChange() {
        chatPlayer(happy, "Ok. Here you go.")
        if (access.inv.count(COINS) == 0) {
            chatPlayer(sad, "Oh, wait. I haven't got any after all, sorry.")
            chatNpc(sad, "Thanks anyway.")
            return
        }
        access.invDel(access.inv, COINS)
        chatNpc(happy, "Hey, thanks a lot!")
        if (choice2("No problem.", true, "Don't I get some sort of quest hint or something now?", false)) {
            chatPlayer(happy, "No problem.")
            return
        }
        chatPlayer(quiz, "So... don't I get a quest hint or something now?")
        chatNpc(confused, "Eh? What are you on about? That's not why I asked for money.")
        chatNpc(sad, "I just need to eat...")
    }

    private suspend fun Dialogue.alleyway() {
        chatPlayer(quiz, Topic.Alleyway.option)
        chatNpc(shifty, "Funny you should ask... there is, as it happens.")
        chatNpc(
            neutral,
            "That's where the Black Arm Gang have their headquarters - and a nastier bunch of " +
                "crooks you won't meet.",
        )
        if (choice2("Thanks for the warning!", true, "Do you think they would let me join?", false)) {
            chatPlayer(happy, "Thanks for the warning!")
            chatNpc(happy, "Don't mention it.")
        }
        askToJoin()
    }

    private suspend fun Dialogue.blackArmGang() {
        chatPlayer(quiz, Topic.BlackArmGang.option)
        if (player.charlieMet) {
            chatNpc(
                neutral,
                "Like I said, word is they've got their headquarters right down this alley. I'd " +
                    "be careful getting mixed up with them, if I were you...",
            )
        } else {
            chatNpc(
                shifty,
                "Funny you should mention them... Word is they've got their headquarters right " +
                    "down this alley. I'd be careful getting mixed up with them, if I were you...",
            )
            player.charlieMet = true
        }
        askToJoin()
    }

    private suspend fun Dialogue.askToJoin() {
        chatPlayer(quiz, "Do you think they would let me join?")
        when {
            arrav.isBlackArm(player) ->
                chatNpc(confused, "I was under the impression you were already one of them...")
            arrav.isPhoenix(player) -> phoenixCollaborator()
            else -> {
                chatNpc(
                    neutral,
                    "You never know. Look for a lady down there called Katrine and have a word " +
                        "with her.",
                )
                chatNpc(worried, "Whatever you do, though, don't get on her bad side.")
                if (arrav.isInProgress(player) && player.blackArmGang == 0) {
                    player.blackArmGang = BLACKARM_TOLD_BY_CHARLIE
                }
            }
        }
    }

    private suspend fun Dialogue.phoenixCollaborator() {
        chatNpc(
            neutral,
            "No chance. You've been running with the Phoenix Gang; they'd never have you now.",
        )
        val how =
            choice2(
                "How did you know I was in the Phoenix Gang?",
                true,
                "Any ideas how I could get in there then?",
                false,
            )
        if (how) {
            chatPlayer(quiz, "How did you know I was in the Phoenix Gang?")
            chatNpc(
                shifty,
                "I've done the odd job for the Phoenix Gang myself. I'd be no good at my work if " +
                    "I didn't know who their members were.",
            )
            chatPlayer(quiz, "I see. So, any ideas how I could get in there?")
        } else {
            chatPlayer(quiz, "Any ideas how I could get in there then?")
        }
        someoneElse(ownGang = "Phoenix", otherGang = "Black Arm")
    }

    private suspend fun Dialogue.phoenixGang() {
        chatPlayer(quiz, Topic.PhoenixGang.option)
        if (player.charliePaid) {
            chatNpc(neutral, "Well, you did pay up, so I suppose I can tell you again.")
            phoenixDirections()
            return
        }
        if (player.charlieHalfPaid) {
            chatNpc(shifty, "You still owe me five coins for that, remember?")
            payRest()
            return
        }
        chatNpc(
            shifty,
            "Course I do. There's nothing about this city I don't know. And for ten coins I'll " +
                "happily share it.",
        )
        val choice =
            choice3(
                "Okay, that sounds fair.",
                1,
                "Ten coins? That's too much! I'll give you five.",
                2,
                "Never mind. I'll find it myself.",
                3,
            )
        when (choice) {
            1 -> {
                chatPlayer(neutral, "Okay, that sounds fair.")
                if (!pay(FULL_FEE, "You give ten coins to Charlie.")) {
                    return
                }
                player.charliePaid = true
                phoenixDirections()
            }
            2 -> {
                chatPlayer(angry, "Ten coins? That's too much! I'll give you five.")
                chatNpc(neutral, "Oh, go on then. I suppose that'll have to do.")
                if (!pay(HALF_FEE, "You give five coins to Charlie.")) {
                    return
                }
                player.charlieHalfPaid = true
                chatNpc(happy, "Cheers.")
                chatPlayer(quiz, "So where are the Phoenix Gang, then?")
                chatNpc(shifty, "Five more coins and I'll tell you.")
                payRest()
            }
            else -> {
                chatPlayer(neutral, "Never mind. I'll find it myself.")
                chatNpc(neutral, "Well, you know where I am if you get lost!")
            }
        }
    }

    private suspend fun Dialogue.payRest() {
        val pays =
            choice2(
                "That's not fair, but I guess I don't have a choice.",
                true,
                "You thieving gutter-scum! You'd better watch your back!",
                false,
            )
        if (!pays) {
            chatPlayer(angry, "You thieving gutter-scum! You'd better watch your back!")
            chatNpc(bored, "Yeah, yeah. You don't scare me, pal.")
            return
        }
        chatPlayer(sad, "That's not fair, but I suppose I've got no choice.")
        if (!pay(HALF_FEE, "You give five coins to Charlie.")) {
            return
        }
        player.charliePaid = true
        player.charlieHalfPaid = false
        phoenixDirections()
    }

    private suspend fun Dialogue.pay(amount: Int, message: String): Boolean {
        if (access.inv.count(COINS) < amount) {
            chatPlayer(sad, "Oh... I don't seem to have enough coins on me.")
            chatNpc(sad, "No money, no directions, guv.")
            return false
        }
        access.invDel(access.inv, COINS, amount)
        access.soundSynth(COINS_SOUND)
        objbox(COINS, message)
        return true
    }

    private suspend fun Dialogue.phoenixDirections() {
        chatNpc(
            neutral,
            "Listen carefully. The Phoenix Gang are east of here. Go down the southern alley " +
                "past the Blue Moon Inn and look for the building that belongs to the VTAM " +
                "Corporation.",
        )
        chatNpc(
            neutral,
            "That's their main hideout. Their weapon stash is two buildings further east. Mind " +
                "you, you'll not get into either without being one of them.",
        )
        chatPlayer(worried, "Without being one of them? That could be awkward.")
        someoneElse(ownGang = "Black Arm", otherGang = "Phoenix")
    }

    private suspend fun Dialogue.someoneElse(ownGang: String, otherGang: String) {
        chatNpc(
            neutral,
            "Your best bet is to find someone who isn't in the $ownGang Gang and have them " +
                "worm their way into the $otherGang Gang for you.",
        )
        chatPlayer(quiz, "Someone? Like who?")
        chatNpc(
            neutral,
            "There's no shortage of adventurers round here besides you. Ask one of them nicely " +
                "and I'm sure they'd help.",
        )
        chatPlayer(neutral, "I see...")
    }

    private suspend fun Dialogue.choose(options: List<Pair<String, Topic>>): Topic =
        when (options.size) {
            4 ->
                choice4(
                    options[0].first,
                    options[0].second,
                    options[1].first,
                    options[1].second,
                    options[2].first,
                    options[2].second,
                    options[3].first,
                    options[3].second,
                )
            else ->
                choice5(
                    options[0].first,
                    options[0].second,
                    options[1].first,
                    options[1].second,
                    options[2].first,
                    options[2].second,
                    options[3].first,
                    options[3].second,
                    options[4].first,
                    options[4].second,
                )
        }

    private enum class Topic(val option: String) {
        WhoAreYou("Who are you?"),
        Alleyway("Is there anything down this alleyway?"),
        BlackArmGang("I'm looking for the Black Arm Gang."),
        PhoenixGang("Any idea where the Phoenix Gang are based?"),
        NoChange("Sorry, I haven't got any."),
        GetAJob("Go get a job!"),
        GiveChange("Ok. Here you go."),
    }

    private companion object {
        const val CHARLIE = "npc.tramppg"
        const val COINS_SOUND = "synth.coins_jingle_1"
        const val FULL_FEE = 10
        const val HALF_FEE = 5
    }
}
