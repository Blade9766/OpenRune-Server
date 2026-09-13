package org.rsmod.content.quest.area.lighthouse.horrorfromthedeep

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.generic.locs.passages.StairNavigator
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.DIARY
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.JOURNAL
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.KEY
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.LARRISSA
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.LARRISSA_INSIDE
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.MANUAL
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.MOLTEN_GLASS
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_LIGHT_FIXED
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_UNLOCKED
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.SWAMP_TAR
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.TINDERBOX
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Lighthouse building: the front door, the spiral staircase's straight-to-the-top options,
 * the iron ladder down to the basement, Jossik's bookcase and the lighting mechanism.
 *
 * Until the light is repaired the front door leads into the wrecked copy of the building, where
 * Larrissa waits and the mechanism is broken. From then on it leads into the real building, but
 * its basement ladder still drops into the quest copy of the basement until the quest is done.
 */
class Lighthouse
@Inject
constructor(
    private val horror: HorrorFromTheDeepQuest,
    private val stairs: StairNavigator,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val search: NpcSearch,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(DOORWAY) { doorway() }
        onOpLoc2(STAIRS_BASE) { climbTwo(it.loc, up = true) }
        onOpLoc2(STAIRS_TOP) { climbTwo(it.loc, up = false) }
        onOpLoc1(LADDER_DOWN) { ladderDown() }
        onOpLoc1(LADDER_UP) { ladderUp() }
        onOpLoc1(BOOKCASE) { searchBookcase() }
        onOpLocU(COG_BROKEN, SWAMP_TAR) { spreadTar() }
        onOpLocU(COG_BROKEN, TINDERBOX) { lightTorch() }
        onOpLocU(COG_BROKEN, MOLTEN_GLASS) { mendLens() }
        for (item in listOf(SWAMP_TAR, TINDERBOX, MOLTEN_GLASS)) {
            onOpLocU(COG_WORKING, item) { mes("The lighting mechanism is working perfectly.") }
        }
    }

    private suspend fun ProtectedAccess.doorway() {
        if (LighthouseCoords.inWreckedBuilding(coords) || coords.z >= LighthouseCoords.DOOR_INSIDE.z) {
            soundSynth(DOOR_SOUND)
            telejump(LighthouseCoords.DOOR_OUTSIDE, TeleportType.Exempt)
            return
        }
        val stage = horror.stage(player)
        if (!horror.isComplete(player) && !horror[player, HorrorFlag.FrontDoor]) {
            if (stage < STAGE_STARTED) {
                mes("The door is locked shut.")
                return
            }
            if (!horror.bridgeRepaired(player)) {
                larrissaSays(
                    "Please, adventurer... I want to know what has happened in there as much as " +
                        "you do, but you need to mend the bridge for me first!",
                )
                return
            }
            if (KEY !in player.inv) {
                soundSynth(LOCKED_SOUND)
                mes("The door is locked.")
                return
            }
            soundSynth(UNLOCK_SOUND)
            mes("You unlock the Lighthouse front door.")
            horror.set(player, HorrorFlag.FrontDoor)
            horror.advanceTo(this, STAGE_UNLOCKED)
        }
        soundSynth(DOOR_SOUND)
        if (!horror.isComplete(player) && stage < STAGE_LIGHT_FIXED) {
            fadeToBlack()
            telejump(LighthouseCoords.WRECKED_DOOR_INSIDE, TeleportType.Exempt)
            delay(1)
            fadeFromBlack()
            ifCloseSub(FADE_OVERLAY)
            if (horror.stage(player) == STAGE_UNLOCKED) {
                mes("The lighthouse has been ransacked. Blood is smeared across the floor and the walls are scored with claw marks.")
            }
            return
        }
        telejump(LighthouseCoords.DOOR_INSIDE, TeleportType.Exempt)
    }

    /** The spiral staircase's "Top-floor" and "Bottom-floor" options, which pass the middle floor. */
    private suspend fun ProtectedAccess.climbTwo(loc: BoundLocInfo, up: Boolean) {
        val midway = stairs.destination(loc, coords, up) ?: return
        val middleCoords = if (up) CoordGrid(loc.x, loc.z, midway.level) else CoordGrid(loc.x, loc.z - 1, midway.level)
        val middleType = ServerCacheManager.getObject(STAIRS_MIDDLE.asRSCM(RSCMType.LOC)) ?: return
        val middle = locRepo.findExact(middleCoords, middleType)?.let { BoundLocInfo(it, middleType) }
        val dest = middle?.let { stairs.destination(it, midway, up) } ?: midway
        arriveDelay()
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.ladderDown() {
        val wrecked = LighthouseCoords.inWreckedBuilding(coords)
        val dest =
            when {
                horror.isComplete(player) && !wrecked -> LighthouseCoords.toReal(LighthouseCoords.BASEMENT_LANDING)
                horror.stage(player) >= STAGE_LIGHT_FIXED || horror.isComplete(player) -> LighthouseCoords.BASEMENT_LANDING
                horror.stage(player) < STAGE_BRIEFED -> {
                    mesbox("You must fix the lighthouse before any ships crash!")
                    return
                }
                else -> {
                    larrissaSays(
                        "Please, adventurer, don't let your curiosity get the better of you! The " +
                            "light has to be fixed before there's an accident!",
                    )
                    return
                }
            }
        anim(CLIMB_DOWN_SEQ)
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.ladderUp() {
        val inCopy = LighthouseCoords.inCavesCopy(coords)
        val dest =
            when {
                !inCopy -> LighthouseCoords.LADDER_LANDING
                horror.isComplete(player) || horror.stage(player) >= STAGE_LIGHT_FIXED -> LighthouseCoords.LADDER_LANDING
                else -> LighthouseCoords.WRECKED_LADDER_LANDING
            }
        anim(CLIMB_UP_SEQ)
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.larrissaSays(text: String) {
        val type = if (LighthouseCoords.inWreckedBuilding(coords)) LARRISSA_INSIDE else LARRISSA
        val larrissa = npcFind(coords, type, LARRISSA_RANGE, HuntVis.Off, search)
        if (larrissa == null) {
            mesbox(text)
            return
        }
        startDialogue(larrissa) { chatNpc(worried, text) }
    }

    private suspend fun ProtectedAccess.searchBookcase() {
        anim(SEARCH_SEQ)
        mesbox("There are three books here that look important... What would you like to do?")
        var choice = emptyList<String>()
        startDialogue {
            choice =
                choice4(
                    "Take the Lighthouse Manual",
                    listOf(MANUAL),
                    "Take the ancient Diary",
                    listOf(DIARY),
                    "Take Jossik's Journal",
                    listOf(JOURNAL),
                    "Take all three books",
                    listOf(MANUAL, DIARY, JOURNAL),
                )
        }
        if (inv.freeSpace() < choice.size) {
            mes(if (choice.size == 1) "You do not have enough room to take that." else "You do not have enough room to take all three.")
            return
        }
        for (book in choice) {
            invAddOrDrop(objRepo, book)
        }
        soundSynth(PICKUP_SOUND)
    }

    private suspend fun ProtectedAccess.spreadTar() {
        if (!canRepair()) {
            return
        }
        if (horror[player, HorrorFlag.Tar]) {
            mes("The torch is already coated in tar.")
            return
        }
        anim(USE_SEQ)
        delay(1)
        if (invDel(inv, SWAMP_TAR).failure) {
            return
        }
        mes("You use the swamp tar to make the torch flammable again.")
        horror.set(player, HorrorFlag.Tar)
        checkRepaired()
    }

    private suspend fun ProtectedAccess.lightTorch() {
        if (!canRepair()) {
            return
        }
        if (horror[player, HorrorFlag.Light]) {
            mes("The torch is already alight.")
            return
        }
        if (!horror[player, HorrorFlag.Tar]) {
            mes("The torch is too old and dry to catch. It needs something flammable on it first.")
            return
        }
        anim(LIGHT_SEQ)
        soundSynth(LIGHT_SOUND)
        delay(2)
        mes("You light the torch with your tinderbox.")
        horror.set(player, HorrorFlag.Light)
        checkRepaired()
    }

    private suspend fun ProtectedAccess.mendLens() {
        if (!canRepair()) {
            return
        }
        if (horror[player, HorrorFlag.Glass]) {
            mes("The lens has already been mended.")
            return
        }
        anim(USE_SEQ)
        soundSynth(GLASS_SOUND)
        delay(1)
        if (invDel(inv, MOLTEN_GLASS).failure) {
            return
        }
        mes("You use the molten glass to repair the lens.")
        horror.set(player, HorrorFlag.Glass)
        checkRepaired()
    }

    private fun ProtectedAccess.canRepair(): Boolean {
        val stage = horror.stage(player)
        if (horror.isComplete(player) || stage >= STAGE_LIGHT_FIXED) {
            mes("The torch is already burning brightly.")
            return false
        }
        if (stage < STAGE_UNLOCKED) {
            mes("Nothing interesting happens.")
            return false
        }
        return true
    }

    private suspend fun ProtectedAccess.checkRepaired() {
        if (!horror.lightRepaired(player)) {
            return
        }
        soundSynth(MACHINERY_SOUND)
        mesbox("You have managed to repair the lighthouse torch!")
        horror.advanceTo(this, STAGE_LIGHT_FIXED)
    }

    private companion object {
        const val DOORWAY = "loc.horror_lighthouse_doorway"
        const val STAIRS_BASE = "loc.horror_lighthouse_spiralstairs_base"
        const val STAIRS_MIDDLE = "loc.horror_lighthouse_spiralstairs_middle"
        const val STAIRS_TOP = "loc.horror_lighthouse_spiralstairs_top"
        const val LADDER_DOWN = "loc.horror_ladder_top"
        const val LADDER_UP = "loc.horror_ladder_base"
        const val BOOKCASE = "loc.horror_bookcase"
        const val COG_BROKEN = "loc.horror_lighthouse_cog_broken"
        const val COG_WORKING = "loc.horror_lighthouse_cog"

        const val LARRISSA_RANGE = 12
        const val FADE_OVERLAY = "interface.fade_overlay"

        const val CLIMB_DOWN_SEQ = "seq.human_pickupfloor"
        const val CLIMB_UP_SEQ = "seq.human_reachforladder"
        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val USE_SEQ = "seq.human_pickuptable"
        const val LIGHT_SEQ = "seq.human_createfire"

        const val DOOR_SOUND = "synth.door_open"
        const val LOCKED_SOUND = "synth.irondoor_locked"
        const val UNLOCK_SOUND = "synth.unlock"
        const val PICKUP_SOUND = "synth.pick"
        const val LIGHT_SOUND = "synth.fire_quiet_to_loud"
        const val GLASS_SOUND = "synth.glass_chink_1"
        const val MACHINERY_SOUND = "synth.grandtree_machinery"
    }
}
