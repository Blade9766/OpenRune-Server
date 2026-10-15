package org.rsmod.content.quest.area.tirannwn.mourningsend

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BROKEN_DEVICE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.FIXED_DEVICE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.GNOME_KEY
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_ADMITTED
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Mourner Headquarters for a player in disguise: the shared rules the Biohazard Headquarters
 * script asks, and the basement under the trapdoor.
 *
 * Only the six disguise pieces are checked; anything else may be worn or carried. Who gets in
 * depends on the player's own progress, never on a door changing for everyone: the front door
 * and the trapdoor open for a disguised player from Arianwyn's briefing onwards (or once the
 * quest policy counts Part I as done). Below, the door to the gnome's cell needs Essyllt's
 * tarnished key from the outside, and the west door into the mines the new key Essyllt hands out
 * in Mourning's End Part II (from the mines side it opens freely); the office door opens freely.
 * The chest in Essyllt's office hands a broken device to anyone admitted who owns neither form of
 * the device, and his desk holds spare keys of both kinds.
 */
@Singleton
class MournerHideout
@Inject
constructor(
    private val mourning: MourningsEndQuest,
    private val doors: QuestDoors,
    private val locRepo: LocRepository,
    private val temple: MourningsEndPart2Quest,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(LADDER_UP) { climbUp() }
        onOpLoc1(CELL_DOOR) { cellDoor(it.loc) }
        onOpLoc1(OFFICE_DOOR) { doors.open(this, it.loc, OFFICE_DOOR_OPEN) }
        onOpLoc1(BACK_ROOM_DOOR) { minesDoor(it.loc) }
        onOpLoc1(OFFICE_CHEST) { openChest(it.loc) }
        onOpLoc1(OFFICE_CHEST_OPEN) { searchChest() }
        onOpLoc2(OFFICE_CHEST_OPEN) { shutChest(it.loc) }
        for (desk in DESKS) {
            onOpLoc1(desk) { searchDesk() }
        }
    }

    /** Whether [player] can pass the front door or trapdoor as a mourner. */
    fun admits(player: Player): Boolean = player.wearsDisguise() && mourning.mayUseBasement(player)

    suspend fun ProtectedAccess.trapdoor() {
        arriveDelay()
        if (!admits(player)) {
            mes("The trapdoor is bolted on the other side.")
            return
        }
        anim(CLIMB_DOWN_SEQ)
        delay(1)
        telejump(BASEMENT_ARRIVAL)
    }

    private suspend fun ProtectedAccess.climbUp() {
        arriveDelay()
        anim(CLIMB_UP_SEQ)
        delay(1)
        telejump(TRAPDOOR_ARRIVAL)
    }

    private suspend fun ProtectedAccess.cellDoor(door: BoundLocInfo) {
        arriveDelay()
        val inside = coords.z < door.coords.z
        if (!inside && !inv.contains(GNOME_KEY)) {
            mes("The door is locked.")
            return
        }
        if (!inside) {
            mes("You unlock the door with the tarnished key.")
        }
        doors.open(this, door, CELL_DOOR_OPEN)
    }

    private suspend fun ProtectedAccess.minesDoor(door: BoundLocInfo) {
        arriveDelay()
        val inside = coords.x >= door.coords.x
        if (inside && !inv.contains(MourningsEndPart2Quest.NEW_KEY)) {
            mes("The door is locked.")
            return
        }
        if (inside) {
            mes("You unlock the door with the new key.")
        }
        doors.open(this, door, BACK_ROOM_DOOR_OPEN)
    }

    private suspend fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        arriveDelay()
        anim(CHEST_SEQ)
        locRepo.change(chest, OFFICE_CHEST_OPEN, CHEST_TICKS)
    }

    private suspend fun ProtectedAccess.shutChest(chest: BoundLocInfo) {
        arriveDelay()
        anim(CHEST_SEQ)
        locRepo.change(chest, OFFICE_CHEST, CHEST_TICKS)
    }

    private suspend fun ProtectedAccess.searchChest() {
        arriveDelay()
        if (mourning.stage(player) < STAGE_ADMITTED && !mourning.isComplete(player)) {
            mes("You search the chest but find nothing you have any use for.")
            return
        }
        if (ownsAnywhere(BROKEN_DEVICE) || ownsAnywhere(FIXED_DEVICE)) {
            mes("The chest is full of strange devices, but as you have one you leave them alone.")
            return
        }
        if (inv.freeSpace() < 1 || invAdd(inv, BROKEN_DEVICE).failure) {
            mes("The chest is full of strange devices, but you have no room to take one.")
            return
        }
        objbox(BROKEN_DEVICE, "The chest is full of strange devices. You take one.")
    }

    private suspend fun ProtectedAccess.searchDesk() {
        arriveDelay()
        val wantsGnomeKey = (mourning.stage(player) >= STAGE_ADMITTED || mourning.isComplete(player)) && !ownsAnywhere(GNOME_KEY)
        val wantsNewKey = (temple.stage(player) >= MourningsEndPart2Quest.STAGE_KEY || temple.unlocked(player)) &&
            !ownsAnywhere(MourningsEndPart2Quest.NEW_KEY)
        val key =
            when {
                wantsNewKey -> MourningsEndPart2Quest.NEW_KEY
                wantsGnomeKey -> GNOME_KEY
                else -> null
            }
        if (key == null) {
            mes("You search the desk but find nothing of interest.")
            return
        }
        if (inv.freeSpace() < 1 || invAdd(inv, key).failure) {
            mes("You find a spare key on the desk, but you have no room to take it.")
            return
        }
        objbox(key, if (key == GNOME_KEY) "You find a tarnished key on the desk." else "You find a newly cut key on the desk.")
    }

    companion object {
        const val TRAPDOOR = "loc.mourning_hideout_trap_door"
        const val LADDER_UP = "loc.mourner_hideout_ladder1"
        const val CELL_DOOR = "loc.mourner_hideout_door3"
        const val CELL_DOOR_OPEN = "loc.mourner_hideout_door3_open"
        const val OFFICE_DOOR = "loc.mourner_hideout_door2"
        const val OFFICE_DOOR_OPEN = "loc.mourner_hideout_door2_open"
        const val BACK_ROOM_DOOR = "loc.mourner_hideout_door4"
        const val BACK_ROOM_DOOR_OPEN = "loc.mourner_hideout_door4_open"
        const val OFFICE_CHEST = "loc.mourning_office_chest_closed"
        const val OFFICE_CHEST_OPEN = "loc.mourning_office_chest_open"
        val DESKS = listOf("loc.mourning_office_table", "loc.mourning_office_table_pre", "loc.mourning_office_table_post")

        val BASEMENT_ARRIVAL = CoordGrid(2044, 4649, 0)
        val TRAPDOOR_ARRIVAL = CoordGrid(2543, 3326, 0)

        /** The basement, which lies away from the city in its own part of the map. */
        fun inBasement(coords: CoordGrid): Boolean = coords.x in 2028..2048 && coords.z in 4624..4656 && coords.level == 0

        const val CLIMB_DOWN_SEQ = "seq.human_pickupfloor"
        const val CLIMB_UP_SEQ = "seq.human_reachforladder"
        const val CHEST_SEQ = "seq.human_pickuptable"
        const val CHEST_TICKS = 100
    }
}

