package org.rsmod.content.quest.area.ardougne.regicide

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.PENDANT

/**
 * Lord Iorwerth's crystal pendant is his proof of allegiance, and he takes it back without a
 * word: from the moment he hands over the Big Book of Bangs, every conversation with him during
 * the quest, and King Lathas at the end, remove it from the inventory, the equipment and the bank.
 * Only the pendant is touched.
 */
@Singleton
class CrystalPendant {
    fun ProtectedAccess.confiscate() {
        for (container in listOf(inv, worn, bank)) {
            val count = container.count(PENDANT)
            if (count > 0) {
                invDel(container, PENDANT, count, strict = false)
            }
        }
    }

    fun ProtectedAccess.isCarried(): Boolean = inv.contains(PENDANT) || worn.contains(PENDANT)
}
