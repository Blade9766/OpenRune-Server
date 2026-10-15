package org.rsmod.api.combat.commons.ranged

import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.back
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.torso
import org.rsmod.api.player.worn.DizanasQuiver
import org.rsmod.api.player.worn.RangedAmmoValidation
import org.rsmod.api.player.worn.RangedAmmoValidation.Validation
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.getOrNull
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

public object RangedAmmunition {
    /** The default chance for standard ammunition to drop on the ground instead of disappearing. */
    public const val DEFAULT_AMMO_DROP_RATE: Int = 5

    /** The obj spawn duration when an ammunition is dropped on the ground after being fired. */
    public const val DEFAULT_AMMO_DROP_DURATION: Int = 200

    /**
     * Resolves the ammunition [player] would fire from [weapon].
     *
     * This is the obj in the ammo slot, unless the player wears a Dizana's quiver whose stored
     * ammunition the weapon can fire while the ammo slot cannot supply anything usable (it is empty,
     * holds a blessing, or holds ammunition of the wrong kind). The ammo slot always has priority.
     *
     * Pass the result to [attemptAmmoUsage] and later to the ammo consumption functions, which know
     * whether it came from the ammo slot or the quiver.
     */
    public fun activeAmmo(player: Player, weapon: ItemServerType): InvObj? =
        DizanasQuiver.activeAmmo(player, weapon)

    /**
     * Verifies that [weapon] can use [ammo] as valid ammunition and sends an appropriate error
     * message to the [player] if it cannot.
     *
     * **Note:** This function only performs validation and messaging. It does **not** remove
     * ammunition or attempt to drop it - use [detractAmmo] and [attemptAmmoDrop] for that behavior.
     *
     * @return `true` if the [ammo] is valid for the given [weapon], or if the [weapon] does not
     *   require specific ammunition
     */
    public fun attemptAmmoUsage(
        player: Player,
        weapon: ItemServerType,
        ammo: ItemServerType?,
    ): Boolean {
        val crossbow = weapon.isCategoryType("category.crossbow")
        if (crossbow) {
            if (ammo == null) {
                player.mes("There is no ammo left in your quiver.")
                return false
            }

            val ammoValidation = validateBolts(weapon, ammo)
            return when (ammoValidation) {
                is Validation.Invalid.IncorrectAmmo -> {
                    player.mes("You can't use that ammo with your crossbow.")
                    false
                }
                is Validation.Invalid.BoneWeaponIncorrectAmmo -> {
                    player.mes("You can't use that ammo with your bone crossbow.")
                    false
                }
                is Validation.Invalid.ExpectedBoneWeapon -> {
                    player.mes("Bone bolts are only usable with a bone crossbow.")
                    false
                }
                is Validation.Invalid.LevelTooHigh -> {
                    player.mes("Your crossbow isn't powerful enough for those bolts.")
                    false
                }
                is Validation.Valid -> true
            }
        }

        val bow = weapon.isCategoryType("category.bow")
        if (bow) {
            if (ammo == null) {
                player.mes("There is no ammo left in your quiver.")
                return false
            }

            val ammoValidation = validateArrows(weapon, ammo)
            return when (ammoValidation) {
                is Validation.Invalid.Ammo -> {
                    player.mes("You can't use that ammo with your bow.")
                    false
                }
                is Validation.Invalid.LevelTooHigh -> {
                    player.mes("Your bow isn't powerful enough for those arrows.")
                    false
                }
                is Validation.Valid -> true
            }
        }

        val ballista = weapon.isCategoryType("category.ballista")
        if (ballista) {
            if (ammo == null) {
                player.mes("There are no javelins in your quiver.")
                return false
            }

            val ammoValidation = validateJavelins(weapon, ammo)
            return when (ammoValidation) {
                is Validation.Invalid -> {
                    player.mes("You can't use that ammo with your ballista.")
                    false
                }
                is Validation.Valid -> true
            }
        }

        return true
    }

