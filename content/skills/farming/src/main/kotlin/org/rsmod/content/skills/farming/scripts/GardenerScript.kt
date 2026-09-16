package org.rsmod.content.skills.farming.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.content.skills.farming.data.FarmingPatches
import org.rsmod.content.skills.farming.state.FarmingStore
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The gardeners who will watch a patch for the right payment.
 *
 * The allotment keepers carry two "Pay" options, one per allotment they tend, which is why they are
 * bound on ops three and four; the hops keepers tend a single patch and use op three only. Their
 * "Talk-to" belongs to Fairytale I's Group of Advanced Gardeners script and is left alone here.
 */
class GardenerScript @Inject constructor(private val store: FarmingStore) : PluginScript() {
    override fun ScriptContext.startup() {
        for (gardener in GARDENERS) {
            gardener.first?.let { patch -> onOpNpc3(gardener.npc) { pay(it.npc, patch) } }
            gardener.second?.let { patch -> onOpNpc4(gardener.npc) { pay(it.npc, patch) } }
        }
    }

    private suspend fun ProtectedAccess.pay(npc: Npc, patchLoc: String) {
        val patch = FarmingPatches.forLoc(patchLoc) ?: return
        val state = store.state(player, patch)
        val crop = state.crop
        if (crop == null) {
            startDialogue(npc) { chatNpc(neutral, "There's nothing growing in that patch yet.") }
            return
        }
        if (state.dead) {
            startDialogue(npc) { chatNpc(sad, "I'm afraid that crop is already beyond saving.") }
            return
        }
        if (state.protectedByFarmer) {
            startDialogue(npc) { chatNpc(happy, "Don't worry, I'm already watching that one.") }
            return
        }
        val payment = crop.protection
        if (payment == null) {
            startDialogue(npc) {
                chatNpc(neutral, "Sorry, that's not a crop I'm willing to look after.")
            }
            return
        }
        if (invTotal(inv, payment.obj) < payment.count) {
            startDialogue(npc) {
                chatNpc(neutral, "I'll watch over your ${crop.displayName} for ${payment.label}.")
            }
            return
        }
        if (invDel(inv, payment.obj, payment.count).failure) {
            return
        }
        store.update(player, patch) { it.protectedByFarmer = true }
        startDialogue(npc) {
            chatNpc(happy, "A pleasure. I'll keep that ${crop.displayName} safe for you.")
        }
    }

    private class Gardener(val npc: String, val first: String?, val second: String?)

    private companion object {
        val GARDENERS =
            listOf(
                Gardener("npc.elstan", "loc.farming_veg_patch_1", "loc.farming_veg_patch_2"),
                Gardener("npc.dantaera", "loc.farming_veg_patch_3", "loc.farming_veg_patch_4"),
                Gardener("npc.kragen", "loc.farming_veg_patch_5", "loc.farming_veg_patch_6"),
                Gardener("npc.lyra", "loc.farming_veg_patch_7", "loc.farming_veg_patch_8"),
                Gardener("npc.farming_gardener_hops_1", "loc.farming_hops_patch_1", null),
                Gardener("npc.francis", "loc.farming_hops_patch_2", null),
                Gardener("npc.farming_gardener_hops_3", "loc.farming_hops_patch_3", null),
                Gardener("npc.farming_gardener_hops_4", "loc.farming_hops_patch_4", null),
                Gardener("npc.farming_gardener_hops_5", "loc.farming_hops_patch_5", null),
            )
    }
}
