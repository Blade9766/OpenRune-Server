package org.rsmod.content.quest.area.lumbridge

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Rune Mysteries.
 *
 * The stage lives in `varp.runemysteries` (63), which drives the Sedridor, Aubury, Brimstail and
 * Distentor multi npcs (their Teleport op only appears at the endstate). Values follow RuneLite's
 * quest helper: 1 with the Duke's talisman, 2 once Sedridor has it, 3 after agreeing to visit
 * Aubury, 4 once Aubury holds the package, 5 after he has read it and handed over his notes, and
 * the endstate [STAGE_COMPLETE] (6). The dialogue flags are the cache's own varbits on
 * `varp.runemysteries_secondary` (3404), which the quest manager never overwrites.
 */
@Singleton
class RuneMysteriesQuest :
    QuestScript(
        "quest_runemysteries",
        "varp.runemysteries",
        rewards { extra("Access to the Rune Essence Mine") },
        ItemRewardDisplay(AIR_TALISMAN),
    ) {

    override fun ScriptContext.init() {}

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Duke Horacio</col> on the <col=800000>1<sup>st</sup> floor of " +
            "Lumbridge Castle</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(player.player)
            val flags = player.player

            if (stage >= STAGE_TALISMAN_GIVEN) {
                strike(DUKE_ENTRY)
            } else {
                objective(
                    "I spoke to <red>Duke Horacio</red> in Lumbridge Castle. He told me that he'd " +
                        "found a <red>Strange Talisman</red> in the Castle which might be of use to " +
                        "the Order of Wizards at the <red>Wizards' Tower</red>. He asked me to take " +
                        "it there and give it to a wizard called <red>Sedridor</red>.",
                ) {}
                objective(
                    "I can find the Wizards' Tower south west of Lumbridge, across the bridge from " +
                        "Draynor Village.",
                ) {}
                objective("If I lose the talisman, I should ask the Duke for another.") {
                    visibleWhen { !access.inv.contains(AIR_TALISMAN) }
                }
            }

            if (stage == STAGE_TALISMAN_GIVEN) {
                objective(
                    "I delivered the Strange Talisman to Sedridor in the basement of the Wizards' " +
                        "Tower. He believes it could be the key to rediscovering the lost " +
                        "incantation to the <red>Rune Essence Mine</red>. I should speak to him " +
                        "again to find out how I can help.",
                ) {}
            }

            if (stage >= STAGE_PACKAGE_DELIVERED) {
                strike(SEDRIDOR_ENTRY)
            } else if (stage == STAGE_PACKAGE) {
                objective(
                    "I delivered the Strange Talisman to Sedridor in the basement of the Wizards' " +
                        "Tower. He believes it might be key to discovering a Teleportation " +
                        "Incantation to the lost Rune Essence Mine. He asked me to help confirm " +
                        "this by delivering a <red>Package</red> to <red>Aubury</red>, an expert on " +
                        "Runecrafting. I can find him in his Rune Shop in south east Varrock.",
                ) {}
                objective("I still need to collect the Package from Sedridor.") {
                    visibleWhen { !flags.rmPackage }
                }
                objective("If I lose the Package, I'll need to ask Sedridor for another.") {
                    visibleWhen { flags.rmPackage && !access.inv.contains(RESEARCH_PACKAGE) }
                }
            }

            if (stage == STAGE_PACKAGE_DELIVERED) {
                objective(
                    "I delivered the Package to Aubury at his Rune Shop in south east Varrock. I " +
                        "should see what he can tell me about the Teleportation Incantation.",
                ) {}
            }

            if (stage == STAGE_NOTES) {
                if (flags.rmNotesGiven) {
                    strike(AUBURY_ENTRY)
                    objective(
                        "I gave Aubury's Research Notes to Sedridor. I should see what he has " +
                            "learnt from them.",
                    ) {}
                } else {
                    objective(
                        "I delivered the Package to Aubury at his Rune Shop in south east Varrock. " +
                            "He confirmed Sedridor's suspicions and asked me to take some " +
                            "<red>Research Notes</red> back to him. I can find Sedridor in the " +
                            "basement of the Wizards' Tower.",
                    ) {}
                    objective("I still need to collect the Research Notes from Aubury.") {
                        visibleWhen { !flags.rmNotes }
                    }
                    objective("If I lose the Research Notes, I'll need to ask Aubury for more.") {
                        visibleWhen { flags.rmNotes && !access.inv.contains(RESEARCH_NOTES) }
                    }
                }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(DUKE_ENTRY)
            line(SEDRIDOR_ENTRY)
            line(
                "I delivered the Package to Aubury in Varrock. He confirmed Sedridor's suspicions " +
                    "and asked me to take some Research Notes back to him. I did so, and Sedridor " +
                    "used them to discover the Teleportation Incantation to the lost Rune Essence " +
                    "Mine. As a thank you for my help, he granted me permission to use the Rune " +
                    "Essence Mine whenever I please.",
            )
        }

    companion object {
        const val STAGE_TALISMAN = 1
        const val STAGE_TALISMAN_GIVEN = 2
        const val STAGE_PACKAGE = 3
        const val STAGE_PACKAGE_DELIVERED = 4
        const val STAGE_NOTES = 5
        const val STAGE_COMPLETE = 6

        const val AIR_TALISMAN = "obj.air_talisman"
        const val RESEARCH_PACKAGE = "obj.research_package"
        const val RESEARCH_NOTES = "obj.research_notes"

        private const val DUKE_ENTRY =
            "I spoke to Duke Horacio in Lumbridge Castle. He told me that he'd found a Strange " +
                "Talisman in the Castle which might be of use to the Order of Wizards at the " +
                "Wizards' Tower. He asked me to take it there and give it to a wizard called " +
                "Sedridor."

        private const val SEDRIDOR_ENTRY =
            "I delivered the Strange Talisman to Sedridor in the Wizards' Tower. He believed it " +
                "might be key to discovering a Teleportation Incantation to the lost Rune Essence " +
                "Mine. He asked me to help confirm this by delivering a Package to Aubury, an " +
                "expert on Runecrafting."

        private const val AUBURY_ENTRY =
            "I delivered the Package to Aubury in Varrock. He confirmed Sedridor's suspicions and " +
                "asked me to take some Research Notes back to him."
    }
}
