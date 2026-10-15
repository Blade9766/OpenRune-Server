package org.rsmod.content.quest.area.mortmyre.naturespirit

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Nature Spirit.
 *
 * The stage lives in `varp.druidspirit` (307), which carries no varbits and drives no multi npcs
 * or locs, so the values follow RuneLite's quest helper: 1 once Drezel sends the player off, 10
 * after entering Mort Myre by its gate, 15 once Filliman has mumbled at someone without a
 * ghostspeak amulet, 20 after speaking to him properly, 25 once the mirror has shown him he is
 * dead, 30 when he has his journal back, 35 with the druidic spell in hand, 40 once Drezel has
 * blessed the player, 60 when the three stones are complete, 65 after the transformation, 75 once
 * the sickle is blessed, 80 with the druid pouch explained, then 90, 100 and 105 for each ghast
 * slain, and the endstate [STAGE_COMPLETE] (110).
 */
@Singleton
class NatureSpiritQuest : QuestScript(
    "quest_naturespirit",
    "varp.druidspirit",
    rewards {
        xp("stat.crafting", CRAFTING_XP)
        xp("stat.defence", COMBAT_XP)
        xp("stat.hitpoints", COMBAT_XP)
    },
    ItemRewardDisplay(BLESSED_SICKLE),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
) {
    /** Set once Drezel has heard that a mumbling spirit haunts Filliman's camp. */
    val toldDrezelOfSpirit = quest.attribute(name = "TOLD_DREZEL_OF_SPIRIT", default = false)

    /** Set once the fungus has been shown to Filliman. */
    val fungusShown = quest.attribute(name = "FUNGUS_SHOWN", default = false)

    /** Set once the brown stone has absorbed the fungus. */
    val fungusPlaced = quest.attribute(name = "FUNGUS_PLACED", default = false)

    /** Set once the grey stone has absorbed the spell scroll. */
    val spellPlaced = quest.attribute(name = "SPELL_PLACED", default = false)

    override fun ScriptContext.init() {}

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun ghastsKilled(player: Player): Int =
        when (stage(player)) {
            in 0 until STAGE_FIRST_GHAST -> 0
            in STAGE_FIRST_GHAST until STAGE_SECOND_GHAST -> 1
            in STAGE_SECOND_GHAST until STAGE_ALL_GHASTS -> 2
            else -> GHASTS_NEEDED
        }

    fun meetsRequirements(player: Player): Boolean =
        QuestRequirements.hasCompleted(player, PRIEST_IN_PERIL) &&
            QuestRequirements.hasCompleted(player, RESTLESS_GHOST)

    override fun subTitle(): String =
        "speaking to <col=800000>Drezel</col> in the mausoleum beneath " +
            "<col=800000>Paterdomus</col>, the temple on the River Salve."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Drezel</red> asked me to look for his friend <red>Filliman Tarlock</red>, a " +
                    "druid who lives somewhere in the south of <red>Mort Myre Swamp</red>. I " +
                    "should enter the swamp by its gate, south of Canifis.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
            }

            objective(
                "I should search the south of the swamp for Filliman's camp.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ENTERED_SWAMP }
            }

            objective(
                "A spirit appeared at the grotto on the island in the south of the swamp, but I " +
                    "could not understand a word. A <red>ghostspeak amulet</red> might help.",
            ) {
                visibleWhen { stage(access.player) == STAGE_HEARD_SPIRIT }
            }

            objective(
                "The spirit is Filliman, but he will not believe he is dead. I need some proof " +
                    "that he is a ghost.",
            ) {
                visibleWhen { stage(access.player) == STAGE_SPOKE_TO_SPIRIT }
                hasItem(MIRROR.removePrefix(OBJ_PREFIX), "I found a mirror under his washing bowl.").strike()
            }

            objective(
                "Filliman accepts that he is dead, but has lost his <red>journal</red>. He " +
                    "remembers something about a knot.",
            ) {
                visibleWhen { stage(access.player) == STAGE_SHOWN_MIRROR }
                hasItem(JOURNAL.removePrefix(OBJ_PREFIX), "I found his journal in a knot in the grotto tree.").strike()
            }

            objective(
                "Filliman has his journal back. I should ask him how I can help.",
            ) {
                visibleWhen { stage(access.player) == STAGE_RETURNED_JOURNAL }
            }

            objective(
                "Filliman wants to become a nature spirit. He gave me a <red>druidic spell</red>, " +
                    "but I must be <red>blessed</red> by a member of the clergy at the temple to " +
                    "the north before I can cast it.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GOT_SPELL }
            }

            objective(
                "I am blessed. Filliman needs 'something from nature', 'something with faith' and " +
                    "'something of the spirit-to-become freely given', arranged on the three " +
                    "stones around him.",
            ) {
                visibleWhen { stage(access.player) in STAGE_BLESSED until STAGE_PUZZLE_SOLVED }
                attribute(fungusPlaced, "The brown stone absorbed a Mort Myre fungus.", strike = true)
                attribute(spellPlaced, "The grey stone absorbed the spell scroll.", strike = true)
            }

            objective(
                "The stones are complete. Filliman is waiting for me inside his <red>grotto</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_PUZZLE_SOLVED }
            }

            objective(
                "Filliman has become a nature spirit. He wants a <red>silver sickle</red> to bless.",
            ) {
                visibleWhen { stage(access.player) in STAGE_TRANSFORMED until STAGE_SICKLE_BLESSED }
                hasItem(SILVER_SICKLE.removePrefix(OBJ_PREFIX), "I have a silver sickle.").strike()
            }

            objective(
                "The nature spirit wants me to slay three <red>ghasts</red> in Mort Myre. I can " +
                    "cast Bloom with my blessed sickle, fill my <red>druid pouch</red> with what " +
                    "grows, and use it to make the ghasts visible.",
            ) {
                visibleWhen { stage(access.player) in STAGE_SICKLE_BLESSED until STAGE_ALL_GHASTS }
                custom(true, "I have slain ${ghastsKilled(access.player)} of $GHASTS_NEEDED ghasts.")
            }

            objective(
                "I have slain all three ghasts. I should tell the nature spirit in his grotto.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ALL_GHASTS }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Drezel sent me into Mort Myre to find the druid Filliman Tarlock. I found his " +
                    "spirit at his grotto and, with a mirror, convinced him he had died.",
            )
            line(
                "With his journal, Drezel's blessing, a fungus and his own spell, I helped him " +
                    "become a nature spirit. He blessed my silver sickle and gave me a druid pouch.",
            )
            line(
                "I released three ghasts from Mort Myre, and his grotto is now an Altar of Nature.",
            )
        }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_ENTERED_SWAMP = 10
        const val STAGE_HEARD_SPIRIT = 15
        const val STAGE_SPOKE_TO_SPIRIT = 20
        const val STAGE_SHOWN_MIRROR = 25
        const val STAGE_RETURNED_JOURNAL = 30
        const val STAGE_GOT_SPELL = 35
        const val STAGE_BLESSED = 40
        const val STAGE_PUZZLE_SOLVED = 60
        const val STAGE_TRANSFORMED = 65
        const val STAGE_SICKLE_BLESSED = 75
        const val STAGE_POUCH_GIVEN = 80
        const val STAGE_FIRST_GHAST = 90
        const val STAGE_SECOND_GHAST = 100
        const val STAGE_ALL_GHASTS = 105
        const val STAGE_COMPLETE = 110

        const val GHASTS_NEEDED = 3
        const val CRAFTING_XP = 3000.0
        const val COMBAT_XP = 2000.0

        const val PRIEST_IN_PERIL = "quest_priestinperil"
        const val RESTLESS_GHOST = "quest_restlessghost"

        const val OBJ_PREFIX = "obj."
        const val GHOSTSPEAK = "obj.amulet_of_ghostspeak"
        const val GHOSTSPEAK_ENCHANTED = "obj.amulet_of_ghostspeak_enchanted"
        const val MEAT_PIE = "obj.meat_pie"
        const val APPLE_PIE = "obj.apple_pie"
        const val WASHING_BOWL = "obj.bowl_empty_filliman"
        const val MIRROR = "obj.mirror"
        const val JOURNAL = "obj.filliman_journal"
        const val DRUIDIC_SPELL = "obj.bloom_spell"
        const val USED_SPELL = "obj.used_bloom_spell"
        const val FUNGUS = "obj.mortmyremushroom"
        const val STEM = "obj.mortmyrebuddingstem"
        const val PEAR = "obj.mortmyrepear"
        const val SILVER_SICKLE = "obj.silver_sickle"
        const val BLESSED_SICKLE = "obj.silver_sickle_blessed"
        const val POUCH_EMPTY = "obj.druid_pouch_empty"
        const val POUCH = "obj.druid_pouch"
        const val ROTTEN_FOOD = "obj.rotten_food"

        const val FILLIMAN = "npc.filliman_tarlock_spirit"
        const val NATURE_SPIRIT = "npc.filliman_tarlock_ns"
        const val GHAST_INVISIBLE = "npc.ghast_invis"
        const val GHAST_VISIBLE = "npc.ghast_vis"
    }
}

