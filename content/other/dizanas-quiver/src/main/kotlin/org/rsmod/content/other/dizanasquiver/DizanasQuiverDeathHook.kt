package org.rsmod.content.other.dizanasquiver

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathItemHook
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.worn.DizanasQuiver
import org.rsmod.api.repo.obj.ObjRepository

/**
 * The ammunition stored in a Dizana's quiver is not part of any inventory, so the death drop
 * selection would never see it. Before the drops are worked out it is tipped into the inventory
 * (or onto the floor when that is full) so it is kept or lost by the usual rules.
 */
class DizanasQuiverDeathHook @Inject constructor(private val objRepo: ObjRepository) :
    PlayerDeathItemHook {
    override fun beforeDrops(context: PlayerDeathContext, handling: PlayerDeathHandling) {
        val player = context.player
        val stored = DizanasQuiver.storedAmmo(player) ?: return
        DizanasQuiver.setStoredAmmo(player, null)
        val name = RSCM.getReverseMapping(RSCMType.OBJ, stored.id)
        player.invAddOrDrop(objRepo, name, stored.count, coords = context.coords)
    }
}
