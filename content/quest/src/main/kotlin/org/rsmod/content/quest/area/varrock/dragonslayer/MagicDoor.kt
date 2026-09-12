package org.rsmod.content.quest.area.varrock.dragonslayer

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.MAP_PART_THALZAR
import org.rsmod.content.quest.manager.QuestAttribute
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Thalzar's hiding place: the magic door in the north-east corner of the Dwarven Mine and the
 * chest behind it. The door has four openings; once silk, a lobster pot, an unfired bowl and a
 * wizard's mind bomb have been put into them it opens for good.
 */
class MagicDoor
@Inject
constructor(
    private val dragonSlayer: DragonSlayerQuest,
    private val locRepo: LocRepository,
    private val doors: DoorPassage,
) : PluginScript() {

    private class Offering(val obj: String, val flag: QuestAttribute<Boolean>, val message: String)

    private val offerings =
        listOf(
            Offering("obj.silk", dragonSlayer.usedSilk, "You put the silk into the opening in the door."),
            Offering("obj.lobster_pot", dragonSlayer.usedLobsterPot, "You put the lobster pot into the opening in the door."),
            Offering("obj.bowl_unfired", dragonSlayer.usedBowl, "You put the unfired bowl into the opening in the door."),
            Offering("obj.wizards_mind_bomb", dragonSlayer.usedMindBomb, "You pour the Wizard's Mind Bomb into the opening in the door."),
        )

    override fun ScriptContext.startup() {
        for (door in listOf(DOOR, DOOR_CLOSED)) {
            onOpLoc1(door) { openDoor(it.loc) }
            for (offering in offerings) {
                onOpLocU(door, offering.obj) { offer(it.loc, offering) }
            }
        }
        onOpLoc1(CHEST_SHUT) { openChest(it.loc) }
        onOpLoc1(CHEST_OPEN) { searchChest() }
        onOpLoc2(CHEST_OPEN) { closeChest(it.loc) }
    }

    /* The door */

    private suspend fun ProtectedAccess.offer(door: BoundLocInfo, offering: Offering) {
        if (offering.flag.get(player)) {
            mes("You have already put one of those into the door.")
            return
        }
        if (invDel(inv, offering.obj, 1).failure) {
            return
        }
        offering.flag.set(player, true)
        dragonSlayer.syncVars(player)
        soundSynth(SOUND_OFFER)
        mes(offering.message)
        if (dragonSlayer.magicDoorUnlocked(player)) {
            mes("The door opens...")
            swingOpen(door)
        }
    }

    private suspend fun ProtectedAccess.openDoor(door: BoundLocInfo) {
        if (!dragonSlayer.magicDoorUnlocked(player)) {
            mesbox(
                "The door is locked. There are four oddly shaped openings in it, as if " +
                    "something should be placed in each of them.",
            )
            return
        }
        swingOpen(door)
    }

    /** Opens the door for a moment and puts the player on the far side of it. */
    private fun ProtectedAccess.swingOpen(door: BoundLocInfo) {
        val z = player.coords.z.coerceIn(DOOR_Z_MIN, DOOR_Z_MAX)
        val dest =
            if (player.coords.x < DOOR_X) CoordGrid(DOOR_X + 1, z, door.coords.level)
            else CoordGrid(DOOR_X - 1, z, door.coords.level)
        doors.hopThrough(this, door, DOOR_OPEN, dest, SOUND_DOOR)
    }

    /* The chest */

    private fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        soundSynth("synth.cupboard_open")
        locRepo.del(chest, CHEST_OPEN_TICKS)
        locRepo.add(chest.coords, CHEST_OPEN, CHEST_OPEN_TICKS, chest.angle, chest.shape)
    }

    private fun ProtectedAccess.closeChest(chest: BoundLocInfo) {
        soundSynth("synth.cupboard_close")
        locRepo.del(chest, CHEST_OPEN_TICKS)
        locRepo.add(chest.coords, CHEST_SHUT, CHEST_OPEN_TICKS, chest.angle, chest.shape)
    }

    private suspend fun ProtectedAccess.searchChest() {
        if (dragonSlayer.hasMapPiece(player, MAP_PART_THALZAR)) {
            mesbox("The chest is empty, apart from the inscription on the lid.")
            return
        }
        if (inv.isFull()) {
            mesbox("There is something in the chest, but you have no room to take it.")
            return
        }
        mesbox("As you open the chest, you notice an inscription on the lid:")
        mesbox(
            "Here I rest the map to my beloved home. To whoever finds it, I beg of you, let " +
                "it be. I was honour-bound not to destroy the map piece, but I have used all " +
                "my magical skill to keep it from being recovered.",
        )
        mesbox(
            "This map leads to the lair of the beast that destroyed my home, devoured my " +
                "family, and burned to a cinder all that I love. But revenge would not " +
                "benefit me now, and to disturb this beast is to risk bringing its wrath down " +
                "upon another land.",
        )
        mesbox(
            "I cannot stop you from taking this map piece now, but think on this: if you can " +
                "slay the Dragon of Crandor, you are a greater hero than my land ever " +
                "produced. There is no shame in backing out now.",
        )
        if (invAdd(inv, MAP_PART_THALZAR).failure) {
            mes("You have no room for the map piece.")
            return
        }
        objbox(MAP_PART_THALZAR, "You find a map piece in the chest.")
    }

    private companion object {
        const val DOOR = "loc.dragon_slayer_qip_magic_door"
        const val DOOR_CLOSED = "loc.dragon_slayer_qip_magic_door_close"
        const val DOOR_OPEN = "loc.dragon_slayer_qip_magic_door_open"
        const val CHEST_SHUT = "loc.oraclechestshut"
        const val CHEST_OPEN = "loc.oraclechestopen"

        const val SOUND_OFFER = "synth.dragonslayer_bgsound_magicdoor"
        const val SOUND_DOOR = "synth.dragonslayer_magicdoor"

        /** The door is three tiles long on the west wall of the chamber. */
        const val DOOR_X = 3050
        const val DOOR_Z_MIN = 9839
        const val DOOR_Z_MAX = 9841

        const val CHEST_OPEN_TICKS = 100
    }
}
