package org.rsmod.content.skills.runecrafting.action

import dev.openrune.types.ItemServerType
import kotlin.math.floor
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseRunecraftingLvl
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.ComboruneRecipeRow
import org.rsmod.api.table.runecrafting.RunecraftingRunesRow
import org.rsmod.content.skills.runecrafting.essencepouch.EssencePouch
import org.rsmod.content.skills.runecrafting.items.BindingNecklace.consumeChargeAfterCombo
import org.rsmod.content.skills.runecrafting.items.BindingNecklace.isWearing
import org.rsmod.content.skills.runecrafting.items.BloodEssence
import org.rsmod.content.skills.runecrafting.items.BloodEssence.applyBloodRuneBonus
import org.rsmod.content.skills.runecrafting.items.RaimentsOfTheEye.applyBonus
import org.rsmod.content.skills.runecrafting.magic.MagicImbue.isActive
import org.rsmod.game.inv.isType

object RunecraftAction {
    private const val RUNECRAFT_WAIT_CYCLE = 3
    private const val RUNECRAFT_SOUND = 2710
    private const val RUNE_ESSENCE = EssencePouch.RUNE_ESSENCE
    private const val PURE_ESSENCE = EssencePouch.PURE_ESSENCE
    private const val DAEYALT_ESSENCE = EssencePouch.DAEYALT_ESSENCE
    private const val GUARDIAN_ESSENCE = EssencePouch.GUARDIAN_ESSENCE
    private const val DARK_ESSENCE = EssencePouch.DARK_ESSENCE_FRAGMENT
    private const val DAEYALT_XP_MULTIPLIER = 1.5
    private const val OURANIA_XP_MULTIPLIER = 1.7
    private const val CORE_RUNE_MULTIPLIER = 11
    private const val CORE_XP_MULTIPLIER = 10
    private const val ASTRAL_RUNE = "obj.astralrune"
    private const val SOUL_RUNE = "obj.soulrune"
    private const val AETHER_CATALYST = "obj.cosmic_soul_catalyst"
    private const val COMBINATION_EXTRACT = "obj.scar_extract_twisted"
    private const val LUNAR_DIPLOMACY_STAGE = "varbit.lunar_quest_main"
    private const val LUNAR_DIPLOMACY_COMPLETE = 190
    private const val NO_SPACE_MESSAGE = "You don't have enough inventory space."

    private val runecraftingExtract = mapOf(
        "obj.scar_extract_warped" to 250,
        "obj.scar_extract_twisted" to 60,
        "obj.scar_extract_mangled" to 60,
        "obj.scar_extract_scarred" to 60
    )

    suspend fun ProtectedAccess.preCraft() {
        anim("seq.human_runecraft")
        spotanim("spotanim.runecrafting", height = 100)
        soundSynth(RUNECRAFT_SOUND)
        delay(RUNECRAFT_WAIT_CYCLE)
    }

    suspend fun ProtectedAccess.craftRune(
        rune: RunecraftingRunesRow,
        xpMods: XpModifiers,
        ouraniaAltar: Boolean = false,
    ) {
        if (!canCraftRune(rune)) {
            return
        }

        preCraft()

        val validEssence = rune.input.map { it.internalName }.toSet()
        val acceptsDaeyalt = PURE_ESSENCE in validEssence
        val daeyaltEssCount =
            if (acceptsDaeyalt) {
                inv.count(DAEYALT_ESSENCE)
            } else {
                0
            }

        if (daeyaltEssCount > 0) {
            craftDaeyaltEssence(rune, xpMods, daeyaltEssCount, ouraniaAltar)
            return
        }

        craftStandardEssence(rune, xpMods, validEssence, ouraniaAltar)
    }

    private fun ProtectedAccess.craftDaeyaltEssence(
        rune: RunecraftingRunesRow,
        xpMods: XpModifiers,
        daeyaltEssCount: Int,
        ouraniaAltar: Boolean,
    ) {
        val level = player.baseRunecraftingLvl
        val baseMultiplier = getBonusMultiplier(rune.output.internalName, level).toInt()
        val produced = applyBonus(daeyaltEssCount * baseMultiplier)

        val xpMultiplier =
            if (ouraniaAltar) {
                DAEYALT_XP_MULTIPLIER * OURANIA_XP_MULTIPLIER
            } else {
                DAEYALT_XP_MULTIPLIER
            }

        finishEssenceCraft(
            rune,
            mapOf(DAEYALT_ESSENCE to daeyaltEssCount),
            daeyaltEssCount * rune.xpPerEssence * xpMultiplier,
            xpMods,
            ouraniaAltar,
            produced,
        )
    }

