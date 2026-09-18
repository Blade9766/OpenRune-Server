package org.rsmod.content.quest.area.rellekka.fremenniktrials

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.COINS
import org.rsmod.game.entity.Player

/**
 * Values of [ftSigmundStep]. Steps 1..13 are the favours asked in turn; from 14 each is an item
 * handed over on the way back to Sigmund.
 */
object MerchantStep {
    const val FIND_FLOWER = 1
    const val BALLAD = 2
    const val BOOTS = 3
    const val TAX_CUT = 4
    const val HUNTING_MAP = 5
    const val BOWSTRING = 6
    const val FISH = 7
    const val SEA_MAP = 8
    const val FORECAST = 9
    const val BODYGUARD = 10
    const val TOKEN = 11
    const val COCKTAIL = 12
    const val PROMISE = 13
    const val NOTE_BOUGHT = 14
    const val COCKTAIL_MADE = 15
    const val TOKEN_GIVEN = 16
    const val CONTRACT_GIVEN = 17
    const val FORECAST_GIVEN = 18
    const val SEA_MAP_GIVEN = 19
    const val FISH_GIVEN = 20
    const val BOWSTRING_GIVEN = 21
    const val HUNTING_MAP_GIVEN = 22
    const val FISCAL_GIVEN = 23
    const val BOOTS_GIVEN = 24
    const val BALLAD_GIVEN = 25
    const val FLOWER_GIVEN = 26
}

/** The Rellekka folk the player can ask about Sigmund's flower. */
enum class MerchantContact(val noIdea: String) {
    Sailor("Not really my affair, outerlander."),
    Olaf("I'm sorry, no I don't."),
    Yrsa("No... sorry, I don't."),
    Brundt("Not really my affair, outerlander."),
    Sigli("No idea at all outerlander."),
    Skulgrimen("I never saw anything like that in my store before."),
    Fisherman("I never saw anything like that in my store before."),
    Swensen("I cannot help you with that, outerlander."),
    Peer("I'm afraid not, outerlander."),
    Thorvald("Not in the slightest."),
    Manni("I have not the first idea about that."),
    Thora("I wouldn't know about that, outerlander."),
    Askeladden("Nope, sorry buddy!"),
}

/**
 * Sigmund's "small favour": his exotic flower belongs to a sailor, who wants a ballad from Olaf,
 * who wants boots from Yrsa, and so on down a chain of thirteen Fremennik until Askeladden sells a
 * promise to stay out of the longhall. The player then carries each item back up the chain.
 *
 * While the trial is running, every contact offers "Ask about the Merchant's trial" beside their
 * usual conversation. Whoever holds the next link reveals their price when asked; anyone handed
 * the item they asked for trades it for theirs. Losing an item means buying Askeladden's note again
 * and carrying it back up.
 */
@Singleton
class MerchantTrial @Inject constructor(private val quest: FremennikTrialsQuest) {

    fun isActive(player: Player): Boolean =
        quest.isInProgress(player) &&
            !player.voted(Trial.Merchant) &&
            player.ftSigmundStep in MerchantStep.FIND_FLOWER until MerchantStep.FLOWER_GIVEN

    /**
     * Offers the Merchant's trial alongside [otherwise] when the trial is running, else goes
     * straight to [otherwise].
     */
    suspend fun Dialogue.withMerchantOption(
        contact: MerchantContact,
        otherwise: suspend Dialogue.() -> Unit,
    ) {
        if (!isActive(player)) {
            otherwise()
            return
        }
        val merchant =
            choice2("Ask about the Merchant's trial", true, "Ask about becoming a Fremennik", false)
        if (merchant) {
            merchantTalk(contact)
        } else {
            otherwise()
        }
    }

    suspend fun Dialogue.merchantTalk(contact: MerchantContact) {
        if (contact == MerchantContact.Sigli || contact == MerchantContact.Swensen) {
            chatNpc(neutral, "Greetings outerlander.")
        }
        val step = player.ftSigmundStep
        if (trade(contact)) {
            return
        }
        if (reveal(contact, step)) {
            return
        }
        if (reminder(contact, step)) {
            return
        }
        if (afterTrade(contact, step)) {
            return
        }
        chatPlayer(quiz, "I don't suppose you have any idea where I could find ${wanted()}, do you?")
        chatNpc(neutral, contact.noIdea)
    }

    /** What the player is currently asking around for. */
    private fun Dialogue.wanted(): String {
        val step = player.ftSigmundStep
        if (step <= MerchantStep.PROMISE) {
            return WANTED.getValue(step)
        }
        val held = HANDOVERS.firstOrNull { it.given in player.inv }
        return held?.let { WANTED.getValue(it.wantedStep) } ?: WANTED.getValue(MerchantStep.PROMISE)
    }

    private fun Dialogue.advanceTo(step: Int) {
        if (player.ftSigmundStep < step) {
            player.ftSigmundStep = step
        }
    }

    /* Carrying the items back up the chain. */

