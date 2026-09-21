package org.rsmod.content.quest.area.ardougne.undergroundpass

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.BADGES
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.BADGE_COUNT
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.BROKEN_STAFF
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.IBANS_STAFF
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_COLLAPSE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_IBAN_LIGHTNING
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOORS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.UNICORN_HORN
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The well outside the Doors of Iban, and the doors themselves.
 *
 * The inscription over them says they will not open while a beating, good heart is present, and
 * the well is how the pass takes one: three paladins' coats of arms and the horn of a unicorn, all
 * of them things that were alive and are not now.
 *
 * The same well still works after the quest, which is what Iban's staff is recharged in.
 */
@Singleton
class WellOfDoors
@Inject
constructor(private val quest: UndergroundPassQuest, private val locRepo: LocRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(WELL) { searchWell() }
        onOpLocU(WELL, UNICORN_HORN) { dropInHorn() }
        for (badge in BADGES) {
            onOpLocU(WELL, badge) { dropInBadge(badge) }
        }
        onOpLocU(WELL, IBANS_STAFF) { rechargeStaff(IBANS_STAFF) }
        onOpLocU(WELL, BROKEN_STAFF) { rechargeStaff(BROKEN_STAFF) }
        for (door in DOORS) {
            onOpLoc1(door) { openDoors(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.searchWell() {
        arriveDelay()
        anim(SEQ_SEARCH)
        delay(1)
        player.readWell = 1
        if (player.doorsOpen) {
            mesbox(
                "The well is empty and the doors beyond it stand open. Whatever was in the water " +
                    "has had what it wanted.",
            )
            return
        }
        mesbox(
            "There is an inscription cut into the lip of the well:<br><br><col=8B0000>The doors of " +
                "Iban will not open while a beating, good heart is present.</col>",
        )
        mesbox(
            "The water is a long way down and there is something pale moving in it. Four somethings " +
                "have gone in already, by the marks on the stone.",
        )
    }

    private suspend fun ProtectedAccess.dropInHorn() {
        arriveDelay()
        if (player.hornInWell) {
            mes("The well has already had a horn.")
            return
        }
        if (invDel(inv, UNICORN_HORN).failure) {
            return
        }
        anim(SEQ_SEARCH)
        soundSynth(SPLASH_SOUND)
        delay(1)
        player.hornInWell = true
        mes("The horn goes into the water without a sound.")
        checkDoors()
    }

    private suspend fun ProtectedAccess.dropInBadge(badge: String) {
        arriveDelay()
        if (invDel(inv, badge).failure) {
            return
        }
        anim(SEQ_SEARCH)
        soundSynth(SPLASH_SOUND)
        delay(1)
        player.badgesInWell = (player.badgesInWell + 1).coerceAtMost(BADGE_COUNT)
        mes("The badge goes into the water without a sound.")
        checkDoors()
    }

    /** The doors open the moment the well has had all four. */
    private suspend fun ProtectedAccess.checkDoors() {
        if (player.doorsOpen || !player.hornInWell || player.badgesInWell < BADGE_COUNT) {
            return
        }
        player.doorsOpen = true
        soundSynth(SOUND_IBAN_LIGHTNING)
        delay(2)
        soundSynth(SOUND_COLLAPSE)
        for (coords in UpassCoords.DOORS_OF_IBAN) {
            locRepo.findExact(coords, DOOR_SHAPE)?.let { locRepo.del(it, Int.MAX_VALUE) }
        }
        quest.advanceTo(this, STAGE_DOORS)
        mesbox("Something in the well takes the last of it, and the doors swing inwards.")
    }

    private suspend fun ProtectedAccess.openDoors(door: BoundLocInfo) {
        arriveDelay()
        if (!player.doorsOpen) {
            mesbox(
                "There is no handle on this side, no lock and no hinge. The well beside them is " +
                    "the only thing here that looks anything like a keyhole.",
            )
            return
        }
        soundSynth(DOOR_SOUND)
        locRepo.del(door, Int.MAX_VALUE)
    }

    /**
     * After the quest the staff is charged the same way the doors were opened, which is the one
     * use the well still has.
     */
    private suspend fun ProtectedAccess.rechargeStaff(staff: String) {
        arriveDelay()
        if (!quest.isComplete(player)) {
            mes("Nothing interesting happens.")
            return
        }
        if (invDel(inv, staff).failure) {
            return
        }
        anim(SEQ_SEARCH)
        soundSynth(SOUND_IBAN_LIGHTNING)
        delay(2)
        invAdd(inv, IBANS_STAFF)
        mesbox("You dip the staff into the well. Whatever is down there fills it again.")
    }

    private companion object {
        const val WELL = "loc.bloodwell_upass"
        val DOORS = arrayOf("loc.cavetempledoor2l", "loc.cavetempledoor2r")
        const val SPLASH_SOUND = "synth.watersplash"
        const val DOOR_SOUND = "synth.stone_door"

        val DOOR_SHAPE = LocShape.CentrepieceStraight
    }
}
