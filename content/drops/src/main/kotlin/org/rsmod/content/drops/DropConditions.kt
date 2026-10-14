package org.rsmod.content.drops

import org.rsmod.api.table.slayer.SlayerUnlockRow
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

private const val CHAMPIONS_GUILD_QP = 32
private const val INV = "inv.inv"

private val HYDRA_PIECES = listOf("obj.hydra_eye", "obj.hydra_fang", "obj.hydra_heart")
private val TATTERED_PAGES = listOf("obj.hosdun_moon_page", "obj.hosdun_sun_page", "obj.hosdun_temple_page")

/** Treasure Trails clue steps are not implemented, so "kill X during a clue step" drops never roll. */
public fun Player.isOnTreasureTrailKillStep(): Boolean = false

/** Sailing port bounty tasks are not implemented. */
public fun Player.isOnSailingBountyTask(): Boolean = false

/** Sailing ships (The Onyx Crest) are not implemented. */
public fun Npc.isOnTheOnyxCrest(): Boolean = false

/** Emote-, spell- and timer-triggered drops whose trigger the server doesn't track yet. */
public fun Player.isPerformingCrabDance(): Boolean = false

public fun Player.usedBloomOnSwampCrab(): Boolean = false

public fun Player.beatAraxxorSpeedTarget(): Boolean = false

public fun Player.carries(obj: String): Boolean = invMap[INV]?.contains(obj) == true

public fun Player.canReceiveChampionScroll(champion: String): Boolean =
    vars["varp.qp"] >= CHAMPIONS_GUILD_QP && vars["varbit.champions_defeated_$champion"] == 0

public fun Player.needsFrozenDoorPiece(god: String): Boolean =
    isOnQuest("miniquest_frozendoor") && vars["varbit.frozen_door_${god}_piece"] == 0

public fun Player.hasCompletedBarbarianTraining(): Boolean = vars["varbit.brut_all_cert_done"] == 1

public fun Player.hasCompletedDiary(varbit: String): Boolean = vars[varbit] == 1

public fun Player.isOnSlayerTaskFor(npc: Npc): Boolean {
    val taskId = vars["varp.slayer_target"]
    return taskId != 0 && vars["varp.slayer_count"] > 0 && SlayerTaskManager.countsAsTaskKill(npc, taskId)
}

public fun Player.hasDulyNoted(): Boolean {
    val unlock = SlayerUnlockRow.all().firstOrNull { it.name.equals("Duly noted", ignoreCase = true) }
    return unlock != null && SlayerTaskManager.hasUnlockedReward(this, unlock.bit)
}

public fun Player.needsBrittleKey(npc: Npc): Boolean =
    vars["varbit.gargboss_unlocked_roof"] == 0 &&
        !hasObjInInventoryOrBank("obj.slayer_roof_key") &&
        isOnSlayerTaskFor(npc)

public fun Player.isTopDamagerOf(npc: Npc): Boolean = npc.damageContributions.topPlayer()?.uuid == uuid

/** Runecraft pouches drop in size order: the next pouch only once every smaller one is owned. */
public fun Player.needsEssencePouch(pouch: String, smaller: List<String>): Boolean =
    !hasObjInInventoryOrBank(pouch) && smaller.all { hasObjInInventoryOrBank(it) }

public fun Player.nextHydraPiece(): String? = HYDRA_PIECES.firstOrNull { !hasObjInInventoryOrBank(it) }

public fun Player.fewestTatteredPage(): String =
    TATTERED_PAGES.minBy { page -> (invMap[INV]?.count(page) ?: 0) + (invMap["inv.bank"]?.count(page) ?: 0) }
