package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.skills.construction.data.Treasure
import org.rsmod.content.skills.construction.house.ActiveHouse
import org.rsmod.content.skills.construction.house.HouseGuards
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The treasure room chest.
 *
 * The owner stocks it by using coins on it, up to the chest's limit and no more than once every five
 * minutes; every chest in the house shares the one hoard. A visitor can only open it once the
 * room's guardian is dead, and searching it takes the whole hoard - the owner and the finder are
 * both told who won and how much. An opened chest closes again after [OPEN_TICKS].
 */
class TreasureScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val guards: HouseGuards,
    private val locRepo: LocRepository,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (chest in Treasure.Chest.entries) {
            onOpLoc1(chest.closed) { open(it.loc, chest) }
            onOpLoc1(chest.open) { search() }
            onOpLocU(chest.closed, COINS) { deposit(chest) }
            onOpLocU(chest.open, COINS) { deposit(chest) }
        }
    }

    private fun ProtectedAccess.open(loc: BoundLocInfo, chest: Treasure.Chest) {
        val house = registry.houseAt(player.coords) ?: return
        if (house.owner !== player && guards.treasureGuarded(house.owner)) {
            mes("You must defeat the guardian before you can open the chest.")
            return
        }
        val coords = loc.coords
        val angle = loc.angle
        val shape = loc.shape
        locRepo.del(loc, Int.MAX_VALUE)
        locRepo.add(coords, chest.open, OPEN_TICKS, angle, shape) {
            locRepo.add(coords, chest.closed, Int.MAX_VALUE, angle, shape)
        }
    }

    private fun ProtectedAccess.search() {
        val house = registry.houseAt(player.coords) ?: return
        val owner = house.owner
        // The hoard is kept on the owner, who may have logged out; it can't be paid out without them.
        if (registry.isVacated(house)) {
            mes("The chest won't open while the owner is away.")
            return
        }
        val hoard = owner.treasure
        if (owner === player) {
            mes(if (hoard == 0) "Your treasure chest is empty." else "Your treasure chest holds $hoard coins.")
            return
        }
        // The owner can leave the chest standing open, which must not let a guest past the guardian.
        if (guards.treasureGuarded(owner)) {
            mes("You must defeat the guardian before you can search the chest.")
            return
        }
        if (hoard == 0) {
            mes("The chest is empty.")
            return
        }
        if (invAdd(inv, COINS, hoard).failure) {
            mes("You don't have enough room to carry the treasure.")
            return
        }
        owner.treasure = 0
        mes("You find $hoard coins in the chest!")
        announce(house, player, "${player.displayName} has won $hoard coins from the treasure chest!")
    }

    private suspend fun ProtectedAccess.deposit(chest: Treasure.Chest) {
        val house = registry.houseAt(player.coords) ?: return
        if (house.owner !== player) {
            mes("This isn't your treasure chest.")
            return
        }
        if (mapClock < player.treasureCooldown) {
            mes("You must wait a while before adding more treasure.")
            return
        }
        val room = chest.limit - player.treasure
        if (room <= 0) {
            mes("Your treasure chest can't hold any more coins.")
            return
        }
        val amount = countDialog("How many coins? (up to $room)").coerceAtMost(room).coerceAtMost(inv.count(COINS))
        if (amount <= 0) {
            return
        }
        if (invDel(inv, COINS, amount).failure) {
            return
        }
        player.treasure += amount
        player.treasureCooldown = mapClock + Treasure.DEPOSIT_COOLDOWN
        mes("You add $amount coins to your treasure chest. It now holds ${player.treasure} coins.")
    }

    /** Tells the owner and everyone else in [house] but [finder] what was won. */
    private fun announce(house: ActiveHouse, finder: Player, message: String) {
        for (other in players) {
            if (other === finder) {
                continue
            }
            if (other === house.owner || registry.houseAt(other.coords) === house) {
                other.mes(message)
            }
        }
    }

    private companion object {
        const val COINS = "obj.coins"
        const val OPEN_TICKS = 100
    }
}

private var Player.treasure by intVarp(Treasure.TREASURE_VARP)
private var Player.treasureCooldown by intVarp(Treasure.COOLDOWN_VARP)