    private suspend fun Dialogue.trade(contact: MerchantContact): Boolean {
        val handover = HANDOVERS.firstOrNull { it.contact == contact } ?: return false
        if (handover.given !in player.inv) {
            return false
        }
        when (contact) {
            MerchantContact.Thora -> {
                chatPlayer(happy, "Hi! Can I please have one of your legendary cocktails now?")
                chatNpc(
                    shocked,
                    "What?!?! I can't believe you... Let me look at that... Askeladden would " +
                        "NEVER... Gosh. It looks legitimate.",
                )
                chatNpc(
                    happy,
                    "Here you go, on the house! You have made my life SO much easier! Knowing " +
                        "that little monster won't be bugging me in here all the time anymore!",
                )
                handOver(handover)
                chatNpc(
                    happy,
                    "That little weasel will have to abide by this written promise that " +
                        "Askeladden can never ever enter the Longhall again! He can't get round " +
                        "this one!",
                )
                chatPlayer(
                    shifty,
                    "Uh... yeah... yeah, you probably won't see someone called Askeladden coming " +
                        "in here...",
                )
            }
            MerchantContact.Manni -> {
                chatPlayer(happy, "Hey. I got your cocktail for you.")
                chatNpc(
                    shocked,
                    "...It is true! The legendary cocktail! I have waited for this day ever since " +
                        "I first started drinking!",
                )
                chatNpc(
                    happy,
                    "Here outerlander, you may take my token. I will happily give up my place at " +
                        "the longhalls table of champions just for a taste of this exquisite " +
                        "beverage!",
                )
                handOver(handover)
                chatPlayer(confused, "It's just a drink...")
                chatNpc(
                    neutral,
                    "No, it is an artform. A drink such as this should be appreciated, and " +
                        "admired. It is like a fine painting, or a tasteful sculpture.",
                )
                chatNpc(
                    neutral,
                    "If what I hear is true, then all other drinks become like unpalatable water in " +
                        "comparison to this!",
                )
                chatPlayer(happy, "I guess you're happy with the trade then!")
            }
            MerchantContact.Thorvald -> {
                chatPlayer(neutral, "I would like your contract to offer your services as a bodyguard.")
                chatNpc(
                    angry,
                    "Oh you would, would you outerlander? I have already told you, I will not " +
                        "demean myself with such a baby sitting job until I can sit in the Longhall " +
                        "with pride.",
                )
                chatPlayer(
                    happy,
                    "It's a good thing I have the Champions' Token right here then, isn't it?",
                )
                chatNpc(
                    shocked,
                    "Ah... well this is a different matter. With that token I can claim my " +
                        "rightful place as a champion in the Long hall!",
                )
                chatNpc(
                    neutral,
                    "Here outerlander, I can suffer the indignity of playing babysitter if it " +
                        "means that I can then revel with my warrior equals in the Long Hall " +
                        "afterwards!",
                )
                chatNpc(
                    neutral,
                    "Here outerlander, take this contract; I will fulfill it to my utmost.",
                )
                handOver(handover)
            }
            MerchantContact.Peer -> {
                chatPlayer(quiz, "Can I have a weather forecast now please?")
                chatNpc(
                    neutral,
                    "I have already told you outerlander; You may have a reading from me when I " +
                        "have a signed contract from a warrior guaranteeing my protection.",
                )
                chatPlayer(happy, "Yeah, I know; I have one right here from Thorvald.")
                chatNpc(
                    shocked,
                    "You have not only persuaded one of the Fremennik to act as a servant to me, " +
                        "but you have enlisted the aid of mighty Thorvald himself???",
                )
                chatNpc(
                    happy,
                    "You may take this forecast with my blessing outerlander. You have offered me " +
                        "the greatest security I can imagine.",
                )
                handOver(handover)
            }
            MerchantContact.Swensen -> {
                chatPlayer(neutral, "I would like your map of fishing spots.")
                chatNpc(
                    angry,
                    "I have already told you outerlander; I will not exchange it for anything " +
                        "other than a divination on the weather from our seer himself!",
                )
                chatPlayer(happy, "What, like this one I have here?")
                chatNpc(
                    shocked,
                    "W-what...? I don't believe it! How did you...? I suppose it doesn't matter, " +
                        "you have my gratitude outerlander!",
                )
                chatNpc(
                    happy,
                    "With this forecast I will be able to plan a safe course for our next raiding " +
                        "expedition!",
                )
                chatNpc(
                    happy,
                    "Here, outerlander; you may take my map of local fishing patterns with my " +
                        "gratitude!",
                )
                handOver(handover)
            }
            MerchantContact.Fisherman -> {
                chatPlayer(happy, "Here. I got you your map.")
                chatNpc(
                    happy,
                    "Great work outerlander! With this, I can finally catch enough fish to make an " +
                        "honest living from it! Here, have the stupid rare fish.",
                )
                handOver(handover)
            }
            MerchantContact.Skulgrimen -> {
                chatPlayer(
                    happy,
                    "Hi there. I got your fish, so can I have that bowstring for Sigli now?",
                )
                chatNpc(
                    happy,
                    "Ohh... That's a nice fish. Very pleased. Here. Take the bowstring. You " +
                        "fulfilled agreement. only fair I do same. Good work outerlander.",
                )
                handOver(handover)
                chatPlayer(happy, "Thanks!")
            }
            MerchantContact.Sigli -> {
                chatPlayer(
                    neutral,
                    "Here. I have your bowstring. Give me your map to the hunting grounds.",
                )
                chatNpc(
                    neutral,
                    "Well met, outerlander. I see some hunting potential within you. Here, take my " +
                        "map, I was getting too dependent on it for my skill anyway.",
                )
                handOver(handover)
            }
            MerchantContact.Brundt -> {
                chatPlayer(happy, "I got Sigli's hunting map for you.")
                chatNpc(
                    happy,
                    "Excellent work outerlander! And so quickly, too! Here, you may take my " +
                        "financial report promising reduced sales taxes on all goods.",
                )
                handOver(handover)
            }
            MerchantContact.Yrsa -> {
                chatPlayer(
                    neutral,
                    "Hello. Can I have those boots now? Here is a written statement from Brundt " +
                        "outlining future tax burdens upon Fremennik merchants and shopkeepers for " +
                        "the year.",
                )
                chatNpc(
                    happy,
                    "Certainly! Let me have a look at what he has written here, just give me a " +
                        "moment...",
                )
                handOver(handover)
                chatNpc(happy, "Yes, that all appears in order. Tell Olaf to come to me next time for shoes!")
            }
            MerchantContact.Olaf -> {
                chatPlayer(quiz, "Hello Olaf. Do you have a beautiful love song written for me?")
                chatNpc(
                    quiz,
                    "That depends outerlander... Do you have some new boots for me? My feet get so " +
                        "tired roaming the land...",
                )
                chatPlayer(happy, "As a matter of fact - I do!")
                chatNpc(
                    happy,
                    "Oh! Superb! Those are great! They're just what I was looking for! Here, take " +
                        "this song with my compliments! It is one of my finest works yet!",
                )
                handOver(handover)
            }
            MerchantContact.Sailor -> {
                chatPlayer(
                    happy,
                    "You'll be glad to know I have had a love song written just for you by Olaf. " +
                        "So can I have that flower of yours now?",
                )
                chatNpc(
                    neutral,
                    "Oh. It's by Olaf? Hmm. Well, a deal's a deal. I just hope it's better than the " +
                        "usual rubbish he comes up with, or my chances are worse than ever.",
                )
                handOver(handover)
            }
            MerchantContact.Askeladden -> return false
        }
        return true
    }

    private suspend fun Dialogue.handOver(handover: Handover) {
        access.invReplace(access.inv, handover.given, 1, handover.received)
        advanceTo(handover.step)
        objbox(handover.received, handover.message)
    }

    /* Each contact's price, the first time the player asks at the right point in the chain. */

