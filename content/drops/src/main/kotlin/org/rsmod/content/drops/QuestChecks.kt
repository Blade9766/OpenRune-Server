package org.rsmod.content.drops

import org.rsmod.content.quest.manager.QuestItemDrops
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player

public fun Player.hasCompletedQuest(quest: String): Boolean =
    QuestRequirements.hasCompleted(this, quest)

public fun Player.isOnQuest(quest: String): Boolean =
    QuestRequirements.isOnQuest(this, quest)

/** [isOnQuest], and the quest still wants [obj] dropped (see [QuestItemDrops]). */
public fun Player.needsQuestDrop(quest: String, obj: String): Boolean =
    isOnQuest(quest) && QuestItemDrops.isNeeded(this, obj)
