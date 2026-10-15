package org.rsmod.content.events.halloween

import dev.openrune.types.ItemServerType
import org.rsmod.api.enums.HalloweenEnums
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

internal var Player.treats by intVarp("varp.halloween_treats")
internal var Player.treatsDeposited by intVarp("varp.halloween_treats_deposited")
internal var Player.ghostlyPresence by boolVarBit("varbit.halloween_ghostly_presence")
internal var Player.generousClaimed by intVarBit("varbit.halloween_generous_claimed")
internal var Player.ghostVision by intVarBit("varbit.hw22_trick_ghostvision")
private var Player.recentNpcIndex by intVarBit("varbit.hw22_trickortreat_lastnpc_nextidx")

internal const val MAX_TREATS = 8000
internal const val COSTUME_PIECES = 3
private const val RECENT_NPCS = 3
private const val HOLIDAY_BITS_PER_VARP = 32

internal val CAULDRON_STYLES: List<ItemServerType> by lazy {
    HalloweenEnums.halloween_cauldron_styles.sortedBy { it.key }.mapNotNull { it.value }
}

private val COSTUME_ITEMS: Set<Int> by lazy {
    HalloweenEnums.halloween_costume_items.mapNotNull { it.value?.id }.toSet()
}

internal fun Player.costumePieces(): Int = worn.objs.count { it != null && it.id in COSTUME_ITEMS }

internal fun Player.hasCostume(): Boolean = costumePieces() >= COSTUME_PIECES

private fun InvObj?.isTreatCauldron(): Boolean =
    this != null && CAULDRON_STYLES.any { it.id == id }

internal fun Player.hasTreatCauldron(): Boolean =
    worn.objs.any { it.isTreatCauldron() } || inv.objs.any { it.isTreatCauldron() }

internal fun Player.ownsTreatCauldron(): Boolean =
    hasTreatCauldron() || invMap.getOrPut("inv.bank").objs.any { it.isTreatCauldron() }

internal fun Player.cauldronStyleIndex(): Int {
    val obj =
        worn.objs.firstOrNull { it.isTreatCauldron() }
            ?: inv.objs.firstOrNull { it.isTreatCauldron() }
    return CAULDRON_STYLES.indexOfFirst { it.id == obj?.id }.coerceAtLeast(0)
}

internal fun ProtectedAccess.addTreats(amount: Int) {
    val added = amount.coerceAtMost(MAX_TREATS - player.treats)
    player.treats += added
    mes(
        "You receive $added treats and put them into your Treat cauldron. You now have " +
            "${player.treats} treats.",
    )
    if (player.treats >= MAX_TREATS) {
        mes("Your Treat cauldron is now full!")
    }
}

internal fun Player.isRecentTreater(slotId: Int): Boolean =
    slotId >= 0 && (1..RECENT_NPCS).any { vars["varp.halloween_recent_npc_$it"] == slotId + 1 }

internal fun ProtectedAccess.rememberTreater(slotId: Int) {
    val index = player.recentNpcIndex % RECENT_NPCS
    vars["varp.halloween_recent_npc_${index + 1}"] = slotId + 1
    player.recentNpcIndex = (index + 1) % RECENT_NPCS
}

internal fun ProtectedAccess.unlockHolidayItem(index: Int) {
    val varp = "varp.diango_holiday_items_${index / HOLIDAY_BITS_PER_VARP + 1}"
    vars[varp] = vars[varp] or (1 shl (index % HOLIDAY_BITS_PER_VARP))
}
