package org.rsmod.content.quest.area.seers.murdermystery

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Singleton
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Murder Mystery.
 *
 * The stage is the whole of `varp.murderquest` (192), endstate 2 from
 * `dbrow.quest_murdermystery`: 1 while the case is open, 2 once it is solved. The murderer is the
 * cache varp `varp.murdersus` (195), rolled when the guard takes the player on. The evidence the
 * player has gathered lives on server varbits on `varp.murder_state` (see `MurderMysteryVars`):
 * - `murder_poison_progress`: 1 once the poison salesman has named the whole family as buyers, 2
 *   once the murderer has claimed what they used their poison on, 3 once that claim is disproved.
 * - `murder_found_thread` and `murder_found_prints`: the thread from the study window and the
 *   fingerprint match against the dagger.
 */
@Singleton
class MurderMysteryQuest : QuestScript(
    QUEST_KEY,
    "varp.murderquest",
    rewards {
        xp("stat.crafting", 1406.0)
        item("obj.coins", 2000, label = "2000 Coins")
    },
    ItemRewardDisplay("obj.coins", zoom = 250),
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isInvestigating(player: Player): Boolean = stage(player) == STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun start(access: ProtectedAccess) {
        if (isStarted(access.player)) {
            return
        }
        quest.setQuestStage(access, STAGE_STARTED)
        access.player.murderSuspect = Suspect.entries[access.random.of(Suspect.entries.size)].id
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    /** Clears the case varbits whenever the case is not open, so a reset or a finish starts clean. */
    fun syncVars(player: Player) {
        if (stage(player) == STAGE_STARTED) {
            return
        }
        if (player.murderSuspect != 0) {
            player.murderSuspect = 0
        }
        if (player.murderPoisonProgress != 0) {
            player.murderPoisonProgress = 0
        }
        if (player.murderFoundThread) {
            player.murderFoundThread = false
        }
        if (player.murderFoundPrints) {
            player.murderFoundPrints = false
        }
    }

    /** Every piece of case evidence the player owns, held, banked or worn. */
    fun ownedCount(access: ProtectedAccess, obj: String): Int =
        access.inv.count(obj) + access.bank.count(obj) + access.worn.count(obj)

    /** The guard keeps all the evidence: every quest item leaves the inventory, bank and body. */
    fun handOverEvidence(access: ProtectedAccess) {
        val removedWorn =
            access.worn.indices.mapNotNull { slot ->
                val obj = access.worn[slot] ?: return@mapNotNull null
                if (EVIDENCE.none { it.asRSCM(RSCMType.OBJ) == obj.id }) null else slot to obj.id
            }
        for (obj in EVIDENCE) {
            for (container in listOf(access.inv, access.bank, access.worn)) {
                val count = container.count(obj)
                if (count > 0) {
                    access.invDel(container, obj, count)
                }
            }
        }
        for ((slot, id) in removedWorn) {
            val wearpos = Wearpos.entries.firstOrNull { it.slot == slot } ?: continue
            val type = ServerCacheManager.getItem(id) ?: continue
            access.publish(HeldEquipEvents.WearposChange(access.player, wearpos, type))
            access.publish(HeldEquipEvents.Unequip(access.player, wearpos, type))
        }
        if (removedWorn.isNotEmpty()) {
            access.rebuildAppearance()
        }
    }

    override fun subTitle(): String =
        "talking to one of the <col=800000>Guards</col> at the <col=800000>Sinclair " +
            "Mansion</col>, north of <col=800000>Camelot</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "Lord Sinclair has been murdered in his mansion. The <red>Guards</red> have no " +
                    "idea who did it, and have asked me to investigate and find proof of who the " +
                    "killer is.",
            ) {}
            objective(
                "The murderer left a <red>dagger</red> and a strange smelling <red>pot</red> in " +
                    "Lord Sinclair's study, and escaped through its <red>window</red>.",
            ) {
                custom(access.player.murderFoundThread, "I found some thread caught on the study window.")
                    .strike()
            }
            objective(
                "Everybody's hands leave marks on shiny things. If I dust the dagger and the " +
                    "family's silver belongings with <red>flour</red>, I could lift the marks " +
                    "with some <red>flypaper</red> and compare them.",
            ) {
                custom(access.player.murderFoundPrints, "The prints on the dagger match one of the family.")
                    .strike()
            }
            objective(
                "Somebody bought a lot of <red>poison</red> recently. Whoever did it may have " +
                    "lied about what they used it on.",
            ) {
                custom(
                    access.player.murderPoisonProgress >= POISON_DISPROVED,
                    "I have proved one of the family lied about their poison.",
                ).strike()
            }
            objective("When I have enough evidence, I should tell the <red>Guards</red> who did it.") {
                visibleWhen {
                    access.player.murderFoundThread &&
                        access.player.murderFoundPrints &&
                        access.player.murderPoisonProgress >= POISON_DISPROVED
                }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I helped the Guards investigate the murder of Lord Sinclair at his mansion " +
                    "north of Camelot.",
            )
            line(
                "The guard dog never barked, so it was no intruder. Thread on the study window, " +
                    "a lie about some poison and the fingerprints on the dagger proved which of " +
                    "his children had killed him.",
            )
            line("The murderer is under house arrest awaiting trial, and the family rewarded me.")
        }

    companion object {
        const val QUEST_KEY = "quest_murdermystery"

        const val STAGE_STARTED = 1
        const val STAGE_COMPLETE = 2

        const val POISON_ASKED_SALESMAN = 1
        const val POISON_CLAIM_HEARD = 2
        const val POISON_DISPROVED = 3

        const val DAGGER = "obj.murderweapon"
        const val DUSTED_DAGGER = "obj.murderweapondust"
        const val UNKNOWN_PRINT = "obj.murderfingerprint1"
        const val KILLERS_PRINT = "obj.murderfingerprint"
        const val PUNGENT_POT = "obj.murderpot2"
        const val FLYPAPER = "obj.murderpaper"

        val EVIDENCE: List<String> by lazy {
            Suspect.entries.flatMap { listOf(it.silverItem, it.dustedItem, it.print) } +
                listOf(
                    RED_THREAD,
                    GREEN_THREAD,
                    BLUE_THREAD,
                    FLYPAPER,
                    PUNGENT_POT,
                    DAGGER,
                    DUSTED_DAGGER,
                    KILLERS_PRINT,
                    UNKNOWN_PRINT,
                )
        }
    }
}