internal object MortMyreCoords {
    /** The two gate leaves on the swamp fence south of Canifis; the swamp is to the south. */
    val GATE_LEFT = CoordGrid(3444, 3458, 0)
    val GATE_RIGHT = CoordGrid(3443, 3458, 0)
    const val GATE_Z = 3458

    /** Outside the grotto entrance, where Filliman's spirit appears. */
    val GROTTO_DOOR_OUTSIDE = CoordGrid(3440, 3337, 0)
    val FILLIMAN_OUTSIDE = CoordGrid(3440, 3336, 0)
    val WASHING_BOWL = CoordGrid(3437, 3337, 0)

    val NATURE_STONE = CoordGrid(3439, 3336, 0)
    val FAITH_STONE = CoordGrid(3440, 3335, 0)
    val SPIRIT_STONE = CoordGrid(3441, 3336, 0)

    /**
     * Inside the grotto, just north of its door. The grotto is mapped twice: the tended grotto
     * of the quest on level 0 and the Altar of Nature it becomes on level 1.
     */
    val GROTTO_ENTRY = CoordGrid(3442, 9734, 0)
    val ALTAR_ENTRY = CoordGrid(3442, 9734, 1)
    val SPIRIT_IN_GROTTO = CoordGrid(3441, 9738, 0)

    /** The same spot in the level-1 Altar of Nature the grotto becomes. */
    val SPIRIT_AT_ALTAR = CoordGrid(3441, 9738, 1)

    /** The broken bridge south of the island: the gap sits at z 3330 between these two rows. */
    const val BRIDGE_NORTH_Z = 3331
    const val BRIDGE_SOUTH_Z = 3329

    fun onGrottoIsland(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 3435..3447 && coords.z in 3331..3344

    /**
     * The swamp decay area: the four map squares of the main swamp. Decay works on whole map
     * squares, so a few tiles beyond the fence count too.
     */
    fun inDecayArea(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 3392..3519 && coords.z in 3328..3455
}

internal fun Player.wearsGhostspeak(): Boolean =
    worn.contains(NatureSpiritQuest.GHOSTSPEAK) || worn.contains(NatureSpiritQuest.GHOSTSPEAK_ENCHANTED)
