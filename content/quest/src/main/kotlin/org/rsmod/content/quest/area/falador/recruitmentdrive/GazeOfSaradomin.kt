package org.rsmod.content.quest.area.falador.recruitmentdrive

import jakarta.inject.Inject
import jakarta.inject.Provider
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** The player's chosen respawn point: [GazeOfSaradomin.LUMBRIDGE] (the default) or Falador. */
internal var Player.respawnPoint by intVarBit("varbit.respawn_point")

/**
 * The Gaze of Saradomin: after Recruitment Drive a player may ask Sir Tiffy Cashien to respawn them
 * in the grounds of the White Knights' Castle instead of Lumbridge. It is only a choice; finishing
 * the quest leaves the respawn point alone. Activities with their own respawn (minigames, quest
 * arenas) still come first.
 */
class GazeOfSaradomin @Inject constructor(private val quest: Provider<RecruitmentDriveQuest>) : PlayerRespawnHook {
    override val respawnPriority: Int
        get() = PlayerRespawnHook.PRIORITY_CHOSEN_POINT

    override fun respawn(player: Player): CoordGrid? =
        if (player.respawnPoint == FALADOR && quest.get().unlocked(player)) FALADOR_RESPAWN else null

    companion object {
        const val LUMBRIDGE = 0
        const val FALADOR = 1

        /** The OSRS wiki's Falador respawn point, in the grounds of the White Knights' Castle. */
        val FALADOR_RESPAWN = CoordGrid(2970, 3342, 0)
    }
}
