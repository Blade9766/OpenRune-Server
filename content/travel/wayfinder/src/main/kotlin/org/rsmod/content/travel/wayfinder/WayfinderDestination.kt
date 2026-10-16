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
 * One stop on the wayfinder network. [key] is the stable id player favourites and recent journeys
 * are saved by; [slot] is the row's display position and only names it while a window is open.
 * [icon] is an obj id for the list row, [preview] a sprite group id for the details picture.
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
    val arrival: String = "",
    val tags: List<String> = emptyList(),
    val icon: Int = -1,
    val preview: Int? = null,
    val cost: Int = 0,
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
            tags.any { it.lowercase().contains(needle) } ||
            keywords.any { it.contains(needle) }
    }
}
