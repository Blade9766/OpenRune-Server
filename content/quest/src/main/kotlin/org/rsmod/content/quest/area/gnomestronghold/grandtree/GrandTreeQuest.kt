package org.rsmod.content.quest.area.gnomestronghold.grandtree

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Grand Tree.
 *
 * The stage lives in `varp.grandtree` (endstate 160 from `dbrow.quest_grandtree`) and its values
 * are dictated by the cache: Charlie (`npc.grandtree_charlie_multi`) is only shown for values
 * 40..150, and the watchtower trapdoor (`loc.grandtree_trapdoorclosed`) only grows its Climb-down
 * form from 130. Every other bit of progress is a quest attribute; no cache varbit shares the
 * varp, so the quest manager's varp writes never clobber anything.
 */
@Singleton
class GrandTreeQuest : QuestScript(
    "quest_grandtree",
    "varp.grandtree",
    rewards {
        xp("stat.attack", ATTACK_XP)
        xp("stat.agility", AGILITY_XP)
        xp("stat.magic", MAGIC_XP)
        extra("Access to the gnome gliders and the Grand Tree mine")
    },
    ItemRewardDisplay(DACONIA_ROCK),
) {
    val foundJournal = quest.attribute(name = "FOUND_JOURNAL", default = false)
    val kingToldOfCharlie = quest.attribute(name = "KING_TOLD_OF_CHARLIE", default = false)
    val shipyardAccess = quest.attribute(name = "SHIPYARD_ACCESS", default = false)
    val femiHelped = quest.attribute(name = "FEMI_HELPED", default = false)
    val femiSneakedIn = quest.attribute(name = "FEMI_SNEAKED_IN", default = false)
    val plansFound = quest.attribute(name = "PLANS_FOUND", default = false)
    val seenGloughScene = quest.attribute(name = "SEEN_GLOUGH_SCENE", default = false)

    /** The letter of the twigs lashed to each watchtower pillar, west to east; empty when bare. */
    val pillarT = quest.attribute(name = "PILLAR_T", default = "")
    val pillarU = quest.attribute(name = "PILLAR_U", default = "")
    val pillarZ = quest.attribute(name = "PILLAR_Z", default = "")
    val pillarO = quest.attribute(name = "PILLAR_O", default = "")

    /** Index into [GrandTreeRoots.ROOTS] of the root hiding the last Daconia rock. */
    val rockRoot = quest.attribute(name = "ROCK_ROOT", default = -1)

    override fun ScriptContext.init() {}

    fun stage(player: Player): Int = quest.getQuestStage(player)

    /** Moves the quest forward to [stage] if it is not already there or past it. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun clearPillars(player: Player) {
        pillarT.set(player, "")
        pillarU.set(player, "")
        pillarZ.set(player, "")
        pillarO.set(player, "")
    }

    fun hasAnyTwigs(player: Player): Boolean = TWIGS.any { player.inv.contains(it) }

    /** The journal DSL takes obj names without their `obj.` prefix. */
    private fun String.bare(): String = removePrefix("obj.")

    override fun subTitle(): String =
        "talking to <col=800000>King Narnode Shareen</col> on the ground floor of the " +
            "<col=800000>Grand Tree</col>, in the Tree Gnome Stronghold."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "The <red>Grand Tree</red> is dying and <red>King Narnode Shareen</red> has asked " +
                    "me to find out why.",
            ) {}

            objective(
                "I should take the <red>bark sample</red> to <red>Hazelmere</red>, on an island " +
                    "east of Yanille, and translate what he says with the <red>translation book</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
                hasItem(BARK_SAMPLE.bare(), "I have the bark sample.").strike()
                hasItem(TRANSLATION_BOOK.bare(), "I have the translation book.").strike()
            }

            objective(
                "Hazelmere wrote his answer on a <red>scroll</red>. I should take it back to " +
                    "<red>King Narnode</red> and translate it for him, word for word.",
            ) {
                visibleWhen { stage(access.player) == STAGE_HAS_SCROLL }
                hasItem(SCROLL.bare(), "I have Hazelmere's scroll.").strike()
            }

            objective(
                "Someone forged the King's seal and took the <red>Daconia rocks</red> from " +
                    "Hazelmere. I should warn the head tree guardian, <red>Glough</red>, in his " +
                    "tree house just south of the Grand Tree.",
            ) {
                visibleWhen { stage(access.player) == STAGE_TRANSLATED }
            }

            objective("I've warned Glough. I should report back to <red>King Narnode</red>.") {
                visibleWhen { stage(access.player) == STAGE_GLOUGH_WARNED }
            }

            objective(
                "Glough has caught a human carrying Daconia rocks. The prisoner, <red>Charlie</red>, " +
                    "is held at the very top of the Grand Tree. I should question him.",
            ) {
                visibleWhen { stage(access.player) == STAGE_PRISONER_REPORTED }
            }

            objective(
                "Charlie says <red>Glough</red> paid him to fetch the rocks. I should search " +
                    "Glough's home for proof and then confront him.",
            ) {
                visibleWhen { stage(access.player) == STAGE_CHARLIE_QUESTIONED }
                attribute(foundJournal, "I found Glough's journal in his cupboard.", finalise = false).preserveObjective().strike()
            }

            objective(
                "Glough had me thrown in the cage and has guards on the front gate. Charlie told " +
                    "me the <red>Karamja shipyard foreman</red> knows what Glough is up to; the " +
                    "password is <red>Ka-Lu-Min</red>. The King's <red>glider pilot</red> on the top " +
                    "floor will fly me out.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ESCAPE_BY_GLIDER }
            }

            objective(
                "The glider crashed on Karamja. The <red>shipyard</red> is on the coast to the " +
                    "east. I should find the <red>foreman</red> and find out what Glough ordered.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ON_KARAMJA }
            }

            objective(
                "The foreman gave me a <red>lumber order</red> for thirty battleships. I should " +
                    "get back into the <red>Tree Gnome Stronghold</red> and show the King. The " +
                    "guards won't let me through the gate; <red>Femi</red> might sneak me in.",
            ) {
                visibleWhen { stage(access.player) == STAGE_HAS_LUMBER_ORDER }
                hasItem(LUMBER_ORDER.bare(), "I have the lumber order.").strike()
            }

            objective(
                "The King still doesn't believe me. <red>Charlie</red> might know where to find " +
                    "more proof.",
            ) {
                visibleWhen { stage(access.player) == STAGE_KING_DOUBTS }
            }

            objective(
                "Glough left the key to his chest at his girlfriend <red>Anita</red>'s house, up " +
                    "the ladder by the tortoise pens in the north-west of the stronghold. I should " +
                    "open the <red>chest</red> in Glough's house and take what I find to the King.",
            ) {
                visibleWhen { stage(access.player) == STAGE_KEY_HINTED }
                hasItem(GLOUGHS_KEY.bare(), "I have Glough's key.").strike()
                attribute(plansFound, "I found Glough's invasion plans in his chest.", finalise = false).preserveObjective().strike()
            }

            objective(
                "The King gave me the odd <red>twigs</red> his guards found in Glough's house. " +
                    "They must fit the four pillars on Glough's <red>watchtower</red>. The " +
                    "translation book should tell me what word to spell.",
            ) {
                visibleWhen { stage(access.player) == STAGE_HAS_TWIGS }
            }

            objective(
                "The twigs opened a <red>trapdoor</red> on Glough's watchtower. I should climb " +
                    "down and see what he's hiding.",
            ) {
                visibleWhen { stage(access.player) == STAGE_TRAPDOOR_OPEN }
            }

            objective(
                "Glough set a <red>black demon</red> on me and fled. I should follow the passage " +
                    "and tell <red>King Narnode</red> everything.",
            ) {
                visibleWhen { stage(access.player) == STAGE_DEMON_SLAIN }
            }

            objective(
                "The guards found Glough, but the tree is still dying. There must be one more " +
                    "<red>Daconia rock</red> hidden in the <red>roots</red> beneath the Grand Tree.",
            ) {
                visibleWhen { stage(access.player) == STAGE_SEARCHING_ROOTS }
                hasItem(DACONIA_ROCK.bare(), "I found the Daconia rock. I should take it to the King.").strike()
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "King Narnode asked me to find out what was killing the Grand Tree. Hazelmere told " +
                    "me a man with the King's seal had taken his Daconia rocks, and Glough, the " +
                    "head tree guardian, locked up a human named Charlie for it.",
            )
            line(
                "Glough threw me in the cage too, so I fled to Karamja by glider. The shipyard " +
                    "foreman told me Glough had ordered thirty battleships, and Glough's chest held " +
                    "plans for invading the whole of Gielinor.",
            )
            line(
                "The twigs from Glough's house opened a trapdoor on his watchtower. Below it he " +
                    "set a black demon on me, but I killed it and the King's guards found Glough " +
                    "hiding under a hoard of Daconia rocks. I found the last rock in the roots and " +
                    "the tree began to recover.",
            )
            line("King Narnode has opened the stronghold to me: the gliders, the spirit tree and the mine under the tree.")
        }

    companion object {
        const val STAGE_STARTED = 10
        const val STAGE_HAS_SCROLL = 20
        const val STAGE_TRANSLATED = 30
        const val STAGE_GLOUGH_WARNED = 40
        const val STAGE_PRISONER_REPORTED = 50
        const val STAGE_CHARLIE_QUESTIONED = 60
        const val STAGE_ESCAPE_BY_GLIDER = 70
        const val STAGE_ON_KARAMJA = 80
        const val STAGE_HAS_LUMBER_ORDER = 90
        const val STAGE_KING_DOUBTS = 100
        const val STAGE_KEY_HINTED = 110
        const val STAGE_HAS_TWIGS = 120
        const val STAGE_TRAPDOOR_OPEN = 130
        const val STAGE_DEMON_SLAIN = 140
        const val STAGE_SEARCHING_ROOTS = 150
        const val STAGE_COMPLETE = 160

        const val ATTACK_XP = 18400.0
        const val AGILITY_XP = 7900.0
        const val MAGIC_XP = 2150.0
        const val RECOMMENDED_COMBAT = 50
        const val WATCHTOWER_AGILITY = 25
        const val FEMI_FEE = 1000

        const val BARK_SAMPLE = "obj.grandtree_barksample"
        const val TRANSLATION_BOOK = "obj.grandtree_translationbook"
        const val JOURNAL = "obj.grandtree_journal"
        const val SCROLL = "obj.grandtree_scroll"
        const val LUMBER_ORDER = "obj.grandtree_order"
        const val GLOUGHS_KEY = "obj.grandtree_gloughskey"
        const val TWIG_T = "obj.grandtree_twigt"
        const val TWIG_U = "obj.grandtree_twigu"
        const val TWIG_Z = "obj.grandtree_twigz"
        const val TWIG_O = "obj.grandtree_twigo"
        const val DACONIA_ROCK = "obj.grandtree_daconiarock"
        const val INVASION_PLANS = "obj.grandtree_invasionplans"
        const val COINS = "obj.coins"
        val TWIGS = listOf(TWIG_T, TWIG_U, TWIG_Z, TWIG_O)

        const val NARNODE = "npc.grandtree_narnode"
        const val NARNODE_HEAD = "npc.grandtree_narnode_1op"
        const val GLOUGH = "npc.grandtree_glough"
        const val GLOUGH_HEAD = "npc.grandtree_glough_visible"
        const val GLOUGH_BATTLE = "npc.grandtree_glough_battle"
        const val CHARLIE = "npc.grandtree_charlie_multi"
        const val CHARLIE_HEAD = "npc.grandtree_charlie"
        const val HAZELMERE = "npc.grandtree_hazelmere"
        const val ANITA = "npc.grandtree_anita"
        const val FEMI = "npc.grandtree_femi"
        const val GNOME_GUARD = "npc.grandtree_spawnedguard"
        const val BLACK_DEMON = "npc.grandtree_blackdemon"
        const val PILOT = "npc.pilot_grand_tree"
        const val FOREMAN = "npc.grandtree_foreman"
        const val SHIPYARD_GUARD = "npc.grandtree_shipyardguard"
        const val JOGRE = "npc.jogre"
    }
}
