package org.rsmod.content.quest.area.lunarisle.dreammentor

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

internal var Player.dreamCombatType by intVarBit("varbit.dream_combattype")
internal var Player.dreamHealth by intVarBit("varbit.dream_health")
internal var Player.dreamSpirit by intVarBit("varbit.dream_spirit")
internal var Player.dreamArmament by intVarBit("varbit.dream_armament")
internal var Player.dreamBankerIntro by boolVarBit("varbit.dream_banker_intro")
internal var Player.dreamCutsceneSeen by boolVarBit("varbit.dream_cutscene_seen")
internal var Player.dreamSpiritQuestion by intVarBit("varbit.dream_spirit_ques")
internal var Player.dreamLampUsed by boolVarBit("varbit.dream_lampused")
internal var Player.dreamDamageDealt by intVarBit("varbit.dream_damagedealt")
internal var Player.dreamCyrisusSpot by intVarBit("varbit.dream_cyris_multi")
internal var Player.dreamRecentFoodA by intVarBit("varbit.dream_mentor_food_a")
internal var Player.dreamRecentFoodB by intVarBit("varbit.dream_mentor_food_b")

/** The fighting style Cyrisus copies from the player, in `varbit.dream_combattype` values. */
enum class CombatStyle(val value: Int, val cyrisusType: String, val battleType: String) {
    Melee(1, "npc.dream_cyrisus_melee", "npc.dream_cyrisus_battle_melee"),
    Magic(2, "npc.dream_cyrisus_caster", "npc.dream_cyrisus_battle_caster"),
    Ranged(3, "npc.dream_cyrisus_ranger", "npc.dream_cyrisus_battle_ranger");

    companion object {
        fun of(player: Player): CombatStyle = entries.firstOrNull { it.value == player.dreamCombatType } ?: Melee

        /**
         * The style that sets the player's combat level: the larger of the melee, ranged and
         * magic terms of the combat formula, melee winning ties.
         */
        fun dominant(attack: Int, strength: Int, ranged: Int, magic: Int): CombatStyle {
            val melee = attack + strength
            val range = ranged * 3 / 2
            val mage = magic * 3 / 2
            return when {
                melee >= range && melee >= mage -> Melee
                range >= mage -> Ranged
                else -> Magic
            }
        }
    }
}

/** Where Cyrisus waits once he has left the cave, in `varbit.dream_cyris_multi` values. */
object CyrisusSpot {
    const val CAVE = 0

    fun oneiromancer(style: CombatStyle): Int = style.value

    fun brazier(style: CombatStyle): Int = style.value + BRAZIER_OFFSET

    private const val BRAZIER_OFFSET = 3
}
