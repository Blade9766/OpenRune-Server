package org.rsmod.content.quest.area.feldip.bigchompy

/** The titles Rantz scratches on the ogre bow, by lifetime chompy kills. */
internal object ChompyRanks {
    private val RANKS =
        listOf(
            5 to "Ogre Novice",
            15 to "Beginner",
            20 to "Ogre Learner",
            30 to "Learner",
            40 to "Ogre Bowman",
            50 to "Bowman",
            70 to "Ogre Yeoman",
            95 to "Yeoman",
            125 to "Ogre Markman",
            170 to "Marksman",
            225 to "Ogre Woodsman",
            300 to "Woodsman",
            400 to "Ogre Forester",
            550 to "Forester",
            700 to "Ogre Bowmaster",
            1000 to "Bowmaster",
            1300 to "Ogre Expert",
            1700 to "Expert",
            2250 to "Ogre Dragon Archer",
            3000 to "Dragon Archer",
            4000 to "Expert Ogre Dragon Archer",
        )

    private const val HIGHEST = "Expert Dragon Archer"

    fun of(kills: Int): String = RANKS.firstOrNull { kills < it.first }?.second ?: HIGHEST

    fun withArticle(kills: Int): String {
        val rank = of(kills)
        val article = if (rank.first().lowercaseChar() in "aeiou") "an" else "a"
        return "$article $rank"
    }
}
