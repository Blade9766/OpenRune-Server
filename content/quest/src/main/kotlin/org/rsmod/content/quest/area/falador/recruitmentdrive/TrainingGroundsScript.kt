package org.rsmod.content.quest.area.falador.recruitmentdrive

import jakarta.inject.Inject
import jakarta.inject.Provider
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The parts of the training grounds every room shares: each room's entrance portal (quit the attempt
 * and go back to Falador), and the exit door and portal of the rooms without a door of their own
 * (Sir Ren Itchood's lock and Miss Cheevers's key door are their rooms' scripts). Logging out leaves
 * the grounds the way dying does: the visit ends, its items and the room's state go, and the passed
 * rooms are kept for the next visit with Sir Tiffy.
 */
class TrainingGroundsScript @Inject constructor(private val testing: RecruitmentTesting) : PluginScript() {
    override fun ScriptContext.startup() {
        for (room in TestRoom.entries) {
            onOpLoc1(room.entrancePortal) { with(testing) { quit(room) } }
            onOpLoc1(room.exitPortal) { with(testing) { proceed(room) } }
            if (room != TestRoom.ACROSTIC && room != TestRoom.IMPROVISATION) {
                onOpLoc1(room.exitDoor) { exitDoor(room) }
            }
        }
        onPlayerLogout {
            testing.abandonVisit(player)
            testing.forgetPlayer(player)
        }
        onPlayerLogin { testing.abandonVisit(player) }
    }

    private suspend fun ProtectedAccess.exitDoor(room: TestRoom) {
        if (testing.passedHere(player, room)) {
            with(testing) { proceed(room) }
            return
        }
        if (testing.testing(player, room)) {
            mes("This door is locked.")
        }
    }
}

/**
 * Dying in the grounds ends the visit like logging out, and nothing but the grounds' own portals and
 * Sir Tiffy's teleport moves a player in or out, so no room item can be carried away.
 */
class TrainingGroundsHooks @Inject constructor(private val testing: Provider<RecruitmentTesting>) :
    PlayerDeathCleanupHook, PlayerTeleportValidateHook {
    override fun cleanup(player: Player) {
        testing.get().abandonVisit(player)
    }

    override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String? {
        if (type == TeleportType.Exempt || !testing.get().grounds.inside(player)) {
            return null
        }
        return "You can't teleport out of the training grounds. Use a portal to leave."
    }
}
