package org.rsmod.content.other.commands

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.api.player.worn.HeldEquipResult
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Gear presets for admins. `::preset <name>` wears the preset's worn items (anything already worn
 * in those slots moves to the inventory) and adds its switches to the inventory;
 * `::preset <name> inv` puts every item in the inventory instead. `::preset` lists the presets.
 */
class AdminPresetCommands
@Inject
constructor(private val equipOp: HeldEquipOp, private val charges: ObjChargeManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("preset", "Spawn a gear preset (ex: ::preset mage)", ::preset) {
            invalidArgs = "Use as ::preset <name> [inv] (ex: ::preset mage)"
        }
    }

    private fun preset(cheat: Cheat) =
        with(cheat) {
            val name = args.getOrNull(0)?.lowercase()
            val preset = name?.let(PRESETS::get)
            if (preset == null) {
                player.mes("Presets: ${PRESETS.keys.joinToString()}. Use as ::preset <name> [inv]")
                return
            }
            val intoInv = args.getOrNull(1).equals("inv", ignoreCase = true)
            val required = preset.worn.size + preset.inv.size
            if (player.inv.freeSpace() < required) {
                player.mes("You need $required free inventory slots for the `$name` preset.")
                return
            }

            for (obj in preset.worn + preset.inv) {
                player.spawn(obj, preset.counts[obj] ?: 1)
            }
            if (intoInv) {
                player.mes("Added the `$name` preset to your inventory.")
                return
            }

            val failures = preset.worn.mapNotNull { obj -> player.equip(obj) }
            if (failures.isEmpty()) {
                player.mes("Equipped the `$name` preset.")
            } else {
                failures.forEach(player::mes)
                player.mes("Equipped the `$name` preset; unequippable items stayed in your inventory.")
            }
        }

    private fun Player.spawn(obj: String, count: Int) {
        val slot = inv.indices.first { inv[it] == null }
        val charge = CHARGED[obj]
        val spawnObj = charge?.uncharged ?: obj
        invAdd(inv, spawnObj.asRSCM(RSCMType.OBJ), count = count, slot = slot)
        if (charge != null) {
            charges.addCharges(inv, slot, charge.max, charge.varobj, charge.max)
        }
    }

    private fun Player.equip(obj: String): String? {
        val id = obj.asRSCM(RSCMType.OBJ)
        val slot = inv.indices.lastOrNull { inv[it]?.id == id } ?: return "Could not find $obj."
        val name = getInvObj(inv[slot]!!).name
        val result = equipOp.equip(this, slot, inv)
        if (result !is HeldEquipResult.Fail) {
            return null
        }
        return "$name: ${result.messages.lastOrNull() ?: "it cannot be worn."}"
    }

    private data class Preset(
        val worn: List<String>,
        val inv: List<String> = emptyList(),
        val counts: Map<String, Int> = emptyMap(),
    )

    private data class Charge(val uncharged: String, val varobj: String, val max: Int)

    private companion object {
        private val CHARGED =
            mapOf(
                "obj.tumekens_shadow" to
                    Charge("obj.tumekens_shadow_uncharged", "varobj.tumeken_charges", 20_000),
                "obj.bow_of_faerdhinen" to
                    Charge(
                        "obj.bow_of_faerdhinen_inactive",
                        "varobj.crystal_weapon_charges",
                        20_000,
                    ),
            )

        private val PRESETS =
            mapOf(
                "mage" to
                    Preset(
                        worn =
                            listOf(
                                "obj.ancestral_hat",
                                "obj.ancestral_robe_top",
                                "obj.ancestral_robe_bottom",
                                "obj.avernic_treads_max",
                                "obj.trail_mage_amulet",
                                "obj.ma2_saradomin_cape",
                                "obj.confliction_gauntlets",
                                "obj.magus_ring",
                                "obj.kodai_wand",
                                "obj.elidinis_ward_fortified",
                            ),
                        inv = listOf("obj.tumekens_shadow"),
                    ),
                "rangedstrength" to
                    Preset(
                        worn =
                            listOf(
                                "obj.masori_mask",
                                "obj.necklace_of_rupture",
                                "obj.masori_body",
                                "obj.masori_chaps",
                                "obj.avernic_treads_max",
                                "obj.dizanas_quiver_infinite",
                                "obj.zaryte_vambraces",
                                "obj.venator_ring",
                                "obj.bow_of_faerdhinen",
                            )
                    ),
                "prayer" to
                    Preset(
                        worn =
                            listOf(
                                "obj.sunfire_helm",
                                "obj.dragonbone_necklace",
                                "obj.sunfire_body",
                                "obj.sunfire_legs",
                                "obj.devout_boots",
                                "obj.soul_cape_red",
                                "obj.holy_wraps",
                                "obj.nzone_rotg",
                                "obj.trail_ancient_staff",
                                "obj.zarosbook_complete",
                                "obj.zeah_blessing_elite",
                            )
                    ),
                "crush" to
                    Preset(
                        worn =
                            listOf(
                                "obj.inquisitors_helm",
                                "obj.amulet_of_rancour",
                                "obj.inquisitors_body",
                                "obj.inquisitors_skirt",
                                "obj.avernic_treads_max",
                                "obj.mythical_cape",
                                "obj.ferocious_gloves",
                                "obj.nzone_heavy_ring",
                                "obj.elder_maul",
                            )
                    ),
                "rangeddef" to
                    Preset(
                        worn =
                            listOf(
                                "obj.justiciar_faceguard",
                                "obj.enchanted_onyx_amulet",
                                "obj.justiciar_chestguard",
                                "obj.justiciar_leg_guards",
                                "obj.guardian_boots",
                                "obj.infernal_cape",
                                "obj.hundred_gauntlets_level_10",
                                "obj.nzone_granite_ring",
                                "obj.crystal_shield",
                                "obj.zaryte_xbow",
                                "obj.dragon_bolts_enchanted_ruby",
                            ),
                        counts = mapOf("obj.dragon_bolts_enchanted_ruby" to 1_000),
                    ),
                "verac" to
                    Preset(
                        worn =
                            listOf(
                                "obj.barrows_verac_head",
                                "obj.damned_amulet",
                                "obj.barrows_verac_body",
                                "obj.barrows_verac_legs",
                                "obj.devout_boots",
                                "obj.hundred_gauntlets_level_10",
                                "obj.barrows_verac_weapon",
                            )
                    ),
            )
    }
}
