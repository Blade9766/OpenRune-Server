package org.rsmod.api.obj.charges

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ObjChargeManagerTest {
    private val charges = ObjChargeManager()

    @Test
    fun `uncharged obj with a charged variant can be charged`() {
        assertTrue(charges.canAddCharges(InvObj("obj.wild_cave_sceptre_uncharged"), VAROBJ, MAX))
    }

    @Test
    fun `uncharged obj without a charged variant is rejected instead of throwing`() {
        assertFalse(charges.canAddCharges(InvObj("obj.abyssal_whip"), VAROBJ, MAX))
        assertFalse(charges.canAddCharges(null, VAROBJ, MAX))
    }

    @Test
    fun `fully charged obj cannot take more charges`() {
        val inv = inventory()
        inv[0] = InvObj("obj.wild_cave_sceptre_uncharged")
        charges.addCharges(inv, 0, MAX, VAROBJ, MAX)
        assertFalse(charges.canAddCharges(inv[0], VAROBJ, MAX))
        assertTrue(charges.canAddCharges(inv[0], VAROBJ, MAX + 1))
    }

    @Test
    fun `uncharge check follows the uncharged variant param`() {
        assertTrue(charges.canRemoveAllCharges(InvObj("obj.wild_cave_sceptre_charged")))
        assertFalse(charges.canRemoveAllCharges(InvObj("obj.abyssal_whip")))
        assertFalse(charges.canRemoveAllCharges(null))
    }

    private fun inventory(): Inventory =
        Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))

    companion object {
        private const val VAROBJ = "varobj.powered_staff_charges"
        private const val MAX = 16_000

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
