package org.rsmod.content.skills.construction.house

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.content.skills.construction.Construction
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Entering, leaving and rebuilding a house.
 *
 * A layout or build-mode change means the client has to be handed a whole new scene, so rather than
 * patching a standing region the house is assembled again from scratch and the player is dropped
 * back onto the tile they were on. Clearing [Player.buildArea] is what forces that scene to be sent.
 */
@Singleton
class HouseAccess
@Inject
constructor(private val registry: HouseRegistry, private val store: HouseStore) {
    fun enter(access: ProtectedAccess, buildMode: Boolean): Boolean {
        val player = access.player
        val state = store.state(player)
        if (!state.owned) {
            access.mes("You don't own a house. Speak to an estate agent to buy one.")
            return false
        }
        val entrance = registry.open(player, state, buildMode)
        if (entrance == null) {
            access.mes("There is no room for your house right now. Try again shortly.")
            return false
        }
        VarPlayerIntMapSetter.set(player, BUILD_MODE_VARBIT, if (buildMode) 1 else 0)
        access.moveInto(entrance)
        access.soundSynth(Construction.TELEPORT_SOUND)
        return true
    }

    fun leave(access: ProtectedAccess) {
        val player = access.player
        val state = store.state(player)
        registry.close(player)
        VarPlayerIntMapSetter.set(player, BUILD_MODE_VARBIT, 0)
        access.telejump(state.location.arrive)
        access.soundSynth(Construction.TELEPORT_SOUND)
    }

    /** Reassembles the house after the layout or the build mode changed. */
    fun rebuild(access: ProtectedAccess, buildMode: Boolean = registry.active(access.player)?.buildMode == true) {
        val player = access.player
        store.save(player)
        val destination = registry.reopen(player, buildMode) ?: return
        VarPlayerIntMapSetter.set(player, BUILD_MODE_VARBIT, if (buildMode) 1 else 0)
        access.moveInto(destination)
    }

    /**
     * Sends a player who logged out - or was otherwise stranded - inside a dead region home.
     *
     * Clearing the build area matters on login: the scene has already been decided by the time this
     * runs, so moving the player alone leaves the client drawing the region that no longer exists.
     */
    fun evict(player: Player) {
        registry.close(player)
        VarPlayerIntMapSetter.set(player, BUILD_MODE_VARBIT, 0)
        if (RegionRegistry.inWorkingArea(player.coords)) {
            player.coords = store.state(player).location.arrive
            player.buildArea = CoordGrid.NULL
        }
    }

    fun exitCoords(player: Player): CoordGrid = store.state(player).location.arrive

    private fun ProtectedAccess.moveInto(dest: CoordGrid) {
        player.buildArea = CoordGrid.NULL
        telejump(dest)
    }

    private companion object {
        const val BUILD_MODE_VARBIT = "varbit.poh_building_mode"
    }
}
