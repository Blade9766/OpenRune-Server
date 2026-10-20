package org.rsmod.content.other.bots.world

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import org.rsmod.content.other.bots.core.BotSave
import org.rsmod.content.other.bots.core.SavedObj
import org.rsmod.game.stat.PlayerSkillXPTable

enum class KitTarget {
    Inv,
    Worn,
    Bank,
}

/** Edits a stored (out-of-world) bot character. Returns null when the change does not fit. */
object BotKit {
    private const val INV_SIZE = 28
    private const val WORN_SIZE = 14
    private const val BANK_SIZE = 800

    fun give(save: BotSave, itemId: Int, count: Int, target: KitTarget): BotSave? {
        val type = ServerCacheManager.getItem(itemId) ?: return null
        if (count <= 0) {
            return null
        }
        return when (target) {
            KitTarget.Inv -> {
                val stacks = type.isStackable || type.isCert
                save.copy(inv = add(save.inv, itemId, count, INV_SIZE, stacks) ?: return null)
            }
            KitTarget.Bank -> save.copy(bank = add(save.bank, itemId, count, BANK_SIZE, stacks = true) ?: return null)
            KitTarget.Worn -> {
                val slot = Wearpos[type.wearpos1]?.slot ?: return null
                if (save.worn.any { it.slot == slot } || count != 1 && !type.isStackable) {
                    return null
                }
                save.copy(worn = save.worn + SavedObj(slot, itemId, count))
            }
        }
    }

    /** Removes up to [count] of [itemId] from [target]; returns the new save and how many went. */
    fun take(save: BotSave, itemId: Int, count: Int, target: KitTarget): Pair<BotSave, Int> {
        val source =
            when (target) {
                KitTarget.Inv -> save.inv
                KitTarget.Worn -> save.worn
                KitTarget.Bank -> save.bank
            }
        var left = count
        val kept = ArrayList<SavedObj>()
        for (obj in source) {
            if (obj.id != itemId || left == 0) {
                kept += obj
                continue
            }
            val removed = minOf(left, obj.count)
            left -= removed
            if (obj.count > removed) {
                kept += obj.copy(count = obj.count - removed)
            }
        }
        val updated =
            when (target) {
                KitTarget.Inv -> save.copy(inv = kept)
                KitTarget.Worn -> save.copy(worn = kept)
                KitTarget.Bank -> save.copy(bank = kept)
            }
        return updated to count - left
    }

    fun setLevel(save: BotSave, stat: String, level: Int): BotSave? {
        if (level !in 1..99 || runCatching { stat.asRSCM(RSCMType.STAT) }.isFailure) {
            return null
        }
        return save.copy(xp = save.xp + (stat to PlayerSkillXPTable.getFineXPFromLevel(level)), levels = save.levels - stat)
    }

    /**
     * Tops [save] up to [kit]: each item up to its count in its target and each level up to its
     * minimum. Nothing already there is duplicated or lowered. Returns null, changing nothing, when
     * an item is unknown or does not fit.
     */
    fun applyKit(save: BotSave, kit: StarterKit, itemId: (String) -> Int?): BotSave? {
        var result = save
        val targets = listOf(KitTarget.Inv to kit.inv, KitTarget.Worn to kit.worn, KitTarget.Bank to kit.bank)
        for ((target, items) in targets) {
            for (entry in items) {
                val id = itemId(entry.item) ?: return null
                val missing = entry.count - count(result, id, target)
                if (missing > 0) {
                    result = give(result, id, missing, target) ?: return null
                }
            }
        }
        for ((stat, level) in kit.levels) {
            val name = "stat.$stat"
            if (PlayerSkillXPTable.getLevelFromFineXP(result.xp[name] ?: 0) < level) {
                result = setLevel(result, name, level) ?: return null
            }
        }
        return result
    }

    private fun count(save: BotSave, itemId: Int, target: KitTarget): Int {
        val objs =
            when (target) {
                KitTarget.Inv -> save.inv
                KitTarget.Worn -> save.worn
                KitTarget.Bank -> save.bank
            }
        return objs.sumOf { if (it.id == itemId) it.count else 0 }
    }

    private fun add(
        objs: List<SavedObj>,
        itemId: Int,
        count: Int,
        size: Int,
        stacks: Boolean,
    ): List<SavedObj>? {
        if (stacks) {
            val existing = objs.firstOrNull { it.id == itemId }
            if (existing != null) {
                val total = existing.count.toLong() + count
                if (total > Int.MAX_VALUE) {
                    return null
                }
                return objs.map { if (it === existing) it.copy(count = total.toInt()) else it }
            }
            val slot = freeSlots(objs, size).firstOrNull() ?: return null
            return objs + SavedObj(slot, itemId, count)
        }
        val slots = freeSlots(objs, size).take(count)
        if (slots.size < count) {
            return null
        }
        return objs + slots.map { SavedObj(it, itemId, 1) }
    }

    private fun freeSlots(objs: List<SavedObj>, size: Int): List<Int> {
        val used = objs.mapTo(HashSet()) { it.slot }
        return (0 until size).filter { it !in used }
    }
}
