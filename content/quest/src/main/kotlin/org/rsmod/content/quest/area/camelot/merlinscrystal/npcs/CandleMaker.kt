package org.rsmod.content.quest.area.camelot.merlinscrystal.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.BLACK_CANDLE
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.BUCKET_OF_WAX
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.CANDLE_MAKER
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_SPOKEN_MORGAN
import org.rsmod.content.quest.area.camelot.merlinscrystal.merlinCandlePromised
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Catherby candle maker. Black candles are bad luck in his trade, so he will only make one in
 * exchange for a bucket full of wax - and the only hives worth robbing for it are Morgan Le
 * Faye's.
 */
class CandleMaker
@Inject
constructor(private val quest: MerlinsCrystalQuest, private val shops: Shops) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(CANDLE_MAKER) { startDialogue(it.npc) { talk() } }
        onOpNpc3(CANDLE_MAKER) { openShop() }
    }

    private suspend fun Dialogue.talk() {
        if (player.merlinCandlePromised && quest.stage(player) == STAGE_SPOKEN_MORGAN) {
            deliverWax()
            return
        }
        chatNpc(happy, "Hi! Would you be interested in some of my fine candles?")
        val topic =
            if (quest.stage(player) == STAGE_SPOKEN_MORGAN) {
                choice3(
                    "Have you got any black candles?",
                    Topic.Black,
                    "Yes please.",
                    Topic.Buy,
                    "No thank you.",
                    Topic.Decline,
                )
            } else {
                choice2("Yes please.", Topic.Buy, "No thank you.", Topic.Decline)
            }
        when (topic) {
            Topic.Buy -> {
                chatPlayer(neutral, "Yes please.")
                access.openShop()
            }
            Topic.Decline -> chatPlayer(happy, "No thank you.")
            Topic.Black -> askForBlackCandle()
        }
    }

    private suspend fun Dialogue.askForBlackCandle() {
        chatPlayer(quiz, "Have you got any black candles?")
        chatNpc(shocked, "BLACK candles???")
        chatNpc(
            confused,
            "Hmmm. In the candle making trade, we have a tradition that it's very bad luck to " +
                "make black candles.",
        )
        chatNpc(confused, "VERY bad luck.")
        chatPlayer(angry, "I will pay good money for one...")
        chatNpc(confused, "I still dunno...")
        chatNpc(confused, "Tell you what. I'll supply you with a black candle...")
        chatNpc(confused, "IF you can bring me a bucket FULL of wax.")
        player.merlinCandlePromised = true
    }

    private suspend fun Dialogue.deliverWax() {
        chatNpc(quiz, "Have you got any wax yet?")
        if (!access.inv.contains(BUCKET_OF_WAX)) {
            chatPlayer(sad, "Nope, not yet.")
            return
        }
        chatPlayer(happy, "Yes, I have some now.")
        if (access.invDel(access.inv, BUCKET_OF_WAX).failure) {
            return
        }
        access.invAdd(access.inv, BLACK_CANDLE)
        objbox(BLACK_CANDLE, "You exchange the wax with the candle maker for a black candle.")
        player.merlinCandlePromised = false
    }

    private fun ProtectedAccess.openShop() {
        shops.open(
            player = player,
            title = SHOP_TITLE,
            shopInv = SHOP_INV,
            buyPercentage = BUY_PERCENTAGE,
            sellPercentage = SELL_PERCENTAGE,
            changePercentage = CHANGE_PERCENTAGE,
        )
    }

    private enum class Topic {
        Buy,
        Decline,
        Black,
    }

    private companion object {
        const val SHOP_TITLE = "Candle Shop"
        const val SHOP_INV = "inv.candleshop"
        const val BUY_PERCENTAGE = 40.0
        const val SELL_PERCENTAGE = 130.0
        const val CHANGE_PERCENTAGE = 3.0
    }
}
