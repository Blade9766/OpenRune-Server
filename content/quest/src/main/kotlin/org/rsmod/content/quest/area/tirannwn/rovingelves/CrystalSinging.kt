package org.rsmod.content.quest.area.tirannwn.rovingelves

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.COINS
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.CRYSTAL_CHARGES

/**
 * Hands out the elves' crystal bows and shields. Their charges are the crystal weapon charge
 * varobj (the same one the crystal bow spends per shot), so an item given here is the ordinary
 * charged item with its count already set. Every exchange is a single inventory transaction: the
 * coins (and a weapon seed, for Ilfeen) leave and the item arrives together, or nothing changes.
 */
@Singleton
class CrystalSinging {
    fun give(access: ProtectedAccess, obj: String, charges: Int): Boolean =
        access.invAdd(access.inv, obj, vars = varsFor(charges)).success

    fun sell(access: ProtectedAccess, obj: String, price: Int, charges: Int): Boolean =
        exchange(access, obj, price, charges, consumed = null)

    /** Ilfeen's service: a crystal weapon seed and [price] coins become [obj]. */
    fun singSeed(access: ProtectedAccess, seed: String, obj: String, price: Int, charges: Int): Boolean =
        exchange(access, obj, price, charges, consumed = seed)

    private fun exchange(access: ProtectedAccess, obj: String, price: Int, charges: Int, consumed: String?): Boolean {
        val inv = access.inv
        return access.player.invTransaction(inv) {
            val from = select(inv)
            if (consumed != null) {
                delete {
                    this.from = from
                    this.obj = consumed.asRSCM(RSCMType.OBJ)
                    this.strictCount = 1
                }
            }
            delete {
                this.from = from
                this.obj = COINS.asRSCM(RSCMType.OBJ)
                this.strictCount = price
            }
            insert {
                this.into = from
                this.obj = obj.asRSCM(RSCMType.OBJ)
                this.strictCount = 1
                this.vars = varsFor(charges)
            }
        }.success
    }

    companion object {
        private val chargeBits by lazy {
            val varobj = ServerCacheManager.getVarObj(CRYSTAL_CHARGES.asRSCM(RSCMType.VAROBJ))
            checkNotNull(varobj) { "Missing varobj: $CRYSTAL_CHARGES" }.bits
        }

        fun varsFor(charges: Int): Int {
            val width = chargeBits.last - chargeBits.first + 1
            require(charges in 0 until (1 shl width)) { "Too many crystal charges: $charges" }
            return charges shl chargeBits.first
        }

        fun formatCoins(amount: Int): String = amount.formatAmount
    }
}