    private suspend fun Dialogue.reveal(contact: MerchantContact, step: Int): Boolean {
        when {
            contact == MerchantContact.Sailor && step == MerchantStep.FIND_FLOWER -> sailorReveal()
            contact == MerchantContact.Olaf && step == MerchantStep.BALLAD -> olafReveal()
            contact == MerchantContact.Yrsa && step == MerchantStep.BOOTS -> yrsaReveal()
            contact == MerchantContact.Brundt && step == MerchantStep.TAX_CUT -> brundtReveal()
            contact == MerchantContact.Sigli && step == MerchantStep.HUNTING_MAP -> sigliReveal()
            contact == MerchantContact.Skulgrimen && step == MerchantStep.BOWSTRING -> skulgrimenReveal()
            contact == MerchantContact.Fisherman && step == MerchantStep.FISH -> fishermanReveal()
            contact == MerchantContact.Swensen && step == MerchantStep.SEA_MAP -> swensenReveal()
            contact == MerchantContact.Peer && step == MerchantStep.FORECAST -> peerReveal()
            contact == MerchantContact.Thorvald && step == MerchantStep.BODYGUARD -> thorvaldReveal()
            contact == MerchantContact.Manni && step == MerchantStep.TOKEN -> manniReveal()
            contact == MerchantContact.Thora && step == MerchantStep.COCKTAIL -> thoraReveal()
            contact == MerchantContact.Askeladden && sellsNote(step) -> askeladdenSellsNote()
            else -> return false
        }
        return true
    }

    private fun Dialogue.sellsNote(step: Int): Boolean {
        if (step == MerchantStep.PROMISE) {
            return true
        }
        if (step < MerchantStep.NOTE_BOUGHT || step >= MerchantStep.FLOWER_GIVEN) {
            return false
        }
        return HANDOVERS.none { it.given in player.inv } && PROMISSORY_NOTE !in player.inv
    }

    private suspend fun Dialogue.sailorReveal() {
        chatPlayer(
            quiz,
            "I don't suppose you have any idea where I could find a rare flower from across the " +
                "sea, do you?",
        )
        chatNpc(
            happy,
            "Ah! Even the outerlanders have heard of my mysterious flower! I found it in a country " +
                "far far away from here!",
        )
        chatPlayer(quiz, "Can I buy it from you?")
        chatNpc(
            neutral,
            "I'm afraid not, outerlander. There is a woman in this village whose heart I seek to " +
                "capture, and I think giving her this strange flower might be my best bet with her.",
        )
        chatPlayer(
            neutral,
            "Maybe you could let me have the flower and do something else to impress her?",
        )
        chatNpc(
            neutral,
            "Hmm... that is not a totally stupid idea outerlander. I know she is a lover of music, " +
                "and a romantic ballad might be just the thing with which to woo her.",
        )
        chatNpc(
            sad,
            "Unfortunately I don't have a musical bone in my entire body, so someone else will have " +
                "to write it for me.",
        )
        chatPlayer(
            quiz,
            "So if I can find someone to write you a romantic ballad, you will give me your flower?",
        )
        chatNpc(neutral, "That sounds like a fair deal to me, outerlander.")
        advanceTo(MerchantStep.BALLAD)
    }

    private suspend fun Dialogue.olafReveal() {
        chatPlayer(quiz, "I don't suppose you have any idea where I could find a love ballad, do you?")
        chatNpc(
            happy,
            "Well, as official Fremennik bard, it falls within my remit to compose all music for " +
                "the tribe. I am fully versed in all the various types of romantic music.",
        )
        chatPlayer(happy, "Great! Can you write me one then?")
        chatNpc(
            neutral,
            "Well... normally I would be thrilled at the chance to show my skill as a poet in " +
                "composing a seductively romantic ballad...",
        )
        chatPlayer(bored, "let me guess; here comes the 'but'.")
        chatNpc(sad, "...but unfortunately I cannot concentrate fully upon my work recently.")
        chatPlayer(quiz, "Why is that then?")
        chatNpc(
            sad,
            "It is these old worn out shoes of mine... As a bard I am expected to wander the " +
                "lands, singing of the glorious battles of our warriors.",
        )
        chatNpc(
            neutral,
            "If you can find me a pair of sturdy boots to replace these old worn out ones of mine, " +
                "I will be happy to spend the time on composing you a romantic ballad.",
        )
        advanceTo(MerchantStep.BOOTS)
    }

    private suspend fun Dialogue.yrsaReveal() {
        chatPlayer(
            quiz,
            "I don't suppose you have any idea where I could find some custom sturdy boots, do you?",
        )
        chatNpc(
            neutral,
            "Well, I don't usually have many shoes in stock here in my little clothes shop... I " +
                "will be able to make you up a pair if you are really desperate though?",
        )
        chatPlayer(neutral, "They're not for me... I need them for Olaf.")
        chatNpc(
            angry,
            "Oh, that foolish bard... Why didn't he just ask me to make him some? It is his stupid " +
                "pride, I believe!",
        )
        chatNpc(
            neutral,
            "I will tell you what I will do outerlander; I know that you must have the ear of the " +
                "chieftain for him to consider you as worthy of becoming a Fremennik by trial.",
        )
        chatNpc(
            neutral,
            "I will make you a pair of sturdy boots for Olaf if you will persuade him to reduce the " +
                "sales tax placed upon all Fremennik shopkeepers. It does nothing but hurt my " +
                "business now.",
        )
        chatPlayer(neutral, "Okay, I will see what I can do.")
        advanceTo(MerchantStep.TAX_CUT)
    }

    private suspend fun Dialogue.brundtReveal() {
        chatPlayer(
            quiz,
            "I don't suppose you have any idea where I could find a guarantee of a reduction on " +
                "sales taxes, do you?",
        )
        chatNpc(
            quiz,
            "A reduction on sales taxes? Why, I am the only one in the Fremennik who may authorise " +
                "such a thing. What does an outerlander want with that?",
        )
        chatPlayer(neutral, "Actually, it's not for me. I need to get it as part of my trials.")
        chatNpc(
            neutral,
            "Hmmm. Interesting. Your trials seem to be very different to those I took as a young " +
                "lad. Well, I am not adverse in principle to giving a slight tax break to our shops.",
        )
        chatNpc(
            neutral,
            "There will of course be a shortfall in the tribe's income, that will need to be made " +
                "up for elsewhere, however.",
        )
        chatNpc(
            neutral,
            "How about this. For many years Sigli has been the only one in the tribe who knows the " +
                "locations of the best hunting grounds where game is easiest to catch.",
        )
        chatNpc(
            neutral,
            "If you can persuade him to let the entire tribe know these hunting grounds, then we " +
                "can increase productivity within the tribe, and any shortfall caused",
        )
        chatNpc(
            neutral,
            "by lowering sales taxes will be covered. I think this is a more than fair arrangement " +
                "to make, don't you?",
        )
        chatPlayer(happy, "Yeah, that sounds very fair.")
        chatNpc(
            happy,
            "Speak to Sigli then, and you may have my promise to reduce our sales taxes. And best " +
                "of luck with the rest of your trials.",
        )
        advanceTo(MerchantStep.HUNTING_MAP)
    }

