package org.rsmod.content.quest.area.ardougne.regicide

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.quest.area.misthalin.SceneCamera
import org.rsmod.content.quest.area.misthalin.SceneRegion
import org.rsmod.content.quest.area.misthalin.lockedScene
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

/**
 * Regicide's cutscenes play in a private copy of the map around the player, so the elves, the
 * guard and the burning tent are only ever seen by the player the scene is for. The world tile to
 * come back to is saved in `varp.regicide_scene_return` before the scene starts; a player who
 * logs out inside one is put back there on their next login instead of into a copy that no
 * longer exists.
 */
@Singleton
class RegicideScenes
@Inject
constructor(private val regionRepo: RegionRepository, private val npcRepo: NpcRepository) {
    private val held = HashMap<Long, Region>()

    internal suspend fun ProtectedAccess.privateScene(
        template: RegionTemplate,
        vantage: CoordGrid,
        faceAt: CoordGrid,
        camera: SceneCamera,
        returnTo: CoordGrid,
        underFade: suspend ProtectedAccess.(SceneRegion) -> Unit = {},
        body: suspend ProtectedAccess.(SceneRegion) -> Unit,
    ) {
        val access = this
        val region = regionRepo.add(template)
        region?.let(regionRepo::protect)
        val scene = SceneRegion(region)
        player.sceneReturn = returnTo.packed
        player.uuid?.let { uuid -> region?.let { held[uuid] = it } }
        try {
            lockedScene(
                vantage = scene[vantage],
                faceAt = scene[faceAt],
                camera = scene.map(camera),
                returnTo = returnTo,
                underFade = { access.underFade(scene) },
            ) {
                body(scene)
            }
        } finally {
            player.sceneReturn = 0
            player.uuid?.let(held::remove)
            region?.let(regionRepo::unprotect)
        }
    }

    fun spawn(type: String, coords: CoordGrid, lifespan: Int = SCENE_LIFESPAN): Npc {
        val npc = Npc(type, coords)
        npc.respawns = false
        npcRepo.add(npc, lifespan)
        npc.mode = NpcMode.None
        return npc
    }

    fun remove(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    /** Puts a player who logged out mid-scene back on the world tile the scene left from. */
    fun restoreLogin(player: Player) {
        val packed = player.sceneReturn
        if (packed == 0) {
            return
        }
        player.coords = CoordGrid(packed)
        player.sceneReturn = 0
    }

    fun discardLeakedRegion(player: Player) {
        val region = player.uuid?.let(held::remove) ?: return
        regionRepo.unprotect(region)
    }

    companion object {
        const val SCENE_LIFESPAN = 200
    }
}
