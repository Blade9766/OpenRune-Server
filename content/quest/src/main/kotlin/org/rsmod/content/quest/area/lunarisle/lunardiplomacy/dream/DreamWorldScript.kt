package org.rsmod.content.quest.area.lunarisle.lunardiplomacy.dream

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.DreamChallenge
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.LUNAR_STAFF
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_CHALLENGES
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_DEFEATED_SELF
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_FACE_SELF
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_IN_DREAM
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarPiece
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.lunarBrazierLit
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.lunarSpokenCentre
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.lunarWasInDream
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Getting into and around the Dream World: burning the soaked kindling on the ceremonial brazier,
 * the Ethereal Being at the centre who hears what each challenge taught, the spring platforms that
 * fling the player between the centre and the six islands, and the book of the player's life that
 * wakes them up again.
 */
class DreamWorldScript
@Inject
constructor(
    private val lunar: LunarDiplomacyQuest,
    private val dream: DreamWorld,
    private val challenges: DreamChallenges,
    private val fight: MeFight,
    private val worldRepo: WorldRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLocU(BRAZIER, TINDERBOX) { lightBrazier() }
        onOpLocU(BRAZIER, SOAKED_KINDLING) { burnKindling() }
        onOpLocU(BRAZIER, KINDLING) { mes("The kindling needs to be soaked in the waking sleep potion first.") }

        onOpNpc1(DreamWorld.BEING_MAN) { startDialogue(it.npc) { being() } }
        onOpNpc1(DreamWorld.BEING_LADY) { startDialogue(it.npc) { being() } }

        for (platform in DreamIsland.entries) {
            onOpLoc1(platform.loc) { stepOn(it.loc, platform) }
        }
        onOpLoc1(PLINTH) { readLife() }

        onPlayerLogout {
            challenges.clearScratch(player)
            fight.cleanup(player)
            dream.forget(player)
        }
        onPlayerLogin { challenges.clearScratch(player) }
    }

    private fun ProtectedAccess.lightBrazier() {
        if (player.lunarBrazierLit) {
            mes("The brazier is already lit.")
            return
        }
        anim(LIGHT_SEQ)
        player.lunarBrazierLit = true
        mes("You light the ceremonial brazier.")
    }

    private suspend fun ProtectedAccess.burnKindling() {
        if (!player.lunarBrazierLit) {
            mes("You need to light the brazier first.")
            return
        }
        val stage = lunar.stage(player)
        if (!DreamWorld.dreamStage(stage) || stage == STAGE_DEFEATED_SELF) {
            mes("Nothing interesting happens.")
            return
        }
        invDel(inv, SOAKED_KINDLING)
        anim(LIGHT_SEQ)
        soundSynth(BRAZIER_SOUND)
        delay(2)
        if (!wearingCeremonialDress()) {
            startDialogue {
                chatPlayer(
                    confused,
                    "Huh? If I dreamt, I don't remember any of it. Wait a minute! I have to wear ALL " +
                        "the ceremonial gear AND be holding the staff.",
                )
            }
            return
        }
        fadeToBlack()
        val entered = with(dream) { enterDream(DreamWorld.CENTRE) }
        if (!entered) {
            fadeFromBlack()
            closeFadeOverlay()
            return
        }
        player.lunarBrazierLit = false
        player.lunarWasInDream = 1
        lunar.advanceTo(this, STAGE_IN_DREAM)
        challenges.clearScratch(player)
        player.midiJingle(DREAM_JINGLE)
        anim(DreamWorld.FALLING_SEQ)
        delay(1)
        fadeFromBlack()
        closeFadeOverlay()
        anim(DreamWorld.LANDING_SEQ)
        soundSynth(DreamWorld.LAND_SOUND)
        delay(2)
        anim(DreamWorld.STAND_SEQ)
        say("Huh? Where am I?")
    }

    private fun ProtectedAccess.wearingCeremonialDress(): Boolean =
        LunarPiece.entries.all { it.obj in player.worn } && LUNAR_STAFF in player.worn

    private suspend fun ProtectedAccess.readLife() {
        var wake = false
        startDialogue {
            wake = choice2("Yes", true, "No", false, title = "Are you sure you want to read and return to reality?")
            if (!wake) {
                return@startDialogue
            }
            chatPlayer(neutral, "Let's see what this says... It seems to be my life story...")
            val pronoun = if (access.isBodyTypeB()) "she" else "he"
            chatPlayer(
                neutral,
                "'In the age of the fifth, a child was born of humble beginnings. With little money " +
                    "and nothing but a dream of adventure, $pronoun set out to...'",
            )
        }
        ifClose()
        if (!wake) {
            return
        }
        fight.cleanup(player)
        challenges.clearScratch(player)
        fadeToBlack()
        with(dream) { wake() }
        delay(1)
        fadeFromBlack()
        closeFadeOverlay()
        startDialogue { chatPlayer(confused, "Huh? Was it all a dream?") }
    }

    private suspend fun ProtectedAccess.stepOn(loc: BoundLocInfo, island: DreamIsland) {
        val world = dream.worldCoords(player)
        if (!dream.isDreaming(player)) {
            return
        }
        if (!DreamWorld.onCentreIsland(world)) {
            with(challenges) { leaveIsland(island.challenge) }
            launch(loc)
            with(dream) { throwTo(DreamWorld.CENTRE) }
            return
        }
        val stage = lunar.stage(player)
        if (stage < STAGE_CHALLENGES) {
            mesbox("You should probably speak to that interesting character in the centre first.")
            return
        }
        if (player.lunarSpokenCentre) {
            mesbox("You need to go and tell that interesting character in the centre what you have just learned first.")
            return
        }
        if (island.challenge.isComplete(player) || stage >= STAGE_FACE_SELF) {
            mes("You've learned all you need to from this area.")
            return
        }
        challenges.clearScratch(player)
        launch(loc)
        with(dream) { throwTo(island.landing) }
        challenges.arrive(this, island.challenge)
    }

    private fun ProtectedAccess.launch(loc: BoundLocInfo) {
        locAnim(worldRepo, loc, EJECT_PLATFORM_SEQ)
    }

    /* The Ethereal Being */

    private suspend fun Dialogue.being() {
        val stage = lunar.stage(player)
        when {
            stage <= STAGE_IN_DREAM -> firstMeeting()
            stage == STAGE_CHALLENGES && player.lunarSpokenCentre -> lesson()
            stage == STAGE_CHALLENGES && DreamChallenge.allComplete(player) -> finalChallenge(recap = true)
            stage == STAGE_CHALLENGES -> {
                chatPlayer(neutral, "Hi.")
                chatNpc(quiz, "Have you learnt anything?")
                chatPlayer(neutral, "Nothing so far.")
                chatNpc(neutral, "Give it time.")
            }
            stage == STAGE_FACE_SELF -> finalChallenge(recap = true)
            stage == STAGE_DEFEATED_SELF -> {
                chatPlayer(happy, "I did it! I confronted myself!")
                chatNpc(happy, "Well done. I think your lesson is complete; best you wake yourself up. Go and read the book on the lectern.")
            }
            else -> chatNpc(neutral, "Your time here is done. Read the book of your life to wake.")
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatPlayer(confused, "What's going on?")
        chatNpc(happy, "Don't you know? You're in the land of your own dreams!")
        chatPlayer(confused, "But you can't know you're dreaming while you're dreaming. You'd wake up!")
        chatNpc(neutral, "Ah, but this is a waking sleep.")
        chatPlayer(quiz, "Okay. So how do I wake up?")
        chatNpc(
            neutral,
            "Read the book on this lectern. It is the book of your life: all that has been, all " +
                "that is, and blank pages for all that will be. Reading it returns your mind to the " +
                "waking world.",
        )
        chatPlayer(quiz, "This is all very deep. What am I supposed to do now?")
        chatNpc(
            neutral,
            "That's up to you. This is a test, so I can't give you the answers, but there are six " +
                "tasks to choose from: 'A game of chance', 'Communicating in numbers', 'Chop, chop, " +
                "chop away', 'Where am I?', 'The race is on!' and 'Anything you can do...'.",
        )
        chatNpc(neutral, "Step on the platforms around this island to reach them.")
        lunar.advanceTo(access, STAGE_CHALLENGES)
        chatPlayer(bored, "This is the strangest place I have ever been.")
        chatNpc(laugh, "Then you don't know your mind very well, do you!")
    }

    private suspend fun Dialogue.lesson() {
        val challenge = DreamChallenge.entries.getOrNull(lunar.pendingLesson.get(player) - 1)
        chatPlayer(neutral, "Hi.")
        chatNpc(quiz, "Have you learnt anything?")
        chatPlayer(neutral, "Well...")
        when (challenge) {
            DreamChallenge.Numbers -> {
                chatPlayer(neutral, "I played a strange game with a man who spoke almost entirely in numbers.")
                chatNpc(quiz, "And what do you suppose that part of your dream was telling you?")
                chatPlayer(neutral, "That to move forward I had to understand someone else.")
                chatNpc(happy, "Exactly. By understanding the people around you, you learn a great deal about yourself.")
            }
            DreamChallenge.Mimic -> {
                chatPlayer(neutral, "I met a man who only spoke with his body. I copied what he did, and before long it felt like we were friends.")
                chatNpc(quiz, "That's dreams for you. But there must be some logic behind it?")
                chatPlayer(neutral, "It showed how important it is to relate to others. We share so much, and can learn a lot from each other.")
                chatNpc(happy, "That seems about right to me.")
            }
            DreamChallenge.Race -> {
                chatPlayer(neutral, "I raced a man who had a straight, easy path, while mine twisted about and was full of obstacles. I still beat him.")
                chatNpc(quiz, "And what lesson does that teach?")
                chatPlayer(neutral, "Not every path is easy, but if you want it enough and put in the effort, you can still succeed.")
                chatNpc(happy, "Very, very good!")
            }
            DreamChallenge.Trees -> {
                chatPlayer(neutral, "I had to cut down trees faster than another man, who kept asking what I thought I could achieve.")
                chatNpc(quiz, "And your thoughts on its meaning?")
                chatPlayer(neutral, "That you need to know your own abilities and have confidence in them to beat whatever stands against you.")
                chatNpc(happy, "I am very impressed.")
            }
            DreamChallenge.Memory -> {
                chatPlayer(neutral, "I had to jump across a field of clouds, some of which weren't real, remembering where I'd been.")
                chatNpc(quiz, "Interesting. Any interpretation?")
                chatPlayer(neutral, "Knowing where you've been and where you're going helps you make the right decisions.")
                chatNpc(happy, "Bingo!")
            }
            DreamChallenge.Dice -> {
                chatPlayer(neutral, "I had to turn over dice in what looked like a game of chance, but there was logic behind it all.")
                chatNpc(quiz, "What do you suppose that means?")
                chatPlayer(neutral, "There's always some of the unknown in life, but there is always an answer if you look for it and keep hope.")
                chatNpc(happy, "Spot on.")
            }
            null -> chatNpc(neutral, "Hmm, it seems your thoughts are clouded. Come back to me when they're clearer.")
        }
        player.lunarSpokenCentre = false
        lunar.pendingLesson.set(player, 0)
        if (DreamChallenge.allComplete(player)) {
            finalChallenge(recap = false)
        }
    }

    private suspend fun Dialogue.finalChallenge(recap: Boolean) {
        if (recap) {
            chatPlayer(neutral, "Hi.")
        }
        chatNpc(
            happy,
            "You've learnt the six core lessons: make sense of others to understand yourself; be " +
                "able to relate to others; use your abilities to progress; know your past and " +
                "present to guide your future; harness your confidence; and appreciate the unknown.",
        )
        chatNpc(
            neutral,
            "Magic comes from within; understanding these lessons opens the power of the Moon Clan " +
                "to you. But there is one more challenge you must face, and I can't tell you what " +
                "to expect.",
        )
        val ready = choice2("Of course. I'm ready.", true, "I'm not ready yet, give me a little more time.", false)
        if (!ready) {
            chatPlayer(neutral, "I'm not ready yet, give me a little more time.")
            return
        }
        chatPlayer(happy, "Of course. I'm ready.")
        lunar.advanceTo(access, STAGE_FACE_SELF)
        access.ifClose()
        fight.begin(access)
    }

    private companion object {
        const val BRAZIER = "loc.lunar_moonclan_brazier_multi"
        const val PLINTH = "loc.lunar_dream_dream_plinth"
        const val TINDERBOX = "obj.tinderbox"
        const val KINDLING = "obj.lunar_moonclan_kindling"
        const val SOAKED_KINDLING = "obj.lunar_moonclan_kindling_soaked"

        const val LIGHT_SEQ = "seq.human_createfire"
        const val BRAZIER_SOUND = "synth.moon_babahouse"
        const val EJECT_PLATFORM_SEQ = "seq.quest_lunar_ejector_platform"

        /** "Dream World", by its cache group. */
        const val DREAM_JINGLE = 96
    }
}

/** A Dream World island, the spring platform that reaches it and where the player lands there. */
enum class DreamIsland(val challenge: DreamChallenge, val loc: String, val landing: CoordGrid) {
    Numbers(DreamChallenge.Numbers, "loc.quest_lunar_spring_numbers_multi", CoordGrid(1785, 5064, 2)),
    Mimic(DreamChallenge.Mimic, "loc.quest_lunar_spring_music_multi", CoordGrid(1771, 5069, 2)),
    Race(DreamChallenge.Race, "loc.quest_lunar_spring_power_multi", CoordGrid(1785, 5079, 2)),
    Trees(DreamChallenge.Trees, "loc.quest_lunar_spring_tree_multi", CoordGrid(1766, 5111, 2)),
    Memory(DreamChallenge.Memory, "loc.quest_lunar_spring_jump_multi", CoordGrid(1736, 5110, 2)),
    Dice(DreamChallenge.Dice, "loc.quest_lunar_spring_dice_multi", CoordGrid(1734, 5068, 2)),
}
