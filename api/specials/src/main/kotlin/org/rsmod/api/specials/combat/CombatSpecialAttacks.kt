package org.rsmod.api.specials.combat

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

public interface CombatSpecialAttack<T : CombatAttack> {
    /**
     * Executes this special attack against an [Npc] target.
     *
     * #### Important Notes:
     * - This function is invoked **before** any special attack energy is deducted.
     * - Return `true` to allow the engine to deduct energy afterward.
     * - If `false` is returned, no energy will be deducted.
     * - If the weapon has a specialized requirement (e.g., Soulreaper Axe), the engine will **not**
     *   perform automatic energy checks or deductions. It is the responsibility of this function to
     *   validate such conditions.
     * - Any additional energy costs beyond the standard value can be deducted manually via
     *   [SpecialAttackManager.takeSpecialEnergy].
     * - If you intend to cancel the combat interaction, you **must** do so explicitly (e.g., via
     *   [SpecialAttackManager.stopCombat]); otherwise, the interaction may linger.
     * - Likewise, if you want the interaction to continue, call
     *   [SpecialAttackManager.continueCombat].
     *
     * @see [SpecialAttackEnergy.isSpecializedRequirement]
     */
    public suspend fun ProtectedAccess.attack(target: Npc, attack: T): Boolean

    /**
     * Executes this special attack against a [Player] target.
     *
     * #### Important Notes:
     * - This function is invoked **before** any special attack energy is deducted.
     * - Return `true` to allow the engine to deduct energy afterward.
     * - If `false` is returned, no energy will be deducted.
     * - If the weapon has a specialized requirement (e.g., Soulreaper Axe), the engine will **not**
     *   perform automatic energy checks or deductions. It is the responsibility of this function to
     *   validate such conditions.
     * - Any additional energy costs beyond the standard value can be deducted manually via
     *   [SpecialAttackManager.takeSpecialEnergy].
     * - If you intend to cancel the combat interaction, you **must** do so explicitly (e.g., via
     *   [SpecialAttackManager.stopCombat]); otherwise, the interaction may linger.
     * - Likewise, if you want the interaction to continue, call
     *   [SpecialAttackManager.continueCombat].
     *
     * @see [SpecialAttackEnergy.isSpecializedRequirement]
     */
    public suspend fun ProtectedAccess.attack(target: Player, attack: T): Boolean
}

public interface MeleeSpecialAttack : CombatSpecialAttack<CombatAttack.Melee>

public interface RangedSpecialAttack : CombatSpecialAttack<CombatAttack.Ranged>

public interface MagicSpecialAttack : CombatSpecialAttack<CombatAttack.Staff>

/**
 * A special attack belonging to a staff that casts a spell of its own, such as the nightmare
 * staves.
 *
 * Unlike [MagicSpecialAttack] these do not ride on a [CombatAttack], because the staff supplies
 * the whole attack itself: it is reachable both while a spell is autocast and while the staff is
 * being used to attack in melee, and the special behaves identically either way.
 */
public interface SpellSpecialAttack {
    public suspend fun ProtectedAccess.attack(target: Npc, weapon: InvObj): Boolean

    public suspend fun ProtectedAccess.attack(target: Player, weapon: InvObj): Boolean
}

/**
 * A special attack belonging to a worn shield, such as the dragonfire shield.
 *
 * These are armed from the shield's own "Activate" option rather than the special attack orb, and
 * they cost no special attack energy - each shield governs its own cooldown. The armed special
 * replaces the player's next attack, whichever combat style they are using, so it is offered every
 * style rather than riding on a [CombatAttack].
 */
public interface ShieldSpecialAttack {
    public suspend fun ProtectedAccess.attack(target: Npc, shield: InvObj): Boolean

    public suspend fun ProtectedAccess.attack(target: Player, shield: InvObj): Boolean
}
