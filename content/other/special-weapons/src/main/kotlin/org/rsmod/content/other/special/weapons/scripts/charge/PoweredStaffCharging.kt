package org.rsmod.content.other.special.weapons.scripts.charge

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.obj.charges.ObjChargeManager.Companion.isFailure
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.content.other.special.weapons.magic.PoweredStaffWeapons.Companion.CHARGES_VAROBJ
import org.rsmod.content.other.special.weapons.magic.PoweredStaffWeapons.Companion.TRIDENT_MAX_CHARGES
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Check, charge, uncharge, swap and dismantle options of the powered staves handled by
 * `PoweredStaffWeapons`. Every staff stores its charges in `varobj.powered_staff_charges`.
 *
 * Inventory op slots follow each obj's cache ops (op1 = Wield, then Check/Charge/Uncharge in the
 * order the cache lists them).
 */
class PoweredStaffCharging
@Inject
constructor(private val charges: ObjChargeManager, private val objRepo: ObjRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        registerTridents()
        registerSanguinesti()
        registerRevenantSceptres()
        registerWarpedSceptre()
        registerBoneStaff()
        registerEyeOfAyak()
        registerStarterStaff()
        registerCorruptedShadow()
    }

    /* Tridents: charged by using any of the runes on them, uncharging keeps the coins/scales. */
    private fun ScriptContext.registerTridents() {
        val seas =
            ChargeSpec(
                name = "trident",
                charged = "obj.tots_charged",
                uncharged = "obj.tots_uncharged",
                cost = listOf(deathRune(1), chaosRune(1), fireRune(5), Cost("obj.coins", 10)),
                refund = listOf(deathRune(1), chaosRune(1), fireRune(5)),
                max = TRIDENT_MAX_CHARGES,
            )
        val seasE = seas.copy(charged = "obj.tots_i_charged", uncharged = "obj.tots_i_uncharged", max = ENHANCED_TRIDENT_MAX_CHARGES)
        val seasOrn = seas.copy(charged = "obj.tots_charged_orn", uncharged = "obj.tots_uncharged_orn")
        val seasEOrn = seasE.copy(charged = "obj.tots_i_charged_orn", uncharged = "obj.tots_i_uncharged_orn")

        val swamp =
            seas.copy(
                charged = "obj.toxic_tots_charged",
                uncharged = "obj.toxic_tots_uncharged",
                cost = listOf(deathRune(1), chaosRune(1), fireRune(5), Cost("obj.snakeboss_scale", 1)),
            )
        val swampE = swamp.copy(charged = "obj.toxic_tots_i_charged", uncharged = "obj.toxic_tots_i_uncharged", max = ENHANCED_TRIDENT_MAX_CHARGES)
        val swampOrn = swamp.copy(charged = "obj.toxic_tots_charged_orn", uncharged = "obj.toxic_tots_uncharged_orn")
        val swampEOrn = swampE.copy(charged = "obj.toxic_tots_i_charged_orn", uncharged = "obj.toxic_tots_i_uncharged_orn")

        for (spec in listOf(seas, seasE, seasOrn, seasEOrn, swamp, swampE, swampOrn, swampEOrn)) {
            // Charged: [Wield, Check, Uncharge, ...] - Uncharged: [Wield, Check, ...]
            onHeld(spec.charged, 3) { inv, slot -> check(spec, inv[slot]) }
            onWornCheck(spec.charged) { check(spec, player.righthand) }
            onHeld(spec.charged, 4) { inv, slot -> uncharge(spec, inv, slot) }
            onHeld(spec.uncharged, 3) { inv, slot -> check(spec, inv[slot]) }
            for (cost in spec.cost) {
                onOpHeldU(spec.charged, cost.obj) { charge(spec, inv, it.firstSlot) }
                onOpHeldU(spec.uncharged, cost.obj) { charge(spec, inv, it.firstSlot) }
            }
        }

        // "(full)" tridents hold a fixed 2,500 charges until first used.
        for ((full, spec) in listOf("obj.tots" to seas, "obj.tots_orn" to seasOrn)) {
            onHeld(full, 3) { _, _ ->
                mes("Your trident has ${TRIDENT_MAX_CHARGES.formatAmount} charges remaining.")
            }
            onWornCheck(full) {
                mes("Your trident has ${TRIDENT_MAX_CHARGES.formatAmount} charges remaining.")
            }
            onHeld(full, 4) { inv, slot -> unchargeFull(spec, inv, slot) }
        }

        // Toxic tridents dismantle into the plain uncharged trident and the magic fang.
        onHeld("obj.toxic_tots_uncharged", 4) { inv, slot ->
            dismantle(inv, slot, "obj.tots_uncharged", "obj.magic_fang", "the magic fang")
        }
        onHeld("obj.toxic_tots_i_uncharged", 4) { inv, slot ->
            dismantle(inv, slot, "obj.tots_i_uncharged", "obj.magic_fang", "the magic fang")
        }

        // Ornamented tridents dismantle back into the base trident and the ornament kit.
        val ornaments =
            mapOf(
                "obj.tots_orn" to "obj.tots",
                "obj.tots_charged_orn" to "obj.tots_charged",
                "obj.tots_uncharged_orn" to "obj.tots_uncharged",
                "obj.tots_i_charged_orn" to "obj.tots_i_charged",
                "obj.tots_i_uncharged_orn" to "obj.tots_i_uncharged",
                "obj.toxic_tots_charged_orn" to "obj.toxic_tots_charged",
                "obj.toxic_tots_uncharged_orn" to "obj.toxic_tots_uncharged",
                "obj.toxic_tots_i_charged_orn" to "obj.toxic_tots_i_charged",
                "obj.toxic_tots_i_uncharged_orn" to "obj.toxic_tots_i_uncharged",
            )
        for ((orn, base) in ornaments) {
            onHeld(orn, 5) { inv, slot ->
                dismantle(inv, slot, base, "obj.demonic_trident_ornament_kit", "the ornament kit")
            }
        }
    }

    private fun ScriptContext.registerSanguinesti() {
        val sang =
            ChargeSpec(
                name = "Sanguinesti staff",
                charged = "obj.sanguinesti_staff",
                uncharged = "obj.sanguinesti_staff_uncharged",
                cost = listOf(Cost("obj.bloodrune", 2)),
                max = 20_000,
            )
        val holy = sang.copy(charged = "obj.sanguinesti_staff_or", uncharged = "obj.sanguinesti_staff_uncharged_or")
        for (spec in listOf(sang, holy)) {
            // Charged: [Wield, Check, Charge, Uncharge] - Uncharged: [Wield, Charge, ...]
            onHeld(spec.charged, 3) { inv, slot -> check(spec, inv[slot]) }
            onWornCheck(spec.charged) { check(spec, player.righthand) }
            onHeld(spec.charged, 4) { inv, slot -> charge(spec, inv, slot) }
            onHeld(spec.charged, 5) { inv, slot -> uncharge(spec, inv, slot) }
            onHeld(spec.uncharged, 3) { inv, slot -> charge(spec, inv, slot) }
            onOpHeldU(spec.charged, "obj.bloodrune") { charge(spec, inv, it.firstSlot) }
            onOpHeldU(spec.uncharged, "obj.bloodrune") { charge(spec, inv, it.firstSlot) }
        }
        onHeld("obj.sanguinesti_staff_uncharged_or", 5) { inv, slot ->
            dismantle(inv, slot, "obj.sanguinesti_staff_uncharged", "obj.tob_hardmode_kit", "the holy ornament kit")
        }
    }

    /*
     * Revenant sceptres: 1,000 ether activates an uncharged sceptre, then one ether per charge up
     * to 16,000. "Swap" toggles the built-in-spell form and the "(a)" autocast form while keeping
     * the charges.
     */
    private fun ScriptContext.registerRevenantSceptres() {
        val thammaron =
            RevenantSceptre(
                spec =
                    ChargeSpec(
                        name = "sceptre",
                        charged = "obj.wild_cave_sceptre_charged",
                        uncharged = "obj.wild_cave_sceptre_uncharged",
                        cost = listOf(Cost(ETHER, 1)),
                        max = REVENANT_MAX_CHARGES,
                    ),
                chargedAutocast = "obj.wild_cave_sceptre_charged_recol",
                unchargedAutocast = "obj.wild_cave_sceptre_uncharged_recol",
            )
        val accursed =
            RevenantSceptre(
                spec =
                    thammaron.spec.copy(
                        charged = "obj.wild_cave_accursed_charged",
                        uncharged = "obj.wild_cave_accursed_uncharged",
                    ),
                chargedAutocast = "obj.wild_cave_accursed_charged_recol",
                unchargedAutocast = "obj.wild_cave_accursed_uncharged_recol",
            )

        for (sceptre in listOf(thammaron, accursed)) {
            val spec = sceptre.spec
            for (charged in listOf(spec.charged, sceptre.chargedAutocast)) {
                // [Wield, Check, Swap, Uncharge]
                onHeld(charged, 3) { inv, slot -> check(spec, inv[slot]) }
                onWornCheck(charged) { check(spec, player.righthand) }
                onHeld(charged, 5) { inv, slot -> unchargeSceptre(spec, inv, slot) }
                onOpHeldU(charged, ETHER) { chargeSceptre(spec, inv, it.firstSlot) }
            }
            for (uncharged in listOf(spec.uncharged, sceptre.unchargedAutocast)) {
                onOpHeldU(uncharged, ETHER) { chargeSceptre(spec, inv, it.firstSlot) }
            }
            // [Wield, Dismantle, Swap, Drop] on the uncharged forms.
            onHeld(spec.charged, 4) { inv, slot -> swap(inv, slot, sceptre.chargedAutocast) }
            onHeld(sceptre.chargedAutocast, 4) { inv, slot -> swap(inv, slot, spec.charged) }
            onHeld(spec.uncharged, 4) { inv, slot -> swap(inv, slot, sceptre.unchargedAutocast) }
            onHeld(sceptre.unchargedAutocast, 4) { inv, slot -> swap(inv, slot, spec.uncharged) }
        }

        for (uncharged in listOf(thammaron.spec.uncharged, thammaron.unchargedAutocast)) {
            onHeld(uncharged, 3) { inv, slot -> dismantleThammaron(inv, slot) }
        }
        for (uncharged in listOf(accursed.spec.uncharged, accursed.unchargedAutocast)) {
            onHeld(uncharged, 3) { inv, slot ->
                dismantle(inv, slot, "obj.wild_cave_sceptre_uncharged", "obj.wbr_vetion_skull", "the skull")
            }
        }
    }

    private fun ScriptContext.registerWarpedSceptre() {
        val spec =
            ChargeSpec(
                name = "warped sceptre",
                charged = "obj.warped_sceptre",
                uncharged = "obj.warped_sceptre_uncharged",
                cost = listOf(chaosRune(2), Cost("obj.earthrune", 5)),
                max = 20_000,
            )
        // Charged: [Wield, Check, Charge, Uncharge] - Uncharged: [Wield, null, Charge, Drop]
        onHeld(spec.charged, 3) { inv, slot -> check(spec, inv[slot]) }
        onWornCheck(spec.charged) { check(spec, player.righthand) }
        onHeld(spec.charged, 4) { inv, slot -> charge(spec, inv, slot) }
        onHeld(spec.charged, 5) { inv, slot -> uncharge(spec, inv, slot) }
        onHeld(spec.uncharged, 4) { inv, slot -> charge(spec, inv, slot) }
        for (cost in spec.cost) {
            onOpHeldU(spec.charged, cost.obj) { charge(spec, inv, it.firstSlot) }
            onOpHeldU(spec.uncharged, cost.obj) { charge(spec, inv, it.firstSlot) }
        }
    }

    /* The bone staff has no uncharged obj, so it is charged by using chaos runes on it. */
    private fun ScriptContext.registerBoneStaff() {
        val spec =
            ChargeSpec(
                name = "bone staff",
                charged = "obj.rat_bone_staff",
                uncharged = "obj.rat_bone_staff",
                cost = listOf(chaosRune(1)),
                max = 20_000,
            )
        // [Wield, Check, Uncharge, Drop]
        onHeld(spec.charged, 3) { inv, slot -> check(spec, inv[slot]) }
        onWornCheck(spec.charged) { check(spec, player.righthand) }
        onHeld(spec.charged, 4) { inv, slot -> uncharge(spec, inv, slot) }
        onOpHeldU(spec.charged, "obj.chaosrune") { charge(spec, inv, it.firstSlot) }
    }

    /*
     * Eye of Ayak: one demon tear per charge, or two death runes and one chaos rune. Uncharging
     * refunds the rune equivalent.
     */
    private fun ScriptContext.registerEyeOfAyak() {
        val runes =
            ChargeSpec(
                name = "Eye of Ayak",
                charged = "obj.eye_of_ayak",
                uncharged = "obj.eye_of_ayak_uncharged",
                cost = listOf(deathRune(2), chaosRune(1)),
                max = 50_000,
            )
        val tears = runes.copy(cost = listOf(Cost("obj.demon_tear", 1)), refund = runes.cost)
        // Charged: [Wield, Check, Uncharge, Drop] - Uncharged: [Wield, Charge, Dismantle, Drop]
        onHeld(runes.charged, 3) { inv, slot -> check(runes, inv[slot]) }
        onWornCheck(runes.charged) { check(runes, player.righthand) }
        onHeld(runes.charged, 4) { inv, slot -> uncharge(runes, inv, slot) }
        onHeld(runes.uncharged, 3) { inv, slot -> chargeAyak(tears, runes, inv, slot) }
        onHeld(runes.uncharged, 4) { _, _ -> mes("The Eye of Ayak cannot be dismantled.") }
        onOpHeldU(runes.charged, "obj.demon_tear") { charge(tears, inv, it.firstSlot) }
        onOpHeldU(runes.uncharged, "obj.demon_tear") { charge(tears, inv, it.firstSlot) }
        for (cost in runes.cost) {
            onOpHeldU(runes.charged, cost.obj) { charge(runes, inv, it.firstSlot) }
            onOpHeldU(runes.uncharged, cost.obj) { charge(runes, inv, it.firstSlot) }
        }
    }

    private fun ScriptContext.registerStarterStaff() {
        for (obj in listOf("obj.deadman_starter_staff", "obj.deadman_apocalypse_staff")) {
            onHeld(obj, 3) { _, _ -> mes("The starter staff has an endless supply of Fire Strikes.") }
        }
    }

    private fun ScriptContext.registerCorruptedShadow() {
        val spec =
            ChargeSpec(
                name = "Tumeken's shadow",
                charged = "obj.deadman_blighted_tumekens_shadow",
                uncharged = "obj.deadman_blighted_tumekens_shadow_uncharged",
                cost = listOf(chaosRune(5), Cost("obj.soulrune", 2)),
                max = 20_000,
            )
        // Charged: [Wield, Check, Charge, Uncharge] - Uncharged: [Wield, Charge, null, Drop]
        onHeld(spec.charged, 3) { inv, slot -> check(spec, inv[slot]) }
        onWornCheck(spec.charged) { check(spec, player.righthand) }
        onHeld(spec.charged, 4) { inv, slot -> charge(spec, inv, slot) }
        onHeld(spec.charged, 5) { inv, slot -> uncharge(spec, inv, slot) }
        onHeld(spec.uncharged, 3) { inv, slot -> charge(spec, inv, slot) }
        for (cost in spec.cost) {
            onOpHeldU(spec.charged, cost.obj) { charge(spec, inv, it.firstSlot) }
            onOpHeldU(spec.uncharged, cost.obj) { charge(spec, inv, it.firstSlot) }
        }
    }

    /* Shared actions */

    private fun ProtectedAccess.check(spec: ChargeSpec, obj: InvObj?) {
        val count = charges.getCharges(obj, CHARGES_VAROBJ)
        mes("Your ${spec.name} has ${count.formatAmount} charges remaining.")
    }

    private suspend fun ProtectedAccess.charge(spec: ChargeSpec, inventory: Inventory, slot: Int) {
        for (cost in spec.cost) {
            if (invTotal(inv, cost.obj) < cost.count) {
                mes("You need ${cost.describe()} to add a charge to your ${spec.name}.")
                return
            }
        }

        val current = charges.getCharges(inventory[slot], CHARGES_VAROBJ)
        if (current >= spec.max) {
            mes("Your ${spec.name} is already fully charged.")
            return
        }

        val affordable = spec.cost.minOf { invTotal(inv, it.obj) / it.count }
        val maxCharges = min(spec.max - current, affordable)
        val question = "How many charges do you want to apply? (Up to ${maxCharges.formatAmount})"
        val requested = min(countDialog(question), maxCharges)
        if (requested <= 0) {
            return
        }

        applyCharges(spec, inventory, slot, requested)
    }

    /**
     * Removes [requested] charges' worth of [ChargeSpec.cost] from the inventory and adds the
     * charges to the obj in [slot].
     */
    private suspend fun ProtectedAccess.applyCharges(
        spec: ChargeSpec,
        inventory: Inventory,
        slot: Int,
        requested: Int,
    ) {
        for (cost in spec.cost) {
            val removed = invDel(inv, cost.obj, cost.count * requested)
            if (removed.failure) {
                return
            }
        }
        val result = charges.addCharges(inventory, slot, requested, CHARGES_VAROBJ, spec.max)
        if (result.isFailure()) {
            return
        }
        objbox(spec.charged, "You apply ${requested.formatAmount} charges to your ${spec.name}.")
    }

    /** The Eye of Ayak's "Charge" option prefers demon tears and falls back to runes. */
    private suspend fun ProtectedAccess.chargeAyak(
        tears: ChargeSpec,
        runes: ChargeSpec,
        inventory: Inventory,
        slot: Int,
    ) {
        val spec = if ("obj.demon_tear" in inv) tears else runes
        charge(spec, inventory, slot)
    }

    private suspend fun ProtectedAccess.uncharge(spec: ChargeSpec, inventory: Inventory, slot: Int) {
        val current = charges.getCharges(inventory[slot], CHARGES_VAROBJ)
        if (current == 0) {
            mes("Your ${spec.name} has no charges to remove.")
            return
        }
        if (!confirmUncharge(spec)) {
            return
        }
        val removed = charges.removeAllCharges(inventory, slot, CHARGES_VAROBJ)
        refund(spec, removed)
    }

    /** A "(full)" trident: converts to the uncharged trident and refunds the runes it held. */
    private suspend fun ProtectedAccess.unchargeFull(spec: ChargeSpec, inventory: Inventory, slot: Int) {
        if (!confirmUncharge(spec)) {
            return
        }
        val obj = inventory[slot] ?: return
        inventory[slot] = InvObj(spec.uncharged, vars = obj.vars)
        refund(spec, TRIDENT_MAX_CHARGES)
    }

    private suspend fun ProtectedAccess.confirmUncharge(spec: ChargeSpec): Boolean {
        val needed = spec.refund.count { it.obj !in inv }
        if (inv.freeSpace() < needed) {
            mes("You don't have enough inventory space to uncharge your ${spec.name}.")
            return false
        }
        return choice2(
            "Proceed.",
            true,
            "Cancel.",
            false,
            title = "Uncharge all the charges from your ${spec.name}?",
        )
    }

    private suspend fun ProtectedAccess.refund(spec: ChargeSpec, chargesRemoved: Int) {
        val parts = mutableListOf<String>()
        for (cost in spec.refund) {
            val count = cost.count * chargesRemoved
            invAddOrDrop(objRepo, cost.obj, count)
            parts += "${count.formatAmount} ${objName(cost.obj, count)}"
        }
        val regained = if (parts.isEmpty()) "" else ", regaining ${parts.joinToString(" and ")}"
        objbox(spec.uncharged, "You uncharge your ${spec.name}$regained.")
    }

    /* Revenant sceptres */

    private suspend fun ProtectedAccess.chargeSceptre(spec: ChargeSpec, inventory: Inventory, slot: Int) {
        val current = charges.getCharges(inventory[slot], CHARGES_VAROBJ)
        val ether = invTotal(inv, ETHER)
        val activation = if (current == 0) REVENANT_ACTIVATION_ETHER else 0
        if (ether <= activation) {
            if (activation > 0) {
                mes("You need at least ${(activation + 1).formatAmount} revenant ether to activate the sceptre.")
            } else {
                mes("You need some revenant ether to charge the sceptre.")
            }
            return
        }
        if (current >= spec.max) {
            mes("Your ${spec.name} is already fully charged.")
            return
        }

        val maxCharges = min(spec.max - current, ether - activation)
        val question = "How many charges do you want to apply? (Up to ${maxCharges.formatAmount})"
        val requested = min(countDialog(question), maxCharges)
        if (requested <= 0) {
            return
        }

        val removed = invDel(inv, ETHER, activation + requested)
        if (removed.failure) {
            return
        }
        val result = charges.addCharges(inventory, slot, requested, CHARGES_VAROBJ, spec.max)
        if (result.isFailure()) {
            return
        }
        val activated = if (activation > 0) " You use ${activation.formatAmount} ether to activate the sceptre." else ""
        objbox(inventory[slot] ?: return, "You apply ${requested.formatAmount} charges to your sceptre.$activated")
    }

    private suspend fun ProtectedAccess.unchargeSceptre(spec: ChargeSpec, inventory: Inventory, slot: Int) {
        val current = charges.getCharges(inventory[slot], CHARGES_VAROBJ)
        if (current == 0) {
            mes("Your sceptre has no charges to remove.")
            return
        }
        if (ETHER !in inv && inv.freeSpace() < 1) {
            mes("You don't have enough inventory space to uncharge your sceptre.")
            return
        }
        val confirmation =
            choice2(
                "Proceed.",
                true,
                "Cancel.",
                false,
                title = "Uncharge all the charges from your sceptre?",
            )
        if (!confirmation) {
            return
        }
        val removed = charges.removeAllCharges(inventory, slot, CHARGES_VAROBJ)
        val total = removed + REVENANT_ACTIVATION_ETHER
        invAddOrDrop(objRepo, ETHER, total)
        objbox(inventory[slot] ?: return, "You uncharge your sceptre, regaining ${total.formatAmount} revenant ether.")
    }

    /** Swaps between the built-in-spell and "(a)" autocast forms, keeping the charges. */
    private fun ProtectedAccess.swap(inventory: Inventory, slot: Int, into: String) {
        val obj = inventory[slot] ?: return
        inventory[slot] = InvObj(into, vars = obj.vars)
        mes("You swap the sceptre's form.")
    }

    private suspend fun ProtectedAccess.dismantleThammaron(inventory: Inventory, slot: Int) {
        val confirmation =
            choice2(
                "Proceed.",
                true,
                "Cancel.",
                false,
                title = "Dismantle the sceptre for ${THAMMARON_DISMANTLE_ETHER.formatAmount} revenant ether?",
            )
        if (!confirmation) {
            return
        }
        val obj = inventory[slot] ?: return
        val removed = invDel(inventory, getInvObj(obj).internalName, count = 1, slot = slot)
        if (removed.failure) {
            return
        }
        invAddOrDrop(objRepo, ETHER, THAMMARON_DISMANTLE_ETHER)
        mes("You dismantle the sceptre and recover ${THAMMARON_DISMANTLE_ETHER.formatAmount} revenant ether.")
    }

    /** Turns the obj in [slot] into [base] (keeping its charges) and hands back [part]. */
    private fun ProtectedAccess.dismantle(inventory: Inventory, slot: Int, base: String, part: String, partName: String) {
        if (inv.freeSpace() < 1) {
            mes("You need a free inventory space to remove $partName.")
            return
        }
        val obj = inventory[slot] ?: return
        inventory[slot] = InvObj(base, vars = obj.vars)
        invAddOrDrop(objRepo, part, 1)
        mes("You remove $partName.")
    }

    /* Helpers */

    private fun ScriptContext.onHeld(
        obj: String,
        op: Int,
        action: suspend ProtectedAccess.(Inventory, Int) -> Unit,
    ) {
        when (op) {
            1 -> onOpHeld1(obj) { action(it.inventory, it.slot) }
            2 -> onOpHeld2(obj) { action(it.inventory, it.slot) }
            3 -> onOpHeld3(obj) { action(it.inventory, it.slot) }
            4 -> onOpHeld4(obj) { action(it.inventory, it.slot) }
            5 -> onOpHeld5(obj) { action(it.inventory, it.slot) }
            else -> error("Invalid held op: $op")
        }
    }

    /** "Check" is the first worn option on every charged staff. */
    private fun ScriptContext.onWornCheck(obj: String, action: suspend ProtectedAccess.() -> Unit) {
        onOpWorn2(obj) { action() }
    }

    private fun objName(obj: String, count: Int = 1): String {
        val type = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: error("No item: $obj")
        val name = type.name.lowercase()
        return if (count == 1 || name.endsWith("s")) name else name + "s"
    }

    private fun Cost.describe(): String = "$count x ${obj.substringAfter("obj.").replace('_', ' ')}"

    private data class Cost(val obj: String, val count: Int)

    private data class ChargeSpec(
        val name: String,
        val charged: String,
        val uncharged: String,
        /** Per-charge cost. */
        val cost: List<Cost>,
        val max: Int,
        /** What uncharging returns per charge; coins and scales are lost on tridents. */
        val refund: List<Cost> = cost,
    )

    private data class RevenantSceptre(
        val spec: ChargeSpec,
        val chargedAutocast: String,
        val unchargedAutocast: String,
    )

    private companion object {
        const val ETHER = "obj.wild_cave_shard"
        const val ENHANCED_TRIDENT_MAX_CHARGES = 20_000
        const val REVENANT_MAX_CHARGES = 16_000
        const val REVENANT_ACTIVATION_ETHER = 1_000
        const val THAMMARON_DISMANTLE_ETHER = 7_500

        fun deathRune(count: Int) = Cost("obj.deathrune", count)

        fun chaosRune(count: Int) = Cost("obj.chaosrune", count)

        fun fireRune(count: Int) = Cost("obj.firerune", count)
    }
}