    private suspend fun Dialogue.sigliReveal() {
        chatPlayer(
            quiz,
            "I don't suppose you have any idea where I could find a map to unspoiled hunting " +
                "grounds, do you?",
        )
        chatNpc(
            neutral,
            "Well, of course I do. I wouldn't be much of a huntsman if I didn't know where to find " +
                "my prey now, would I outerlander?",
        )
        chatPlayer(neutral, "No, I guess not. So can I have it?")
        chatNpc(
            neutral,
            "Directions to my hunting grounds could mean the end of my livelihood. The only way I " +
                "would be prepared to give them up would be...",
        )
        chatPlayer(quiz, "What? Power? Money? Women? Wine?")
        chatNpc(
            neutral,
            "...a new string for my hunting bow. Not just any bowstring; I need a custom " +
                "bowstring, balanced for my bow precisely to keep my hunt competitive.",
        )
        chatNpc(
            neutral,
            "Only in this way would I allow the knowledge of my hunting grounds to be passed on to " +
                "strangers.",
        )
        chatPlayer(quiz, "So where would I get that?")
        chatNpc(
            neutral,
            "I have no idea. But then again, I'm happy with my old bowstring and being the only " +
                "person who knows where my hunting ground is.",
        )
        advanceTo(MerchantStep.BOWSTRING)
    }

    private suspend fun Dialogue.skulgrimenReveal() {
        chatPlayer(
            quiz,
            "I don't suppose you have any idea where I could find a finely balanced custom " +
                "bowstring, do you?",
        )
        chatNpc(
            neutral,
            "Aye, I have a few in stock. What would an outerlander be wanting with equipment like " +
                "that?",
        )
        chatPlayer(neutral, "It's for Sigli. It needs to be weighted precisely to suit his hunting bow.")
        chatNpc(
            neutral,
            "For Sigli eh? Well, I made his bow in the first place, so I'll be able to select the " +
                "right string for you... just one small problem.",
        )
        chatPlayer(quiz, "What's that?")
        chatNpc(
            neutral,
            "This string you'll be wanting... Very rare. Take a lot of time to recreate. Not sure " +
                "you have the cash for it.",
        )
        chatPlayer(neutral, "Then maybe you'll accept something else...?")
        chatNpc(
            happy,
            "Heh. Good thinking outerlander. Well, it's true, there is more to life than just " +
                "making money. Making weapons is good money, but it's not why I do it.",
        )
        chatNpc(
            neutral,
            "I'll tell you what. I heard a rumour that one of the fishermen down by the docks " +
                "caught some weird looking fish as they were fishing the other day.",
        )
        chatNpc(
            neutral,
            "From what I hear this fish is unique. Nobody's ever seen its like before. This " +
                "intrigues me. I'd like to have it for myself. Make a good trophy.",
        )
        chatNpc(quiz, "You get me that fish, I give you the bowstring. What do you say? We got a deal?")
        chatPlayer(happy, "Sounds good to me.")
        advanceTo(MerchantStep.FISH)
    }

    private suspend fun Dialogue.fishermanReveal() {
        chatPlayer(
            quiz,
            "I don't suppose you have any idea where I could find an exotic and extremely rare " +
                "fish, do you?",
        )
        chatNpc(happy, "Ah, so even outerlanders have heard of my amazing catch the other day!")
        chatPlayer(quiz, "You have it? Can I trade you something for it?")
        chatNpc(
            neutral,
            "As exotic looking as it is, it is bad eating. I will happily trade it if you can find " +
                "me the secret map of the best fishing spots that the navigator has hidden away.",
        )
        chatPlayer(quiz, "Is that all?")
        chatNpc(
            neutral,
            "Indeed it is, outerlander. The only reason I sit out here in the cold all day long is " +
                "so I don't have to pay his outrageous prices.",
        )
        chatNpc(
            happy,
            "By getting me his copy of that map, I will finally be self sufficient. I might even " +
                "make a profit!",
        )
        chatPlayer(neutral, "I'll see what I can do.")
        advanceTo(MerchantStep.SEA_MAP)
    }

    private suspend fun Dialogue.swensenReveal() {
        chatPlayer(
            quiz,
            "I don't suppose you have any idea where I could find a map of deep sea fishing spots " +
                "do you?",
        )
        chatNpc(
            happy,
            "Hmmm? Why of course! As the navigator for the Fremennik I keep all of our maps secure " +
                "right here.",
        )
        chatPlayer(happy, "Great! Can I have it?")
        chatNpc(
            neutral,
            "Have it? Just like that? I think not outerlander. This map shows all of the prime " +
                "fishing locations nearby.",
        )
        chatNpc(
            neutral,
            "It is very valuable to our clan. I am afraid I can not just give it away.",
        )
        chatPlayer(quiz, "Perhaps I can trade you something for it?")
        chatNpc(
            neutral,
            "A trade? For a map of the best fishing spots in a hundred leagues? I will trade it for " +
                "no less than a weather forecast from our Seer.",
        )
        chatNpc(
            neutral,
            "As a navigator, the weather is extremely important for plotting the best course. " +
                "Unfortunately the Seer is always too busy to help me with a forecast.",
        )
        chatPlayer(quiz, "Where could I get a weather forecast from then?")
        chatNpc(
            neutral,
            "I just told you: from the Seer. You will need to persuade him to take the time to make " +
                "a forecast somehow.",
        )
        advanceTo(MerchantStep.FORECAST)
    }

    private suspend fun Dialogue.peerReveal() {
        chatPlayer(
            quiz,
            "I don't suppose you have any idea where I could find a weather forecast from the " +
                "Fremennik Seer do you?",
        )
        chatNpc(neutral, "Er.... Yes, because I AM the Fremennik Seer.")
        chatPlayer(quiz, "Can I have a weather forecast then please?")
        chatNpc(
            neutral,
            "You require a divination of the weather? This is a simple matter for me, but I will " +
                "require something in return from you for this small service.",
        )
        chatPlayer(bored, "I knew you were going to say that...")
        chatNpc(
            neutral,
            "Do not fret, outerlander; it is a fairly simple matter. I require a bodyguard for " +
                "protection. Find someone willing to offer me this service.",
        )
        chatPlayer(quiz, "That's all?")
        chatNpc(neutral, "That is all.")
        advanceTo(MerchantStep.BODYGUARD)
    }

