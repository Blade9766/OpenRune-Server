package org.rsmod.content.quest.area.lunarisle.dreammentor

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Dream Mentor.
 *
 * The stage is `varbit.dream_prog` (bits 0-5 of `varp.dream_main`), endstate 28 from
 * `dbrow.quest_dreammentor`. Every stage is even: 'Birds-Eye' Jack's bank multinpc shows him only
 * on the even values from [STAGE_NEEDS_ARMOUR] up. Cyrisus' recovery is held in the cache's own
 * `dream_health`, `dream_spirit` and `dream_armament` varbits (percentages the Guidance System
 * interface draws), the armour chosen from his bank in `dream_arma_item1..5`, and where he waits in
 * town in `dream_cyris_multi`, which both of his town multinpcs switch on.
 */
@Singleton
class DreamMentorQuest : QuestScript(
    QUEST_KEY,
    "varp.dream_main",
    rewards {
        xp("stat.hitpoints", HITPOINTS_XP)
        xp("stat.magic", MAGIC_XP)
        item(DREAMY_LAMP)
        scroll(
            "15,000 Hitpoints XP",
            "10,000 Magic XP",
            "Seven new Lunar spells",
            "A dreamy lamp",
            "'Birds-Eye' Jack's banking",
            "without a Seal of Passage",
        )
    },
    ItemRewardDisplay(DREAMY_LAMP, zoom = 200),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
    questVarbit = "varbit.dream_prog",
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    /**
     * The quest's own unlocks: really complete, or never started while the server's requirement
     * policy counts it as done. A bare policy check would also unlock them for players mid-quest.
     */
    fun unlocked(player: Player): Boolean =
        isComplete(player) || stage(player) == 0 && QuestRequirements.hasCompleted(player, QUEST_KEY)

    /** Moves the stage forward to [stage]; never back, so a replayed step cannot undo progress. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    /** The quests and the combat level the player still lacks before they can help Cyrisus. */
    fun missingRequirements(player: Player): List<String> {
        val missing = mutableListOf<String>()
        if (player.combatLevel < REQUIRED_COMBAT) {
            missing += "a combat level of $REQUIRED_COMBAT"
        }
        for ((key, name) in REQUIRED_QUESTS) {
            if (!QuestRequirements.hasCompleted(player, key)) {
                missing += name
            }
        }
        return missing
    }

    /** Clears Cyrisus' recovery and armour once the quest has been reset to not started. */
    fun syncVars(player: Player) {
        if (stage(player) != 0) {
            return
        }
        for (varbit in SUB_STATE_VARBITS) {
            setVarBit(player, varbit, 0)
        }
    }

    override fun subTitle(): String =
        "talking to the <col=800000>fallen man</col> in the small cave in the " +
            "<col=800000>Lunar Isle mine</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(access.player)
            val p = access.player
            objective(
                "I found a <red>fallen man</red> in a small cave in the <red>Lunar Isle mine</red>. " +
                    "He is badly hurt. I can <red>Inspect</red> him to see how he is doing.",
            ) {
                visibleWhen { stage in STAGE_STARTED until STAGE_STANDING }
                custom(
                    stage in RECOVERY_FOOD_STAGES,
                    "He needs feeding. He won't eat the same kind of food twice in a row, so I " +
                        "should bring <red>three different kinds</red> and take turns with them.",
                ).preserveObjective(strikeObjective = false)
                custom(
                    stage in RECOVERY_SPIRIT_STAGES,
                    "He has had enough to eat for now. I should <red>talk to him</red> and keep " +
                        "his spirits up with positive answers.",
                ).preserveObjective(strikeObjective = false)
                custom(true, "His health is at ${p.dreamHealth}% and his spirit at ${p.dreamSpirit}%.").preserveObjective(strikeObjective = false)
            }
            objective(
                "The man is called <red>Cyrisus</red>. He wants some armour before he leaves the " +
                    "cave, and says a friend at the <red>Lunar Isle bank</red> can help.",
            ) {
                visibleWhen { stage in STAGE_STANDING until STAGE_ARMED }
                stageAtLeast(
                    STAGE_GOT_CHEST,
                    "'Birds-Eye' Jack gave me <red>Cyrisus's chest</red>. I should fill it from " +
                        "Cyrisus's bank with a helm, body, legs, boots and weapon that suit him, " +
                        "then take it to him.",
                ).preserveObjective()
            }
            objective(
                "Cyrisus is happy with his armour. I need to finish feeding him and raising his " +
                    "spirit until he is fully recovered.",
            ) {
                visibleWhen { stage == STAGE_ARMED }
                custom(true, "His health is at ${p.dreamHealth}% and his spirit at ${p.dreamSpirit}%.").preserveObjective(strikeObjective = false)
            }
            objective(
                "Cyrisus admitted he is <red>afraid of combat</red>. He wants me to come with him " +
                    "to the <red>Oneiromancer</red> in the south-east of the island.",
            ) {
                visibleWhen { stage == STAGE_RESTORED }
            }
            objective(
                "The Oneiromancer can send us into Cyrisus' dream together. I need to fill her " +
                    "<red>dream vial</red> with <red>water</red>, add <red>goutweed</red>, and then " +
                    "a <red>ground astral rune</red>: an astral rune broken with a hammer and " +
                    "ground with a pestle and mortar.",
            ) {
                visibleWhen { stage == STAGE_POTION && !p.dreamCutsceneSeen }
                custom(
                    true,
                    "Then I must light the brazier in the <red>Dream Hall</red> and speak to Cyrisus.",
                ).preserveObjective(strikeObjective = false)
            }
            objective(
                "Cyrisus' fears wait for us in his dream. Without prayer, I must defeat " +
                    "<red>The Inadequacy</red>, <red>The Everlasting</red>, <red>The Untouchable</red> " +
                    "and <red>The Illusive</red> in one go. Light the Dream Hall brazier and speak " +
                    "to Cyrisus to go back in.",
            ) {
                visibleWhen { stage == STAGE_POTION && p.dreamCutsceneSeen }
            }
            objective(
                "Cyrisus has conquered his fear! I should meet him at the <red>Oneiromancer</red> " +
                    "again.",
            ) {
                visibleWhen { stage == STAGE_CONQUERED }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I nursed Cyrisus, an adventurer lost in the Lunar Isle mine, back to health, and " +
                    "dressed him for battle from his own bank with the help of 'Birds-Eye' Jack.",
            )
            line(
                "With the Oneiromancer's dream potion we entered his dream together and beat his " +
                    "four fears. In thanks he taught me seven new Lunar spells.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_dreammentor"

        const val STAGE_STARTED = 2
        const val STAGE_AWAKE = 4
        const val STAGE_SPIRIT_LIFTED = 6
        const val STAGE_FED_MORE = 8
        const val STAGE_SITTING = 10
        const val STAGE_FED_WELL = 12
        const val STAGE_STANDING = 14
        const val STAGE_NEEDS_ARMOUR = 16
        const val STAGE_GOT_CHEST = 18
        const val STAGE_ARMED = 20
        const val STAGE_RESTORED = 22
        const val STAGE_POTION = 24
        const val STAGE_CONQUERED = 26
        const val STAGE_COMPLETE = 28

        val RECOVERY_FOOD_STAGES = setOf(STAGE_STARTED, STAGE_SPIRIT_LIFTED, STAGE_SITTING)
        val RECOVERY_SPIRIT_STAGES = setOf(STAGE_AWAKE, STAGE_FED_MORE, STAGE_FED_WELL)

        const val REQUIRED_COMBAT = 85
        const val HITPOINTS_XP = 15_000.0
        const val MAGIC_XP = 10_000.0

        const val DREAMY_LAMP = "obj.dream_lantern"
        const val SEAL_OF_PASSAGE = "obj.lunar_seal_of_passage"

        val REQUIRED_QUESTS =
            listOf(
                "quest_lunardiplomacy" to "Lunar Diplomacy",
                "quest_eadgarsruse" to "Eadgar's Ruse",
            )

        val SUB_STATE_VARBITS =
            listOf(
                "varbit.dream_combattype",
                "varbit.dream_lastfood",
                "varbit.dream_health",
                "varbit.dream_spirit",
                "varbit.dream_armament",
                "varbit.dream_banker_intro",
                "varbit.dream_cutscene_seen",
                "varbit.dream_spirit_ques",
                "varbit.dream_arma_item1",
                "varbit.dream_arma_item2",
                "varbit.dream_arma_item3",
                "varbit.dream_arma_item4",
                "varbit.dream_arma_item5",
                "varbit.dream_lampused",
                "varbit.dream_damagedealt",
                "varbit.dream_cyris_multi",
                "varbit.dream_mentor_food_a",
                "varbit.dream_mentor_food_b",
            )

        fun setVarBit(player: Player, varbit: String, value: Int) {
            if (player.vars[varbit] != value) {
                VarPlayerIntMapSetter.set(player, varbit, value)
            }
        }
    }
}
