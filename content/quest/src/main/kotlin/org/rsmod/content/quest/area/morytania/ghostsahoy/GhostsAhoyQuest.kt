package org.rsmod.content.quest.area.morytania.ghostsahoy

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Ghosts Ahoy.
 *
 * The stage is the cache varbit `varbit.ahoy_questvar` (endstate 8 from `dbrow.quest_ghostsahoy`),
 * which shares `varp.ahoy_varbits_1` with the quest's other cache varbits, so only the varbit is
 * ever written. Ak-Haranu's multinpc appears from [STAGE_GATHER] and the toll barriers turn into
 * their free post-quest form at [STAGE_COMPLETE], both driven by the client from the same varbit.
 *
 * Parallel objectives keep their own vars: the nettle tea, the model ship, Robin's debt, the
 * lobster, the bedsheet job, the temple door and the three items handed to the Old Crone use the
 * cache's `ahoy_*` varbits; the flag puzzle and the petition use server-only varbits on
 * `varp.ahoy_state`, and the last ghost asked to sign is `varp.ahoy_last_signer`. Quest items
 * themselves (map scraps, book, manual, robes, keys) are tracked by what the player holds, so a
 * lost item can be fetched again until it has been handed over.
 */
@Singleton
class GhostsAhoyQuest :
    QuestScript(
        QUEST_KEY,
        "varp.ahoy_varbits_1",
        rewards {
            xp("stat.prayer", REWARD_PRAYER_XP)
            item(ECTOPHIAL, label = "An Ectophial")
            extra("Free passage into Port Phasmatys")
        },
        ItemRewardDisplay(ECTOPHIAL, zoom = 250),
        completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
        questVarbit = "varbit.ahoy_questvar",
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::normalise)
    }

    override fun subTitle(): String =
        "talking to <col=800000>Velorina</col> in <col=800000>Port Phasmatys</col>, east of " +
            "<col=800000>Canifis</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            step(stage > STAGE_STARTED, "Velorina asked me to plead with Necrovarus to let the ghosts of Port Phasmatys pass on.")
            if (stage >= STAGE_REFUSED) {
                step(stage > STAGE_REFUSED, "Necrovarus refused to listen. I should tell Velorina.")
            }
            if (stage >= STAGE_FIND_CRONE) {
                step(stage > STAGE_FIND_CRONE, "Velorina told me of an old crone west of the farm, who once knew Necrovarus.")
            }
            if (stage == STAGE_FIND_CRONE && teaState(p) == TEA_ASKED) {
                line("The Old Crone can't remember a thing without a cup of milky nettle tea in her porcelain cup.")
            }
            if (stage >= STAGE_GATHER) {
                step(
                    stage > STAGE_GATHER,
                    "The Old Crone can enchant my ghostspeak amulet to command Necrovarus if I bring her the Book of " +
                        "Haricanto, a translation manual and Necrovarus's mystical robes.",
                )
            }
            if (stage == STAGE_GATHER) {
                gatherLines(p)
            }
            if (stage >= STAGE_ENCHANTED) {
                step(stage > STAGE_ENCHANTED, "My ghostspeak amulet is enchanted. I must command Necrovarus to let the ghosts go.")
            }
            if (stage >= STAGE_RELEASED) {
                line("Necrovarus has released the ghosts. I should tell Velorina the news.")
            }
        }

    private fun org.rsmod.content.quest.manager.QuestJournalBuilder.step(done: Boolean, text: String) {
        if (done) strike(text) else line(text)
    }

    private fun org.rsmod.content.quest.manager.QuestJournalBuilder.gatherLines(p: Player) {
        val book = givenBook(p) || p.owns(BOOK)
        val manual = givenManual(p) || p.owns(MANUAL)
        val robes = givenRobes(p) || p.owns(ROBES)
        when {
            book -> step(givenBook(p), "I have the Book of Haricanto.")
            p.owns(MAP) -> line("The treasure map leads from the Saradomin statue on Dragontooth Island.")
            toyBoat(p) == BOAT_RETURNED -> line("The Old Man's map is in three scraps somewhere aboard and around the wreck.")
            toyBoat(p) == BOAT_GIVEN -> line("The Old Crone's model ship might jog the memory of the Old Man on the wreck.")
            else -> line("The Old Crone might know something if I offer to help her.")
        }
        if (toyBoat(p) == BOAT_GIVEN) {
            for (part in FlagPart.entries) {
                if (isSeen(p, part)) {
                    line("The ${part.label} of the wreck's flag is ${target(p, part)?.label}.")
                }
            }
        }
        when {
            manual -> step(givenManual(p), "I have the translation manual.")
            bowState(p) == BOW_SIGNED -> line("Robin signed an oak longbow. Ak-Haranu will trade his manual for it.")
            bowState(p) >= BOW_ASKED ->
                line("Ak-Haranu wants an oak longbow signed by Robin. Robin owes me ${robinDebt(p)} coins.")
            else -> line("Ak-Haranu, the trader at the docks, may have a translation manual.")
        }
        when {
            robes -> step(givenRobes(p), "I have Necrovarus's mystical robes.")
            isTempleUnlocked(p) -> line("The robes are kept in a coffin behind the temple's upstairs door.")
            isPetitionPresented(p) -> line("Necrovarus dropped a bone key. It should open the temple's upstairs door.")
            isPetitionApproved(p) -> line("Gravingas approved the petition. I should present it to Necrovarus.")
            p.owns(PETITION) -> line("The petition has ${signatures(p)} of $SIGNATURES_NEEDED signatures.")
            else -> line("Gravingas, protesting in town, might help me get into Necrovarus's temple.")
        }
    }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I helped Velorina free the ghosts of Port Phasmatys. The Old Crone enchanted my ghostspeak " +
                    "amulet with the Book of Haricanto, a translation manual and Necrovarus's own robes.",
            )
            line("Commanded by the amulet, Necrovarus had to let any ghost who wished pass on.")
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = quest.isQuestInProgress(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        if (stage > stage(access.player)) {
            quest.setQuestStage(access, stage)
        }
    }

    /** The toll is waived once Necrovarus has been commanded, even before Velorina hears of it. */
    fun hasFreePassage(player: Player): Boolean = stage(player) >= STAGE_RELEASED

    fun teaState(player: Player): Int = player.teaVar

    fun setTeaState(player: Player, value: Int) {
        player.teaVar = value
    }

    fun toyBoat(player: Player): Int = player.toyBoatVar

    fun setToyBoat(player: Player, value: Int) {
        player.toyBoatVar = value
    }

    fun bowState(player: Player): Int = player.bowVar

    fun setBowState(player: Player, value: Int) {
        player.bowVar = value
    }

    fun robinDebt(player: Player): Int {
        val state = bowState(player)
        return if (state in BOW_ASKED..BOW_ASKED + MAX_DEBT_UNITS) (state - BOW_ASKED) * RUNEDRAW_STAKE else 0
    }

    fun signatures(player: Player): Int = player.signatureVar

    fun setSignatures(player: Player, value: Int) {
        player.signatureVar = value.coerceIn(0, SIGNATURES_NEEDED)
    }

    fun lastSigner(player: Player): Int = player.lastSignerVar

    fun setLastSigner(player: Player, value: Int) {
        player.lastSignerVar = value
    }

    fun isPetitionApproved(player: Player): Boolean = player.petitionApprovedVar == 1

    fun setPetitionApproved(player: Player, approved: Boolean) {
        player.petitionApprovedVar = if (approved) 1 else 0
    }

    fun isPetitionPresented(player: Player): Boolean = player.petitionPresentedVar == 1

    fun setPetitionPresented(player: Player) {
        player.petitionPresentedVar = 1
    }

    fun isLobsterKilled(player: Player): Boolean = player.lobsterVar == 1

    fun setLobsterKilled(player: Player) {
        player.lobsterVar = 1
    }

    fun isSheetRequested(player: Player): Boolean = player.sheetVar == 1

    fun setSheetRequested(player: Player) {
        player.sheetVar = 1
    }

    fun isTempleUnlocked(player: Player): Boolean = player.templeVar == 1

    fun setTempleUnlocked(player: Player) {
        player.templeVar = 1
    }

    fun isWindHigh(player: Player): Boolean = player.windVar == 1

    fun setWindHigh(player: Player, high: Boolean) {
        player.windVar = if (high) 1 else 0
    }

    fun givenBook(player: Player): Boolean = player.givenBookVar == 1

    fun givenManual(player: Player): Boolean = player.givenManualVar == 1

    fun givenRobes(player: Player): Boolean = player.givenRobesVar == 1

    fun setGiven(player: Player, obj: String) {
        when (obj) {
            BOOK -> player.givenBookVar = 1
            MANUAL -> player.givenManualVar = 1
            ROBES -> player.givenRobesVar = 1
        }
    }

    fun hasGivenAll(player: Player): Boolean = givenBook(player) && givenManual(player) && givenRobes(player)

    /** The flag's solution, rolled once per player the first time it is needed and kept after. */
    fun target(player: Player, part: FlagPart): FlagColour? = FlagColour.byId(player.vars[part.targetVarbit])

    fun ensureTargets(player: Player, roll: (Int) -> Int) {
        for (part in FlagPart.entries) {
            if (target(player, part) == null) {
                player.setVarbit(part.targetVarbit, FlagColour.entries[roll(FlagColour.entries.size)].id)
            }
        }
    }

    fun applied(player: Player, part: FlagPart): FlagColour? = FlagColour.byId(player.vars[part.appliedVarbit])

    fun setApplied(player: Player, part: FlagPart, colour: FlagColour?) {
        player.setVarbit(part.appliedVarbit, colour?.id ?: 0)
    }

    fun clearApplied(player: Player) {
        for (part in FlagPart.entries) setApplied(player, part, null)
    }

    fun isFlagCorrect(player: Player): Boolean =
        FlagPart.entries.all { target(player, it) != null && applied(player, it) == target(player, it) }

    fun isSeen(player: Player, part: FlagPart): Boolean = (player.flagSeenVar shr part.ordinal) and 1 == 1

    fun markSeen(player: Player, part: FlagPart) {
        player.flagSeenVar = player.flagSeenVar or (1 shl part.ordinal)
    }

    /**
     * Whether the player may take a map scrap: during the quest, before the book has been dug up,
     * and only if neither that scrap nor the finished map is already in hand or in the bank.
     */
    fun needsScrap(player: Player, scrap: String): Boolean =
        stage(player) == STAGE_GATHER &&
            !givenBook(player) &&
            !player.owns(BOOK) &&
            !player.owns(MAP) &&
            !player.owns(scrap)

    fun needsBook(player: Player): Boolean =
        stage(player) == STAGE_GATHER && !givenBook(player) && !player.owns(BOOK)

    fun needsRobes(player: Player): Boolean =
        stage(player) == STAGE_GATHER && !givenRobes(player) && !player.owns(ROBES)

    fun needsManual(player: Player): Boolean =
        stage(player) == STAGE_GATHER && !givenManual(player) && !player.owns(MANUAL)

    private fun Player.setVarbit(varbit: String, value: Int) {
        org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(this, varbit, value)
    }

    private fun normalise(player: Player) {
        if (stage(player) != 0) {
            return
        }
        for (part in FlagPart.entries) {
            player.setVarbit(part.targetVarbit, 0)
            player.setVarbit(part.appliedVarbit, 0)
        }
        player.flagSeenVar = 0
        player.petitionApprovedVar = 0
        player.petitionPresentedVar = 0
        player.lastSignerVar = 0
        player.teaVar = 0
        player.toyBoatVar = 0
        player.bowVar = 0
        player.signatureVar = 0
        player.lobsterVar = 0
        player.sheetVar = 0
        player.templeVar = 0
        player.windVar = 0
        player.givenBookVar = 0
        player.givenManualVar = 0
        player.givenRobesVar = 0
    }

    companion object {
        const val QUEST_KEY = "quest_ghostsahoy"
        const val PRIEST_IN_PERIL = "quest_priestinperil"
        const val RESTLESS_GHOST = "quest_restlessghost"

        const val STAGE_STARTED = 1
        const val STAGE_REFUSED = 2
        const val STAGE_FIND_CRONE = 3
        const val STAGE_GATHER = 4
        const val STAGE_ENCHANTED = 5
        const val STAGE_RELEASED = 6
        const val STAGE_COMPLETE = 8

        const val TEA_NONE = 0
        const val TEA_ASKED = 1
        const val TEA_DRUNK = 2

        const val BOAT_NONE = 0
        const val BOAT_GIVEN = 1
        const val BOAT_RETURNED = 2

        /** `varbit.ahoy_subquest_bow`: asked, then asked plus 25 coins of debt per unit, signed, traded. */
        const val BOW_NONE = 0
        const val BOW_ASKED = 1
        const val MAX_DEBT_UNITS = 4
        const val BOW_SIGNED = 6
        const val BOW_TRADED = 7

        const val RUNEDRAW_STAKE = 25
        const val SIGNATURES_NEEDED = 10
        const val TOLL = 2
        const val BOAT_FARE = 25
        const val BOAT_FARE_CHARMED = 10

        const val REWARD_PRAYER_XP = 2400.0

        const val COOKING_REQ = 20
        const val AGILITY_REQ = 25

        const val VELORINA = "npc.ahoy_velorina"
        const val NECROVARUS = "npc.ahoy_necrovarus"
        const val GRAVINGAS = "npc.ahoy_ghost_protestor"
        const val AK_HARANU = "npc.ahoy_akharanu"
        const val ROBIN = "npc.ahoy_robin"
        const val CRONE = "npc.ahoy_crone"
        const val OLD_MAN = "npc.ahoy_oldman"
        const val VILLAGER = "npc.ahoy_ghost_villager"
        const val INNKEEPER = "npc.ahoy_ghost_innkeeper"
        const val CAPTAIN = "npc.ahoy_ghost_captain_1"
        const val GIANT_LOBSTER = "npc.giant_lobster"

        const val GHOSTSPEAK = "obj.amulet_of_ghostspeak"
        const val GHOSTSPEAK_ENCHANTED = "obj.amulet_of_ghostspeak_enchanted"
        const val ECTOPHIAL = "obj.ectophial"
        const val ECTOPHIAL_EMPTY = "obj.ectophial_empty"
        const val ECTOTOKEN = "obj.ectotoken"
        const val COINS = "obj.coins"
        const val ROBES = "obj.ahoy_robes_of_necrovarus"
        const val BOOK = "obj.ahoy_book_of_haricanto"
        const val MANUAL = "obj.ahoy_translation_manual"
        const val TOY_BOAT = "obj.ahoy_toy_boat"
        const val TOY_BOAT_REPAIRED = "obj.ahoy_toy_boat_repaired"
        const val BONE_KEY = "obj.ahoy_bone_key"
        const val CHEST_KEY = "obj.ahoy_chest_key"
        const val SCRAP_1 = "obj.ahoy_map_scrap_1"
        const val SCRAP_2 = "obj.ahoy_map_scrap_2"
        const val SCRAP_3 = "obj.ahoy_map_scrap_3"
        const val MAP = "obj.ahoy_map_complete"
        const val PETITION = "obj.ahoy_petition"
        const val BEDSHEET = "obj.ahoy_bedsheet"
        const val BEDSHEET_SLIMED = "obj.ahoy_bedsheetgreen"
        const val BUCKET_OF_SLIME = "obj.bucket_ectoplasm"
        const val BUCKET = "obj.bucket_empty"
        const val OAK_LONGBOW = "obj.oak_longbow"
        const val SIGNED_OAK_LONGBOW = "obj.oak_longbow_signed"
        const val CHARMED_RING = "obj.ring_of_charos_unlocked"
        const val SPADE = "obj.spade"

        const val PORCELAIN_CUP = "obj.chinacup_empty"
        const val CUP_OF_TEA = "obj.chinacup_of_nettletea"
        const val CUP_OF_MILKY_TEA = "obj.chinacup_of_nettletea_milky"
        const val BOWL_OF_TEA = "obj.bowl_nettletea"
        const val BOWL_OF_MILKY_TEA = "obj.bowl_nettletea_milky"
        const val BOWL_OF_NETTLE_WATER = "obj.bowl_nettlewater"
        const val BOWL_OF_WATER = "obj.bowl_water"
        const val BOWL = "obj.bowl_empty"
        const val NETTLES = "obj.nettles_picked"
        const val BUCKET_OF_MILK = "obj.bucket_milk"

        const val SILK = "obj.silk"
        const val NEEDLE = "obj.needle"
        const val THREAD = "obj.thread"
        const val KNIFE = "obj.knife"
    }
}

