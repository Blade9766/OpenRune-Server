package org.rsmod.content.skills.magic.lunar

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.script.onEvent
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.skills.construction.data.PlankType
import org.rsmod.events.EventBus
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Plank Make: one log into one plank, for [COST_PERCENT]% of what the sawmill charges. The log,
 * the coins and the plank swap in one transaction that is tried before the runes are taken and
 * committed after, so the spell either does all of it or none of it.
 */
class PlankMakeScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val launcher: ProtectedAccessLauncher,
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val spell = LunarDreamSpell.PlankMake.component.asRSCM(RSCMType.COMPONENT)
        val key = EventBus.composeLongKey(spell, INVENTORY_ITEMS.asRSCM(RSCMType.COMPONENT))
        onEvent<IfOverlayButtonT>(key) {
            val target = targetObj ?: return@onEvent resendSlot(player.inv, 0)
            if (player.isDelayed || player.isAccessProtected) {
                resendSlot(player.inv, 0)
                return@onEvent
            }
            val slot = targetSlot
            player.clearPendingAction(eventBus)
            launcher.launch(player) { plankMake(slot, target) }
        }
    }

    internal fun ProtectedAccess.plankMake(slot: Int, target: ItemServerType) {
        if (actionDelay > mapClock) {
            return
        }
        val spell = LunarDreamSpell.PlankMake.resolve(spells) ?: return
        val plank = PlankType.forLogs(target.internalName)
        if (plank == null) {
            mes("You need to cast this spell on logs that a sawmill would accept.")
            return
        }
        if (inv[slot]?.id != target.id) {
            resendSlot(inv, 0)
            return
        }
        val cost = cost(plank)
        if (inv.count(COINS) < cost) {
            mes("You need $cost coins to turn those logs into a plank.")
            return
        }
        if (!runes.canCastSpell(player, spell) || !convert(slot, plank, cost, commit = false)) {
            return
        }
        if (runes.attemptCast(player, spell).isFailure()) {
            return
        }
        if (!convert(slot, plank, cost, commit = true)) {
            return
        }
        actionDelay = mapClock + CAST_DELAY
        anim(LunarDreamSpell.PlankMake.seq)
        spotanim(LunarDreamSpell.PlankMake.spotanim)
        statAdvance("stat.magic", spell.castXp)
    }

    private fun ProtectedAccess.convert(slot: Int, plank: PlankType, cost: Int, commit: Boolean): Boolean =
        player.invTransaction(inv, autoCommit = commit) {
            val pack = select(inv)
            delete {
                from = pack
                obj = plank.logs.asRSCM(RSCMType.OBJ)
                strictCount = 1
                strictSlot = slot
            }
            delete {
                from = pack
                obj = COINS.asRSCM(RSCMType.OBJ)
                strictCount = cost
            }
            insert {
                into = pack
                obj = plank.plank.asRSCM(RSCMType.OBJ)
                strictCount = 1
            }
        }.success

    companion object {
        const val COINS = "obj.coins"
        const val INVENTORY_ITEMS = "component.inventory:items"
        const val COST_PERCENT = 70
        const val CAST_DELAY = 3

        fun cost(plank: PlankType): Int = plank.cost * COST_PERCENT / 100
    }
}