    private fun ProtectedAccess.craftStandardEssence(
        rune: RunecraftingRunesRow,
        xpMods: XpModifiers,
        validEssence: Set<String>,
        ouraniaAltar: Boolean,
    ) {
        val runeEssCount = if (RUNE_ESSENCE in validEssence) inv.count(RUNE_ESSENCE) else 0
        val pureEssCount = if (PURE_ESSENCE in validEssence) inv.count(PURE_ESSENCE) else 0
        val guardianEssCount =
            if (PURE_ESSENCE in validEssence) {
                inv.count(GUARDIAN_ESSENCE)
            } else {
                0
            }
        val darkEssCount =
            if (DARK_ESSENCE in validEssence) {
                inv.count(DARK_ESSENCE)
            } else {
                0
            }
        val totalEssence = runeEssCount + pureEssCount + guardianEssCount + darkEssCount
        if (totalEssence <= 0) {
            return
        }

        val essence =
            linkedMapOf(
                PURE_ESSENCE to pureEssCount,
                GUARDIAN_ESSENCE to guardianEssCount,
                DARK_ESSENCE to darkEssCount,
                RUNE_ESSENCE to runeEssCount,
            )

        val level = player.baseRunecraftingLvl
        val baseMultiplier = getBonusMultiplier(rune.output.internalName, level).toInt()
        val produced = essence.values.sumOf { count -> applyBonus(count * baseMultiplier) }
        val xpMultiplier = if (ouraniaAltar) OURANIA_XP_MULTIPLIER else 1.0

        finishEssenceCraft(
            rune,
            essence,
            totalEssence * rune.xpPerEssence * xpMultiplier,
            xpMods,
            ouraniaAltar,
            produced,
        )
    }

    private fun ProtectedAccess.finishEssenceCraft(
        rune: RunecraftingRunesRow,
        essence: Map<String, Int>,
        xp: Double,
        xpMods: XpModifiers,
        ouraniaAltar: Boolean,
        producedRunes: Int,
    ) {
        val output = rune.output.internalName
        val essenceConsumed = essence.values.sum()
        val extract = rune.extract.internalName
        val extractBonus = extractBonus(extract)
        val bloodBonusCap =
            if (!ouraniaAltar && output == BloodEssence.BLOOD_RUNE) essenceConsumed else 0

        val maxRunes = producedRunes.toLong() + extractBonus + bloodBonusCap
        if (!canFitRunes(mapOf(output to maxRunes), freedSlots(essence))) {
            mes(NO_SPACE_MESSAGE)
            return
        }
        if (!removeAll(essence)) {
            return
        }

        var totalRunes = producedRunes
        if (extractBonus > 0 && invDel(inv, extract, 1).success) {
            totalRunes += extractBonus
        }
        if (bloodBonusCap > 0) {
            applyBloodRuneBonus(essenceConsumed)?.let { bonus -> totalRunes += bonus }
        }

        if (totalRunes > 0 && invAdd(inv, output, totalRunes).failure) {
            return
        }
        advanceRunecraftingXp(xp, xpMods)
    }

    private suspend fun ProtectedAccess.canCraftRune(rune: RunecraftingRunesRow): Boolean {
        if (rune.output.internalName == ASTRAL_RUNE && vars[LUNAR_DIPLOMACY_STAGE] < LUNAR_DIPLOMACY_COMPLETE) {
            mesbox("You don't have permission yet to use this altar.")
            return false
        }
        val level = player.baseRunecraftingLvl
        if (level < rune.statReq.first().t1) {
            mesbox(
                "You need Runecrafting level ${rune.statReq.first().t1} to craft ${rune.output.name.lowercase()}s.",
            )
            return false
        }

        val validEssenceIds = rune.input.map { it.internalName }.toSet()
        val acceptsPureSubstitutes = PURE_ESSENCE in validEssenceIds
        val checkIds =
            when {
                acceptsPureSubstitutes ->
                    validEssenceIds + DAEYALT_ESSENCE + GUARDIAN_ESSENCE
                DARK_ESSENCE in validEssenceIds ->
                    validEssenceIds
                else -> validEssenceIds
            }
        val hasEssence = checkIds.any(inv::contains)
        if (!hasEssence) {
            val essenceName = rune.input.first().name.lowercase()
            if (acceptsPureSubstitutes) {
                mesbox(
                    "You do not have any $essenceName, Daeyalt essence, or guardian essence to bind.",
                )
            } else {
                mesbox("You do not have any $essenceName to bind.")
            }
            return false
        }

        return true
    }