    private suspend fun Dialogue.thorvaldReveal() {
        chatPlayer(
            quiz,
            "I don't suppose you have any idea where I could find a brave and powerful warrior to " +
                "act as a bodyguard?",
        )
        chatNpc(
            angry,
            "Know you not who I am outerlander? There are none more brave or powerful than me " +
                "amongst all the Fremennik!",
        )
        chatNpc(
            neutral,
            "However... The role of bodyguard is below me, as a noble warrior. You might as well " +
                "ask me to babysit the children!",
        )
        chatPlayer(quiz, "Is there no way you would do this for me?")
        chatNpc(
            neutral,
            "There is but one way outerlander. Since I was steeled in battle, I have dreamt of " +
                "earning my place at the Champions Table in the Long Hall.",
        )
        chatNpc(
            neutral,
            "It is a tradition amongst us that the bravest and strongest are honoured with a table " +
                "of champions to drink and feast all that they can in our Long Hall.",
        )
        chatNpc(
            sad,
            "Unfortunately, there are only a fixed number of places available at the table, and " +
                "these places were all filled many moons ago by others.",
        )
        chatNpc(
            neutral,
            "Although my worthiness is undeniable, the only way I may take my place is if one of " +
                "those already there die, or give up their place to me voluntarily.",
        )
        chatPlayer(
            shifty,
            "So you want me to go kill one of them off for you? Make it look like an accident?",
        )
        chatNpc(shocked, "WHAT? No, no, not at all! I am shocked you would suggest such a thing!")
        chatNpc(
            neutral,
            "If you can persuade one of the Revellers to give up their Champions' Token to you so " +
                "that I might take their place, you may have my contract as a bodyguard.",
        )
        chatPlayer(neutral, "Okay, I'll see what I can do.")
        advanceTo(MerchantStep.TOKEN)
    }

    private suspend fun Dialogue.manniReveal() {
        chatPlayer(
            quiz,
            "I don't suppose you have any idea where I could find a token to allow a seat at the " +
                "champions table, do you?",
        )
        chatNpc(
            happy,
            "As a matter of fact, I do. I have one right here. I earnt my place here at the " +
                "longhall for surviving over 5000 battles and raiding parties.",
        )
        chatNpc(
            neutral,
            "Due to my contribution to the tribe, I am now permitted to spend my days here in the " +
                "longhall listening to the epic tales of the bard, and drinking beer.",
        )
        chatPlayer(
            happy,
            "Cool. That sounds pretty sweet! So I guess you don't want to give it away?",
        )
        chatNpc(
            sad,
            "I think it sounds better than it actually is outerlander. I miss my glory days of " +
                "combat on the battlefield. And to tell you the truth, the beer here isn't great, " +
                "and the bards' music is lousy.",
        )
        chatNpc(
            neutral,
            "I would happily give up my token if it were not for the one thing that keeps me here. " +
                "Our barkeep is one of the best in the world, and has worked in taverns across the " +
                "land.",
        )
        chatNpc(
            neutral,
            "When she was younger, she experimented a lot with her drinks, and invented a cocktail " +
                "so alcoholic and tasty that it has become something of a legend to all who enjoy " +
                "a drink.",
        )
        chatNpc(
            sad,
            "Unfortunately, she decided that cocktails were not a suitable drink for Fremennik " +
                "warriors, and vowed to never again make it.",
        )
        chatNpc(
            sad,
            "I have been here every day since she returned, hoping that someday she might change " +
                "her mind and I might try this legendary cocktail for myself. Alas, it has never " +
                "come to pass...",
        )
        chatNpc(
            neutral,
            "If you can persuade her to make me her legendary cocktail, I will be happy to never " +
                "let another drop of alcohol pass my lips, and will give you my champions token.",
        )
        chatPlayer(quiz, "That's all?")
        chatNpc(neutral, "That's all.")
        advanceTo(MerchantStep.COCKTAIL)
    }

    private suspend fun Dialogue.thoraReveal() {
        chatPlayer(
            quiz,
            "I don't suppose you have any idea where I could find the longhall barkeeps' legendary " +
                "cocktail, do you?",
        )
        chatNpc(shocked, "How did you hear about that?!?!?")
        chatNpc(
            neutral,
            "I didn't think anybody knew about that... Well, it is true that in my younger years as " +
                "a barkeep, I wandered the lands trying various alcoholic delicacies.",
        )
        chatNpc(
            happy,
            "Did you ever realise just how many different types of alcohol there are here in " +
                "Gielinor? Lots!",
        )
        chatNpc(
            happy,
            "Well, anyway, I used a fusion of various drinks from all around the world to create " +
                "the greatest cocktail ever made!",
        )
        chatNpc(
            sad,
            "Of course, when my wanderlust was gone, and I returned back to Rellekka to serve as " +
                "barkeep here, I gave all that up.",
        )
        chatPlayer(quiz, "But you still remember how to make it, right?")
        chatNpc(neutral, "Of course.")
        chatPlayer(
            quiz,
            "And you have all the ingredients here? I don't need to go chasing round the world for " +
                "obscure ingredients to make it?",
        )
        chatNpc(quiz, "No, I have them all here. Why?")
        chatPlayer(quiz, "Can you make me your legendary cocktail then?")
        chatNpc(
            sad,
            "I would rather not; it is a reminder of a life I left behind when I came back.",
        )
        chatPlayer(quiz, "Any way I could change your mind?")
        chatNpc(
            neutral,
            "You need this to become a Fremennik, right? Well, you seem okay for an outerlander, it " +
                "would be a shame to see you fail. You know Askeladden?",
        )
        chatPlayer(neutral, "That kid outside? Sure.")
        chatNpc(
            angry,
            "He is nothing but a pest. He keeps sneaking in and stealing beer. I shudder to think " +
                "what he will be like when he has passed his trial of manhood,",
        )
        chatNpc(
            angry,
            "and is allowed in here legitimately. If you can get him to sign a contract promising " +
                "that he will NEVER EVER EVER darken my doorway here again, you get the drink.",
        )
        chatPlayer(quiz, "Any idea how I can get him to do that?")
        chatNpc(
            neutral,
            "Knowing that little horror, he'll probably be willing to in exchange for some cash. " +
                "You should go ask him yourself though.",
        )
        advanceTo(MerchantStep.PROMISE)
    }

