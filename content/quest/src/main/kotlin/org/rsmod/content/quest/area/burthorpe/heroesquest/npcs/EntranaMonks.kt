package org.rsmod.content.quest.area.burthorpe.heroesquest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The monks of Entrana and their High Priest. The monks heal the injured, and while a player is
 * just setting out on the Heroes' Quest both point them towards the Queen of the Ice's gloves for
 * the Entranan Firebird's burning feather.
 */
class EntranaMonks @Inject constructor(private val heroes: HeroesQuest) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(MONK) { startDialogue(it.npc) { monk(it.npc) } }
        onOpNpc1(HIGH_PRIEST) { startDialogue(it.npc) { highPriest() } }
    }

    private suspend fun Dialogue.monk(npc: Npc) {
        chatNpc(neutral, "Greetings traveller.")
        if (heroes.stage(player) == STAGE_STARTED) {
            chatPlayer(quiz, "Hello, I am in search of an Entranan Firebird. Can you help me?")
            chatNpc(
                neutral,
                "Oh ho! Another one! I don't think they exist, but you'd probably ask the head " +
                    "monk about it. If anyone would know, he would!",
            )
            chatPlayer(happy, "Thanks!")
            chatNpc(quiz, "Anything else you wanted to know?")
        }
        val heal =
            choice2(
                "Can you heal me? I'm injured.",
                true,
                "Isn't this place built a bit out of the way?",
                false,
            )
        if (!heal) {
            chatPlayer(quiz, "Isn't this place built a bit out of the way?")
            chatNpc(
                neutral,
                "We like it that way actually! We get disturbed less. We still get rather a large " +
                    "amount of travellers looking for sanctuary and healing here as it is!",
            )
            return
        }
        chatPlayer(quiz, "Can you heal me? I'm injured.")
        chatNpc(neutral, "Ok.")
        access.ifClose()
        npc.anim(HEAL_SEQ)
        access.spotanim(HEAL_SPOTANIM, height = HEAL_HEIGHT)
        access.soundSynth(HEAL_SOUND)
        access.statHeal("stat.hitpoints", HEAL_CONSTANT, HEAL_PERCENT)
        access.mes("You feel a little better.")
    }

    private suspend fun Dialogue.highPriest() {
        chatNpc(happy, "Many greetings. Welcome to our fair island.")
        if (heroes.stage(player) == STAGE_STARTED) {
            chatPlayer(neutral, "Hello, I am in search of an Entranan Firebird. Can you help me?")
            chatNpc(
                neutral,
                "Well adventurer, I have heard that they exist on this island, but have not ever " +
                    "seen one myself.",
            )
            chatNpc(
                neutral,
                "According to legend however, they are so hot that their merest touch can burn " +
                    "flesh.",
            )
            chatNpc(
                neutral,
                "Apparently long ago a great hero used a pair of magical gloves, stolen from the " +
                    "Queen of the Ice to cool the bird down enough to catch as a pet.",
            )
            chatPlayer(quiz, "So where would I find this 'Queen of the Ice' then?")
            chatNpc(
                neutral,
                "I know not adventurer, it was just a myth I was told as a young boy. Perhaps " +
                    "somewhere icy?",
            )
            chatPlayer(happy, "Thanks!")
            return
        }
        chatNpc(happy, "Enjoy your stay here. May it be spiritually uplifting!")
    }

    private companion object {
        const val MONK = "npc.entrana_monk"
        const val HIGH_PRIEST = "npc.high_priest_of_entrana"

        const val HEAL_SEQ = "seq.human_castheal"
        const val HEAL_SPOTANIM = "spotanim.heal_casting"
        const val HEAL_HEIGHT = 120
        const val HEAL_SOUND = "synth.heal"
        const val HEAL_CONSTANT = 2
        const val HEAL_PERCENT = 20
    }
}
