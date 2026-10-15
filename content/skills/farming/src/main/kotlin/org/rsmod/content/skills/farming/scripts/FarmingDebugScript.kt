package org.rsmod.content.skills.farming.scripts

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onCommand
import org.rsmod.content.skills.farming.data.FarmingPatch
import org.rsmod.content.skills.farming.data.FarmingPatches
import org.rsmod.content.skills.farming.state.FarmingStore
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Infects the nearest patch, so that the disease and cure paths can be reached on demand rather
 * than waited out - left to chance they are a roll of a few in a hundred on each growth cycle.
 *
 * The command turns down every state the game itself never produces: an empty patch, a dead crop, a
 * crop on the cycle it was sown or the one that finished it, and one a gardener is watching. Those
 * last two are the pair of conditions the growth loop's own roll is guarded by, so a patch this
 * command refuses is exactly a patch that could never have caught anything. A test run against a
 * state no player could meet would prove nothing - least of all one that infected a protected crop,
 * which would hide the protection failing to hold.
 */
class FarmingDebugScript @Inject constructor(private val store: FarmingStore) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("cropdisease") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Infect the crop in the nearest farming patch"
            cheat { infect(player) }
        }
    }

    private fun infect(player: Player) {
        val patch = nearestPatch(player)
        if (patch == null) {
            player.mes("There are no farming patches near you.")
            return
        }
        val state = store.state(player, patch)
        val crop = state.crop
        val name = crop?.displayName
        when {
            crop == null -> player.mes("There is nothing growing in that ${patch.kind.label}.")
            state.dead -> player.mes("The $name is already dead.")
            state.diseased -> player.mes("The $name is already diseased.")
            !crop.canCatchDisease(state.stage) ->
                player.mes("A $name cannot catch anything at this stage of its growth.")
            state.protectedByFarmer ->
                player.mes("A gardener is watching that $name; it cannot catch anything.")
            else -> {
                store.update(player, patch) { it.diseased = true }
                val infected = store.state(player, patch)
                VarPlayerIntMapSetter.set(player, patch.varbit, infected.varbitValue())
                player.mes("The $name in the ${patch.kind.label} is now diseased.")
            }
        }
    }

    private fun nearestPatch(player: Player): FarmingPatch? =
        FarmingPatches.visibleFrom(player.coords).minByOrNull {
            abs(it.coords.x - player.coords.x) + abs(it.coords.z - player.coords.z)
        }
}
