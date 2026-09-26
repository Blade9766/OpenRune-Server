package org.rsmod.content.quest.area.lumbridge.dorgeshuun

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.BONE_CROSSBOW
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.JUNA_CHATHEAD
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_HAM_HIDEOUT
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_MILL
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_REVIVED
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.TORCH
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_FOLLOWER_HAM
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_SHOWDOWN
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeCellar
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest
import org.rsmod.content.quest.area.varrock.demonslayer.beginCutscene
import org.rsmod.content.quest.area.varrock.demonslayer.endCutscene
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietScenes
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * The quest's cutscenes. The sky scene plays where the player stands; the rest play in private
 * copies of their map squares (Juna's cave, the H.A.M. meeting room, the Dorgeshuun mine) through
 * [RomeoJulietScenes], so nobody else sees the actors. Each returns the player to where they
 * started, and none of them hold the quest back if the copy cannot be made.
 */
@Singleton
class DttdScenes
@Inject
constructor(
    private val dttd: DeathToTheDorgeshuunQuest,
    private val follower: ZanikFollower,
    private val scenes: RomeoJulietScenes,
    private val npcRepo: NpcRepository,
) {
    /** Zanik steps out of Lumbridge Castle and sees the sky for the first time. */
    suspend fun ProtectedAccess.sunrise(zanik: Npc) {
        player.dttdTourSun = true
        follower.waitHere(player)
        val spot = player.coords
        try {
            beginCutscene()
            camMoveTo(spot.translate(-4, -6), height = SKY_CAMERA_HEIGHT, rate = SKY_RATE, rate2 = SKY_RATE)
            camLookAt(spot.translate(2, 14), height = SKY_LOOK_HEIGHT, rate = SKY_RATE, rate2 = SKY_RATE)
            midiJingle(FIRST_SUNSHINE)
            zanik.anim(ZANIK_TURN_SEQ)
            startDialogue(zanik) {
                chatNpcNoTurn(happy, "So this is the surface!")
                chatNpcNoTurn(shocked, "What's that? The giant light?")
                chatPlayer(neutral, "That's the sun.")
                chatNpcNoTurn(shocked, "It's so bright! I can't look at it!")
                chatNpcNoTurn(
                    happy,
                    "But it's amazing to think that there's nothing above me. Just the sun and the air and " +
                        "empty space, forever...",
                )
                chatNpcNoTurn(
                    happy,
                    "For generations my people have lived with rock over our heads. But now I can see the sky!",
                )
            }
        } finally {
            endCutscene()
            follower.followAgain(player)
        }
    }

    /**
     * Zanik's memory of the day the Tears of Guthix marked her, told in Juna's cave; ends with the
     * two of them heading for the H.A.M. hideout.
     */
    suspend fun ProtectedAccess.markStory() {
        val origin = player.coords
        midiJingle(ZANIKS_THEME)
        inScene(MARK_KEY, TOG_PLAYER_TILE, origin) { visit -> playMarkStory(visit) }
        val zanik = follower.following(player)
        val finish: suspend Dialogue.() -> Unit = {
            chatNpc(
                neutral,
                "Ever since then I've wondered what the sign meant. But perhaps you will help me to find out, " +
                    "${player.displayName}!",
            )
            chatNpc(
                happy,
                "I've seen enough of Lumbridge now. Let's get to the HAM lair and see if they're up to anything!",
            )
        }
        if (zanik != null) startDialogue(zanik, conversation = finish) else startDialogue(finish)
        dttd.advanceTo(this, STAGE_HAM_HIDEOUT)
        if (follower.isFollowing(player)) {
            follower.spawn(player, ZANIK_FOLLOWER_HAM, at = zanik?.coords)
        }
    }

    private suspend fun ProtectedAccess.playMarkStory(visit: RomeoJulietScenes.Visit) {
        var zanik = scenes.spawn(visit, ZANIK_UNMARKED, ZANIK_STORY_TILE, Direction.East)
        camMoveTo(visit.at(STORY_CAMERA_FROM), height = STORY_CAMERA_HEIGHT, rate = FAST, rate2 = FAST)
        camLookAt(visit.at(ZANIK_STORY_TILE), height = LOOK_HEIGHT, rate = FAST, rate2 = FAST)
        delay(1)
        fadeFromBlack()
        startDialogue {
            mesbox(
                "It was about the same time the passage to the surface opened up. I was visiting Juna, the " +
                    "Story-Snake. She likes to hear of my adventures and she sometimes lets me drink the " +
                    "Tears of Guthix.",
            )
        }
        line(zanik) {
            chatNpcNoTurn(
                happy,
                "...And when they opened up the giant frog's stomach they found all the missing silver, and " +
                    "Mistag's brooch!",
            )
        }
        line { junaLine(neutral, "Your stories have entertained me. I will let you into the cave for a short time.") }
        line { mesbox("I collected the tears like I had before...") }
        npcRepo.del(zanik, Int.MAX_VALUE)
        zanik = scenes.spawn(visit, ZANIK_WITH_BOWL, ZANIK_STORY_TILE, Direction.East)
        walkAlong(zanik, listOf(visit.at(WEEPING_WALL_TILE)))
        zanik.lockFacingDirection(Direction.South)
        line(zanik) { chatNpcNoTurn(happy, "I hope I get ranging XP!") }
        zanik.anim(ZANIK_DRINK_SEQ)
        soundSynth(DRINK_SOUND)
        delay(DRINK_TICKS)
        val marked = scenes.spawn(visit, ZANIK_MARKED_GLOWING, WEEPING_WALL_TILE, Direction.West)
        npcRepo.del(zanik, Int.MAX_VALUE)
        marked.anim(ZANIK_BRANDED_SEQ)
        soundSynth(BRANDING_SOUND)
        line(marked) { chatNpcNoTurn(shocked, "Aaaah!") }
        soundSynth(BRANDING_2_SOUND)
        line(marked) { chatNpcNoTurn(shocked, "What -- what's happening?") }
        line { junaLine(neutral, "It is not normal for the Tears to have that effect.") }
        line(marked) { chatNpcNoTurn(worried, "I know! What happened?") }
        line { junaLine(neutral, "It is a sign. The power of the Tears has marked you for a special purpose.") }
        line {
            junaLine(
                neutral,
                "I cannot tell you more now. That mark will soon fade, but someday it will glow again, and on " +
                    "that day you should return to me so that I can tell you more about your destiny.",
            )
        }
        line(marked) { chatNpcNoTurn(shocked, "Wow...") }
    }

    /** Juna calls on the Tears and Zanik comes back to life. */
    suspend fun ProtectedAccess.revival() {
        dttd.advanceTo(this, STAGE_REVIVED)
        val zanik = Npc(ZANIK_MARKED_GLOWING, REVIVAL_TILE)
        zanik.mode = NpcMode.None
        zanik.moveRestrict = MoveRestrict.NoMove
        zanik.respawns = false
        npcRepo.add(zanik, REVIVAL_NPC_TICKS)
        try {
            beginCutscene()
            camMoveTo(REVIVAL_CAMERA_FROM, height = STORY_CAMERA_HEIGHT, rate = FAST, rate2 = FAST)
            camLookAt(REVIVAL_TILE, height = LOOK_HEIGHT, rate = FAST, rate2 = FAST)
            faceSquare(REVIVAL_TILE)
            startDialogue { junaLine(neutral, "Then I will call upon their power.") }
            anim(BOWL_MAGIC_SEQ)
            spotanim(BOWL_MAGIC_SPOTANIM)
            midiJingle(RESURRECTION_JINGLE)
            soundSynth(RESURRECTION_SOUND)
            zanik.anim(ZANIK_HOVER_SEQ)
            delay(HOVER_TICKS)
            zanik.anim(ZANIK_WAKE_SEQ)
            delay(WAKE_TICKS)
            zanik.facePlayer(player)
            startDialogue(zanik) {
                chatNpc(shocked, "Uuuhhh!")
                chatNpc(angry, "Get off me, you monsters!")
                chatNpc(shocked, "What are you doing? Noo! Don't kill me! ${player.displayName}, help me!")
                chatNpc(confused, "Oh!")
                chatNpc(sad, "They killed me, didn't they, ${player.displayName}?")
                chatPlayer(sad, "Yes.")
                chatNpc(confused, "Then what am I doing here?")
                chatPlayer(neutral, "Juna brought you back from the dead.")
                junaLine(
                    neutral,
                    "Listen to me, Zanik. You are marked for a special purpose, and you have been granted new " +
                        "life so that you can carry it out. Your destiny is to lead all goblins into a new age.",
                )
                chatNpc(confused, "But what does that mean?")
                junaLine(
                    neutral,
                    "Someday you will know. But today you must hurry to foil the plot that even now threatens " +
                        "your home!",
                )
                chatNpc(
                    shocked,
                    "The plot! ${player.displayName}, I've got to tell you what I heard outside the meeting room!",
                )
            }
        } finally {
            endCutscene()
        }
        val tile = zanik.coords
        if (zanik.isSlotAssigned) {
            npcRepo.del(zanik, Int.MAX_VALUE)
        }
        flashback()
        dttd.advanceTo(this, STAGE_MILL)
        follower.spawn(player, ZANIK_SHOWDOWN, at = tile)
    }

    /** What Zanik overheard at the storeroom doors: the plan to flood Dorgesh-Kaan. */
    suspend fun ProtectedAccess.flashback() {
        inScene(FLASHBACK_KEY, MEETING_LISTEN_TILE, player.coords) { visit -> playFlashback(visit) }
    }

    private suspend fun ProtectedAccess.playFlashback(visit: RomeoJulietScenes.Visit) {
        val zanik = scenes.spawn(visit, ZANIK_HAM, MEETING_ZANIK_TILE, Direction.North)
        val sigmund = scenes.spawn(visit, CUTSCENE_SIGMUND, SIGMUND_SEAT, Direction.South)
        val johanhus = scenes.spawn(visit, CUTSCENE_JOHANHUS, JOHANHUS_SEAT, Direction.West)
        val deacon = scenes.spawn(visit, CUTSCENE_DEACON, DEACON_SEAT, Direction.East)
        val member = scenes.spawn(visit, CUTSCENE_MEMBER, MEMBER_SEAT, Direction.East)
        faceDirection(Direction.North)
        anim(LISTEN_BEND_SEQ)
        zanik.anim(ZANIK_LISTEN_BEND_SEQ)
        camMoveTo(visit.at(MEETING_CAMERA_FROM), height = STORY_CAMERA_HEIGHT, rate = FAST, rate2 = FAST)
        camLookAt(visit.at(MEETING_CAMERA_AT), height = LOOK_HEIGHT, rate = FAST, rate2 = FAST)
        delay(1)
        fadeFromBlack()
        anim(LISTEN_IDLE_SEQ)
        zanik.anim(ZANIK_LISTEN_IDLE_SEQ)
        line { chatPlayer(neutral, "I can't hear anything.") }
        line(zanik) { chatNpcNoTurn(neutral, "Shh! I can hear them.") }
        line(sigmund) {
            chatNpcNoTurn(
                happy,
                "Good news, brothers! Our tunnel from the water mill cellar has reached the cave goblin " +
                    "tunnels, and the last shipment of machinery is on its way from Keldagrim.",
            )
        }
        line(johanhus) {
            chatNpcNoTurn(
                neutral,
                "Do we have to deal with the dwarves? They may not exactly be monsters but they're still not human.",
            )
        }
        line(member) {
            chatNpcNoTurn(
                angry,
                "If it wasn't for the King's tolerance of monsters, we humans would have a better industry " +
                    "than the dwarves!",
            )
        }
        line(deacon) { chatNpcNoTurn(happy, "Well said, brother!") }
        line(sigmund) {
            chatNpcNoTurn(
                neutral,
                "I'll need to take some of the low-level members to help unload the machinery at the " +
                    "Lumbridge water mill.",
            )
        }
        line(johanhus) { chatNpcNoTurn(neutral, "By all means. It's about time that lot made themselves useful.") }
        line(sigmund) { chatNpcNoTurn(happy, "Soon the mighty River Lum will wash away those evil goblins forever!") }
        line(deacon) { chatNpcNoTurn(happy, "Saradomin will surely bless us for destroying those abominations!") }
        for (npc in listOf(sigmund, johanhus, deacon, member)) {
            npc.anim(TOAST_SEQ)
            npc.say("Death to the Dorgeshuun!")
        }
        delay(TOAST_TICKS)
    }

    /** Zanik and the player come home to the mine as heroes; completes the quest. */
    suspend fun ProtectedAccess.homecoming() {
        inScene(HOMECOMING_KEY, HOMECOMING_PLAYER_TILE, MINES_ARRIVAL, title = homecomingTitle()) { visit ->
            playHomecoming(visit)
        }
        telejump(MINES_ARRIVAL, TeleportType.Exempt)
        follower.remove(player)
        dttd.complete(this)
        if (LostTribeCellar.LIGHT_SOURCES.none { it in player.inv || it in player.worn } && inv.hasFreeSpace()) {
            invAdd(inv, TORCH)
            startDialogue { objbox(TORCH, "Zanik gives you a torch so you can see.") }
        }
    }

    private fun ProtectedAccess.homecomingTitle(): String =
        "~ ${player.displayName} and Zanik return victorious to the Dorgeshuun mine ~"

    private suspend fun ProtectedAccess.playHomecoming(visit: RomeoJulietScenes.Visit) {
        val zanik = scenes.spawn(visit, ZANIK_PLAIN, HOMECOMING_ZANIK_TILE, Direction.East)
        val urtag = scenes.spawn(visit, LostTribeQuest.URTAG, HOMECOMING_URTAG_TILE, Direction.West)
        scenes.spawn(visit, CUTSCENE_MISTAG, HOMECOMING_MISTAG_TILE, Direction.West)
        faceDirection(Direction.East)
        camMoveTo(visit.at(HOMECOMING_CAMERA_FROM), height = STORY_CAMERA_HEIGHT, rate = FAST, rate2 = FAST)
        camLookAt(visit.at(HOMECOMING_URTAG_TILE), height = LOOK_HEIGHT, rate = FAST, rate2 = FAST)
        delay(1)
        fadeFromBlack()
        line(urtag) { chatNpcNoTurn(neutral, "Welcome back, Zanik. What have you to report?") }
        line(zanik) { chatNpcNoTurn(happy, "The city is safe! The HAM group was plotting against us, but we stopped them!") }
        line(urtag) {
            chatNpcNoTurn(
                neutral,
                "You stopped them this time, but the surface still poses a danger. The council has been " +
                    "debating how much contact we should have with it, and wants you to make a recommendation.",
            )
        }
        line(zanik) {
            chatNpcNoTurn(happy, "The surface is a strange and wonderful place! We should embrace it, not shut ourselves off!")
        }
        line(zanik) {
            chatNpcNoTurn(
                happy,
                "And not all surface-dwellers are bad. Without ${player.displayName} the city would have been " +
                    "destroyed for sure!",
            )
        }
        line(urtag) { chatNpcNoTurn(happy, "${player.displayName}, we are in your debt once again.") }
        line(urtag) {
            chatNpcNoTurn(
                happy,
                "The council has voted that, if Zanik's recommendation was positive, humans from Lumbridge " +
                    "should be allowed into the city. You may now enter Dorgesh-Kaan!",
            )
        }
        line(zanik) { chatNpcNoTurn(happy, "This is brilliant! I'm sure you won't regret this!") }
        line(zanik) {
            chatNpcNoTurn(
                happy,
                "Thank you for taking me on that adventure, ${player.displayName}! I'll show you how to use the " +
                    "bone crossbow like I promised I would!",
            )
        }
        zanik.anim(ZANIK_FIRE_SEQ)
        soundSynth(CROSSBOW_SPECIAL_SOUND)
        line { objbox(BONE_CROSSBOW, "Zanik shows you how to use the bone crossbow's special attack.") }
        line(zanik) {
            chatNpcNoTurn(
                happy,
                "Come find me in the city if you ever want to chat, ${player.displayName}. I'm going to go and " +
                    "think about what Juna said about my destiny. I don't know what it will involve, but I have " +
                    "a feeling we will adventure together again!",
            )
        }
    }

    /**
     * Fades out, puts the player in a private copy of [enter]'s map square, runs [scene] and brings
     * them back to [returnTo]; the scene is skipped when no copy can be made.
     */
    private suspend fun ProtectedAccess.inScene(
        key: String,
        enter: CoordGrid,
        returnTo: CoordGrid,
        title: String? = null,
        scene: suspend ProtectedAccess.(RomeoJulietScenes.Visit) -> Unit,
    ) {
        val zanik = follower.following(player)
        zanik?.let { follower.waitHere(player) }
        fadeToBlack()
        if (title != null) {
            ifSetText(FADE_MESSAGE, title)
        }
        val visit = with(scenes) { enterScene(key, enter, returnTo) }
        if (visit == null) {
            fadeFromBlack()
            closeFadeOverlay()
            zanik?.let { follower.followAgain(player) }
            return
        }
        try {
            beginCutscene()
            if (title != null) {
                delay(TITLE_TICKS)
            }
            scene(visit)
        } catch (e: Exception) {
            logger.error(e) { "Death to the Dorgeshuun scene $key failed for ${player.displayName}." }
        } finally {
            fadeToBlack()
            endCutscene()
            with(scenes) { leaveScene() }
            telejump(returnTo, TeleportType.Exempt)
            delay(1)
            fadeFromBlack()
            closeFadeOverlay()
        }
        if (follower.isFollowing(player)) {
            follower.relocate(player, returnTo)
            follower.followAgain(player)
        }
    }

    private suspend fun ProtectedAccess.walkAlong(npc: Npc, route: List<CoordGrid>) {
        npc.clearFacingLock()
        npc.movementLocked = false
        npc.moveRestrict = MoveRestrict.PassThru
        npc.mode = NpcMode.None
        npc.walk(route)
        val dest = route.last()
        for (tick in 0 until WALK_TIMEOUT) {
            if (npc.coords == dest) {
                break
            }
            delay(1)
        }
        npc.moveRestrict = MoveRestrict.NoMove
        npc.movementLocked = true
    }

    private suspend fun Dialogue.junaLine(mood: dev.openrune.types.MesAnimType, text: String) =
        chatNpcSpecific("Juna", JUNA_CHATHEAD, mood, text)

    private suspend fun ProtectedAccess.line(npc: Npc, block: suspend Dialogue.() -> Unit) {
        startDialogue(npc) { block() }
    }

    private suspend fun ProtectedAccess.line(block: suspend Dialogue.() -> Unit) {
        startDialogue { block() }
    }

    companion object {
        private val logger = InlineLogger()

        const val FADE_MESSAGE = "component.fade_overlay:message"
        const val MARK_KEY = "dttd_mark_story"
        const val FLASHBACK_KEY = "dttd_flashback"
        const val HOMECOMING_KEY = "dttd_homecoming"

        const val FIRST_SUNSHINE = "jingle.first_sunshine"
        const val ZANIKS_THEME = "jingle.zaniks_theme"
        const val RESURRECTION_JINGLE = "jingle.zaniks_resurrection"

        const val ZANIK_UNMARKED = "npc.dttd_zanik_unmarked"
        const val ZANIK_WITH_BOWL = "npc.dttd_zanik_with_tog_bowl"
        const val ZANIK_MARKED_GLOWING = "npc.dttd_zanik_marked_glowing"
        const val ZANIK_HAM = "npc.dttd_zanik_ham_robes"
        const val ZANIK_PLAIN = "npc.dorgesh_zanik_there"
        const val CUTSCENE_SIGMUND = "npc.dttd_cutscene_sigmund"
        const val CUTSCENE_JOHANHUS = "npc.dttd_cutscene_johanhus"
        const val CUTSCENE_DEACON = "npc.dttd_cutscene_deacon"
        const val CUTSCENE_MEMBER = "npc.dttd_cutscene_man"
        const val CUTSCENE_MISTAG = "npc.lost_tribe_cutscene_mistag"

        const val ZANIK_TURN_SEQ = "seq.dttd_zanik_turnonspot"
        const val ZANIK_DRINK_SEQ = "seq.dttd_zanik_drink_togbowl"
        const val ZANIK_BRANDED_SEQ = "seq.dttd_zanik_drunk_tog_in_pain"
        const val ZANIK_HOVER_SEQ = "seq.dttd_zanik_revival"
        const val ZANIK_WAKE_SEQ = "seq.dttd_zanik_return_to_life"
        const val ZANIK_LISTEN_BEND_SEQ = "seq.dttd_zanik_bend_to_listen_at_door"
        const val ZANIK_LISTEN_IDLE_SEQ = "seq.dttd_zanik_bent_to_listen_at_door_idle"
        const val ZANIK_FIRE_SEQ = "seq.dttd_zanik_firing_crossbow"
        const val LISTEN_BEND_SEQ = "seq.dttd_bend_to_listen_at_door"
        const val LISTEN_IDLE_SEQ = "seq.dttd_bent_to_listen_at_door_idle"
        const val TOAST_SEQ = "seq.dttd_ham_meeting_toasting"
        const val BOWL_MAGIC_SEQ = "seq.dttd_bowl_dropping_magic"
        const val BOWL_MAGIC_SPOTANIM = "spotanim.dttd_bowl_magic"

        const val DRINK_SOUND = "synth.dttd_drink"
        const val BRANDING_SOUND = "synth.dttd_branding_zanik"
        const val BRANDING_2_SOUND = "synth.dttd_branding_2"
        const val RESURRECTION_SOUND = "synth.dttd_zanik_resurrection"
        const val CROSSBOW_SPECIAL_SOUND = "synth.dttd_bone_crossbow_sa"

        const val SKY_CAMERA_HEIGHT = 300
        const val SKY_LOOK_HEIGHT = 2000
        const val SKY_RATE = 3
        const val STORY_CAMERA_HEIGHT = 600
        const val LOOK_HEIGHT = 150
        const val FAST = 100
        const val DRINK_TICKS = 3
        const val HOVER_TICKS = 4
        const val WAKE_TICKS = 3
        const val TOAST_TICKS = 3
        const val TITLE_TICKS = 4
        const val WALK_TIMEOUT = 12
        const val REVIVAL_NPC_TICKS = 200

        /** Juna's chamber in the Tears of Guthix cave; Juna lies coiled on 3252,9516. */
        val TOG_PLAYER_TILE = CoordGrid(3245, 9508, 2)
        val ZANIK_STORY_TILE = CoordGrid(3250, 9517, 2)
        val WEEPING_WALL_TILE = CoordGrid(3258, 9515, 2)
        val STORY_CAMERA_FROM = CoordGrid(3248, 9511, 2)
        val REVIVAL_TILE = CoordGrid(3251, 9515, 2)
        val REVIVAL_CAMERA_FROM = CoordGrid(3247, 9511, 2)

        /** Outside the double doors of the storeroom meeting room, and the table inside. */
        val MEETING_LISTEN_TILE = CoordGrid(2571, 5203, 0)
        val MEETING_ZANIK_TILE = CoordGrid(2572, 5203, 0)
        val SIGMUND_SEAT = CoordGrid(2571, 5214, 0)
        val JOHANHUS_SEAT = CoordGrid(2573, 5212, 0)
        val DEACON_SEAT = CoordGrid(2570, 5212, 0)
        val MEMBER_SEAT = CoordGrid(2570, 5211, 0)
        val MEETING_CAMERA_FROM = CoordGrid(2575, 5208, 0)
        val MEETING_CAMERA_AT = CoordGrid(2571, 5212, 0)

        /** In front of Mistag in the Dorgeshuun mine. */
        val MINES_ARRIVAL = LostTribeQuest.MINES_ARRIVAL
        val HOMECOMING_PLAYER_TILE = CoordGrid(3316, 9612, 0)
        val HOMECOMING_ZANIK_TILE = CoordGrid(3316, 9611, 0)
        val HOMECOMING_URTAG_TILE = CoordGrid(3319, 9612, 0)
        val HOMECOMING_MISTAG_TILE = CoordGrid(3319, 9614, 0)
        val HOMECOMING_CAMERA_FROM = CoordGrid(3313, 9608, 0)
    }
}