    suspend fun ProtectedAccess.craftOurania(xpMods: XpModifiers) {
        val daeyaltCount = inv.count(DAEYALT_ESSENCE)
        if (daeyaltCount > 0) {
            craftOuraniaDaeyalt(xpMods, daeyaltCount)
            return
        }

        if (!hasPureLikeEssence()) {
            mesbox("You do not have any pure essence to bind.")
            return
        }

        preCraft()

        val essence =
            linkedMapOf(
                PURE_ESSENCE to inv.count(PURE_ESSENCE),
                GUARDIAN_ESSENCE to inv.count(GUARDIAN_ESSENCE),
            )
        craftOuraniaBatch(essence, xpMultiplier = OURANIA_XP_MULTIPLIER, xpMods = xpMods)
    }

    private suspend fun ProtectedAccess.craftOuraniaDaeyalt(
        xpMods: XpModifiers,
        daeyaltCount: Int,
    ) {
        preCraft()
        val xpMultiplier = DAEYALT_XP_MULTIPLIER * OURANIA_XP_MULTIPLIER
        craftOuraniaBatch(
            mapOf(DAEYALT_ESSENCE to daeyaltCount),
            xpMultiplier = xpMultiplier,
            xpMods = xpMods,
        )
    }

    private fun ProtectedAccess.craftOuraniaBatch(
        essence: Map<String, Int>,
        xpMultiplier: Double,
        xpMods: XpModifiers,
    ) {
        val essenceCount = essence.values.sum()
        if (essenceCount <= 0) {
            return
        }

        val level = player.baseRunecraftingLvl
        val produced = linkedMapOf<String, Int>()
        val xpByRune = mutableMapOf<String, Double>()

        repeat(essenceCount) {
            val rune = rollOuraniaRune(level)
            val output = rune.output.internalName
            val multiplier = getBonusMultiplier(output, level).toInt()
            produced[output] = (produced[output] ?: 0) + applyBonus(multiplier)
            xpByRune[output] = (xpByRune[output] ?: 0.0) + rune.xpPerEssence * xpMultiplier
        }

        if (!canFitRunes(produced.mapValues { it.value.toLong() }, freedSlots(essence))) {
            mes(NO_SPACE_MESSAGE)
            return
        }
        if (!removeAll(essence)) {
            return
        }

        var totalXp = 0.0
        for ((output, amount) in produced) {
            if (invAdd(inv, output, amount).success) {
                totalXp += xpByRune[output] ?: 0.0
            }
        }

        advanceRunecraftingXp(totalXp, xpMods)
    }

    suspend fun ProtectedAccess.craftCore(
        rune: RunecraftingRunesRow,
        coreItem: String,
        xpMods: XpModifiers,
    ) {
        if (!inv.contains(coreItem)) {
            return
        }

        val level = player.baseRunecraftingLvl
        if (level < rune.statReq.first().t1) {
            mesbox(
                "You need Runecrafting level ${rune.statReq.first().t1} to craft ${rune.output.name.lowercase()}s.",
            )
            return
        }

        preCraft()

        val output = rune.output.internalName
        val multiplier = getBonusMultiplier(output, level).toInt()
        val produced = applyBonus(CORE_RUNE_MULTIPLIER * multiplier)
        if (!canFitRunes(mapOf(output to produced.toLong()), freedSlots(mapOf(coreItem to 1)))) {
            mes(NO_SPACE_MESSAGE)
            return
        }

        if (invDel(inv, coreItem, 1).failure) {
            return
        }
        if (invAdd(inv, output, produced).failure) {
            return
        }
        advanceRunecraftingXp(rune.xpPerEssence * CORE_XP_MULTIPLIER, xpMods)
    }

