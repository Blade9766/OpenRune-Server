package org.rsmod.content.quest.area.desert.icthlarin.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.front
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.other.pets.cats.CatPet
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinCats
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.CATSPEAK_AMULET
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.SPHINX_TOKEN
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_FIRST_FLASHBACK_DONE
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_SPHINX_TOKEN
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_WOKE_IN_SOPHANEM
import org.rsmod.content.quest.area.desert.icthlarin.ilhGaveToken
import org.rsmod.content.quest.area.desert.icthlarin.ilhMetSphinx
import org.rsmod.content.quest.area.desert.icthlarin.ilhSphinxTookCat
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Sphinx, guardian of feline kind, lounging in the middle of Sophanem. She has no time for
 * humans, but will talk to anyone who brings a cat, and it is their cat she really talks to.
 */
class Sphinx
@Inject
constructor(
    private val quest: IcthlarinsLittleHelperQuest,
    private val cats: IcthlarinCats,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(SPHINX) { startDialogue(it.npc) { talk() } }
    }

    private suspend fun Dialogue.talk() {
        val stage = quest.stage(player)
        when {
            stage < STAGE_WOKE_IN_SOPHANEM -> ignored()
            stage < STAGE_FIRST_FLASHBACK_DONE -> askForHelp()
            stage == STAGE_FIRST_FLASHBACK_DONE -> riddle()
            stage == STAGE_SPHINX_TOKEN -> afterToken()
            else -> mockGuardian()
        }
    }

    private suspend fun Dialogue.ignored() {
        val cat = cats.anyPet(player)
        if (cat == null) {
            chatPlayer(happy, "Good day.")
            mesbox("The Sphinx ignores you.")
            return
        }
        catGreeting(cat)
        mesbox("The Sphinx seems to have no interest in you whatsoever.")
    }

    /** Before the first flashback, the Sphinx can only suggest that something might jog the memory. */
    private suspend fun Dialogue.askForHelp() {
        cats.anyPet(player)?.let { catGreeting(it) }
        chatPlayer(quiz, "Excuse me. I need help.")
        if (player.ilhMetSphinx) {
            chatNpc(
                neutral,
                "We've already had this conversation, have we not? You're in a spot of bother, but " +
                    "you can't remember what happened to you.",
            )
            chatPlayer(sad, "Pretty much.")
            chatNpc(
                neutral,
                "Perhaps you should try to find something to remind you again. I find that important " +
                    "objects and locations can trigger memories, no matter how repressed they are.",
            )
            return
        }
        chatNpc(neutral, "Yes, your companion tells me that you are in a spot of bother.")
        chatPlayer(quiz, "What companion? Oh, you mean my pet cat?")
        chatNpc(neutral, "Indeed.")
        chatPlayer(
            worried,
            "Do you have any idea what happened to me? One moment I was talking to a wanderer in the " +
                "desert, the next I was here. I also have a cracking headache.",
        )
        chatNpc(quiz, "A wanderer?")
        chatPlayer(neutral, "Yes. She had red hair, and weird red eyes as well.")
        chatNpc(
            neutral,
            "Interesting, very interesting. I'm afraid I do not know what happened to you, but it " +
                "sounds like quite a bit occurred between you meeting that wanderer and now.",
        )
        chatPlayer(sad, "But I can't remember any of it.")
        chatNpc(
            neutral,
            "Then I suggest you find something to remind you. I find that important objects and " +
                "locations can trigger memories, no matter how repressed they are.",
        )
        player.ilhMetSphinx = true
    }

    private suspend fun Dialogue.riddle() {
        chatPlayer(happy, "Good day.")
        val cat = cats.anyPet(player)
        if (cat == null) {
            chatNpc(
                neutral,
                "I have no interest in speaking with you. You should bring your cat to me again. The " +
                    "two of us have much to discuss.",
            )
            if (player.ilhSphinxTookCat) {
                chatPlayer(angry, "But you stole my last one!")
                chatNpc(neutral, "Well, you would be well advised to get another.")
            }
            return
        }
        catConverses(cat)
        chatPlayer(happy, "I managed to regain some of my memories.")
        if (player.ilhHeardGraveRobbing) {
            chatNpc(
                neutral,
                "Yes, your cat told me already. Something to do with grave robbing, tisk tisk. So " +
                    "unbecoming of a guardian of cats.",
            )
        } else {
            chatNpc(
                neutral,
                "Yes, your cat tells me you're in a spot of bother. Something to do with grave " +
                    "robbing, tisk tisk. So unbecoming of a guardian of cats.",
            )
            player.ilhHeardGraveRobbing = true
        }
        chatPlayer(worried, "Can you help me? Please?")
        chatNpc(
            laugh,
            "Well, I have to say I enjoy a laugh at someone's expense now and again... so let's have " +
                "a bit of fun.",
        )
        chatPlayer(worried, "I don't like the sound of this.")
        chatNpc(neutral, "I'll help you if you can solve a simple puzzle of mine, but...")
        chatPlayer(sad, "There's always a 'but', isn't there?")
        chatNpc(neutral, "... but if you answer incorrectly, I get to keep your cat.")
        if (!choice2("Okay, that sounds fair.", true, "I don't want to risk my cat.", false)) {
            chatPlayer(worried, "I don't want to risk my cat.")
            disappointed()
            return
        }
        chatPlayer(happy, "Okay, that sounds fair.")
        chatNpc(
            neutral,
            "A husband and wife have six sons and each son has one sister. How many people are in " +
                "the family?",
        )
        val answer = choice5("7.", 7, "9.", 9, "12.", 12, "14.", 14, "I don't know.", 0)
        if (answer == 0) {
            chatPlayer(confused, "I don't know.")
            disappointed()
            return
        }
        chatPlayer(neutral, "$answer.")
        chatNpc(quiz, "Are you sure?")
        if (answer != RIDDLE_ANSWER) {
            mesbox("Your cat shakes its head frantically and starts to scratch your leg.")
        }
        if (!choice2("Totally positive.", true, "I don't know. I don't want to risk my cat.", false)) {
            chatPlayer(worried, "I don't know. I don't want to risk my cat.")
            disappointed()
            return
        }
        chatPlayer(happy, "Totally positive.")
        if (answer == RIDDLE_ANSWER) {
            chatNpc(happy, "Well answered, human. I guess you get to keep your cat.")
            chatPlayer(quiz, "Now, will you help me?")
            chatNpc(neutral, "With your grave robbing problem?")
        } else {
            takeCat()
            chatPlayer(quiz, "Could you at least help me now?")
            chatNpc(neutral, "What was it you needed help with? Oh yes, I remember now. You're a grave robber.")
        }
        graveRobber()
    }

    private suspend fun Dialogue.takeCat() {
        mesbox("Your cat abandons you for the Sphinx.")
        with(cats) { access.loseCat() }
        access.soundSynth(HAPPY_MEEOOW)
        player.ilhSphinxTookCat = true
        val pronoun = if (player.appearance.bodyType == 1) "she" else "he"
        chatNpc(neutral, "I think your master has proven that $pronoun is too foolish to look after you. Come with me.")
        chatPlayer(
            angry,
            "Oh, come on! You can't take my cat. I've spent so long training and looking after it!",
        )
        chatNpc(
            neutral,
            "Well, you should have thought of that before you risked it for some advice from a crazy " +
                "old Sphinx.",
        )
    }

    private suspend fun Dialogue.graveRobber() {
        chatPlayer(angry, "I didn't rob anybody's grave!")
        chatNpc(
            neutral,
            "Too true, you didn't rob just anybody's grave. You managed to rob the grave of Klenter, " +
                "the recently deceased High Priest of Icthlarin. Do you know what that means?",
        )
        chatPlayer(confused, "Er... no.")
        chatNpc(
            neutral,
            "Icthlarin is the Menaphite god of the dead. By desecrating the tomb of one of his " +
                "priests, you've made an enemy out of every person in this city.",
        )
        chatPlayer(angry, "But I didn't desecrate any tomb! There's no proof!")
        chatNpc(
            neutral,
            "Really? Because if I'm not mistaken, that's Klenter's shade right over there. He " +
                "definitely doesn't seem happy with you. There's also the burial jar in your " +
                "possession. How do you explain that?",
        )
        chatPlayer(shocked, "How did you know about the burial jar?")
        chatNpc(neutral, "I can smell the rotting organs from here.")
        chatPlayer(
            worried,
            "Look, I don't know exactly what happened. All I know is that wanderer hypnotised me and " +
                "had me steal something from this pyramid.",
        )
        chatNpc(quiz, "The burial jar?")
        chatPlayer(confused, "I... I guess so. It's all still very unclear.")
        chatNpc(
            neutral,
            "Interesting. I'm afraid I can't help you. I have my own concerns without involving " +
                "myself with petty human affairs.",
        )
        chatNpc(
            neutral,
            "However, I suggest you seek out the new High Priest of Icthlarin to the south east. He " +
                "might be able to assist you. Of course, you'll need to convince him to talk to you first.",
        )
        chatNpc(neutral, "Here, I have a token that he will know is from me. Take that to him, and I'm sure he will speak with you.")
        access.invAdd(access.inv, SPHINX_TOKEN)
        quest.advanceTo(access, STAGE_SPHINX_TOKEN)
        objbox(SPHINX_TOKEN, "The Sphinx gives you a token.")
        chatPlayer(neutral, "Thanks... I guess.")
        topics()
    }

    private suspend fun Dialogue.afterToken() {
        val cat = cats.anyPet(player)
        if (cat == null) {
            chatNpc(
                neutral,
                "I have no interest in speaking with you. You should bring your cat to me again. The " +
                    "two of us have much to discuss.",
            )
            if (player.ilhSphinxTookCat) {
                chatPlayer(angry, "But you stole my last one!")
                chatNpc(neutral, "Well, you would be well advised to get another.")
            }
            return
        }
        catGreeting(cat)
        if (SPHINX_TOKEN !in player.inv && !player.ilhGaveToken) {
            chatPlayer(sad, "I lost that token you gave me. Could I have another please?")
            chatNpc(neutral, "Ah, you humans.")
            access.invAdd(access.inv, SPHINX_TOKEN)
            objbox(SPHINX_TOKEN, "The Sphinx gives you another token.")
            chatPlayer(happy, "Thanks.")
        }
        topics()
    }

    private suspend fun Dialogue.topics() {
        var topic =
            choice3(
                "Tell me about yourself.", TOPIC_HERSELF,
                "What's going on in this city?", TOPIC_CITY,
                "I'll go speak with the High Priest.", TOPIC_LEAVE,
            )
        while (true) {
            topic =
                when (topic) {
                    TOPIC_HERSELF -> {
                        aboutHerself()
                        choice2("What's going on in this city?", TOPIC_CITY, GET_GOING, TOPIC_GET_GOING)
                    }
                    TOPIC_CITY -> {
                        aboutTheCity()
                        choice2("Tell me about yourself.", TOPIC_HERSELF, GET_GOING, TOPIC_GET_GOING)
                    }
                    TOPIC_GET_GOING -> {
                        chatPlayer(neutral, GET_GOING)
                        return
                    }
                    else -> {
                        chatPlayer(neutral, "I'll go speak with the High Priest.")
                        return
                    }
                }
        }
    }

    private suspend fun Dialogue.aboutHerself() {
        chatPlayer(quiz, "Tell me about yourself.")
        chatNpc(
            neutral,
            "I am an agent of neither good nor evil. I am unconcerned with the plight of mankind and " +
                "their petty wars and beliefs.",
        )
        chatPlayer(quiz, "Well, now that you've told me what you're not, can you perhaps tell me what you are?")
        chatNpc(neutral, "I am the guardian of feline kind.")
        chatPlayer(quiz, "Why would they need a guardian?")
        chatNpc(
            neutral,
            "Cats play a significant role in the religion of this area, particularly when it comes " +
                "to guarding the dead from the Devourer.",
        )
        chatPlayer(quiz, "The Devourer?")
        chatNpc(neutral, "She's the very incarnation of destruction.")
        chatPlayer(confused, "I don't understand.")
        chatNpc(quiz, "What understanding do you have of the Menaphite gods?")
        chatPlayer(neutral, "Little to none.")
        chatNpc(neutral, "Well the Menaphite Pantheon is made up of four main deities.")
        chatNpc(
            neutral,
            "The two main gods of the pantheon are Tumeken, the god of light, and Elidinis, the " +
                "goddess of fertility.",
        )
        chatNpc(neutral, "Tumeken and Elidinis also have two children. Icthlarin is one, and the Devourer is the other.")
        chatPlayer(laugh, "They named her the Devourer? Not great at parenting, were they?")
        chatNpc(neutral, "She was not always called such. She is known by her deeds now, rather than her name.")
        chatPlayer(quiz, "So if the Devourer is the bad sibling, does that make Icthlarin the good one?")
        chatNpc(
            neutral,
            "That is the common belief, but things are rarely that simple. In some ways, neither of " +
                "them are good or bad.",
        )
        chatNpc(
            neutral,
            "Icthlarin is the god of the dead. He takes care of the passing of souls to their next " +
                "life. The Devourer is the goddess of destruction, and that is what she craves.",
        )
        chatPlayer(quiz, "I thought you said she wasn't evil?")
        chatNpc(
            neutral,
            "That's not quite what I said. Still, is destruction enough to make one evil? Fire " +
                "destroys, but is that evil?",
        )
        chatPlayer(neutral, "Um... no, but it also creates heat.")
        chatNpc(
            neutral,
            "We could have an overlong theological debate now, but let's instead get to my point. " +
                "Destruction has a place, so maybe the Devourer does as well. However, she has taken it too far.",
        )
        chatPlayer(quiz, "How?")
        chatNpc(
            neutral,
            "The Devourer wishes to destroy every soul in existence, living or dead. As you can " +
                "imagine, this has brought her into conflict with Icthlarin.",
        )
        chatPlayer(
            neutral,
            "Ah, I see where this is going. The cats are used to protect the dead from the Devourer, " +
                "like you said earlier. But who protects the cats?",
        )
        chatNpc(
            neutral,
            "Well anticipated. The Devourer is terrified of cats for reasons I will not explain. " +
                "However, her followers, although few in number, are not. I ensure the survival of " +
                "cats. For this service, Icthlarin grants me eternal life.",
        )
        chatPlayer(quiz, "So, then, you are a follower of Icthlarin?")
        chatNpc(
            neutral,
            "Nothing of the sort. As I've said before, I have no interest in humans and their gods. " +
                "I look after my kind. For that, Icthlarin looks after me.",
        )
    }

    private suspend fun Dialogue.aboutTheCity() {
        chatPlayer(quiz, "What's going on in this city?")
        chatNpc(neutral, "I thought that would be pretty obvious.")
        chatPlayer(happy, "Well, I could benefit from your point of view. You seem the independent type.")
        chatNpc(
            happy,
            "Flattery, my adventurer, will get you everywhere. But to answer your question, Sophanem " +
                "has been struck with plagues.",
        )
        chatPlayer(quiz, "More than one?")
        chatNpc(neutral, "Quite a number. There's a plague of frogs to the west. In the east, you can also find locusts.")
        chatNpc(
            neutral,
            "The cows have only been producing sour milk since the outbreak, and the residents have " +
                "been struck down by a rash of spots.",
        )
        chatPlayer(quiz, "The residents? There are a lot of living people here for a city of the dead.")
        chatNpc(
            neutral,
            "Sophanem is called the city of the dead because the dead are brought here. Death plays " +
                "a rather large role in the beliefs of the people and, as a result, the business.",
        )
    }

    /** Once the High Priest has taken the player in hand, the Sphinx only has barbs to offer. */
    private suspend fun Dialogue.mockGuardian() {
        val cat = cats.anyPet(player)
        if (cat == null) {
            chatNpc(
                neutral,
                "I have no interest in speaking with you. You should bring your cat to me again. The " +
                    "two of us have much to discuss.",
            )
            return
        }
        catGreeting(cat)
        if (quest.isComplete(player) && !hasCatspeakAmulet()) {
            chatPlayer(sad, "I seem to have lost the amulet the High Priest gave me.")
            chatNpc(neutral, "Careless human. Your cat will be wanting to talk to you, so here is another.")
            access.invAdd(access.inv, CATSPEAK_AMULET)
            objbox(CATSPEAK_AMULET, "The Sphinx gives you an amulet of catspeak.")
            return
        }
        chatNpc(neutral, "Ah, human. How go things, great guardian of cats?")
        chatPlayer(angry, "Are you mocking me?")
        chatNpc(neutral, "I don't need to mock you, human. You're doing a good enough job of that on your own.")
        chatPlayer(angry, "Fine. Come on, cat. Lets leave the nasty Sphinx alone.")
        chatNpcSpecific("Cat", cat.npc, happy, "Meow!")
    }

    private fun Dialogue.hasCatspeakAmulet(): Boolean =
        player.front?.let { CATSPEAK_AMULET_ID == it.id } == true ||
            access.inv.count(CATSPEAK_AMULET) > 0 ||
            access.bank.count(CATSPEAK_AMULET) > 0

    private suspend fun Dialogue.disappointed() {
        chatNpc(neutral, "How disappointing. Still, nothing ventured, nothing gained. Goodbye.")
    }

    private suspend fun Dialogue.catGreeting(cat: CatPet) {
        chatPlayer(happy, "Good day.")
        catConverses(cat)
    }

    private suspend fun Dialogue.catConverses(cat: CatPet) {
        mesbox("The Sphinx ignores you.")
        chatNpc(happy, "Ah, how interesting... a cat. Come here to me, kitty.")
        access.soundSynth(MEEOOW)
        chatNpcSpecific("Cat", cat.npc, happy, "Meow.")
        mesbox("Your cat and the Sphinx converse in a yeowling language for a short time.")
    }

    private companion object {
        const val SPHINX = "npc.ics_little_sphinx"
        const val RIDDLE_ANSWER = 9

        const val MEEOOW = "synth.meeoow"
        const val HAPPY_MEEOOW = "synth.happy_meeoow"

        const val TOPIC_HERSELF = 1
        const val TOPIC_CITY = 2
        const val TOPIC_LEAVE = 3
        const val TOPIC_GET_GOING = 4
        const val GET_GOING = "This is all very interesting, but I've got to get going."

        val CATSPEAK_AMULET_ID: Int by lazy { CATSPEAK_AMULET.asRSCM(RSCMType.OBJ) }

        /** The Sphinx has already heard about the grave robbing from the player's cat. */
        var Player.ilhHeardGraveRobbing by boolVarBit("varbit.ics_met_sphinx")
    }
}
