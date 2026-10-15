package org.rsmod.content.quest.area.rellekka.fremenniktrials.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.FISHERMAN
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.SAILOR
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.SKULGRIMEN
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.THORA
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.YRSA
import org.rsmod.content.quest.area.rellekka.fremenniktrials.MerchantContact
import org.rsmod.content.quest.area.rellekka.fremenniktrials.MerchantTrial
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Rellekka folk who are not on the trials' council or only take part in the Merchant's trial:
 * the traders, the sailor and the fisherman, and the townsfolk who all get asked whether they sit
 * on the council.
 */
class RellekkaTownsfolk
@Inject
constructor(
    private val quest: FremennikTrialsQuest,
    private val merchant: MerchantTrial,
    private val shops: RellekkaShops,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(THORA) { startDialogue(it.npc) { thora() } }
        onOpNpc3(THORA) { with(shops) { trade(it.npc, RellekkaShop.Longhall) } }
        onOpNpc1(YRSA) { startDialogue(it.npc) { yrsa() } }
        onOpNpc1(SKULGRIMEN) { startDialogue(it.npc) { skulgrimen() } }
        onOpNpc3(SKULGRIMEN) { with(shops) { trade(it.npc, RellekkaShop.BattleGear) } }
        onOpNpc1(FISHERMAN) { startDialogue(it.npc) { fisherman() } }
        onOpNpc1(SAILOR) { startDialogue(it.npc) { sailor() } }
        onOpNpc1(FISH_MONGER) { startDialogue(it.npc) { trader(RellekkaShop.FishMonger) } }
        onOpNpc3(FISH_MONGER) { with(shops) { trade(it.npc, RellekkaShop.FishMonger) } }
        onOpNpc1(FUR_TRADER) { startDialogue(it.npc) { trader(RellekkaShop.FurTrader) } }
        onOpNpc3(FUR_TRADER) { with(shops) { trade(it.npc, RellekkaShop.FurTrader) } }
        for ((npc, lines) in VILLAGERS) {
            onOpNpc1(npc) { startDialogue(it.npc) { villager(lines) } }
        }
    }

    private suspend fun Dialogue.contact(
        contact: MerchantContact,
        otherwise: suspend Dialogue.() -> Unit,
    ) {
        when {
            quest.isComplete(player) -> fremennikGreeting()
            !quest.isStarted(player) -> outerlanderRebuff()
            else -> with(merchant) { withMerchantOption(contact, otherwise) }
        }
    }

    private suspend fun Dialogue.fremennikGreeting() {
        chatNpc(happy, "Greetings, ${quest.fremennikName(player)}! What can I do for you?")
    }

    private suspend fun Dialogue.thora() =
        contact(MerchantContact.Thora) {
            chatPlayer(quiz, "Hi. Are you a member of the council?")
            chatNpc(neutral, "I am afraid not outerlander.")
            chatPlayer(neutral, "Oh, okay then.")
        }

    private suspend fun Dialogue.yrsa() =
        contact(MerchantContact.Yrsa) {
            chatPlayer(quiz, "Hello, are you a member of the council?")
            chatNpc(neutral, "I am afraid not outerlander. Goodbye.")
        }

    private suspend fun Dialogue.skulgrimen() =
        contact(MerchantContact.Skulgrimen) {
            chatPlayer(quiz, "Are you a member of the council of elders?")
            chatNpc(neutral, "No. Weapons are my speciality. Not politics.")
            chatPlayer(neutral, "Okay, thanks.")
        }

    private suspend fun Dialogue.sailor() =
        contact(MerchantContact.Sailor) {
            chatPlayer(quiz, "Hello, are you a member of the council?")
            chatNpc(neutral, "I am afraid not outerlander. Goodbye.")
        }

    private suspend fun Dialogue.fisherman() =
        contact(MerchantContact.Fisherman) {
            chatPlayer(quiz, "Are you a member of the Fremmenik council?")
            chatNpc(neutral, "Indeed I am, outerlander. Why do you ask?")
            chatPlayer(
                neutral,
                "Well, I was told by Brundt that I could be voted into the tribe as an honorary " +
                    "Fremmenik if I could find seven members of the council of elders to vote in my " +
                    "favour.",
            )
            chatNpc(neutral, "Aye, that is indeed true.")
            chatPlayer(quiz, "So can I have your vote?")
            chatNpc(
                laugh,
                "You can have my vote on the day the skies turn red, the waters turn pink, the rocks " +
                    "turn yellow and the sun turns black.",
            )
            chatPlayer(confused, "Um... do you have any estimate on when that is?")
            chatNpc(angry, "I'm telling you I will never vote for you, you stupid outlander.")
        }

    private suspend fun Dialogue.trader(shop: RellekkaShop) {
        if (!quest.isComplete(player)) {
            chatNpc(neutral, "I don't trade with outerlanders.")
            return
        }
        chatNpc(happy, "Greetings, ${quest.fremennikName(player)}! Would you like to see my wares?")
        val trader = npc ?: return
        if (choice2("Yes please.", true, "No thanks.", false)) {
            with(shops) { access.trade(trader, shop) }
        }
    }

    private suspend fun Dialogue.villager(lines: suspend Dialogue.() -> Unit) {
        if (quest.isComplete(player)) {
            fremennikGreeting()
            return
        }
        if (!quest.isStarted(player)) {
            outerlanderRebuff()
            return
        }
        lines()
    }

    private companion object {
        const val FISH_MONGER = "npc.viking_fish_monger"
        const val FUR_TRADER = "npc.viking_fur_monger"

        fun lines(block: suspend Dialogue.() -> Unit) = block

        val notACouncillor = lines {
            chatPlayer(quiz, "Hello, are you a member of the council?")
            chatNpc(neutral, "I am afraid not outerlander. Goodbye.")
        }

        val VILLAGERS: Map<String, suspend Dialogue.() -> Unit> =
            mapOf(
                "npc.viking_reveller" to
                    lines {
                        chatPlayer(
                            quiz,
                            "I know this is a bit of a long shot... but are you a member of the " +
                                "council of elders?",
                        )
                        chatNpc(
                            drunk,
                            "I'm now a member of the counshil of eblars? Thanksh a lot buddy! " +
                                "(hic) Letsh have a drink to celebrate!",
                        )
                        chatPlayer(confused, "Uh... I'll take that as a no.")
                    },
                "npc.viking_reveller_2" to
                    lines {
                        chatPlayer(
                            quiz,
                            "I know this is a bit of a long shot... but are you a member of the " +
                                "council of elders?",
                        )
                        chatNpc(
                            drunk,
                            "Counshil alwaysh meshing up my drinksh 'do thish, do that, pass this " +
                                "tesht something something something something'.. man I hate that " +
                                "counshil.",
                        )
                        chatPlayer(confused, "I guess you're not then.")
                    },
                "npc.viking_woman_indoors" to
                    lines {
                        chatPlayer(
                            quiz,
                            "Hello. I'm looking for a member of the council of elders who will " +
                                "support my application to become an honorary Fremennik.",
                        )
                        chatNpc(neutral, "I'm a council member.")
                        chatPlayer(
                            quiz,
                            "So can I somehow persuade you to vote for me at the council of elders?",
                        )
                        chatNpc(angry, "No. Frankly, I don't like you.")
                        chatPlayer(shocked, "But you don't even know me!")
                        chatNpc(
                            angry,
                            "You also smell bad. And have stupid hair. And bad breath. And you walk " +
                                "a bit funny.",
                        )
                        chatPlayer(angry, "I didn't come all the way up here just to be insulted!")
                        chatNpc(
                            neutral,
                            "No, I'm quite sure you could have found plenty of people to insult you " +
                                "wherever your home is.",
                        )
                        chatPlayer(
                            angry,
                            "You know what? Forget you, you stupid barbarian! I don't need your " +
                                "stupid vote!",
                        )
                        chatNpc(
                            angry,
                            "Tch, typical outerlander rudeness. I'm glad I didn't vote for you now.",
                        )
                    },
                "npc.viking_woman4" to
                    lines {
                        chatPlayer(quiz, "Do you know any council members?")
                        chatNpc(neutral, "I know that I am one.")
                        chatPlayer(
                            happy,
                            "Great! Can you vote for me at the council of elders for me to become a " +
                                "Fremennik?",
                        )
                        chatNpc(neutral, "Sure. On one condition.")
                        chatPlayer(bored, "(sigh) What condition?")
                        chatNpc(
                            laugh,
                            "Actually there is no condition! I don't want to vote for you! You seem " +
                                "too gullible to be a Fremennik!",
                        )
                        chatPlayer(sad, "It's kind of mean to trick me like that...")
                        chatNpc(
                            angry,
                            "What do I care? I am a Fremennik and you are just some stupid " +
                                "outerlander! You stupid outerlander!",
                        )
                    },
                "npc.viking_man" to notACouncillor,
                "npc.viking_man2" to notACouncillor,
                "npc.viking_man3" to notACouncillor,
                "npc.viking_man4" to notACouncillor,
                "npc.viking_man5" to notACouncillor,
                "npc.viking_woman" to notACouncillor,
                "npc.viking_woman2" to notACouncillor,
                "npc.viking_woman3" to notACouncillor,
                "npc.viking_heckler" to notACouncillor,
                "npc.viking_heckler_2" to notACouncillor,
                "npc.viking_heckler_3" to notACouncillor,
                "npc.viking_heckler_4" to notACouncillor,
            )
    }
}
