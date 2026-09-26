package org.rsmod.content.quest.area.lumbridge.dorgeshuun

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_HAM_HIDEOUT
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_MILL
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_STOREROOMS
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_TOUR
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_ZANIK_DEAD
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.THIEVING_REQ
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_HAM_CHATHEAD
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The H.A.M. hideout under the field west of Lumbridge: its guards, Johanhus Ulsbrecht, Jimmy the
 * Chisel in the cell, and the hidden trapdoor south of the stage that leads down to the storerooms
 * (`varbit.dttd_ham_trapdoor_state`: 0 rubble, 1 hidden trapdoor, 2 open, 3 rubble again once the
 * guards have found it). The Lumbridge swamp script asks [beforeDescending] before letting the player
 * down through the hideout's surface trapdoor, so Zanik can have her say.
 */
@Singleton
class HamHideout
@Inject
constructor(
    private val dttd: DeathToTheDorgeshuunQuest,
    private val follower: ZanikFollower,
    private val storerooms: HamStorerooms,
    private val launcher: ProtectedAccessLauncher,
    private val random: GameRandom,
) : PluginScript() {

    init {
        follower.onTick(::tick)
    }

    override fun ScriptContext.startup() {
        for (guard in HAM_GUARDS) {
            onOpNpc1(guard) { startDialogue(it.npc) { hamGuard() } }
        }
        onOpNpc1(JOHANHUS) { startDialogue(it.npc) { johanhus() } }
        onOpNpc1(JIMMY) { startDialogue(it.npc) { jimmy() } }
        onOpLoc1(HIDDEN_TRAPDOOR) { openHiddenTrapdoor() }
        onOpLoc2(HIDDEN_TRAPDOOR) { pickHiddenTrapdoor() }
        onOpLoc1(CELL_DOOR) {
            arriveDelay()
            mes("The door is locked.")
            soundSynth(LOCKED_SOUND)
        }
        onOpLoc5(CELL_DOOR) { pickCellDoor() }
    }

    /**
     * Zanik's reaction to going down into the hideout; returns false when the player is kept up
     * top. Called before the climb, with the trapdoor already unlocked.
     */
    suspend fun ProtectedAccess.beforeDescending(): Boolean {
        val zanik = follower.following(player) ?: return true
        when (dttd.stage(player)) {
            STAGE_TOUR -> {
                var goingDown = false
                startDialogue(zanik) {
                    chatNpc(angry, "We can't go into the HAM lair yet! I'm not wearing my disguise!")
                    chatNpc(
                        neutral,
                        "You said you'd show me around Lumbridge before we went down there, ${player.displayName}.",
                    )
                    if (choice2("Okay.", false, "I'm going down anyway.", true, title = "Select an option")) {
                        chatPlayer(neutral, "I'm going down anyway.")
                        chatNpc(sad, "I'll wait for you back in the castle then.")
                        follower.sendHome(player)
                        goingDown = true
                    } else {
                        chatPlayer(neutral, "Okay.")
                    }
                }
                return goingDown
            }
            in STAGE_HAM_HIDEOUT..STAGE_STOREROOMS ->
                if (!dttd.wearsHamSet(player)) {
                    startDialogue(zanik) {
                        chatNpc(
                            angry,
                            "Aren't you going to put on your HAM disguise? You might be able to do without one " +
                                "but I don't want you giving me away!",
                        )
                    }
                    return false
                }
            in STAGE_MILL..Int.MAX_VALUE -> {
                var goingDown = false
                startDialogue(zanik) {
                    chatNpc(
                        confused,
                        "What are we going down there again for? We know where we need to go -- the Lumbridge " +
                            "water mill!",
                    )
                    if (choice2("Okay.", false, "I'm going down anyway.", true)) {
                        chatPlayer(neutral, "I'm going down anyway.")
                        chatNpc(sad, "I'll wait for you back in the castle then.")
                        follower.sendHome(player)
                        goingDown = true
                    } else {
                        chatPlayer(neutral, "Okay.")
                    }
                }
                return goingDown
            }
        }
        return true
    }

    fun afterDescending(player: Player) {
        follower.relocate(player)
    }

    /** Back up the ladder after the guards took Zanik, the player looks for her. */
    suspend fun ProtectedAccess.afterClimbingOut() {
        follower.relocate(player)
        if (dttd.stage(player) != STAGE_ZANIK_DEAD || !player.dttdZanikCorpse) {
            return
        }
        startDialogue {
            chatPlayer(worried, "Zanik?")
            chatPlayer(worried, "Zanik! Where are you?")
        }
    }

    /** Sigmund, holed up in the hideout, gloats when the player brings Zanik to him. */
    suspend fun Dialogue.sigmundMeetsZanik(): Boolean {
        val stage = dttd.stage(player)
        if (stage !in STAGE_HAM_HIDEOUT..STAGE_STOREROOMS || !follower.isFollowing(player)) {
            return false
        }
        chatNpc(
            angry,
            "So you've brought a friend down here to mock me, ${player.displayName}? Well soon I will be the " +
                "one mocking you!",
        )
        zanik(
            quiz,
            "Is that Sigmund? The one who tried to start the war between Lumbridge and the cave goblins?",
        )
        chatNpc(
            angry,
            "Yes, I am! And I'm not ashamed of it! Those cave goblins are an abomination in the eyes of Holy " +
                "Saradomin!",
        )
        chatNpc(
            angry,
            "But I have another plan to destroy them. Soon Johanhus and I will be meeting to discuss the details.",
        )
        chatNpc(angry, "And there's nothing you can do about it!")
        return true
    }

    private suspend fun Dialogue.zanik(mood: dev.openrune.types.MesAnimType, text: String) =
        chatNpcSpecific("Zanik", ZANIK_HAM_CHATHEAD, mood, text)

    private fun disguisedWithZanik(player: Player): Boolean =
        dttd.stage(player) == STAGE_HAM_HIDEOUT && follower.isFollowing(player) && dttd.wearsHamSet(player)

    private suspend fun Dialogue.hamGuard() {
        val disguised = dttd.wearsHamSet(player)
        if (disguised) {
            chatNpc(
                happy,
                "Hello, ${player.brother()}, you fit in much better now that you're correctly attired. Before you " +
                    "stuck out like a sore thumb! Keep your eyes peeled though; I've heard there are thieves " +
                    "amongst us!",
            )
        }
        if (dttd.stage(player) == STAGE_ZANIK_DEAD) {
            chatNpc(happy, "Did you hear? They caught a goblin spy down in the store rooms!")
            chatPlayer(angry, "What did you do to her?")
            chatNpc(
                laugh,
                "Oh, Sigmund and some of the guys took it upstairs and dealt with it. Can't have goblins coming " +
                    "down here!",
            )
            chatNpc(
                laugh,
                "You should have seen how it was screaming for mercy! Those monsters aren't so tough when " +
                    "they're outnumbered twenty to one!",
            )
            return
        }
        if (disguisedWithZanik(player)) {
            meetZanik()
        }
        guardMenu()
    }

    private suspend fun Dialogue.meetZanik() {
        chatNpc(quiz, "I don't think I've seen you before! What's your name?")
        zanik(happy, "I'm Zanik of--")
        zanik(worried, "I mean, I'm Zanik!")
        chatNpc(confused, "Zanik? What kind of a name is that?")
        chatPlayer(neutral, "Zanik isn't from round here.")
        chatNpc(happy, "It's good that we're getting members from further afield!")
        chatNpc(quiz, "Is the monster problem very bad where you're from, Zanik?")
        zanik(sad, "Oh yes. My whole city is overrun by goblins.")
        chatNpc(shocked, "Goblins? That's terrible!")
        chatNpc(
            angry,
            "You know, there are goblins living right underneath Lumbridge. And the duke is treating them as if " +
                "they were people like you and me! He's even signed a peace treaty with them!",
        )
        zanik(quiz, "So what's your group planning to do about it?")
        chatNpc(
            neutral,
            "Johanhus doesn't tell his plans to ordinary members like us, but I've heard he and Sigmund are " +
                "planning something big.",
        )
        chatNpc(happy, "I don't think we'll have to worry about the cave goblin threat for much longer!")
        zanik(worried, "No... I'm sure the threat will be dealt with soon.")
        player.dttdHamCivilians = true
    }

    private suspend fun Dialogue.guardMenu() {
        when (
            menu(
                "What are all you people doing here?" to 1,
                "Who are you and what do you do here?" to 2,
                "What do you think you're going to achieve?" to 3,
                "Where did all you people come from?" to 4,
                "Ok, thanks." to 0,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "What are all you people doing here?")
                when (random.of(maxExclusive = 3)) {
                    0 ->
                        chatNpc(
                            neutral,
                            "Many of us disagree with the king about what freedoms the local monster population " +
                                "should have. We're taking a stand and mobilising our forces against the " +
                                "monstrous hordes.",
                        )
                    1 ->
                        chatNpc(
                            happy,
                            "I'm totally in awe of Johanhus, he really knows what's what. I know he keeps going on " +
                                "about monsters and it's clear there are too many of them, so hey, I agree with " +
                                "whatever Johanhus says.",
                        )
                    else ->
                        chatNpc(
                            neutral,
                            "We're against the monsters..like Johanhus says...we don't like them...you know.",
                        )
                }
            }
            2 -> {
                chatPlayer(quiz, "Who are you and what do you do here?")
                chatNpc(
                    neutral,
                    "I'm a strong believer in the non-monsters policy...we should really get rid of them...and if " +
                        "that means I have to live in a cave like a monster, so be it!",
                )
            }
            3 -> {
                chatPlayer(quiz, "What do you think you're going to achieve?")
                chatNpc(
                    neutral,
                    "We want a world without monsters, to live in safety and without fear of being attacked by " +
                        "these ferocious beasts.",
                )
                chatPlayer(neutral, "But there aren't that many ferocious beasts in the towns and cities.")
                chatNpc(
                    angry,
                    "That's not enough, we want to get rid of them totally, we want to enjoy the surrounding " +
                        "lands and not worry about our children playing in caves and so on.",
                )
            }
            4 -> {
                chatPlayer(quiz, "Where did all you people come from?")
                chatNpc(
                    neutral,
                    "Most of us came from small towns that had been attacked by monsters. We all got fed up with " +
                        "it and so decided to join this movement. We're hoping to return to the towns and cities " +
                        "when we've cleaned up the areas",
                )
                chatNpc(neutral, "that these monsters live in.")
            }
            else -> chatPlayer(neutral, "Ok, thanks.")
        }
    }

    private suspend fun Dialogue.johanhus() {
        val stage = dttd.stage(player)
        if (stage in STAGE_ZANIK_DEAD until STAGE_MILL) {
            chatNpc(
                laugh,
                "So, you got out of the cell? No matter...your goblin friend has already been dealt with...and " +
                    "Sigmund is on his way to deal with the rest of her foul brood!",
            )
            return
        }
        chatNpc(
            neutral,
            "Welcome ${player.brother()}... to our organisation... Ours is a fight for humanity in all its shapes " +
                "and forms... and to rid mankind of the monsters that exist in our world.",
        )
        while (true) {
            val options = buildList {
                add("What kind of organisation is this?" to 1)
                add("Who are you and what do you do here?" to 2)
                add("Ok, thanks." to 0)
                if (stage == STAGE_HAM_HIDEOUT) {
                    add("Are you planning to do anything about the cave goblins?" to 3)
                }
            }
            when (menu(options)) {
                1 -> {
                    chatPlayer(quiz, "What kind of organisation is this?")
                    chatNpc(
                        neutral,
                        "We're a proactive organisation working towards the cessation of monsters in normal " +
                            "civilised human society. There seems to be no backbone in this land so we're " +
                            "stepping up to the challenge.",
                    )
                    chatPlayer(
                        quiz,
                        "Hmm, how do you propose to do that? I mean, I see people everyday killing goblins " +
                            "around here!",
                    )
                    chatNpc(
                        neutral,
                        "We're mobilising people and we're starting our own society...cleaning out the caves " +
                            "once inhabited by these foul creatures and defending them so that they can never " +
                            "again shelter sub human species.",
                    )
                    chatPlayer(neutral, "That sounds kind of strange, but hey it's your choice.")
                }
                2 -> {
                    chatPlayer(quiz, "Who are you and what do you do here?")
                    chatNpc(
                        happy,
                        "My name is Johanhus and I lead these glorious people on a courageous mission called, " +
                            "'Humans against Monsters', we mean to make this land free of monsters so we can all " +
                            "live in peace.",
                    )
                }
                3 -> {
                    caveGoblins()
                    return
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.caveGoblins() {
        chatPlayer(quiz, "Are you planning to do anything about the cave goblins?")
        chatNpc(
            neutral,
            "You should know, brother... we don't let ordinary members in on all our plans... but rest assured " +
                "those foul creatures will be dealt with very soon!",
        )
        player.dttdHamJohanhus = true
        while (true) {
            when (
                menu(
                    "Can't you please tell me?" to 1,
                    "Is it really necessary?" to 2,
                    "That's good." to 0,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Can't you please tell me?")
                    chatNpc(
                        angry,
                        "No! That kind of information is for...only senior members of the group. Ordinary members " +
                            "like you must learn to...trust your leaders.",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "Is it really necessary?")
                    chatNpc(
                        angry,
                        "How can you even...ask that? Right beneath Lumbridge is a whole cave system full of those " +
                            "creatures! They've got a passage leading right up into the castle kitchen!",
                    )
                    chatNpc(
                        angry,
                        "And I've heard that there's a whole city of those things down there, not that anyone has " +
                            "ever seen it. What have they got in there? What are they planning?",
                    )
                }
                else -> {
                    chatPlayer(happy, "That's good.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.jimmy() {
        chatNpc(happy, "Hello mate!")
        if (dttd.stage(player) == STAGE_ZANIK_DEAD) {
            chatPlayer(worried, "Did you see what happened to my friend?")
            chatNpc(
                neutral,
                "There was a lot of commotion when they dragged you in here. There was someone else, she was " +
                    "struggling like anything. They said they'd take her outside. Dunno what happened to her then.",
            )
            chatPlayer(shocked, "Oh no! I've got to find her!")
        }
    }

    private fun Player.brother(): String = if (appearance.pronoun == 1) "sister" else "brother"

    /* The hidden trapdoor */

    private suspend fun ProtectedAccess.openHiddenTrapdoor() {
        arriveDelay()
        when (player.dttdHamTrapdoor) {
            TRAPDOOR_HIDDEN -> {
                mes("The trapdoor is locked.")
                soundSynth(LOCKED_SOUND)
            }
            TRAPDOOR_OPEN -> with(storerooms) { climbDown() }
        }
    }

    private suspend fun ProtectedAccess.pickHiddenTrapdoor() {
        arriveDelay()
        if (player.dttdHamTrapdoor != TRAPDOOR_HIDDEN) {
            return
        }
        if (player.thievingLvl < THIEVING_REQ) {
            startDialogue {
                mesbox("You need a thieving level of $THIEVING_REQ in order to pick the lock of that trapdoor.")
            }
            val zanik = follower.following(player) ?: return
            startDialogue(zanik) {
                chatNpc(quiz, "Can't you open it?")
                chatPlayer(sad, "No, I'm not good enough at thieving.")
                chatNpc(
                    sad,
                    "I'm not much good at thieving either, I'm afraid. You'd better get some practice.",
                )
            }
            return
        }
        mes("You attempt to pick the lock on the trapdoor.", ChatType.Spam)
        anim(PICK_LOCK_SEQ)
        soundSynth(PICK_LOCK_SOUND)
        delay(PICK_LOCK_TICKS)
        if (random.of(maxExclusive = 100) >= (PICK_LOCK_BASE + player.thievingLvl).coerceAtMost(PICK_LOCK_MAX)) {
            mes("You fail to pick the lock.")
            return
        }
        mes("You pick the lock on the trapdoor.")
        soundSynth(TRAPDOOR_OPEN_SOUND)
        player.dttdHamTrapdoor = TRAPDOOR_OPEN
    }

    /* The cell */

    private suspend fun ProtectedAccess.pickCellDoor() {
        arriveDelay()
        if (player.coords.x < CELL_DOOR_TILE.x) {
            mes("You have no reason to break into the cell.")
            return
        }
        mes("You attempt to pick the lock on the door.", ChatType.Spam)
        anim(PICK_LOCK_SEQ)
        soundSynth(PICK_LOCK_SOUND)
        delay(PICK_LOCK_TICKS)
        mes("You pick the lock and slip out of the cell.")
        soundSynth(DOOR_OPEN_SOUND)
        telejump(OUTSIDE_CELL)
    }

    /* Noticed while walking about with Zanik */

    private fun tick(player: Player, zanik: Npc) {
        if (dttd.stage(player) != STAGE_HAM_HIDEOUT || !dttd.wearsHamSet(player)) {
            return
        }
        val coords = player.coords
        if (!player.dttdHamDeacon && coords.isWithinDistance(DEACON_TILE, DEACON_RANGE)) {
            player.dttdHamDeacon = true
            launcher.launch(player) {
                startDialogue(zanik) {
                    chatNpc(angry, "Listen to what that speaker's saying, ${player.displayName}!")
                    chatNpc(
                        angry,
                        "And they're all lapping it up! No one challenges him or even asks questions. They're " +
                            "just sitting there while he fills their minds with hate!",
                    )
                }
            }
            return
        }
        if (
            player.dttdHamTrapdoor == TRAPDOOR_RUBBLE &&
                player.dttdHamJohanhus &&
                player.dttdHamDeacon &&
                coords.isWithinDistance(TRAPDOOR_TILE, RUBBLE_RANGE)
        ) {
            player.dttdHamTrapdoor = TRAPDOOR_HIDDEN
            launcher.launch(player) {
                startDialogue(zanik) {
                    chatNpc(neutral, "Look, there.")
                    chatPlayer(quiz, "Where?")
                    chatNpc(neutral, "Over there. There's a trapdoor hidden by dirt.")
                    chatPlayer(
                        happy,
                        "You cave goblins must have better eyesight than humans. I would never have noticed it " +
                            "without you!",
                    )
                }
            }
        }
    }

    companion object {
        const val TRAPDOOR_RUBBLE = 0
        const val TRAPDOOR_HIDDEN = 1
        const val TRAPDOOR_OPEN = 2
        const val TRAPDOOR_BURIED = 3

        const val HIDDEN_TRAPDOOR = "loc.dttd_ham_trapdoor"
        const val CELL_DOOR = "loc.favour_prisondoor"
        const val JOHANHUS = "npc.favour_johanhus_ulsbrecht"
        const val JIMMY = "npc.favour_jimmy"
        val HAM_GUARDS =
            listOf("npc.favour_guard_male_no_beard", "npc.favour_guard_male_bearded", "npc.favour_guard_long_beard")

        const val PICK_LOCK_SEQ = "seq.human_picklock_chest"
        const val PICK_LOCK_SOUND = "synth.pick_lock"
        const val LOCKED_SOUND = "synth.locked"
        const val TRAPDOOR_OPEN_SOUND = "synth.trapdoor_open"
        const val DOOR_OPEN_SOUND = "synth.door_open"
        const val PICK_LOCK_TICKS = 3
        const val PICK_LOCK_BASE = 30
        const val PICK_LOCK_MAX = 90

        const val DEACON_RANGE = 5
        const val RUBBLE_RANGE = 2

        /** The deacon preaches from the stage in the middle of the hideout. */
        val DEACON_TILE = CoordGrid(3165, 9628, 0)
        val TRAPDOOR_TILE = CoordGrid(3166, 9622, 0)

        /** The cell door is on the west side of the cell Jimmy the Chisel sits in. */
        val CELL_DOOR_TILE = CoordGrid(3183, 9611, 0)
        val OUTSIDE_CELL = CoordGrid(3182, 9611, 0)
        val JAIL_WAKE_TILE = CoordGrid(3184, 9612, 0)
    }
}
