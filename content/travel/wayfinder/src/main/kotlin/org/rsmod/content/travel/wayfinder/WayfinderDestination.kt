package org.rsmod.content.travel.wayfinder

import org.rsmod.map.CoordGrid

enum class WayfinderCategory(val id: Int, val label: String) {
    Towns(0, "Towns & Cities"),
    Skilling(1, "Skilling"),
    Dungeons(2, "Dungeons"),
    Slayer(3, "Slayer"),
    Bosses(4, "Bosses"),
    Minigames(5, "Minigames & Activities"),
    Wilderness(6, "Wilderness");

    companion object {
        fun of(id: Int): WayfinderCategory? = entries.firstOrNull { it.id == id }
    }
}

enum class WayfinderDanger(val id: Int, val label: String, val colour: String) {
    Safe(0, "Safe", "00ff00"),
    Low(1, "Low danger", "ffff00"),
    High(2, "High danger", "ff981f"),
    Deadly(3, "Deadly - items are lost on death", "ff0000");

    companion object {
        fun of(id: Int): WayfinderDanger = entries.firstOrNull { it.id == id } ?: Safe
    }
}

/**
 * One stop on the wayfinder network. [slot] is the destination's permanent number: favourites
 * and recent destinations are saved by it, so it must never be reused or renumbered.
 */
data class WayfinderDestination(
    val key: String,
    val slot: Int,
    val name: String,
    val category: WayfinderCategory,
    val description: String,
    val coords: CoordGrid,
    val danger: WayfinderDanger,
    val warning: String?,
    val quest: String?,
    val botAllowed: Boolean,
    val keywords: List<String>,
    val enabled: Boolean,
) {
    /** Dangerous destinations always ask a player to confirm first. */
    val needsConfirmation: Boolean
        get() = danger >= WayfinderDanger.High || category == WayfinderCategory.Wilderness

    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            return false
        }
        return name.lowercase().contains(needle) ||
            key.contains(needle.replace(' ', '_')) ||
            keywords.any { it.contains(needle) }
    }
}
