package org.rsmod.content.quest.manager

import java.util.concurrent.ConcurrentHashMap
import org.rsmod.game.entity.Player

/**
 * Lets a quest veto its own quest-item drops. Drop tables already gate quest items on the quest
 * being in progress; a quest that registers an item here also stops it dropping again once the
 * player already has one, so a lost item comes back from the next kill and a held one never
 * doubles up.
 */
public object QuestItemDrops {
    private val needs = ConcurrentHashMap<String, (Player) -> Boolean>()

    public fun register(obj: String, needed: (Player) -> Boolean) {
        needs[obj] = needed
    }

    public fun isNeeded(player: Player, obj: String): Boolean = needs[obj]?.invoke(player) ?: true
}
