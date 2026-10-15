package org.rsmod.content.skills.construction.house

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.atomic.AtomicReference
import org.rsmod.api.area.checker.isInWildernessBasic
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.fold
import org.rsmod.api.invtx.invDel
import org.rsmod.api.net.central.CentralSocialService
import org.rsmod.api.net.central.OpenRuneCentralWorldLink.CentralSocialSnapshotResult
import org.rsmod.api.player.ironman.IronmanActivity
import org.rsmod.api.player.ironman.IronmanRestrictions
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.content.skills.construction.Construction
import org.rsmod.content.skills.construction.data.Combat
import org.rsmod.content.skills.construction.data.Dungeon
import org.rsmod.content.skills.construction.data.HouseLocation
import org.rsmod.content.skills.construction.data.Kitchen
import org.rsmod.content.skills.construction.scripts.ServantDialogues
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Entering, leaving and rebuilding a house, for its owner and for guests.
 *
 * A layout or build-mode change means the client has to be handed a whole new scene, so rather than
 * patching a standing region the house is assembled again from scratch and the player is dropped
 * back onto the tile they were on. Clearing [Player.buildArea] is what forces that scene to be sent.
 *
 * A guest is simply anyone standing in another player's house: they go wherever its owner's
 * changes take it - along to the rebuilt house, or out to its portal when the owner starts
 * building. When the owner leaves, guests stay: the house goes on standing until the last of them
 * is out (see [HouseRegistry.vacate]), and an owner who comes back finds them still there.
 */
