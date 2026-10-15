package org.rsmod.content.quest.area.burthorpe.heroesquest.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_MANSION
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_REPORTED
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.GRIP_KEYS
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.ID_PAPERS
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.MISC_KEY
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.PHOENIX_CHARLIE
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.PHOENIX_KILLED_GRIP
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal const val GRIP = "npc.grip"
private const val ATTACK_WARNING_QUEUE = "queue.hero_grip_attack_warning"

/**
 * Grip, Scarface Pete's head guard, who keeps the treasure room keys in his jacket. He takes on
 * the Black Arm player as his deputy "Hartigen" and hands them a miscellaneous key; only a Phoenix
 * player who has found the mansion's side entrance may attack him.
 */
class Grip @Inject constructor(private val heroes: HeroesQuest) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(GRIP) { startDialogue(it.npc) { grip() } }
        onPlayerQueue(ATTACK_WARNING_QUEUE) {
            startDialogue {
                chatPlayer(
                    neutral,
                    "I can't attack the head guard here! There are too many witnesses around to " +
                        "see me do it! I'd have the whole of Brimhaven after me! Besides, if he " +
                        "dies I want the promotion!",
                )
                mesbox("Perhaps you need another player's help...?")
            }
        }
    }

    private suspend fun Dialogue.grip() {
        if (!heroes.blackArmAt(player, BLACKARM_MANSION)) {
            chatPlayer(neutral, "Hello.")
            chatNpc(angry, "Hello yourself. Shouldn't you be somewhere else?")
            return
        }
        if (heroes.stage(player) == BLACKARM_MANSION && !report()) {
            return
        }
        duties()
    }

    private suspend fun Dialogue.report(): Boolean {
        chatPlayer(neutral, "Hi there. I am Hartigen, reporting for duty as your new deputy sir!")
        chatNpc(neutral, "Ah good, at last. You took your time getting here! Now let me see...")
        chatNpc(
            neutral,
            "I'll get your hours and duty roster sorted out in a while. Oh, and do you have your " +
                "I.D. papers with you? Internal security is almost as important as external " +
                "security for a guard.",
        )
        if (access.inv.count(ID_PAPERS) == 0) {
            chatPlayer(sad, "Oh dear... I don't have that with me anymore..")
            chatNpc(angry, "Well that's no good! Go get them immediately, then report back for duty.")
            return false
        }
        chatPlayer(neutral, "Right here sir!")
        access.invDel(access.inv, ID_PAPERS)
        mesbox("You hand the ID papers over to Grip.")
        heroes.setStage(access, BLACKARM_REPORTED)
        return true
    }

    private suspend fun Dialogue.duties() {
        var askedDuties = false
        var askedTreasure = false
        while (true) {
            val topic =
                when {
                    askedDuties ->
                        choice3(
                            "So can I guard the treasure room please?",
                            Topic.Treasure,
                            "Well, I'd better sort my new room out.",
                            Topic.Leave,
                            "Anything I can do now?",
                            Topic.Task,
                        )
                    askedTreasure ->
                        choice2(
                            "So what do my duties involve?",
                            Topic.Duties,
                            "Well, I'd better sort my new room out.",
                            Topic.Leave,
                        )
                    else ->
                        choice3(
                            "So can I guard the treasure room please?",
                            Topic.Treasure,
                            "So what do my duties involve?",
                            Topic.Duties,
                            "Well, I'd better sort my new room out.",
                            Topic.Leave,
                        )
                }
            when (topic) {
                Topic.Treasure -> {
                    chatPlayer(quiz, "So can I guard the treasure room please?")
                    chatNpc(
                        neutral,
                        "Well, I might post you outside it sometimes. I prefer to be the only one " +
                            "allowed inside however.",
                    )
                    chatNpc(
                        neutral,
                        "There's some pretty valuable artefacts in there! Those keys stay ONLY " +
                            "with the head guard and Scarface Pete.",
                    )
                    askedTreasure = true
                    askedDuties = false
                }
                Topic.Duties -> {
                    chatPlayer(quiz, "So what do my duties involve?")
                    chatNpc(
                        neutral,
                        "You'll have various guard related duties on various shifts. I'll assign " +
                            "specific duties as they are required as and when they become " +
                            "necessary. Just so you know, if anything happens to me",
                    )
                    chatNpc(
                        neutral,
                        "you'll need to take over as head guard here. You'll find important keys " +
                            "to the treasure room and Pete's quarters inside my jacket - although " +
                            "I doubt anything bad's going to happen to",
                    )
                    chatNpc(laugh, "me anytime soon!")
                    mesbox("Grip laughs to himself at the thought.")
                    askedDuties = true
                    askedTreasure = false
                }
                Topic.Task -> {
                    chatPlayer(quiz, "Anything I can do now?")
                    if (heroes.owns(access, MISC_KEY)) {
                        chatNpc(neutral, "Can't think of anything right now.")
                        return
                    }
                    chatNpc(
                        neutral,
                        "Hmm. Well, you could find out what this key opens for me. Apparently " +
                            "it's for something in this building, but for the life of me I can't " +
                            "find what.",
                    )
                    access.invAdd(access.inv, MISC_KEY)
                    objbox(MISC_KEY, "Grip hands you a key.")
                    return
                }
                Topic.Leave -> {
                    chatPlayer(neutral, "Well, I'd better sort my new room out.")
                    chatNpc(
                        neutral,
                        "Yeah, I'll give you time to settle in. Better get a good nights sleep, I " +
                            "expect you to report for duty at oh five hundred hours tomorrow on " +
                            "the dot!",
                    )
                    return
                }
            }
        }
    }

    private enum class Topic {
        Treasure,
        Duties,
        Task,
        Leave,
    }
}

/**
 * Grip can only be attacked by a Phoenix player who has been through Charlie's secret door; to
 * anyone else he is a head guard with a room full of witnesses.
 */
class GripAttackHook @Inject constructor(private val heroes: HeroesQuest) : NpcAttackValidateHook {
    private val gripId by lazy { GRIP.asRSCM(RSCMType.NPC) }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id != gripId || heroes.phoenixAt(player, PHOENIX_CHARLIE)) {
            return NpcAttackValidateResult.Pass
        }
        if (ATTACK_WARNING_QUEUE !in player.queueList) {
            player.queue(ATTACK_WARNING_QUEUE, 1)
        }
        return NpcAttackValidateResult.Deny("")
    }
}

/**
 * Grip's keyring falls where he dies for his deputy to pick up, and the Phoenix player who shot
 * him has done their part of the heist.
 */
class GripKillHook
@Inject
constructor(private val heroes: HeroesQuest, private val objRepo: ObjRepository) : NpcDeathKillHook {
    private val gripId by lazy { GRIP.asRSCM(RSCMType.NPC) }

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id != gripId) {
            return
        }
        objRepo.add(GRIP_KEYS, context.npc.coords, KEYRING_TICKS)
        val hero = context.hero
        if (heroes.isPhoenix(hero) && heroes.stage(hero) == PHOENIX_CHARLIE) {
            heroes.jumpTo(hero, PHOENIX_KILLED_GRIP)
        }
    }

    private companion object {
        const val KEYRING_TICKS = 200
    }
}