    private suspend fun Dialogue.askeladdenSellsNote() {
        chatPlayer(
            quiz,
            "I don't suppose you have any idea where I could find a written promise from " +
                "Askeladden to stay out of the Longhall?",
        )
        chatNpc(
            shocked,
            "What? I can't believe she asked you to get a written promise from me to stay out!",
        )
        chatPlayer(neutral, "Yup, she really did.")
        chatNpc(
            sad,
            "Awwwwwww.... but the longhall is just SO MUCH FUN! I'd live there if I could! I " +
                "suppose you really need that promise to help become a Fremennik, huh?",
        )
        chatPlayer(neutral, "Yeah, I really do...")
        chatNpc(
            happy,
            "Well I'll tell you what buddy; As it's you, I'll give you that written promise. All I " +
                "ask in return for it is a measly 5000 gold. What do you say?",
        )
        if (!choice2("Yes", true, "No", false)) {
            chatPlayer(
                worried,
                "I don't think so... That's really quite a lot of money...",
            )
            chatNpc(
                laugh,
                "Hey, suit yourself buddy. You change your mind, the bank of Askeladden is open for " +
                    "deposits 24 hours a day! Eh-heh-heh-heh-heh.",
            )
            return
        }
        chatPlayer(happy, "That's all you want in return? Sure thing. Here you go.")
        if (player.inv.count(COINS) < NOTE_PRICE) {
            chatPlayer(sad, "...how embarrassing. I appear to be short...")
            chatNpc(
                angry,
                "Trying to scam me, huh buddy? Well it won't work! You come back with the cash, or " +
                    "don't come back at all!",
            )
            return
        }
        if (player.inv.isFull() && player.inv.count(COINS) != NOTE_PRICE) {
            mesbox("Your inventory is too full to accept the written promise.")
            return
        }
        access.invDel(access.inv, COINS, NOTE_PRICE)
        access.invAdd(access.inv, PROMISSORY_NOTE)
        advanceTo(MerchantStep.NOTE_BOUGHT)
        objbox(PROMISSORY_NOTE, "Askeladden hands you a signed promissory note.")
        chatNpc(
            laugh,
            "Done, and done. Let me know if you got any more cash burning a hole in your pocket I " +
                "can relieve you of, buddy.",
        )
    }

    /* Repeat conversations with whoever the player is currently working for. */

    private suspend fun Dialogue.reminder(contact: MerchantContact, step: Int): Boolean {
        when {
            contact == MerchantContact.Yrsa && step == MerchantStep.TAX_CUT -> {
                chatPlayer(
                    quiz,
                    "I don't suppose you have any idea where I could find a guarantee of a " +
                        "reduction on sales taxes, do you?",
                )
                chatNpc(neutral, "Yes I do outerlander. Only the Chieftain may permit such a thing. Talk to him.")
            }
            contact == MerchantContact.Skulgrimen && step == MerchantStep.FISH -> {
                chatPlayer(
                    quiz,
                    "I don't suppose you have any idea where I could find an exotic and extremely " +
                        "rare fish, do you?",
                )
                chatNpc(shocked, "What? There's another one?")
                chatPlayer(confused, "Er... no, it's the one for you that I'm looking for...")
                chatNpc(
                    neutral,
                    "Ah. I see. I already told you. Some guy down by the docks was bragging. Best " +
                        "ask there, I reckon.",
                )
            }
            contact == MerchantContact.Fisherman && step == MerchantStep.SEA_MAP -> {
                chatPlayer(
                    quiz,
                    "I don't suppose you have any idea where I could find a map of deep sea " +
                        "fishing spots do you?",
                )
                chatNpc(
                    angry,
                    "You should pay attention when I speak! I already told you, that rip off " +
                        "navigator has it, and I want it!",
                )
            }
            contact == MerchantContact.Swensen && step == MerchantStep.FORECAST -> {
                chatPlayer(
                    quiz,
                    "I don't suppose you have any idea where I could find a weather forecast from " +
                        "the Fremennik Seer do you?",
                )
                chatNpc(confused, "Uh... from the Seer perhaps?")
            }
            contact == MerchantContact.Peer && step == MerchantStep.BODYGUARD -> {
                chatPlayer(
                    quiz,
                    "I don't suppose you have any idea where I could find a brave and powerful " +
                        "warrior to act as a bodyguard?",
                )
                chatNpc(
                    neutral,
                    "If I did, then I would simply have asked them myself now, wouldn't I, " +
                        "outerlander?",
                )
            }
            contact == MerchantContact.Thorvald && step == MerchantStep.TOKEN -> {
                chatPlayer(quiz, "I don't suppose you have any idea where I could find a Champions' Token?")
                chatNpc(
                    neutral,
                    "If you can persuade one of the Revellers to give up their Champions' Token to " +
                        "you so that I might take their place, you may have my contract as a " +
                        "bodyguard.",
                )
            }
            contact == MerchantContact.Manni && step == MerchantStep.COCKTAIL -> {
                chatPlayer(
                    quiz,
                    "I don't suppose you have any idea where I could find the longhall barkeeps' " +
                        "legendary cocktail, do you?",
                )
                chatNpc(
                    neutral,
                    "Uh... yes, the longhall barkeep has it. So could you get me my drink now please?",
                )
            }
            contact == MerchantContact.Thora && step == MerchantStep.PROMISE -> {
                chatPlayer(
                    quiz,
                    "I don't suppose you have any idea where I could find a written promise from " +
                        "Askeladden to stay out of the Longhall?",
                )
                chatNpc(
                    neutral,
                    "Well, as I say, you should talk to him about that. Knowing the little runt as I " +
                        "do though He'll probably do it for the cash.",
                )
            }
            else -> return false
        }
        return true
    }

