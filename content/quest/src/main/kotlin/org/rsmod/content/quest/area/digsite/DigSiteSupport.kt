package org.rsmod.content.quest.area.digsite

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.NETTLE_TEA_CUPS
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TEA_CUPS

/** Whether the player is carrying or wearing [obj]. */
internal fun ProtectedAccess.carries(obj: String): Boolean =
    player.inv.contains(obj) || player.worn.contains(obj)

/** Whether [obj] is anywhere the player can reach it, so "I lost it" replies can be refused. */
internal fun ProtectedAccess.carriesOrBanks(obj: String): Boolean =
    carries(obj) || bank.count(obj) > 0

/** The cup of tea the panning guide would accept, or `null` when the player has none. */
internal fun ProtectedAccess.heldTea(): String? = TEA_CUPS.firstOrNull { player.inv.contains(it) }

internal fun isNettleTea(obj: String): Boolean = obj in NETTLE_TEA_CUPS

/** "a belt buckle", "an old tooth", "some coins" - for the "you find..." boxes. */
internal fun findName(obj: String): String {
    val name = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: return "something"
    val lower = name.lowercase()
    return when {
        lower == "coins" || lower.endsWith("s") && !lower.endsWith("ss") -> "some $lower"
        lower.first() in "aeiou" -> "an $lower"
        else -> "a $lower"
    }
}