/** What a mourner says to a player who looks like one of them. */
internal suspend fun Dialogue.disguisedMournerChat() {
    if (access.random.randomBoolean(2)) {
        chatPlayer(neutral, "Hello.")
        chatNpc(neutral, "Good day. Are you in need of assistance?")
    } else {
        chatPlayer(neutral, "Good day.")
        chatNpc(quiz, "Are the townspeople giving you trouble?")
    }
    val wantsHelp = choice2("Yes, but I don't think you can help.", true, "No, I just wanted to talk to a friendly face.", false)
    if (wantsHelp) {
        chatPlayer(neutral, "Yes, but I don't think you can help.")
        chatNpc(neutral, "You'd be surprised how much the brute force of the Guard can help.")
        chatPlayer(neutral, "Well, I'll be sure to ask if I need some muscle.")
    } else {
        chatPlayer(neutral, "No, I just wanted to talk to a friendly face.")
        chatNpc(angry, "Do I look friendly to you? I must work on my scowl.")
    }
}

/** A mourner inside the Headquarters, to a disguised player. */
internal suspend fun Dialogue.recruitMournerChat() {
    chatPlayer(neutral, "Good day.")
    chatNpc(happy, "Good to see so many new recruits. Soon nobody will be able to stand against the Death Guard.")
    chatPlayer(worried, "That sounds... err... great.")
    chatNpc(neutral, "It is. You should report to Essyllt downstairs. He's in charge.")
}
