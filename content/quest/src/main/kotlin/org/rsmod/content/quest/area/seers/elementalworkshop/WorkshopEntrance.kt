package org.rsmod.content.quest.area.seers.elementalworkshop

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.generic.locs.passages.StairNavigator
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.KEY
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The odd looking wall on the north side of the Seers' Village smithy, and the spiral stairs
 * behind it down to the workshop. The battered key unlocks the wall once; after that it opens for
 * the player like any door, so a lost key never shuts them out again.
 */
class WorkshopEntrance
@Inject
constructor(
    private val ew: ElementalWorkshopQuest,
    private val passages: GenericPassageScript,
    private val stairs: StairNavigator,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (wall in ODD_WALLS) {
            onOpLoc1(wall) { openWall(it.loc) }
            onOpLocU(wall, KEY) { unlockWall(it.loc) }
            onOpLocU(wall) { mes("You can't unlock the door with that.") }
        }
        onOpLoc1(STAIRS_DOWN) { climb(WORKSHOP_LANDING) }
        onOpLoc1(STAIRS_UP) { climb(SMITHY_LANDING) }
    }

    private suspend fun ProtectedAccess.openWall(wall: BoundLocInfo) {
        arriveDelay()
        val inside = coords.z > wall.coords.z
        if (inside || ew.isWorkshopUnlocked(player)) {
            walkThrough(wall)
            return
        }
        if (KEY in inv) {
            unlockWall(wall)
            return
        }
        mes("Looking closely, you spot a tiny keyhole hidden in the stonework. The wall is locked.")
    }

    private suspend fun ProtectedAccess.unlockWall(wall: BoundLocInfo) {
        arriveDelay()
        if (!ew.isWorkshopUnlocked(player)) {
            soundSynth(UNLOCK_SOUND)
            mes("You fit the battered key into a hidden keyhole. The wall swings open.")
            ew.unlockWorkshop(player)
        }
        walkThrough(wall)
    }

    private suspend fun ProtectedAccess.walkThrough(wall: BoundLocInfo) {
        val type = ServerCacheManager.getObject(wall.id) ?: return
        with(passages) { walkThrough(wall, type) }
    }

    private suspend fun ProtectedAccess.climb(target: CoordGrid) {
        val dest = stairs.landing(target) ?: target
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    internal companion object {
        val ODD_WALLS = listOf("loc.elemental_workshop_oddwall_l", "loc.elemental_workshop_oddwall_r")
        const val STAIRS_DOWN = "loc.elemental_workshop_spiralstairstop"
        const val STAIRS_UP = "loc.elemental_workshop_spiralstairs"
        const val UNLOCK_SOUND = "synth.unlock"

        /** Beside the foot of the spiral stairs in the workshop's central chamber. */
        val WORKSHOP_LANDING = CoordGrid(2716, 9888, 0)

        /** Inside the smithy's hidden room, between the odd looking wall and the stairs. */
        val SMITHY_LANDING = CoordGrid(2709, 3496, 0)
    }
}
