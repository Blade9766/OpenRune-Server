package org.rsmod.content.quest.area.lighthouse.horrorfromthedeep

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.util.WeaponCategory
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_LIGHT_FIXED
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The metal wall Silas built across the basement. The two middle slabs show a face ringed by six
 * slots; each takes one of the four elemental runes, a sword or an arrow, and the item is lost
 * once placed. With all six in, the outer slabs become one-way doors: the eastern one leads
 * into the foyer above the caves, the western one back out.
 */
class StrangeWall @Inject constructor(private val horror: HorrorFromTheDeepQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (slab in MIDDLE_SLABS) {
            onOpLoc1(slab) { study() }
            onOpLocU(slab) { insert(it.objType) }
        }
        onOpLoc1(ENTRY_DOOR) { passThrough(it.loc, fromBasement = true) }
        onOpLoc1(EXIT_DOOR) { passThrough(it.loc, fromBasement = false) }
    }

    /** The face on the wall; each slot's item model shows once that item has been placed. */
    private fun ProtectedAccess.study() {
        ifOpenMainModal(DOOR_INTERFACE)
        val open = horror.isComplete(player) || horror[player, HorrorFlag.WallOpen]
        for (slot in Slot.entries) {
            ifSetHide(slot.component, !open && !horror[player, slot.flag])
        }
    }

    private suspend fun ProtectedAccess.insert(obj: ItemServerType) {
        val slot = slotFor(obj)
        if (slot == null) {
            mes("Nothing interesting happens.")
            return
        }
        if (horror.isComplete(player) || horror[player, HorrorFlag.WallOpen]) {
            mes("The wall has already been opened.")
            return
        }
        if (horror.stage(player) < STAGE_LIGHT_FIXED) {
            mes("Nothing interesting happens.")
            return
        }
        if (horror[player, slot.flag]) {
            mes("There is already ${slot.article} in that slot.")
            return
        }
        var confirmed = false
        startDialogue {
            chatPlayer(worried, "I don't think I'll get that back if I put it in there.")
            confirmed = choice2("Yes", true, "No", false, title = "Really place the ${slot.noun} into the door?")
        }
        if (!confirmed) {
            return
        }
        val name = RSCM.getReverseMapping(RSCMType.OBJ, obj.id)
        if (invDel(inv, name).failure) {
            return
        }
        anim(PLACE_SEQ)
        soundSynth(PLACE_SOUND)
        mes("You place ${slot.article} into the slot in the wall.")
        horror.set(player, slot.flag)
        if (horror.wallFilled(player)) {
            delay(1)
            soundSynth(OPEN_SOUND)
            mes("You hear the sound of something moving within the wall.")
            horror.set(player, HorrorFlag.WallOpen)
        }
    }

    private suspend fun ProtectedAccess.passThrough(door: BoundLocInfo, fromBasement: Boolean) {
        val onBasementSide = coords.z < door.z
        if (onBasementSide != fromBasement) {
            mes("The wall won't budge from this side.")
            return
        }
        if (!horror.isComplete(player) && !horror[player, HorrorFlag.WallOpen]) {
            mes("The strange wall won't move.")
            return
        }
        arriveDelay()
        soundSynth(OPEN_SOUND)
        delay(1)
        val dest = if (fromBasement) door.coords else door.coords.translateZ(-1)
        telejump(dest, TeleportType.Exempt)
        soundSynth(CLOSE_SOUND)
    }

    private enum class Slot(val flag: HorrorFlag, val noun: String, val article: String, val component: String) {
        Air(HorrorFlag.AirRune, "rune", "an air rune", "component.horror_metaldoor:horror_air"),
        Water(HorrorFlag.WaterRune, "rune", "a water rune", "component.horror_metaldoor:horror_water"),
        Earth(HorrorFlag.EarthRune, "rune", "an earth rune", "component.horror_metaldoor:horror_earth"),
        Fire(HorrorFlag.FireRune, "rune", "a fire rune", "component.horror_metaldoor:horror_fire"),
        Sword(HorrorFlag.Sword, "weapon", "a sword", "component.horror_metaldoor:horror_melee"),
        Arrow(HorrorFlag.Arrow, "arrow", "an arrow", "component.horror_metaldoor:horror_ranging"),
    }

    private fun slotFor(obj: ItemServerType): Slot? {
        RUNES[obj.id]?.let { return it }
        val name = obj.name.lowercase()
        val isSword =
            (obj.weaponCategory == WeaponCategory.StabSword || obj.weaponCategory == WeaponCategory.SlashSword) &&
                (name.endsWith(" sword") || name.endsWith(" longsword")) &&
                name !in EXCLUDED_SWORDS
        if (isSword) {
            return Slot.Sword
        }
        if (obj.category in ARROW_CATEGORIES) {
            return Slot.Arrow
        }
        return null
    }

    private companion object {
        const val DOOR_INTERFACE = "interface.horror_metaldoor"
        const val ENTRY_DOOR = "loc.horror_far_right_door"
        const val EXIT_DOOR = "loc.horror_far_left_door"
        val MIDDLE_SLABS = listOf("loc.horror_mid_left_door", "loc.horror_mid_right_door")

        const val PLACE_SEQ = "seq.human_pickuptable"
        const val PLACE_SOUND = "synth.strangedoor_sound"
        const val OPEN_SOUND = "synth.strangedoor_open"
        const val CLOSE_SOUND = "synth.strangedoor_close"

        val RUNES =
            mapOf(
                "obj.airrune".asRSCM(RSCMType.OBJ) to Slot.Air,
                "obj.waterrune".asRSCM(RSCMType.OBJ) to Slot.Water,
                "obj.earthrune".asRSCM(RSCMType.OBJ) to Slot.Earth,
                "obj.firerune".asRSCM(RSCMType.OBJ) to Slot.Fire,
            )

        val ARROW_CATEGORIES =
            setOf("category.arrows".asRSCM(RSCMType.CATEGORY), "category.dragon_arrow".asRSCM(RSCMType.CATEGORY))

        val EXCLUDED_SWORDS = setOf("rusty sword", "prop sword")
    }
}
