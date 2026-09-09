package org.rsmod.content.other.special.weapons.scripts.charge

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.fletchingLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.content.other.special.weapons.ranged.RevenantBowWeapons.Companion.ACTIVATION_ETHER
import org.rsmod.content.other.special.weapons.ranged.RevenantBowWeapons.Companion.ETHER_VAROBJ
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Revenant ether handling for Craw's bow and the Webweaver bow.
 *
 * Using ether on an uncharged bow spends 1,000 ether to activate it; that ether and everything
 * used on the bow afterwards (up to 16,000 ether of ammunition) is stored on the bow. Uncharging
 * returns all of it, including the activation ether, and turns the bow back into its uncharged
 * form. Dismantling an uncharged Craw's bow yields 7,500 ether; dismantling an uncharged Webweaver
 * bow splits it back into Craw's bow and the Fangs of Venenatis, which is also how it is made
 * (85 Fletching).
 */
class RevenantBowCharging
@Inject
constructor(private val charges: ObjChargeManager, private val objRepo: ObjRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (bow in BOWS) {
            onOpHeldU(REVENANT_ETHER, bow.uncharged) { addEther(inv, it.secondSlot, it.second, bow) }
            onOpHeldU(REVENANT_ETHER, bow.charged) { addEther(inv, it.secondSlot, it.second, bow) }
            onOpHeld3(bow.charged) { checkEther(it.inventory[it.slot], bow) }
            onOpWorn2(bow.charged) { checkEther(player.righthand, bow) }
            onOpHeld5(bow.charged) { uncharge(inv, it.slot, bow) }
        }

        onOpHeld3(CRAWS.uncharged) { dismantleCraws(inv, it.slot) }
        onOpHeld3(WEBWEAVER.uncharged) { dismantleWebweaver(inv, it.slot) }
        onOpHeldU(CRAWS.uncharged, VENENATIS_FANGS) { attachFangs(inv, it.firstSlot) }
    }

    private fun ProtectedAccess.addEther(
        inventory: Inventory,
        bowSlot: Int,
        bowType: ItemServerType,
        bow: RevenantBow,
    ) {
        val current = charges.getCharges(inventory[bowSlot], ETHER_VAROBJ)
        val ether = invTotal(inv, REVENANT_ETHER)
        if (ether == 0) {
            mes("You don't have any revenant ether to charge the bow with.")
            return
        }

        val activating = current == 0
        if (activating && ether < ACTIVATION_ETHER) {
            mes(
                "You need at least ${ACTIVATION_ETHER.formatAmount} revenant ether " +
                    "to activate ${bow.name}."
            )
            return
        }

        val space = MAX_TOTAL_ETHER - current
        if (space <= 0) {
            mes("${bow.name} can't hold any more revenant ether.")
            return
        }

        val add = min(space, ether)
        val removeEther = invDel(inv, REVENANT_ETHER, add)
        if (removeEther.failure) {
            return
        }

        // Paranoid check: should always be the case.
        check(inventory[bowSlot].isType(bowType))

        charges.addCharges(
            inventory = inventory,
            slot = bowSlot,
            add = add,
            internal = ETHER_VAROBJ,
            max = MAX_TOTAL_ETHER,
        )
        val ammo = ammoOf(charges.getCharges(inventory[bowSlot], ETHER_VAROBJ))
        if (activating) {
            mes(
                "You use ${ACTIVATION_ETHER.formatAmount} revenant ether to activate ${bow.name} " +
                    "and load it with ${ammo.formatAmount} ether."
            )
        } else {
            mes(
                "You add ${add.formatAmount} revenant ether to ${bow.name}. " +
                    "It now holds ${ammo.formatAmount} ether."
            )
        }
    }

    private fun ProtectedAccess.checkEther(obj: InvObj?, bow: RevenantBow) {
        val ammo = ammoOf(charges.getCharges(obj, ETHER_VAROBJ))
        mes("${bow.name} has ${ammo.formatAmount} revenant ether left to fire.")
    }

    private suspend fun ProtectedAccess.uncharge(
        inventory: Inventory,
        bowSlot: Int,
        bow: RevenantBow,
    ) {
        val total = charges.getCharges(inventory[bowSlot], ETHER_VAROBJ)
        if (total == 0) {
            charges.removeAllCharges(inventory, bowSlot, ETHER_VAROBJ)
            return
        }

        if (REVENANT_ETHER !in inv && inv.freeSpace() < 1) {
            mes("You don't have enough inventory space to uncharge ${bow.name}.")
            return
        }

        val confirmed =
            choice2(
                "Proceed.",
                true,
                "Cancel.",
                false,
                title =
                    "Uncharge ${bow.name}? All ${total.formatAmount} revenant ether " +
                        "will be returned to you.",
            )
        if (!confirmed) {
            return
        }

        val removed = charges.removeAllCharges(inventory, bowSlot, ETHER_VAROBJ)
        check(removed > 0)
        invAddOrDrop(objRepo, REVENANT_ETHER, removed)
        objbox(
            bow.uncharged,
            400,
            "You uncharge ${bow.name}, regaining ${removed.formatAmount} " +
                "revenant ether in the process.",
        )
    }

    private suspend fun ProtectedAccess.dismantleCraws(inventory: Inventory, bowSlot: Int) {
        val confirmed =
            choice2(
                "Proceed.",
                true,
                "Cancel.",
                false,
                title =
                    "Dismantle Craw's bow for ${DISMANTLE_ETHER.formatAmount} revenant ether? " +
                        "The bow will be destroyed.",
            )
        if (!confirmed) {
            return
        }
        val removeBow = invDel(inventory, CRAWS.uncharged, count = 1, slot = bowSlot)
        if (removeBow.failure) {
            return
        }
        invAddOrDrop(objRepo, REVENANT_ETHER, DISMANTLE_ETHER)
        objbox(
            REVENANT_ETHER,
            400,
            "You dismantle Craw's bow and recover " +
                "${DISMANTLE_ETHER.formatAmount} revenant ether.",
        )
    }

    private suspend fun ProtectedAccess.dismantleWebweaver(inventory: Inventory, bowSlot: Int) {
        if (inv.freeSpace() < 1) {
            mes("You need a free inventory space to dismantle the Webweaver bow.")
            return
        }
        val confirmed =
            choice2(
                "Proceed.",
                true,
                "Cancel.",
                false,
                title = "Dismantle the Webweaver bow back into Craw's bow and the Fangs of Venenatis?",
            )
        if (!confirmed) {
            return
        }
        val removeBow = invDel(inventory, WEBWEAVER.uncharged, count = 1, slot = bowSlot)
        if (removeBow.failure) {
            return
        }
        invAdd(inventory, CRAWS.uncharged, count = 1, slot = bowSlot)
        invAddOrDrop(objRepo, VENENATIS_FANGS, 1)
        objbox(
            CRAWS.uncharged,
            400,
            "You dismantle the Webweaver bow, recovering Craw's bow and the Fangs of Venenatis.",
        )
    }

    private suspend fun ProtectedAccess.attachFangs(inventory: Inventory, bowSlot: Int) {
        if (player.fletchingLvl < FANGS_FLETCHING_REQ) {
            mes(
                "You need a Fletching level of $FANGS_FLETCHING_REQ " +
                    "to attach the Fangs of Venenatis to Craw's bow."
            )
            return
        }
        val removeParts =
            invDel(inv, type1 = CRAWS.uncharged, count1 = 1, type2 = VENENATIS_FANGS, count2 = 1)
        if (removeParts.failure) {
            return
        }
        val addBow = invAdd(inventory, WEBWEAVER.uncharged, count = 1, slot = bowSlot)
        if (addBow.failure) {
            invAddOrDrop(objRepo, WEBWEAVER.uncharged, 1)
        }
        objbox(
            WEBWEAVER.uncharged,
            400,
            "You attach the Fangs of Venenatis to Craw's bow, creating the Webweaver bow.",
        )
    }

    /** Ether available to fire: whatever is stored beyond the activation ether. */
    private fun ammoOf(stored: Int): Int = max(0, stored - ACTIVATION_ETHER)

    private data class RevenantBow(val uncharged: String, val charged: String, val name: String)

    private companion object {
        const val REVENANT_ETHER = "obj.wild_cave_shard"
        const val VENENATIS_FANGS = "obj.wbr_venenatis_fang"

        /** 1,000 activation ether plus 16,000 ether of ammunition. */
        const val MAX_TOTAL_ETHER = ACTIVATION_ETHER + 16_000
        const val DISMANTLE_ETHER = 7_500
        const val FANGS_FLETCHING_REQ = 85

        val CRAWS = RevenantBow("obj.wild_cave_bow_uncharged", "obj.wild_cave_bow_charged", "Craw's bow")
        val WEBWEAVER =
            RevenantBow(
                "obj.wild_cave_webweaver_uncharged",
                "obj.wild_cave_webweaver_charged",
                "the Webweaver bow",
            )
        val BOWS = listOf(CRAWS, WEBWEAVER)
    }
}