@Singleton
class HouseAccess
@Inject
constructor(
    private val registry: HouseRegistry,
    private val store: HouseStore,
    private val players: PlayerList,
    private val collision: CollisionFlagMap,
    private val db: GameDbManager,
    private val social: CentralSocialService,
    private val servants: ServantDialogues,
    private val playerRepo: PlayerRepository,
    private val regions: RegionRegistry,
    private val houseServants: HouseServants,
) {
    fun owns(player: Player): Boolean = store.state(player).owned

    fun buildMode(player: Player): Boolean = registry.active(player)?.buildMode == true

    fun enter(access: ProtectedAccess, buildMode: Boolean, sound: Boolean = true): Boolean {
        val player = access.player
        val state = store.state(player)
        if (!state.owned) {
            access.mes("You don't own a house. Speak to an estate agent to buy one.")
            return false
        }
        // A house left standing for its guests is reopened like any other, and they are carried
        // across to it - unless the owner is coming back to build, which puts them out.
        val live = registry.active(player)
        val before = live ?: registry.reclaim(player)
        val guests = before?.let { registry.guests(it, players) }.orEmpty()
        if (buildMode) {
            expelGuests(player)
        }
        val entrance = registry.open(player, state, buildMode)
        if (entrance == null) {
            // A house just taken back from its guests goes back to them, rather than stand ownerless.
            if (live == null) {
                registry.vacate(player, players)
            }
            access.mes("There is no room for your house right now. Try again shortly.")
            return false
        }
        VarPlayerIntMapSetter.set(player, BUILD_MODE_VARBIT, if (buildMode) 1 else 0)
        access.moveInto(entrance)
        val after = registry.active(player)
        if (!buildMode && before != null && after != null) {
            for (guest in guests) {
                move(guest, registry.carry(before, after, guest.coords) ?: entrance)
            }
        }
        player.timer(Dungeon.TRAP_TIMER, 1)
        if (sound) {
            access.soundSynth(Construction.TELEPORT_SOUND)
        }
        // Coming here may have emptied a house its owner left; nothing else would notice.
        registry.sweep(players)
        return true
    }

    /**
     * Takes [access]'s player into the house of the player called [name], if that house stands at
     * [location], its owner is home and not building, and the owner lets them in (see
     * [HouseVisitors]). An ironman can't visit at all.
     *
     * The owner's private chat setting, friends and ignores live in the central social service, so
     * they are fetched on the way in. When that service can't be reached the visit is let through,
     * as it was before the rule existed, rather than shutting every house.
     */
    suspend fun visit(access: ProtectedAccess, name: String, location: HouseLocation): Boolean {
        val guest = access.player
        guest.attr[LAST_VISITED] = name.trim()
        val owner = players.firstOrNull { it.displayName.equals(name.trim(), ignoreCase = true) }
        if (owner === guest) {
            return enter(access, buildMode = false)
        }
        if (IronmanRestrictions.block(guest, IronmanActivity.POH)) {
            return false
        }
        if (owner != null && isHome(owner, location) && isLocked(owner)) {
            access.mes("That player's house is locked.")
            return false
        }
        if (owner == null || !isHome(owner, location) || !welcomes(access, owner)) {
            access.mes("They do not seem to be at home.")
            return false
        }
        // The owner may have gone out, or started building, while their settings were fetched.
        val house = registry.active(owner)
        if (house == null || !isHome(owner, location)) {
            access.mes("They do not seem to be at home.")
            return false
        }
        if (house.buildMode) {
            access.mes("That player is in building mode.")
            return false
        }
        if (isLocked(owner)) {
            access.mes("That player's house is locked.")
            return false
        }
        registry.vacate(guest, players)
        VarPlayerIntMapSetter.set(guest, BUILD_MODE_VARBIT, 0)
        access.moveInto(registry.entranceOf(house))
        access.soundSynth(Construction.TELEPORT_SOUND)
        guest.timer(Dungeon.TRAP_TIMER, 1)
        registry.sweep(players)
        with(servants) { access.greetGuest(owner) }
        return true
    }

    fun isLocked(owner: Player): Boolean = owner.vars[HouseVisitors.LOCKED_VARBIT] == 1

    /**
     * The exit portal's Lock: an owner in their own house locks it against visitors, or unlocks it.
     * Guests already in it stay.
     */
    fun toggleLock(access: ProtectedAccess) {
        val player = access.player
        if (registry.houseAt(player.coords)?.owner !== player) {
            access.mes("Only the owner of this house can lock it.")
            return
        }
        val locked = !isLocked(player)
        VarPlayerIntMapSetter.set(player, HouseVisitors.LOCKED_VARBIT, if (locked) 1 else 0)
        access.mes(if (locked) "You lock your house. Nobody else can come in." else "You unlock your house.")
    }

    /**
     * An owner found outside every house - gone by a spell, a tablet or a jewellery teleport rather
     * than the exit portal - leaves their house to its guests just as the portal would.
     */
    fun leftWithoutPortal(player: Player) {
        if (registry.isScrying(player)) {
            return
        }
        // Guests too: a spirit tree or a tablet takes them out without the portal's tidying up.
        stripHouseItems(player)
        if (registry.active(player) == null || registry.isInside(player)) {
            return
        }
        registry.vacate(player, players)
        VarPlayerIntMapSetter.set(player, BUILD_MODE_VARBIT, 0)
    }

    /** The name last given to a portal or board to visit, for the board's Visit-Last. */
    fun lastVisited(player: Player): String? = player.attr[LAST_VISITED]

    private fun isHome(owner: Player, location: HouseLocation): Boolean {
        val house = registry.active(owner) ?: return false
        return house.state.location == location && registry.isInside(owner)
    }

    private suspend fun welcomes(access: ProtectedAccess, owner: Player): Boolean {
        if (owner.characterId <= 0) {
            return true
        }
        val reply = AtomicReference<CentralSocialSnapshotResult?>()
        val answered = AtomicReference(false)
        db.request(
            request = { social.socialSnapshot(owner.characterId) },
            response = { result ->
                reply.set(result.fold(onOk = { it }, onErr = { null }))
                answered.set(true)
            },
        )
        var waited = 0
        while (!answered.get() && waited < SOCIAL_TIMEOUT) {
            access.delay(1)
            waited++
        }
        val snapshot = (reply.get() as? CentralSocialSnapshotResult.Ok)?.snapshot ?: return true
        return HouseVisitors.admits(
            privateChat = snapshot.privateChat,
            friends = snapshot.friends.map { it.displayName },
            ignores = snapshot.ignores.map { it.displayName },
            guest = access.player.displayName,
        )
    }

    /**
     * The exit portal: an owner closes their house behind them, a guest just walks out of it. They
     * land outside the house's portal, or at [to] when a portal chamber portal sends them elsewhere.
     */
    fun leave(access: ProtectedAccess, sound: Boolean = true, to: CoordGrid? = null) {
        val player = access.player
        val visited = registry.houseAt(player.coords)?.takeIf { it.owner !== player }
        stripHouseItems(player)
        if (visited != null) {
            access.telejump(to ?: visited.state.location.arrive)
            registry.sweep(players)
        } else {
            registry.vacate(player, players)
            VarPlayerIntMapSetter.set(player, BUILD_MODE_VARBIT, 0)
            access.telejump(to ?: store.state(player).location.arrive)
        }
        if (sound) {
            access.soundSynth(Construction.TELEPORT_SOUND)
        }
    }

    /**
     * Reassembles the house after the layout or the build mode changed. A [preview] is built in its
     * place without being saved, to show a change the player has not made yet; the next rebuild
     * without one puts the real house back.
     */
    fun rebuild(
        access: ProtectedAccess,
        buildMode: Boolean = registry.active(access.player)?.buildMode == true,
        preview: HouseState? = null,
    ) {
        val player = access.player
        if (preview == null) {
            store.save(player)
        }
        val before = registry.active(player)
        val guests = before?.let { registry.guests(it, players) }.orEmpty()
        if (buildMode && before != null) {
            registry.sendOutScriers(before)
        }
        val destination = registry.reopen(player, buildMode, preview ?: store.state(player)) ?: return
        VarPlayerIntMapSetter.set(player, BUILD_MODE_VARBIT, if (buildMode) 1 else 0)
        access.moveInto(destination)
        player.timer(Dungeon.TRAP_TIMER, 1)
        val after = registry.active(player) ?: return
        for (guest in guests) {
            val landing = if (buildMode) null else before?.let { registry.carry(it, after, guest.coords) }
            if (landing == null) {
                guest.mes(STARTED_BUILDING)
                move(guest, after.state.location.arrive)
            } else {
                move(guest, landing)
            }
        }
    }

    /**
     * Sends a player who logged out - or was otherwise stranded - inside a dead region out to the
     * portal of the house they were in, or of their own.
     *
     * Clearing the build area matters on login: the scene has already been decided by the time this
     * runs, so moving the player alone leaves the client drawing the region that no longer exists.
     */
    fun evict(player: Player) {
        // A logout drops any errand still queued, so the servant isn't out on one any more.
        houseServants.forget(player)
        registry.endScry(player)?.let { scry ->
            playerRepo.reveal(player)
            player.coords = scry.origin
        }
        val house = registry.houseAt(player.coords)
        val visited = house?.takeIf { it.owner !== player }
        if (house != null) {
            stripHouseItems(player)
        }
        registry.vacate(player, players)
        VarPlayerIntMapSetter.set(player, BUILD_MODE_VARBIT, 0)
        // Only a house, or a region that is gone altogether: other instances see to their own players.
        val stranded = house == null && RegionRegistry.inWorkingArea(player.coords) && regions[player.coords] == null
        if (house != null || stranded) {
            player.coords = (visited?.state?.location ?: store.state(player).location).arrive
            player.buildArea = CoordGrid.NULL
        }
        registry.sweep(players)
    }

    /**
     * Tidies up after a death in a house, once the player is out at its portal: house-only items go,
     * and an owner's house closes behind them.
     */
    fun afterDeath(player: Player) {
        stripHouseItems(player)
        if (registry.active(player) != null && !registry.isInside(player)) {
            registry.vacate(player, players)
            VarPlayerIntMapSetter.set(player, BUILD_MODE_VARBIT, 0)
        }
        registry.sweep(players)
    }

    /**
     * Scrying, from a scrying pool or a portal nexus in Scry mode: the player gazes at [coords] for
     * [SCRY_TICKS] without going there, then comes back into the house through its loading screen,
     * to where they stood. The client can only draw what is around its player, so the player is
     * hidden, held still and moved there, and the house is kept up for them meanwhile. A place in
     * the Wilderness is never shown; the player is told how many players are near it instead. Old
     * School's own scry spots are not in the cache, so the teleport's landing spot is shown, and how
     * long a look lasts is a guess.
     */
    suspend fun scry(access: ProtectedAccess, coords: CoordGrid) {
        val player = access.player
        if (coords.isInWildernessBasic()) {
            val near = players.count { it !== player && it.coords.level == coords.level && it.coords.chebyshev(coords) <= SCRY_RADIUS }
            access.mes(if (near == 1) "There is 1 player in that area." else "There are $near players in that area.")
            return
        }
        val house = registry.houseAt(player.coords) ?: return
        registry.beginScry(player, house)
        playerRepo.hide(player)
        try {
            access.moveInto(coords)
            access.delay(SCRY_TICKS)
        } finally {
            comeBack(access)
        }
    }

    private fun comeBack(access: ProtectedAccess) {
        val player = access.player
        val scry = registry.endScry(player) ?: return
        playerRepo.reveal(player)
        val outside = store.state(scry.owner).location.arrive
        val owner = scry.owner.uuid
        val house = registry.active(scry.owner)
        // A guest put out meanwhile, or whose host started building, comes back to the portal outside.
        val back =
            when {
                scry.sendOut -> outside
                player.uuid != owner && house?.buildMode == true -> outside
                registry.houseAt(scry.origin)?.owner?.uuid == owner -> scry.origin
                house != null -> registry.entranceOf(house)
                else -> outside
            }
        access.moveInto(back)
        player.timer(Dungeon.TRAP_TIMER, 1)
    }

    private fun CoordGrid.chebyshev(other: CoordGrid): Int = maxOf(kotlin.math.abs(x - other.x), kotlin.math.abs(z - other.z))

    /** Puts [guest] out of [house] at its portal. */
    fun sendOut(guest: Player, house: ActiveHouse) {
        guest.mes("You have been expelled from the house.")
        move(guest, house.state.location.arrive)
    }

    fun exitCoords(player: Player): CoordGrid = store.state(player).location.arrive

    /**
     * The house options' Expel Guests: puts everyone else in the player's own house out at its
     * portal. False when the player is not in their own house.
     */
    fun expelGuests(access: ProtectedAccess): Boolean {
        val player = access.player
        if (!registry.isInside(player)) {
            return false
        }
        expelGuests(player, EXPELLED)
        return true
    }

    /** Whether Teleport to House lands inside, per the house options' Teleport Inside. */
    fun teleportsInside(player: Player): Boolean = player.vars[TELEPORT_OUTSIDE_VARBIT] == 0

    /**
     * The build mode Teleport to House enters in: the current one when already home, otherwise the
     * house options' Default Building Mode.
     */
    fun teleportBuildMode(player: Player): Boolean =
        registry.active(player)?.buildMode ?: (player.vars[TELEPORT_BUILDING_VARBIT] == 1)

    /** Puts everyone visiting [owner]'s house out at its portal, telling them [why]. */
    private fun expelGuests(owner: Player, why: String = STARTED_BUILDING) {
        val house = registry.active(owner) ?: return
        registry.sendOutScriers(house)
        for (guest in registry.guests(house, players)) {
            guest.mes(why)
            move(guest, house.state.location.arrive)
        }
    }

    /** Teas and drinks served in a house do not survive leaving it. */
    /** Teas, drinks and the combat room's gear, carried or worn, don't survive leaving the house. */
    private fun stripHouseItems(player: Player) {
        for (obj in Kitchen.HOUSE_ONLY + Combat.RACK_ITEMS) {
            for (held in listOf(player.inv, player.worn)) {
                val count = held.count(obj)
                if (count > 0) {
                    player.invDel(held, obj, count)
                }
            }
        }
    }

    private fun move(player: Player, dest: CoordGrid) {
        if (registry.houseAt(dest) == null) {
            stripHouseItems(player)
        }
        player.buildArea = CoordGrid.NULL
        PathingEntityCommon.telejump(player, collision, dest)
    }

    private fun ProtectedAccess.moveInto(dest: CoordGrid) {
        player.buildArea = CoordGrid.NULL
        telejump(dest)
    }

    private companion object {
        /** How long a scry lasts: ten seconds. */
        const val SCRY_TICKS = 16

        /** How far round a Wilderness spot its players are counted. */
        const val SCRY_RADIUS = 16

        /** A name is not something a varp can hold, and this only has to last the session. */
        val LAST_VISITED = AttributeKey<String>()

        const val EXPELLED = "You have been expelled from the house."
        const val STARTED_BUILDING = "The owner of this house has started building, so you have been sent out."

        /** Cycles to wait for the central social service before letting a visitor through. */
        const val SOCIAL_TIMEOUT = 10

        const val BUILD_MODE_VARBIT = "varbit.poh_building_mode"
        const val TELEPORT_OUTSIDE_VARBIT = "varbit.poh_tele_toggle"
        const val TELEPORT_BUILDING_VARBIT = "varbit.poh_teleport_building_mode"
    }
}
