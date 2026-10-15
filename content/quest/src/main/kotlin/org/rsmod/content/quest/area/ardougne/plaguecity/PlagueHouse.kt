package org.rsmod.content.quest.area.ardougne.plaguecity

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.SMALL_KEY
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_FREED_ELENA
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GOT_WARRANT
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_MOURNER_REFUSED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_SNEAKED_IN
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_TALKED_MILLI
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_UNLOCKED_CELL
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.WARRANT
import org.rsmod.content.quest.area.ardougne.plaguecity.npcs.westArdougneMourner
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The boarded-up plague house in the south-east corner of West Ardougne where the mourners hold
 * Elena. Two mourners guard the black-crossed doors; the cell key is in a barrel by the stairs
 * and Elena waits behind the prison door in the basement.
 *
 * The doors are `varbit.mourning_mourner_vis` multilocs (the cross comes off in a later quest),
 * so the ops are registered on the base type and both visible forms.
 */
class PlagueHouse
@Inject
constructor(
    private val plagueCity: PlagueCityQuest,
    private val doors: QuestDoors,
    private val search: NpcSearch,
) : PluginScript() {

    private val quest
        get() = plagueCity.quest

    override fun ScriptContext.startup() {
        for (door in FRONT_DOORS) {
            onOpLoc1(door) { frontDoor(it.loc) }
        }
        onOpNpc1(GUARD) { startDialogue(it.npc) { guard() } }
        onOpNpcU(GUARD) { useOnGuard(it.npc, it.objType.internalName) }
        onOpLoc1(BARREL) { searchBarrel() }
        onOpLoc1(STAIRS_DOWN) { stairs(BASEMENT_ARRIVAL) }
        onOpLoc1(STAIRS_UP) { stairs(GROUND_ARRIVAL) }
        onOpLoc1(CELL_DOOR) { cellDoor(it.loc) }
        onOpNpc1(ELENA) { startDialogue(it.npc) { elena() } }
    }

    private suspend fun ProtectedAccess.frontDoor(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        val inside = player.coords.z < door.coords.z
        val stage = plagueCity.stage(player)
        if (inside || stage >= STAGE_SNEAKED_IN) {
            doors.open(this, door, FRONT_DOOR_OPEN)
            return
        }
        mesbox("The door won't open. You notice a black cross on the door.")
        when {
            stage == STAGE_GOT_WARRANT && inv.contains(WARRANT) -> {
                startDialogue {
                    chatNpcSpecific("Mourner", GUARD_HEAD, neutral, "I'd stand away from there. That black cross means that house has been touched by the plague.")
                    warrant()
                }
                sneakInside(door)
            }
            stage == STAGE_TALKED_MILLI ->
                startDialogue {
                    chatNpcSpecific("Mourner", GUARD_HEAD, neutral, "I'd stand away from there. That black cross means that house has been touched by the plague.")
                    when (
                        choice3(
                            "But I think a kidnap victim is in here.", 1,
                            "I fear not a mere plague.", 2,
                            "Thanks for the warning.", 3,
                        )
                    ) {
                        1 -> {
                            chatPlayer(neutral, "But I think a kidnap victim is in here.")
                            chatNpcSpecific("Mourner", GUARD_HEAD, neutral, "Sounds unlikely, even kidnappers wouldn't go in there. Even if someone is in there, they're probably dead by now.")
                            when (
                                choice2(
                                    "Good point.", 1,
                                    "I want to check anyway.", 2,
                                )
                            ) {
                                1 -> chatPlayer(neutral, "Good point.")
                                2 -> {
                                    chatPlayer(neutral, "I want to check anyway.")
                                    chatNpcSpecific("Mourner", GUARD_HEAD, neutral, "You don't have clearance to go in there.")
                                    clearance()
                                }
                            }
                        }
                        2 -> {
                            chatPlayer(neutral, "I fear not a mere plague.")
                            chatNpcSpecific("Mourner", GUARD_HEAD, neutral, "That's irrelevant. You don't have clearance to go in there.")
                            clearance()
                        }
                        3 -> chatPlayer(neutral, "Thanks for the warning.")
                    }
                }
            else ->
                startDialogue {
                    chatNpcSpecific("Mourner", GUARD_HEAD, neutral, "I'd stand away from there. That black cross means that house has been touched by the plague.")
                }
        }
    }

    private suspend fun Dialogue.clearance() {
        chatPlayer(quiz, "How do I get clearance?")
        chatNpcSpecific("Mourner", GUARD_HEAD, neutral, "Well you'd need to apply to the head mourner, or I suppose Bravek the city warder.")
        chatNpcSpecific("Mourner", GUARD_HEAD, neutral, "I wouldn't get your hopes up though.")
        plagueCity.advanceTo(access, STAGE_MOURNER_REFUSED)
    }

    /** The guards argue about the warrant for long enough to slip past. */
    private suspend fun Dialogue.warrant() {
        chatPlayer(neutral, "I have a warrant from Bravek to enter here.")
        chatNpcSpecific("Mourner", GUARD_HEAD, confused, "This is highly irregular. Please wait...")
        val guards = search.findAll(GUARD_POST, GUARD, GUARD_RADIUS, HuntVis.Off).take(2).toList()
        val first = guards.getOrNull(0)
        val second = guards.getOrNull(1)
        if (first != null) {
            first.say("Hey... I've got someone here with a warrant from Bravek, what should we do?")
        } else {
            chatNpcSpecific("Mourner", GUARD_HEAD, confused, "Hey... I've got someone here with a warrant from Bravek, what should we do?")
        }
        delay(2)
        if (second != null) {
            second.say("Well you can't let them in...")
        } else {
            chatNpcSpecific("Mourner", GUARD_HEAD, neutral, "Well you can't let them in...")
        }
        delay(2)
    }

    private suspend fun ProtectedAccess.sneakInside(door: BoundLocInfo) {
        mesbox("You wait until the mourner's back is turned and sneak into the building.")
        plagueCity.advanceTo(this, STAGE_SNEAKED_IN)
        doors.open(this, door, FRONT_DOOR_OPEN)
        delay(1)
        telejump(door.coords.translateZ(-1))
    }

    private suspend fun Dialogue.guard() {
        val stage = plagueCity.stage(player)
        if (stage == STAGE_GOT_WARRANT && player.inv.contains(WARRANT)) {
            warrant()
            access.sneakInside(access.nearestDoor())
            return
        }
        westArdougneMourner(plagueCity)
    }

    private suspend fun ProtectedAccess.useOnGuard(npc: Npc, obj: String) {
        arriveDelay()
        faceEntitySquare(npc)
        if (obj != WARRANT) {
            mes("The mourner isn't interested in that.")
            return
        }
        startDialogue(npc) { warrant() }
        sneakInside(nearestDoor())
    }

    private fun ProtectedAccess.nearestDoor(): BoundLocInfo {
        val target = DOOR_TILES.minByOrNull { it.chebyshevDistance(player.coords) } ?: DOOR_TILES.first()
        val info = doors.find(target, FRONT_DOOR_BASE)
        return if (info != null) BoundLocInfo(info, 1, 1, 0) else BoundLocInfo(doors.find(target, FRONT_DOORS[1]) ?: error("Plague house door missing at $target"), 1, 1, 0)
    }

    private suspend fun ProtectedAccess.searchBarrel() {
        arriveDelay()
        val stage = plagueCity.stage(player)
        if (stage in STAGE_SNEAKED_IN until STAGE_UNLOCKED_CELL && !inv.contains(SMALL_KEY)) {
            if (inv.isFull()) {
                objbox(SMALL_KEY, "You find a small key in the barrel, but you don't have room to take it.")
                return
            }
            anim(SEARCH_SEQ)
            invAdd(inv, SMALL_KEY)
            objbox(SMALL_KEY, "You find a small key in the barrel.")
            return
        }
        anim(SEARCH_SEQ)
        mes("You search the barrel but find nothing of interest.")
    }

    private suspend fun ProtectedAccess.stairs(dest: CoordGrid) {
        arriveDelay()
        delay(1)
        telejump(dest)
    }

    private suspend fun ProtectedAccess.cellDoor(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        val stage = plagueCity.stage(player)
        when {
            stage >= STAGE_UNLOCKED_CELL -> doors.open(this, door, CELL_DOOR_OPEN)
            inv.contains(SMALL_KEY) -> {
                soundSynth(UNLOCK_SOUND)
                mesbox("You unlock the door.")
                plagueCity.advanceTo(this, STAGE_UNLOCKED_CELL)
                doors.open(this, door, CELL_DOOR_OPEN)
            }
            else -> {
                mesbox("The door is locked.")
                startDialogue { lockedIn() }
            }
        }
    }

    private suspend fun Dialogue.lockedIn() {
        chatNpcSpecific("Elena", ELENA_HEAD, worried, "Hey get me out of here please!")
        chatPlayer(sad, "I would do but I don't have a key.")
        chatNpcSpecific("Elena", ELENA_HEAD, neutral, "I think there may be one around somewhere. I'm sure I heard them stashing it somewhere.")
        if (!plagueCity.keyAsked.get(player)) {
            plagueCity.keyAsked.set(player, true)
            plagueCity.syncVars(player)
        }
        when (
            choice2(
                "Have you caught the plague?", 1,
                "Okay, I'll look for it.", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Have you caught the plague?")
                chatNpcSpecific("Elena", ELENA_HEAD, neutral, "No, I have none of the symptoms.")
                chatPlayer(confused, "Strange, I was told this house was plague infected.")
                chatNpcSpecific("Elena", ELENA_HEAD, neutral, "I suppose that was a cover up by the kidnappers.")
            }
            2 -> chatPlayer(neutral, "Okay, I'll look for it.")
        }
    }

    private suspend fun Dialogue.elena() {
        if (plagueCity.stage(player) < STAGE_UNLOCKED_CELL) {
            lockedIn()
            return
        }
        chatPlayer(happy, "Hi, you're free to go! Your kidnappers don't seem to be about right now.")
        chatNpc(happy, "Thank you, being kidnapped was so inconvenient. I was on my way back to East Ardougne with some samples, I want to see if I can diagnose a cure for this plague.")
        chatPlayer(neutral, "Well you can leave via the manhole in the middle of the city.")
        chatNpc(happy, "Go and see my father, I'll make sure he adequately rewards you. Now I'd better leave while I still can.")
        // Elena's cell form is a varp-multi npc: raising the stage hides her, and her own house
        // (and Edmond, back in his garden) show up instead.
        plagueCity.elenaHome.set(player, true)
        plagueCity.edmondBelow.set(player, false)
        plagueCity.syncVars(player)
        plagueCity.advanceTo(access, STAGE_FREED_ELENA)
    }

    companion object {
        const val FRONT_DOOR_BASE = "loc.plagueelenadoorshut"
        val FRONT_DOORS = listOf(FRONT_DOOR_BASE, "loc.plagueelenadoorshut_cross_vis", "loc.plagueelenadoorshut_vis")
        const val FRONT_DOOR_OPEN = "loc.plagueelenadooropen"
        const val BARREL = "loc.plaguekeybarrel"
        const val STAIRS_DOWN = "loc.plaguehousestairsdown"
        const val STAIRS_UP = "loc.plaguehousestairsup"
        const val CELL_DOOR = "loc.elenagateshut"
        const val CELL_DOOR_OPEN = "loc.elenagateopen"

        /** The two mourners outside; multi-npcs whose visible form carries the chat head. */
        const val GUARD = "npc.mourner_elena_guard"
        const val GUARD_HEAD = "npc.mourner_elena_guard_vis"

        /** Elena in her cell; a varp-multi npc hidden once she has been freed. */
        const val ELENA = "npc.elenap"
        const val ELENA_HEAD = "npc.elenap_vis"

        val DOOR_TILES = listOf(CoordGrid(2533, 3272, 0), CoordGrid(2540, 3273, 0))
        val GUARD_POST = CoordGrid(2536, 3274, 0)
        const val GUARD_RADIUS = 8

        val BASEMENT_ARRIVAL = CoordGrid(2538, 9672, 0)
        val GROUND_ARRIVAL = CoordGrid(2538, 3269, 0)

        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val UNLOCK_SOUND = "synth.unlock"
    }
}
