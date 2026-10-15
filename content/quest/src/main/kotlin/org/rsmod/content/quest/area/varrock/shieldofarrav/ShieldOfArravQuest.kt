package org.rsmod.content.quest.area.varrock.shieldofarrav

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Shield of Arrav.
 *
 * The quest list reads `varbit.shieldofarrav` (varp 3403, shared with Scorpion Catcher), endstate
 * 2 from `dbrow.quest_shieldofarrav`: 1 while the shield is being hunted, 2 once King Roald has
 * paid out. The real progress lives in two whole cache varps, one per gang:
 * - `varp.phoenixgang`: the shared opening (Reldo's quest [PHOENIX_STARTED], the book read
 *   [PHOENIX_READ_BOOK], Reldo's pointers [PHOENIX_TOLD_BY_RELDO]) and then the Phoenix route:
 *   Baraek's directions [PHOENIX_TOLD_BY_BARAEK], Straven's task [PHOENIX_TASKED], membership
 *   [PHOENIX_JOINED] and [PHOENIX_COMPLETE]. Jonny the Beard's multinpc pins [PHOENIX_TASKED] (the
 *   only value that makes him attackable) and hides him from [PHOENIX_JOINED] on.
 * - `varp.blackarmgang`: Charlie's hint [BLACKARM_TOLD_BY_CHARLIE], Katrine's crossbow task
 *   [BLACKARM_TASKED], membership [BLACKARM_JOINED] and [BLACKARM_COMPLETE].
 *
 * The quest needs two players in opposite gangs: each brings their gang's half of the shield to
 * the curator for two half-certificates, and they swap one to make a whole certificate each.
 */