/** The three parts of the wreck's flag, as the mast describes them and the dyes colour them. */
enum class FlagPart(val label: String, val targetVarbit: String, val appliedVarbit: String) {
    TOP("top half", "varbit.ahoy_flag_top_target", "varbit.ahoy_flag_top"),
    BOTTOM("bottom half", "varbit.ahoy_flag_bottom_target", "varbit.ahoy_flag_bottom"),
    SKULL("skull emblem", "varbit.ahoy_flag_skull_target", "varbit.ahoy_flag_skull"),
}

enum class FlagColour(val id: Int, val label: String, val dye: String) {
    RED(1, "red", "obj.reddye"),
    YELLOW(2, "yellow", "obj.yellowdye"),
    BLUE(3, "blue", "obj.bluedye"),
    ORANGE(4, "orange", "obj.orangedye"),
    GREEN(5, "green", "obj.greendye"),
    PURPLE(6, "purple", "obj.purpledye");

    companion object {
        fun byId(id: Int): FlagColour? = entries.firstOrNull { it.id == id }

        fun byDye(dye: String): FlagColour? = entries.firstOrNull { it.dye == dye }
    }
}

fun Player.owns(obj: String): Boolean =
    inv.contains(obj) || worn.contains(obj) || invMap["inv.bank"]?.contains(obj) == true

