package org.rsmod.content.quest.area.paterdomus.priestinperil

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Priest in Peril.
 *
 * The stage lives in `varp.priestperil` (302), which carries no varbits, and follows the client:
 * the Temple Guardian (a multinpc on the varp) is shown for stages 0-2, Drezel in his cell for
 * 4-8 and Drezel in the mausoleum from 8. Every essence handed over adds one to the stage, from
 * [STAGE_ESSENCE] up to the endstate [STAGE_COMPLETE] once all fifty are given. The client's
 * stage 61 (Drezel's blessing) would overflow the endstate, so the blessing is [blessed].
 *
 * The finer dialogue flags are the cache's own varbits on `varp.vampire_secondary`, a varp the
 * quest manager never writes, so they are used directly.
 */
@Singleton
class PriestInPerilQuest : QuestScript(
    "quest_priestinperil",
    "varp.priestperil",
    rewards {
        xp("stat.prayer", PRAYER_XP)
        item(WOLFBANE)
    },
    ItemRewardDisplay(WOLFBANE),
) {
    /** Set once Drezel has blessed the player, letting them through the holy barrier. */
    val blessed = quest.attribute(name = "BLESSED", default = false)

    /** Set once the player has swapped the golden key at the monument and opened the west gate. */
    val westGateUnlocked = quest.attribute(name = "WEST_GATE_UNLOCKED", default = false)

    /** Set once Drezel has blessed murky water for the first time. */
    val waterBlessedBefore = quest.attribute(name = "WATER_BLESSED_BEFORE", default = false)

    /** One bit per monument whose golden gift has been swapped away; see [Mausoleum]. */
    val monumentSwaps = quest.attribute(name = "MONUMENT_SWAPS", default = 0)

    override fun ScriptContext.init() {
        onPlayerLogin { syncHoodedMonk(player) }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun essenceGiven(player: Player): Int = (stage(player) - STAGE_ESSENCE).coerceIn(0, ESSENCE_NEEDED)

    /**
     * The hooded monk stays dead for a player who holds the key he dropped, and comes back if they
     * lose it before Drezel's cell is open.
     */
    fun syncHoodedMonk(player: Player) {
        if (stage(player) !in STAGE_ROALD_FURIOUS..STAGE_MET_DREZEL) {
            return
        }
        player.hoodedMonkDead = player.holdsAnywhere(GOLDEN_KEY) || player.holdsAnywhere(IRON_KEY)
    }

    override fun subTitle(): String =
        "speaking to <col=800000>King Roald</col> in <col=800000>Varrock Palace</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>King Roald</red> has not heard from <red>Drezel</red>, the priest of the " +
                    "temple of <red>Paterdomus</red> east of Varrock, for some days. He wants me " +
                    "to check on him.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
            }

            objective(
                "Someone behind the temple door claiming to be Drezel asked me to get rid of a " +
                    "big dog-like thing in the <red>mausoleum</red> north of the temple. Something " +
                    "about this seems suspicious.",
            ) {
                visibleWhen { stage(access.player) == STAGE_AGREED_TO_KILL_DOG }
            }

            objective(
                "I killed the dog in the mausoleum. I should let <red>King Roald</red> know.",
            ) {
                visibleWhen { stage(access.player) == STAGE_KILLED_DOG }
            }

            objective(
                "King Roald was furious: the dog was the mausoleum's guardian, and someone has " +
                    "clearly done something to Drezel. I must return to <red>Paterdomus</red> and " +
                    "set things right.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ROALD_FURIOUS }
            }

            objective(
                "Zamorakian monks have locked Drezel in a cell on the top floor of the temple. " +
                    "I need the key to his cell, which their <red>hooded leader</red> may carry, " +
                    "and a way to deal with the <red>vampyre</red> in the coffin beside it.",
            ) {
                visibleWhen { stage(access.player) == STAGE_MET_DREZEL }
                hasItem(GOLDEN_KEY.removePrefix(OBJ_PREFIX), "I took a golden key from the hooded monk, but it does not fit the cell.").strike()
                hasItem(IRON_KEY.removePrefix(OBJ_PREFIX), "I found an iron key on a monument in the mausoleum.").strike()
                hasItem(MURKY_WATER.removePrefix(OBJ_PREFIX), "I have some water from the River Salve.").strike()
            }

            objective(
                "Drezel's cell is open. He can bless water from the <red>well</red> in the " +
                    "mausoleum, which I should pour over the <red>coffin</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_CELL_UNLOCKED }
                hasItem(BLESSED_WATER.removePrefix(OBJ_PREFIX), "Drezel has blessed my water.").strike()
            }

            objective(
                "The vampyre is sealed in its coffin. I should speak to <red>Drezel</red> again.",
            ) {
                visibleWhen { stage(access.player) == STAGE_COFFIN_SEALED }
            }

            objective(
                "Drezel asked me to meet him in the <red>mausoleum</red>, through the north-east " +
                    "gate of the monument room.",
            ) {
                visibleWhen { stage(access.player) == STAGE_MEET_IN_MAUSOLEUM }
            }

            objective(
                "The Zamorakians polluted the River Salve. Drezel needs <red>50 unnoted rune or " +
                    "pure essence</red> to soak up the evil magic.",
            ) {
                visibleWhen { stage(access.player) in STAGE_ESSENCE until STAGE_COMPLETE }
                custom(true, "He still needs ${ESSENCE_NEEDED - essenceGiven(access.player)} more.")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "King Roald sent me to check on Drezel, the priest of Paterdomus. Zamorakian monks " +
                    "had imprisoned him and tricked me into killing the mausoleum's guardian.",
            )
            line(
                "I took a key from their hooded leader, swapped it for the cell key on a monument, " +
                    "and sealed a vampyre in its coffin with water from the Salve that Drezel " +
                    "blessed.",
            )
            line(
                "With fifty essence Drezel soaked up the evil magic poisoning the river. He gave me " +
                    "his family's dagger, Wolfbane, and the way into Morytania is open to me.",
            )
        }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_AGREED_TO_KILL_DOG = 2
        const val STAGE_KILLED_DOG = 3
        const val STAGE_ROALD_FURIOUS = 4
        const val STAGE_MET_DREZEL = 5
        const val STAGE_CELL_UNLOCKED = 6
        const val STAGE_COFFIN_SEALED = 7
        const val STAGE_MEET_IN_MAUSOLEUM = 8
        const val STAGE_ESSENCE = 10
        const val STAGE_COMPLETE = 60

        const val ESSENCE_NEEDED = STAGE_COMPLETE - STAGE_ESSENCE
        const val PRAYER_XP = 1406.0
        const val RECOMMENDED_COMBAT = 15

        const val OBJ_PREFIX = "obj."
        const val WOLFBANE = "obj.dagger_wolfbane"
        const val GOLDEN_KEY = "obj.pipkey_gold"
        const val IRON_KEY = "obj.pipkey_iron"
        const val BUCKET = "obj.bucket_empty"
        const val BUCKET_OF_WATER = "obj.bucket_water"
        const val MURKY_WATER = "obj.bucket_murkywater"
        const val BLESSED_WATER = "obj.bucket_blessedwater"
        const val RUNE_ESSENCE = "obj.blankrune"
        const val PURE_ESSENCE = "obj.blankrune_high"
        const val NOTED_RUNE_ESSENCE = "obj.cert_blankrune"
        const val NOTED_PURE_ESSENCE = "obj.cert_blankrune_high"

        const val KING_ROALD = "npc.king_roald"
        const val DREZEL_CELL = "npc.priestperiltrappedmonk"
        const val DREZEL_MAUSOLEUM = "npc.priestperiltrappedmonk2"
        const val TEMPLE_GUARDIAN = "npc.priestperilguarddog"
        const val HOODED_MONK = "npc.priestperilevilmonk3"
    }
}

var Player.returnedToRoald by boolVarBit("varbit.priestperil_returned_to_roald")
var Player.returnedToFakeDrezel by boolVarBit("varbit.priestperil_returned_to_fake_drezel")
var Player.hoodedMonkDead by boolVarBit("varbit.priestperilevilmonk_dead")
var Player.triedFakeKey by boolVarBit("varbit.priestperil_tried_fake_key")

internal fun Player.holdsAnywhere(obj: String): Boolean =
    inv.contains(obj) || worn.contains(obj) || invMap.getOrPut("inv.bank").contains(obj)
