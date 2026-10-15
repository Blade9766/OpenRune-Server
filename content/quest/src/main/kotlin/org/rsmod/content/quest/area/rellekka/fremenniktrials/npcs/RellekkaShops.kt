package org.rsmod.content.quest.area.rellekka.fremenniktrials.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest
import org.rsmod.game.entity.Npc

enum class RellekkaShop(
    val title: String,
    val inv: String,
    val sellPercentage: Double,
    val buyPercentage: Double,
) {
    Longhall("Rellekka Longhall Bar", "inv.viking_bar", 130.0, 70.0),
    GeneralStore("Sigmund the Merchant", "inv.viking_general_store", 130.0, 50.0),
    BattleGear("Skulgrimen's Battle Gear", "inv.viking_weapons_shop", 130.0, 70.0),
    FishMonger("Fremennik Fish Monger", "inv.viking_fishmonger", 130.0, 70.0),
    FurTrader("Fremennik Fur Trader", "inv.viking_furshop", 120.0, 95.0),
}

/** Rellekka's traders deal only with members of the Fremennik. */
@Singleton
class RellekkaShops
@Inject
constructor(private val quest: FremennikTrialsQuest, private val shops: Shops) {
    suspend fun ProtectedAccess.trade(npc: Npc, shop: RellekkaShop) {
        if (!quest.isComplete(player)) {
            startDialogue(npc) {
                chatNpc(
                    neutral,
                    "I don't trade with outerlanders. Become a Fremennik, and perhaps we will do " +
                        "business.",
                )
            }
            return
        }
        shops.open(
            player = player,
            title = shop.title,
            shopInv = shop.inv,
            buyPercentage = shop.buyPercentage,
            sellPercentage = shop.sellPercentage,
            changePercentage = CHANGE_PERCENTAGE,
        )
    }

    private companion object {
        const val CHANGE_PERCENTAGE = 3.0
    }
}
