package org.rsmod.api.player.worn

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.util.UncheckedType
import dev.openrune.util.Wearpos
import org.rsmod.api.player.back
import org.rsmod.api.player.vars.VarPlayerIntMapDelegate
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.getOrNull

/**
 * The extra ammunition slot granted by a worn Dizana's quiver.
 *
 * The client contract (clientscripts 5023-5028 and the `dizanas_quiver` interface) reads the stored
 * ammunition from two varps: `varp.dizanas_quiver_temp_ammo` holds the obj id (`-1` when empty) and
 * `varp.dizanas_quiver_temp_ammo_amount` the stack size. Both are permanent varps, so they double as
 * the server-side storage; the `inv.dizanas_quiver_ammo` inventory the cache also defines is never
 * transmitted by the client scripts and is left unused.
 *
 * When a bow or crossbow cannot fire what sits in the ammo slot (it is empty, holds a blessing, or
 * holds the wrong ammunition type) but can fire the stored ammunition, the stored ammunition is used
 * instead - the ammo slot always has priority.
 */
public object DizanasQuiver {
    public const val AMMO_VARP: String = "varp.dizanas_quiver_temp_ammo"
    public const val AMOUNT_VARP: String = "varp.dizanas_quiver_temp_ammo_amount"

    /**
     * Which Ava's device effect has been applied to the quiver: `0` none, otherwise an index into
     * [AMMO_SAVE_RATES].
     */
    public const val AMMO_SAVE_VARBIT: String = "varbit.dizanas_quiver_ammo_save"

    /** Ammo conservation chances (percent) for the attractor, accumulator and assembler. */
    public val AMMO_SAVE_RATES: List<Int> = listOf(60, 72, 80)

    /** Every quiver variant that provides the extra ammunition slot. */
    public val objs: Set<String> =
        setOf(
            "obj.dizanas_quiver_uncharged",
            "obj.dizanas_quiver_uncharged_trouver",
            "obj.dizanas_quiver_charged",
            "obj.dizanas_quiver_charged_trouver",
            "obj.dizanas_quiver_infinite",
            "obj.dizanas_quiver_infinite_trouver",
        )

    private val objIds: Set<Int> by lazy { objs.map { it.asRSCM(RSCMType.OBJ) }.toSet() }

    public fun isQuiver(type: ItemServerType): Boolean = type.id in objIds

    public fun isQuiver(obj: InvObj?): Boolean = obj != null && obj.id in objIds

    public fun isWearing(player: Player): Boolean = isQuiver(player.back)

    /** Only arrows and bolts fit in the quiver; javelins and atlatl darts do not. */
    public fun canStore(type: ItemServerType): Boolean =
        type.isCategoryType("category.arrows") ||
            type.isCategoryType("category.dragon_arrow") ||
            type.isCategoryType("category.crossbow_bolt")

    /** The ammunition stored in the quiver, whether or not the quiver is currently worn. */
    @OptIn(UncheckedType::class)
    public fun storedAmmo(player: Player): InvObj? {
        val id = player.vars[AMMO_VARP]
        val count = player.vars[AMOUNT_VARP]
        if (id <= 0 || count <= 0) {
            return null
        }
        return InvObj(id, count)
    }

    public fun storedAmmoType(player: Player): ItemServerType? = getOrNull(storedAmmo(player))

    /** Replaces the stored ammunition. A `null` or empty [obj] clears the slot. */
    public fun setStoredAmmo(player: Player, obj: InvObj?) {
        val vars = VarPlayerIntMapDelegate.from(player)
        if (obj == null || obj.count <= 0) {
            vars[AMMO_VARP] = -1
            vars[AMOUNT_VARP] = 0
        } else {
            vars[AMMO_VARP] = obj.id
            vars[AMOUNT_VARP] = obj.count
        }
    }

    /**
     * Makes sure the client-facing varps describe an empty slot rather than the default `0`, which
     * the client would otherwise draw as obj id 0.
     */
    public fun ensureInitialised(player: Player) {
        if (storedAmmo(player) == null && player.vars[AMMO_VARP] != -1) {
            setStoredAmmo(player, null)
        }
    }

    /**
     * Returns the stored ammunition when a worn quiver would supply it for [weapon]: the ammo slot
     * holds nothing the weapon can fire, while the stored ammunition is usable. Returns `null` in
     * every other case, including when the ammo slot's ammunition is what would be fired.
     */
    public fun activeStoredAmmo(player: Player, weapon: ItemServerType?): InvObj? {
        if (weapon == null || !RangedAmmoValidation.requiresAmmo(weapon)) {
            return null
        }
        if (!isWearing(player)) {
            return null
        }
        val stored = storedAmmo(player) ?: return null
        val storedType = getOrNull(stored) ?: return null
        if (!RangedAmmoValidation.isUsable(weapon, storedType)) {
            return null
        }
        val wornAmmo = player.worn[Wearpos.Quiver.slot]
        if (wornAmmo != null && RangedAmmoValidation.isUsable(weapon, getInvObj(wornAmmo))) {
            return null
        }
        return stored
    }

    /**
     * The ammunition [player] would fire from [weapon]: the ammo slot obj unless a worn quiver's
     * stored ammunition takes over (see [activeStoredAmmo]).
     */
    public fun activeAmmo(player: Player, weapon: ItemServerType?): InvObj? =
        activeStoredAmmo(player, weapon) ?: player.worn[Wearpos.Quiver.slot]

    /** Whether [obj] is the stored ammunition rather than the ammo slot's. */
    public fun isStoredAmmo(player: Player, obj: InvObj?): Boolean {
        val stored = storedAmmo(player) ?: return false
        return obj != null && obj.id == stored.id && obj.count == stored.count
    }

    /** Percent chance a worn quiver conserves ammunition, or `null` if no device was applied. */
    public fun ammoSaveRate(player: Player): Int? {
        val tier = player.vars[AMMO_SAVE_VARBIT]
        return AMMO_SAVE_RATES.getOrNull(tier - 1)
    }

    public fun setAmmoSaveRate(player: Player, rate: Int?) {
        val tier = if (rate == null) 0 else AMMO_SAVE_RATES.indexOf(rate) + 1
        VarPlayerIntMapDelegate.from(player)[AMMO_SAVE_VARBIT] = tier
    }
}