fun Player.hasGhostspeak(): Boolean =
    worn.contains(GhostsAhoyQuest.GHOSTSPEAK) || worn.contains(GhostsAhoyQuest.GHOSTSPEAK_ENCHANTED)

fun Player.isDisguised(): Boolean = worn.contains(GhostsAhoyQuest.BEDSHEET_SLIMED)

private var Player.teaVar: Int by intVarBit("varbit.ahoy_subquest_nettletea")
private var Player.toyBoatVar: Int by intVarBit("varbit.ahoy_subquest_toyboat")
private var Player.bowVar: Int by intVarBit("varbit.ahoy_subquest_bow")
private var Player.signatureVar: Int by intVarBit("varbit.ahoy_signaturecounter")
private var Player.lobsterVar: Int by intVarBit("varbit.ahoy_killed_lobster")
private var Player.sheetVar: Int by intVarBit("varbit.ahoy_requested_sheet")
private var Player.templeVar: Int by intVarBit("varbit.ahoy_templedoor_unlocked")
private var Player.windVar: Int by intVarBit("varbit.ahoy_windspeed")
private var Player.givenBookVar: Int by intVarBit("varbit.ahoy_given_book")
private var Player.givenManualVar: Int by intVarBit("varbit.ahoy_given_manual")
private var Player.givenRobesVar: Int by intVarBit("varbit.ahoy_given_robes")
private var Player.flagSeenVar: Int by intVarBit("varbit.ahoy_flag_seen")
private var Player.petitionApprovedVar: Int by intVarBit("varbit.ahoy_petition_approved")
private var Player.petitionPresentedVar: Int by intVarBit("varbit.ahoy_petition_presented")
private var Player.lastSignerVar: Int by intVarp("varp.ahoy_last_signer")
