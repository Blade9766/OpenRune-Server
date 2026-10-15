package org.rsmod.content.quest.area.karamja.piratestreasure

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.GROCERY_CRATE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.KARAMJA_RUM
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.WHITE_APRON
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.WYDIN
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.WYDIN_DOOR
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.WYDIN_DOOR_OPEN
import org.rsmod.content.quest.area.varrock.dragonslayer.DoorPassage
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Wydin's food store in Port Sarim: only staff wearing a white apron may use the door to the back
 * room, where Luthas's banana crates are unpacked. Wydin's own Talk-to lives with the Port Sarim
 * shopkeepers, which call [wydinEmployee], [wydinJobOption] and [askWydinForJob].
 */
class WydinsStore
@Inject
constructor(
    private val treasure: PiratesTreasureQuest,
    private val doors: DoorPassage,
    private val npcs: NpcList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(WYDIN_DOOR) { door(it.loc) }
        onOpLoc1(GROCERY_CRATE) { searchCrate() }
    }

    private suspend fun ProtectedAccess.door(door: BoundLocInfo) {
        if (player.coords.x < BACK_ROOM_EAST_EDGE || mayEnterBackRoom()) {
            doors.walkThrough(this, door, WYDIN_DOOR_OPEN)
            return
        }
        val wydin = findWydin()
        if (wydin == null) {
            mes("The door is locked.")
            return
        }
        startDialogue(wydin) { stopAtDoor(treasure) }
    }

    private fun ProtectedAccess.mayEnterBackRoom(): Boolean =
        player.ptWydinJob && worn.contains(WHITE_APRON)

    private fun ProtectedAccess.findWydin(): Npc? =
        npcs.firstOrNull {
            it != null &&
                it.isType(WYDIN) &&
                it.coords.level == player.coords.level &&
                it.coords.chebyshevDistance(player.coords) <= WYDIN_SEARCH_RADIUS
        }

    private suspend fun ProtectedAccess.searchCrate() {
        mes("There are a lot of bananas in the crate.")
        if (!player.ptRumShipped || treasure.stage(player) != STAGE_STARTED) {
            return
        }
        if (invAdd(inv, KARAMJA_RUM).failure) {
            mes("You find your bottle of rum, but you have no room to carry it.")
            return
        }
        player.ptRumShipped = false
        mes("You find your bottle of rum in amongst the bananas.")
    }

    private companion object {
        /** The door sits on the west edge of this column; the back room lies to the west. */
        const val BACK_ROOM_EAST_EDGE = 3012
        const val WYDIN_SEARCH_RADIUS = 10
    }
}

/** "Can I get a job here?" while Frank is still waiting on his rum and Wydin hasn't hired them. */
fun wydinJobOption(treasure: PiratesTreasureQuest, player: Player): String? =
    if (treasure.stage(player) == STAGE_STARTED && !player.ptWydinJob) {
        "Can I get a job here?"
    } else {
        null
    }

/** Wydin's greeting once he has hired the player; returns false when he hasn't. */
suspend fun Dialogue.wydinEmployee(openShop: suspend Dialogue.() -> Unit): Boolean {
    if (!player.ptWydinJob) {
        return false
    }
    chatNpc(quiz, "Is it nice and tidy round the back now?")
    val topic =
        choice4(
            "Yes, can I work out front now?",
            1,
            "Yes, are you going to pay me yet?",
            2,
            "No, it's a complete mess",
            3,
            "Can I buy something please?",
            4,
        )
    when (topic) {
        1 -> {
            chatPlayer(quiz, "Yes, can I work out front now?")
            chatNpc(neutral, "No, I'm the one who works here.")
        }
        2 -> {
            chatPlayer(quiz, "Yes, are you going to pay me yet?")
            chatNpc(shifty, "Umm... No, not yet.")
        }
        3 -> {
            chatPlayer(sad, "No, it's a complete mess.")
            chatNpc(happy, "Ah well, it'll give you something to do, won't it.")
        }
        else -> {
            chatPlayer(quiz, "Can I buy something please?")
            chatNpc(happy, "Yes, of course.")
            openShop()
        }
    }
    return true
}

suspend fun Dialogue.askWydinForJob(prompt: String = "Can I get a job here?") {
    chatPlayer(quiz, prompt)
    chatNpc(
        happy,
        "Well, you're keen, I'll give you that. Okay, I'll give you a go. Have you got your own " +
            "white apron?",
    )
    val worn = access.worn.contains(WHITE_APRON)
    if (!worn && !access.inv.contains(WHITE_APRON)) {
        chatPlayer(sad, "No, I haven't.")
        chatNpc(
            neutral,
            "Well, you can't work here unless you have a white apron. Health and safety " +
                "regulations, you understand.",
        )
        chatPlayer(quiz, "Where can I get one of those?")
        chatNpc(
            neutral,
            "Well, I get all of mine over at the clothing shop in Varrock. They sell them cheap " +
                "there.",
        )
        chatNpc(
            neutral,
            "Oh, and I'm sure that I've seen a spare one over in Gerrant's fish store somewhere. " +
                "It's the little place just north of here.",
        )
        return
    }
    chatPlayer(happy, "Yes, I have one right here.")
    player.ptWydinJob = true
    chatNpc(
        happy,
        "Wow - you are well prepared! You're hired. Go through to the back and tidy up for me, " +
            "please.",
    )
    if (!worn) {
        chatNpc(neutral, "You need to put your white apron on first though.")
    }
}

private suspend fun Dialogue.stopAtDoor(treasure: PiratesTreasureQuest) {
    if (player.ptWydinJob) {
        chatNpc(neutral, "Can you put your white apron on before going in there, please?")
        return
    }
    chatNpc(
        angry,
        "Hey, you can't go in there. Only employees of the grocery store can go in.",
    )
    if (wydinJobOption(treasure, player) == null) {
        return
    }
    if (choice2("Well, can I get a job here?", true, "Sorry, I didn't realise.", false)) {
        askWydinForJob("Well, can I get a job here?")
    } else {
        chatPlayer(sad, "Sorry, I didn't realise.")
    }
}
