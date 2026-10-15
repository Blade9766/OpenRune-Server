package org.rsmod.content.skills.construction.scripts

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.construction.data.Trophies
import org.rsmod.content.skills.construction.data.Trophies.Kind
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Talk-to on a mounted head. Lines follow the wiki transcripts; each head speaks through its own
 * chathead npc, picked by the display it hangs on. A guest gets the head's shorter guest
 * conversation, which names the house owner.
 */
class TalkingHeadsScript @Inject constructor(private val registry: HouseRegistry) : PluginScript() {
    override fun ScriptContext.startup() {
        for (trophy in Trophies.ALL.filter { it.kind == Kind.HEAD }) {
            for (tier in trophy.tier..3) {
                val wood = Kind.HEAD.woods[tier - 1]
                onOpLoc1(trophy.loc(tier)) {
                    val owner = registry.houseAt(player.coords)?.owner
                    startDialogue {
                        if (owner != null && owner !== player) {
                            guestTalk(trophy.key, wood, owner)
                        } else {
                            talk(trophy.key, wood)
                        }
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.talk(key: String, wood: String) {
        when (key) {
            "crawlinghand" -> crawlingHand(Head("Crawling hand", "npc.poh_mounted_crawlinghand_$wood"))
            "cockatrice" -> cockatrice(Head("Cockatrice", "npc.poh_mounted_cockatrice_$wood"))
            "basilisk" -> basilisk(Head("Basilisk", "npc.poh_mounted_basilisk_$wood"))
            "kurask" -> kurask(Head("Kurask", "npc.poh_mounted_kurask_$wood"))
            "abyssal" -> abyssalDemon(Head("Abyssal demon", "npc.poh_mounted_abyssaldemon_$wood"))
            "kbd" -> kingBlackDragon()
            "kalphitequeen" -> kalphiteQueen(Head("Kalphite Queen", "npc.poh_mounted_kq"))
            "vorkath" -> vorkath(Head("Vorkath", "npc.poh_mounted_vorkath"))
            "alchemical_hydra" -> hydra(Head("Alchemical Hydra", "npc.poh_mounted_hydra"))
        }
    }

    private suspend fun Dialogue.guestTalk(key: String, wood: String, owner: Player) {
        val name = owner.displayName
        val subject = owner.appearance.subjectPronoun().lowercase()
        val objective =
            when (subject) {
                "he" -> "him"
                "she" -> "her"
                else -> "them"
            }
        when (key) {
            "crawlinghand" -> {
                val hand = Head("Crawling hand", "npc.poh_mounted_crawlinghand_$wood")
                chatPlayer(happy, "Hey, a crawling hand!")
                say(hand, quiz, "Yes, what?")
                chatPlayer(laugh, "$name must be pretty handy to have slayed that!")
            }
            "cockatrice" -> {
                chatPlayer(happy, "Hey, a cockatrice!")
                say(
                    Head("Cockatrice", "npc.poh_mounted_cockatrice_$wood"),
                    angry,
                    "$name deaded me! That wasn't very nice!",
                )
            }
            "basilisk" -> {
                say(Head("Basilisk", "npc.poh_mounted_basilisk_$wood"), bored, "What do you want?")
                chatPlayer(worried, "Oh, er, nothing!")
            }
            "kurask" -> {
                say(Head("Kurask", "npc.poh_mounted_kurask_$wood"), verymad, "I KILL YOU!!!")
                chatPlayer(laugh, "No, $name kill you!")
            }
            "abyssal" -> {
                chatPlayer(happy, "$name killed an abyssal demon! Cool!")
                say(
                    Head("Abyssal demon", "npc.poh_mounted_abyssaldemon_$wood"),
                    neutral,
                    "Cool for $objective maybe. How would you like to be stuck on a wall?",
                )
            }
            "kbd" -> {
                val left = Head("King Black Dragon", "npc.poh_mounted_kbd_left")
                val middle = Head("King Black Dragon", "npc.poh_mounted_kbd_middle")
                val right = Head("King Black Dragon", "npc.poh_mounted_kbd_right")
                chatPlayer(happy, "Hey, $name killed the King Black Dragon!")
                say(middle, angry, "No $subject didn't!")
                say(
                    left,
                    shifty,
                    "What? Oh, ah, no, of course $subject didn't. We're actually an artificial " +
                        "likeness of the King Black Dragon. No one could really kill the King Black " +
                        "Dragon!",
                )
                say(middle, shifty, "No! We're - I mean, it's - far too powerful!")
                say(right, confused, "What are you talking about? Of course we're the King Black Dragon!")
                say(middle, angry, "Shut up, you idiot!")
            }
            "kalphitequeen" -> {
                val queen = Head("Kalphite Queen", "npc.poh_mounted_kq")
                say(queen, angry, "Soft-thing! How dare you approach the queen of the kalphite?")
                chatPlayer(laugh, "$name killed you! I can do what I like.")
                say(
                    queen,
                    angry,
                    "$name killed me but you could not. My successor will be as strong as me. Come " +
                        "down to meet her, she is unafraid.",
                )
            }
            "vorkath" -> {
                val dragon = Head("Vorkath", "npc.poh_mounted_vorkath")
                chatPlayer(happy, "A defeated dragon is a good dragon.")
                say(dragon, angry, "With the power of my 3 legs, I will get my revenge.")
                chatPlayer(neutral, "Sorry to tell you this... but you don't even have 3 legs now, you're a head.")
                say(dragon, angry, "Who do I have to blame for this?")
                chatPlayer(shifty, "You should probably talk to $name about that.")
            }
            "alchemical_hydra" -> {
                val hydra = Head("Alchemical Hydra", "npc.poh_mounted_hydra")
                chatPlayer(quiz, "You look a bit put out there, what's wrong?")
                say(hydra, angry, "I will crush you with the power of all five heads!")
                chatPlayer(laugh, "Well three seem to be missing now!")
                say(hydra, shocked, "What? HOW?")
                chatPlayer(shifty, "You should probably talk to $name about that.")
            }
        }
    }

    private class Head(val title: String, val npc: String)

    private suspend fun Dialogue.say(head: Head, mesanim: MesAnimType, text: String) {
        chatNpcSpecific(head.title, head.npc, mesanim, text)
    }

    private suspend fun Dialogue.crawlingHand(hand: Head) {
        when (access.random.of(0, 2)) {
            0 -> {
                chatPlayer(laugh, "Hey, I was going to make some furniture, do you think you could lend a HAND?")
                say(hand, bored, "Very funny.")
            }
            1 -> {
                chatPlayer(happy, "Hey, hand, do you want to know how I slayed you?")
                say(hand, quiz, "I don't know, how?")
                chatPlayer(laugh, "Because you're just a hand! You're ARMLESS!")
            }
            else -> {
                chatPlayer(quiz, "Hey, you're just a hand, right? So what do you eat?")
                say(hand, happy, "Finger food, of course!")
            }
        }
    }

    private suspend fun Dialogue.cockatrice(bird: Head) {
        say(bird, angry, "You deaded me!")
        chatPlayer(neutral, "Well, yes.")
        say(bird, angry, "What did you do that for?")
        when (
            choice3(
                "A slayer master told me to",
                0,
                "So I could mount your head on my wall",
                1,
                "I just wanted to",
                2,
            )
        ) {
            0 -> {
                chatPlayer(neutral, "A slayer master told me to.")
                say(bird, sad, "Why do the slayer masters all pick on poor cockatrice?")
                chatPlayer(neutral, "They pick on lots of other creatures too.")
                say(bird, angry, "Then mount one of them on your wall and let poor cockatrice rest in peace!")
            }
            1 -> {
                chatPlayer(neutral, "So I could mount your head on my wall.")
                say(bird, sad, "Another cockatrice falls victim to the dreaded mirror shield!")
                chatPlayer(happy, "Don't take it personally! You look good on my wall!")
                say(bird, angry, "I don't care! I think I looked better with a body!")
            }
            else -> {
                chatPlayer(neutral, "I just wanted to.")
                say(bird, verymad, "You dirty rotten swine, you!")
                chatPlayer(shocked, "Steady on...")
                say(bird, angry, "I will kill you with my paralyzing-type magic eyes look!")
                say(bird, angry, "Dots appear in air between eyes and victim. Dot! Dot! Dotty!")
                chatPlayer(confused, "Er, nothing's happening...")
                say(bird, angry, "Concentrates mental power. Eyes narrow beak clenches veins on head stand out.")
                say(bird, angry, "Strain!")
                chatPlayer(neutral, "You're dead, cockatrice. Your eyes are glass beads. It won't work.")
                say(bird, verymad, "STRA-A-AIN!")
                chatPlayer(neutral, "I think I'll leave you to it.")
            }
        }
    }

    private suspend fun Dialogue.basilisk(lizard: Head) {
        say(lizard, bored, "What do you want?")
        when (
            choice4(
                "I want to mock you",
                0,
                "I want to apologise for killing you",
                1,
                "I just wanted to check that you're okay",
                2,
                "Nothing",
                3,
            )
        ) {
            0 -> {
                chatPlayer(laugh, "I want to mock you.")
                say(lizard, bored, "All right. Go on then.")
                chatPlayer(laugh, "You're a ${insult()}!")
                say(lizard, bored, "I'm going back to sleep.")
            }
            1 -> basiliskApology(lizard)
            2 -> {
                chatPlayer(quiz, "I just wanted to check that you're okay.")
                say(lizard, bored, "Apart from being dead and stuffed and hanging on a wall, you mean?")
                chatPlayer(neutral, "Uh... yeah, apart from that are you okay?")
                say(lizard, neutral, "Actually there's something blocking my view of the far wall.")
                chatPlayer(confused, "I don't see anything.")
                say(lizard, neutral, "Perhaps if you were to move to one side of me.")
                mesbox("You walk to the side of the basilisk head...")
                chatPlayer(confused, "I still don't see anything.")
                say(lizard, neutral, "Oh, it's moved away. I can see now.")
            }
            else -> {
                chatPlayer(neutral, "Nothing.")
                say(lizard, bored, "Leave me alone.")
            }
        }
    }

    private suspend fun Dialogue.basiliskApology(lizard: Head) {
        chatPlayer(sad, "I want to apologise for killing you.")
        say(lizard, bored, "Go on then.")
        chatPlayer(sad, "I'm, um, very sorry I killed you.")
        say(lizard, bored, "Really sorry?")
        if (!choice2("Yes, really", true, "No, not really", false)) {
            chatPlayer(laugh, "No, not really!")
            say(lizard, bored, "I don't care.")
            return
        }
        chatPlayer(sad, "Yes really!")
        say(lizard, bored, "Really really?")
        if (!choice2("Yes, really really", true, "Don't push it", false)) {
            chatPlayer(angry, "Don't push it!")
            say(lizard, bored, "I don't care anyway.")
            return
        }
        chatPlayer(sad, "Yes, really really!")
        say(lizard, bored, "Fat lot of good that does, I'm still dead.")
        when (
            choice3(
                "I'm not THAT sorry",
                0,
                "But will you forgive me?",
                1,
                "I promise not to do it again",
                2,
            )
        ) {
            0 -> {
                chatPlayer(angry, "I'm not THAT sorry!")
                say(lizard, bored, "I don't care.")
            }
            1 -> {
                chatPlayer(quiz, "Will you forgive me?")
                say(lizard, happy, "Of course I'll forgive you!")
                chatPlayer(happy, "Really?")
                say(lizard, angry, "No!")
            }
            else -> {
                chatPlayer(sad, "I promise not to do it again.")
                say(lizard, bored, "Of couse you won't do it again, you can only kill me once.")
                when (
                    choice2(
                        "That's why I won't do it again",
                        true,
                        "But I won't do it to any other basilisks",
                        false,
                    )
                ) {
                    true -> {
                        chatPlayer(neutral, "That's why I won't do it again! There'd be no point!")
                        say(lizard, bored, "I don't care.")
                    }
                    false -> {
                        chatPlayer(sad, "But I won't do it to any other basilisks!")
                        say(lizard, quiz, "Really?")
                        when (choice3("Yes, really!", 0, "No, not really!", 1, "Don't start that again!", 2)) {
                            0 -> {
                                chatPlayer(sad, "Yes, really!")
                                say(lizard, bored, "All right then. Apology accepted. Now leave me alone.")
                            }
                            1 -> {
                                chatPlayer(laugh, "No, not really!")
                                say(lizard, bored, "I don't care.")
                            }
                            else -> {
                                chatPlayer(angry, "Don't start that again!")
                                say(lizard, bored, "Leave me alone then.")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Dialogue.insult(): String {
        fun pick(words: List<String>) = words[access.random.of(0, words.size - 1)]
        val adjective = pick(listOf("boring", "fat", "hideous", "puny", "smelly", "stupid"))
        val thing = pick(listOf("beetle", "chicken", "egg", "mud", "slime", "worm"))
        val verb = pick(listOf("brained", "eating", "like", "loving", "smelling", "witted"))
        val noun = pick(listOf("basilisk", "idiot", "lizard", "mudworm", "slimeball", "weakling"))
        return "$adjective $thing-$verb $noun"
    }

    private suspend fun Dialogue.kurask(kurask: Head) {
        say(kurask, verymad, "I KILL YOU!!!")
        chatPlayer(angry, "No, I kill you!")
        say(kurask, verymad, "UUUHRG! Now I kill you!")
        chatPlayer(laugh, "How are you going to do that? You're just a head on a wall!")
        say(kurask, angry, "Uhhhhrrr...")
        when (
            choice3(
                "Why are you so violent?",
                0,
                "What do you think about up there?",
                1,
                "I killed you really easily!",
                2,
            )
        ) {
            0 -> kuraskViolence(kurask)
            1 -> kuraskThoughts(kurask)
            else -> kuraskGloat(kurask)
        }
    }

    private suspend fun Dialogue.kuraskViolence(kurask: Head) {
        chatPlayer(quiz, "Why are you so violent?")
        say(kurask, angry, "You kill me! Uuurgh! That make me angry!")
        when (
            choice3(
                "You seemed pretty angry before I killed you",
                0,
                "I'm sorry I killed you",
                1,
                "I killed you really easily!",
                2,
            )
        ) {
            0 -> {
                chatPlayer(neutral, "You seemed pretty angry before I killed you!")
                say(kurask, madlaugh, "I like angry!")
            }
            1 -> {
                chatPlayer(sad, "I'm sorry I killed you.")
                say(kurask, verymad, "I hate sorry! Makes me more angry! WANT TO KILL YOU!")
                if (choice2("Please try to calm down", true, "I'm not really sorry", false)) {
                    chatPlayer(worried, "Please try to calm down!")
                    say(kurask, madlaugh, "Hate calm! Smash it! Hur hur hur!")
                } else {
                    chatPlayer(laugh, "I'm not really sorry!")
                    say(kurask, verymad, "That make me more angry! Uuuurgh!")
                    chatPlayer(quiz, "Is there anything that doesn't make you angry?")
                    say(kurask, madlaugh, "No! I like angry! Hur hur hur!")
                }
            }
            else -> kuraskGloat(kurask)
        }
    }

    private suspend fun Dialogue.kuraskThoughts(kurask: Head) {
        chatPlayer(quiz, "What do you think about up there?")
        say(kurask, confused, "Think?")
        chatPlayer(neutral, "You know, what goes through your tiny stuffed head?")
        say(kurask, angry, "Little bugs...")
        chatPlayer(shocked, "You have bugs living in you? Eww!")
        say(kurask, verymad, "Little bugs! Stomp and crush and stomp!")
        when (
            choice3(
                "Yeah! Stomp the bugs!",
                0,
                "What have the bugs done to you?",
                1,
                "You can't, you've got no feet!",
                2,
            )
        ) {
            0 -> {
                chatPlayer(happy, "Yeah! Stomp the bugs!")
                say(kurask, verymad, "Stomp crush splat!")
                say(
                    kurask,
                    verymad,
                    "Smash! Destroy! Crunch break tear destroy splunch! Hurt wound kill hit punch " +
                        "stab slash kill!",
                )
                when (
                    choice3(
                        "'Splunch'? That's not a word!",
                        0,
                        "You said 'kill' twice",
                        1,
                        "Yeah! Kill smash destroy!",
                        2,
                    )
                ) {
                    0 -> {
                        chatPlayer(confused, "'Splunch'? That is not a word!")
                        say(kurask, verymad, "I HATE WORDS! Kill all words!")
                    }
                    1 -> {
                        chatPlayer(neutral, "You said 'kill' twice!")
                        say(kurask, madlaugh, "I like kill! Hur hur hur hur!")
                    }
                    else -> {
                        chatPlayer(happy, "Yeah! Kill smash destroy!")
                        say(kurask, madlaugh, "Kill smash destroy! Hur hur hur!")
                    }
                }
            }
            1 -> {
                chatPlayer(quiz, "What have the bugs done to you?")
                say(kurask, angry, "Skitter skitter through head noise in ears behind eyes.")
                say(kurask, verymad, "HATE THEM! Kill kill kill!")
            }
            else -> {
                chatPlayer(laugh, "You can't, you've got no feet!")
                say(kurask, sad, "No feet...")
                say(kurask, verymad, "Hate lack of feet! Stomp lack of feet! Kill crush destroy smash!")
                chatPlayer(confused, "That makes no sense! You can't destroy the absence of something!")
                say(kurask, verymad, "Hate requirement to make sense! Smash it kill it destroy kill kill!")
                chatPlayer(confused, "You can't physically destroy an abstract concept! It's impossible!")
                say(kurask, verymad, "Hate abstract concepts! Hate impossible! Kill kill kill destroy smash!")
                chatPlayer(bored, "This is getting both surreal and repetitive.")
            }
        }
    }

    private suspend fun Dialogue.kuraskGloat(kurask: Head) {
        chatPlayer(laugh, "I killed you really easily!")
        say(kurask, angry, "Uhhhhrrr...")
        chatPlayer(laugh, "Yeah! I could kill you again in my sleep! I think I might go off and kill some other kurask!")
        say(kurask, verymad, "Uuuurrrrh! Hate you!")
        chatPlayer(laugh, "What are you going to do about it? Eh? I totally owned you!")
        say(kurask, verymad, "Hate you hate you hate you!!!")
    }

    private suspend fun Dialogue.abyssalDemon(demon: Head) {
        say(demon, neutral, "Have you considered visiting <col=800000>THE ABYSS</col>?")
        when (
            choice3(
                "I visit the abyss all the time",
                0,
                "It's too scary for me",
                1,
                "Could I get an abyssal whip?",
                2,
            )
        ) {
            0 -> {
                chatPlayer(happy, "I visit the abyss all the time!")
                say(
                    demon,
                    neutral,
                    "I bet you just rush through it though. Everyone there is in such a rush. No " +
                        "one stops to appreciate the beauty of <col=800000>THE ABYSS</col>.",
                )
                if (
                    choice2(
                        "I have to run through quickly or I'll die",
                        true,
                        "The abyss looks pretty ugly to me",
                        false,
                    )
                ) {
                    chatPlayer(worried, "I have to run through it quickly or I'll die!")
                    say(demon, neutral, "Death is a small thing compared to the beauty of <col=800000>THE ABYSS</col>.")
                } else {
                    chatPlayer(neutral, "The abyss looks pretty ugly to me.")
                    say(demon, sad, "Poor deluded fool. There is no hope for you at all.")
                }
            }
            1 -> {
                chatPlayer(worried, "It's too scary for me!")
                say(
                    demon,
                    neutral,
                    "But does not the fear contribute to your appreciation of " +
                        "<col=800000>THE ABYSS</col>?",
                )
                if (choice2("No, it's just scary", true, "I suppose fear does heighten the senses", false)) {
                    chatPlayer(worried, "No, it's just scary.")
                    say(
                        demon,
                        neutral,
                        "Poor human. You must not judge <col=800000>THE ABYSS</col> by the standards " +
                            "of this world. You must learn to embrace your fear as part of the " +
                            "experience of <col=800000>THE ABYSS</col>.",
                    )
                } else {
                    chatPlayer(neutral, "I suppose fear does heighten the senses.")
                    say(
                        demon,
                        shifty,
                        "Then you should enhance them further by raising the stakes. Next time you " +
                            "go to <col=800000>THE ABYSS</col> you should take all your most " +
                            "valuable items with you.",
                    )
                }
            }
            else -> {
                chatPlayer(quiz, "Could I get an abyssal whip?")
                say(
                    demon,
                    shifty,
                    "You must take all your gold and all your most valued items, and take them " +
                        "into <col=800000>THE ABYSS</col> without weapons or armour.",
                )
                chatPlayer(quiz, "And then will I get an abyssal whip?")
                say(demon, madlaugh, "You'll get an <col=800000>ABYSSAL WHIPPING</col>!")
                chatPlayer(bored, "That pun was abyssmal.")
            }
        }
    }

    private suspend fun Dialogue.kingBlackDragon() {
        val left = Head("King Black Dragon", "npc.poh_mounted_kbd_left")
        val middle = Head("King Black Dragon", "npc.poh_mounted_kbd_middle")
        val right = Head("King Black Dragon", "npc.poh_mounted_kbd_right")
        say(middle, quiz, "What?")
        val powerful =
            choice2(
                "How do you feel about all the more powerful monsters?",
                true,
                "Which of you heads is...",
                false,
            )
        if (powerful) {
            chatPlayer(quiz, "How do you feel about all the more powerful monsters?")
            say(left, angry, "There are no monsters more powerful than us!")
            say(middle, happy, "We're the top monster of all Gielinor!")
            chatPlayer(neutral, "No you're not. The Kalphite Queen is more powerful than you!")
            say(middle, confused, "Kalphite Queen? What's that?")
            chatPlayer(neutral, "She's a giant insect who lives in the desert.")
            say(middle, confused, "An insect?")
            say(right, laugh, "Ha ha ha ha!")
            say(left, angry, "No insect could be tougher than us! We're the best!")
            chatPlayer(neutral, "No, she's way tougher than you!")
            say(left, angry, "I don't believe it!")
            say(
                middle,
                neutral,
                "And even if this Kalphite Queen is real, which I doubt, second best isn't bad, is it?",
            )
            chatPlayer(neutral, "But it's not just the Kalphite Queen. What about the TzTok-Jad?")
            say(left, angry, "Never heard of it!")
            chatPlayer(neutral, "Or the Dagannoth Rex?")
            say(right, angry, "You're making it up!")
            chatPlayer(neutral, "Or the Chaos Elemental?")
            say(
                middle,
                shifty,
                "Now then, how do you know you're not just making all these monsters up to " +
                    "demoralise us?",
            )
            chatPlayer(neutral, "All right then, what about me?")
            say(left, laugh, "Puny human! You're not a fearsome monster!")
            chatPlayer(happy, "I defeated you, didn't I? So I must be stronger than you!")
            say(left, angry, "You got lucky! I'll get you next time!")
            chatPlayer(laugh, "Now that you're a stuffed head? I don't think so!")
            return
        }
        chatPlayer(quiz, "Which of you heads is...")
        say(left, happy, "I am!")
        say(right, angry, "Shut up! I am!")
        say(middle, angry, "Don't be silly! It's obvious that I am!")
        chatPlayer(confused, "But you don't even know what I was going to say!")
        say(
            middle,
            happy,
            "It doesn't matter. I'm the strongest, cleverest, and most attractive. Whatever it " +
                "is, I am the most of it!",
        )
        say(left, worried, "Just a minute. What if it's something bad?")
        say(
            middle,
            neutral,
            "Good point. What is it you were going to say? Because if it's something good, I'm " +
                "it, but if it's something bad then it's one of these two ugly mugs.",
        )
        chatPlayer(confused, "I've forgotten what I was going to ask now.")
        say(right, happy, "Me! I am!")
        say(middle, confused, "What?")
        say(right, sad, "Sorry, just said that on reflex.")
    }

    private suspend fun Dialogue.kalphiteQueen(queen: Head) {
        say(queen, angry, "Soft-thing! How dare you approach the queen of the kalphite?")
        chatPlayer(neutral, "I killed you, remember?")
        say(
            queen,
            angry,
            "Yes, you killed this queen, but by now another will have risen up! One kalphite may " +
                "die but the hive goes on!",
        )
        say(
            queen,
            angry,
            "The kalphite race grows stronger every day, and our young feed on the blood of the " +
                "soft-things that invade from above!",
        )
        say(
            queen,
            angry,
            "Someday we will overrun the world again and all soft creatures will die. But we will " +
                "reserve the worst fate for those who have killed a queen and hung her head in " +
                "their house.",
        )
        say(
            queen,
            verymad,
            "We will lay our eggs in your brain and you will not die until it explodes and a " +
                "million kalphite emerge to make a grand hive of all the world!",
        )
        if (choice2("You don't scare me!", true, "Please don't kill me!", false)) {
            chatPlayer(angry, "You don't scare me!")
            say(queen, angry, "Your pitiful misplaced confidence is irrelevant. You will all die!")
        } else {
            chatPlayer(worried, "Please don't kill me!")
            say(queen, madlaugh, "Ha ha ha! It is too late for pleading now, pathetic mammal! Your fate is sealed!")
        }
    }

    private suspend fun Dialogue.vorkath(dragon: Head) {
        chatPlayer(happy, "A defeated dragon is a good dragon.")
        say(dragon, angry, "With the power of my 3 legs, I will get my revenge.")
        chatPlayer(neutral, "Sorry to tell you this... but you don't even have 3 legs now, you're a head.")
        say(dragon, angry, "Who do I have to blame for this?")
        chatPlayer(worried, "Erm... about that.")
        mesbox("Now seems like a good time to run...")
    }

    private suspend fun Dialogue.hydra(hydra: Head) {
        chatPlayer(quiz, "You look a bit put out there, what's wrong?")
        say(hydra, angry, "I will crush you with the power of all five heads!")
        chatPlayer(laugh, "Well three seem to be missing now!")
        say(hydra, shocked, "What? HOW?")
        chatPlayer(shifty, "Well it might have been me.")
    }
}
