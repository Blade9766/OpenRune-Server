package org.rsmod.content.quest.area.karamja.legendsquest

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Legends' Quest.
 *
 * The stage is the whole of `varp.legendsquest` (139), endstate [STAGE_COMPLETE] (75) from
 * `dbrow.quest_legends`. The values are Jagex's own, as 2004Scape's `quest_legends` recovered
 * them, and one of them is fixed by the cache: Radimus Erkle is the multinpc
 * `npc.radimus_erkle_hut` in his study up to 49 and `npc.radimus_erkle_guild` in the main hall
 * from [STAGE_RETURNED] (50), when the gilded totem pole is handed in. The four training sessions
 * he gives in the hall each add five, and the fourth completes the quest.
 *
 * Everything else the quest remembers sits on server varbits of `varp.legends_state` (see
 * `LegendsVars`): the mapped jungle sections, what Ungadulu has let slip, the runes pressed into
 * the marked wall, the gems placed over the carved rocks, the pure water left in the blessed bowl,
 * the crystal pieces fused in the furnace, the fate of Viyeldi and the dead heroes Nezikchened
 * raised for the final fight.
 */
@Singleton
class LegendsQuest : QuestScript(
    QUEST_KEY,
    "varp.legendsquest",
    rewards {
        scroll(
            "4 Quest Points",
            "4 x 7,650 experience in skills of",
            "your choice, from Radimus Erkle",
            "Access to the Legends' Guild",
        )
    },
    ItemRewardDisplay(GILDED_TOTEM, zoom = 250),
    completionJingle = Quest.QUEST_COMPLETE_1_JINGLE,
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun start(access: ProtectedAccess) {
        if (!isStarted(access.player)) {
            quest.setQuestStage(access, STAGE_STARTED)
        }
    }

    fun setStage(access: ProtectedAccess, stage: Int) {
        quest.setQuestStage(access, stage)
    }

    /** Moves the quest on to [stage] only when it currently stands at [from]. */
    fun advanceFrom(access: ProtectedAccess, from: Int, stage: Int) {
        if (stage(access.player) == from) {
            quest.setQuestStage(access, stage)
        }
    }

    /** Moves the quest on to [stage] unless it is already there or beyond. */
    fun raiseTo(access: ProtectedAccess, stage: Int) {
        if (stage(access.player) < stage) {
            quest.setQuestStage(access, stage)
        }
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    fun hasRequiredQuests(player: Player): Boolean =
        REQUIRED_QUESTS.all { QuestRequirements.hasCompleted(player, it.first) }

    fun missingQuests(player: Player): List<String> =
        REQUIRED_QUESTS.filterNot { QuestRequirements.hasCompleted(player, it.first) }.map { it.second }

    fun questPoints(player: Player): Int = player.legendsQuestPoints

    /** Anywhere the player holds [obj]: carried, worn or banked. */
    fun owns(access: ProtectedAccess, obj: String): Boolean =
        access.inv.count(obj) > 0 || access.worn.count(obj) > 0 || access.bank.count(obj) > 0

    /** Wipes the quest's own varbits whenever the quest is reset back to not started. */
    fun syncVars(player: Player) {
        if (stage(player) != 0) {
            return
        }
        player.legendsMapped = 0
        player.legendsEnteredCavern = false
        player.legendsAskedWhere = false
        player.legendsAskedWho = false
        player.legendsCalledVacu = false
        player.legendsRunesPlaced = 0
        player.legendsGems = 0
        player.legendsBowlUses = 0
        player.legendsWinchRope = false
        player.legendsBravery = false
        player.legendsCrystals = 0
        player.legendsKilledViyeldi = false
        player.legendsGaveDagger = false
        player.legendsHeroesSlain = 0
    }

    override fun subTitle(): String =
        "talking to the guards at the <col=800000>Legends' Guild</col>, north of " +
            "<col=800000>Ardougne</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(player.player)
            objective(
                "<red>Radimus Erkle</red> will admit me to the <red>Legends' Guild</red> if I map " +
                    "the <red>Kharazi Jungle</red> in the south of <red>Karamja</red> and bring back a " +
                    "gift from its natives to display in the Guild.",
            ) {
                visibleWhen { stage >= STAGE_STARTED }
            }
            jungleObjectives(player, stage)
            ungaduluObjectives(stage)
            yommiObjectives(stage)
            sourceObjectives(player, stage)
            totemObjectives(stage)
        }

    private fun QuestJournalBuilder.jungleObjectives(access: ProtectedAccess, stage: Int) {
        val mapped = Integer.bitCount(access.player.legendsMapped)
        objective(
            "I need an <red>axe</red> and a <red>machete</red> to cut my way into the jungle, " +
                "and <red>papyrus</red> and <red>charcoal</red> to map its three areas. " +
                "I have mapped $mapped of them so far.",
        ) {
            visibleWhen { stage == STAGE_STARTED }
        }
        objective(
            "I have finished the map. A <red>jungle forester</red> on the edge of the jungle " +
                "might trade something for a look at it.",
        ) {
            visibleWhen { stage == STAGE_MAPPED_JUNGLE }
        }
        objective(
            "The forester gave me a <red>bullroarer</red>. If I swing it inside the Kharazi " +
                "Jungle it may attract a native.",
        ) {
            visibleWhen { stage in STAGE_GOT_BULLROARER..STAGE_SWUNG_BULLROARER }
        }
    }

    private fun QuestJournalBuilder.ungaduluObjectives(stage: Int) {
        objective(
            "<red>Gujuo</red> of the Kharazi tribe told me their totem pole has been corrupted. " +
                "Their shaman <red>Ungadulu</red> has the seeds to grow a new one, but he is held " +
                "in caves behind three rocks in the north-west of the jungle.",
        ) {
            visibleWhen { stage in STAGE_ACCEPTED_RESCUE..STAGE_FOUND_ENTRANCE }
        }
        objective(
            "Ungadulu is trapped inside a flaming octagram and seems possessed. He mumbled " +
                "something about <red>pure water</red>.",
        ) {
            visibleWhen { stage == STAGE_SPOKE_UNGADULU }
        }
        objective(
            "Gujuo says the sacred water pool in the middle of the jungle is pure, but it can " +
                "only be carried in a <red>blessed vessel</red> made of the <red>metal of the " +
                "sun</red>. I should make one, have Gujuo bless it and fill it using a " +
                "<red>hollow reed</red>.",
        ) {
            visibleWhen { stage == STAGE_ASKED_HOLY_WATER }
        }
        objective(
            "I have pure water to douse the flames. There must be something in these caves that " +
                "can drive out whatever has taken Ungadulu - perhaps a <red>Book of Binding</red>.",
        ) {
            visibleWhen { stage in STAGE_FILLED_BOWL..STAGE_SUMMONED_NEZIKCHENED }
        }
    }

    private fun QuestJournalBuilder.yommiObjectives(stage: Int) {
        objective(
            "I drove the demon <red>Nezikchened</red> out of Ungadulu. He can give me " +
                "<red>Yommi tree seeds</red>, which must be germinated in pure water before they " +
                "are planted in fertile soil.",
        ) {
            visibleWhen { stage == STAGE_DEFEATED_NEZIKCHENED_FIRE }
        }
        objective(
            "The seeds are germinated. I should plant them in some <red>fertile soil</red> in " +
                "the jungle and water the tree with more pure water.",
        ) {
            visibleWhen { stage == STAGE_GERMINATED_SEEDS }
        }
        objective(
            "The sacred pool has turned to sludge. <red>Gujuo</red> may know where else pure " +
                "water can be found.",
        ) {
            visibleWhen { stage == STAGE_POOL_DRIED }
        }
    }

    private fun QuestJournalBuilder.sourceObjectives(access: ProtectedAccess, stage: Int) {
        objective(
            "The source of the pool lies deep below Ungadulu's caves, guarded by a supernatural " +
                "fear. Gujuo told me to brew a <red>bravery potion</red> from <red>Snake " +
                "weed</red> and <red>Ardrigal</red> in a vial of water.",
        ) {
            visibleWhen { stage == STAGE_TALKED_GUJUO_POOL }
            custom(access.player.legendsBravery, "I have drunk the bravery potion.")
        }
        objective(
            "I climbed down into the <red>Viyeldi caves</red>. Three dead heroes guard pieces " +
                "of a crystal that might be fused together in an ancient furnace.",
        ) {
            visibleWhen { stage in STAGE_ENTERED_LOWER_DUNGEON..STAGE_CRYSTAL_SMELTED }
        }
        objective(
            "A barrier guards the cavern of the source. A heart-shaped <red>recess</red> in the " +
                "wall beside it might take the crystal once it has been brought to life.",
        ) {
            visibleWhen { stage == STAGE_CRYSTAL_SMELTED }
        }
        objective(
            "A boulder stops the stream in the cavern of the source, and a spirit called " +
                "<red>Echned Zekin</red> haunts it.",
        ) {
            visibleWhen { stage in STAGE_HEART_IN_RECESS..STAGE_RECEIVED_DAGGER }
        }
        objective(
            "The spirit was <red>Nezikchened</red>. I beat him again and can now fill my " +
                "blessed bowl at the source.",
        ) {
            visibleWhen { stage == STAGE_DEFEATED_NEZIKCHENED_WATER }
        }
        objective(
            "I have pure water from the source. Now I can grow a <red>Yommi tree</red>, fell it " +
                "and carve a totem pole from it with a rune axe.",
        ) {
            visibleWhen { stage == STAGE_SACRED_WATER }
        }
    }

    private fun QuestJournalBuilder.totemObjectives(stage: Int) {
        objective(
            "I carved a <red>Yommi totem</red>. I should use it on the corrupted totem pole in " +
                "the jungle.",
        ) {
            visibleWhen { stage in STAGE_COLLECTED_TOTEM..STAGE_DEFEATED_NEZIKCHENED_FINAL }
        }
        objective("I replaced the evil totem pole. <red>Gujuo</red> may want to see me.") {
            visibleWhen { stage == STAGE_REPLACED_TOTEM }
        }
        objective(
            "Gujuo gave me a <red>gilded totem pole</red>. I should take it and the completed " +
                "map back to <red>Radimus Erkle</red>.",
        ) {
            visibleWhen { stage == STAGE_GOT_GILDED_TOTEM }
        }
        objective(
            "Radimus Erkle has admitted me to the Guild and offered me training in the main " +
                "Legends' Guild hall.",
        ) {
            visibleWhen { stage in STAGE_RETURNED until STAGE_COMPLETE }
        }
    }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line("I mapped the Kharazi Jungle for Radimus Erkle and befriended Gujuo of the Kharazi tribe.")
            line(
                "I freed the shaman Ungadulu from the demon Nezikchened, found the source of the " +
                    "sacred water beneath the Viyeldi caves and banished Nezikchened for good.",
            )
            line(
                "I grew a Yommi tree, carved a totem pole to replace the corrupted one, and was " +
                    "given a gilded totem pole that now stands in the Legends' Guild.",
            )
            line("I am now a member of the Legends' Guild.")
        }

    companion object {
        const val QUEST_KEY = "quest_legends"

        const val STAGE_STARTED = 1
        const val STAGE_MAPPED_JUNGLE = 2
        const val STAGE_GOT_BULLROARER = 3
        const val STAGE_SWUNG_BULLROARER = 4
        const val STAGE_ACCEPTED_RESCUE = 5
        const val STAGE_FOUND_ENTRANCE = 6
        const val STAGE_SPOKE_UNGADULU = 7
        const val STAGE_ASKED_HOLY_WATER = 8
        const val STAGE_FILLED_BOWL = 10
        const val STAGE_SUMMONED_NEZIKCHENED = 11
        const val STAGE_DEFEATED_NEZIKCHENED_FIRE = 12
        const val STAGE_GERMINATED_SEEDS = 13
        const val STAGE_POOL_DRIED = 14
        const val STAGE_TALKED_GUJUO_POOL = 15
        const val STAGE_ENTERED_LOWER_DUNGEON = 16
        const val STAGE_CRYSTAL_SMELTED = 17
        const val STAGE_HEART_IN_RECESS = 18
        const val STAGE_PUSHED_BOULDER = 19
        const val STAGE_RECEIVED_DAGGER = 20
        const val STAGE_DEFEATED_NEZIKCHENED_WATER = 22
        const val STAGE_SACRED_WATER = 25
        const val STAGE_COLLECTED_TOTEM = 30
        const val STAGE_SPAWNED_NEZIKCHENED_FINAL = 32
        const val STAGE_DEFEATED_NEZIKCHENED_FINAL = 35
        const val STAGE_REPLACED_TOTEM = 40
        const val STAGE_GOT_GILDED_TOTEM = 45
        const val STAGE_RETURNED = 50
        const val STAGE_TRAINING_STEP = 5
        const val STAGE_TRAINED_FOUR = 70
        const val STAGE_COMPLETE = 75

        const val REQUIRED_QUEST_POINTS = 107
        const val TRAINING_XP = 7650.0

        val REQUIRED_QUESTS =
            listOf(
                "quest_heroes" to "Heroes' Quest",
                "quest_familycrest" to "Family Crest",
                "quest_shilovillage" to "Shilo Village",
                "quest_undergroundpass" to "Underground Pass",
                "quest_waterfall" to "Waterfall Quest",
            )

        const val NOTES = "obj.thkaramjamap"
        const val NOTES_COMPLETE = "obj.thkaramjamapcomp"
        const val BULLROARER = "obj.bullroarer"
        const val MACHETE = "obj.machette"
        const val PAPYRUS = "obj.papyrus"
        const val CHARCOAL = "obj.charcoal"
        const val SKETCH = "obj.goldbowlpic"
        const val GOLD_BOWL = "obj.goldbowl_empty"
        const val GOLD_BOWL_WATER = "obj.goldbowl_water"
        const val GOLD_BOWL_PURE = "obj.goldbowl_pure"
        const val BLESSED_BOWL = "obj.goldbowlbless_empty"
        const val BLESSED_BOWL_WATER = "obj.goldbowlbless_water"
        const val BLESSED_BOWL_PURE = "obj.goldbowlbless_pure"
        const val HOLLOW_REED = "obj.reed_hollow"
        const val SHAMANS_TOME = "obj.shamans_tome"
        const val BOOK_OF_BINDING = "obj.book_of_binding"
        const val ENCHANTED_VIAL = "obj.vial_enchanted"
        const val HOLY_WATER = "obj.holy_water"
        const val YOMMI_SEEDS = "obj.yommiseeds"
        const val YOMMI_SEEDS_GERMINATED = "obj.yommiseeds_germ"
        const val SNAKEWEED_MIXTURE = "obj.snakeweed_sol"
        const val ARDRIGAL_MIXTURE = "obj.ardrigal_sol"
        const val BRAVERY_POTION = "obj.bravery_pot"
        const val VIYELDI_HAT = "obj.viyeldihat"
        const val CRYSTAL_CHUNK = "obj.heartcrystal_sectiona"
        const val CRYSTAL_HUNK = "obj.heartcrystal_sectionb"
        const val CRYSTAL_LUMP = "obj.heartcrystal_sectionc"
        const val HEART_CRYSTAL = "obj.heartcrystal"
        const val HEART_CRYSTAL_GLOWING = "obj.heartcrystal_glow"
        const val DARK_DAGGER = "obj.deathdagger"
        const val GLOWING_DAGGER = "obj.deathdaggerdone"
        const val HOLY_FORCE = "obj.holyforce"
        const val YOMMI_TOTEM = "obj.thtotempole"
        const val GILDED_TOTEM = "obj.thtotempolegift"

        val GOLD_BOWLS = listOf(GOLD_BOWL, GOLD_BOWL_WATER, GOLD_BOWL_PURE)
        val BLESSED_BOWLS = listOf(BLESSED_BOWL, BLESSED_BOWL_WATER, BLESSED_BOWL_PURE)
    }
}

private val Player.legendsQuestPoints: Int by intVarp("varp.qp")
