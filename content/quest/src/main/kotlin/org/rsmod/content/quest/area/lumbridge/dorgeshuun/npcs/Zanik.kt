package org.rsmod.content.quest.area.lumbridge.dorgeshuun.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.HAM_SET
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_HAM_HIDEOUT
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_MET_ZANIK
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_MILL
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_REVIVED
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_STOREROOMS
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_TOUR
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_CELLAR
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_FOLLOWER
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_FOLLOWER_HAM
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_SHOWDOWN
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.inLumbridge
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DttdScenes
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.HamStorerooms
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.WaterMill
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.ZanikFollower
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.ZanikTour
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.dttdHamDeacon
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.dttdHamJohanhus
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.dttdTourSun
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Zanik of the Dorgeshuun: waiting by the ladder in the Lumbridge Castle cellar, and following the
 * player around Lumbridge, the H.A.M. hideout and the water mill.
 */
class Zanik
@Inject
constructor(
    private val dttd: DeathToTheDorgeshuunQuest,
    private val follower: ZanikFollower,
    private val tour: ZanikTour,
    private val scenes: DttdScenes,
    private val storerooms: HamStorerooms,
    private val mill: WaterMill,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ZANIK_CELLAR) { cellar(it.npc) }
        onOpNpc1(ZANIK_FOLLOWER) { followerTalk(it.npc) }
        onOpNpc1(ZANIK_FOLLOWER_HAM) { followerTalk(it.npc) }
        onOpNpc1(ZANIK_SHOWDOWN) { followerTalk(it.npc) }
    }

    private suspend fun ProtectedAccess.cellar(npc: Npc) {
        when (dttd.stage(player)) {
            STAGE_STARTED -> startDialogue(npc) { firstMeeting() }
            STAGE_MET_ZANIK -> startDialogue(npc) { disguises(npc) }
            STAGE_TOUR ->
                startDialogue(npc) {
                    chatNpc(happy, "Are you ready to finish our tour now?")
                    readyToGo()
                }
            STAGE_HAM_HIDEOUT,
            STAGE_STOREROOMS ->
                startDialogue(npc) {
                    chatNpc(quiz, "Are you ready to go to the HAM cave now?")
                    readyToGo()
                }
            STAGE_MILL ->
                startDialogue(npc) {
                    chatNpc(
                        angry,
                        "Quick, ${player.displayName}! Let's get to the water mill and stop Sigmund from " +
                            "destroying my home!",
                    )
                    if (choice2("Let's go!", true, "Not now.", false)) {
                        chatPlayer(happy, "Let's go!")
                        chatNpc(happy, "Okay!")
                        bringOut()
                    } else {
                        chatPlayer(neutral, "Not now.")
                        chatNpc(sad, "Oh well.")
                    }
                }
        }
    }

    private suspend fun Dialogue.readyToGo() {
        if (choice2("Yes.", true, "Not now.", false)) {
            chatPlayer(happy, "Yes.")
            chatNpc(happy, "Well then, let's go!")
            bringOut()
        } else {
            chatPlayer(neutral, "Not now.")
            chatNpc(sad, "Oh well.")
        }
    }

    /** Zanik comes out of the cellar to follow the player, unless a pet is already in the way. */
    private suspend fun Dialogue.bringOut(): Boolean {
        if (follower.hasOtherFollower(player)) {
            chatNpc(neutral, "You should pick up your pet thingy. It would only get in the way.")
            return false
        }
        follower.spawn(player)
        return true
    }

    private suspend fun Dialogue.firstMeeting() {
        chatNpc(happy, "You must be the famous ${player.displayName}!")
        val yes =
            choice2(
                "Yes, I'm ${player.displayName}!",
                true,
                "No, I'm not ${player.displayName}.",
                false,
            )
        if (!yes) {
            chatPlayer(neutral, "No, I'm not ${player.displayName}.")
            chatNpc(sad, "Oh well.")
            return
        }
        chatPlayer(happy, "Yes, I'm ${player.displayName}!")
        chatNpc(happy, "Pleased to meet you! I'm Zanik of the Dorgeshuun!")
        chatNpc(happy, "I can't wait to see the surface! But first you need to get us both sets of HAM robes.")
        dttd.advanceTo(access, STAGE_MET_ZANIK)
        disguiseQuestions(allowOk = false)
    }

    private suspend fun Dialogue.disguises(npc: Npc) {
        chatNpc(quiz, "Hi ${player.displayName}! Have you got our disguises yet?")
        if (dttd.hamSets(player) >= 2) {
            chatPlayer(happy, "Yes, I have two sets of robes!")
            if (follower.hasOtherFollower(player)) {
                chatNpc(neutral, "You should pick up your pet thingy. It would only get in the way.")
                return
            }
            if (!takeRobes()) {
                return
            }
            chatNpc(happy, "All right, let's go! I can't wait to see all around Lumbridge!")
            mesbox("Zanik takes a set of H.A.M. robes from you.")
            dttd.advanceTo(access, STAGE_TOUR)
            follower.spawn(player)
            return
        }
        val topic =
            menu(
                "Not yet." to 0,
                "Where do I get the robes?" to 1,
                "Aren't you scared of the surface?" to 2,
                "What's that mark on your forehead?" to 3,
            )
        if (topic == 0) {
            chatPlayer(neutral, "Not yet.")
            chatNpc(
                sad,
                "I'm so looking forward to seeing the surface. But the Council insists that I get the " +
                    "disguise first.",
            )
            return
        }
        disguiseTopic(topic)
        disguiseQuestions(allowOk = true)
    }

    private suspend fun Dialogue.disguiseQuestions(allowOk: Boolean) {
        var asked = allowOk
        while (true) {
            val options = buildList {
                add("Where do I get the robes?" to 1)
                add("Aren't you scared of the surface?" to 2)
                add("What's that mark on your forehead?" to 3)
                if (asked) {
                    add("OK" to 0)
                }
            }
            val topic = menu(options)
            if (topic == 0) {
                chatPlayer(neutral, "OK.")
                chatNpc(happy, "See you soon!")
                return
            }
            disguiseTopic(topic)
            asked = true
        }
    }

    private suspend fun Dialogue.disguiseTopic(topic: Int) {
        when (topic) {
            1 -> {
                chatPlayer(quiz, "Where do I get the robes?")
                chatNpc(
                    laugh,
                    "How should I know? You're the surface-dweller! Maybe you could steal them from the " +
                        "HAM people or something.",
                )
                chatNpc(
                    neutral,
                    "We'll need two sets of full robes: shirt, robe, hood, cloak, gloves, boots and badge.",
                )
            }
            2 -> {
                chatPlayer(quiz, "Aren't you scared of the surface?")
                chatNpc(worried, "Well... a bit. All our legends say it's a terrible place.")
                chatNpc(
                    neutral,
                    "But that's a bad reason not to visit it. Maybe the legends are wrong. Or even if " +
                        "they're right, we should know for sure.",
                )
                chatNpc(
                    happy,
                    "And I've always wondered... what's beyond the next cave? What's at the end of every " +
                        "tunnel? The surface is a whole new world to explore, and I can't wait!",
                )
            }
            3 -> {
                chatPlayer(quiz, "What's that mark on your forehead?")
                chatNpc(shocked, "I, er, er...")
                chatNpc(worried, "To be honest I don't know. It happened when...")
                chatNpc(
                    sad,
                    "If you don't mind, I'd rather not talk about it now. Maybe once you've shown me some " +
                        "of the surface.",
                )
            }
        }
    }

    /** Zanik takes one of the two outfits, from the pack first so the player keeps what they wear. */
    private fun Dialogue.takeRobes(): Boolean {
        for (piece in HAM_SET) {
            val carried = player.inv.count(piece)
            val worn = if (piece in player.worn) 1 else 0
            if (carried + worn < 2 || carried == 0) {
                return false
            }
        }
        return HAM_SET.all { access.invDel(access.inv, it).success }
    }

    private suspend fun ProtectedAccess.followerTalk(npc: Npc) {
        if (!follower.isFollowing(player) || follower.following(player) !== npc) {
            return
        }
        val stage = dttd.stage(player)
        when {
            stage == STAGE_TOUR -> startDialogue(npc) { touring(npc) }
            stage in STAGE_HAM_HIDEOUT..STAGE_STOREROOMS && storerooms.isInside(player) ->
                with(storerooms) { talkToZanik(npc) }
            stage in STAGE_HAM_HIDEOUT..STAGE_STOREROOMS -> startDialogue(npc) { hamStage() }
            stage >= STAGE_REVIVED -> with(mill) { talkToZanik(npc) }
        }
    }

    private suspend fun Dialogue.touring(npc: Npc) {
        if (!player.dttdTourSun && tour.inCastle(player.coords)) {
            chatNpc(confused, "Where are we? I thought that ladder led to the surface.")
            chatPlayer(happy, "We are on the surface! We're in Lumbridge Castle!")
            chatNpc(
                happy,
                "It's so enclosed, it could almost be underground. Let's go outside, " +
                    "${player.displayName}! I want to see the sun!",
            )
            when (
                menu(
                    "I need to do something else for a bit." to 0,
                    "Aren't you going to put on your disguise?" to 1,
                    "Will you tell me about the mark on your forehead?" to 2,
                    "Follow me!" to 3,
                )
            ) {
                0 -> somethingElse()
                1 -> {
                    chatPlayer(quiz, "Aren't you going to put on your disguise?")
                    chatNpc(
                        happy,
                        "There's no need for that yet. Why don't you show me around Lumbridge first? I'm so " +
                            "excited about visiting the surface!",
                    )
                }
                2 -> notYet()
                3 -> {
                    chatPlayer(happy, "Follow me!")
                    chatNpc(happy, "Okay!")
                    follower.followAgain(player)
                }
            }
            return
        }
        with(tour) { remark() }
        when (
            menu(
                "I need to do something else for a bit." to 0,
                "Have you seen enough of Lumbridge yet?" to 1,
                "Will you tell me about the mark on your forehead?" to 2,
            )
        ) {
            0 -> somethingElse()
            1 -> {
                chatPlayer(quiz, "Have you seen enough of Lumbridge yet?")
                if (with(tour) { stillToSee() }) {
                    return
                }
                chatNpc(
                    happy,
                    "I think I've seen enough of Lumbridge now. If you like I'll tell you about the mark on " +
                        "my forehead.",
                )
                if (choice2("Yes please!", true, "Actually I'm not interested!", false)) {
                    chatPlayer(happy, "Yes please!")
                    tellStory()
                } else {
                    chatPlayer(neutral, "Actually I'm not interested!")
                    chatNpc(sad, "You don't care about me at all!")
                }
            }
            2 -> {
                chatPlayer(quiz, "Will you tell me about the mark on your forehead?")
                when {
                    !dttd.hasToured(player) -> notYet()
                    !inLumbridge(player.coords) ->
                        chatNpc(neutral, "All right. But not here. Let's go back into Lumbridge town.")
                    else -> tellStory()
                }
            }
        }
    }

    private suspend fun Dialogue.notYet() {
        chatNpc(shocked, "I, er...")
        chatNpc(
            neutral,
            "I will tell you, ${player.displayName}, but not just yet. Finish showing me around Lumbridge " +
                "and then I'll tell you.",
        )
    }

    private suspend fun Dialogue.tellStory() {
        with(scenes) { access.markStory() }
    }

    private suspend fun Dialogue.somethingElse() {
        chatPlayer(neutral, "I need to do something else for a bit.")
        chatNpc(neutral, "All right. I'll wait for you in the castle cellar.")
        follower.sendHome(player)
    }

    private suspend fun Dialogue.hamStage() {
        val hideout = tour.inHamHideout(player.coords)
        when {
            !dttd.wearsHamSet(player) -> {
                chatNpc(
                    angry,
                    "Aren't you going to put on your HAM disguise? You might be able to do without one but " +
                        "I don't want you giving me away!",
                )
                when (
                    menu(
                        "I need to do something else for a bit." to 0,
                        "Okay." to 1,
                        "I've lost my HAM disguise!" to 2,
                    )
                ) {
                    0 -> somethingElse()
                    1 -> chatPlayer(neutral, "Okay.")
                    2 -> {
                        chatPlayer(worried, "I've lost my HAM disguise!")
                        chatNpc(neutral, "You'll have to find a new one from somewhere then.")
                    }
                }
            }
            hideout && !(player.dttdHamJohanhus && player.dttdHamDeacon) -> {
                chatNpc(
                    neutral,
                    "So this is the HAM hideout. It's so squalid and dirty! I don't know how they can bear " +
                        "to live here.",
                )
                hideoutMenu(
                    "We ought to talk to some of the HAM people. That's what we're here for, after all!",
                )
            }
            hideout -> {
                chatNpc(neutral, "Let's have another look around the main area. Maybe there's something we missed.")
                hideoutMenu(
                    "Not yet. There must be another area where the HAM people make their plans. We should " +
                        "try to find it.",
                )
            }
            else -> {
                chatNpc(
                    happy,
                    "What are we waiting for? Let's get to the HAM hideout and find out what they're planning!",
                )
                if (choice2("I need to do something else for a bit.", false, "Okay.", true)) {
                    chatPlayer(neutral, "Okay.")
                } else {
                    somethingElse()
                }
            }
        }
    }

    private suspend fun Dialogue.hideoutMenu(answer: String) {
        if (choice2("I need to do something else for a bit.", false, "Have you seen enough?", true)) {
            chatPlayer(quiz, "Have you seen enough?")
            chatNpc(neutral, answer)
        } else {
            somethingElse()
        }
    }
}
