package org.rsmod.content.quest.area.desert.shadowofthestorm

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Shadow of the Storm.
 *
 * The stage is `varbit.agrith_quest` (bits 0-6 of `varp.agrith_quest_varp`, which also carries the
 * incantation, the kiln trail and the four recruits), endstate 125 from
 * `dbrow.quest_shadowofthestorm`. Every stage the cache cares about is a multiple of ten, because
 * the multinpcs and multilocs of Uzer index their transform lists with it:
 * - [STAGE_STARTED]: Father Reen has sent the player east; `varbit.agrith_badden_uzer` puts Father
 *   Badden in the ruins.
 * - [STAGE_BRIEFED]: Badden has explained the disguise. Black clothes and a blackened Silverlight
 *   get the player past Evil Dave, who stands by the portal from [STAGE_STARTED] to
 *   [STAGE_FOUND_TOME].
 * - [STAGE_INFILTRATED]: the player is one of the cult; Denath is waiting by the throne.
 * - [STAGE_TASKED]: Denath has given the player their incantation, stored backwards in
 *   `varbit.agrith_incantation_1` to `_5` - those varbits hold the order the *tome* uses, and
 *   Denath recites it in reverse.
 * - [STAGE_ASKED_MATTHEW] and [STAGE_ASKED_GOLEM]: the trail to the tome. At
 *   [STAGE_ASKED_GOLEM] the four kilns outside turn into their "Look-in" variants and
 *   `varbit.agrith_kiln` says which one holds it.
 * - [STAGE_FIRST_RITUAL]: Matthew has the tome and Denath has called the circle.
 * - [STAGE_CHASE]: the chant went backwards and unmade Denath. Tanya, Eric and Evil Dave are
 *   in the temple passage, Uzer is under a sandstorm, and Eric is under a pile of rubble.
 * - [STAGE_RECRUITING]: Tanya's sigil is in hand; four sigils need four willing casters, tracked
 *   by `varbit.agrith_convinced_dave`, `_badden_uzer`, `_reen_uzer` and `_convinced_golem`.
 * - [STAGE_SECOND_RITUAL] and [STAGE_FIGHT]: the summoning, and Agrith-Naar himself.
 * - [STAGE_SLAIN]: the demon is dead and Silverlight has drunk his blood.
 */
