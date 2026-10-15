package org.rsmod.content.quest.area.ardougne.regicide

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

internal var Player.readBook by intVarBit("varbit.regicide_read_book")
internal var Player.chemistChat by intVarBit("varbit.regicide_chemist_chat")
internal var Player.elenaChat by intVarBit("varbit.regicide_elena_chat")
internal var Player.elenaPostQuest by intVarBit("varbit.regicide_elena_postquest")
internal var Player.downWell by intVarBit("varbit.regicide_down_well")
internal var Player.seenGuard by intVarBit("varbit.regicide_seen_guard")
internal var Player.givenRabbit by intVarBit("varbit.regicide_given_rabbit")
internal var Player.tarInStill by intVarBit("varbit.regicide_had_tar")
internal var Player.catapultChat by intVarBit("varbit.regicide_catapult_chat")
internal var Player.readMessage by intVarBit("varbit.regicide_read_message")
internal var Player.messengerSeen by intVarBit("varbit.regicide_messenger_seen")
internal var Player.trackerReport by intVarBit("varbit.regicide_tracker_report")
internal var Player.stillTotal by intVarp("varp.regicide_still_total")
internal var Player.stillSettings by intVarp("varp.regicide_still_settings")
internal var Player.sceneReturn by intVarp("varp.regicide_scene_return")

/**
 * Regicide.
 *
 * The stage is the whole of the cache varp `varp.regicide_quest` (endstate 15 from
 * `dbrow.quest_regicide`). Three of its values are fixed by the cache, because multis index the
 * varp directly: the old camp's footprints only offer "Follow" at [STAGE_TRACKER_HELPING] to
 * [STAGE_DENSE_FOREST] and vanish after, and the Elf Tracker leaves the old camp from
 * [STAGE_LETTER] on.
 *
 * The sub-objectives use the cache's `regicide_*` varbits on `varp.regicide_bits`: the book read,
 * each ingredient Lord Iorwerth was asked about, the Chemist and Elena spoken to, the tar loaded
 * into the still, the guard seen and the catapult guard given his rabbit (which also hides him,
 * through his multinpc). The still keeps its progress in `varp.regicide_still_total` and draws
 * its gauges and valves from `varp.regicide_still_settings`. What the cache has no var for (the
 * letter read, the messenger seen, the camp reported) lives on the server-only
 * `varp.regicide_state`, and `varp.regicide_scene_return` holds the world tile a private quest
 * scene sends the player back to if they log out inside it.
 */
