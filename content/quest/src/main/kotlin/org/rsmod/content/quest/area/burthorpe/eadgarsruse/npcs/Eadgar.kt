package org.rsmod.content.quest.area.burthorpe.eadgarsruse.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.CHICKENS_NEEDED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.EADGAR
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.FAKE_MAN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.FAKE_MAN_NPC
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.GRAIN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.GRAIN_NEEDED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.LOGS
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.PARROT
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.PARROT_NPC
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.RAW_CHICKEN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.ROBE
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_ASKED_EADGAR
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_COOK_FIRST
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_FAKE_MAN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_FETCH_PARROT
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_HANDING_IN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_MAKE_POTION
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_MET_COOK
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_PARROT_HIDDEN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_PARROT_TRAINED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_PARROT_WANTED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_PLAN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.TROLL_POTION
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.erChickensGiven
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.erGrainGiven
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.erLogsGiven
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.erRobeGiven
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.ownsAnywhere
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Mad Eadgar, in his cave at the top of Trollheim. He serves goat stew for logs, works out the
 * plan to fool Burntmeat, takes the fake man's ingredients as they come and builds it once the
 * parrot has learned to scream.
 */
class Eadgar
@Inject
constructor(
    private val eadgarsRuse: EadgarsRuseQuest,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(EADGAR) { startDialogue(it.npc) { eadgar() } }
        onOpLocU(STEW_POT) { leaveStewAlone() }
    }

    private suspend fun ProtectedAccess.leaveStewAlone() {
        arriveDelay()
        startDialogue { chatPlayer(worried, "I'd better not mess around with Eadgar's stew!") }
        mes("You decide to leave Eadgar's stew alone.")
    }

    private suspend fun Dialogue.eadgar() {
        val stage = eadgarsRuse.stage(player)
        when {
            stage == 0 || eadgarsRuse.isComplete(player) -> {
                greet()
                options()
            }
            stage == STAGE_STARTED || stage == STAGE_ASKED_EADGAR -> {
                greet()
                options(goutweed = true)
            }
            stage == STAGE_COOK_FIRST -> {
                askAboutGoutweed()
                cookWantsHuman()
            }
            stage == STAGE_MET_COOK -> {
                chatNpc(neutral, "Oh, it's you. Have you talked to the troll cook yet?")
                cookWantsHuman()
            }
            stage == STAGE_PARROT_WANTED -> parrotWanted()
            stage == STAGE_PLAN -> parrotAdvice()
            stage == STAGE_PARROT_HIDDEN -> {
                chatPlayer(happy, "I hid the parrot under the rack in the troll prison.")
                chatNpc(happy, "Good work. How about those other items?")
                eadgarsRuse.advanceTo(access, STAGE_HANDING_IN)
                handIn()
            }
            stage == STAGE_HANDING_IN -> {
                chatNpc(quiz, "How are you getting on?")
                handIn()
            }
            stage == STAGE_MAKE_POTION -> truthPotion()
            stage == STAGE_FETCH_PARROT -> {
                chatNpc(neutral, "Now just go fetch that poor parrot back, it's probably had enough by now.")
                anythingElse()
            }
            stage == STAGE_PARROT_TRAINED -> makeFakeMan()
            stage == STAGE_FAKE_MAN -> fakeManReminder()
            else -> {
                chatNpc(quiz, "Well? Did the plan work?")
                chatPlayer(happy, "Yes! I can get into the storeroom now.")
                chatNpc(happy, "Glad to hear it!")
                anythingElse()
            }
        }
    }

    private suspend fun Dialogue.greet() {
        chatPlayer(happy, "Hi!")
        chatNpc(happy, "Welcome to Mad Eadgar's! ${GREETINGS[access.random.of(GREETINGS.size)]}")
        chatNpc(happy, "Would you care to sample our delicious home cooking?")
    }

    private suspend fun Dialogue.anythingElse() {
        chatNpc(neutral, "Anything else I can do for you?")
        options()
    }

    private suspend fun Dialogue.options(goutweed: Boolean = false) {
        val picked =
            if (goutweed) {
                choice4(
                    "I need to find some goutweed.", Topic.Goutweed,
                    "Why do you live so close to the trolls?", Topic.Trolls,
                    "What do you have to offer?", Topic.Offer,
                    "No thanks, Eadgar.", Topic.NoThanks,
                )
            } else {
                choice3(
                    "Why do you live so close to the trolls?", Topic.Trolls,
                    "What do you have to offer?", Topic.Offer,
                    "No thanks, Eadgar.", Topic.NoThanks,
                )
            }
        topic(picked)
    }

    private suspend fun Dialogue.topic(topic: Topic) {
        when (topic) {
            Topic.Goutweed -> {
                askAboutGoutweed()
                chatNpc(neutral, "Goutweed is used as an ingredient in troll cooking. You should ask one of their cooks.")
                eadgarsRuse.advanceTo(access, STAGE_ASKED_EADGAR)
            }
            Topic.Trolls -> {
                chatPlayer(quiz, "Why do you live so close to the trolls? Isn't it dangerous?")
                chatNpc(
                    neutral,
                    "Well, I suppose I do keep getting captured by the trolls and thrown in prison..." +
                        "But they always release me in the end, I'm far too old and skinny for their tastes.",
                )
                chatNpc(happy, "In any case, this is my home, and I'm not leaving it. And this area has the tastiest goats!")
            }
            Topic.Offer -> offer()
            Topic.NoThanks -> noThanks()
        }
    }

    private suspend fun Dialogue.askAboutGoutweed() {
        chatPlayer(neutral, "I need to find some goutweed. Sanfew said you might be able to help.")
        chatNpc(neutral, "Sanfew, you say? Ah, haven't seen him in a while...")
    }

    private suspend fun Dialogue.noThanks() {
        chatPlayer(neutral, "No thanks, Eadgar.")
        chatNpc(happy, "Your loss!")
    }

    private suspend fun Dialogue.offer() {
        chatPlayer(quiz, "What do you have to offer?")
        chatNpc(
            happy,
            "The chef's recommendation for today is mountain goat stew. I'll give some stew in " +
                "exchange for logs for my fire. They're hard to come by around here.",
        )
        val stew = choice2("I'd like some mountain goat stew, please.", true, "No thanks, Eadgar.", false)
        if (!stew) {
            noThanks()
            return
        }
        chatPlayer(happy, "I'd like some mountain goat stew, please.")
        if (LOGS !in player.inv) {
            chatNpc(angry, "You don't have any logs! I may be mad, but I'm not stupid!")
            return
        }
        access.invDel(access.inv, LOGS)
        chatNpc(happy, "Here you go! Enjoy!")
        access.mes("You get a bowl of mountain goat stew.")
        access.mes("You eat the mountain goat stew.")
        access.statHeal("stat.hitpoints", STEW_HEAL, 0)
        access.mes("It heals some health.")
    }

    private suspend fun Dialogue.cookWantsHuman() {
        chatPlayer(
            sad,
            "I talked to the troll cook but he wouldn't tell me anything, and now he wants me to " +
                "find him some tasty human for his stew.",
        )
        chatNpc(worried, "Oh dear, that's no good. You can't just go hand over a human to those trolls...")
        chatNpc(happy, "Aha! I have a plan!")
        chatPlayer(quiz, "Really?")
        chatNpc(happy, "Yes! It's bound to work. First of all, I will need a parrot!")
        chatPlayer(shocked, "A PARROT? Where am I going to find one of those?")
        chatNpc(neutral, "At the zoo, where else?")
        eadgarsRuse.advanceTo(access, STAGE_PARROT_WANTED)
        anythingElse()
    }

    private suspend fun Dialogue.parrotWanted() {
        chatNpc(quiz, "Oh, it's you. Have you got me a parrot yet?")
        if (PARROT in player.inv) {
            explainPlan()
            return
        }
        chatPlayer(sad, "I haven't been able to find one yet.")
        chatNpc(neutral, "Well, go see if the zoo have one.")
        val whereIsZoo = choice2("Where's the zoo?", true, "Okay, I'll be right back.", false)
        if (whereIsZoo) {
            chatPlayer(quiz, "Where's the zoo?")
            chatNpc(neutral, "It's in Ardougne, south west of here.")
        }
        chatPlayer(neutral, "Okay, I'll be right back.")
        anythingElse()
    }

    private suspend fun Dialogue.explainPlan() {
        parrot("Raaawk! Polly wanna cracker!")
        chatPlayer(happy, "Here it is! Now are you going to explain your plan?")
        chatNpc(happy, "Yes, yes, of course. It's quite ingenious really!")
        chatNpc(
            neutral,
            "What we need is something that looks like a human, sounds like a human, smells like " +
                "a human and tastes like a human.",
        )
        eadgarsRuse.advanceTo(access, STAGE_PLAN)
        chatPlayer(quiz, "How are we going to make it look like a human?")
        chatNpc(neutral, LOOK_LIKE)
        chatPlayer(quiz, "How are we going to make it sound like a human?")
        chatNpc(neutral, "That's what the parrot's for, of course.")
        parrot("Who's a pretty boy then?")
        chatNpc(worried, "Although the trolls might get suspicious if it doesn't say what they expect a human to say...")
        chatNpc(neutral, HIDE_IT)
        chatPlayer(quiz, "How are we going to make it smell like a human?")
        chatNpc(neutral, SMELL_LIKE)
        chatNpc(neutral, SANFEW_CLOTHES)
        chatPlayer(quiz, "And how are we going to make it taste like a human?")
        chatNpc(happy, TASTE_LIKE)
        chatNpc(neutral, "That's the plan. Anything else I can do for you?")
        options()
    }

    /** While the parrot is still to be hidden. A lost parrot has flown back to Eadgar. */
    private suspend fun Dialogue.parrotAdvice() {
        if (!player.ownsAnywhere(PARROT)) {
            returnLostParrot()
        }
        chatPlayer(quiz, "What should I do with this parrot?")
        chatNpc(neutral, "I told you, put it somewhere it'll learn to say the sort of thing the trolls will expect it to say.")
        chatNpc(neutral, "This is likely to be screaming. There's bound to be a good spot somewhere in the troll prison.")
        chatNpc(neutral, "Anything else I can do for you?")
        while (true) {
            val picked =
                choice5(
                    "How are we going to make it look like a human?", 1,
                    "How are we going to make it sound like a human?", 2,
                    "How are we going to make it smell like a human?", 3,
                    "How are we going to make it taste like a human?", 4,
                    "More", 5,
                )
            when (picked) {
                1 -> {
                    chatPlayer(quiz, "How are we going to make it look like a human?")
                    chatNpc(neutral, LOOK_LIKE)
                }
                2 -> {
                    chatPlayer(quiz, "How are we going to make it sound like a human?")
                    chatNpc(neutral, "That's what the parrot's for, of course.")
                    chatNpc(neutral, HIDE_IT)
                }
                3 -> {
                    chatPlayer(quiz, "How are we going to make it smell like a human?")
                    chatNpc(neutral, SMELL_LIKE)
                    chatNpc(neutral, SANFEW_CLOTHES)
                }
                4 -> {
                    chatPlayer(quiz, "And how are we going to make it taste like a human?")
                    chatNpc(happy, TASTE_LIKE)
                }
                else -> {
                    options()
                    return
                }
            }
            chatNpc(neutral, "Anything else I can do for you?")
        }
    }

    private suspend fun Dialogue.returnLostParrot() {
        chatNpc(
            neutral,
            "Oh, it's you. You didn't lose your parrot did you? One flew over here and I managed " +
                "to capture it.",
        )
        chatPlayer(sad, "...")
        chatNpc(neutral, "That's what I thought.")
        access.invAddOrDrop(objRepo, PARROT)
        access.mes("Eadgar hands you the drunk parrot.")
    }

    /**
     * Takes whatever the player carries of what is still owed, then has them list what was handed
     * over and Eadgar what is left. Once nothing is owed he moves on to the truth potion.
     */
    private suspend fun Dialogue.handIn() {
        val owed = eadgarsRuse.stillNeeded(player)
        val logs = minOf(owed.logs, access.inv.count(LOGS))
        val chickens = minOf(owed.chickens, access.inv.count(RAW_CHICKEN))
        val grain = minOf(owed.grain, access.inv.count(GRAIN))
        val robe = minOf(owed.robe, access.inv.count(ROBE))
        take(LOGS, logs)
        take(RAW_CHICKEN, chickens)
        take(GRAIN, grain)
        take(ROBE, robe)
        if (logs > 0) player.erLogsGiven = true
        player.erChickensGiven = (player.erChickensGiven + chickens).coerceAtMost(CHICKENS_NEEDED)
        player.erGrainGiven = (player.erGrainGiven + grain).coerceAtMost(GRAIN_NEEDED)
        if (robe > 0) player.erRobeGiven = true
        chatPlayer(neutral, "I have ${supplies(logs, chickens, grain, robe)}.")
        val left = eadgarsRuse.stillNeeded(player)
        if (!left.isEmpty) {
            chatNpc(neutral, "You now need ${supplies(left.logs, left.chickens, left.grain, left.robe)}.")
            anythingElse()
            return
        }
        chatNpc(happy, "That's everything!")
        chatNpc(happy, "Good, good, everything is almost finished.")
        chatNpc(neutral, "Of course, we can't just give him the dummy and expect to get anything useful out of him.")
        chatPlayer(quiz, "Oh?")
        chatNpc(neutral, "No, of course not. We need to make sure he'll tell you what you need to know.")
        chatPlayer(quiz, "And how do we do that?")
        chatNpc(
            neutral,
            "I happen to know that the trolls are susceptible to a certain kind of plant that grows " +
                "around this mountain. I call it Troll Thistle. If properly prepared, it can be " +
                "made into a sort of Troll truth potion.",
        )
        eadgarsRuse.advanceTo(access, STAGE_MAKE_POTION)
        potionRecipe()
    }

    private fun Dialogue.take(obj: String, count: Int) {
        if (count > 0) {
            access.invDel(access.inv, obj, count)
        }
    }

    private suspend fun Dialogue.potionRecipe() {
        chatPlayer(quiz, "How do you prepare it?")
        chatNpc(neutral, "You'll have to dry it in a fire, then grind it and mix it into a potion with Ranarr weed.")
        chatPlayer(neutral, "Okay, I'll be back with that soon.")
        anythingElse()
    }

    private suspend fun Dialogue.truthPotion() {
        if (TROLL_POTION in player.inv) {
            chatPlayer(happy, "I've got the troll truth potion.")
            access.invDel(access.inv, TROLL_POTION)
            chatNpc(happy, "Excellent, thank you. Now just go fetch that poor parrot back, it's probably had enough by now.")
            eadgarsRuse.advanceTo(access, STAGE_FETCH_PARROT)
            return
        }
        chatPlayer(quiz, "How do I make the troll truth potion again?")
        chatNpc(
            neutral,
            "Troll Thistle grows around this mountain. If properly prepared, it can be made into a " +
                "sort of Troll truth potion.",
        )
        potionRecipe()
    }

    private suspend fun Dialogue.makeFakeMan() {
        if (PARROT in player.inv) {
            chatPlayer(happy, "I've brought the parrot.")
            access.invDel(access.inv, PARROT)
            chatNpc(happy, "Hand it here...there we go! Can you tell this isn't a bona fide human being? I sure can't!")
        } else {
            chatNpc(
                neutral,
                "Oh, it's you. You didn't lose your parrot did you? One flew over here and I " +
                    "managed to capture it.",
            )
            chatPlayer(sad, "...")
            chatNpc(neutral, "That's what I thought.")
            chatNpc(
                happy,
                "Just give me a moment...there we go! Can you tell this isn't a bona fide human " +
                    "being? I sure can't!",
            )
        }
        access.invAddOrDrop(objRepo, FAKE_MAN)
        eadgarsRuse.advanceTo(access, STAGE_FAKE_MAN)
        fakeMan("What am I doing here? Ow! Where's the rest of the Guard? Agh! I won't tell you anything!")
        options()
    }

    private suspend fun Dialogue.fakeManReminder() {
        chatNpc(quiz, "Have you given the fake man to the troll cook yet?")
        if (player.ownsAnywhere(FAKE_MAN)) {
            chatPlayer(neutral, "Not yet.")
            chatNpc(neutral, "Go on then, give it to the cook!")
        } else {
            chatPlayer(sad, "No. I lost it.")
            chatNpc(angry, "You bumbling imbecile!")
            access.invAddOrDrop(objRepo, FAKE_MAN)
            chatNpc(neutral, "Never mind, here's one I prepared earlier.")
        }
        anythingElse()
    }

    private suspend fun Dialogue.parrot(text: String) = chatNpcSpecific("Parrot", PARROT_NPC, happy, text)

    private suspend fun Dialogue.fakeMan(text: String) = chatNpcSpecific("Fake man", FAKE_MAN_NPC, shocked, text)

    private enum class Topic { Goutweed, Trolls, Offer, NoThanks }

    companion object {
        const val STEW_POT = "loc.troll_eadgar_cooking_pot"
        const val STEW_HEAL = 10

        val GREETINGS =
            listOf(
                "Happiness in a bowl!",
                "You'll never want to leave!",
                "We don't serve trolls!",
                "Serving all day, every day!",
            )

        const val LOOK_LIKE = "We'll just make a scarecrow. We'll need logs, and 10 sheaves of wheat for stuffing."
        const val HIDE_IT = "You should hide it for a while somewhere it can pick up some more appropriate catchphrases."
        const val SMELL_LIKE = "Well, we'll need to dress it up anyway; if we use dirty clothes it'll smell human to the trolls."
        const val SANFEW_CLOTHES = "I'm a man of few clothes myself, so you might want to ask old Sanfew about that one."
        const val TASTE_LIKE =
            "That's easy, we'll just stuff it with a few chickens. Everything tastes like chicken! " +
                "Five raw chickens should be enough."

        private val NUMBERS = listOf("no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")

        /** "the logs, two chickens, ten bundles of grain and no dirty clothes", as Eadgar says it. */
        fun supplies(logs: Int, chickens: Int, grain: Int, robe: Int): String {
            val logText = if (logs > 0) "the logs" else "no logs"
            val chickenText = "${NUMBERS[chickens]} ${if (chickens == 1) "chicken" else "chickens"}"
            val grainText = "${NUMBERS[grain]} ${if (grain == 1) "bundle" else "bundles"} of grain"
            val robeText = if (robe > 0) "the dirty clothes" else "no dirty clothes"
            return "$logText, $chickenText, $grainText and $robeText"
        }
    }
}
