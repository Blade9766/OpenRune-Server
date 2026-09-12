package org.rsmod.content.quest.area.varrock.dragonslayer

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.Magic
import org.rsmod.api.bosses.dsl.Melee
import org.rsmod.api.bosses.dsl.WithinMeleeRange
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript

/**
 * Melzar the Mad, in the basement of his maze. He fights with his fists, his feet and a spell
 * or two, ranting all the while. The magenta key comes from his drop table.
 */
class MelzarTheMad @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override val spec =
        boss("npc.melzar_the_mad") {
            stats(attackRate = 4, aggressionRadius = 4)

            val kick =
                ability("kick") {
                    say("Feel the wrath of my feet!")
                    anim("seq.human_unarmedkick")
                    hit {
                        damage(0..5).roll()
                        type(Melee)
                    }
                }

            val punch =
                ability("punch") {
                    say("By the power of custard!")
                    anim("seq.human_unarmedpunch")
                    hit {
                        damage(0..5).roll()
                        type(Melee)
                    }
                }

            val teaSpell =
                ability("tea_spell") {
                    say("Let me drink my tea in peace.")
                    anim("seq.dragon_slayer_qip_melzar2")
                    sound("synth.dragonslayer_melzar_spell")
                    hit {
                        damage(0..7).roll()
                        type(Magic)
                        spotanim("spotanim.confuse_impact")
                    }
                }

            val rockSpell =
                ability("rock_spell") {
                    say("Leave me alone, I need to feed my pet rock.")
                    anim("seq.dragon_slayer_qip_melzar2")
                    sound("synth.dragonslayer_melzar_spell")
                    hit {
                        damage(0..7).roll()
                        type(Magic)
                        spotanim("spotanim.weaken_impact")
                    }
                }

            phase("combat") {
                weightedSelectorRandom {
                    +random(kick, weight = 2, requires = WithinMeleeRange)
                    +random(punch, weight = 2, requires = WithinMeleeRange)
                    +random(teaSpell, weight = 1)
                    +random(rockSpell, weight = 1)
                }
            }
        }
}
