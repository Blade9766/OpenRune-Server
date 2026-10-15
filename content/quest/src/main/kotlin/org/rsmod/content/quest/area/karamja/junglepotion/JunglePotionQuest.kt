package org.rsmod.content.quest.area.karamja.junglepotion

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Jungle Potion.
 *
 * The stage lives in `varp.junglepotion` (175), endstate [STAGE_COMPLETE] (12). Each herb takes two
 * steps, as in Jagex's script: the odd stage means Trufitus has asked for it, the even stage that
 * the player has picked it from its plant. Trufitus only takes a herb the player picked, so one
 * dropped by a jogre or tribesman is refused as not fresh.
 */
@Singleton
class JunglePotionQuest : QuestScript(
    QUEST_KEY,
    "varp.junglepotion",
    rewards { xp("stat.herblore", HERBLORE_XP) },
    ItemRewardDisplay(MARRENTILL, zoom = 220),
) {
    val heardFinalBlessing = quest.attribute(name = "HEARD_FINAL_BLESSING", default = false)

    override fun ScriptContext.init() {}

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun isInProgress(player: Player): Boolean = stage(player) in STAGE_GET_SNAKE_WEED until STAGE_COMPLETE

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    fun canStart(player: Player): Boolean = QuestRequirements.hasCompleted(player, DRUIDIC_RITUAL)

    /** The herb Trufitus is waiting for, or null outside the herb-gathering stages. */
    fun wantedHerb(player: Player): JungleHerb? = JungleHerb.forStage(stage(player))

    /**
     * Once Trufitus has described a herb its plant yields for good. A server that assumes quests are
     * complete lets players who never started this one gather every herb as well.
     */
    fun canGather(player: Player, herb: JungleHerb): Boolean {
        val stage = stage(player)
        if (stage >= herb.askedStage) {
            return true
        }
        return stage == 0 && QuestRequirements.hasCompleted(player, QUEST_KEY)
    }

    fun onHerbPicked(access: ProtectedAccess, herb: JungleHerb) {
        if (stage(access.player) == herb.askedStage) {
            advanceTo(access, herb.pickedStage)
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Trufitus</col> in <col=800000>Tai Bwo Wannai</col> on " +
            "<col=800000>Karamja</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Trufitus Shakaya</red> of Tai Bwo Wannai needs five rare jungle herbs for a " +
                    "potion that will let him commune with his gods. He will tell me where to find " +
                    "each one in turn, and I must pick each herb myself and clean it before I hand it over.",
            ) {}

            for (herb in JungleHerb.entries) {
                objective(herb.clue) {
                    visibleWhen { stage(access.player) in herb.askedStage..herb.pickedStage }
                }
                objective("I have picked the ${herb.displayName}. Once it is clean I should take it to <red>Trufitus</red>.") {
                    visibleWhen { stage(access.player) == herb.pickedStage }
                }
                objective("I have given Trufitus the ${herb.displayName}.") {
                    visibleWhen { stage(access.player) > herb.pickedStage }
                    finalise(strike = true)
                }
            }

            objective("Trufitus has all five herbs. I should speak to him to see the ritual through.") {
                visibleWhen { stage(access.player) == STAGE_ALL_HERBS }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "The people of Tai Bwo Wannai had fled their village into the jungle, and their " +
                    "shaman Trufitus needed to ask the gods what fate awaited them.",
            )
            line(
                "I found Snake Weed by the marshy vines, Ardrigal beneath the palms on the eastern " +
                    "peninsula, Sito Foil on scorched earth, Volencia Moss on the mining rocks and " +
                    "Rogue's Purse deep in the caverns below the northern cliffs.",
            )
            line("In return Trufitus taught me some of his Herblore techniques.")
        }

    companion object {
        const val QUEST_KEY = "quest_junglepotion"
        const val DRUIDIC_RITUAL = "quest_druidicritual"

        const val STAGE_GET_SNAKE_WEED = 1
        const val STAGE_ALL_HERBS = 11
        const val STAGE_COMPLETE = 12

        const val HERBLORE_XP = 775.0

        const val TRUFITUS = "npc.trufitus"
        const val MARRENTILL = "obj.marentill"
        const val COINS = "obj.coins"

        const val POTHOLE_ROCKS = "loc.pothole_cave_entrance"
        const val POTHOLE_HANDHOLDS = "loc.jp_caverocksout"
        val POTHOLE_LANDING = CoordGrid(0, 44, 148, 14, 48)
        val POTHOLE_EXIT = CoordGrid(0, 44, 48, 7, 48)
    }
}

