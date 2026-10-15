package org.rsmod.content.other.special.weapons.scripts.charge

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Charging, checking, reverting and uncharging of the crystal bow and the Bow of Faerdhinen.
 *
 * Crystal shards are used on the bow directly: each shard adds 100 charges up to 20,000. An
 * inactive bow becomes active again with the first shard. Reverting a crystal bow turns it back
 * into a crystal weapon seed; uncharging a Bow of Faerdhinen makes it inactive, and uncharging a
 * corrupted bow drops it back to the inactive bow. Shards spent on charges are never refunded.
 *
 * Corrupting a Bow of Faerdhinen (2,000 shards at a singing bowl) is singing bowl content and is
 * not handled here.
 */
class CrystalBowCharging @Inject constructor(private val charges: ObjChargeManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (bow in CHARGEABLE_BOWS) {
            onOpHeldU(CRYSTAL_SHARD, bow.obj) { addShards(inv, it.secondSlot, it.second, bow) }
        }

        for (bow in CHARGEABLE_BOWS.filter { it.hasCheckOp }) {
            onOpHeld3(bow.obj) { checkCharges(it.inventory[it.slot], bow) }
            onOpWorn2(bow.obj) { checkCharges(player.righthand, bow) }
        }

        onOpHeld4("obj.crystal_bow") { revertCrystalBow(inv, it.slot, "obj.crystal_bow") }
        onOpHeld4("obj.crystal_bow_2500") { revertCrystalBow(inv, it.slot, "obj.crystal_bow_2500") }
        onOpHeld4("obj.crystal_bow_inactive") { revertCrystalBow(inv, it.slot, "obj.crystal_bow_inactive") }

        onOpHeld4("obj.bow_of_faerdhinen") { unchargeFaerdhinen(inv, it.slot) }
        for (corrupted in CORRUPTED_BOWS) {
            onOpHeld4(corrupted) { unchargeCorrupted(inv, it.slot, corrupted) }
        }
    }

    private suspend fun ProtectedAccess.addShards(
        inventory: Inventory,
        bowSlot: Int,
        bowType: ItemServerType,
        bow: CrystalBow,
    ) {
        val current = charges.getCharges(inventory[bowSlot], CRYSTAL_CHARGES)
        if (current >= MAX_CHARGES) {
            mes("Your ${bow.name} is already fully charged.")
            return
        }

        val shards = invTotal(inv, CRYSTAL_SHARD)
        if (shards == 0) {
            mes("You don't have any crystal shards to charge the bow with.")
            return
        }

        val shardsToFill = (MAX_CHARGES - current + CHARGES_PER_SHARD - 1) / CHARGES_PER_SHARD
        val maxShards = min(shards, shardsToFill)
        val question = "How many crystal shards would you like to add? (Up to $maxShards)"
        val requested = min(countDialog(question), maxShards)
        if (requested <= 0) {
            return
        }

        val removeShards = invDel(inv, CRYSTAL_SHARD, requested)
        if (removeShards.failure) {
            return
        }

        // Paranoid check: the bow should not have moved while the count dialog was open.
        check(inventory[bowSlot].isType(bowType))

        charges.addCharges(
            inventory = inventory,
            slot = bowSlot,
            add = requested * CHARGES_PER_SHARD,
            internal = CRYSTAL_CHARGES,
            max = MAX_CHARGES,
        )
        val total = charges.getCharges(inventory[bowSlot], CRYSTAL_CHARGES)
        val shardWord = if (requested == 1) "shard" else "shards"
        mes(
            "You add $requested crystal $shardWord to your ${bow.name}. " +
                "It now has ${total.formatAmount} charges."
        )
    }

    private fun ProtectedAccess.checkCharges(obj: InvObj?, bow: CrystalBow) {
        val remaining = charges.getCharges(obj, CRYSTAL_CHARGES)
        mes("Your ${bow.name} has ${remaining.formatAmount} charges remaining.")
    }

    private suspend fun ProtectedAccess.revertCrystalBow(
        inventory: Inventory,
        bowSlot: Int,
        bow: String,
    ) {
        val remaining = charges.getCharges(inventory[bowSlot], CRYSTAL_CHARGES)
        val warning =
            if (remaining > 0) {
                "Revert the bow into a crystal weapon seed? " +
                    "Its ${remaining.formatAmount} charges will be lost."
            } else {
                "Revert the bow into a crystal weapon seed?"
            }
        val confirmed = choice2("Proceed.", true, "Cancel.", false, title = warning)
        if (!confirmed) {
            return
        }

        val removeBow = invDel(inventory, bow, count = 1, slot = bowSlot)
        if (removeBow.failure) {
            return
        }
        invAdd(inventory, CRYSTAL_WEAPON_SEED, count = 1, slot = bowSlot)
        objbox(CRYSTAL_WEAPON_SEED, 400, "You revert the crystal bow into a crystal weapon seed.")
    }

    private suspend fun ProtectedAccess.unchargeFaerdhinen(inventory: Inventory, bowSlot: Int) {
        val remaining = charges.getCharges(inventory[bowSlot], CRYSTAL_CHARGES)
        val confirmed =
            choice2(
                "Proceed.",
                true,
                "Cancel.",
                false,
                title =
                    "Uncharge the bow? Its ${remaining.formatAmount} charges will be lost " +
                        "and the bow will become inactive.",
            )
        if (!confirmed) {
            return
        }
        charges.removeAllCharges(inventory, bowSlot, CRYSTAL_CHARGES)
        objbox(
            "obj.bow_of_faerdhinen_inactive",
            400,
            "You uncharge the Bow of Faerdhinen. It is now inactive.",
        )
    }

    private suspend fun ProtectedAccess.unchargeCorrupted(
        inventory: Inventory,
        bowSlot: Int,
        corrupted: String,
    ) {
        val confirmed =
            choice2(
                "Proceed.",
                true,
                "Cancel.",
                false,
                title =
                    "Uncharge the corrupted bow? The shards used to corrupt it " +
                        "are lost and the bow will become inactive.",
            )
        if (!confirmed) {
            return
        }
        val removeBow = invDel(inventory, corrupted, count = 1, slot = bowSlot)
        if (removeBow.failure) {
            return
        }
        invAdd(inventory, "obj.bow_of_faerdhinen_inactive", count = 1, slot = bowSlot)
        objbox(
            "obj.bow_of_faerdhinen_inactive",
            400,
            "You uncharge the Bow of Faerdhinen. It is now inactive.",
        )
    }

    private data class CrystalBow(val obj: String, val name: String, val hasCheckOp: Boolean)

    private companion object {
        const val CRYSTAL_CHARGES = "varobj.crystal_weapon_charges"
        const val CRYSTAL_SHARD = "obj.prif_crystal_shard"
        const val CRYSTAL_WEAPON_SEED = "obj.crystal_seed_old"

        const val MAX_CHARGES = 20_000
        const val CHARGES_PER_SHARD = 100

        val CHARGEABLE_BOWS =
            listOf(
                CrystalBow("obj.crystal_bow", "crystal bow", hasCheckOp = true),
                CrystalBow("obj.crystal_bow_2500", "crystal bow", hasCheckOp = true),
                CrystalBow("obj.crystal_bow_inactive", "crystal bow", hasCheckOp = false),
                CrystalBow("obj.bow_of_faerdhinen", "Bow of Faerdhinen", hasCheckOp = true),
                CrystalBow(
                    "obj.bow_of_faerdhinen_inactive",
                    "Bow of Faerdhinen",
                    hasCheckOp = false,
                ),
            )

        val CORRUPTED_BOWS =
            listOf(
                "obj.bow_of_faerdhinen_infinite",
                "obj.bow_of_faerdhinen_infinite_ithell",
                "obj.bow_of_faerdhinen_infinite_iorwerth",
                "obj.bow_of_faerdhinen_infinite_trahaearn",
                "obj.bow_of_faerdhinen_infinite_cadarn",
                "obj.bow_of_faerdhinen_infinite_crwys",
                "obj.bow_of_faerdhinen_infinite_meilyr",
                "obj.bow_of_faerdhinen_infinite_amlodd",
            )
    }
}