    /**
     * Rolls whether the worn cape conserves the ammunition about to be fired. Ava's devices carry
     * their chance as an `ammo_recovery_rate` param; a Dizana's quiver only conserves ammunition
     * once one of those devices has been applied to it (see [DizanasQuiver.ammoSaveRate]).
     */
    public fun conserveAmmo(player: Player, random: GameRandom): Boolean {
        val cape = getOrNull(player.back) ?: return false

        val recoveryRate =
            if (DizanasQuiver.isQuiver(cape)) {
                DizanasQuiver.ammoSaveRate(player) ?: return false
            } else {
                cape.paramOrNull(params.ammo_recovery_rate) ?: return false
            }

        val body = getOrNull(player.torso)
        if (body != null && body.param(params.metallic_interference)) {
            return false
        }

        return recoveryRate > random.of(maxExclusive = 100)
    }

    /**
     * Removes [detract] ammunition of type [wornType] from [wearpos]. When [wearpos] is the ammo
     * slot but the ammunition being fired is the one stored in a worn Dizana's quiver, the stored
     * stack is reduced instead.
     */
    public fun detractAmmo(
        player: Player,
        wearpos: Wearpos,
        wornType: ItemServerType,
        detract: Int,
        eventBus: EventBus,
    ) {
        val startObj = player.worn[wearpos.slot]
        if (wearpos == Wearpos.Quiver && !startObj.isType(wornType)) {
            detractStoredAmmo(player, wornType, detract)
            return
        }
        check(startObj.isType(wornType)) {
            "Expected worn obj to match `wornType`: wearpos=$wearpos, obj=$startObj, type=$wornType"
        }

        val oldCount = startObj.count
        check(oldCount >= detract) { "Unexpected low worn count: $oldCount (expected=$detract)" }

        player.worn[wearpos.slot] = startObj.copy(count = oldCount - detract)

        val outOfAmmo = oldCount - detract == 0
        if (outOfAmmo) {
            player.worn[wearpos.slot] = null

            // Official behavior: manually unequipping the quiver slot triggers an appearance
            // rebuild, but running out of ammo does not.
            val rebuildAppearance = wearpos == Wearpos.RightHand
            WornUnequipOp.notifyWornUnequip(
                player = player,
                wearpos = wearpos,
                objType = wornType,
                eventBus = eventBus,
                rebuildAppearance = rebuildAppearance,
            )
        }
    }

    private fun detractStoredAmmo(player: Player, ammoType: ItemServerType, detract: Int) {
        val stored = checkNotNull(DizanasQuiver.storedAmmo(player)) { "No quiver ammo stored." }
        check(stored.isType(ammoType)) {
            "Expected quiver ammo to match `wornType`: stored=$stored, type=$ammoType"
        }
        check(stored.count >= detract) {
            "Unexpected low quiver ammo count: ${stored.count} (expected=$detract)"
        }
        val remaining = stored.count - detract
        DizanasQuiver.setStoredAmmo(player, if (remaining > 0) stored.copy(count = remaining) else null)
    }

    public fun attemptAmmoDrop(
        player: Player,
        delay: Int,
        ammoType: ItemServerType,
        ammoCount: Int,
        dropCoord: CoordGrid,
        dropDuration: Int,
        collision: CollisionFlagMap,
        worldQueues: WorldQueueList,
        objRepo: ObjRepository,
    ) {
        // Note: This might not be the "official" behavior - ideally, this check would happen
        // after the `delay` has passed. However, collision flags are unlikely to change in
        // that short time frame, and performing the check here prevents adding unnecessary
        // entries to the world queue. This is a micro-optimization, but practically free
        // and safe.
        if (collision.isWalkBlocked(dropCoord)) {
            return
        }

        val obj = Obj.fromOwner(player, dropCoord, ammoType, ammoCount)
        worldQueues.add(delay) { objRepo.add(obj, dropDuration) }
    }

    public fun validateArrows(weapon: ItemServerType, ammo: ItemServerType): Validation =
        RangedAmmoValidation.validateArrows(weapon, ammo)

    public fun validateBolts(weapon: ItemServerType, ammo: ItemServerType): Validation =
        RangedAmmoValidation.validateBolts(weapon, ammo)

    public fun validateJavelins(weapon: ItemServerType, ammo: ItemServerType): Validation =
        RangedAmmoValidation.validateJavelins(weapon, ammo)
}
