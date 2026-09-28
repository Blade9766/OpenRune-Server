package org.rsmod.content.quest.area.mortton.shades.catacombs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpObj3
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.api.script.onOpWorn4
import org.rsmod.api.script.onOpWorn5
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType
import org.rsmod.game.obj.Obj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Player.coffinContents: Int by intVarp("varp.shades_coffin")

/**
 * Dampe's coffins. A broken coffin fitted with a lock stores shade remains - 3 in bronze up to 28
 * in gold - kept in `varp.shades_coffin` as six 5-bit counts from Loar to Urium. Fill takes every
 * remains in the inventory; Empty hands them back highest tier first; an open coffin also catches
 * remains as they are picked up. Removing the lock loses whatever is inside.
 */
class ShadeCoffins @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        for (metal in ShadeMetal.entries) {
            for (open in listOf(false, true)) {
                val coffin = coffin(metal, open)
                onOpHeld1(coffin) { fill(metal) }
                onOpHeld3(coffin) { configure(metal, open, inv) }
                onOpHeld4(coffin) { empty() }
                onOpWorn2(coffin) { fill(metal) }
                onOpWorn3(coffin) { configure(metal, open, player.worn) }
                onOpWorn4(coffin) { toggle(metal, open, player.worn) }
                onOpWorn5(coffin) { empty() }
            }
        }
        for (remains in REMAINS) {
            onOpObj3(itemType(remains)) { take(it.obj, remains) }
        }
    }

    private fun ProtectedAccess.fill(metal: ShadeMetal) {
        val capacity = CAPACITY.getValue(metal)
        var stored = 0
        for ((tier, remains) in REMAINS.withIndex()) {
            while (remains in inv && total(player) < capacity) {
                invDel(inv, remains)
                add(player, tier, 1)
                stored++
            }
        }
        mes(
            when {
                stored > 0 -> "You put the shade remains into the coffin."
                total(player) >= capacity -> "Your coffin is full."
                else -> "You have no shade remains to put in the coffin."
            },
        )
    }

    private fun ProtectedAccess.empty() {
        var removed = 0
        for (tier in REMAINS.indices.reversed()) {
            while (count(player, tier) > 0 && !inv.isFull()) {
                invAdd(inv, REMAINS[tier])
                add(player, tier, -1)
                removed++
            }
        }
        mes(
            when {
                removed > 0 && total(player) > 0 -> "You take out as many remains as you can carry."
                removed > 0 -> "You empty the coffin."
                total(player) > 0 -> "You don't have enough inventory space."
                else -> "Your coffin is empty."
            },
        )
    }

    private suspend fun ProtectedAccess.configure(metal: ShadeMetal, open: Boolean, held: Inventory) {
        val choice =
            choice3(
                "Check shade count",
                1,
                if (open) "Close coffin" else "Open coffin",
                2,
                "Remove lock",
                3,
            )
        when (choice) {
            1 -> checkCount(metal)
            2 -> toggle(metal, open, held)
            else -> removeLock(metal, open, held)
        }
    }

    private suspend fun ProtectedAccess.checkCount(metal: ShadeMetal) {
        val parts = REMAINS_NAMES.indices.mapNotNull { tier ->
            count(player, tier).takeIf { it > 0 }?.let { "$it ${REMAINS_NAMES[tier]}" }
        }
        val contents = if (parts.isEmpty()) "nothing" else parts.joinToString(", ")
        mesbox("Your coffin holds ${total(player)}/${CAPACITY.getValue(metal)} remains: $contents.")
    }

    private fun ProtectedAccess.toggle(metal: ShadeMetal, open: Boolean, held: Inventory) {
        swapInPlace(held, coffin(metal, open), coffin(metal, !open))
        mes(if (open) "You close the coffin." else "You open the coffin; shade remains you pick up will go straight into it.")
    }

    private suspend fun ProtectedAccess.removeLock(metal: ShadeMetal, open: Boolean, held: Inventory) {
        val sure =
            choice2(
                "Yes, remove the lock.",
                true,
                "No.",
                false,
                title = "Any remains in the coffin will be lost.",
            )
        if (!sure) {
            return
        }
        player.coffinContents = 0
        swapInPlace(held, coffin(metal, open), BROKEN_COFFIN)
        if (!invAdd(inv, lock(metal), strict = false).success) {
            objRepo.add(lock(metal), coords, GROUND_TICKS, player)
        }
        mes("You prise the lock off the coffin.")
    }

    private suspend fun ProtectedAccess.take(obj: Obj, remains: String) {
        if (coords != obj.coords) {
            delay(1)
        }
        val metal = openCoffin(player)
        val tier = REMAINS.indexOf(remains)
        val intoCoffin = metal != null && total(player) < CAPACITY.getValue(metal)
        if (!intoCoffin && inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        if (!objRepo.del(obj)) {
            return
        }
        if (intoCoffin) {
            add(player, tier, 1)
            mes("You put the ${REMAINS_NAMES[tier]} remains into your coffin.")
        } else {
            invAdd(inv, remains)
        }
    }

    private fun openCoffin(player: Player): ShadeMetal? =
        ShadeMetal.entries.firstOrNull { coffin(it, true) in player.inv || coffin(it, true) in player.worn }

    private fun itemType(name: String) =
        requireNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { "Missing obj: $name" }

    companion object {
        const val BROKEN_COFFIN = "obj.shades_coffin_broken"
        const val GROUND_TICKS = 200
        const val BITS = 5
        const val MASK = (1 shl BITS) - 1

        val REMAINS = (1..6).map { "obj.shade_bones$it" }
        val REMAINS_NAMES = listOf("Loar", "Phrin", "Riyl", "Asyn", "Fiyr", "Urium")

        internal val CAPACITY =
            mapOf(
                ShadeMetal.Bronze to 3,
                ShadeMetal.Steel to 8,
                ShadeMetal.Black to 14,
                ShadeMetal.Silver to 20,
                ShadeMetal.Gold to 28,
            )

        internal fun coffin(metal: ShadeMetal, open: Boolean): String =
            "obj.shades_coffin_${metal.key}" + if (open) "_open" else ""

        internal fun lock(metal: ShadeMetal): String = "obj.shades_lock_${metal.key}"

        fun hasCoffin(player: Player): Boolean {
            val all = listOf(BROKEN_COFFIN) + ShadeMetal.entries.flatMap { listOf(coffin(it, false), coffin(it, true)) }
            return all.any { it in player.inv || it in player.worn }
        }

        /** Replaces [from] with [to] in the same slot, so the coffin never jumps around. */
        internal fun ProtectedAccess.swapInPlace(held: Inventory, from: String, to: String) {
            val slot = held.indices.firstOrNull { held[it].isType(from) } ?: return
            invDel(held, from, slot = slot)
            invAdd(held, to, slot = slot)
        }

        fun clear(player: Player) {
            player.coffinContents = 0
        }

        private fun count(player: Player, tier: Int): Int = (player.coffinContents shr (tier * BITS)) and MASK

        private fun total(player: Player): Int = REMAINS.indices.sumOf { count(player, it) }

        private fun add(player: Player, tier: Int, delta: Int) {
            val next = (count(player, tier) + delta).coerceIn(0, MASK)
            val cleared = player.coffinContents and (MASK shl (tier * BITS)).inv()
            player.coffinContents = cleared or (next shl (tier * BITS))
        }
    }
}
