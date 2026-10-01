package org.rsmod.content.quest.area.mortton.myreque

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.content.quest.area.ardougne.fadeFromBlack
import org.rsmod.content.quest.area.ardougne.fadeToBlack
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.POUCH
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.POUCH_EMPTY
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.WEAPONS
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.getInvObj

/**
 * Cyreg's boat between Mort'ton and the Hollows. The crossing itself is a short faded scene
 * rather than a rowed route: one ghast rises at the boat each way, and a charged druid pouch
 * spends a charge to drive it off. Without one the ghast rots a piece of food, or failing that
 * feeds on the player, but never for a killing blow.
 */
@Singleton
class SwampBoat @Inject constructor() {

    suspend fun ProtectedAccess.rowToHollows() {
        fadeToBlack()
        mes("Cyreg pushes off into the mists of Mort Myre.")
        ghastAtTheBoat()
        telejump(MyrequeCoords.HOLLOWS_LANDING, TeleportType.Exempt)
        fadeFromBlack()
        mes("The boat bumps against the bank of the Hollows.")
    }

    suspend fun ProtectedAccess.rowToMortton() {
        fadeToBlack()
        mes("You row back through the swamp to Mort'ton.")
        ghastAtTheBoat()
        telejump(MyrequeCoords.MORTTON_LANDING, TeleportType.Exempt)
        fadeFromBlack()
    }

    private fun ProtectedAccess.ghastAtTheBoat() {
        soundSynth(GHAST_SOUND)
        if (inv.count(POUCH) > 0) {
            invDel(inv, POUCH, 1)
            if (inv.count(POUCH) == 0) {
                invAdd(inv, POUCH_EMPTY)
            }
            mes("A ghast claws at the side of the boat! Your druid pouch flares and it sinks back into the water.")
            return
        }
        val foodSlot = inv.indices.firstOrNull { slot -> inv[slot]?.let { isFood(getInvObj(it).internalName) } == true }
        if (foodSlot != null) {
            val food = getInvObj(checkNotNull(inv[foodSlot])).internalName
            invDel(inv, food, count = 1, slot = foodSlot)
            invAdd(inv, ROTTEN_FOOD)
            mes("Something invisible rakes through your pack. A terrible stench rises from your food.")
            mes("A charged druid pouch would have kept the ghasts away.")
            return
        }
        val damage = random.of(1, MAX_FEED).coerceAtMost(player.hitpoints - 1)
        if (damage > 0) {
            queueHit(delay = 1, type = HitType.Typeless, damage = damage)
        }
        mes("Something cold feeds on you as the boat drifts. A charged druid pouch would have kept it away.")
    }

    private fun isFood(internal: String): Boolean {
        val type = ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ)) ?: return false
        return type.interfaceOptions.any { it == "Eat" } && internal != ROTTEN_FOOD
    }

    private companion object {
        const val GHAST_SOUND = "synth.ghast_attack"
        const val ROTTEN_FOOD = "obj.rotten_food"
        const val MAX_FEED = 3
    }
}

/** Each delivery weapon still missing from the pack, as "2 x Steel sword". Worn ones don't count. */
internal fun missingWeapons(inv: Inventory): List<String> =
    WEAPONS.mapNotNull { (obj, needed) ->
        val short = needed - inv.count(obj)
        if (short <= 0) {
            null
        } else {
            val name = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: obj
            "$short x $name"
        }
    }
