package org.rsmod.api.player.worn

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.CategoryType
import org.rsmod.api.config.refs.params

/**
 * Pure checks for whether a piece of ammunition can be fired from a ranged weapon.
 *
 * These live outside the combat modules so that anything which needs to know what a player would
 * shoot (worn bonuses, the Dizana's quiver storage) can share the exact same rules as combat.
 */
public object RangedAmmoValidation {
    private val defaultArrows = CategoryType("category.arrows".asRSCM(RSCMType.CATEGORY))
    private val defaultBolts = CategoryType("category.crossbow_bolt".asRSCM(RSCMType.CATEGORY))
    private val defaultJavelins = CategoryType("category.javelin".asRSCM(RSCMType.CATEGORY))

    /** Whether [weapon] needs ammunition from the ammo slot to attack. */
    public fun requiresAmmo(weapon: ItemServerType): Boolean =
        weapon.isCategoryType("category.bow") ||
            weapon.isCategoryType("category.crossbow") ||
            weapon.isCategoryType("category.ballista")

    /**
     * Validates [ammo] against [weapon]. Returns `null` when [weapon] does not use ammunition from
     * the ammo slot at all.
     */
    public fun validate(weapon: ItemServerType, ammo: ItemServerType): Validation? =
        when {
            weapon.isCategoryType("category.crossbow") -> validateBolts(weapon, ammo)
            weapon.isCategoryType("category.bow") -> validateArrows(weapon, ammo)
            weapon.isCategoryType("category.ballista") -> validateJavelins(weapon, ammo)
            else -> null
        }

    /** `true` when [weapon] needs ammunition and [ammo] is usable with it. */
    public fun isUsable(weapon: ItemServerType, ammo: ItemServerType): Boolean =
        validate(weapon, ammo) is Validation.Valid

    public fun validateArrows(weapon: ItemServerType, ammo: ItemServerType): Validation {
        val requiredAmmo = weapon.paramOrNull(params.required_ammo) ?: defaultArrows

        // Dragon arrows have a separate category from standard arrows, but any bow that accepts
        // regular arrows can also use dragon arrows, provided the `levelrequire` threshold is met.
        val isAlternativeAmmo =
            requiredAmmo.isType("category.arrows") && ammo.isCategoryType("category.dragon_arrow")

        if (!ammo.isCategory(requiredAmmo) && !isAlternativeAmmo) {
            return Validation.Invalid.IncorrectAmmo
        }

        if (ammo.param(params.levelrequire) > weapon.param(params.levelrequire)) {
            return Validation.Invalid.LevelTooHigh
        }

        return Validation.Valid
    }

    public fun validateBolts(weapon: ItemServerType, ammo: ItemServerType): Validation {
        val requiredAmmo = weapon.paramOrNull(params.required_ammo) ?: defaultBolts

        if (!ammo.isCategory(requiredAmmo)) {
            return if (weapon.param(params.bone_weapon) != 0) {
                Validation.Invalid.BoneWeaponIncorrectAmmo
            } else {
                Validation.Invalid.IncorrectAmmo
            }
        }

        if (ammo.param(params.bone_weapon) != 0 && weapon.param(params.bone_weapon) == 0) {
            return Validation.Invalid.ExpectedBoneWeapon
        }

        if (ammo.param(params.levelrequire) > weapon.param(params.levelrequire)) {
            return Validation.Invalid.LevelTooHigh
        }

        return Validation.Valid
    }

    public fun validateJavelins(weapon: ItemServerType, ammo: ItemServerType): Validation {
        val requiredAmmo = weapon.paramOrNull(params.required_ammo) ?: defaultJavelins
        return if (!ammo.isCategory(requiredAmmo)) {
            Validation.Invalid.IncorrectAmmo
        } else {
            Validation.Valid
        }
    }

    public sealed class Validation {
        public data object Valid : Validation()

        public sealed class Invalid : Validation() {
            public data object LevelTooHigh : Invalid()

            public sealed class Ammo : Invalid()

            public data object IncorrectAmmo : Ammo()

            public data object BoneWeaponIncorrectAmmo : Ammo()

            public data object ExpectedBoneWeapon : Ammo()
        }
    }
}
