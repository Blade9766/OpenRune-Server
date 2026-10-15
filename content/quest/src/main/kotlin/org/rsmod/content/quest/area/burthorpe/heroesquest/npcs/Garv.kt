package org.rsmod.content.quest.area.burthorpe.heroesquest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_MANSION
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_PAPERS
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACK_ARMOUR
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.ID_PAPERS
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal const val GARV = "npc.garv"

/**
 * Garv, who guards the front door of Scarface Pete's mansion. He lets in the Black Arm player
 * posing as Hartigen, the Black Knight deputy, once they turn up in black armour with the papers.
 */
class Garv @Inject constructor(private val heroes: HeroesQuest) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(GARV) { startDialogue(it.npc) { garv() } }
    }

    private suspend fun Dialogue.garv() {
        chatNpc(neutral, "Hello. What do you want?")
        if (introduceAsHartigen(heroes)) {
            return
        }
        val enter =
            if (heroes.isComplete(player)) {
                false
            } else {
                choice2("Can I go in there?", true, "I want for nothing!", false)
            }
        if (!enter) {
            chatPlayer(neutral, "I want for nothing!")
            chatNpc(neutral, "You're one of a very lucky few then.")
            return
        }
        chatPlayer(quiz, "Can I go in there?")
        if (heroes.blackArmAt(player, BLACKARM_MANSION)) {
            chatNpc(neutral, "Of course you can. You work here now, don't you?")
        } else {
            chatNpc(neutral, "No. In there is private.")
        }
    }
}

/** Garv's answer to a player trying the front door; true when he lets them in. */
internal suspend fun Dialogue.garvAtDoor(heroes: HeroesQuest): Boolean = introduceAsHartigen(heroes)

/**
 * The Black Arm impostor's introduction. Garv only believes it from someone dressed like a Black
 * Knight and carrying Hartigen's papers, and it only comes up while that is the next step.
 */
private suspend fun Dialogue.introduceAsHartigen(heroes: HeroesQuest): Boolean {
    if (heroes.stage(player) != BLACKARM_PAPERS || !heroes.isBlackArm(player)) {
        return false
    }
    chatPlayer(neutral, "Hi. I'm Hartigen. I've come to work here.")
    if (!BLACK_ARMOUR.all { access.worn.count(it) > 0 }) {
        chatNpc(angry, "Hartigen the Black Knight? I don't think so. He doesn't dress like that.")
        return false
    }
    chatNpc(neutral, "I assume you have your I.D. papers then?")
    if (access.inv.count(ID_PAPERS) == 0) {
        chatPlayer(
            shifty,
            "Uh... yeeeeaaaaah.... about that.... I must have left them in my other suit of " +
                "armour....",
        )
        return false
    }
    chatNpc(neutral, "You'd better come in then. Grip will want to talk to you.")
    heroes.setStage(access, BLACKARM_MANSION)
    return true
}
