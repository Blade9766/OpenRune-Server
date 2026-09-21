package org.rsmod.content.quest.area.lunarisle.lunardiplomacy.dream

import jakarta.inject.Inject
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.interfaces.emotes.PlayEmote
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Binds the Dream World islands' npcs, scenery, rival timer and emotes to [DreamChallenges]. */
class DreamChallengesScript @Inject constructor(private val challenges: DreamChallenges) : PluginScript() {
    override fun ScriptContext.startup() {
        with(challenges) {
            onOpNpc1(DreamWorld.FLUKE) { fluke() }
            onOpNpc1(DreamWorld.NUMERATOR) { numerator() }
            onOpNpc1(DreamWorld.MIMIC) { mimic() }
            onOpNpc1(DreamWorld.PERCEPTIVE) { perceptive() }
            onOpNpc1(DreamWorld.EXPERT) { expert() }
            onOpNpc1(DreamWorld.GUIDE) { guide() }

            for (die in DreamChallenges.Die.entries) {
                onOpLoc1(die.loc) { rollDie(die) }
            }
            for (number in 0..9) {
                onOpLoc1("loc.lunar_dream_number_$number") { pressNumber(number) }
            }

            onOpLoc1(DREAM_TREE) { chopDreamTree(it.loc) }
            onOpLoc1(PLAYER_LOG_PILE) { depositLogs() }
            onOpLoc1(RIVAL_LOG_PILE) { mes("That's the woodcutter's pile. Why help him?") }

            onOpLoc1(HURDLE) { jumpHurdle(it.loc) }

            onOpLoc1(DREAM_PUFF) { jumpToPuff(it.loc) }
            onOpLoc1(PLATFORM_EDGE) { jumpToPuff(it.loc) }
        }
        onPlayerSoftTimer(DreamChallenges.RIVAL_TIMER) { challenges.onRivalTimer(player) }
        onEvent<PlayEmote> { challenges.emotePlayed(player, seq.id) }
    }

    private companion object {
        const val DREAM_TREE = "loc.lunar_dream_dream_tree"
        const val PLAYER_LOG_PILE = "loc.lunar_dream_logplay_multi"
        const val RIVAL_LOG_PILE = "loc.lunar_dream_lognpc_multi"
        const val HURDLE = "loc.lunar_dream_hurdles"
        const val DREAM_PUFF = "loc.quest_lunar_dream_puff_platform_multi"
        const val PLATFORM_EDGE = "loc.quest_lunar_jumpgame_edge_dummy"
    }
}
