package org.rsmod.content.quest.area.feldip.bigchompy

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.output.HintArrows
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onApNpc5
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BONES
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CHOMPY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CHOMPY_DEAD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CHOMPY_DISPLAY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CHOMPY_EAT_SEQ
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CHOMPY_LAND_SEQ
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CHOMPY_SQUAWK_SOUND
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FEATHER
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.OGRE_BOW
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.PICK_UP_SEQ
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.RAW_CHOMPY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_CHOMPY_ATE_TOAD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_DROPPED_TOAD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_GOT_BOW
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_KILLED_CHOMPY
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The chompy birds a bloated toad lures down, and what is left of one after it has been shot.
 *
 * A chompy lands within a few tiles of the bait, walks over to it and swallows it. Only the hunter
 * whose toad it came for may shoot it, and only with the ogre bow: that is the whole point of the
 * quest, since Rantz's arrows are no use out of that bow.
 */
@Singleton
class ChompyBirds
@Inject
constructor(
    private val quest: BigChompyBirdHuntingQuest,
    private val hunt: ChompyHunt,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onNpcQueue(EAT_TOAD_QUEUE) { approachBait() }
        onNpcQueue(SWALLOW_QUEUE) { swallowBait() }
        // The chompy carries Attack in its fifth slot, so hand it to the standard attack op.
        onApNpc5(CHOMPY) { opNpc2(it.npc) }
        onOpNpc5(CHOMPY) { opNpc2(it.npc) }
        onOpNpc4(CHOMPY_DEAD) { pluck(it.npc) }
        onOpHeld3(OGRE_BOW) { checkKills() }
    }

    /** Brings a chompy down beside [bait] for the player who placed it. */
    fun land(owner: Player, bait: Npc) {
        val chompy = hunt.landChompy(owner, bait.coords) ?: return
        chompy.anim(CHOMPY_LAND_SEQ)
        chompy.say("Sqwirk!")
        owner.soundSynth(CHOMPY_SQUAWK_SOUND)
        HintArrows.hintNpc(owner, chompy)
        chompy.queue(EAT_TOAD_QUEUE, APPROACH_CYCLES)
    }

    private fun StandardNpcAccess.approachBait() {
        val bait = hunt.baitNear(npc.coords, radius = APPROACH_RANGE)
        if (bait == null) {
            npc.defaultMode()
            return
        }
        npc.resetMode()
        npc.faceSquare(bait.coords)
        if (npc.coords.chebyshevDistance(bait.coords) > 1) {
            npc.walk(bait.coords)
            npc.queue(EAT_TOAD_QUEUE, APPROACH_CYCLES)
            return
        }
        npc.say("Sqwark!")
        npc.anim(CHOMPY_EAT_SEQ)
        npc.queue(SWALLOW_QUEUE, SWALLOW_CYCLES)
    }

    private fun StandardNpcAccess.swallowBait() {
        val bait = hunt.baitNear(npc.coords, radius = 1)
        npc.say("Gobble!")
        if (bait != null) {
            bait.say("!!Croak!!")
            hunt.remove(bait)
        }
        npc.defaultMode()
        val owner = hunt.ownerOf(npc) ?: return
        if (quest.stage(owner) == STAGE_DROPPED_TOAD) {
            hunt.withAccess(owner) { quest.advanceTo(this, STAGE_CHOMPY_ATE_TOAD) }
        }
    }

    private suspend fun ProtectedAccess.pluck(carcass: Npc) {
        arriveDelay()
        if (!hunt.ownsKill(carcass, player)) {
            mes("This is not your chompy bird to pluck.")
            return
        }
        mes("You start plucking the chompy bird.")
        anim(PICK_UP_SEQ)
        delay(1)
        val tile = carcass.coords
        objRepo.add(RAW_CHOMPY, tile, DROP_DURATION, receiver = player)
        objRepo.add(BONES, tile, DROP_DURATION, receiver = player)
        invAddOrDrop(objRepo, FEATHER, random.of(FEATHERS_MIN, FEATHERS_MAX))
        hunt.remove(carcass)
    }

    private suspend fun ProtectedAccess.checkKills() {
        if (!quest.isComplete(player)) {
            mes("You've scratched up no kills yet!")
            mes("You've got to complete the quest first!")
            return
        }
        val kills = player.chompyKills
        mes("You've scratched up a total of $kills chompy bird kills so far!")
        if (kills == 0) {
            mes("You've not even started.")
            return
        }
        mes("~ You're ${ChompyRanks.withArticle(kills)}! ~")
        objbox(
            CHOMPY_DISPLAY,
            OBJBOX_ZOOM,
            "You've killed a total of <col=0000ff>$kills</col> chompy birds so far! " +
                "<col=0000ff>~ You're ${ChompyRanks.withArticle(kills)}! ~</col>",
        )
    }

    /** Records a kill and leaves the carcass standing where it fell, waiting to be plucked. */
    fun shotDown(hero: Player, npc: Npc) {
        if (!hunt.ownsKill(npc, hero)) {
            return
        }
        hunt.leaveCarcass(hero, npc.coords)
        if (quest.stage(hero) == STAGE_GOT_BOW) {
            hunt.withAccess(hero) { quest.advanceTo(this, STAGE_KILLED_CHOMPY) }
            return
        }
        if (!quest.isComplete(hero) || hero.chompyKills >= KILL_CAP) {
            return
        }
        hero.mes("You scratch a notch on your bow for the chompy bird kill.")
        hero.chompyKills += 1
        if (hero.chompyKills == EXPERT_KILLS) {
            hunt.withAccess(hero) {
                statAdvance("stat.ranged", EXPERT_RANGED_XP)
                mes("You've been awarded ranged experience for your relentless pursuit of chompies!")
                objbox(
                    CHOMPY_DISPLAY,
                    OBJBOX_ZOOM,
                    "<col=0000ff>**** Congratulations! $EXPERT_KILLS Chompies! ****</col> " +
                        "<col=800000>~ You're an Expert Dragon Archer! ~</col> This is the highest honour that " +
                        "can be bestowed on any chompy bird hunter.",
                )
            }
        }
    }

    private companion object {
        const val EAT_TOAD_QUEUE = "queue.chompy_eat_toad"
        const val SWALLOW_QUEUE = "queue.chompy_toad_eaten"

        const val APPROACH_CYCLES = 2
        const val SWALLOW_CYCLES = 3
        const val APPROACH_RANGE = 8

        const val DROP_DURATION = 200
        const val FEATHERS_MIN = 10
        const val FEATHERS_MAX = 30

        const val OBJBOX_ZOOM = 250

        /** The tally stops counting here, as it does in OSRS. */
        const val KILL_CAP = 100000
        const val EXPERT_KILLS = 4000
        const val EXPERT_RANGED_XP = 300000.0
    }
}

