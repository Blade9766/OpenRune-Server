package org.rsmod.content.quest.area.rellekka.fremenniktrials.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.SIGMUND
import org.rsmod.content.quest.area.rellekka.fremenniktrials.MerchantStep
import org.rsmod.content.quest.area.rellekka.fremenniktrials.MerchantTrial.Companion.EXOTIC_FLOWER
import org.rsmod.content.quest.area.rellekka.fremenniktrials.Trial
import org.rsmod.content.quest.area.rellekka.fremenniktrials.ftSigmundStep
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Sigmund the Merchant, who wants the exotic flower that starts the Merchant's trial. */
class Sigmund
@Inject
constructor(private val quest: FremennikTrialsQuest, private val shops: RellekkaShops) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(SIGMUND) { startDialogue(it.npc) { talk() } }
        onOpNpc3(SIGMUND) { with(shops) { trade(it.npc, RellekkaShop.GeneralStore) } }
    }

    private suspend fun Dialogue.talk() {
        when {
            quest.hasVote(player, Trial.Merchant) -> {
                chatPlayer(happy, "Hello there!")
                chatNpc(
                    happy,
                    "Hello again outerlander! I am amazed once more at your apparent skill at " +
                        "merchanting!",
                )
                chatPlayer(quiz, "So I can count on your vote at the council of elders?")
                chatNpc(
                    happy,
                    "Absolutely, outerlander. Your merchanting skills will be a real boon to the " +
                        "Fremennik.",
                )
            }
            !quest.isStarted(player) -> outerlanderRebuff()
            EXOTIC_FLOWER in player.inv -> {
                chatPlayer(happy, "Hello there!")
                chatPlayer(happy, "Here's that flower you wanted.")
                access.invDel(access.inv, EXOTIC_FLOWER)
                chatNpc(
                    happy,
                    "Incredible! Your merchanting skills might even match my own! I have no choice " +
                        "but to recommend you to the council of elders!",
                )
                quest.grantVote(access, Trial.Merchant)
            }
            player.ftSigmundStep >= MerchantStep.FIND_FLOWER -> {
                chatPlayer(happy, "Hello there!")
                chatNpc(
                    quiz,
                    "So... how goes it outerlander? Did you manage to obtain my flower for me yet? " +
                        "Or do you lack the necessary merchanting skills?",
                )
                chatPlayer(
                    neutral,
                    "I'm still working on it... Do you have any suggestion where to start looking " +
                        "for it?",
                )
                chatNpc(
                    neutral,
                    "I suggest you ask around the other Fremennik in the town. A good merchant will " +
                        "find exactly what their customer needs somewhere.",
                )
            }
            else -> offerTrial()
        }
    }

    private suspend fun Dialogue.offerTrial() {
        chatPlayer(happy, "Hello there!")
        chatNpc(neutral, "Hello outerlander.")
        chatPlayer(quiz, "Are you a member of the council?")
        chatNpc(
            neutral,
            "That I am outerlander; it is a position that brings my family and I pride.",
        )
        chatPlayer(quiz, "I was wondering if I can count on your vote at the council of elders?")
        chatNpc(
            neutral,
            "You wish to become a Fremennik? I may be persuaded to swing my vote to your favour, " +
                "but you will first need to do a little task for me.",
        )
        chatPlayer(bored, "How did I know it wouldn't be that simple for your vote?")
        chatNpc(
            neutral,
            "Calm yourself outerlander. It is but a small task really... I simply require a flower.",
        )
        chatPlayer(quiz, "A flower? What's the catch?")
        chatNpc(
            neutral,
            "The catch? Well... it is not just any flower. Someone in this town has an extremely " +
                "rare flower from a far off land that they picked up on their travels.",
        )
        chatNpc(
            neutral,
            "I would like you to demonstrate your merchanting skills to me, by persuading them to " +
                "part with it, and then give it to me for my vote.",
        )
        chatPlayer(neutral, "Well... I guess that doesn't sound too hard...")
        chatNpc(happy, "Excellent! You will obtain this rare flower for me then?")
        if (!choice2("Yes", true, "No", false)) {
            chatPlayer(
                angry,
                "You know what? No. This all sounds like a lot of hassle to me, and frankly I just " +
                    "can't be bothered with it right now.",
            )
            chatPlayer(neutral, "I'll go get someone else to vote for me.")
            chatNpc(
                neutral,
                "As you wish outerlander. If you change your mind, come and see me again; I am very " +
                    "interested in getting my hands on that flower.",
            )
            return
        }
        chatPlayer(
            neutral,
            "Okay. I don't think this will be too difficult. Any suggestions on where to start " +
                "looking for this flower?",
        )
        player.ftSigmundStep = MerchantStep.FIND_FLOWER
        chatNpc(
            laugh,
            "Ah, well outerlander, if I knew where to start looking I would simply do it myself!",
        )
        chatPlayer(angry, "No help at ALL?")
        chatNpc(
            neutral,
            "We are a very insular clan, so I would not expect you to have to leave this town to " +
                "find whatever you need.",
        )
    }
}

/** What any Fremennik says to an outerlander who has not yet asked Brundt about joining the clan. */
internal suspend fun Dialogue.outerlanderRebuff() {
    chatNpc(
        neutral,
        "I am forbidden to speak with outerlanders. If you have business here, take it up with our " +
            "chieftain, Brundt.",
    )
}
