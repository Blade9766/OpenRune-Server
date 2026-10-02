package org.rsmod.content.quest.area.tirannwn.templeoflight

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * Thorgel's shopping list: the 47 items every list asks for, plus one ticket, one book and one key
 * picked at random when the list is written (the OSRS wiki's item list). The picks are saved on
 * `varp.mourning2_state` the moment the list is written, so logging out never changes them; only
 * finishing a list and asking for a new one picks again. Each delivered item sets one bit across
 * `varp.mourning2_thorgel_1` and `_2`, in [entries] order, which is append-only.
 *
 * `varbit.mourning_dwarf_startedtask` holds where the player is: 0 before any list, 1 with a list
 * open, 2 between lists.
 */
object ThorgelList {
    class Entry(val index: Int, val objs: List<String>, val label: String)

    val GUARANTEED =
        listOf(
            "obj.babydragon_bones" to "Babydragon bones",
            "obj.ball_of_wool" to "Ball of wool",
            "obj.bronze_bar" to "Bronze bar",
            "obj.bronze_med_helm" to "Bronze med helm",
            "obj.bucket_milk" to "Bucket of milk",
            "obj.cake_tin" to "Cake tin",
            "obj.cheese" to "Cheese",
            "obj.chisel" to "Chisel",
            "obj.cooked_meat" to "Cooked meat",
            "obj.egg" to "Egg",
            "obj.slayer_facemask" to "Facemask",
            "obj.fishing_rod" to "Fishing rod",
            "obj.flax" to "Flax",
            "obj.gold_ring" to "Gold ring",
            "obj.hammer" to "Hammer",
            "obj.iron_axe" to "Iron axe",
            "obj.nails_iron" to "Iron nails",
            "obj.iron_pickaxe" to "Iron pickaxe",
            "obj.jug_wine" to "Jug of wine",
            "obj.kebab" to "Kebab",
            "obj.knife" to "Knife",
            "obj.leather_boots" to "Leather boots",
            "obj.leather_gloves" to "Leather gloves",
            "obj.lobster_pot" to "Lobster pot",
            "obj.lockpick" to "Lockpick",
            "obj.necklace_mould" to "Necklace mould",
            "obj.needle" to "Needle",
            "obj.oak_logs" to "Oak logs",
            "obj.piedish" to "Pie dish",
            "obj.woodplank" to "Plank",
            "obj.pot_flour" to "Pot of flour",
            "obj.cactus_potato" to "Potato cactus",
            "obj.sack_potato_10" to "Potatoes(10)",
            "obj.blankrune_high" to "Pure essence",
            "obj.redberries" to "Redberries",
            "obj.rope" to "Rope",
            "obj.rotten_tomato" to "Rotten tomato",
            "obj.shears" to "Shears",
            "obj.skull" to "Skull",
            "obj.spade" to "Spade",
            "obj.swamppaste" to "Swamp paste",
            "obj.thread" to "Thread",
            "obj.tinderbox" to "Tinderbox",
            "obj.unicorn_horn_dust" to "Unicorn horn dust",
            "obj.vial_water" to "Vial of water",
            "obj.white_apron" to "White apron",
            "obj.white_berries" to "White berries",
        )

    val TICKETS =
        listOf(
            listOf("obj.archery_ticket") to "Archery ticket",
            listOf("obj.agilityarena_ticket") to "Agility arena ticket",
            listOf("obj.castlewars_ticket") to "Castle wars ticket",
        )

    /** The Book of the Elemental Shield counts in either state, as Elemental Workshop leaves it. */
    val BOOKS =
        listOf(
            listOf("obj.barrows_book_history") to "Crumbling tome",
            listOf("obj.elemental_workshop_shield_book_slashed", "obj.elemental_workshop_shield_book") to "Book of the Elemental Shield",
            listOf("obj.mourning_book1") to "Prifddinas' history",
        )

    val KEYS =
        listOf(
            listOf("obj.jail_key") to "Jail key",
            listOf("obj.dusty_key") to "Dusty key",
            listOf("obj.witches_doorkey") to "Door key",
        )

    const val TICKET_VARBIT = "varbit.mourning2_thorgel_ticket"
    const val BOOK_VARBIT = "varbit.mourning2_thorgel_book"
    const val KEY_VARBIT = "varbit.mourning2_thorgel_key"
    val DELIVERED_VARPS = listOf("varp.mourning2_thorgel_1", "varp.mourning2_thorgel_2")
    private const val BITS_PER_VARP = 30

    const val TASK_NONE = 0
    const val TASK_OPEN = 1
    const val TASK_BETWEEN = 2

    val TICKET_INDEX = GUARANTEED.size
    val BOOK_INDEX = TICKET_INDEX + 1
    val KEY_INDEX = TICKET_INDEX + 2
    val SIZE = KEY_INDEX + 1

    /** The player's list as written, or null if they have none open. */
    fun entries(player: Player): List<Entry>? {
        if (player.thorgelTask != TASK_OPEN) return null
        val ticket = TICKETS.getOrNull(player.vars[TICKET_VARBIT] - 1) ?: return null
        val book = BOOKS.getOrNull(player.vars[BOOK_VARBIT] - 1) ?: return null
        val key = KEYS.getOrNull(player.vars[KEY_VARBIT] - 1) ?: return null
        return GUARANTEED.mapIndexed { index, (obj, label) -> Entry(index, listOf(obj), label) } +
            Entry(TICKET_INDEX, ticket.first, ticket.second) +
            Entry(BOOK_INDEX, book.first, book.second) +
            Entry(KEY_INDEX, key.first, key.second)
    }

    fun outstanding(player: Player): List<Entry> = entries(player).orEmpty().filter { !delivered(player, it.index) }

    fun delivered(player: Player, index: Int): Boolean {
        val varp = DELIVERED_VARPS[index / BITS_PER_VARP]
        return player.vars[varp] and (1 shl (index % BITS_PER_VARP)) != 0
    }

    fun markDelivered(player: Player, indices: Collection<Int>) {
        val words = DELIVERED_VARPS.map { player.vars[it] }.toMutableList()
        for (index in indices) {
            words[index / BITS_PER_VARP] = words[index / BITS_PER_VARP] or (1 shl (index % BITS_PER_VARP))
        }
        for ((i, varp) in DELIVERED_VARPS.withIndex()) {
            if (player.vars[varp] != words[i]) VarPlayerIntMapSetter.set(player, varp, words[i])
        }
    }

    /** Writes a fresh list: new picks (1-3 each, from [pick]), nothing delivered, the list open. */
    fun write(player: Player, pick: (Int) -> Int) {
        MourningsEndPart2Quest.setVarBit(player, TICKET_VARBIT, pick(TICKETS.size) + 1)
        MourningsEndPart2Quest.setVarBit(player, BOOK_VARBIT, pick(BOOKS.size) + 1)
        MourningsEndPart2Quest.setVarBit(player, KEY_VARBIT, pick(KEYS.size) + 1)
        for (varp in DELIVERED_VARPS) {
            if (player.vars[varp] != 0) VarPlayerIntMapSetter.set(player, varp, 0)
        }
        player.thorgelTask = TASK_OPEN
    }

    /** The list as the paper shows it: what is still needed, one item a line. */
    fun text(player: Player): String = outstanding(player).joinToString("<br>") { it.label }

    /** The first carried obj that would fill [entry], if any; noted items do not count. */
    fun carried(player: Player, entry: Entry): String? = entry.objs.firstOrNull { player.inv.contains(it) }
}
