package org.rsmod.content.skills.construction.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.construction.data.Portals

/** How many of [obj] the inventory holds, counting its banknotes ([Portals.NOTES]). */
internal fun ProtectedAccess.countWithNotes(obj: String): Int =
    inv.count(obj) + (Portals.NOTES[obj]?.let { inv.count(it) } ?: 0)

/** Takes [count] of [obj], the items themselves first and then their banknotes. */
internal fun ProtectedAccess.takeWithNotes(obj: String, count: Int) {
    val loose = minOf(inv.count(obj), count)
    if (loose > 0) {
        invDel(inv, obj, loose)
    }
    val note = Portals.NOTES[obj] ?: return
    if (count > loose) {
        invDel(inv, note, count - loose)
    }
}
