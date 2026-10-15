package org.rsmod.content.quest.area.varrock.ragandboneman.npcs

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.COINS
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.FORTUNATO
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.STAGE_VINEGAR
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.VINEGAR
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.VINEGAR_PRICE
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Fortunato, Draynor Village's wine merchant. His vinegar stays under the counter until the
 * player, sent by the Odd Old Man, asks for it; after that his shop stocks it at a coin a jug and
 * he will also sell jugs straight across the counter.
 */
class Fortunato
@Inject
constructor(
    private val rb: RagAndBoneManQuest,
    private val shops: Shops,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(FORTUNATO) { startDialogue(it.npc) { fortunato() } }
        onOpNpc3(FORTUNATO) { trade() }
    }

    private suspend fun Dialogue.fortunato() {
        chatNpc(neutral, "Can I help you at all?")
        chatNpc(happy, "Ah! Good afternoon to you. I take it you have come for a refill?")
        val options = buildList {
            add("Yes, let's see your wines." to Topic.TRADE)
            when {
                rb.isVinegarUnlocked(player) -> add("I'd like some vinegar." to Topic.BUY_VINEGAR)
                rb.isStarted(player) -> add("The Odd Old Man sent me for vinegar." to Topic.ODD_OLD_MAN)
                else -> add("Do you sell vinegar?" to Topic.ASK_VINEGAR)
            }
            add("Not today." to Topic.LEAVE)
        }
        when (menu(options)) {
            Topic.TRADE -> {
                chatPlayer(neutral, "Yes, let's see your wines.")
                access.trade()
            }
            Topic.ODD_OLD_MAN -> oddOldMan()
            Topic.BUY_VINEGAR -> buyVinegar()
            Topic.ASK_VINEGAR -> {
                chatPlayer(quiz, "Do you sell vinegar?")
                chatNpc(angry, "Vinegar? Sir or madam, I am a purveyor of fine wines. Vinegar is merely wine that has given up.")
                chatNpc(shifty, "...Though I may have the odd jug of it out the back for those with a genuine need.")
            }
            Topic.LEAVE -> chatPlayer(neutral, "Not today.")
        }
    }

    private suspend fun Dialogue.oddOldMan() {
        chatPlayer(neutral, "The Odd Old Man sent me for vinegar.")
        chatNpc(confused, "The one with the bones and the... lively sack? He does get through a lot of vinegar.")
        chatNpc(neutral, "Very well. Wine that turns isn't wine I can sell as wine, so I keep it for him. A coin a jug, and you'll want one jug for each bone.")
        rb.advanceTo(access, STAGE_VINEGAR)
        access.mes("Fortunato will now sell you jugs of vinegar.")
        buyVinegar()
    }

    private suspend fun Dialogue.buyVinegar() {
        chatNpc(quiz, "How many jugs will it be?")
        val count = menu("One jug." to 1, "Five jugs." to 5, "Eight jugs." to 8, "None, thanks." to 0)
        if (count == 0) {
            chatPlayer(neutral, "None, thanks.")
            return
        }
        when (val problem = purchaseProblem(access, count)) {
            null -> {
                sellVinegar(access, count)
                chatNpc(happy, "There you are. Do give him my regards, and keep upwind of the sack.")
            }
            else -> chatNpc(sad, problem)
        }
    }

    /** Why [count] jugs can't be bought, or null when the coins and the pack space are there. */
    fun purchaseProblem(access: ProtectedAccess, count: Int): String? {
        val price = count * VINEGAR_PRICE
        val coins = access.inv.count(COINS)
        if (coins < price) {
            return "That's $price coin${if (price == 1) "" else "s"}, and you've only $coins. No credit, I'm afraid."
        }
        val freedByCoins = if (coins == price) 1 else 0
        if (access.inv.freeSpace() + freedByCoins < count) {
            return "You haven't room for $count jug${if (count == 1) "" else "s"}. Make some space and come back."
        }
        return null
    }

    private fun sellVinegar(access: ProtectedAccess, count: Int) {
        access.player.invTransaction(access.inv) {
            val inventory = select(access.inv)
            delete {
                from = inventory
                obj = COINS.asRSCM()
                strictCount = count * VINEGAR_PRICE
            }
            insert {
                into = inventory
                obj = VINEGAR.asRSCM()
                strictCount = count
            }
        }
    }

    private fun ProtectedAccess.trade() {
        val unlocked = rb.isVinegarUnlocked(player)
        if (!unlocked && rb.isStarted(player)) {
            mes("Fortunato keeps his vinegar under the counter. Perhaps mention the Odd Old Man to him.")
        }
        shops.open(
            player = player,
            title = "Fortunato's Fine Wine.",
            shopInv = if (unlocked) VINEGAR_SHOP else WINE_SHOP,
            buyPercentage = 60.0,
            sellPercentage = 100.0,
            changePercentage = 2.0,
        )
    }

    private enum class Topic { TRADE, ODD_OLD_MAN, BUY_VINEGAR, ASK_VINEGAR, LEAVE }

    companion object {
        const val VINEGAR_SHOP = "inv.wine_vinegar_merchant"
        const val WINE_SHOP = "inv.rag_wine_merchant_no_vinegar"
    }
}