@Singleton
class ShadowOfTheStormQuest : QuestScript(
    QUEST_KEY,
    "varp.agrith_quest_varp",
    rewards {
        scroll("10,000 experience in a combat skill of your choice", "Darklight")
    },
    ItemRewardDisplay(DARKLIGHT, zoom = 130),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
    questVarbit = "varbit.agrith_quest",
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun inProgress(player: Player): Boolean = isStarted(player) && !isComplete(player)

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

    /**
     * Clears the quest's own varbits when it is reset, and puts the Uzer golem back on its feet
     * when the quest ends: while it is away at the ritual The Golem's clay counter is parked on
     * [GOLEM_CLAY_AWAY], which hides its Uzer spawn.
     */
    fun syncVars(player: Player) {
        val stage = stage(player)
        if (stage == 0) {
            for (varbit in SUB_STATE_VARBITS) {
                setVarBit(player, varbit, 0)
            }
            return
        }
        if (stage >= STAGE_COMPLETE && player.vars["varbit.golem_clay"] == GOLEM_CLAY_AWAY) {
            setVarBit(player, "varbit.golem_clay", GOLEM_CLAY_REPAIRED)
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Father Reen</col>, south of the " +
            "<col=800000>Al Kharid</col> bank."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(player.player)
            objective(
                "<red>Father Reen</red> believes a demon-worshipping cult has gathered in the " +
                    "ruins of <red>Uzer</red>. His brother <red>Father Badden</red> is watching " +
                    "them from the ruins and is expecting me.",
            ) {
                visibleWhen { stage == STAGE_STARTED }
            }
            objective(
                "Badden wants me inside the cult. They only take people dressed for it: " +
                    "<red>three pieces of black clothing</red>, and <red>Silverlight</red> " +
                    "stained black with a <red>black mushroom</red>.",
            ) {
                visibleWhen { stage == STAGE_BRIEFED }
                hasItem(DYED_SILVERLIGHT, "I have stained Silverlight black.").strike()
            }
            objective(
                "<red>Evil Dave</red> let me through the portal below the ruins. Their leader " +
                    "<red>Denath</red> is waiting by the throne.",
            ) {
                visibleWhen { stage == STAGE_INFILTRATED }
            }
            objective(
                "Denath has given me my part of the incantation and wants me to take " +
                    "<red>Josef</red>'s place in the summoning. I should ask the others about him.",
            ) {
                visibleWhen { stage in STAGE_TASKED until STAGE_FIRST_RITUAL }
                hasItem(SIGIL_MOULD, "Jennifer gave me a demonic sigil mould.").strike()
                hasItem(SIGIL, "I cast a demonic sigil from a silver bar.").strike()
                stageAtLeast(
                    STAGE_ASKED_MATTHEW,
                    "Matthew says Josef dropped a book the night he disappeared.",
                ).strike()
                stageAtLeast(
                    STAGE_ASKED_GOLEM,
                    "The <red>clay golem</red> saw Denath kill Josef and hide the book in one of " +
                        "the <red>kilns</red> outside.",
                ).strike()
                hasItem(TOME, "I have the demonic tome. I should read it, then give it to Matthew.")
            }
            objective(
                "Matthew has the tome and Denath has called us to the circle. I should stand on " +
                    "my point of the pentagram and <red>chant</red> my incantation.",
            ) {
                visibleWhen { stage == STAGE_FIRST_RITUAL }
            }
            objective(
                "The incantation was backwards, and it unmade Denath - he was <red>Agrith-Naar</red> " +
                    "all along. The others fled into the temple passage.",
            ) {
                visibleWhen { stage == STAGE_CHASE }
            }
            objective(
                "Four sigils, four casters. Agrith-Naar can be summoned again and killed with " +
                    "<red>Silverlight</red>, but only if I can talk four people into the circle.",
            ) {
                visibleWhen { stage == STAGE_RECRUITING }
                custom(
                    player.player.daveConvinced >= RECRUITED,
                    "<red>Evil Dave</red> has gone back to the throne room.",
                ).strike()
                custom(
                    player.player.baddenAtUzer >= RECRUITED,
                    "<red>Father Badden</red> has agreed to cast with me.",
                ).strike()
                custom(
                    player.player.reenAtUzer >= RECRUITED,
                    "<red>Father Reen</red> has agreed to cast with me.",
                ).strike()
                custom(
                    player.player.golemConvinced >= GOLEM_REFUSED,
                    "The golem refuses: something written in its head forbids it.",
                ).strike()
                custom(
                    player.player.golemConvinced >= GOLEM_REPROGRAMMED,
                    "I used the <red>strange implement</red> to take that page out of its skull.",
                ).strike()
                custom(
                    player.player.golemConvinced >= GOLEM_RECRUITED,
                    "The <red>clay golem</red> has agreed to cast with me.",
                ).strike()
            }
            objective(
                "Everyone is in place. I should stand on my point of the pentagram and " +
                    "<red>chant</red> the incantation in the order the tome gives it.",
            ) {
                visibleWhen { stage == STAGE_SECOND_RITUAL }
            }
            objective(
                "<red>Agrith-Naar</red> is here. Any weapon will wound him, but the killing blow " +
                    "must be struck with <red>Silverlight</red>.",
            ) {
                visibleWhen { stage == STAGE_FIGHT }
            }
            objective(
                "The demon is dead and his blood has soaked into Silverlight. I should speak to " +
                    "<red>Father Reen</red>.",
            ) {
                visibleWhen { stage == STAGE_SLAIN }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Father Reen sent me to Uzer, where his brother Badden was watching a cult of " +
                    "demon worshippers meet beneath the ruins.",
            )
            line(
                "Their leader Denath gave me an incantation and a place in his summoning. It ran " +
                    "backwards, and speaking it unmade him: Denath was the demon Agrith-Naar.",
            )
            line(
                "With Reen, Badden, Evil Dave and the clay golem holding the circle I called him " +
                    "back and cut him down with Silverlight, which drank his blood and became " +
                    "Darklight.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_shadowofthestorm"

        const val GOLEM_QUEST = "quest_golem"
        const val DEMON_SLAYER_QUEST = "quest_demonslayer"

        const val STAGE_STARTED = 10
        const val STAGE_BRIEFED = 20
        const val STAGE_INFILTRATED = 30
        const val STAGE_TASKED = 40
        const val STAGE_ASKED_MATTHEW = 50
        const val STAGE_ASKED_GOLEM = 60
        const val STAGE_FOUND_TOME = 70
        const val STAGE_FIRST_RITUAL = 80
        const val STAGE_CHASE = 90
        const val STAGE_RECRUITING = 100
        const val STAGE_SECOND_RITUAL = 110
        const val STAGE_FIGHT = 120
        const val STAGE_SLAIN = 124
        const val STAGE_COMPLETE = 125

        /** `varbit.agrith_badden_uzer`, `_reen_uzer` and `_convinced_dave` all use these. */
        const val IN_UZER = 1
        const val RECRUITED = 2

        const val GOLEM_REFUSED = 1
        const val GOLEM_REPROGRAMMED = 2
        const val GOLEM_RECRUITED = 3

        /** `varbit.golem_clay` values: four pieces of clay is a working golem, five hides it. */
        const val GOLEM_CLAY_REPAIRED = 4
        const val GOLEM_CLAY_AWAY = 5

        const val CRAFTING_REQ = 30
        const val BLACK_ITEMS_REQ = 3
        const val COMBAT_XP_REWARD = 10_000.0

        const val REEN = "npc.agrith_reen"

        /**
         * Spawn types. A multinpc's ops only ever arrive on its base name, so every hook has to
         * be registered on the type that was actually placed on the map, not on the form the
         * player sees.
         */
        const val REEN_AL_KHARID_SPAWN = "npc.agrith_reen_alkharid"
        const val REEN_UZER_SPAWN = "npc.agrith_reen_uzer"
        const val BADDEN_UZER_SPAWN = "npc.agrith_badden_uzer"
        const val DAVE_PORTAL_SPAWN = "npc.agrith_dave_at_portal"
        const val DAVE_PASSAGE_SPAWN = "npc.agrith_dave_in_passage"
        const val TANYA_PASSAGE_SPAWN = "npc.agrith_tanya_falling"

        const val BADDEN = "npc.agrith_badden"
        const val DAVE = "npc.agrith_dave"
        const val DENATH = "npc.agrith_denath"
        const val JENNIFER = "npc.agrith_jennifer"
        const val MATTHEW = "npc.agrith_matthew"
        const val TANYA = "npc.agrith_tanya"
        const val ERIC = "npc.agrith_eric"
        const val PATRICK = "npc.agrith_patrick"
        const val GHOST_SPAWN = "npc.agrith_tanya_ghost"
        const val AGRITH_NAAR = "npc.agrith_naar"
        const val THRONE_GOLEM = "npc.agrith_golem_throneroom"

        /** The circle versions of each caster, drawn holding their sigil. */
        const val DENATH_SIGIL = "npc.agrith_denath_sigil"
        const val DAVE_SIGIL = "npc.agrith_dave_sigil"
        const val ERIC_SIGIL = "npc.agrith_eric_sigil"
        const val TANYA_SIGIL = "npc.agrith_tanya_sigil"
        const val REEN_SIGIL = "npc.agrith_reen_sigil"
        const val BADDEN_SIGIL = "npc.agrith_badden_sigil"
        const val GOLEM_SIGIL = "npc.agrith_golem_sigil"

        const val SILVERLIGHT = "obj.silverlight"
        const val DYED_SILVERLIGHT = "obj.agrith_silverlight_dyed"
        const val DARKLIGHT = "obj.darklight"
        const val SIGIL_MOULD = "obj.agrith_sigil_mould"
        const val SIGIL = "obj.agrith_sigil"
        const val TOME = "obj.agrith_book"
        const val MUSHROOM = "obj.golem_mushroom"
        const val BLACK_DYE = "obj.golem_ink"
        const val IMPLEMENT = "obj.golem_golemkey"
        const val DESERT_SHIRT = "obj.desert_shirt"
        const val DESERT_ROBE = "obj.desert_robe"
        const val DYED_DESERT_SHIRT = "obj.agrith_desert_shirt_dyed"
        const val DYED_DESERT_ROBE = "obj.agrith_desert_robe_dyed"

        /** `dbrow.synth_unsummoning`. */
        const val SOUND_APPEAR = "synth.agrith_appear"
        const val SOUND_CIRCLE = "synth.agrith_circle"
        const val SOUND_PORTAL_CLOSE = "synth.agrith_portalclose"
        const val SOUND_SANDSTORM = "synth.agrith_sandstorm"
        const val SOUND_SUMMON = "synth.agrith_summon"

        val SUB_STATE_VARBITS =
            listOf(
                "varbit.agrith_kiln",
                "varbit.agrith_convinced_golem",
                "varbit.agrith_convinced_dave",
                "varbit.agrith_badden_uzer",
                "varbit.agrith_reen_uzer",
                "varbit.agrith_incantation_1",
                "varbit.agrith_incantation_2",
                "varbit.agrith_incantation_3",
                "varbit.agrith_incantation_4",
                "varbit.agrith_incantation_5",
            )

        fun setVarBit(player: Player, varbit: String, value: Int) {
            if (player.vars[varbit] != value) {
                VarPlayerIntMapSetter.set(player, varbit, value)
            }
        }
    }
}