@Singleton
class ShieldOfArravQuest : QuestScript(
    QUEST_KEY,
    "varp.scorpcatcher_secondary",
    rewards { item(COINS, REWARD_COINS, label = "600 Coins") },
    ItemRewardDisplay(CROSSBOW, zoom = 130),
    questVarbit = "varbit.shieldofarrav",
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun isStarted(player: Player): Boolean = quest.getQuestStage(player) >= STAGE_STARTED

    fun isInProgress(player: Player): Boolean = quest.isQuestInProgress(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun isPhoenix(player: Player): Boolean = player.phoenixGang >= PHOENIX_JOINED

    fun isBlackArm(player: Player): Boolean = player.blackArmGang >= BLACKARM_JOINED

    fun isGangMember(player: Player): Boolean = isPhoenix(player) || isBlackArm(player)

    /** The half of the shield the player's own gang keeps, or null before they join one. */
    fun gangShield(player: Player): String? =
        when {
            isPhoenix(player) -> PHOENIX_SHIELD
            isBlackArm(player) -> BLACKARM_SHIELD
            else -> null
        }

    fun gangCertificate(player: Player): String? =
        when {
            isPhoenix(player) -> PHOENIX_CERTIFICATE
            isBlackArm(player) -> BLACKARM_CERTIFICATE
            else -> null
        }

    fun start(access: ProtectedAccess) {
        if (isStarted(access.player)) {
            return
        }
        quest.setQuestStage(access, STAGE_STARTED)
        access.player.phoenixGang = PHOENIX_STARTED
    }

    /** King Roald's payout: the gang that got the shield is marked done and the scroll shows. */
    fun complete(access: ProtectedAccess) {
        val player = access.player
        if (isPhoenix(player)) {
            player.phoenixGang = PHOENIX_COMPLETE
        } else {
            player.blackArmGang = BLACKARM_COMPLETE
        }
        quest.completeQuest(access)
    }

    fun owns(access: ProtectedAccess, obj: String): Boolean =
        access.inv.count(obj) > 0 || access.bank.count(obj) > 0

    /** A reset leaves nothing behind: both gang varps and every quest varbit go back to zero. */
    fun syncVars(player: Player) {
        if (isStarted(player)) {
            return
        }
        if (player.phoenixGang != 0) player.phoenixGang = 0
        if (player.blackArmGang != 0) player.blackArmGang = 0
        if (player.charlieMet) player.charlieMet = false
        if (player.charliePaid) player.charliePaid = false
        if (player.charlieHalfPaid) player.charlieHalfPaid = false
        if (player.weaponsmasterDead) player.weaponsmasterDead = false
        if (player.shieldGiven != 0) player.shieldGiven = 0
        if (player.stravenTrampChat) player.stravenTrampChat = false
        if (player.stravenShieldChat) player.stravenShieldChat = false
        if (player.phoenixSource) player.phoenixSource = false
    }

    override fun subTitle(): String =
        "talking to <col=800000>Reldo</col> in the <col=800000>Varrock Palace library</col>. " +
            "You will need a friend in the opposite gang to help you complete this quest."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val p = player.player
            objective(
                "<red>Reldo</red>, the palace librarian, told me a book called <red>The Shield of " +
                    "Arrav</red> in the library might have a quest in it.",
            ) {
                custom(p.phoenixGang >= PHOENIX_READ_BOOK, "I found the book and read it.").strike()
            }
            objective(
                "The shield was stolen from the museum by the <red>Phoenix Gang</red>, and a " +
                    "splinter group, the <red>Black Arm Gang</red>, may have some of it. Reldo " +
                    "might know where to find them.",
            ) {
                visibleWhen { p.phoenixGang == PHOENIX_READ_BOOK }
            }
            objective(
                "Reldo said <red>Baraek</red>, the fur trader in the market, has connections with " +
                    "the Phoenix Gang, and <red>Charlie the Tramp</red> by the south gate knows " +
                    "about the Black Arm Gang. I need to join one of them.",
            ) {
                visibleWhen {
                    p.phoenixGang == PHOENIX_TOLD_BY_RELDO && p.blackArmGang == 0
                }
            }
            phoenixObjectives(p)
            blackArmObjectives(p)
            objective(
                "I should take my half of the shield to the <red>curator</red> of the Varrock " +
                    "Museum so he can verify it.",
            ) {
                visibleWhen { isGangMember(p) && p.shieldGiven == 0 }
                custom(
                    gangShield(p)?.let { owns(access, it) } != true,
                    "My gang keeps its half of the shield " +
                        (if (isPhoenix(p)) "in a chest in their hideout." else "in a cupboard upstairs.") +
                        " I should find it and take it to the <red>curator</red> of the museum.",
                )
            }
            objective(
                "The curator gave me two half-certificates. I need a player from the other gang " +
                    "to trade me their half so I can join them into a whole <red>certificate</red> " +
                    "and claim the reward from <red>King Roald</red>.",
            ) {
                visibleWhen { p.shieldGiven != 0 }
                custom(
                    access.inv.count(CERTIFICATE) > 0,
                    "I have a whole certificate. I should take it to <red>King Roald</red> in " +
                        "Varrock Palace.",
                )
            }
        }

    private fun QuestJournalBuilder.phoenixObjectives(p: Player) {
        objective(
            "Baraek told me the Phoenix Gang hide behind the <red>VTAM Corporation</red>, down " +
                "the alley past the Blue Moon Inn.",
        ) {
            visibleWhen { p.phoenixGang == PHOENIX_TOLD_BY_BARAEK }
        }
        objective(
            "<red>Straven</red> wants me to kill <red>Jonny the Beard</red> in the Blue Moon Inn " +
                "and bring back his intelligence report.",
        ) {
            visibleWhen { p.phoenixGang == PHOENIX_TASKED }
            custom(
                access.inv.count(INTEL_REPORT) > 0,
                "I have Jonny the Beard's intelligence report. I should take it to " +
                    "<red>Straven</red>.",
            )
        }
        objective("I am a member of the <red>Phoenix Gang</red>.") {
            visibleWhen { isPhoenix(p) }
        }
    }

    private fun QuestJournalBuilder.blackArmObjectives(p: Player) {
        objective(
            "Charlie the Tramp told me the Black Arm Gang are down his alley. I should speak to " +
                "<red>Katrine</red>.",
        ) {
            visibleWhen { p.blackArmGang == BLACKARM_TOLD_BY_CHARLIE }
        }
        objective(
            "<red>Katrine</red> will let me join the Black Arm Gang if I steal two " +
                "<red>Phoenix crossbows</red> from the Phoenix Gang's weapon store. I'll need a " +
                "Phoenix Gang member's <red>weapon store key</red> to get in.",
        ) {
            visibleWhen { p.blackArmGang == BLACKARM_TASKED }
            custom(
                access.inv.count(CROSSBOW) >= 2,
                "I have two Phoenix crossbows. I should take them to <red>Katrine</red>.",
            )
        }
        objective("I am a member of the <red>Black Arm Gang</red>.") {
            visibleWhen { isBlackArm(p) }
        }
    }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            val route =
                if (player.player.phoenixGang == PHOENIX_COMPLETE) {
                    "I joined the Phoenix Gang by killing Jonny the Beard for his intelligence " +
                        "report, and found their half of the shield in a chest in their hideout."
                } else {
                    "I joined the Black Arm Gang by stealing two crossbows from the Phoenix Gang, " +
                        "and found their half of the shield in a cupboard in their hideout."
                }
            line(
                "Reldo pointed me to a book about the Shield of Arrav, stolen from the museum " +
                    "long ago by the Phoenix Gang.",
            )
            line(route)
            line(
                "With the help of a member of the other gang, I returned the whole shield to the " +
                    "museum and King Roald paid me my half of the reward.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_shieldofarrav"

        const val STAGE_STARTED = 1
        const val STAGE_COMPLETE = 2

        const val PHOENIX_STARTED = 1
        const val PHOENIX_READ_BOOK = 2
        const val PHOENIX_TOLD_BY_RELDO = 3
        const val PHOENIX_TOLD_BY_BARAEK = 4
        const val PHOENIX_TASKED = 8
        const val PHOENIX_JOINED = 9
        const val PHOENIX_COMPLETE = 10

        const val BLACKARM_TOLD_BY_CHARLIE = 1
        const val BLACKARM_TASKED = 2
        const val BLACKARM_JOINED = 3
        const val BLACKARM_COMPLETE = 4

        const val SHIELD_GIVEN_PHOENIX = 1
        const val SHIELD_GIVEN_BLACKARM = 2

        const val RECOMMENDED_COMBAT = 10
        const val REWARD_COINS = 600

        const val COINS = "obj.coins"
        const val BOOK = "obj.the_shield_of_arrav"
        const val INTEL_REPORT = "obj.intelligence_report"
        const val WEAPON_STORE_KEY = "obj.phoenixkey2"
        const val CROSSBOW = "obj.phoenix_crossbow"
        const val PHOENIX_SHIELD = "obj.arravshield1"
        const val BLACKARM_SHIELD = "obj.arravshield2"
        const val PHOENIX_CERTIFICATE = "obj.arravcertificate_lft"
        const val BLACKARM_CERTIFICATE = "obj.arravcertificate_rht"
        const val CERTIFICATE = "obj.arravcertificate"
    }
}