@Singleton
class RegicideQuest :
    QuestScript(
        QUEST_KEY,
        "varp.regicide_quest",
        rewards {
            xp("stat.agility", AGILITY_XP_REWARD)
            item(COINS, COIN_REWARD, label = "15,000 Coins")
            extra("Access to Arandar and Port Tyras")
        },
        ItemRewardDisplay(BARREL_BOMB, zoom = 250),
        completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    /** Moves the stage forward to [stage]; never back, so a replayed step cannot undo progress. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    /**
     * Whether the quest can be accepted: Underground Pass done and an unboosted Crafting level of
     * [CRAFTING_REQ]. The Agility level is only asked for at the dense forest.
     */
    fun canStart(access: ProtectedAccess): Boolean =
        QuestRequirements.hasCompleted(access.player, UNDERGROUND_PASS) &&
            access.statBase("stat.crafting") >= CRAFTING_REQ

    /**
     * The dense forest needs the tracker's lesson first; after the quest it is common knowledge,
     * as it is for a player the quest requirement policy counts as done without starting it.
     */
    fun knowsDenseForest(player: Player): Boolean =
        stage(player) >= STAGE_DENSE_FOREST ||
            (stage(player) == 0 && QuestRequirements.hasCompleted(player, QUEST_KEY))

    /** Lord Iorwerth opens the pass through Arandar when he hands over his letter. */
    fun arandarUnlocked(player: Player): Boolean = stage(player) >= STAGE_LETTER

    /** The ingredients Lord Iorwerth can be asked about, each with its cache varbit. */
    enum class Ingredient(val varbit: String) {
        QUICKLIME("varbit.regicide_quicklime_chat"),
        SULPHUR("varbit.regicide_sulphur_chat"),
        NAPHTHA("varbit.regicide_naphtha_chat"),
        BARREL("varbit.regicide_barrel_chat"),
        FUSE("varbit.regicide_fuse_chat"),
    }

    fun askedAbout(player: Player, ingredient: Ingredient): Boolean = player.vars[ingredient.varbit] == 1

    fun markAsked(player: Player, ingredient: Ingredient) = setVarBit(player, ingredient.varbit, 1)

    /** Clears the quest's own flags when it is reset to "not started". */
    fun syncVars(player: Player) {
        if (stage(player) != 0) {
            return
        }
        for (varbit in OWN_VARBITS + Ingredient.entries.map { it.varbit }) {
            setVarBit(player, varbit, 0)
        }
        player.stillTotal = 0
        player.stillSettings = 0
    }

    override fun subTitle(): String =
        "talking to <col=800000>King Lathas</col> in <col=800000>Ardougne Castle</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            step(
                stage > STAGE_STARTED,
                "King Lathas wants me to go through the Underground Pass and the Well of Voyage to " +
                    "help Lord Iorwerth's elves find and kill his brother, King Tyras.",
            )
            if (stage >= STAGE_MET_ELVES) {
                step(stage > STAGE_MET_ELVES, "Two elves told me to find Lord Iorwerth's camp in the north west of the forest.")
            }
            if (stage >= STAGE_MET_IORWERTH) {
                step(stage > STAGE_TRACKER_REFUSED, "Lord Iorwerth sent me to his tracker at Tyras's old camp, north of the poisoned lake.")
            }
            if (stage == STAGE_TRACKER_REFUSED) {
                line("The tracker wants proof that Lord Iorwerth sent me.")
            }
            if (stage >= STAGE_GOT_PENDANT) {
                step(stage > STAGE_GOT_PENDANT, "Lord Iorwerth gave me his crystal pendant to show the tracker.")
            }
            if (stage >= STAGE_TRACKER_HELPING) {
                step(stage > STAGE_TRACKER_HELPING, "The tracker asked me to search the west end of the old camp.")
            }
            if (stage >= STAGE_FOUND_TRACKS) {
                step(stage > STAGE_FOUND_TRACKS, "I found tracks leading west, but they disappear into dense forest.")
            }
            if (stage >= STAGE_DENSE_FOREST) {
                step(stage > STAGE_DENSE_FOREST, "The tracker showed me how to find a way through the dense forest.")
            }
            if (stage >= STAGE_GUARD_KILLED) {
                step(stage > STAGE_GUARD_KILLED, "One of Tyras's guards attacked me in the forest. Tyras's new camp must be close.")
            }
            if (stage >= STAGE_FOUND_CAMP) {
                step(stage > STAGE_FOUND_CAMP, "I found Tyras Camp, but General Hining won't let anyone near the king. I should tell Lord Iorwerth.")
            }
            if (stage >= STAGE_MAKE_BOMB) {
                step(stage > STAGE_MAKE_BOMB, "Lord Iorwerth gave me a book describing a bomb that should deal with Tyras.")
            }
            if (stage == STAGE_MAKE_BOMB) {
                bombLines(access)
            }
            if (stage >= STAGE_TYRAS_DEAD) {
                step(stage > STAGE_TYRAS_DEAD, "The catapult threw my barrel bomb into Tyras's tent. I should tell Lord Iorwerth.")
            }
            if (stage >= STAGE_LETTER) {
                step(stage > STAGE_LETTER_UNSEALED, "Lord Iorwerth gave me a sealed letter for King Lathas as proof.")
            }
            if (stage >= STAGE_LETTER_UNSEALED) {
                line("The letter has been unsealed. I should give it to King Lathas and act as if nothing has happened.")
            }
        }

    private fun QuestJournalBuilder.step(done: Boolean, text: String) {
        if (done) strike(text) else line(text)
    }

    private fun QuestJournalBuilder.bombLines(access: ProtectedAccess) {
        val p = access.player
        if (p.readBook == 0) {
            line("I should read the book and ask Lord Iorwerth about anything I need.")
            return
        }
        line("I need a barrel of naphtha, a pot of quicklime, some ground sulphur, a fuse and a tinderbox.")
        if (p.chemistChat == 1) {
            line("The Chemist in Rimmington has let me use his fractionalising still on coal tar.")
        }
        if (access.owns(BARREL_BOMB)) {
            line("My barrel bomb is ready. Tyras and his men use catapults against the elves.")
        }
    }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line("King Lathas sent me west through the Well of Voyage to help Lord Iorwerth be rid of King Tyras.")
            line("With the help of Iorwerth's tracker I found Tyras's camp, and turned his own catapult on his tent with a barrel bomb.")
            line(
                "On the way back Arianwyn, leader of the elven resistance, broke the letter's seal: Lathas and " +
                    "Iorwerth are working together to bring the Dark Lord into this world. I gave the letter to " +
                    "King Lathas all the same, as if I knew nothing.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_regicide"
        const val UNDERGROUND_PASS = "quest_undergroundpass"

        const val STAGE_STARTED = 1
        const val STAGE_MET_ELVES = 2
        const val STAGE_MET_IORWERTH = 3
        const val STAGE_TRACKER_REFUSED = 4
        const val STAGE_GOT_PENDANT = 5

        /** Fixed by `loc.regicide_old_camp_footprints`, which offers "Follow" from here to [STAGE_DENSE_FOREST]. */
        const val STAGE_TRACKER_HELPING = 6
        const val STAGE_FOUND_TRACKS = 7
        const val STAGE_DENSE_FOREST = 8
        const val STAGE_GUARD_KILLED = 9
        const val STAGE_FOUND_CAMP = 10
        const val STAGE_MAKE_BOMB = 11
        const val STAGE_TYRAS_DEAD = 12

        /** Fixed by `npc.regicide_old_camp_tracker`, which has no transform from this value on. */
        const val STAGE_LETTER = 13
        const val STAGE_LETTER_UNSEALED = 14
        const val STAGE_COMPLETE = 15

        const val CRAFTING_REQ = 10
        const val AGILITY_REQ = 56
        const val AGILITY_XP_REWARD = 13_750.0
        const val COIN_REWARD = 15_000

        /* Npcs. A multinpc's ops arrive on the type placed on the map, so hooks name both forms. */
        const val KING_LATHAS = "npc.kinglathas"
        const val MESSENGER = "npc.regicide_kings_messenger"
        const val IDRIS = "npc.regicide_good_elf1"
        const val ESSYLLT = "npc.regicide_evil_elf1"
        const val MORVRAN = "npc.regicide_evil_elf2"
        const val IORWERTH = "npc.lord_iorwerth"
        const val IORWERTH_VIS = "npc.lord_iorwerth_vis"
        const val TRACKER = "npc.regicide_old_camp_tracker"
        const val TRACKER_VIS = "npc.regicide_old_camp_tracker_vis"
        const val ENCOUNTER_GUARD = "npc.regicide_old_camp_guard"
        const val CAMP_GUARD = "npc.regicide_tyras_camp_guard"
        const val CAMP_GUARD_2 = "npc.regicide_tyras_guard"
        const val TENT_GUARD = "npc.regicide_tyras_camp_tent_guard"
        const val CATAPULT_GUARD = "npc.regicide_tyras_lazy_guard"
        const val CATAPULT_GUARD_VIS = "npc.regicide_tyras_lazy_guard_vis"
        const val HINING = "npc.regicide_general_hining"
        const val ARIANWYN = "npc.regicide_good_elf3"
        const val CHEMIST = "npc.chemist"

        /* Objs. */
        const val KINGS_MESSAGE = "obj.regicide_quest_kings_summons"
        const val IORWERTH_MESSAGE = "obj.regicide_iorwerth_message"
        const val PENDANT = "obj.regicide_crystal_pendant"
        const val BOOK = "obj.regicide_alchemy"
        const val SULPHUR = "obj.regicide_sulphar"
        const val GROUND_SULPHUR = "obj.regicide_sulphar_dust"
        const val LIMESTONE = "obj.limestone"
        const val QUICKLIME = "obj.regicide_quicklime"
        const val POT_OF_QUICKLIME = "obj.regicide_quicklime_dust"
        const val BARREL = "obj.regicide_barrel_empty"
        const val BARREL_OF_COAL_TAR = "obj.regicide_barrel_tar"
        const val BARREL_OF_NAPHTHA = "obj.regicide_barrel_naphtha"
        const val NAPHTHA_SULPHUR_MIX = "obj.regicide_barrel_naphtha_sulphar_mix"
        const val NAPHTHA_QUICKLIME_MIX = "obj.regicide_barrel_naphtha_quicklime_mix"
        const val UNFUSED_BOMB = "obj.regicide_barrel_lid"
        const val BARREL_BOMB = "obj.regicide_barrel_lid_fused"
        const val STRIP_OF_CLOTH = "obj.regicide_cloth"
        const val COOKED_RABBIT = "obj.cooked_rabbit"
        const val ROAST_RABBIT = "obj.spit_roasted_rabbit_meat"
        const val PESTLE_AND_MORTAR = "obj.pestle_and_mortar"
        const val POT = "obj.pot_empty"
        const val COAL = "obj.coal"
        const val TINDERBOX = "obj.tinderbox"
        const val COINS = "obj.coins"

        val RABBITS = listOf(COOKED_RABBIT, ROAST_RABBIT)

        /** The cache varbits on `varp.regicide_bits` and `varp.regicide_state` a reset clears. */
        val OWN_VARBITS =
            listOf(
                "varbit.regicide_koftik_food",
                "varbit.regicide_down_well",
                "varbit.regicide_seen_guard",
                "varbit.regicide_given_rabbit",
                "varbit.regicide_had_tar",
                "varbit.regicide_chemist_chat",
                "varbit.regicide_read_book",
                "varbit.regicide_elena_chat",
                "varbit.regicide_catapult_chat",
                "varbit.regicide_elena_postquest",
                "varbit.regicide_read_message",
                "varbit.regicide_messenger_seen",
                "varbit.regicide_tracker_report",
            )

        fun setVarBit(player: Player, varbit: String, value: Int) {
            if (player.vars[varbit] != value) {
                VarPlayerIntMapSetter.set(player, varbit, value)
            }
        }
    }
}

internal fun ProtectedAccess.owns(obj: String): Boolean = inv.contains(obj) || worn.contains(obj) || bank.contains(obj)