/**
 * The five herbs in the order Trufitus asks for them, with the plant each is searched from and
 * the loc that plant turns into for a minute once picked.
 */
enum class JungleHerb(
    val askedStage: Int,
    val displayName: String,
    val grimy: String,
    val clean: String,
    val plant: String,
    val pickedPlant: String,
    val searchNoun: String,
    val nothingFound: String,
    val slowSearch: Boolean,
    val clue: String,
) {
    SnakeWeed(
        askedStage = 1,
        displayName = "Snake Weed",
        grimy = "obj.unidentified_snake_weed",
        clean = "obj.snake_weed",
        plant = "loc.snake_vine_full",
        pickedPlant = "loc.snake_vine_empty",
        searchNoun = "vine",
        nothingFound = "Unfortunately, you find nothing of interest.",
        slowSearch = true,
        clue =
            "The first herb is <red>Snake Weed</red>. It grows near the vines in an area to the " +
                "<red>south west</red> where the ground turns soft and the water kisses your feet.",
    ),
    Ardrigal(
        askedStage = 3,
        displayName = "Ardrigal",
        grimy = "obj.unidentified_ardrigal",
        clean = "obj.ardrigal",
        plant = "loc.ardrigal_palm_full",
        pickedPlant = "loc.ardrigal_palm_empty",
        searchNoun = "palm",
        nothingFound = "You find nothing of significance.",
        slowSearch = false,
        clue =
            "Next is <red>Ardrigal</red>, related to the palm. It grows in its brother's shady " +
                "profusion on a small <red>peninsula to the east</red>, just after the cliffs come " +
                "down to meet the sands.",
    ),
    SitoFoil(
        askedStage = 5,
        displayName = "Sito Foil",
        grimy = "obj.unidentified_sito_foil",
        clean = "obj.sito_foil",
        plant = "loc.sito_soil_full",
        pickedPlant = "loc.sito_soil_empty",
        searchNoun = "scorched earth",
        nothingFound = "You find nothing of significance.",
        slowSearch = false,
        clue =
            "Next is <red>Sito Foil</red>, which grows best where the ground has been " +
                "<red>blackened by the living flame</red>.",
    ),
    VolenciaMoss(
        askedStage = 7,
        displayName = "Volencia Moss",
        grimy = "obj.unidentified_volencia_moss",
        clean = "obj.volencia_moss",
        plant = "loc.volencia_moss_rock_full",
        pickedPlant = "loc.volencia_moss_rock_empty",
        searchNoun = "rock",
        nothingFound = "You find nothing of significance.",
        slowSearch = false,
        clue =
            "Next is <red>Volencia Moss</red>, which clings to rocks of high metal content in a " +
                "frequently disturbed place, somewhere <red>south east</red> of the village.",
    ),
    RoguesPurse(
        askedStage = 9,
        displayName = "Rogue's Purse",
        grimy = "obj.unidentified_rogues_purse",
        clean = "obj.rogues_purse",
        plant = "loc.rogues_purse_cave_full",
        pickedPlant = "loc.rogues_purse_cave_empty",
        searchNoun = "wall",
        nothingFound = "Unfortunately, you find nothing of interest.",
        slowSearch = true,
        clue =
            "The last herb is <red>Rogue's Purse</red>. It inhabits the darkness of the " +
                "<red>caverns in the north</red> of the island, reached by a secret entrance in the " +
                "northern cliffs.",
    );

    val pickedStage: Int
        get() = askedStage + 1

    companion object {
        fun forStage(stage: Int): JungleHerb? = entries.firstOrNull { stage == it.askedStage || stage == it.pickedStage }

        fun byGrimy(obj: String): JungleHerb? = entries.firstOrNull { it.grimy == obj }

        fun byClean(obj: String): JungleHerb? = entries.firstOrNull { it.clean == obj }
    }
}
