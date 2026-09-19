package org.rsmod.content.quest.area.desert.thegolem.npcs

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.FEATHER
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.PEN
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.PHOENIX
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.THIEVING_REQ
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Desert Phoenix wandering north-west of Uzer, by the clay rocks. Its tail feathers are the
 * pens golems were once programmed with; grabbing one works like a pickpocket.
 */
class DesertPhoenix @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc3(PHOENIX) { grabFeather(it.npc) }
    }

    private suspend fun ProtectedAccess.grabFeather(phoenix: Npc) {
        if (player.thievingLvl < THIEVING_REQ) {
            mes("You need a Thieving level of $THIEVING_REQ to grab the phoenix's tail-feather.")
            return
        }
        if (FEATHER in player.inv || PEN in player.inv) {
            mes("You already have a phoenix tail-feather.")
            return
        }
        arriveDelay()
        faceEntitySquare(phoenix)
        anim(STEAL_SEQ)
        spam("You attempt to grab the phoenix's tail-feather.")
        delay(2)
        if (!phoenix.isSlotAssigned) {
            return
        }
        if (random.of(maxExclusive = 256) < successChance()) {
            invAddOrDrop(objRepo, FEATHER)
            statAdvance("stat.thieving", FEATHER_XP)
            soundSynth(STEAL_SOUND)
            mes("You grab a tail-feather.")
            return
        }
        mes("You fail to grab the feather.")
        phoenix.facePlayer(player)
        phoenix.say("Squawk!")
        queueHit(source = phoenix, delay = 1, type = HitType.Typeless, damage = STUN_DAMAGE)
        spotanim(STUN_SPOTANIM, height = STUN_SPOTANIM_HEIGHT)
        soundSynth(STUN_SOUND)
        CombatEffects.stun(player, STUN_CYCLES)
        mes("You've been stunned!")
    }

    /** Out of 256: a coin flip at the requirement, near-certain by level 90. */
    private fun ProtectedAccess.successChance(): Int {
        val over = (player.thievingLvl - THIEVING_REQ).coerceAtLeast(0)
        return (LOW_CHANCE + over * (HIGH_CHANCE - LOW_CHANCE) / (MAX_LEVEL - THIEVING_REQ))
            .coerceAtMost(HIGH_CHANCE)
    }

    private companion object {
        const val FEATHER_XP = 26.0
        const val STUN_DAMAGE = 2
        const val STUN_CYCLES = 9
        const val STUN_SPOTANIM_HEIGHT = 100
        const val LOW_CHANCE = 128
        const val HIGH_CHANCE = 250
        const val MAX_LEVEL = 90

        const val STEAL_SEQ = "seq.human_pickpocket"
        const val STEAL_SOUND = "synth.pick"
        const val STUN_SPOTANIM = "spotanim.stunned_thieving"
        const val STUN_SOUND = "synth.thieving_stunned"
    }
}
