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
            gardener.first.takeIf(List<String>::isNotEmpty)?.let { patches ->
                onOpNpc3(gardener.npc) { pay(it.npc, patches) }
            }
            gardener.second.takeIf(List<String>::isNotEmpty)?.let { patches ->
                onOpNpc4(gardener.npc) { pay(it.npc, patches) }
            }
        }
    }

    /**
     * Most gardeners carry one "Pay" per patch they tend, so [patchLocs] holds a single patch and
     * the choice is already made. Alan is the exception: he watches both Farming Guild allotments
     * but the cache only gives him one "Pay", so his option falls to whichever of the two is
     * actually waiting on a farmer.
     */
    private suspend fun ProtectedAccess.pay(npc: Npc, patchLocs: List<String>) {
        val patchLoc = patchLocs.firstOrNull { awaitingProtection(it) } ?: patchLocs.first()
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
                chatNpc(
                    neutral,
                    "I'll watch over your ${crop.displayName} for ${payment.label}, but you " +
                        "haven't got that on you.",
                )
            }
            return
        }
        startDialogue(npc) {
            chatNpc(neutral, "I'll watch over your ${crop.displayName} for ${payment.label}.")
            val accepted =
                choice2(
                    "Yes, here you go.",
                    true,
                    "No thanks.",
                    false,
                    title = "Pay ${payment.label}?",
                )
            if (!accepted) {
                chatPlayer(neutral, "No thanks.")
                return@startDialogue
            }
            if (invDel(inv, payment.obj, payment.count).failure) {
                return@startDialogue
            }
            store.update(player, patch) { it.protectedByFarmer = true }
            chatNpc(happy, "A pleasure. I'll keep that ${crop.displayName} safe for you.")
        }
    }

    private fun ProtectedAccess.awaitingProtection(patchLoc: String): Boolean {
        val patch = FarmingPatches.forLoc(patchLoc) ?: return false
        val state = store.state(player, patch)
        return state.crop != null && !state.dead && !state.protectedByFarmer
    }

    private class Gardener(val npc: String, val first: List<String>, val second: List<String>)

    private companion object {
        /**
         * The op each patch hangs off comes from the gardener's own menu: "Pay (north-west)" and
         * friends are ops three and four, in the order the cache lists them, so the compass
         * direction has to match how the two patches actually sit relative to each other.
         */
        fun gardener(npc: String, first: String, second: String? = null) =
            Gardener(npc, listOf(first), listOfNotNull(second))

        val GARDENERS =
            listOf(
                // Pay (north-west) / Pay (south-east)
                gardener("npc.elstan", "loc.farming_veg_patch_1", "loc.farming_veg_patch_2"),
                gardener("npc.dantaera", "loc.farming_veg_patch_3", "loc.farming_veg_patch_4"),
                gardener("npc.kragen", "loc.farming_veg_patch_5", "loc.farming_veg_patch_6"),
                gardener("npc.lyra", "loc.farming_veg_patch_7", "loc.farming_veg_patch_8"),
                gardener("npc.fortis_gardener", "loc.farming_veg_patch_16", "loc.farming_veg_patch_17"),
                // Pay (north-east) / Pay (south-west)
                gardener(
                    "npc.hosidius_allotment_gardener",
                    "loc.farming_veg_patch_10",
                    "loc.farming_veg_patch_11",
                ),
                // Pay (north) / Pay (south)
                gardener("npc.prif_gardener", "loc.farming_veg_patch_14", "loc.farming_veg_patch_15"),
                // A single "Pay" covering both of the guild's allotments.
                Gardener(
                    "npc.farming_gardener_farmguild_t1",
                    listOf("loc.farming_veg_patch_12", "loc.farming_veg_patch_13"),
                    emptyList(),
                ),
                // Hops keepers tend one patch each.
                gardener("npc.farming_gardener_hops_1", "loc.farming_hops_patch_1"),
                gardener("npc.francis", "loc.farming_hops_patch_2"),
                gardener("npc.farming_gardener_hops_3", "loc.farming_hops_patch_3"),
                gardener("npc.farming_gardener_hops_4", "loc.farming_hops_patch_4"),
                gardener("npc.farming_gardener_hops_5", "loc.farming_hops_patch_5"),
            )
    }
}
