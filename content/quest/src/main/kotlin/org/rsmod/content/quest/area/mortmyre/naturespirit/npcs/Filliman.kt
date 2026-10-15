package org.rsmod.content.quest.area.mortmyre.naturespirit.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.mortmyre.naturespirit.MortMyreCoords
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.DRUIDIC_SPELL
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.FILLIMAN
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.FUNGUS
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.JOURNAL
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.MIRROR
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_ENTERED_SWAMP
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_GOT_SPELL
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_HEARD_SPIRIT
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_PUZZLE_SOLVED
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_RETURNED_JOURNAL
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_SHOWN_MIRROR
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_SPOKE_TO_SPIRIT
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_TRANSFORMED
import org.rsmod.content.quest.area.mortmyre.naturespirit.wearsGhostspeak
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The spirit of Filliman Tarlock, who appears outside his grotto. Without a ghostspeak amulet he
 * only moans; with one he refuses to believe he is dead until he sees his reflection, then needs
 * his journal, gives the player a druidic spell, and at last performs his transformation ritual
 * once the three stones around him are complete.
 */
@Singleton
class Filliman
@Inject
constructor(
    private val natureSpirit: NatureSpiritQuest,
    private val objRepo: ObjRepository,
    private val world: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(FILLIMAN) { startDialogue(it.npc) { talk(it.npc) } }
        onOpNpcU(FILLIMAN) { useOnFilliman(it.npc, it.objType.internalName) }
    }

    suspend fun Dialogue.talk(spirit: Npc) {
        val stage = natureSpirit.stage(player)
        if (stage >= STAGE_TRANSFORMED) {
            access.mes("This spirit seems too ethereal to communicate with.")
            return
        }
        if (!player.wearsGhostspeak()) {
            if (stage < STAGE_HEARD_SPIRIT) {
                mumbling()
            } else {
                moan()
            }
            return
        }
        when {
            stage < STAGE_SPOKE_TO_SPIRIT -> firstWords()
            stage == STAGE_SPOKE_TO_SPIRIT -> stillAlive()
            stage == STAGE_SHOWN_MIRROR -> lostJournal()
            stage == STAGE_RETURNED_JOURNAL -> {
                chatNpc(
                    neutral,
                    "Thanks for the journal, I've been reading it. It looks like I came to a violent " +
                        "and bitter end but that's not really important. I just have to figure out " +
                        "what I am going to do now?",
                )
                plans()
            }
            stage == STAGE_GOT_SPELL -> {
                chatNpc(quiz, "Hello there, have you been blessed yet?")
                chatPlayer(neutral, "No, not yet.")
                chatNpc(angry, "Well, hurry up!")
            }
            stage < STAGE_PUZZLE_SOLVED -> ritual(spirit)
            else -> chatNpc(happy, "Please come down into the grotto, we have much to discuss.")
        }
    }

    private suspend fun Dialogue.moan() {
        chatNpc(sad, "Ahhrs Oooohh arhhhhAHhhh.")
    }

    private suspend fun Dialogue.mumbling() {
        chatNpc(confused, "Cannot wake up... Where am I?")
        chatPlayer(confused, "Huh? What's this?")
        chatNpc(confused, "What did I write down now? Put it in the knot hole.")
        natureSpirit.advanceTo(access, STAGE_HEARD_SPIRIT)
        moan()
        chatPlayer(angry, "Huh! Now you're just not making any sense at all! I just cannot understand you!")
    }

    private suspend fun Dialogue.firstWords() {
        chatPlayer(quiz, "Hello?")
        natureSpirit.advanceTo(access, STAGE_SPOKE_TO_SPIRIT)
        chatNpc(happy, "Oh, I understand you! At last, someone who doesn't just mumble. I understand what you're saying!")
        denials()
    }

    private suspend fun Dialogue.stillAlive() {
        chatPlayer(happy, "Hello again!")
        chatNpc(
            neutral,
            "Oh, hello there, do you still think I'm dead? It's hard to see how I could be dead when " +
                "I'm still in the world. I can see everything quite clearly. And nothing of what you " +
                "say reflects the truth.",
        )
        chatPlayer(neutral, "Yes, I do think you're dead and I'll prove it somehow.")
        denials()
    }

    private suspend fun Dialogue.denials() {
        while (true) {
            when (
                choice4(
                    "I'm wearing an amulet of ghost speak!",
                    1,
                    "How long have you been a ghost?",
                    2,
                    "What's it like being a ghost?",
                    3,
                    "Ok, thanks.",
                    4,
                )
            ) {
                1 -> {
                    chatPlayer(happy, "I'm wearing an amulet of ghost speak!")
                    chatNpc(sad, "Why you poor fellow, have you passed away and you want to send a message back to a loved one?")
                    chatPlayer(confused, "Err.. Not exactly...")
                    chatNpc(
                        sad,
                        "You have come to haunt my dreams until I pass on your message to a dearly " +
                            "loved one. I understand. Pray, tell me who would you like me to pass a " +
                            "message on to?",
                    )
                    chatPlayer(worried, "Ermm, you don't understand... It's just that..")
                    chatNpc(quiz, "Yes!")
                    chatPlayer(worried, "Well, please don't be upset or anything... But you're the ghost!")
                    chatNpc(laugh, "Don't be silly now! That in no way reflects the truth!")
                }
                2 -> {
                    chatPlayer(quiz, "How long have you been a ghost?")
                    chatNpc(angry, "What?! Don't be preposterous! I'm not a ghost! How could you say something like that?")
                    chatPlayer(
                        neutral,
                        "But it's true, you're a ghost... well, at least that is to say, you're sort " +
                            "of not alive anymore.",
                    )
                    chatNpc(
                        neutral,
                        "Don't be silly, I can see you, I can see that tree. If I were dead, I " +
                            "wouldn't be able to see anything.. What you say just doesn't reflect the " +
                            "truth. You'll have to try harder to pull one over on me!",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "What's it like being a ghost?")
                    chatNpc(shifty, "Oh, it's quite.... Oh... Trying to catch me out were you! Anyone can clearly see that I am not a ghost!")
                    chatPlayer(
                        neutral,
                        "But you are a ghost, look at yourself! I can see straight through you! " +
                            "You're as dead as this swamp! Err... No offence or anything...",
                    )
                    chatNpc(
                        neutral,
                        "No I won't take offence because I'm not dead and I'm afraid you'll have to " +
                            "come up with some pretty conclusive proof before I believe it. What a " +
                            "strange dream this is.",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Ok thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.lostJournal() {
        chatPlayer(neutral, "Hello again..")
        chatNpc(
            worried,
            "Oh, hello... Sorry, you've caught me at a bad time, it's just that I've had a sign you " +
                "see and I need to find my journal.",
        )
        chatPlayer(quiz, "Where did you put it?")
        chatNpc(
            confused,
            "Well, if I knew that, I wouldn't still be looking for it. However, I do remember " +
                "something about a knot? Perhaps I was meant to tie a knot or something?",
        )
    }

    private suspend fun ProtectedAccess.useOnFilliman(spirit: Npc, obj: String) {
        arriveDelay()
        faceEntitySquare(spirit)
        val stage = natureSpirit.stage(player)
        when (obj) {
            MIRROR -> startDialogue(spirit) { mirror(stage) }
            JOURNAL -> startDialogue(spirit) { journal(stage) }
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun Dialogue.mirror(stage: Int) {
        if (!player.wearsGhostspeak()) {
            moan()
            return
        }
        if (stage > STAGE_SPOKE_TO_SPIRIT) {
            chatNpc(laugh, "Yes, I know, I'm already dead! Thanks for reminding me.")
            return
        }
        if (stage < STAGE_ENTERED_SWAMP) {
            return
        }
        objbox(MIRROR, "You use the mirror on the spirit of the dead Filliman Tarlock.")
        chatPlayer(happy, "Here take a look at this, perhaps you can see that you're utterly transparent now!")
        access.invDel(access.inv, MIRROR)
        objbox(MIRROR, "The spirit of Filliman reaches forwards and takes the mirror.")
        chatNpc(
            confused,
            "Well, that is the most peculiar thing I've ever experienced. This mirror must somehow be " +
                "dysfunctional. Strange how well it reflects the stagnant swamp behind me, but there " +
                "is nothing of my own visage apparent.",
        )
        chatPlayer(
            laugh,
            "That's because you're dead! Dead as a door nail.. Deader in fact... You bear a " +
                "remarkable resemblance to worm bait! Err.. No offence...",
        )
        natureSpirit.advanceTo(access, STAGE_SHOWN_MIRROR)
        chatNpc(
            sad,
            "I think you might be right my friend, though I still feel very much alive. It is " +
                "strange how I still come to be here and yet I've not turned into a Ghast.",
        )
        chatNpc(neutral, "It must be a sign... Yes a sign... I must try to find out what it means. Now, where did I put my journal?")
    }

    private suspend fun Dialogue.journal(stage: Int) {
        if (!player.wearsGhostspeak()) {
            moan()
            return
        }
        if (stage < STAGE_SHOWN_MIRROR) {
            chatNpc(neutral, "Oh, keep hold of that, I may need it later.")
            return
        }
        if (stage > STAGE_SHOWN_MIRROR) {
            access.mes("Nothing interesting happens.")
            return
        }
        access.invDel(access.inv, JOURNAL)
        objbox(JOURNAL, "You give the journal to Filliman Tarlock.")
        chatPlayer(happy, "Here, I found this, maybe you can use it?")
        chatNpc(happy, "My journal! That should help to collect my thoughts.")
        natureSpirit.advanceTo(access, STAGE_RETURNED_JOURNAL)
        objbox(
            JOURNAL,
            "~ The spirit starts leafing through the journal. ~<br>~ He seems quite distant as he " +
                "regards the pages. ~<br>~ After some time the druid faces you again. ~",
        )
        chatNpc(
            neutral,
            "It's all coming back to me now. It looks like I came to a violent and bitter end but " +
                "that's not important now. I just have to figure out what I am going to do now?",
        )
        plans()
    }

    private suspend fun Dialogue.plans() {
        while (true) {
            when (
                choice5(
                    "Being dead, what options do you think you have?",
                    1,
                    "So, what's your plan?",
                    2,
                    "Well, good luck with that.",
                    3,
                    "How can I help?",
                    4,
                    "Ok thanks.",
                    5,
                )
            ) {
                1 -> {
                    chatPlayer(
                        quiz,
                        "Being dead, what options do you think you have? I'm not trying to be rude or " +
                            "something, but it's not like you have many options is it? I mean, it's " +
                            "either up or down for you isn't it?",
                    )
                    chatNpc(
                        laugh,
                        "Hmm, well you're a poetic one aren't you. Your material world logic stands " +
                            "you in good stead... If you're standing in the material world...",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "So, what's your plan?")
                    chatNpc(
                        neutral,
                        "In my former incarnation I was Filliman Tarlock, a great druid of some " +
                            "power. I spent many years in this place, which was once a forest and I " +
                            "would wish to protect it as a nature sprit.",
                    )
                }
                3 -> {
                    chatPlayer(neutral, "Well, good luck with that.")
                    chatNpc(worried, "Won't you help me to become a nature spirit? I could really use your help!")
                }
                4 -> {
                    howToHelp()
                    return
                }
                else -> {
                    chatPlayer(neutral, "Ok thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.howToHelp() {
        chatPlayer(quiz, "How can I help?")
        chatNpc(
            happy,
            "Will you help me to become a nature spirit? The directions for becoming one are a bit " +
                "vague, I need three things but I know how to get one of them. Perhaps you can help " +
                "collect the rest?",
        )
        chatPlayer(quiz, "I might be interested, what's involved?")
        chatNpc(
            neutral,
            "Well, the book says, that I need, and I quote:- 'Something with faith', 'something " +
                "from nature' and 'something of the 'spirit-to-become' freely given'. Hmm, I know " +
                "how to get something from nature.",
        )
        chatPlayer(confused, "Well, that does seem a bit vague.")
        chatNpc(
            neutral,
            "Hmm, it does and I could understand if you didn't want to help. However, if you could " +
                "perhaps at least get the item from nature, that would be a start. Perhaps we can " +
                "figure out the rest as we go along.",
        )
        access.invAddOrDrop(objRepo, DRUIDIC_SPELL)
        natureSpirit.advanceTo(access, STAGE_GOT_SPELL)
        objbox(DRUIDIC_SPELL, "The druid produces a small sheet of papyrus with some writing on it.")
        chatNpc(
            neutral,
            "This spell needs to be cast in the swamp after you have been blessed. I'm afraid " +
                "you'll need to go to the temple to the North and ask a member of the clergy to " +
                "bless you.",
        )
        chatPlayer(quiz, "Blessed, what does that do?")
        chatNpc(
            neutral,
            "It is required if you're to cast this druid spell. Once you've cast the spell, you " +
                "should find something from nature. Bring it back to me and then we'll try to " +
                "figure out the other things we need.",
        )
    }

    private suspend fun Dialogue.ritual(spirit: Npc) {
        if (!natureSpirit.fungusShown.get(player)) {
            if (FUNGUS !in player.inv && !natureSpirit.fungusPlaced.get(player)) {
                chatPlayer(neutral, "Hello, I've been blessed but I don't know what to do now.")
                chatNpc(
                    neutral,
                    "Well, you need to bring 'something from nature', 'something with faith' and " +
                        "'something of the spirit-to-become freely given.'",
                )
                chatPlayer(confused, "Yeah, but what does that mean?")
                chatNpc(
                    neutral,
                    "Hmm, it is a conundrum, however, if you use that Bloom spell I gave you, you " +
                        "should be able to get something from nature. Once you have that, we may be " +
                        "able to puzzle the rest out.",
                )
                return
            }
            chatNpc(quiz, "Did you manage to get something from nature?")
            objbox(FUNGUS, "You show the fungus to Filliman.")
            natureSpirit.fungusShown.set(player, true)
            chatNpc(
                happy,
                "Wonderful, the mushroom represents 'something from nature'. Now we need to work " +
                    "out what the other components of the spell are!",
            )
        }
        while (true) {
            when (
                choice5(
                    "What are the things that are needed?",
                    1,
                    "What should I do when I have those things?",
                    2,
                    "I think I've solved the puzzle!",
                    3,
                    "Could I have another bloom scroll please?",
                    4,
                    "Ok, thanks.",
                    5,
                )
            ) {
                1 -> {
                    chatNpc(
                        neutral,
                        "The three things are: 'Something with faith', 'something from nature' and " +
                            "'something of the spirit-to-become freely given'.",
                    )
                    chatPlayer(quiz, "Ok, and 'something from nature' is the mushroom from the bloom spell you gave me?")
                    chatNpc(
                        neutral,
                        "Yes, that's correct, that seems right to me. The other things we need are " +
                            "'something with faith' and 'something of the spirit-to-become freely given.",
                    )
                    chatPlayer(quiz, "Do you have any ideas what those things are?")
                    chatNpc(sad, "I'm sorry my friend, but I do not.")
                }
                2 -> {
                    chatPlayer(quiz, "What should we do when we have those things?")
                    chatNpc(
                        neutral,
                        "Ah yes, I looked this up. It says,.. 'to arrange upon three rocks around the " +
                            "spirit-to-become...'. Then I must cast a spell. As you can see, I've " +
                            "already placed the rocks. I must have planned to do this before I died!",
                    )
                    chatPlayer(quiz, "Can we just place the components on any rock?")
                    chatNpc(
                        confused,
                        "Well, the only thing the journal says is that 'something with faith stands " +
                            "south of the spirit-to-become', but I'm so confused now I don't really " +
                            "know what that means. Oh, if only I had all my faculties!",
                    )
                }
                3 -> {
                    solvedPuzzle(spirit)
                    return
                }
                4 -> {
                    chatPlayer(quiz, "Could I have another bloom scroll please?")
                    if (player.inv.isFull()) {
                        chatNpc(neutral, "I would my friend, but you don't seem to have enough space in your inventory.")
                        return
                    }
                    chatNpc(neutral, "Sure, but please look after this one.")
                    access.invAdd(access.inv, DRUIDIC_SPELL)
                    objbox(DRUIDIC_SPELL, "The spirit of Filliman Tarlock gives you another bloom spell.")
                    return
                }
                else -> {
                    chatPlayer(neutral, "Ok, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.solvedPuzzle(spirit: Npc) {
        chatPlayer(happy, "I think I've solved the puzzle!")
        chatNpc(
            neutral,
            "Oh really.. Have you placed all the items on the stones? Ok, well, lets try! " +
                "<col=0000ff>~ The druid attempts to cast a spell.~</col>",
        )
        spirit.anim(CAST_SEQ)
        spirit.spotanim(CAST_SPOTANIM)
        access.soundSynth(TRANSFORM_START_SOUND)
        val onFaithStone = player.coords == MortMyreCoords.FAITH_STONE
        if (!onFaithStone || !natureSpirit.fungusPlaced.get(player) || !natureSpirit.spellPlaced.get(player)) {
            chatNpc(worried, "Hmm, something still doesn't seem right. I think we need something more before we can continue.")
            return
        }
        for (stone in listOf(MortMyreCoords.NATURE_STONE, MortMyreCoords.FAITH_STONE, MortMyreCoords.SPIRIT_STONE)) {
            access.spotanimMap(world, STONE_SPOTANIM, stone)
        }
        delay(RITUAL_TICKS)
        natureSpirit.advanceTo(access, STAGE_PUZZLE_SOLVED)
        chatNpc(
            happy,
            "Aha, everything seems to be in place! You can come through now into the grotto for the " +
                "final section of my transformation.",
        )
    }

    private companion object {
        const val CAST_SEQ = "seq.human_castteleport"
        const val CAST_SPOTANIM = "spotanim.druidicspirit_effect"
        const val STONE_SPOTANIM = "spotanim.druid_shooting_star"
        const val TRANSFORM_START_SOUND = "synth.spirit_transform_start"
        const val RITUAL_TICKS = 2
    }
}