    suspend fun ProtectedAccess.craftAether(xpMods: XpModifiers) {
        val aetherRune =
            RunecraftingRunesRow.all().firstOrNull {
                it.output.internalName == "obj.aetherrune"
            } ?: return

        val level = player.baseRunecraftingLvl
        if (level < aetherRune.statReq.first().t1) {
            mesbox("You need Runecrafting level ${aetherRune.statReq.first().t1} to craft aether runes.")
            return
        }

        val guardianCount = inv.count(GUARDIAN_ESSENCE)
        val soulCount = inv.count(SOUL_RUNE)
        val craftCount = minOf(guardianCount, soulCount)
        if (craftCount <= 0) {
            mes("You need guardian essence and soul runes to craft aether runes.")
            return
        }

        if (!inv.contains(AETHER_CATALYST)) {
            mes("You need an aether catalyst to craft aether runes.")
            return
        }

        preCraft()

        val output = aetherRune.output.internalName
        val extract = aetherRune.extract.internalName
        val extractBonus = extractBonus(extract)
        val produced = applyBonus(craftCount)
        val consumed =
            linkedMapOf(
                AETHER_CATALYST to 1,
                GUARDIAN_ESSENCE to craftCount,
                SOUL_RUNE to craftCount,
            )
        if (!canFitRunes(mapOf(output to produced.toLong() + extractBonus), freedSlots(consumed))) {
            mes(NO_SPACE_MESSAGE)
            return
        }
        if (!removeAll(consumed)) {
            return
        }

        var totalRunes = produced
        if (extractBonus > 0 && invDel(inv, extract, 1).success) {
            totalRunes += extractBonus
        }

        if (invAdd(inv, output, totalRunes).failure) {
            return
        }
        advanceRunecraftingXp(craftCount * aetherRune.xpPerEssence, xpMods)
    }

    private fun rollOuraniaRune(level: Int): RunecraftingRunesRow {
        val eligible =
            RunecraftingRunesRow.all().filter { row ->
                val output = row.output.internalName
                output !in ouraniaExcludedRunes &&
                    row.statReq.first().t1 <= level &&
                    PURE_ESSENCE in row.input.map { it.internalName }
            }
        return eligible.randomOrNull() ?: RunecraftingRunesRow.all().first { it.output.internalName == "obj.airrune" }
    }

    private val ouraniaExcludedRunes =
        setOf(
            "obj.sunfirerune",
            "obj.wrathrune",
            "obj.aetherrune",
            "obj.soulrune",
        )

    fun getBonusMultiplier(rune: String, level: Int): Double =
        when (rune) {
            "obj.airrune" -> floor(level / 11.0) + 1
            "obj.mindrune" -> floor(level / 14.0) + 1
            "obj.waterrune" -> floor(level / 19.0) + 1
            "obj.earthrune" -> floor(level / 26.0) + 1
            "obj.firerune" -> floor(level / 35.0) + 1
            "obj.bodyrune" -> floor(level / 46.0) + 1
            "obj.cosmicrune" -> floor(level / 59.0) + 1
            "obj.chaosrune" -> floor(level / 74.0) + 1
            "obj.naturerune" -> floor(level / 91.0) + 1
            "obj.astralrune" -> floor(level / 82.0) + 1
            "obj.lawrune" -> floor(level / 95.0) + 1
            "obj.deathrune" -> floor(level / 99.0) + 1
            else -> 1.0
        }

