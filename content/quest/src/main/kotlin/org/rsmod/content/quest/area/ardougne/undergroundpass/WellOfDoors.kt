package org.rsmod.content.quest.area.ardougne.undergroundpass

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.BADGES
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.BADGE_COUNT
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.BROKEN_STAFF
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.IBANS_STAFF
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_LOCKED_DOOR
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOORS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_UNICORN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.UNICORN_HORN
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The well of fire outside the Doors of Iban, and the doors themselves.
 *
 * The inscription on the well says the doors will not open while a beating, good heart is
 * present, and the well is how the pass takes one: the three paladins' coats of arms and the horn
 * of a unicorn, all thrown into its flames. With the last of them in, the skull over the doors
 * unlocks them.
 *
 * The same well still answers Iban's staff after the quest.
 */
@Singleton
class WellOfDoors @Inject constructor(private val quest: UndergroundPassQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(WELL) { searchWell() }
        onOpLocU(WELL, UNICORN_HORN) { throwHorn() }
        for (badge in BADGES) {
            onOpLocU(WELL, badge) { throwBadge(badge) }
        }
        onOpLocU(WELL, IBANS_STAFF) { chargeStaff() }
        onOpLocU(WELL, BROKEN_STAFF) { mes("Nothing interesting happens.") }
        for (door in DOORS) {
            onOpLoc1(door) { openDoors(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.searchWell() {
        arriveDelay()
        mes("You search the stone structure...")
        delay(1)
        mes("On the side you find an old inscription,")
        mes("It reads...")
        delay(1)
        player.readWell = 1
        mesbox(
            "<col=8B0000>The doors of Iban will not open while a beating, good heart is " +
                "present.</col><br><br>Cast into the flames all that is pure, and the way will " +
                "be clear.",
        )
    }

    private suspend fun ProtectedAccess.throwHorn() {
        arriveDelay()
        mes("You throw the unicorn horn into the flames...")
        delay(1)
        if (invDel(inv, UNICORN_HORN).failure) {
            return
        }
        player.hornInWell = true
        mes("You hear a howl in the distance.")
        checkDoors()
    }

    private suspend fun ProtectedAccess.throwBadge(badge: String) {
        arriveDelay()
        mes("You throw the coat of arms into the flames...")
        delay(1)
        if (invDel(inv, badge).failure) {
            return
        }
        player.badgesInWell = (player.badgesInWell + 1).coerceAtMost(BADGE_COUNT)
        mes("You hear a howl in the distance.")
        checkDoors()
    }

    private fun ProtectedAccess.checkDoors() {
        if (player.doorsOpen || !player.hornInWell || player.badgesInWell < BADGE_COUNT) {
            return
        }
        player.doorsOpen = true
        soundSynth(SOUND_LOCKED_DOOR)
        mes("You hear a click from nearby...")
        mes("It sounded like it came from the skull above the door")
    }

    /**
     * The pair in the pass opens onto the pair at the edge of the lair, so going through either one
     * puts the player beside the other. Only the side in the pass is locked by the well.
     */
    private suspend fun ProtectedAccess.openDoors(door: BoundLocInfo) {
        arriveDelay()
        if (door.coords.z > UpassCoords.PASS_DOORS_MIN_Z) {
            if (!player.doorsOpen) {
                mes("The door is locked.")
                return
            }
            telejump(UpassCoords.DOORS_LAIR_SIDE, TeleportType.Exempt)
            if (quest.stage(player) == STAGE_UNICORN) {
                quest.advanceTo(this, STAGE_DOORS)
            }
            return
        }
        telejump(UpassCoords.DOORS_PASS_SIDE, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.chargeStaff() {
        arriveDelay()
        if (!quest.isComplete(player)) {
            mes("Nothing interesting happens.")
            return
        }
        mes("You hold the staff above the well...")
        delay(1)
        mes("...And feel the power of Zamorak flow through you.")
    }

    private companion object {
        const val WELL = "loc.bloodwell_upass"
        val DOORS = arrayOf("loc.cavetempledoor2l", "loc.cavetempledoor2r")
    }
}
