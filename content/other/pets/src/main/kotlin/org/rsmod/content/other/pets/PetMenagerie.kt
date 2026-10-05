package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.other.pets.cats.Cats
import org.rsmod.content.other.pets.dogs.Dogs
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

/**
 * Pets kept in a player-owned house's menagerie.
 *
 * The cache lists every pet a menagerie can hold in enum 985, by a slot from 0 to 70, and the
 * client's pet house list reads one bit per slot across three varps. A pet with several forms keeps
 * the form it was stored in as the index into that pet's form enum, in its
 * `poh_menagerie_multiform_*` varbit, which is what the list draws and what comes back out.
 *
 * Besides those, a pet house keeps up to twelve extras - cats, dogs, pet rocks, pet fish and the
 * other companions the wiki lists - in the cache's `inv.poh_menagerie_pets`, which the list also
 * draws.
 */
object PetMenagerie {
    private const val SLOTS_ENUM = 985
    private val VARPS = listOf("varp.prayer20", "varp.menagerie_contents2", "varp.menagerie_contents3")
    private val FIRST_SLOT = listOf(0, 32, 63)

    /** Each form enum the pet house list switches on, mapped to the varbit holding its form. */
    private val FORMS: Map<Int, String> =
        mapOf(
            1686 to "kqpet",
            1687 to "snakepet",
            1688 to "vetionpet",
            3733 to "molepet",
            1691 to "skillpetmining",
            1689 to "skillpethunter",
            1690 to "skillpetrunecrafting",
            2861 to "skillpetagility_bigger",
            285 to "corppet",
            1893 to "infernopet",
            1711 to "olmpet",
            3856 to "verzikpet",
            995 to "smokepet",
            2946 to "thievingpet",
            3554 to "soulwarspet",
            3665 to "jadpet",
            3713 to "skillpetfishing",
            3938 to "nightmarepet",
            3939 to "sarachnispet",
            4802 to "wardenpet",
            4918 to "muspahpet",
            4904 to "callistopet",
            4905 to "venenatispet",
            3754 to "skillpetwoodcutting",
            4823 to "araxxorpet",
            3349 to "royaltitanpet",
            5774 to "gryphonbosspet",
        ).mapValues { "varbit.poh_menagerie_multiform_${it.value}" } +
            mapOf(
                2788 to "varbit.poh_menagerie_mutliform_phoenixpet",
                2849 to "varbit.poh_menagerie_mutliform_farmingpet",
            )

    /** [forms] maps each form's index in its form enum to its obj; a single-form pet is just [base]. */
    private class Slot(val index: Int, val base: Int, val forms: Map<Int, Int>, val formVarbit: String?) {
        fun holds(obj: Int): Boolean = obj == base || obj in forms.values
    }

    private val slots: List<Slot> by lazy {
        val formEnums = FORMS.keys.associateWith(::intEnum)
        intEnum(SLOTS_ENUM).toSortedMap().map { (index, base) ->
            val formEnum = formEnums.entries.firstOrNull { base in it.value.values }
            Slot(index, base, formEnum?.value.orEmpty(), formEnum?.let { FORMS.getValue(it.key) })
        }
    }

    private fun intEnum(id: Int): Map<Int, Int> =
        ServerCacheManager.getEnum(id)?.values?.entries?.associate { (key, value) ->
            (key as Number).toInt() to (value as Number).toInt()
        } ?: emptyMap()

    /** Every varbit a multi-form pet's stored form is kept in. */
    val formVarbits: Collection<String>
        get() = FORMS.values

    /** How many pets the menagerie can hold at most. */
    val size: Int
        get() = slots.size

    /** The slot that holds [obj], in any of its forms, or null when it is not a menagerie pet. */
    fun slotOf(obj: Int): Int? = slots.firstOrNull { it.holds(obj) }?.index

    fun isStored(player: Player, slot: Int): Boolean {
        val (varp, bit) = bitOf(slot) ?: return false
        return player.vars[varp] and (1 shl bit) != 0
    }

    /** Every slot [player] has a pet stored in. */
    fun stored(player: Player): List<Int> = slots.map { it.index }.filter { isStored(player, it) }

    /** Stores [obj], remembering which of its pet's forms it is. Returns false if it cannot be stored. */
    fun store(player: Player, obj: Int): Boolean {
        val slot = slots.firstOrNull { it.holds(obj) } ?: return false
        val (varp, bit) = bitOf(slot.index) ?: return false
        VarPlayerIntMapSetter.set(player, varp, player.vars[varp] or (1 shl bit))
        val form = slot.forms.entries.firstOrNull { it.value == obj }?.key
        if (slot.formVarbit != null && form != null) {
            VarPlayerIntMapSetter.set(player, slot.formVarbit, form)
        }
        return true
    }

    /** Takes the pet out of [slot] and returns the obj it was stored as, or null if it was empty. */
    fun take(player: Player, slot: Int): Int? {
        if (!isStored(player, slot)) {
            return null
        }
        val entry = slots.firstOrNull { it.index == slot } ?: return null
        val (varp, bit) = bitOf(slot) ?: return null
        VarPlayerIntMapSetter.set(player, varp, player.vars[varp] and (1 shl bit).inv())
        return formOf(player, entry)
    }

    /** The obj a stored pet shows as - its stored form. */
    fun shownObj(player: Player, slot: Int): Int? = slots.firstOrNull { it.index == slot }?.let { formOf(player, it) }

    private fun formOf(player: Player, slot: Slot): Int {
        val form = slot.formVarbit?.let { player.vars[it] } ?: return slot.base
        return slot.forms[form] ?: slot.base
    }

    /** Whether [pet], in any form, is kept in [player]'s menagerie. */
    fun holds(player: Player, pet: Pet): Boolean =
        pet.forms.any { form -> slotOf(form.objId)?.let { isStored(player, it) } == true }

    const val EXTRAS: String = "inv.poh_menagerie_pets"

    private val OTHER_EXTRAS: Set<String> =
        setOf(
            "obj.vt_useless_rock",
            "obj.fishbowl_bluefish",
            "obj.fishbowl_greenfish",
            "obj.fishbowl_spinefish",
            "obj.poh_toy_cat",
            "obj.wgs_broav",
            "obj.hw25_chair_obj_reward",
            "obj.current_affairs_mayor_of_catherby",
            "obj.easter26_runaway_egg",
        ) + (1..7).map { "obj.easter26_egg_companion" + if (it == 1) "" else "0$it" }

    val Player.menagerieExtras: Inventory
        get() = invMap.getOrPut(EXTRAS)

    /** Whether [obj] can be kept as one of a pet house's extras. */
    fun isExtra(obj: Int): Boolean =
        Cats.forObj(obj) != null ||
            Dogs.forObj(obj) != null ||
            runCatching { RSCM.getReverseMapping(RSCMType.OBJ, obj) }.getOrNull() in OTHER_EXTRAS

    /** The npc a stored extra roams the menagerie as, when it has one. */
    fun extraNpc(obj: Int): String? =
        Cats.forObj(obj)?.npc ?: Dogs.forObj(obj)?.npc ?: Pets.forObj(obj)?.second?.npc

    private fun bitOf(slot: Int): Pair<String, Int>? {
        val group = FIRST_SLOT.indexOfLast { slot >= it }.takeIf { it >= 0 } ?: return null
        return VARPS[group] to slot - FIRST_SLOT[group]
    }
}
