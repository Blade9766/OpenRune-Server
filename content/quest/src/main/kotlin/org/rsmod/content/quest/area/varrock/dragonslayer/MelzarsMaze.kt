package org.rsmod.content.quest.area.varrock.dragonslayer

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.MAP_PART_MELZAR
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.MAZE_KEY
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Melzar's Maze, north of Rimmington: the locked front door, the coloured key doors, the one-way
 * exit doors, the ladders the generic scripts do not cover, and the chest with Melzar's map piece.
 *
 * Each coloured door is locked from the side the player first meets it on and swings freely
 * from the other, so the keys (which crumble on use) only ever move the player forwards. Which
 * side is "locked" comes from the map: the red doors are on the west wall of the ground-floor
 * hall, the orange doors on the east wall of the ghosts' rooms, the yellow doors south and east
 * of the skeletons, and the basement doors north of the zombies, Melzar and the lesser demon.
 */
class MelzarsMaze
@Inject
constructor(
    private val dragonSlayer: DragonSlayerQuest,
    private val locRepo: LocRepository,
    private val doors: DoorPassage,
    private val random: GameRandom,
) : PluginScript() {

    private class KeyDoor(val loc: String, val key: String, val colour: String, val lockedSide: (BoundLocInfo) -> WallSide)

    private val keyDoors =
        listOf(
            KeyDoor("loc.reddoor", "obj.redkey", "red") { WallSide.EAST },
            KeyDoor("loc.orangedoor", "obj.orangekey", "orange") { WallSide.WEST },
            KeyDoor("loc.yellowdoor", "obj.yellowkey", "yellow") { if (it.angle.id == 0) WallSide.WEST else WallSide.NORTH },
            KeyDoor("loc.bluedoor", "obj.bluekey", "blue") { WallSide.EAST },
            KeyDoor("loc.magentadoor", "obj.magentakey", "magenta") { WallSide.SOUTH },
            KeyDoor("loc.greendoor", "obj.greenkey", "green") { WallSide.SOUTH },
        )

    override fun ScriptContext.startup() {
        onOpLoc1(FRONT_DOOR) { frontDoor(it.loc) }
        for (door in keyDoors) {
            onOpLoc1(door.loc) { keyDoor(it.loc, door) }
        }
        onOpLoc1(EXIT_DOOR) { exitDoor(it.loc) }

        onOpLoc1(BASEMENT_LADDER) { climb(BASEMENT_ARRIVAL, down = true) }
        onOpLoc1(EXIT_LADDER) { climb(EXIT_LADDER_ARRIVAL, down = true) }
        onOpLoc1(CELLAR_EXIT_LADDER) { climb(CELLAR_EXIT_ARRIVAL, down = false) }
        onOpLoc1(BROKEN_LADDER) { mes("The ladder is broken. You can't climb it from here.") }
        onOpLoc1(TRAPDOOR) { mes("The trapdoor is bolted from below. It can only be opened from the other side.") }

        onOpLoc1(CHEST_SHUT) { openChest(it.loc) }
        onOpLoc1(CHEST_OPEN) { searchChest() }
        onOpLoc2(CHEST_OPEN) { closeChest(it.loc) }

        onOpLoc1(WARDROBE) { openWardrobe(it.loc) }
        onOpLoc2(WARDROBE_OPEN) { searchWardrobe(it.loc) }
        onOpLoc3(WARDROBE_OPEN) { closeWardrobe(it.loc) }
        onOpLoc1(BOOKSHELVES) { searchBookshelves() }
    }

    /* Doors */

    private suspend fun ProtectedAccess.frontDoor(door: BoundLocInfo) {
        val outside = door.sideOf(player.coords) == WallSide.EAST
        if (outside) {
            if (!inv.contains(MAZE_KEY)) {
                mesbox("The door is locked. It looks like it needs a key.")
                return
            }
            mes("You unlock the door with the maze key.")
        }
        doors.walkThrough(this, door, FRONT_DOOR)
    }

    private suspend fun ProtectedAccess.keyDoor(loc: BoundLocInfo, door: KeyDoor) {
        val locked = loc.sideOf(player.coords) == door.lockedSide(loc)
        if (locked) {
            if (!inv.contains(door.key)) {
                mesbox("The door is locked. It looks like it needs a ${door.colour} key.")
                return
            }
            invDel(inv, door.key, 1)
            mes("The key disintegrates as it unlocks the door.")
        }
        doors.walkThrough(this, loc, door.loc)
    }

    /** The exit doors only open from inside the maze. */
    private suspend fun ProtectedAccess.exitDoor(door: BoundLocInfo) {
        val insideSide = if (door.coords.z < DUNGEON_Z) WallSide.WEST else WallSide.EAST
        if (door.sideOf(player.coords) != insideSide) {
            mesbox("This door only opens from the other side.")
            return
        }
        doors.walkThrough(this, door, EXIT_DOOR)
    }

    /* Ladders */

    private suspend fun ProtectedAccess.climb(dest: CoordGrid, down: Boolean) {
        arriveDelay()
        anim(if (down) CLIMB_DOWN_SEQ else CLIMB_UP_SEQ)
        delay(1)
        telejump(dest)
    }

    /* The chest with Melzar's map piece */

    private fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        soundSynth("synth.cupboard_open")
        locRepo.del(chest, FURNITURE_OPEN_TICKS)
        locRepo.add(chest.coords, CHEST_OPEN, FURNITURE_OPEN_TICKS, chest.angle, chest.shape)
    }

    private fun ProtectedAccess.closeChest(chest: BoundLocInfo) {
        soundSynth("synth.cupboard_close")
        locRepo.del(chest, FURNITURE_OPEN_TICKS)
        locRepo.add(chest.coords, CHEST_SHUT, FURNITURE_OPEN_TICKS, chest.angle, chest.shape)
    }

    private suspend fun ProtectedAccess.searchChest() {
        if (dragonSlayer.hasMapPiece(player, MAP_PART_MELZAR)) {
            mesbox("The chest is empty.")
            return
        }
        if (invAdd(inv, MAP_PART_MELZAR).failure) {
            mesbox("There is something in the chest, but you have no room to take it.")
            return
        }
        objbox(MAP_PART_MELZAR, "You find a map piece in the chest.")
    }

    /* Furniture */

    private fun ProtectedAccess.openWardrobe(wardrobe: BoundLocInfo) {
        soundSynth("synth.cupboard_open")
        locRepo.del(wardrobe, FURNITURE_OPEN_TICKS)
        locRepo.add(wardrobe.coords, WARDROBE_OPEN, FURNITURE_OPEN_TICKS, wardrobe.angle, wardrobe.shape)
    }

    private fun ProtectedAccess.closeWardrobe(wardrobe: BoundLocInfo) {
        soundSynth("synth.cupboard_close")
        locRepo.del(wardrobe, FURNITURE_OPEN_TICKS)
        locRepo.add(wardrobe.coords, WARDROBE, FURNITURE_OPEN_TICKS, wardrobe.angle, wardrobe.shape)
    }

    private suspend fun ProtectedAccess.searchWardrobe(wardrobe: BoundLocInfo) {
        when (wardrobe.coords.level) {
            0 -> mesbox("The wardrobe is full of heaps of dead rats!")
            1 ->
                mesbox(
                    "The wardrobe is full of scorched and broken objects: clothes, tools and " +
                        "children's toys. Each is labelled with a scrawled number. You don't " +
                        "see anything of value or use.",
                )
            else ->
                mesbox(
                    "The wardrobe is full of heaps of bones and incomplete skeletons, each one " +
                        "labelled with a number.",
                )
        }
    }

    private suspend fun ProtectedAccess.searchBookshelves() {
        mesbox("These books are full of scrawled notes. You open one at a random page...")
        mesbox(MELZAR_NOTES[random.of(MELZAR_NOTES.indices)])
    }

    private companion object {
        const val FRONT_DOOR = "loc.melzardoor"
        const val EXIT_DOOR = "loc.funexit"

        const val BASEMENT_LADDER = "loc.funladdertop"
        const val EXIT_LADDER = "loc.dragonslayer_melzar_exit_laddertop"
        const val CELLAR_EXIT_LADDER = "loc.dragon_slayer_ladder_from_cellar"
        const val BROKEN_LADDER = "loc.ladder_broken"
        const val TRAPDOOR = "loc.dragon_slayer_qip_trapdoor_closed"

        const val CHEST_SHUT = "loc.funchestshut"
        const val CHEST_OPEN = "loc.funchestopen"
        const val WARDROBE = "loc.dragonslayer_spookywardrobe"
        const val WARDROBE_OPEN = "loc.dragonslayer_spookywardrobe_open"
        const val BOOKSHELVES = "loc.dragon_slayer_qip_spookybookshelves"

        const val CLIMB_DOWN_SEQ = "seq.human_pickupfloor"
        const val CLIMB_UP_SEQ = "seq.human_reachforladder"

        /** Basement locs sit in the dungeon copy of the map, 6400 tiles north. */
        const val DUNGEON_Z = 6400

        /** Under the ladder at 2932,3240 on the ground floor, beside the zombies. */
        val BASEMENT_ARRIVAL = CoordGrid(2932, 9640, 0)

        /** Outside the north wall, below the first-floor exit ladder at 2925,3258. */
        val EXIT_LADDER_ARRIVAL = CoordGrid(2925, 3259, 0)

        /** Outside the north wall, beside the trapdoor above the basement ladder at 2928,9658. */
        val CELLAR_EXIT_ARRIVAL = CoordGrid(2928, 3259, 0)

        const val FURNITURE_OPEN_TICKS = 100

        val MELZAR_NOTES =
            listOf(
                "Day 1,203. The rats have learned to count. I have numbered them so that I " +
                    "may count them back. Custard remains the answer. Custard is always the answer.",
                "The ghosts will not stop asking about the fire. I tell them the fire is over. " +
                    "They do not believe me. Perhaps if I label the fire it will stay in its place.",
                "Thalzar hid his piece under a mountain and Lozar keeps hers in a box. Fools! " +
                    "Mine is guarded by a demon, and the demon is guarded by ME.",
                "Note to self: feed the pet rock. Note to the pet rock: stop staring. The tea " +
                    "is mine. The map is mine. The key to the green door is the demon's, for now.",
            )
    }
}
