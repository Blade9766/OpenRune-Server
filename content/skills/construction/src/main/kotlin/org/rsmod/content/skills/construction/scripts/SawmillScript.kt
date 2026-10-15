package org.rsmod.content.skills.construction.scripts

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.skills.construction.data.PlankType
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The sawmill operators, who turn logs into the planks every piece of house furniture is built
 * from. Handing over logs and clicking "Buy-plank" reach the same place: pick a plank, pick an
 * amount, pay per plank, and the operator converts as many as the purse and the pack allow.
 */
class SawmillScript @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        for (operator in OPERATORS) {
            onOpNpc3(operator) { buyPlanks(it.npc, preferred = null) }
            onOpNpcU(operator) { event ->
                val used = RSCM.getReverseMapping(RSCMType.OBJ, event.objType.id)
                PlankType.forLogs(used)?.let { buyPlanks(event.npc, it) }
            }
        }
    }

    private suspend fun ProtectedAccess.buyPlanks(npc: Npc, preferred: PlankType?) {
        val plank = preferred ?: choosePlank(npc) ?: return
        val carried = invTotal(inv, plank.logs)
        if (carried <= 0) {
            startDialogue(npc) {
                chatNpc(neutral, "You don't have any ${logName(plank)} for me to cut.")
            }
            return
        }

        val requested = requestAmount(npc, plank, carried)
        if (requested <= 0) {
            return
        }

        val affordable = invCoinTotal() / plank.cost
        if (affordable <= 0) {
            startDialogue(npc) {
                chatNpc(
                    neutral,
                    "I charge ${plank.cost} coins a plank, and you can't afford even one.",
                )
            }
            return
        }

        val converted = minOf(requested, carried, affordable)
        if (!invTakeFee(converted * plank.cost)) {
            return
        }
        if (invDel(inv, plank.logs, converted).failure) {
            invAdd(inv, COINS, converted * plank.cost)
            return
        }
        invAdd(inv, plank.plank, converted)

        val noun = if (converted == 1) plank.label.lowercase() else "${plank.label.lowercase()}s"
        startDialogue(npc) {
            chatNpc(happy, "There you go: $converted $noun.")
            if (converted < requested) {
                chatNpc(neutral, "That's all I could cut with what you brought me.")
            }
        }
    }

    private suspend fun ProtectedAccess.choosePlank(npc: Npc): PlankType? {
        startDialogue(npc) {
            chatNpc(happy, "I can turn your logs into planks. Which would you like?")
        }
        val choice =
            menu(
                "Which plank?",
                hotkeys = true,
                choices = PlankType.entries.map { "${it.label} (${it.cost} coins each)" },
            )
        return PlankType.entries.getOrNull(choice)
    }

    private suspend fun ProtectedAccess.requestAmount(
        npc: Npc,
        plank: PlankType,
        carried: Int,
    ): Int {
        val choice =
            menu(
                "How many ${plank.label.lowercase()}s?",
                hotkeys = true,
                choices = listOf("1", "5", "10", "All ($carried)", "Enter amount"),
            )
        return when (choice) {
            0 -> 1
            1 -> 5
            2 -> 10
            3 -> carried
            4 -> countDialog("How many would you like?")
            else -> 0
        }
    }

    private fun logName(plank: PlankType): String =
        if (plank == PlankType.NORMAL) "logs" else "${plank.label.removeSuffix(" plank")} logs"

    private companion object {
        const val COINS = "obj.coins"

        val OPERATORS =
            listOf(
                "npc.poh_sawmill_opp",
                "npc.prif_sawmill_operator",
                "npc.auburn_sawmill_operator",
            )
    }
}
