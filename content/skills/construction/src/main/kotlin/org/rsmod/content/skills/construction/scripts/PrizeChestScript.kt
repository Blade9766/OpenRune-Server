package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.skills.construction.data.Games
import org.rsmod.content.skills.construction.house.HouseGames
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.content.skills.construction.house.prizeCoins
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The games room prize chest.
 *
 * The owner puts coins in by using them on it, up to the chest's limit, and can open it to take
 * them back out. Anyone else needs a prize key, won from a game played in the house: opening the
 * chest with one uses the key up and takes the whole prize, and everyone in the house hears who won.
 */
class PrizeChestScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val games: HouseGames,
    private val locRepo: LocRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (chest in Games.PrizeChest.entries) {
            onOpLoc1(chest.closed) { open(it.loc, chest) }
            onOpLocU(chest.closed, COINS) { deposit(chest) }
            onOpLocU(chest.closed, Games.PRIZE_KEY) { open(it.loc, chest) }
        }
    }

    private suspend fun ProtectedAccess.open(loc: BoundLocInfo, chest: Games.PrizeChest) {
        val house = registry.houseAt(player.coords) ?: return
        val owner = house.owner
        // The prize is kept on the owner, who may have logged out; it can't be paid out without them.
        if (registry.isVacated(house)) {
            mes("The chest won't open while the owner is away.")
            return
        }
        if (owner !== player && !inv.contains(Games.PRIZE_KEY)) {
            mes("You need a prize key to open this chest.")
            return
        }
        anim(OPEN_SEQ)
        showOpen(loc, chest)
        val prize = owner.prizeCoins
        if (owner === player) {
            reclaim(prize)
            return
        }
        if (prize <= 0) {
            mes("The chest is empty.")
            return
        }
        if (invDel(inv, Games.PRIZE_KEY, 1).failure) {
            return
        }
        if (invAdd(inv, COINS, prize).failure) {
            invAdd(inv, Games.PRIZE_KEY, 1)
            mes("You don't have enough room to carry the prize.")
            return
        }
        owner.prizeCoins = 0
        mes("You claim the prize of $prize coins!")
        val inside = games.inside(house)
        for (other in inside) {
            if (other !== player) {
                other.mes("${player.displayName} has claimed the prize of $prize coins.")
            }
        }
        if (owner !in inside) {
            owner.mes("${player.displayName} has claimed the prize from your prize chest.")
        }
    }

    private suspend fun ProtectedAccess.reclaim(prize: Int) {
        if (prize <= 0) {
            mes("Your prize chest is empty.")
            return
        }
        val take = choice2("Take the $prize coins back.", true, "Leave them as the prize.", false, title = "The chest holds $prize coins.")
        // A guest may have claimed the prize while the owner was deciding.
        val left = player.prizeCoins
        if (!take || left <= 0) {
            if (take) {
                mes("Your prize chest is empty.")
            }
            return
        }
        if (invAdd(inv, COINS, left).failure) {
            mes("You don't have enough room to carry the coins.")
            return
        }
        player.prizeCoins = 0
        mes("You take the $left coins out of the prize chest.")
    }

    private suspend fun ProtectedAccess.deposit(chest: Games.PrizeChest) {
        val house = registry.houseAt(player.coords) ?: return
        if (house.owner !== player) {
            mes("Only the owner of this house can offer a prize.")
            return
        }
        val room = chest.limit - player.prizeCoins
        if (room <= 0) {
            mes("Your prize chest can't hold any more coins.")
            return
        }
        val amount = countDialog("How many coins? (up to $room)").coerceAtMost(room).coerceAtMost(inv.count(COINS))
        if (amount <= 0) {
            return
        }
        if (invDel(inv, COINS, amount).failure) {
            return
        }
        player.prizeCoins += amount
        mes("You put $amount coins in the prize chest. The prize is now ${player.prizeCoins} coins.")
    }

    private fun showOpen(loc: BoundLocInfo, chest: Games.PrizeChest) {
        val coords = loc.coords
        val angle = loc.angle
        val shape = loc.shape
        locRepo.del(loc, Int.MAX_VALUE)
        locRepo.add(coords, chest.open, OPEN_TICKS, angle, shape) {
            locRepo.add(coords, chest.closed, Int.MAX_VALUE, angle, shape)
        }
    }

    private companion object {
        const val COINS = "obj.coins"
        const val OPEN_SEQ = "seq.human_openchest"
        const val OPEN_TICKS = 3
    }
}
