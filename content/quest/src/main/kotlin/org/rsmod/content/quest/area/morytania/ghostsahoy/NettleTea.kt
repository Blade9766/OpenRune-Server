package org.rsmod.content.quest.area.morytania.ghostsahoy

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOWL
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOWL_OF_MILKY_TEA
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOWL_OF_NETTLE_WATER
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOWL_OF_TEA
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOWL_OF_WATER
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BUCKET
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BUCKET_OF_MILK
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CUP_OF_MILKY_TEA
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CUP_OF_TEA
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.NETTLES
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.PORCELAIN_CUP
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Nettle tea outside the cooking range: nettles stirred into a bowl of water, milk added to the
 * tea, and the tea poured from its bowl into a cup. Boiling the nettle-water is an ordinary
 * cooking-table recipe (`dbrow.cooking_nettle_tea`), so fires and ranges handle it, Cooking level
 * 20 and the chance of the water boiling over included.
 */
class NettleTea : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU(NETTLES, BOWL_OF_WATER) { swap(NETTLES, BOWL_OF_WATER, null, BOWL_OF_NETTLE_WATER, "You place the nettles into the bowl of water.") }
        onOpHeldU(BUCKET_OF_MILK, BOWL_OF_TEA) { swap(BUCKET_OF_MILK, BOWL_OF_TEA, BUCKET, BOWL_OF_MILKY_TEA, "You add some milk to the nettle tea.") }
        onOpHeldU(BUCKET_OF_MILK, CUP_OF_TEA) { swap(BUCKET_OF_MILK, CUP_OF_TEA, BUCKET, CUP_OF_MILKY_TEA, "You add some milk to the cup of tea.") }
        onOpHeldU(BUCKET_OF_MILK, PLAIN_CUP_OF_TEA) { swap(BUCKET_OF_MILK, PLAIN_CUP_OF_TEA, BUCKET, PLAIN_CUP_OF_MILKY_TEA, "You add some milk to the cup of tea.") }
        onOpHeldU(BOWL_OF_TEA, PORCELAIN_CUP) { swap(BOWL_OF_TEA, PORCELAIN_CUP, BOWL, CUP_OF_TEA, POUR_MESSAGE) }
        onOpHeldU(BOWL_OF_MILKY_TEA, PORCELAIN_CUP) { swap(BOWL_OF_MILKY_TEA, PORCELAIN_CUP, BOWL, CUP_OF_MILKY_TEA, POUR_MESSAGE) }
        onOpHeldU(BOWL_OF_TEA, PLAIN_CUP) { swap(BOWL_OF_TEA, PLAIN_CUP, BOWL, PLAIN_CUP_OF_TEA, POUR_MESSAGE) }
        onOpHeldU(BOWL_OF_MILKY_TEA, PLAIN_CUP) { swap(BOWL_OF_MILKY_TEA, PLAIN_CUP, BOWL, PLAIN_CUP_OF_MILKY_TEA, POUR_MESSAGE) }
    }

    /** Turns [first] and [second] into [result] (and [leftover], when given) in one transaction. */
    private fun ProtectedAccess.swap(
        first: String,
        second: String,
        leftover: String?,
        result: String,
        message: String,
    ) {
        val made =
            player.invTransaction(inv) {
                val pack = select(inv)
                delete {
                    from = pack
                    obj = first.asRSCM()
                    strictCount = 1
                }
                delete {
                    from = pack
                    obj = second.asRSCM()
                    strictCount = 1
                }
                insert {
                    into = pack
                    obj = result.asRSCM()
                    strictCount = 1
                }
                if (leftover != null) {
                    insert {
                        into = pack
                        obj = leftover.asRSCM()
                        strictCount = 1
                    }
                }
            }
        if (made.failure) {
            mes("You don't have enough inventory space to do that.")
            return
        }
        mes(message)
    }

    private companion object {
        const val PLAIN_CUP = "obj.cup_empty"
        const val PLAIN_CUP_OF_TEA = "obj.cup_of_nettletea"
        const val PLAIN_CUP_OF_MILKY_TEA = "obj.cup_of_nettletea_milky"
        const val POUR_MESSAGE = "You pour the nettle tea into the cup."
    }
}