    private suspend fun Dialogue.afterTrade(contact: MerchantContact, step: Int): Boolean {
        val handover = HANDOVERS.firstOrNull { it.contact == contact }
        val traded =
            when (contact) {
                MerchantContact.Askeladden -> step >= MerchantStep.NOTE_BOUGHT
                else -> handover != null && step >= handover.step
            }
        if (!traded) {
            return false
        }
        when (contact) {
            MerchantContact.Askeladden -> {
                chatPlayer(quiz, "I thought you really liked the long hall?")
                chatNpc(happy, "I do!")
                chatPlayer(quiz, "Then why did you sign this guarantee that you will never enter it again?")
                chatNpc(
                    laugh,
                    "Aha! It is because I am cunning! That guarantee says that Askeladden will " +
                        "never enter the longhall again! But when I have completed my Fremennik " +
                        "trials,",
                )
                chatNpc(
                    laugh,
                    "and passed my trial of manhood, I will be given a new name, as is our custom, " +
                        "and will therefore not be Askeladden anymore! That guarantee isn't worth " +
                        "the paper it's written on!!",
                )
                chatNpc(laugh, "You didn't think I would give up going to the longhall for only 5000 did you?")
                chatPlayer(bored, "Knowing you, I guess I didn't.")
            }
            MerchantContact.Thora -> {
                chatPlayer(quiz, "Thanks for making me this cocktail. Why don't you make them anymore normally?")
                chatNpc(
                    sad,
                    "Ah... when I gave up my travels across the world many years back, to return to " +
                        "my expected role as longhall barkeep, as my mother, and her mother, were " +
                        "before me,",
                )
                chatNpc(
                    sad,
                    "I gave up a lot of the freedom I had found in the outside world. I know it is " +
                        "our custom to shun outerlanders and their ways, but I didn't find them as " +
                        "bad as the stories say.",
                )
                chatNpc(
                    sad,
                    "Sometimes I feel as though we Fremennik live in a prison that we have " +
                        "constructed for ourselves, and that WE are the outerlanders, out here on the " +
                        "edge of the world...",
                )
                chatNpc(
                    neutral,
                    "I'm sorry, I think it is part of the job of Longhall barkeep to get " +
                        "philosophical about things occasionally. I wish you all the best of luck " +
                        "with your trials, outerlander.",
                )
                chatNpc(
                    happy,
                    "When you have finished, perhaps you will come back here, and we can share a " +
                        "drink over tales of the outside world?",
                )
                chatPlayer(happy, "Thanks, I'd like that.")
            }
            MerchantContact.Manni -> {
                chatPlayer(
                    quiz,
                    "So it doesn't bother you at all that you just gave up your place here for one " +
                        "drink?",
                )
                chatNpc(
                    happy,
                    "Ah, but it was not just any drink... It was the finest cocktail ever created! " +
                        "Now that I have tasted it, I need never drink again, for my tastebuds will " +
                        "never be so excited!",
                )
                chatPlayer(quiz, "So it was nice?")
                chatNpc(happy, "It was... exquisite!")
                chatPlayer(quiz, "What did it taste of, then?")
                chatNpc(neutral, "Mostly tomato juice.")
            }
            MerchantContact.Thorvald -> {
                chatPlayer(shifty, "You didn't take much persuading to 'lower' yourself to a bodyguard.")
                chatNpc(
                    neutral,
                    "You misunderstand, outerlander. Normally I will only battle for a noble cause, " +
                        "but have never been recognised as a true champion here.",
                )
                chatNpc(
                    happy,
                    "With this Champion's token, I can stand alongside my warrior brethren in the " +
                        "Long Hall, and revel in the glories of past victories together!",
                )
            }
            MerchantContact.Peer -> {
                chatPlayer(neutral, "So, about this forecast...")
                chatNpc(neutral, "Yes, outerlander?")
                chatPlayer(
                    quiz,
                    "I still don't know why you didn't just let me have one anyway in the first " +
                        "place. Surely it means nothing to you?",
                )
                chatNpc(
                    neutral,
                    "That is not true, outerlander. Although I see glimpses of the future all of " +
                        "the time, using my powers brings the attention of the gods to me.",
                )
                chatNpc(
                    worried,
                    "Some of the gods are spiteful and cruel, and I fear if I use my powers too much " +
                        "then I will meet with unpredictable accidents. This is why I needed " +
                        "protection.",
                )
                chatPlayer(confused, "Okay... I... think I understand...")
            }
            MerchantContact.Swensen -> {
                chatPlayer(
                    quiz,
                    "If this map of fishing spots is so valuable, why did you give it away to me so " +
                        "easily?",
                )
                chatNpc(
                    neutral,
                    "Hmmm? Well, firstly it will be of value to our entire clan, so I have lost " +
                        "nothing from giving it to you.",
                )
                chatNpc(
                    laugh,
                    "The other reason is of course that I have already memorised it, so I can make " +
                        "myself another copy whenever I want!",
                )
            }
            MerchantContact.Fisherman -> {
                chatPlayer(bored, "I don't see what's so special about this so called rare fish.")
                chatNpc(neutral, "Me neither, outerlander. That is why I gave it to you.")
            }
            MerchantContact.Skulgrimen -> {
                chatPlayer(quiz, "So about this bowstring... was it hard to make or something?")
                chatNpc(
                    neutral,
                    "Not hard. Just a trick to it. Takes skill to learn, but when learnt, easy. " +
                        "Sigli will be happy. Finest bowstring on continent. Will suit his needs " +
                        "perfectly.",
                )
            }
            MerchantContact.Sigli -> {
                chatPlayer(quiz, "So you really don't mind giving this away to me?")
                chatNpc(
                    neutral,
                    "No outerlander... it is hard to explain. That map makes my role as huntsman too " +
                        "easy. I fear my skills are becoming dulled. Now I must track my prey once " +
                        "more.",
                )
                chatNpc(neutral, "To begin again from scratch... I feel this may keep me sharp.")
            }
            MerchantContact.Brundt -> {
                chatPlayer(
                    quiz,
                    "So cutting sales tax isn't going to ruin your economy here or anything?",
                )
                chatNpc(
                    neutral,
                    "Not at all outerlander; now that we have Sigli's map we can increase the amount " +
                        "of hunts we run, and make up any shortfall that way.",
                )
            }
            MerchantContact.Yrsa -> {
                chatPlayer(
                    happy,
                    "Hey, these shoes look pretty comfy. Think you could make me a pair like them?",
                )
                chatNpc(
                    neutral,
                    "Maybe if you pass your trial and become a full fledged member of the Fremennik...",
                )
            }
            MerchantContact.Olaf -> {
                chatPlayer(quiz, "So you think this song is pretty good then?")
                chatNpc(
                    happy,
                    "Ahhh.... outerlander... it is the most beautiful romantic ballad I have ever " +
                        "been inspired to write...",
                )
                chatNpc(
                    happy,
                    "Only a woman with a heart of icy stone could fail to be moved by its beauty!",
                )
                chatPlayer(happy, "Thanks! That sounds perfect!")
            }
            MerchantContact.Sailor -> {
                chatPlayer(quiz, "so tell me... who is this woman that you are trying to impress anyway?")
                chatNpc(
                    shifty,
                    "It's Thora, the longhall barkeep. Please don't tell her though. She's not like " +
                        "the rest of the Fremennik girls, she has a secret desire to see the world.",
                )
                chatNpc(neutral, "Being a sailor, I can really relate to that.")
            }
        }
        return true
    }