    suspend fun ProtectedAccess.craftCombination(combo: ComboruneRecipeRow, xpMods: XpModifiers) {
        val output = combo.output ?: return
        val input = combo.input ?: return
        val talisman = combo.talisman ?: return
        val xp = combo.xp ?: return

        if (!canCraftCombo(output, input, talisman, combo.statReq.first().t1)) {
            return
        }

        preCraft()

        val craftCount =
            minOf(countPureLikeEssence(), inv.count(input.internalName))
        if (craftCount <= 0) {
            return
        }

        val usingMagicImbue = player.isActive()
        val pureTaken = minOf(inv.count(PURE_ESSENCE), craftCount)
        val consumed = linkedMapOf<String, Int>()
        if (!usingMagicImbue) {
            consumed[talisman.internalName] = 1
        }
        consumed[PURE_ESSENCE] = pureTaken
        consumed[GUARDIAN_ESSENCE] = craftCount - pureTaken
        consumed[input.internalName] = craftCount

        val extractBonus = extractBonus(COMBINATION_EXTRACT)
        val maxRunes = craftCount.toLong() + extractBonus
        if (!canFitRunes(mapOf(output.internalName to maxRunes), freedSlots(consumed))) {
            mes(NO_SPACE_MESSAGE)
            return
        }
        if (!removeAll(consumed)) {
            return
        }

        val wearingBinding = player.isWearing()
        val craftedCount =
            if (wearingBinding) {
                craftCount
            } else {
                (1..craftCount).count { random.of(100) < 50 }
            }

        var totalRunes = craftedCount
        if (extractBonus > 0 && invDel(inv, COMBINATION_EXTRACT, 1).success) {
            totalRunes += extractBonus
        }

        if (totalRunes > 0 && invAdd(inv, output.internalName, totalRunes).failure) {
            return
        }
        advanceRunecraftingXp(craftedCount * (xp.toDouble() / 10.0), xpMods)

        if (wearingBinding) {
            consumeChargeAfterCombo()
        }
    }

    fun ProtectedAccess.advanceRunecraftingXp(baseXp: Double, xpMods: XpModifiers) {
        statAdvance("stat.runecrafting", baseXp * xpMods.get(player, "stat.runecrafting"))
    }

    private val RunecraftingRunesRow.xpPerEssence: Double
        get() = xp / 10.0

    private fun ProtectedAccess.countPureLikeEssence(): Int =
        inv.count(PURE_ESSENCE) + inv.count(GUARDIAN_ESSENCE)

    private fun ProtectedAccess.hasPureLikeEssence(): Boolean = countPureLikeEssence() > 0

    private fun ProtectedAccess.extractBonus(extract: String): Int =
        if (inv.contains(extract)) runecraftingExtract[extract] ?: 0 else 0

    private fun ProtectedAccess.removeAll(items: Map<String, Int>): Boolean {
        for ((item, amount) in items) {
            if (amount > 0 && invDel(inv, item, amount).failure) {
                return false
            }
        }
        return true
    }

    private fun ProtectedAccess.freedSlots(items: Map<String, Int>): Int {
        var freed = 0
        for ((item, amount) in items) {
            var remaining = amount
            for (obj in inv) {
                if (remaining <= 0) {
                    break
                }
                if (obj == null || !obj.isType(item)) {
                    continue
                }
                if (obj.count > remaining) {
                    break
                }
                remaining -= obj.count
                freed++
            }
        }
        return freed
    }

    private fun ProtectedAccess.canFitRunes(outputs: Map<String, Long>, freedSlots: Int): Boolean {
        var slotsNeeded = 0
        for ((output, amount) in outputs) {
            if (amount <= 0) {
                continue
            }
            val existing = inv.physicalCount(output)
            if (existing + amount > Int.MAX_VALUE) {
                return false
            }
            if (existing == 0) {
                slotsNeeded++
            }
        }
        return slotsNeeded <= inv.freeSpace() + freedSlots
    }

    private suspend fun ProtectedAccess.canCraftCombo(
        output: ItemServerType,
        input: ItemServerType,
        talisman: ItemServerType,
        requiredLevel: Int?,
    ): Boolean {
        val levelReq = requiredLevel ?: return false
        val level = player.baseRunecraftingLvl
        val outputName = output.name
        val inputName = input.name
        val talismanName = talisman.name

        if (level < levelReq) {
            mesbox("You need Runecrafting level $levelReq to craft ${outputName}s.")
            return false
        }
        if (!hasPureLikeEssence()) {
            mes("You need pure essence to craft ${outputName}s.")
            return false
        }
        if (!inv.contains(input.internalName)) {
            mes("You need ${inputName}s to craft ${outputName}s.")
            return false
        }
        if (!player.isActive() && !inv.contains(talisman.internalName)) {
            mes("You need a $talismanName to craft ${outputName}s.")
            return false
        }
        return true
    }
}
