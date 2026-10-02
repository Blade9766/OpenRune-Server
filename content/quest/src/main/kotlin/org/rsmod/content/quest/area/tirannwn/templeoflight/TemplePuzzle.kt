package org.rsmod.content.quest.area.tirannwn.templeoflight

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * A player's light puzzle: their saved pillars, what they are owed and what waits in the crystal
 * dispenser's tray.
 *
 * Every change goes through [commit], which saves the pillars and redraws the player's light in
 * the same tick as the inventory change that caused it, so an interrupted dialogue or a repeated
 * packet either finds the old state or the new one, never half of each.
 *
 * Pieces are never created anywhere but the chests and the dispenser. The player is owed the
 * dispenser's starting set and each opened chest's contents ([owed]); resetting the dispenser
 * takes everything out of the pillars and makes good whatever the player no longer holds anywhere
 * (inventory, equipment or bank), so the pieces in the tray, the pillars and the player's
 * possession never add up to more than they are owed.
 */
@Singleton
class TemplePuzzle @Inject constructor(val quest: MourningsEndPart2Quest) {
    private val lights = TempleLights()

    fun state(player: Player): PuzzleState = TemplePuzzleStore.load(player)

    fun trace(player: Player): LightResult = lights.trace(player)

    fun refresh(player: Player): LightResult = lights.refresh(player)

    fun commit(player: Player, state: PuzzleState): LightResult {
        TemplePuzzleStore.save(player, state)
        return lights.refresh(player)
    }

    fun isOpen(player: Player, door: LightDoor): Boolean = trace(player).isOpen(door)

    fun owed(player: Player): Map<TempleItem, Int> {
        val owed = HashMap(STARTING_SET)
        for (chest in TempleChest.entries) {
            if (player.vars[chest.varbit] == 0) continue
            for ((item, count) in chest.contents) owed[item] = (owed[item] ?: 0) + count
        }
        return owed
    }

    fun held(player: Player): Map<TempleItem, Int> {
        val bank = player.invMap.getOrPut("inv.bank")
        return TempleItem.entries.associateWith { item ->
            player.inv.count(item.obj) + player.worn.count(item.obj) + bank.count(item.obj)
        }
    }

    fun tray(player: Player): Map<TempleItem, Int> = TempleItem.entries.associateWith { player.vars[trayVarbit(it)] }

    fun setTray(player: Player, item: TempleItem, count: Int) {
        val varbit = trayVarbit(item)
        if (player.vars[varbit] != count) {
            VarPlayerIntMapSetter.set(player, varbit, count)
        }
    }

    /**
     * Empties every pillar into the tray and resets the preset mirrors, then tops the tray up to
     * what the player is owed beyond what they hold. Returns what the tray now holds.
     */
    fun reset(player: Player): Map<TempleItem, Int> {
        val owed = owed(player)
        val held = held(player)
        commit(player, PuzzleState())
        for (item in TempleItem.entries) {
            val missing = (owed[item] ?: 0) - (held[item] ?: 0)
            setTray(player, item, missing.coerceIn(0, TRAY_LIMIT[item] ?: 0))
        }
        if (player.vars[STARTER_VARBIT] == 0) {
            VarPlayerIntMapSetter.set(player, STARTER_VARBIT, 1)
        }
        return tray(player)
    }

    companion object {
        val STARTING_SET = mapOf(TempleItem.MIRROR to 4, TempleItem.YELLOW to 1)

        /** Whether the dispenser has handed out its starting set. */
        const val STARTER_VARBIT = "varbit.mourning_temple_parts_1"

        /**
         * The cache's dispenser trays. The cache names two of the crystal trays orange and red;
         * nothing in the client reads them, and the dispenser only ever holds yellow, cyan and
         * blue crystals, so they serve the yellow and cyan ones.
         */
        fun trayVarbit(item: TempleItem): String =
            when (item) {
                TempleItem.MIRROR -> "varbit.mourning_temple_mirrors_reset_tray"
                TempleItem.YELLOW -> "varbit.mourning_temple_orange_crystal_reset_tray"
                TempleItem.CYAN -> "varbit.mourning_temple_red_crystal_reset_tray"
                TempleItem.BLUE -> "varbit.mourning_temple_blue_crystal_reset_tray"
                TempleItem.FRACTURED_HORIZONTAL -> "varbit.mourning_temple_fractured_crystal_1_reset_tray"
                TempleItem.FRACTURED_VERTICAL -> "varbit.mourning_temple_fractured_crystal_2_reset_tray"
            }

        private val TRAY_LIMIT = TempleItem.entries.associateWith { if (it == TempleItem.MIRROR) 63 else 1 }
    }
}

/**
 * The five chests behind the light doors, in the order the walkthrough opens them, with what each
 * holds (the OSRS wiki's loot tables) and the cache flag that remembers it was emptied.
 */
enum class TempleChest(val closed: String, val open: String, val varbit: String, val contents: Map<TempleItem, Int>) {
    BLUE_DOOR("loc.mourning_temple_light_parts_2_closed", "loc.mourning_temple_light_parts_2_open", "varbit.mourning_temple_parts_2", mapOf(TempleItem.MIRROR to 2, TempleItem.CYAN to 1)),
    MAGENTA_DOOR("loc.mourning_temple_light_parts_3_closed", "loc.mourning_temple_light_parts_3_open", "varbit.mourning_temple_parts_3", mapOf(TempleItem.MIRROR to 2)),
    NORTH_WEST("loc.mourning_temple_light_parts_5_closed", "loc.mourning_temple_light_parts_5_open", "varbit.mourning_temple_parts_5", mapOf(TempleItem.MIRROR to 2, TempleItem.FRACTURED_HORIZONTAL to 1)),
    SOUTH_WEST("loc.mourning_temple_light_parts_4_closed", "loc.mourning_temple_light_parts_4_open", "varbit.mourning_temple_parts_4", mapOf(TempleItem.BLUE to 1)),
    SOUTH_EAST("loc.mourning_temple_light_parts_6_closed", "loc.mourning_temple_light_parts_6_open", "varbit.mourning_temple_parts_6", mapOf(TempleItem.MIRROR to 3, TempleItem.FRACTURED_VERTICAL to 1)),
    ;

    val slots: Int
        get() = contents.values.sum()
}
