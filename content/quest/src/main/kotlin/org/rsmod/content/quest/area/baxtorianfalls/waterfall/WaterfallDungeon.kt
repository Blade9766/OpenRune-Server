package org.rsmod.content.quest.area.baxtorianfalls.waterfall

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.fadeFromBlack
import org.rsmod.content.quest.area.ardougne.fadeToBlack
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.AMULET
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.BAXTORIAN_KEY
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.PILLAR_COUNT
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.RUNES_PER_PILLAR
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.STAGE_FLOOR_RISEN
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.STAGE_RUNES_PLACED
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.URN_EMPTY
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.URN_FULL
import org.rsmod.content.quest.area.varrock.dragonslayer.DoorPassage
import org.rsmod.content.quest.area.varrock.dragonslayer.WallSide
import org.rsmod.content.quest.area.varrock.dragonslayer.sideOf
import org.rsmod.content.quest.area.varrock.dragonslayer.tileAcross
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The caves inside Baxtorian Falls.
 *
 * Baxtorian's tomb is mapped twice. The room the player walks into (2561..2570 x 9902..9917) has
 * the six pillars, the statues of Baxtorian and Glarial, and the chalice floating out of reach on
 * level 1. An identical copy 38 tiles east and one tile south has the chalice on the floor.
 * Placing the amulet "raises the floor" by moving the player into the copy, and leaving the copy
 * through its doors puts them back at the matching spot in the real corridor.
 */
