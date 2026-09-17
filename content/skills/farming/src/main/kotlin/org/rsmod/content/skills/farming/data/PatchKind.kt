package org.rsmod.content.skills.farming.data

/**
 * The four patch families this plugin grows, and the varbit values their shared "empty patch"
 * states occupy. Every patch multiloc in the cache opens with the same four entries - three weed
 * stages and the raked patch - so the values are identical across families, but each family still
 * gets its own entry because the rake messages, tools and crop tables differ.
 */
enum class PatchKind(val label: String) {
    ALLOTMENT("allotment"),
    HERB("herb patch"),
    FLOWER("flower patch"),
    HOPS("hops patch");

    companion object {
        /** Fully overgrown; a freshly seen patch starts here. */
        const val WEEDS_HEAVY: Int = 0
        const val WEEDS_MEDIUM: Int = 1
        const val WEEDS_LIGHT: Int = 2

        /** Raked clean and ready for a seed. */
        const val WEEDED: Int = 3
    }
}
