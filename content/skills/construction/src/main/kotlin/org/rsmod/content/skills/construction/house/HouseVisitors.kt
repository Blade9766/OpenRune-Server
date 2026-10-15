package org.rsmod.content.skills.construction.house

/**
 * Who an owner lets into their house, by their private chat setting: everyone when it is on, only
 * their friends when it is set to friends, and nobody when it is off. Nobody on their ignore list
 * gets in, whatever the setting, and nobody at all while the owner has locked the house at its exit
 * portal ([LOCKED_VARBIT]).
 */
object HouseVisitors {
    const val LOCKED_VARBIT: String = "varbit.poh_house_locked"

    const val PRIVATE_ON: Int = 0
    const val PRIVATE_FRIENDS: Int = 1
    const val PRIVATE_OFF: Int = 2

    fun admits(privateChat: Int, friends: Collection<String>, ignores: Collection<String>, guest: String): Boolean {
        val name = normalise(guest)
        if (ignores.any { normalise(it) == name }) {
            return false
        }
        return when (privateChat) {
            PRIVATE_ON -> true
            PRIVATE_FRIENDS -> friends.any { normalise(it) == name }
            else -> false
        }
    }

    /** Display names match whatever their case, and a space, underscore or non-breaking space alike. */
    private fun normalise(name: String): String =
        name.trim().lowercase().replace('_', ' ').replace(NBSP, ' ')

    private val NBSP = Char(0xA0)
}