class WaterfallDungeon
@Inject
constructor(
    private val waterfall: WaterfallQuest,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val doors: DoorPassage,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(EXIT_DOOR) { exitFalls() }
        onOpLoc1(CRATE) { searchCrate() }
        onOpLoc1(TOMB_DOOR) { openTombDoor(it.loc) }
        for (pillar in PILLAR_FORMS) {
            for ((rune, _) in PILLAR_RUNES) {
                onOpLocU(pillar, rune) { placeRune(it.loc, rune) }
            }
        }
        onOpLocU(STATUE_GLARIAL, AMULET) { placeAmulet() }
        onOpLoc1(CHALICE) { takeTreasure() }
        onOpLoc1(CHALICE_ASH) { takeTreasure() }
        onOpLocU(CHALICE, URN_FULL) { pourAshes() }
    }

    private suspend fun ProtectedAccess.exitFalls() {
        soundSynth(DOOR_SOUND)
        mesbox("You leave the waterfall.")
        telejump(WaterfallCoords.LEDGE, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.searchCrate() {
        anim(SEARCH_SEQ)
        delay(1)
        if (BAXTORIAN_KEY in player.inv) {
            mes("You search the crate but find nothing of interest.")
            return
        }
        invAddOrDrop(objRepo, BAXTORIAN_KEY)
        objbox(BAXTORIAN_KEY, "Hidden inside the crate you find a key.")
    }

    /** Both doors on the way to the tomb sit on the north edge of their tile and lock from the south. */
    private suspend fun ProtectedAccess.openTombDoor(door: BoundLocInfo) {
        if (door.sideOf(player.coords) == WallSide.SOUTH) {
            if (BAXTORIAN_KEY !in player.inv) {
                soundSynth(LOCKED_SOUND)
                mes("The door is locked.")
                return
            }
            mes("You unlock the door with the key.")
            doors.walkThrough(this, door, TOMB_DOOR)
            return
        }
        if (door.coords.inRaisedCopy()) {
            soundSynth(DOOR_SOUND)
            arriveDelay()
            telejump(door.tileAcross(player.coords).toRealRoom(), TeleportType.Exempt)
            return
        }
        doors.walkThrough(this, door, TOMB_DOOR)
    }

    private suspend fun ProtectedAccess.placeRune(pillar: BoundLocInfo, rune: String) {
        val runeIndex = PILLAR_RUNES.getValue(rune)
        val pillarIndex = pillarIndex(pillar.coords) ?: return
        val bit = 1 shl (pillarIndex * RUNES_PER_PILLAR + runeIndex)
        val placed = waterfall.pillarRunes.get(player)
        if ((placed and bit) != 0) {
            mes("You have already placed that kind of rune on this pillar.")
            return
        }
        if (invDel(inv, rune).failure) {
            return
        }
        anim(PLACE_SEQ)
        soundSynth(PLACE_SOUND)
        spotanimMap(worldRepo, SMOKE_SPOTANIM, pillar.coords)
        waterfall.pillarRunes.set(player, placed or bit)
        objbox(rune, "You set the rune into the pillar and it vanishes in a puff of smoke.")
        if (waterfall.allRunesPlaced(player)) {
            waterfall.advanceTo(this, STAGE_RUNES_PLACED)
        }
    }

    private suspend fun ProtectedAccess.placeAmulet() {
        if (player.coords.inRaisedCopy()) {
            mes("Glarial's statue already wears her amulet.")
            return
        }
        if (!waterfall.allRunesPlaced(player)) {
            invDel(inv, AMULET)
            mesbox("You begin to hang the amulet around the statue's neck, but water starts pouring into the chamber...")
            mesbox("...and the flood sweeps you out of the cave and down the river.")
            washDownstream(bruised = true)
            return
        }
        if (invDel(inv, AMULET).failure) {
            return
        }
        mesbox("You hang the amulet around the statue's neck. A deep rumble sounds beneath your feet as the floor begins to rise.")
        soundSynth(RUMBLE_SOUND)
        fadeToBlack()
        telejump(player.coords.toRaisedCopy(), TeleportType.Exempt)
        delay(1)
        fadeFromBlack()
        waterfall.advanceTo(this, STAGE_FLOOR_RISEN)
    }

    private suspend fun ProtectedAccess.takeTreasure() {
        if (waterfall.isComplete(player)) {
            mes("Nothing is left in the chalice but some old ashes.")
            return
        }
        mesbox("You reach for the treasure in the chalice, but water rushes into the chamber...")
        mesbox("...and the flood sweeps you out of the cave and down the river.")
        loseGlarialsRelics()
        washDownstream(bruised = true)
    }

    private suspend fun ProtectedAccess.pourAshes() {
        if (waterfall.isComplete(player)) {
            mes("Nothing is left in the chalice but some old ashes.")
            return
        }
        if (waterfall.stage(player) < STAGE_FLOOR_RISEN) {
            mes("You can't reach the chalice from here.")
            return
        }
        if (inv.freeSpace() < REWARD_SLOTS) {
            mesbox("You will need at least $REWARD_SLOTS free inventory spaces to carry the treasure.")
            return
        }
        if (invDel(inv, URN_FULL).failure) {
            return
        }
        invAddOrDrop(objRepo, URN_EMPTY)
        anim(POUR_SEQ)
        soundSynth(POUR_SOUND)
        mesbox("You gently pour Glarial's ashes into the chalice, and lift out Baxtorian's treasure...")
        waterfall.quest.completeQuest(this)
    }

    private fun ProtectedAccess.loseGlarialsRelics() {
        invDel(inv, URN_FULL)
        invDel(inv, AMULET)
        if (AMULET in player.worn) {
            invDel(player.worn, AMULET)
        }
    }

    private companion object {
        const val EXIT_DOOR = "loc.baxtorian_door_waterfall_quest"
        const val CRATE = "loc.baxtorian_crate_waterfall_quest"
        const val TOMB_DOOR = "loc.baxtorian_door_2_waterfall_quest"

        /**
         * The pillar is a multiloc on `varbit.sote`, and the used-item bridge hands the resolved
         * form to `onOpLocU`, so every form is registered.
         */
        val PILLAR_FORMS =
            listOf(
                "loc.stonepillar_small_waterfall_quest",
                "loc.stonepillar_small_waterfall_quest_noop",
                "loc.stonepillar_small_waterfall_quest_op",
            )
        const val STATUE_GLARIAL = "loc.statue_queen_waterfall_quest"
        const val CHALICE = "loc.baxtorian_chalice_waterfall_quest"
        const val CHALICE_ASH = "loc.baxtorian_chalice_waterfall_quest_ash"

        /** Rune obj to its bit within a pillar's three. */
        val PILLAR_RUNES = mapOf("obj.airrune" to 0, "obj.waterrune" to 1, "obj.earthrune" to 2)

        /** Pillar columns and rows in the real tomb room. */
        const val PILLAR_WEST_X = 2562
        const val PILLAR_EAST_X = 2569
        const val PILLAR_SOUTH_Z = 9910
        const val PILLAR_ROW_GAP = 2

        const val COPY_OFFSET_X = 38
        const val COPY_OFFSET_Z = -1
        const val COPY_MIN_X = 2590

        const val REWARD_SLOTS = 5

        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val PLACE_SEQ = "seq.human_pickuptable"
        const val POUR_SEQ = "seq.human_pickuptable"
        const val SMOKE_SPOTANIM = "spotanim.smokepuff"
        const val DOOR_SOUND = "synth.door_open"
        const val LOCKED_SOUND = "synth.irondoor_locked"
        const val PLACE_SOUND = "synth.smokepuff"
        const val RUMBLE_SOUND = "synth.contact_rumble"
        const val POUR_SOUND = "synth.vial_pour"

        fun CoordGrid.inRaisedCopy(): Boolean = x >= COPY_MIN_X

        fun CoordGrid.toRaisedCopy(): CoordGrid = translate(COPY_OFFSET_X, COPY_OFFSET_Z)

        fun CoordGrid.toRealRoom(): CoordGrid = translate(-COPY_OFFSET_X, -COPY_OFFSET_Z)

        fun pillarIndex(coords: CoordGrid): Int? {
            val real = if (coords.inRaisedCopy()) coords.toRealRoom() else coords
            val column =
                when (real.x) {
                    PILLAR_WEST_X -> 0
                    PILLAR_EAST_X -> 1
                    else -> return null
                }
            val row = (real.z - PILLAR_SOUTH_Z) / PILLAR_ROW_GAP
            if (row !in 0 until PILLAR_COUNT / 2 || (real.z - PILLAR_SOUTH_Z) % PILLAR_ROW_GAP != 0) {
                return null
            }
            return column * (PILLAR_COUNT / 2) + row
        }
    }
}