/**
 * Only the hunter whose bait brought a chompy down may shoot it, and only with the ogre bow Rantz
 * lends out; anything else is too weak to trouble the bird.
 */
class ChompyAttackHook
@Inject
constructor(private val hunt: ChompyHunt, private val quest: BigChompyBirdHuntingQuest) :
    NpcAttackValidateHook {
    private val ogreBow = OGRE_BOW.asRSCM(RSCMType.OBJ)

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!hunt.isChompy(npc)) {
            return NpcAttackValidateResult.Pass
        }
        if (!hunt.ownsKill(npc, player)) {
            return NpcAttackValidateResult.Deny("This is not your chompy bird to shoot.")
        }
        if (quest.stage(player) < STAGE_GOT_BOW) {
            return NpcAttackValidateResult.Deny("Rantz is doing the shooting around here.")
        }
        if (player.worn[Wearpos.RightHand.slot]?.id != ogreBow) {
            return NpcAttackValidateResult.Deny(
                "Your weapon isn't powerful enough to hurt the chompy bird.",
            )
        }
        return NpcAttackValidateResult.Pass
    }
}

/** Killing a chompy leaves the carcass standing where it fell, waiting to be plucked. */
class ChompyKillHook
@Inject
constructor(private val hunt: ChompyHunt, private val chompies: ChompyBirds) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        if (!hunt.isChompy(context.npc)) {
            return
        }
        chompies.shotDown(context.hero, context.npc)
    }
}
