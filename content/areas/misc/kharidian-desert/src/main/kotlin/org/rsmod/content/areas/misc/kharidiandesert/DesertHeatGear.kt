package org.rsmod.content.areas.misc.kharidiandesert

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.feet
import org.rsmod.api.player.hands
import org.rsmod.api.player.hat
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.legs
import org.rsmod.api.player.torso
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj

/**
 * How long the desert lets a player go between drinks.
 *
 * The base is 150 ticks (90 seconds), per Mod Ash. Armour traps the heat and takes time off by
 * slot; desert-appropriate clothing adds time per piece, up to 220 ticks in a full desert set.
 * The cache has no armour category, so "armour" is anything with a melee defence bonus, except
 * magic robes, vambraces and non-metal boots, which the wiki lists as heat-neutral.
 */
internal object DesertHeatGear {
    private const val BASE_TICKS = 150
    private const val MIN_TICKS = 40
    private const val MAX_TICKS = 220

    private const val HAT_PENALTY = 10
    private const val TORSO_PENALTY = 40
    private const val LEGS_PENALTY = 30
    private const val FEET_PENALTY = 10
    private const val SHIELD_PENALTY = 10
    private const val HANDS_PENALTY = 10

    /** Extra ticks per worn piece of desert-appropriate clothing (12s = 20 ticks, 6s = 10, 3s = 5). */
    private val PROTECTION_BY_NAME: Map<String, Int> =
        mapOf(
            "obj.desert_shirt" to 20,
            "obj.desert_robe" to 20,
            "obj.desert_boots" to 10,
            "obj.agrith_desert_shirt_dyed" to 20,
            "obj.agrith_desert_robe_dyed" to 20,
            "obj.slave_shirt" to 10,
            "obj.slave_robe" to 10,
            "obj.slave_boots" to 5,
            "obj.roguetrader_fez_hat" to 20,
            "obj.roguetrader_carpetsller_top" to 20,
            "obj.roguetrader_carpetsller_top2" to 20,
            "obj.roguetrader_carpetsller_legs" to 20,
            "obj.roguetrader_carpetsller_legs2" to 20,
            "obj.roguetrader_menaphite_hat" to 20,
            "obj.roguetrader_menaphite_top" to 20,
            "obj.roguetrader_menaphite_legs" to 20,
            "obj.roguetrader_menaphite_legs2" to 20,
            "obj.roguetrader_menaphite_hat_red" to 20,
            "obj.roguetrader_menaphite_top_red" to 20,
            "obj.roguetrader_menaphite_legs_red" to 20,
            "obj.roguetrader_menaphite_legs_red2" to 20,
            "obj.motherlode_reward_hat" to 10,
            "obj.motherlode_reward_top" to 20,
            "obj.motherlode_reward_legs" to 20,
            "obj.motherlode_reward_boots" to 10,
            "obj.motherlode_reward_hat_gold" to 10,
            "obj.motherlode_reward_top_gold" to 20,
            "obj.motherlode_reward_legs_gold" to 20,
            "obj.motherlode_reward_boots_gold" to 10,
            "obj.fossil_motherlode_reward_hat" to 10,
            "obj.fossil_motherlode_reward_top" to 20,
            "obj.fossil_motherlode_reward_legs" to 20,
            "obj.fossil_motherlode_reward_boots" to 10,
            "obj.elid_robetop" to 20,
            "obj.elid_robebottoms" to 20,
            "obj.feud_desert_disguise" to 20,
            "obj.water_circlet_charged" to 20,
        )

    private val protection: Map<Int, Int> by lazy {
        PROTECTION_BY_NAME.mapKeys { (name, _) -> name.asRSCM(RSCMType.OBJ) }
    }

    private val METAL_BOOTS =
        listOf(
            "bronze", "iron", "steel", "black", "white", "mithril", "adamant", "rune", "dragon",
            "granite", "fancy", "fighting", "bandos", "guardian", "primordial",
        )

    fun interval(player: Player): Int {
        var ticks = BASE_TICKS
        ticks += slot(player.hat, HAT_PENALTY, ::isArmour)
        ticks += slot(player.torso, TORSO_PENALTY, ::isArmour)
        ticks += slot(player.legs, LEGS_PENALTY, ::isArmour)
        ticks += slot(player.feet, FEET_PENALTY, ::isMetalBoots)
        ticks += slot(player.lefthand, SHIELD_PENALTY, ::isArmour)
        ticks += slot(player.hands, HANDS_PENALTY, ::isGloves)
        return ticks.coerceIn(MIN_TICKS, MAX_TICKS)
    }

    private fun slot(obj: InvObj?, penalty: Int, heats: (InvObj) -> Boolean): Int {
        obj ?: return 0
        protection[obj.id]?.let { return it }
        return if (heats(obj)) -penalty else 0
    }

    private fun isArmour(obj: InvObj): Boolean {
        val type = getInvObj(obj)
        val meleeDefence =
            type.param(params.defence_stab) + type.param(params.defence_slash) +
                type.param(params.defence_crush)
        return meleeDefence > 0 && type.param(params.attack_magic) <= 0
    }

    private fun isGloves(obj: InvObj): Boolean =
        isArmour(obj) && !getInvObj(obj).name.contains("vambraces", ignoreCase = true)

    private fun isMetalBoots(obj: InvObj): Boolean {
        val name = getInvObj(obj).name.lowercase()
        return isArmour(obj) && "d'hide" !in name && METAL_BOOTS.any { name.contains(it) }
    }
}
