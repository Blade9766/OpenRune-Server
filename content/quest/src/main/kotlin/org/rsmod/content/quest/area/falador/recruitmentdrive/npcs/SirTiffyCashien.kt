package org.rsmod.content.quest.area.falador.recruitmentdrive.npcs

import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.falador.recruitmentdrive.GazeOfSaradomin
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentDriveQuest
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentDriveQuest.Companion.COINS
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentDriveQuest.Companion.REWARD_COINS
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentDriveQuest.Companion.SALLET
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentDriveQuest.Companion.SHOP_INV
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentDriveQuest.Companion.SHOP_TITLE
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentDriveQuest.Companion.SIR_TIFFY
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentState
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentTesting
import org.rsmod.content.quest.area.falador.recruitmentdrive.rdSpokeToTiffy
import org.rsmod.content.quest.area.falador.recruitmentdrive.respawnPoint
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sir Tiffy Cashien, head of recruitment for the Temple Knights, on his bench in Falador Park. He
 * takes a player put forward by Sir Amik Varze to the training grounds (empty-handed only), welcomes
 * them once they have passed five tests in a row, and afterwards sells initiate armour and moves their
 * respawn point between Lumbridge and Falador.
 */
class SirTiffyCashien
@Inject
constructor(
    private val quest: RecruitmentDriveQuest,
    private val testing: RecruitmentTesting,
    private val shops: Shops,
    private val objRepo: ObjRepository,
) : PluginScript() {
    private enum class Next {
        NOTHING,
        TEST,
        COMPLETE,
        SHOP,
    }

    override fun ScriptContext.startup() {
        onOpNpc1(SIR_TIFFY) { talk(it.npc) }
    }

    internal suspend fun ProtectedAccess.talk(npc: Npc) {
        var next = Next.NOTHING
        startDialogue(npc) { next = conversation() }
        when (next) {
            Next.TEST -> with(testing) { beginTesting() }
            Next.COMPLETE -> complete()
            Next.SHOP -> shops.open(player, SHOP_TITLE, SHOP_INV, buyPercentage = 100.0, sellPercentage = 50.0, changePercentage = 0.0)
            Next.NOTHING -> Unit
        }
    }

    private val Dialogue.male: Boolean
        get() = player.appearance.bodyType == Constants.bodytype_a

    private suspend fun Dialogue.conversation(): Next {
        val stage = quest.stage(player)
        return when {
            quest.unlocked(player) -> afterQuest()
            stage == RecruitmentDriveQuest.STAGE_STARTED && RecruitmentState.allPassed(player) -> passedTests()
            stage == RecruitmentDriveQuest.STAGE_STARTED && player.rdSpokeToTiffy == 1 -> anotherGo()
            stage == RecruitmentDriveQuest.STAGE_STARTED -> firstMeeting()
            else -> standard()
        }
    }

    private suspend fun Dialogue.standard(): Next {
        val greeting = if (male) "What ho, sir." else "What ho, milady."
        chatPlayer(happy, "Hello.")
        chatNpc(happy, "$greeting Spiffing day for a walk in the park, what?")
        chatPlayer(confused, "...spiffing?")
        chatNpc(laugh, "Absolutely, top-hole! Well, can't stay and chat all day, dontchaknow! Ta-ta for now!")
        chatPlayer(confused, "Erm... goodbye.")
        return Next.NOTHING
    }

    private suspend fun Dialogue.firstMeeting(): Next {
        chatPlayer(quiz, "Sir Amik Varze sent me to meet you here for some sort of testing...?")
        chatNpc(happy, "Ah, ${player.displayName}! Amik told me all about you, dontchaknow! Spiffing job you did with the old Black Knights there, absolutely first class.")
        chatPlayer(confused, "...thanks, I think.")
        chatNpc(happy, "Well, a top-notch fellow like yourself is just the right sort we've been seeking for our organisation.")
        while (true) {
            when (choice4("Testing...?", 1, "Organisation...?", 2, "Yes, let's go!", 3, "No, I've changed my mind.", 4)) {
                1 -> testingExplained()
                2 -> organisationExplained()
                3 -> return letsGo()
                else -> return declined()
            }
        }
    }

    private suspend fun Dialogue.anotherGo(): Next {
        chatNpc(happy, "Ah, what ho! Back for another go at the old testing, what?")
        return when (choice2("Yes, let's go!", true, "No, I've changed my mind.", false)) {
            true -> letsGo()
            false -> declined()
        }
    }

    private suspend fun Dialogue.testingExplained() {
        chatPlayer(quiz, "...testing? What exactly do you mean by testing?")
        chatNpc(angry, "Jolly bad show! Varze was supposed to have informed you about all this before sending you here!")
        chatNpc(neutral, "Well, not your fault I suppose, what? Anywho, our organisation is looking for a certain specific type of person to join.")
        chatPlayer(quiz, "So... You want me to go kill some monster or something for you?")
        chatNpc(neutral, "Not at all, old bean. There's plenty of warriors around should we require dumb muscle.")
        chatNpc(neutral, "That's really not the kind of thing our organisation is after, what?")
        chatPlayer(quiz, "So you want me to go and fetch you some kind of common item, and then take it for delivery somewhere on the other side of the country?")
        chatPlayer(angry, "Because I really hate doing that!")
        chatNpc(laugh, "Haw haw haw What a dull thing to ask of someone, what?")
        chatNpc(happy, "I know what you mean though, I did my fair share of running errands when I was a young adventurer, myself!")
        chatPlayer(quiz, "So what exactly will this test consist of?")
        chatNpc(neutral, "Can't let just any old riff-raff in, what? The mindless thugs and bully boys are best left in the White Knights or the Guards, we look for the top-shelf brains to join us.")
        chatPlayer(worried, "So you want to test my brains? Will it hurt?")
        chatNpc(laugh, "Haw Haw Haw That's a good one!")
        chatNpc(neutral, "Not in the slightest... Well, maybe a bit, but we all have to make sacrifices occasionally, what?")
        chatPlayer(quiz, "What do you want me to do then?")
        chatNpc(neutral, "It's a test of wits, what? I'll take you to our secret training grounds, and you will have to pass through a series of five separate intelligence tests to prove you're our sort of adventurer.")
        chatNpc(neutral, "Standard puzzle room rules will apply.")
        chatPlayer(quiz, "Erm... what are 'standard puzzle room rules' exactly?")
        chatNpc(neutral, "Never done this sort of thing before, what?")
        chatNpc(neutral, "The simple rules are: No items or equipment to be brought with you. Each room is a self contained puzzle. You may quit at any time.")
        chatNpc(neutral, "Of course, if you quit a room, then all your progress up to that point will be cleared, and you'll have to start again from scratch.")
        chatNpc(happy, "Our organisation manages to filter all the top-notch adventurers this way. So, are you ready to go?")
    }

    private suspend fun Dialogue.organisationExplained() {
        chatPlayer(quiz, "This organisation you keep mentioning... Perhaps you could tell me a little about it?")
        chatNpc(angry, "Oh, that Amik! Jolly bad form, did he not tell you anything that he was supposed to?")
        chatPlayer(neutral, "No... He didn't really tell me anything except to come here and meet you...")
        chatNpc(neutral, "Well now old sport, let me give you the heads up and the low down, what?")
        chatNpc(neutral, "I represent the Temple Knights. We are the premier order of Knights in Asgarnia, if not the world. Saradomin himself personally founded our order centuries ago, and we answer only to him.")
        chatNpc(neutral, "Only the very best of the best are permitted to join, and the powers we command are formidable indeed.")
        chatNpc(neutral, "You might say that we are the front line of defence for the entire kingdom!")
        chatPlayer(quiz, "So what's the difference between you and the White Knights?")
        chatNpc(laugh, "Well, in simple terms, we're better! Any fool with a sword can manage to get into the White Knights, which is mostly the reason they are so very, very incompetent, what?")
        chatNpc(neutral, "The Temple Knights on the other hand have to be smarter, stronger, better than all others. We are the elite. No man controls us for our orders come directly from Saradomin himself!")
        chatPlayer(happy, "Cool, you mean I get to meet Saradomin?")
        chatNpc(neutral, "Well, uh, not exactly... Sir Vey Lance, our head of operations, is the only one of our order who has ever personally met Saradomin, but everything he tells us to do is done with Saradomin's implicit permission.")
        chatNpc(neutral, "It's not every job where you have more authority than the king, now is it?")
        chatPlayer(shocked, "Wait... You can order the King around?!?!")
        chatNpc(neutral, "Well, not me personally, I'm only in the recruitment side of things dontchaknow, but the higher ranking members of the organisation have almost absolute power over this kingdom.")
        chatNpc(neutral, "Plus a few others, so I hear....")
        chatNpc(neutral, "Anyway, this is why we keep our organisation shrouded in secrecy, and why we demand such rigorous testing for all potential recruits. Speaking of which, are you ready to begin your testing?")
    }

    private suspend fun Dialogue.letsGo(): Next {
        chatPlayer(happy, "Yeah, this sounds right up my street. Let's go!")
        if (testing.entryRefusal(player) != null) {
            val old = if (male) "boy" else "gal"
            chatNpc(sad, "Well, bad luck old $old, you'll need to have a completely empty inventory and you can't be wearing any equipment before we can accurately test you.")
            chatNpc(neutral, "Don't want people cheating by smuggling stuff in, what?")
            chatNpc(neutral, "Come and see me again after you've been to the old bank to drop your stuff off, what?")
            return Next.NOTHING
        }
        chatNpc(happy, "Jolly good show! Now, the training grounds location is a secret so...")
        chatNpc(happy, "Here we go! Mind your head!")
        chatNpc(happy, "Oops, ignore the smell! Nearly there!")
        chatNpc(happy, "And... Here we are! Best of luck!")
        player.rdSpokeToTiffy = 1
        return Next.TEST
    }

    private suspend fun Dialogue.declined(): Next {
        chatPlayer(neutral, "Well... I don't know... This doesn't really sound like my kind of thing...")
        chatPlayer(neutral, "To be honest, I'd rather go kill stuff for loot than use my mind to work things out...")
        chatNpc(neutral, "Well, it takes all sorts to make the world spin, what? Maybe you'll find a nice job with the Guards sometime.")
        chatNpc(sad, "Sad though, your intelligence file seemed quite promising. Should you change your mind, you come back and let me know, what?")
        return Next.NOTHING
    }

    private suspend fun Dialogue.passedTests(): Next {
        chatPlayer(happy, "I believe I've managed to pass the tests!")
        chatNpc(happy, "Oh, jolly well done! Your performance will need to be evaluated by Sir Vey personally, but I don't think it's going too far ahead of myself to welcome you to the team!")
        return Next.COMPLETE
    }

    private fun ProtectedAccess.complete() {
        if (!RecruitmentState.allPassed(player) || !quest.complete(this)) {
            return
        }
        RecruitmentState.clearAttempt(player)
        invAddOrDrop(objRepo, COINS, REWARD_COINS)
        invAddOrDrop(objRepo, SALLET)
        mes("Your respawn point has been left alone, but you can now speak to Sir Tiffy to change it to Falador.")
    }

    private suspend fun Dialogue.afterQuest(): Next {
        val greeting = if (male) "What ho, sir." else "What ho, milady."
        chatNpc(happy, "$greeting Jolly good show on the old training grounds thingy, what?")
        while (true) {
            when (
                choice5(
                    "Do you have any jobs for me yet?", 1,
                    "Can you explain the Gaze of Saradomin to me?", 2,
                    "Can I buy some armour?", 3,
                    "Can I switch respawns please?", 4,
                    "Goodbye.", 5,
                    title = "Select an option",
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Do you have any jobs for me yet?")
                    chatNpc(neutral, "Sorry old bean but we are still in the process of organising.")
                    chatNpc(neutral, "I'm sure that we will have something for you soon, so please feel free to check back later. Anything else I can do for you in the meantime?")
                }
                2 -> explainGaze()
                3 -> {
                    chatPlayer(quiz, "Can I buy some armour?")
                    chatNpc(happy, "Of course old bean. I can sell you up to Initiate level items only I'm afraid.")
                    return Next.SHOP
                }
                4 -> {
                    switchRespawn()
                    return Next.NOTHING
                }
                else -> {
                    chatPlayer(happy, "Well, see you around Tiffy.")
                    chatNpc(happy, "Ta-ta for now, old bean!")
                    return Next.NOTHING
                }
            }
        }
    }

    private suspend fun Dialogue.explainGaze() {
        val title = if (male) "sirrah" else "milady"
        chatPlayer(quiz, "I don't really understand this 'Gaze of Saradomin' thing... Do you think you could explain what it does for me?")
        chatNpc(happy, "Certainly $title! As you know, we Temple Knights are personally favoured by Saradomin himself.")
        chatNpc(laugh, "And when I say personally favoured, I don't mean that sometime off in the future he's going to buy us all a drink!")
        chatNpc(neutral, "He watches over each of us, and when we die he catches us as we fall, and ensures we arrive back at Falador castle safe and sound.")
        chatNpc(neutral, "We usually lose some equipment when he does so, but it's a small price to pay to be hale and hearty again, what?")
        chatNpc(neutral, "Some lucky fellows have a similar system going already, but when they die they spawn in that squalid little swamp village Lumbridge.")
        chatPlayer(shifty, "Yeah, what kind of person would want to spawn there... Certainly not me, and I never have! Honest!")
        chatNpc(happy, "Well, you should be glad that we offer you a step up then! Falador is clearly a far superior town to spend your time in!")
        chatNpc(happy, "Was there something else you wanted to ask good old Tiffy, $title?")
    }

    private suspend fun Dialogue.switchRespawn() {
        chatPlayer(quiz, "Can I switch respawns, please?")
        val toFalador = player.respawnPoint != GazeOfSaradomin.FALADOR
        if (toFalador) {
            chatNpc(happy, "Ah, so you'd like to respawn in Falador, the good old homestead! Are you sure?")
        } else {
            chatNpc(shocked, "What? You're saying you want to respawn in Lumbridge? Are you sure?")
        }
        val yes = if (toFalador) "Yes, I want to respawn in Falador." else "Yes, I want to respawn in Lumbridge."
        if (!choice2(yes, true, "Actually, no thanks. I like my respawn point.", false)) {
            chatPlayer(neutral, "Actually, no thanks. I like my respawn point.")
            chatNpc(neutral, "As you wish, what? Ta-ta for now.")
            return
        }
        chatPlayer(happy, yes)
        if (toFalador) {
            player.respawnPoint = GazeOfSaradomin.FALADOR
            chatNpc(happy, "Top-hole, what? Good old Fally is definitely the hot-spot nowadays!")
        } else {
            player.respawnPoint = GazeOfSaradomin.LUMBRIDGE
            chatNpc(neutral, "Why anyone would want to visit that smelly little swamp village of oiks is quite beyond me, I'm afraid, but the deed is done now.")
        }
    }
}
