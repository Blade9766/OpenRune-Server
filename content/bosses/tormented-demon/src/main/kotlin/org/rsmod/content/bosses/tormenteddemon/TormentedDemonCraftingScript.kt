package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.stat.fletchingLvl
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TormentedDemonCraftingScript : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU("obj.bone_claw", "obj.bone_claw") { combineBurningClaws() }
        onOpLocCategoryU("category.anvil", "obj.tormented_synapse") { craftSynapseAtAnvil() }
        onOpHeldU("obj.tormented_synapse", "obj.unstrung_magic_longbow") { craftScorchingBow() }
    }

    private fun ProtectedAccess.combineBurningClaws() {
        if (invDel(inv, "obj.bone_claw", 2).success) {
            invAdd(inv, "obj.bone_claws", 1)
            mes("You bring the two claws together and combine them.")
        }
    }

    private suspend fun ProtectedAccess.craftSynapseAtAnvil() {
        if (!hasHammer()) {
            mesbox("You need a hammer to work metal with an anvil.")
            return
        }
        when {
            invTotal(inv, "obj.arclight") > 0 -> craftEmberlight()
            invTotal(inv, "obj.iron_bar") > 0 && invTotal(inv, "obj.battlestaff") > 0 ->
                craftPurgingStaff()
            else ->
                mesbox(
                    "You need Arclight, or an iron bar and a battlestaff, to work the " +
                        "tormented synapse into a weapon.",
                )
        }
    }

    private suspend fun ProtectedAccess.craftEmberlight() {
        if (player.smithingLvl < 74) {
            mesbox(
                "You need a Smithing level of at least 74 to attach the tormented synapse " +
                    "to Arclight.",
            )
            return
        }

        mesbox("You set to work fusing the synapse into Arclight's blade...")
        delay(3)
        anim("seq.human_smithing")
        soundSynth(3771)
        delay(4)

        if (fuse(listOf("obj.tormented_synapse", "obj.arclight"), "obj.emberlight")) {
            statAdvance("stat.smithing", 730.0)
            objbox("obj.emberlight", "The synapse fuses with the blade, and Emberlight is complete.")
        }
    }

    private suspend fun ProtectedAccess.craftPurgingStaff() {
        if (player.smithingLvl < 55 || player.craftingLvl < 74) {
            mesbox(
                "You need a Smithing level of at least 55 and a Crafting level of at least " +
                    "74 to work the tormented synapse into a battlestaff.",
            )
            return
        }

        mesbox("You set to work fusing the synapse into the battlestaff...")
        delay(3)
        anim("seq.human_smithing")
        soundSynth(3771)
        delay(4)

        val inputs = listOf("obj.tormented_synapse", "obj.iron_bar", "obj.battlestaff")
        if (fuse(inputs, "obj.purging_staff")) {
            statAdvance("stat.crafting", 730.0)
            statAdvance("stat.smithing", 13.0)
            objbox("obj.purging_staff", "The synapse fuses with the staff, and it starts to purge.")
        }
    }

    private suspend fun ProtectedAccess.craftScorchingBow() {
        if (player.fletchingLvl < 74) {
            mesbox(
                "You need a Fletching level of at least 74 to work the tormented synapse " +
                    "into a magic longbow.",
            )
            return
        }

        mesbox("You bind the synapse to the bow, and it begins to smoulder...")
        delay(3)
        anim("seq.stringing_magic_longbow")
        soundSynth(3771)
        delay(2)

        if (fuse(listOf("obj.tormented_synapse", "obj.unstrung_magic_longbow"), "obj.scorching_bow")) {
            statAdvance("stat.fletching", 730.0)
            objbox("obj.scorching_bow", "The synapse fuses with the bow, and it starts to smoulder.")
        }
    }

    private fun ProtectedAccess.fuse(inputs: List<String>, product: String): Boolean {
        if (!player.fuseSynapse(inv, inputs, product)) {
            mes("You need to keep all the materials in your inventory to finish.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.hasHammer(): Boolean =
        inv.contains("obj.hammer") ||
            inv.contains("obj.imcando_hammer") ||
            inv.contains("obj.imcando_hammer_offhand")
}

internal fun Player.fuseSynapse(inv: Inventory, inputs: List<String>, product: String): Boolean =
    invTransaction(inv) {
            val pack = select(inv)
            for (input in inputs) {
                delete {
                    from = pack
                    obj = input.asRSCM()
                    strictCount = 1
                }
            }
            insert {
                into = pack
                obj = product.asRSCM()
                strictCount = 1
            }
        }
        .success