    /** One link of the chain: [contact] takes [given] and hands back [received]. */
    private class Handover(
        val contact: MerchantContact,
        val given: String,
        val received: String,
        val step: Int,
        /** The step whose request [received] satisfies, for what the player is asking around for. */
        val wantedStep: Int,
        val message: String,
    )

    companion object {
        const val NOTE_PRICE = 5000

        const val PROMISSORY_NOTE = "obj.viking_promissary_note2"
        const val LEGENDARY_COCKTAIL = "obj.viking_legendary_cocktail"
        const val CHAMPIONS_TOKEN = "obj.viking_champion_token"
        const val WARRIORS_CONTRACT = "obj.viking_promissary_note3"
        const val WEATHER_FORECAST = "obj.viking_weather_forecast"
        const val SEA_FISHING_MAP = "obj.viking_another_map"
        const val UNUSUAL_FISH = "obj.viking_unique_fish"
        const val CUSTOM_BOWSTRING = "obj.viking_bowstring"
        const val TRACKING_MAP = "obj.viking_map_to_hunting_grounds"
        const val FISCAL_STATEMENT = "obj.viking_promissary_note"
        const val STURDY_BOOTS = "obj.viking_new_boots"
        const val FREMENNIK_BALLAD = "obj.viking_song"
        const val EXOTIC_FLOWER = "obj.viking_rare_flower"

        /** Every item of the chain, the note first; the order Askeladden's note travels in. */
        val CHAIN_ITEMS =
            listOf(
                PROMISSORY_NOTE,
                LEGENDARY_COCKTAIL,
                CHAMPIONS_TOKEN,
                WARRIORS_CONTRACT,
                WEATHER_FORECAST,
                SEA_FISHING_MAP,
                UNUSUAL_FISH,
                CUSTOM_BOWSTRING,
                TRACKING_MAP,
                FISCAL_STATEMENT,
                STURDY_BOOTS,
                FREMENNIK_BALLAD,
                EXOTIC_FLOWER,
            )

        private val WANTED =
            mapOf(
                MerchantStep.FIND_FLOWER to "a rare flower from across the sea",
                MerchantStep.BALLAD to "a love ballad",
                MerchantStep.BOOTS to "some custom sturdy boots",
                MerchantStep.TAX_CUT to "a guarantee of a reduction on sales taxes",
                MerchantStep.HUNTING_MAP to "a map to unspoiled hunting grounds",
                MerchantStep.BOWSTRING to "a finely balanced custom bowstring",
                MerchantStep.FISH to "an exotic and extremely rare fish",
                MerchantStep.SEA_MAP to "a map of deep sea fishing spots",
                MerchantStep.FORECAST to "a weather forecast from the Fremennik Seer",
                MerchantStep.BODYGUARD to "a brave and powerful warrior to act as a bodyguard",
                MerchantStep.TOKEN to "a token to allow a seat at the champions table",
                MerchantStep.COCKTAIL to "the longhall barkeeps' legendary cocktail",
                MerchantStep.PROMISE to "a written promise from Askeladden to stay out of the Longhall",
            )

        private val HANDOVERS =
            listOf(
                Handover(
                    MerchantContact.Thora,
                    PROMISSORY_NOTE,
                    LEGENDARY_COCKTAIL,
                    MerchantStep.COCKTAIL_MADE,
                    MerchantStep.COCKTAIL,
                    "Thora mixes you her legendary cocktail.",
                ),
                Handover(
                    MerchantContact.Manni,
                    LEGENDARY_COCKTAIL,
                    CHAMPIONS_TOKEN,
                    MerchantStep.TOKEN_GIVEN,
                    MerchantStep.TOKEN,
                    "Manni gives you his champions token.",
                ),
                Handover(
                    MerchantContact.Thorvald,
                    CHAMPIONS_TOKEN,
                    WARRIORS_CONTRACT,
                    MerchantStep.CONTRACT_GIVEN,
                    MerchantStep.BODYGUARD,
                    "Thorvald gives you a signed contract to act as a bodyguard.",
                ),
                Handover(
                    MerchantContact.Peer,
                    WARRIORS_CONTRACT,
                    WEATHER_FORECAST,
                    MerchantStep.FORECAST_GIVEN,
                    MerchantStep.FORECAST,
                    "Peer gives you a weather forecast.",
                ),
                Handover(
                    MerchantContact.Swensen,
                    WEATHER_FORECAST,
                    SEA_FISHING_MAP,
                    MerchantStep.SEA_MAP_GIVEN,
                    MerchantStep.SEA_MAP,
                    "Swensen gives you a map of the best local fishing spots.",
                ),
                Handover(
                    MerchantContact.Fisherman,
                    SEA_FISHING_MAP,
                    UNUSUAL_FISH,
                    MerchantStep.FISH_GIVEN,
                    MerchantStep.FISH,
                    "The fisherman gives you his unusual fish.",
                ),
                Handover(
                    MerchantContact.Skulgrimen,
                    UNUSUAL_FISH,
                    CUSTOM_BOWSTRING,
                    MerchantStep.BOWSTRING_GIVEN,
                    MerchantStep.BOWSTRING,
                    "Skulgrimen gives you a custom bowstring.",
                ),
                Handover(
                    MerchantContact.Sigli,
                    CUSTOM_BOWSTRING,
                    TRACKING_MAP,
                    MerchantStep.HUNTING_MAP_GIVEN,
                    MerchantStep.HUNTING_MAP,
                    "Sigli gives you his map of the hunting grounds.",
                ),
                Handover(
                    MerchantContact.Brundt,
                    TRACKING_MAP,
                    FISCAL_STATEMENT,
                    MerchantStep.FISCAL_GIVEN,
                    MerchantStep.TAX_CUT,
                    "Brundt gives you a signed fiscal statement.",
                ),
                Handover(
                    MerchantContact.Yrsa,
                    FISCAL_STATEMENT,
                    STURDY_BOOTS,
                    MerchantStep.BOOTS_GIVEN,
                    MerchantStep.BOOTS,
                    "Yrsa gives you a pair of sturdy boots.",
                ),
                Handover(
                    MerchantContact.Olaf,
                    STURDY_BOOTS,
                    FREMENNIK_BALLAD,
                    MerchantStep.BALLAD_GIVEN,
                    MerchantStep.BALLAD,
                    "Olaf gives you a romantic ballad.",
                ),
                Handover(
                    MerchantContact.Sailor,
                    FREMENNIK_BALLAD,
                    EXOTIC_FLOWER,
                    MerchantStep.FLOWER_GIVEN,
                    MerchantStep.FIND_FLOWER,
                    "The sailor gives you his exotic flower.",
                ),
            )
    }
}
