package org.rsmod.content.quest.area.karamja.shilovillage

import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BEADS_OF_THE_DEAD
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BONE_BEADS
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.SWORD_POMMEL
import toRs

/** Whether the player carries, wears or has banked [obj]. */
internal fun ProtectedAccess.owns(obj: String): Boolean =
    player.inv.contains(obj) || player.worn.contains(obj) || bank.contains(obj)

/** Bervirius' pommel counts as found once it, or anything made from it, is in the player's hands. */
internal fun ProtectedAccess.ownsSwordPommel(): Boolean =
    owns(SWORD_POMMEL) || owns(BONE_BEADS) || owns(BEADS_OF_THE_DEAD)

/** Shows [text] on the parchment scroll used by quest journals. */
internal fun ProtectedAccess.readScroll(title: String, text: String) {
    val lines = text.lines().flatMap { it.toRs(inheritPreviousTags = true, wrapAt = SCROLL_WRAP).split("<br>") }
    ifOpenMain("interface.questjournal")
    player.runClientScript(SCROLL_INIT_SCRIPT)
    ifSetText("component.questjournal:title", "<col=7f0000>$title</col>")
    for (line in 1..SCROLL_LINES) {
        ifSetText("component.questjournal:qj$line", lines.getOrElse(line - 1) { "" })
    }
}

private const val SCROLL_INIT_SCRIPT = 5240
private const val SCROLL_WRAP = 64
private const val SCROLL_LINES = 24
