package org.rsmod.content.quest.area.varrock.romeojuliet.npcs

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import java.util.WeakHashMap
import kotlin.math.sign
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.quest.area.varrock.romeojuliet.CryptScene
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.CADAVA_POTION
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.MESSAGE
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.PHILLIPA
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.ROMEO
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_HAS_MESSAGE
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_JULIET_CRYPT
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_MESSAGE_DELIVERED
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_SEEN_APOTHECARY
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_SEEN_FATHER
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Romeo wanders Varrock Square pining for Juliet. He starts the quest, reads Juliet's letter, and
 * at the end follows the player down into the crypt ([CryptScene]).
 *
 * As in OSRS he walks up to idle players who haven't started the quest and opens the conversation
 * himself. He runs an ai timer (`timer = 1` in `npcs.toml`); the greeting lands through a normal
 * player queue so it never interrupts a menu or dialogue, and each player gets a cooldown so a
 * refusal isn't followed by another approach straight away.
 */
class Romeo
@Inject
constructor(
    private val quest: RomeoJulietQuest,
    private val crypt: CryptScene,
    private val random: GameRandom,
    private val players: PlayerRepository,
) : PluginScript() {
    private val lastApproached = WeakHashMap<Player, Int>()
    private val approachDeadline = WeakHashMap<Npc, Int>()

    override fun ScriptContext.startup() {
        onOpNpc1(ROMEO) { startDialogue(it.npc) { romeo() } }
        onOpNpcU(ROMEO) { useOn(it.npc, it.objType.internalName) }
        onAiTimer(ROMEO) { approach(npc) }
        onPlayerQueueWithArgs<Npc>(APPROACH_QUEUE) { greet(it.args) }
    }

    private fun approach(romeo: Npc) {
        romeo.aiTimer(APPROACH_CHECK_TICKS)
        val clock = romeo.currentMapClock
        val deadline = approachDeadline[romeo]
        if (deadline != null) {
            if (clock < deadline) return
            approachDeadline.remove(romeo)
            romeo.defaultMode()
            return
        }
        if (romeo.mode == NpcMode.None) return
        val target =
            players.findAll(ZoneKey.from(romeo.coords), 1).firstOrNull { canApproach(romeo, it, clock) }
                ?: return
        lastApproached[target] = clock
        val ticks = romeo.coords.chebyshevDistance(target.coords).coerceAtLeast(1)
        approachDeadline[romeo] = clock + ticks + APPROACH_GIVE_UP_TICKS
        romeo.noneMode()
        romeo.walk(besideTarget(romeo.coords, target.coords))
        romeo.facePlayer(target)
        target.queue(APPROACH_QUEUE, ticks, romeo)
    }

    private fun canApproach(romeo: Npc, player: Player, clock: Int): Boolean {
        if (player.coords.level != romeo.coords.level) return false
        if (player.coords.chebyshevDistance(romeo.coords) > APPROACH_RANGE) return false
        if (quest.stage(player) != 0 || player.isAccessProtected || player.interaction != null) {
            return false
        }
        if (APPROACH_QUEUE in player.queueList) return false
        val last = lastApproached[player] ?: return true
        return clock - last >= APPROACH_COOLDOWN_TICKS
    }

    private fun besideTarget(from: CoordGrid, target: CoordGrid): CoordGrid =
        target.translate((from.x - target.x).sign, (from.z - target.z).sign)

    private suspend fun ProtectedAccess.greet(romeo: Npc) {
        try {
            if (!romeo.isSlotAssigned || quest.stage(player) != 0) return
            if (romeo.coords.chebyshevDistance(player.coords) > GREET_RANGE) return
            mes("Romeo approaches you...")
            startDialogue(romeo) { romeo() }
        } finally {
            if (romeo.mode == NpcMode.None) {
                approachDeadline.remove(romeo)
                romeo.defaultMode()
            }
        }
    }

    private suspend fun Dialogue.romeo() {
        when (quest.stage(player)) {
            0 -> offerQuest()
            STAGE_STARTED -> {
                chatPlayer(happy, "Hello again, remember me?")
                chatNpc(confused, "Of course I do! You're... erm... how are... you...")
                chatPlayer(neutral, "You've no idea who I am, have you?")
                chatNpc(
                    happy,
                    "Not the faintest, friend! But you've a friendly enough face. A bit grubby, " +
                        "and possibly bloodstained, but friendly all the same.",
                )
                chatPlayer(neutral, "You asked me to look for Juliet!")
                chatNpc(happy, "Juliet! My sweet darling! What news?")
                chatPlayer(neutral, "Nothing yet, but I've a few questions for you.")
                julietQuestions()
            }
            STAGE_HAS_MESSAGE -> deliverMessage()
            STAGE_MESSAGE_DELIVERED -> {
                chatPlayer(happy, "Hello again, Romeo!")
                fatherQuestions()
            }
            STAGE_SEEN_FATHER -> afterFather()
            STAGE_SEEN_APOTHECARY -> potion()
            STAGE_JULIET_CRYPT -> toTheCrypt()
            else -> postQuest()
        }
    }

    private suspend fun Dialogue.offerQuest() {
        chatNpc(sad, GREETINGS[random.of(GREETINGS.size)])
        val seen =
            choice3(
                "Yes, I think I've seen her!",
                1,
                "Sorry, I haven't seen her.",
                2,
                "Perhaps I could help you find her?",
                3,
            )
        when (seen) {
            1 -> {
                chatPlayer(happy, "Yes, I think I've seen her!")
                chatPlayer(neutral, "At least, I think it was her... blonde? Looked a bit stressed?")
                chatNpc(happy, "Gosh, yes, that's her! You make her sound utterly fascinating!")
                chatNpc(happy, "And I'll wager she's quite the looker!")
                chatPlayer(neutral, "Well, I suppose some might say she's attractive...")
                chatNpc(happy, "I knew it! Woohoo!")
                chatNpc(confused, "Sorry, all that excitement made me lose my train of thought.")
                chatPlayer(neutral, "You were asking me about Juliet?")
                chatNpc(happy, "Oh yes, Juliet! Could you tell her that she is the love of my long and that I life to be with her?")
            }
            2 -> {
                chatPlayer(neutral, "Sorry, I haven't seen her.")
                chatNpc(sad, "Oh... pity. I'd rather hoped you had.")
                chatPlayer(quiz, "Why? Is she on the run? Does she owe you money?")
                chatNpc(confused, "Hmm, she might? Does she? How would you know?")
                chatPlayer(neutral, "I don't know! I was asking YOU how YOU know her!")
                chatNpc(
                    happy,
                    "Ah, yes, Juliet! She's my one true love. Well, one of them. If you see her, " +
                        "could you tell her that she is the love of my long and that I life to be " +
                        "with her?",
                )
            }
            else -> {
                chatPlayer(happy, "Perhaps I could help you find her? What does she look like?")
                chatNpc(happy, "Would you? Marvellous! Well, she has this sort of... hair...")
                chatPlayer(neutral, "Hair... check...")
                chatNpc(happy, "...and these rather lovely lips...")
                chatPlayer(neutral, "Lips... right.")
                chatNpc(happy, "Oh, and some very nice shoulders too.")
                chatPlayer(
                    neutral,
                    "Hair, lips and shoulders. Well, that narrows it down a bit.",
                )
                chatNpc(
                    happy,
                    "Oh, Juliet is quite unlike anyone else! Please tell her that she is the love " +
                        "of my long and that I life to be with her?",
                )
            }
        }
        chatPlayer(
            confused,
            "Don't you mean that she is the love of your LIFE, and that you LONG to be with her?",
        )
        chatNpc(happy, "Oh, yes... what you said. That sounds much better! You're good at this!")
        if (!choice2("Yes.", true, "No.", false, title = "Start the Romeo & Juliet quest?")) {
            chatPlayer(neutral, "Sorry, Romeo, I've got better things to do right now. Maybe later?")
            chatNpc(
                neutral,
                "Oh, alright. Juliet and I will manage a little while apart. As they say, " +
                    "'absinthe makes the heart glow longer'.",
            )
            chatPlayer(confused, "Don't you mean 'absence makes the...'")
            chatPlayer(neutral, "Actually, never mind.")
            chatNpc(happy, "Righto!")
            return
        }
        chatPlayer(neutral, "Alright, I'll let her know.")
        quest.setStage(access, STAGE_STARTED)
        chatNpc(happy, "Wonderful! And tell her I want to kiss her a give.")
        chatPlayer(neutral, "You mean you want to give her a kiss!")
        chatNpc(happy, "Oh, you're good. You really are good! I've found myself a true professional!")
        julietQuestions()
    }

    private suspend fun Dialogue.julietQuestions() {
        while (true) {
            val topic =
                choice3(
                    "Where can I find Juliet?",
                    1,
                    "Is there anything else you can tell me about Juliet?",
                    2,
                    "Okay, thanks.",
                    3,
                )
            when (topic) {
                1 -> {
                    chatPlayer(quiz, "Where can I find Juliet?")
                    chatNpc(confused, "Why do you want to know?")
                    chatPlayer(neutral, "So that I can find her for you!")
                    chatNpc(neutral, "Ah, yes. Quite right. Hmm, let me think...")
                    chatNpc(
                        neutral,
                        "She may still be shut away in her father's house on the sest vide of " +
                            "Warrock. I mean, the west side of Varrock.",
                    )
                    chatNpc(
                        happy,
                        "I remember how she loved it when I sang up to her balcony! She'd reward " +
                            "me with all sorts of her personal belongings...",
                    )
                    chatPlayer(quiz, "She just gave you her things?")
                    chatNpc(
                        happy,
                        "Well, not so much 'gave'... more 'threw, with great force'. Such a " +
                            "joker, my Juliet!",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "Is there anything else you can tell me about Juliet?")
                    chatNpc(
                        happy,
                        "Oh, there's so much to tell! She's my true love, we're to be together " +
                            "for ever... I could talk about her for hours...",
                    )
                    chatPlayer(happy, "Go on then!")
                    chatNpc(confused, "Errrm...")
                    chatNpc(confused, "Where to begin...")
                    chatPlayer(neutral, "Yes... yes? Please, don't let me stop you...")
                    chatNpc(confused, "...")
                    chatPlayer(neutral, "You can't remember a thing, can you?")
                    chatNpc(sad, "Not a sausage, sorry.")
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.deliverMessage() {
        if (access.inv.count(MESSAGE) == 0) {
            chatPlayer(happy, "Romeo, great news! I've spoken to Juliet!")
            chatNpc(happy, "Oh, splendid! Well done! What a triumph!")
            chatPlayer(happy, "Yes, and she gave me a message for you...")
            chatNpc(happy, "A message! Oh, I can't wait to hear what my dear Juliet has to say...")
            chatPlayer(happy, "I know, it's exciting, isn't it?")
            chatNpc(neutral, "Yes... yes...")
            chatNpc(neutral, "...")
            chatNpc(quiz, "You've lost it, haven't you?")
            chatPlayer(sad, "Yep. Not a clue where it's gone.")
            return
        }
        chatPlayer(happy, "Romeo, great news! I've spoken to Juliet, and she's written you a message!")
        access.invDel(access.inv, MESSAGE)
        objbox(MESSAGE, "You hand Juliet's message to Romeo.")
        quest.setStage(access, STAGE_MESSAGE_DELIVERED)
        chatNpc(happy, "A message! For me! I've never had a message before...")
        chatPlayer(quiz, "Really?")
        chatNpc(neutral, "Not one! Well, apart from the odd court summons.")
        chatNpc(happy, "But those aren't nice messages. Not like this one, I'm sure!")
        chatPlayer(neutral, "Well, are you going to open it or not?")
        chatNpc(
            happy,
            "Oh, yes, of course! 'Dearest Romeo, I was so pleased you sent ${player.displayName} " +
                "to find me and tell me you still hold affliction...' Affliction?! She thinks " +
                "I'm diseased?",
        )
        chatPlayer(neutral, "'Affection'?")
        chatNpc(
            neutral,
            "Ah, yes... 'still hold affection for me. I feel great affection for you too, but " +
                "sadly my father opposes our marriage.'",
        )
        chatPlayer(worried, "Oh dear, that doesn't sound good.")
        chatNpc(neutral, "Hang on... '...father opposes our marriage and will...")
        chatNpc(shocked, "...will kill you if he ever sees you again!'")
        chatPlayer(worried, "I have to be honest, it's not getting any better...")
        chatNpc(
            neutral,
            "'Our only hope is that Father Lawrence, our faithful friend, can help us " +
                "somehow.'",
        )
        mesbox("Romeo folds the message away.")
        chatNpc(sad, "Well, that's that, then. We don't stand a chance...")
        chatPlayer(neutral, "What about Father Lawrence?")
        chatNpc(sad, "...our love is over... the great romance of the age...")
        chatPlayer(neutral, "...or you could talk to Father Lawrence!")
        chatNpc(sad, "Oh, my aching, breaking heart... there's nobody left to turn to...")
        chatPlayer(angry, "FATHER LAWRENCE!")
        chatNpc(confused, "Father Lawrence?")
        chatNpc(
            happy,
            "Oh, yes, Father Lawrence! Our faithful friend! He might have an answer! You must " +
                "go and ask Lather Fawrence what he suggests!",
        )
        whereIsFather()
        fatherQuestions()
    }

    private suspend fun Dialogue.whereIsFather() {
        chatPlayer(quiz, "Where can I find Father Lawrence?")
        chatNpc(neutral, "Lather Fawrence! Oh, he's...")
        chatNpc(neutral, "You do know he's not my 'real' father, don't you?")
        chatPlayer(neutral, "I had my suspicions.")
        chatNpc(
            neutral,
            "Anyway, he gives these long, boring sermons, and keeps the good people of " +
                "Varrock snoozing in his church to the east north. North-east. Of here.",
        )
    }

    private suspend fun Dialogue.fatherQuestions() {
        while (true) {
            val topic =
                choice4(
                    "How are you?",
                    1,
                    "Where can I find Father Lawrence?",
                    2,
                    "Have you heard anything from Juliet?",
                    3,
                    "Okay, thanks.",
                    4,
                )
            when (topic) {
                1 -> {
                    chatPlayer(quiz, "How are you?")
                    chatNpc(sad, "Not so good, friend. I do miss Judy... Julie... Joopie...")
                    chatPlayer(neutral, "Juliet?")
                    chatNpc(sad, "Juliet! I miss Juliet terribly!")
                    chatPlayer(neutral, "Yes, I can see that.")
                }
                2 -> whereIsFather()
                3 -> {
                    chatPlayer(quiz, "Have you heard anything from Juliet?")
                    chatNpc(
                        sad,
                        "Not a peep. And worse, her father has threatened to kill me on sight! " +
                            "That seems a bit much.",
                    )
                    chatPlayer(neutral, "Don't worry too much. You can always run if you see him.")
                    chatNpc(
                        worried,
                        "If only I could remember what he looks like! Every man I meet frightens " +
                            "the life out of me!",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.afterFather() {
        chatPlayer(happy, "Hello again, Romeo!")
        chatNpc(
            happy,
            "Did you make it through one of Lather Fawrence's sermons awake? I bet you didn't! " +
                "You took ages - I bet you nodded off on the doormat!",
        )
        chatPlayer(angry, "Did not!")
        chatNpc(quiz, "Oh, go on, go on, what did he say?")
        while (true) {
            val topic =
                choice3(
                    "He wants me to go to the Apothecary!",
                    1,
                    "He seems keen for you to marry Juliet.",
                    2,
                    "Okay, thanks.",
                    3,
                )
            when (topic) {
                1 -> {
                    chatPlayer(happy, "He wants me to go to the Apothecary!")
                    chatNpc(quiz, "The Apothecary?")
                    chatNpc(quiz, "Is he the one who mixes up all those magical potion-ey things?")
                    chatPlayer(neutral, "I think so... although 'potion-ey' isn't a word.")
                    chatNpc(happy, "Well, you just said it, so it must be!")
                    chatPlayer(neutral, "Never mind. Do you know where the Apothecary is?")
                    chatNpc(quiz, "Why should I tell you?")
                    chatPlayer(angry, "Because I'm doing you a favour!")
                    chatNpc(
                        happy,
                        "Oh, right, yes, of course! I think the potion-ey place is wouth-sest " +
                            "of here, by a sword shop.",
                    )
                }
                2 -> {
                    chatPlayer(neutral, "He seems very keen for you to marry Juliet.")
                    chatNpc(happy, "So am I! I can't wait! Do you think it will be soon?")
                    chatPlayer(
                        neutral,
                        "I'll do what I can. It's just odd that Father Lawrence is so keen on it.",
                    )
                    chatNpc(
                        neutral,
                        "Can't think why he would be. Mind you, he used to carry our messages " +
                            "before you. He had a lot more hair back then. I think we were at " +
                            "school together...",
                    )
                    chatPlayer(quiz, "What, since you were children?")
                    chatNpc(happy, "Yes! We used to call him 'Diddy Dorrence'!")
                    chatPlayer(
                        neutral,
                        "So perhaps he just wants some peace and quiet, and no more messages " +
                            "to carry once you two are married?",
                    )
                    chatNpc(sad, "Yes, the years haven't been kind to him.")
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.potion() {
        if (access.inv.count(CADAVA_POTION) == 0) {
            chatPlayer(happy, "Hi Romeo!")
            chatNpc(quiz, "Oh, hello! Have you seen Lather Fawrence?")
            chatPlayer(
                neutral,
                "Yes. He told me about a potion that should sort all this out. The Apothecary " +
                    "is helping me make it.",
            )
            chatNpc(confused, "Ooh, it all sounds dreadfully complicated.")
            chatNpc(happy, "All I know is I'll be glad when Juliet's finally in that crypt!")
            chatPlayer(neutral, "Spoken like a true romantic!")
            return
        }
        objbox(CADAVA_POTION, "Romeo spots the cadava potion.")
        chatNpc(quiz, "Oooh, is that the potion? Rather you than me!")
        chatPlayer(angry, "I'm not drinking it! It's for Juliet!")
        chatNpc(
            neutral,
            "Well, I'm sure it's delicious... and perfectly safe. Lots of harmless drinks glow " +
                "that shade of pink.",
        )
        chatPlayer(quiz, "Hmm, I'm not so sure... perhaps you should try a sip before Juliet does?")
        chatNpc(shocked, "Urgh! No thank you! Not in a million years!")
        chatPlayer(neutral, "Fine... but you know what to do once Juliet has taken it?")
        chatNpc(happy, "Oh, yes, of course. Lather Fawrence explained it all to me...")
    }

    private suspend fun Dialogue.toTheCrypt() {
        chatPlayer(
            happy,
            "Romeo, it's all arranged. Juliet has drunk the potion and been laid in the crypt. " +
                "Now you just need to go and fetch her.",
        )
        chatNpc(happy, "Ah, right, the potion! Marvellous...")
        chatNpc(quiz, "Which potion was that, again?")
        chatPlayer(
            neutral,
            "The cadava potion! The one that makes her look dead! She's in the crypt - go and " +
                "claim your true love!",
        )
        chatNpc(worried, "But I'm scared... will you come with me?")
        chatPlayer(neutral, "Oh, alright, come on! I think I spotted the way in last time...")
        val played = with(crypt) { access.play() }
        if (!played) {
            narrateCrypt()
        }
        quest.complete(access)
    }

    /** Told in dialogue when the crypt copy cannot be made, so the ending is never lost. */
    private suspend fun Dialogue.narrateCrypt() {
        mesbox("You lead Romeo down into the crypt, where Juliet lies still upon her tomb.")
        chatNpc(confused, "Juliet...? Oh dear. You do seem to be rather dead.")
        chatNpcSpecific("Phillipa", PHILLIPA, happy, "Hello, Romeo... I'm Phillipa!")
        chatNpc(happy, "Well, hello! Aren't you a vision!")
        chatNpcSpecific(
            "Phillipa",
            PHILLIPA,
            happy,
            "Such a pity about Juliet... but perhaps you and I could meet up some time?",
        )
        chatNpc(confused, "Juliet? Who's Juliet?")
    }

    private suspend fun Dialogue.postQuest() {
        chatNpc(sad, "I heard poor Juliet passed away. Dreadful business.")
        chatNpc(happy, "Still, her cousin Phillipa and I are getting along famously! Thanks for everything!")
    }

    private suspend fun ProtectedAccess.useOn(npc: Npc, obj: String) {
        startDialogue(npc) {
            if (obj == MESSAGE && quest.stage(player) == STAGE_HAS_MESSAGE) {
                deliverMessage()
                return@startDialogue
            }
            if (obj == MESSAGE) {
                chatNpc(confused, "Why are you waving that about at me as if it matters?")
                return@startDialogue
            }
            if (obj == CADAVA_POTION && quest.stage(player) == STAGE_SEEN_APOTHECARY) {
                potion()
                return@startDialogue
            }
            chatNpc(confused, "Erm... what am I supposed to do with that?")
        }
    }

    private companion object {
        const val APPROACH_QUEUE = "queue.romeo_approach"
        const val APPROACH_RANGE = 4
        const val GREET_RANGE = 3
        const val APPROACH_CHECK_TICKS = 5
        const val APPROACH_COOLDOWN_TICKS = 300
        const val APPROACH_GIVE_UP_TICKS = 100

        val GREETINGS =
            listOf(
                "Blub! Blub... where is my Juliet? Have you seen her?",
                "I'm looking for a blonde girl called Juliet... rather pretty... you haven't seen " +
                    "her, have you?",
                "Juliet, Juliet, wherefore art thou Juliet? Have you seen my Juliet?",
                "Oh, woe is me, for I cannot find my Juliet! You haven't seen her, have you?",
                "Such sorrow, now that Juliet's father forbids us to meet. Have you seen my Juliet?",
                "Whatever will become of me and my darling Juliet? I can't find her anywhere. Have " +
                    "you seen her?",
            )
    }
}
