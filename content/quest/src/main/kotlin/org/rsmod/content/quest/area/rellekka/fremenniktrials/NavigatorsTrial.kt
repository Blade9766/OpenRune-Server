package org.rsmod.content.quest.area.rellekka.fremenniktrials

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.SWENSEN
import org.rsmod.content.quest.area.rellekka.fremenniktrials.npcs.outerlanderRebuff
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Swensen the Navigator's trial: find the way through the maze of portal rooms beneath his house.
 *
 * Each torchlit room has one portal leading on to the next; every other portal drops the player
 * into the dark rooms, whose portals only lead to other dark rooms. The way through spells his
 * name: south, west, east, north, south, east, north. Escape ropes in every room lead back up to
 * his house, and the ladder in the last room earns his vote.
 */
@Singleton
class NavigatorsTrial
@Inject
constructor(
    private val quest: FremennikTrialsQuest,
    private val merchant: MerchantTrial,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(SWENSEN) { startDialogue(it.npc) { swensen() } }
        onOpLoc1(MAZE_TOP_LADDER) { enterMaze() }
        onOpLoc1(MAZE_ENTRANCE_LADDER) { climbOut(HOUSE_LADDER_LANDING) }
        onOpLoc1(ESCAPE_ROPE) { escape() }
        onOpLoc1(MAZE_EXIT_LADDER) { mazeSolved() }
        for ((index, portal) in PATH_PORTALS.withIndex()) {
            onOpLoc1(portal) { usePortal(ROOMS[index + 1]) }
        }
        onOpLoc1(WRONG_PORTAL) { usePortal(DARK_ROOMS[random.of(DARK_ROOMS.size)]) }
    }

    /* Swensen */

    private suspend fun Dialogue.swensen() {
        when {
            !quest.isStarted(player) -> outerlanderRebuff()
            merchant.isActive(player) -> {
                chatPlayer(happy, "Hello!")
                with(merchant) {
                    withMerchantOption(MerchantContact.Swensen) {
                        if (quest.hasVote(player, Trial.Navigator)) swensenVoted() else navigatorsTrial()
                    }
                }
            }
            quest.hasVote(player, Trial.Navigator) -> swensenVoted()
            else -> {
                chatPlayer(happy, "Hello!")
                navigatorsTrial()
            }
        }
    }

    private suspend fun Dialogue.swensenVoted() {
        chatNpc(
            happy,
            "Greetings again outerlander! I must say, I am still impressed with the way you moved " +
                "through my maze!",
        )
        chatPlayer(quiz, "Thanks. So I can rely on your vote in my favour at the council of elders?")
        chatNpc(happy, "Absolutely! You will be an asset to the clan!")
    }

    private suspend fun Dialogue.navigatorsTrial() {
        if (player.ftSwensenStarted) {
            chatPlayer(neutral, "Man, your maze is pretty tough!")
            chatNpc(
                laugh,
                "Hahahaha it is the most complex route I have ever devised! I am truly a genius at " +
                    "navigation! The world will remember my name!",
            )
            chatPlayer(quiz, "Can't I do something else for your vote at the council of elders?")
            chatNpc(neutral, "No, you cannot. It is my maze, or nothing.")
            return
        }
        chatPlayer(
            neutral,
            "I am trying to become a member of the Fremennik clan! The Chieftain told me that I may be " +
                "able to gain your vote at the council of elders?",
        )
        chatNpc(
            neutral,
            "You wish to stop being an outerlander? I can understand that! I have no reason why I " +
                "would prevent you becoming a Fremennik...",
        )
        chatNpc(neutral, "...but you must first pass a little test for me to prove you are worthy.")
        chatPlayer(quiz, "What kind of test?")
        chatNpc(
            neutral,
            "Well, I serve our clan as a navigator. The seas can be a fearful place when you know not " +
                "where you are heading.",
        )
        chatNpc(
            neutral,
            "Should something happen to me, all members of our tribe have some basic sense of " +
                "direction so that they may always return safely home.",
        )
        chatNpc(
            neutral,
            "If you are able to demonstrate to me that you too have a good sense of direction then I " +
                "will recommend you to the rest of the council of elders immediately.",
        )
        chatPlayer(quiz, "Well, how would I go about showing that?")
        chatNpc(
            neutral,
            "Ah, a simple task! Below this building I have constructed a maze; should you be able to " +
                "walk from one side to the other that will be proof to me.",
        )
        chatNpc(quiz, "You wish to try my challenge?")
        if (!choice2("Yes", true, "No", false)) {
            chatPlayer(
                bored,
                "No thanks. I think I can get someone else to vouch for me to the council of elders, " +
                    "without going through a stupid maze like some lab rat.",
            )
            chatNpc(
                neutral,
                "Well, I am sorry you feel that way outerlander. I cannot vouch for you until you prove " +
                    "to me you have some skill that will benefit our clan somehow.",
            )
            chatNpc(confused, "What exactly is a 'lab rat' anyway?")
            chatPlayer(neutral, "Ah, forget it.")
            return
        }
        chatPlayer(happy, "A maze? Is that all? Sure, it sounds simple enough.")
        player.ftSwensenStarted = true
        chatNpc(
            neutral,
            "I will warn you outerlander, this maze was designed by myself, and is of the most " +
                "fiendish complexity!",
        )
        chatPlayer(happy, "Oh really? Watch and learn...")
    }

    private suspend fun Dialogue.noManners() {
        chatNpcSpecific(
            "Swensen the Navigator",
            SWENSEN,
            angry,
            "Have you no manners outerlander? This is my home, you will show me the due respect!",
        )
        chatPlayer(sad, "Erm.... sorry I guess.")
    }

    /* The maze */

    private fun isRunning(access: ProtectedAccess): Boolean =
        quest.isInProgress(access.player) && access.player.ftSwensenStarted

    private suspend fun ProtectedAccess.enterMaze() {
        when {
            quest.hasVote(player, Trial.Navigator) ->
                startDialogue { chatPlayer(bored, "No way am I doing that maze again!") }
            !isRunning(this) -> startDialogue { noManners() }
            else -> {
                arriveDelay()
                anim(CLIMB_DOWN_SEQ)
                delay(1)
                telejump(ROOMS.first(), TeleportType.Exempt)
            }
        }
    }

    /** Swensen's trapdoors only open from below. */
    suspend fun ProtectedAccess.trapdoor() {
        arriveDelay()
        if (!isRunning(this) || quest.hasVote(player, Trial.Navigator)) {
            startDialogue { noManners() }
            return
        }
        mesbox(
            "You try to open the trapdoor but it won't budge! It looks like the trapdoor can only be " +
                "opened from the other side.",
        )
    }

    private suspend fun ProtectedAccess.usePortal(destination: CoordGrid) {
        arriveDelay()
        soundSynth(PORTAL_SOUND)
        delay(1)
        telejump(destination, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.climbOut(landing: CoordGrid) {
        arriveDelay()
        anim(CLIMB_UP_SEQ)
        delay(1)
        telejump(landing, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.escape() {
        climbOut(TRAPDOOR_LANDING)
        startDialogue {
            chatNpcSpecific(
                "Swensen the Navigator",
                SWENSEN,
                laugh,
                "Decided you could not solve my labyrinth after all, did you outerlander? Too tricky " +
                    "for you?",
            )
            chatPlayer(sad, "Yeah, it is really confusing down there...")
            chatNpcSpecific(
                "Swensen the Navigator",
                SWENSEN,
                laugh,
                "Hahaha! Don't worry outerlander, you are not the first to fail its puzzle!",
            )
        }
    }

    private suspend fun ProtectedAccess.mazeSolved() {
        climbOut(HOUSE_LADDER_LANDING)
        if (!isRunning(this) || player.voted(Trial.Navigator)) {
            return
        }
        startDialogue {
            chatNpcSpecific(
                "Swensen the Navigator",
                SWENSEN,
                shocked,
                "Outerlander! You have finished my maze! I am genuinely impressed!",
            )
            chatPlayer(
                quiz,
                "So does that mean I can rely on your vote at the council of elders to allow me into " +
                    "your village?",
            )
            chatNpcSpecific(
                "Swensen the Navigator",
                SWENSEN,
                happy,
                "Of course outerlander! I am nothing if not a man of my word!",
            )
            chatPlayer(happy, "Thanks!")
        }
        quest.grantVote(this, Trial.Navigator)
    }

    companion object {
        const val MAZE_TOP_LADDER = "loc.vt_mazeladdertopentrance"
        const val MAZE_ENTRANCE_LADDER = "loc.vt_mazeladderentrance"
        const val MAZE_EXIT_LADDER = "loc.vt_mazeladderexit"
        const val ESCAPE_ROPE = "loc.vt_mazeladderescapeladder"
        const val WRONG_PORTAL = "loc.vt_mazeportal_wrong"

        /** The portal out of each torchlit room in turn: S, W, E, N, S, E, N. */
        val PATH_PORTALS =
            listOf(
                "loc.vt_mazeportal_1",
                "loc.vt_mazeportal_2",
                "loc.vt_mazeportal_3",
                "loc.vt_mazeportal_4",
                "loc.vt_mazeportal_5",
                "loc.vt_mazeportal_6",
                "loc.vt_mazeportal_7",
            )

        /** Where the player lands in each torchlit room, from the entrance to the exit ladder. */
        val ROOMS =
            listOf(
                CoordGrid(2632, 10005),
                CoordGrid(2643, 10015),
                CoordGrid(2654, 10004),
                CoordGrid(2666, 10015),
                CoordGrid(2631, 10026),
                CoordGrid(2654, 10037),
                CoordGrid(2667, 10026),
                CoordGrid(2666, 10037),
            )

        val DARK_ROOMS =
            listOf(
                CoordGrid(2632, 10015),
                CoordGrid(2643, 10005),
                CoordGrid(2643, 10026),
                CoordGrid(2643, 10039),
                CoordGrid(2654, 10015),
                CoordGrid(2656, 10027),
                CoordGrid(2665, 10004),
                CoordGrid(2631, 10037),
            )

        val HOUSE_LADDER_LANDING = CoordGrid(2645, 3657)
        val TRAPDOOR_LANDING = CoordGrid(2648, 3657)

        /** Swensen's house, where the two trapdoors into the maze are. */
        fun isSwensensHouse(coords: CoordGrid): Boolean =
            coords.level == 0 && coords.x in 2639..2650 && coords.z in 3653..3663

        const val CLIMB_DOWN_SEQ = "seq.human_climbing_down"
        const val CLIMB_UP_SEQ = "seq.human_climbing"
        const val PORTAL_SOUND = "synth.teleport_all"
    }
}
