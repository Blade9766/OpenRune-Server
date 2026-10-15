package org.rsmod.api.combat.formulas.maxhit

import java.util.EnumSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.formulas.attributes.DamageReductionAttributes

class PowerOfDeathReductionTest {
    @Test
    fun `power of death halves incoming damage`() {
        assertEquals(20, applyReductions(40, DamageReductionAttributes.PowerOfDeath))
        // Odd damage rounds down, as every other reduction in this path does.
        assertEquals(12, applyReductions(25, DamageReductionAttributes.PowerOfDeath))
    }

    @Test
    fun `power of death stacks with the elysian proc`() {
        val both =
            applyReductions(
                40,
                DamageReductionAttributes.ElysianProc,
                DamageReductionAttributes.PowerOfDeath,
            )
        // 40 -> 30 (elysian, 3/4) -> 15 (power of death, 1/2).
        assertEquals(15, both)
    }

    @Test
    fun `damage is untouched without the attribute`() {
        assertEquals(40, applyReductions(40))
    }

    private fun applyReductions(damage: Int, vararg attributes: DamageReductionAttributes): Int {
        val set = EnumSet.noneOf(DamageReductionAttributes::class.java)
        set.addAll(attributes)
        return MaxHitOperations.applyDamageReductions(
            startDamage = damage,
            activeDefenceBonus = null,
            reductionAttributes = set,
        )
    }
}
