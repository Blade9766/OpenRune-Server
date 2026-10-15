package org.rsmod.content.quest.area.paterdomus.priestinperil.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.KING_ROALD
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.RECOMMENDED_COMBAT
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_AGREED_TO_KILL_DOG
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_CELL_UNLOCKED
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_KILLED_DOG
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_MET_DREZEL
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_ROALD_FURIOUS
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.paterdomus.priestinperil.hoodedMonkDead
import org.rsmod.content.quest.area.paterdomus.priestinperil.returnedToFakeDrezel
import org.rsmod.content.quest.area.paterdomus.priestinperil.returnedToRoald
import org.rsmod.content.quest.area.paterdomus.priestinperil.triedFakeKey
import org.rsmod.content.quest.area.varrock.shieldofarrav.ARRAV_OPTION
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest
import org.rsmod.content.quest.area.varrock.shieldofarrav.canAskKing
import org.rsmod.content.quest.area.varrock.shieldofarrav.kingShieldOfArrav
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * King Roald, in the throne room of Varrock Palace. He starts Priest in Peril and pays out the
 * Shield of Arrav reward.
 */
class KingRoald
@Inject
constructor(
    private val priestInPeril: PriestInPerilQuest,
    private val arrav: ShieldOfArravQuest,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(KING_ROALD) { startDialogue(it.npc) { roald() } }
        onOpNpcU(KING_ROALD) {
            if (arrav.canAskKing(this)) {
                startDialogue(it.npc) { kingShieldOfArrav(arrav) }
            } else {
                mes("Nothing interesting happens.")
            }
        }
    }

    private suspend fun Dialogue.roald() {
        val stage = priestInPeril.stage(player)
        val arravTopic = arrav.canAskKing(access)
        if (stage >= STAGE_COMPLETE) {
            afterQuest(arravTopic)
            return
        }
        chatPlayer(neutral, "Greetings, your majesty.")
        chatNpc(neutral, "Yes, citizen. Do you need something?")
        val job = if (stage == 0) "I'm looking for a quest!" else "About that job I'm doing..."
        val picked =
            if (arravTopic) {
                choice3(job, JOB, ARRAV_OPTION, ARRAV, "Not really.", NOTHING)
            } else {
                choice2(job, JOB, "Not really.", NOTHING)
            }
        when (picked) {
            JOB ->
                when (stage) {
                    0 -> offerQuest()
                    STAGE_STARTED -> jobOptions()
                    STAGE_AGREED_TO_KILL_DOG -> spokeToDrezel()
                    STAGE_KILLED_DOG -> killedTheDog()
                    STAGE_ROALD_FURIOUS -> stillNotSecure()
                    STAGE_MET_DREZEL -> freeingDrezel()
                    STAGE_CELL_UNLOCKED -> nationalSecurity()
                    else -> keepItUp()
                }
            ARRAV -> kingShieldOfArrav(arrav)
            else -> busy()
        }
    }

    private suspend fun Dialogue.busy() {
        chatPlayer(neutral, "Not really.")
        chatNpc(neutral, "You will have to excuse me then. I am very busy as I have a kingdom to run!")
    }

    private suspend fun Dialogue.offerQuest() {
        chatPlayer(happy, "I'm looking for a quest!")
        chatNpc(
            quiz,
            "A quest you say? Hmm... What an odd request to make of the King. It's funny you " +
                "should mention it though, as there is something you can do for me.",
        )
        chatNpc(
            neutral,
            "Are you aware of Paterdomus? It's a temple east of here. It stands on the holy River " +
                "Salve and guards the only passage into the deadly vampyre-infested lands of " +
                "Morytania.",
        )
        if (!choice2("Yes, I think I've heard of it.", true, "No.", false)) {
            chatPlayer(neutral, "No.")
            chatNpc(neutral, "Well, it is east of here. Come back if you would like to help.")
            return
        }
        chatPlayer(neutral, "Yes, I think I've heard of it.")
        chatNpc(
            neutral,
            "Very good. Well, it has been some days since last I heard from Drezel, the priest " +
                "who lives there. Be a sport and go check in on the silly old codger for me, would " +
                "you?",
        )
        if (player.combatLevel < RECOMMENDED_COMBAT) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RECOMMENDED_COMBAT.",
            )
        }
        if (!choice2("Yes.", true, "No.", false, title = "Start the Priest in Peril quest?")) {
            chatPlayer(bored, "No. That sounds boring.")
            chatNpc(
                neutral,
                "Yes, I dare say it does. I wouldn't even have mentioned it had you not seemed to " +
                    "be looking for something to do anyway.",
            )
            return
        }
        chatPlayer(neutral, "Sure. I don't have anything better to do right now.")
        player.returnedToRoald = false
        player.returnedToFakeDrezel = false
        player.hoodedMonkDead = false
        player.triedFakeKey = false
        priestInPeril.quest.advanceQuestStage(access)
        chatNpc(
            happy,
            "Many thanks, adventurer! I would have sent one of the squires, but they wanted " +
                "payment for it!",
        )
        questions(includeWhereToGo = false)
    }

    private suspend fun Dialogue.jobOptions() {
        chatPlayer(neutral, "About that job I'm doing...")
        chatNpc(quiz, "You have news of Drezel for me?")
        questions(includeWhereToGo = true)
    }

    private suspend fun Dialogue.questions(includeWhereToGo: Boolean) {
        while (true) {
            val picked =
                if (includeWhereToGo) {
                    choice4(
                        "Where am I supposed to go again?",
                        WHERE,
                        "Why do you care about Drezel anyway?",
                        WHY,
                        "Do I get a reward for this?",
                        REWARD,
                        "I have to go.",
                        LEAVE,
                    )
                } else {
                    choice3(
                        "Why do you care about Drezel anyway?",
                        WHY,
                        "Do I get a reward for this?",
                        REWARD,
                        "I'll get going.",
                        LEAVE,
                    )
                }
            when (picked) {
                WHERE -> whereToGo()
                WHY -> whyDrezel()
                REWARD -> reward()
                else -> {
                    chatPlayer(neutral, if (includeWhereToGo) "I have to go." else "I'll get going.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.whereToGo() {
        chatPlayer(quiz, "Where am I supposed to go again?")
        chatNpc(
            neutral,
            "The temple of Paterdomus where Drezel lives. It is but a short journey east from " +
                "here. It lies at the source of the River Salve. Don't worry, you can't miss it.",
        )
    }

    private suspend fun Dialogue.whyDrezel() {
        chatPlayer(quiz, "Why do you care about Drezel anyway?")
        chatNpc(
            angry,
            "Well, that is a slightly impertinent question to ask of your King, but I shall " +
                "overlook it this time.",
        )
        chatNpc(
            neutral,
            "As you are no doubt aware, this kingdom worships Saradomin, the god of wisdom and " +
                "order. As such, it is a peaceful place to live and prosper.",
        )
        chatNpc(
            neutral,
            "Paterdomus, the temple where Drezel lives, stands on the eastern border of " +
                "Misthalin. It guards the only passage into the evil lands of Morytania.",
        )
        chatPlayer(quiz, "Evil?")
        chatNpc(
            worried,
            "Oh yes. Morytania is a fearful place, filled to the brim with accursed servants of " +
                "the chaos god Zamorak. All of them are terrible, but none more so than the " +
                "rulers of the region, the vampyres.",
        )
        chatNpc(
            neutral,
            "Thankfully, the sacred River Salve marks a natural border between Misthalin and " +
                "Morytania. The waters of the river have been blessed with Saradomin's almighty " +
                "power, preventing any invasion by the vampyres.",
        )
        chatNpc(
            neutral,
            "Drezel is descended from one of the original Saradominist priests who first blessed " +
                "the river. His job is to ensure nothing happens to the river that might allow " +
                "the evil of Morytania to invade this land.",
        )
        chatNpc(
            neutral,
            "This is the reason why the lack of communication from him bothers me somewhat, " +
                "although I am sure it's nothing too serious. Nobody would dare to try and attack " +
                "our kingdom!",
        )
    }

    private suspend fun Dialogue.reward() {
        chatPlayer(quiz, "Do I get a reward for this?")
        chatNpc(
            neutral,
            "You will be rewarded with the knowledge that you have done the right thing and " +
                "assisted the King of Misthalin.",
        )
        chatPlayer(shifty, "So... that would be a 'no' then?")
        chatNpc(neutral, "That is correct.")
    }

    private suspend fun Dialogue.spokeToDrezel() {
        chatPlayer(neutral, "About that job I'm doing...")
        chatNpc(quiz, "You have news of Drezel for me?")
        chatPlayer(
            shifty,
            "Well... I went to the temple like you asked me to... and I spoke to someone inside...",
        )
        chatNpc(neutral, "Ah, well that must have been Drezel then. What did he say?")
        chatPlayer(worried, "Well... he seemed to be having some kind of trouble. He asked for my help.")
        chatNpc(neutral, "Well, I expect you to offer him your full assistance in whatever he needs.")
        chatPlayer(worried, "But-")
        chatNpc(
            angry,
            "Now, run along. I have a lot to do and it sounds like Drezel needs you.",
        )
        chatPlayer(sad, "Well... okay then.")
        player.returnedToRoald = true
    }

    private suspend fun Dialogue.killedTheDog() {
        chatPlayer(neutral, "About that job I'm doing...")
        chatNpc(quiz, "You have news of Drezel for me?")
        chatPlayer(
            neutral,
            "Well, I went to the temple and spoke to... Drezel. He asked me to kill a dog in the " +
                "mausoleum.",
        )
        chatNpc(shocked, "A dog? Tell me you didn't do it?!")
        chatPlayer(worried, "Well...")
        chatNpc(verymad, "Are you a complete imbecile?")
        if (player.returnedToRoald) {
            chatPlayer(
                angry,
                "I tried to tell you it was a bit suspicious, but you told me to help Drezel!",
            )
        } else {
            chatPlayer(worried, "It did all seem a bit suspicious...")
        }
        chatNpc(
            verymad,
            "Do you even realise what you've done? That mausoleum contains the only passage " +
                "between Morytania and Misthalin! Not only that, it's built right over the source " +
                "of the River Salve!",
        )
        chatNpc(
            verymad,
            "That 'dog' was responsible for guarding the entrance to the mausoleum! With it " +
                "gone, there's nothing to stop someone from sabotaging the blessings on the river. " +
                "Thanks to you, all of Misthalin is now at risk!",
        )
        chatPlayer(worried, "But Drezel...")
        chatNpc(
            verymad,
            "Do you not see what's happened here, you absolute cretin! Obviously some fiend has " +
                "done something to Drezel and tricked your feeble intellect into helping them!",
        )
        priestInPeril.advanceTo(access, STAGE_ROALD_FURIOUS)
        chatNpc(
            angry,
            "Now you get back there and get this mess sorted out. If you don't do whatever is " +
                "necessary to safeguard this kingdom from attack, I will see you beheaded for " +
                "high treason!",
        )
        chatPlayer(worried, "Y-yes, your highness.")
    }

    private suspend fun Dialogue.stillNotSecure() {
        chatPlayer(neutral, "About that job I'm doing...")
        chatNpc(angry, "Why haven't you ensured the border with Morytania is secure yet?")
        chatPlayer(sad, "Okay, okay... I'm going, I'm going... There's no need to shout...")
        chatNpc(
            verymad,
            "No need to shout?! Listen, and listen well, and see if your puny mind can comprehend " +
                "this: if the border is not protected, then we are all at the mercy of the " +
                "vampyres!",
        )
        chatNpc(
            angry,
            "I would say that me shouting at you for your incompetence is the least of your " +
                "worries right now. Now get yourself back to Paterdomus! At once!",
        )
    }

    private suspend fun Dialogue.freeingDrezel() {
        chatPlayer(neutral, "About that job I'm doing...")
        chatNpc(quiz, "Is the border with Morytania secure yet?")
        chatPlayer(
            neutral,
            "Not yet, but I'm working on it. First I need to free Drezel. He's been imprisoned by " +
                "some Zamorakian monks.",
        )
        chatNpc(
            angry,
            "What? This is wholly unacceptable! I order you to do all that you can to free Drezel " +
                "immediately!",
        )
        chatPlayer(neutral, "Yes, as I said, that's what I'm working on.")
        chatNpc(happy, "Good work! Always a place for quick thinkers in my kingdom!")
    }

    private suspend fun Dialogue.nationalSecurity() {
        chatPlayer(neutral, "About that job I'm doing...")
        chatNpc(quiz, "Is the border with Morytania secure yet?")
        chatPlayer(neutral, "Not yet, but Drezel and I are working on it.")
        chatNpc(
            angry,
            "Well, best you get back to it then. This is a matter of national security and if I " +
                "find out we are vulnerable I will hold you personally responsible!",
        )
        chatPlayer(worried, "Yes, your highness.")
    }

    private suspend fun Dialogue.keepItUp() {
        chatPlayer(neutral, "About that job I'm doing...")
        chatNpc(quiz, "Is the border with Morytania secure yet?")
        chatPlayer(neutral, "Not yet, but Drezel and I are working on it.")
        chatNpc(happy, "Good, good. Keep up the good work.")
    }

    private suspend fun Dialogue.afterQuest(arravTopic: Boolean) {
        chatNpc(neutral, "Yes, citizen. Do you need something?")
        val picked =
            if (arravTopic) {
                choice3("About that job you gave me...", JOB, ARRAV_OPTION, ARRAV, "Not really.", NOTHING)
            } else {
                choice2("About that job you gave me...", JOB, "Not really.", NOTHING)
            }
        if (picked == ARRAV) {
            kingShieldOfArrav(arrav)
            return
        }
        if (picked != JOB) {
            busy()
            return
        }
        chatPlayer(neutral, "About that job you gave me...")
        chatNpc(
            happy,
            "Ah, yes, we received word from Drezel. I hear the Salve is safe once more. Very good " +
                "work!",
        )
        chatPlayer(
            quiz,
            "Thank you. Will you be sending some soldiers over to sort out those Zamorakians?",
        )
        chatNpc(
            neutral,
            "Yes, yes, we'll make sure they're dealt with. Might take a little while, mind you. " +
                "Lots of priorities to balance around here. I'm sure we'll have them gone before " +
                "the end of the year though.",
        )
        chatPlayer(shifty, "Right...")
    }

    private companion object {
        const val JOB = 1
        const val ARRAV = 2
        const val NOTHING = 3

        const val WHERE = 1
        const val WHY = 2
        const val REWARD = 3
        const val LEAVE = 4
    }
}
