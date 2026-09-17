package org.rsmod.api.combat.formulas.attributes.collector

import jakarta.inject.Inject
import java.util.EnumSet
import org.rsmod.api.combat.commons.styles.AttackStyle
import org.rsmod.api.combat.formulas.attributes.DamageReductionAttributes
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.player.hat
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.legs
import org.rsmod.api.player.righthand
import org.rsmod.api.player.torso
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.type.getOrNull

public class DamageReductionAttributeCollector
@Inject
constructor(private val attackStyles: AttackStyles) {
    // `random` is an explicit parameter to indicate that this function relies on randomness
    // for certain effects, such as the Elysian spirit shield proc.
    public fun collectNvP(player: Player, random: GameRandom): EnumSet<DamageReductionAttributes> {
        val attributes = EnumSet.noneOf(DamageReductionAttributes::class.java)

        val shield = player.lefthand
        if (shield.isType("obj.elysian") && random.of(maxExclusive = 10) < 7) {
            attributes += DamageReductionAttributes.ElysianProc
        }

        val weaponType = getOrNull(player.righthand)
        if (weaponType != null && weaponType.isCategoryType("category.dinhs_bulwark")) {
            val attackStyle = attackStyles.get(player)
            val passiveDelay = player.vars["varp.dinhs_passive_delay"]
            val isPassiveDelayed = player.currentMapClock < passiveDelay
            if (attackStyle == AttackStyle.AggressiveMelee && !isPassiveDelayed) {
                attributes += DamageReductionAttributes.DinhsBlock
            }
        }

        if (EquipmentChecks.isJusticiarSet(player.hat, player.torso, player.legs)) {
            attributes += DamageReductionAttributes.Justiciar
        }

        return attributes
    }

    /**
     * As [collectNvP], plus the reductions that only apply to incoming melee damage.
     */
    public fun collectNvPMelee(
        player: Player,
        random: GameRandom,
    ): EnumSet<DamageReductionAttributes> {
        val attributes = collectNvP(player, random)
        if (player.powerOfDeathActive()) {
            attributes += DamageReductionAttributes.PowerOfDeath
        }
        return attributes
    }

    /**
     * As [collectPvP], plus the reductions that only apply to incoming melee damage.
     */
    public fun collectPvPMelee(
        player: Player,
        random: GameRandom,
    ): EnumSet<DamageReductionAttributes> {
        val attributes = collectPvP(player, random)
        if (player.powerOfDeathActive()) {
            attributes += DamageReductionAttributes.PowerOfDeath
        }
        return attributes
    }

    // The staff special sets the expiration; unequipping the staff clears it, so the clock is
    // the only thing left to check here.
    private fun Player.powerOfDeathActive(): Boolean =
        currentMapClock < vars["varp.sotd_spec_expiration"]

    // `random` is an explicit parameter to indicate that this function relies on randomness
    // for certain effects, such as the Elysian spirit shield proc.
    public fun collectPvP(player: Player, random: GameRandom): EnumSet<DamageReductionAttributes> {
        val attributes = EnumSet.noneOf(DamageReductionAttributes::class.java)

        val shield = player.lefthand
        if (shield.isType("obj.elysian") && random.of(maxExclusive = 10) < 7) {
            attributes += DamageReductionAttributes.ElysianProc
        }

        return attributes
    }
}
