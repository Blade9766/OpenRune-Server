package org.rsmod.content.quest.area.falador.knightssword.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.stat.baseSmithingLvl
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.BLURITE_ORE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.BLURITE_SWORD
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.IRON_BAR
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.IRON_BARS_NEEDED
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.PORTRAIT
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.REDBERRY_PIE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_DESIGN_SHOWN
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_PICTURE_NEEDED
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_PIE_GIVEN
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_PORTRAIT_LOCATED
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_RELDO
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.THURGO
import org.rsmod.content.quest.manager.menu
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Thurgo, the last Imcando dwarf, in his hut on the coast south of Port Sarim. A redberry pie wins
 * him over; once he has seen the portrait he forges the blurite sword from one blurite ore and two
 * iron bars, which are only taken once the sword is ready. After the quest he forges more on
 * request and sells the Smithing skillcape.
 */
class Thurgo @Inject constructor(private val ks: KnightsSwordQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(THURGO) { startDialogue(it.npc) { thurgo() } }
    }

    private suspend fun Dialogue.thurgo() {
        val stage = ks.stage(player)
        val quest =
            when (stage) {
                STAGE_RELDO -> if (REDBERRY_PIE in access.inv) Topic.GivePie else Topic.AskWithoutPie
                STAGE_PIE_GIVEN -> Topic.SpecialSword
                in STAGE_PICTURE_NEEDED..STAGE_DESIGN_SHOWN -> Topic.AboutSword
                STAGE_COMPLETE -> Topic.AnotherSword
                else -> null
            }
        val options = buildList {
            quest?.let { add(it.option to it) }
            if (stage >= STAGE_COMPLETE && REDBERRY_PIE in access.inv) {
                add(Topic.SparePie.option to Topic.SparePie)
            }
            if (player.baseSmithingLvl >= MAX_LEVEL) {
                add(Topic.Skillcape.option to Topic.Skillcape)
            } else {
                add(Topic.Cape.option to Topic.Cape)
            }
            add(Topic.Leave.option to Topic.Leave)
        }
        when (menu(options)) {
            Topic.GivePie -> givePie()
            Topic.AskWithoutPie -> askWithoutPie()
            Topic.SpecialSword -> specialSword()
            Topic.AboutSword -> aboutSword()
            Topic.AnotherSword -> anotherSword()
            Topic.SparePie -> sparePie()
            Topic.Skillcape -> skillcape()
            Topic.Cape -> cape()
            Topic.Leave -> {
                chatPlayer(neutral, Topic.Leave.option)
                chatNpc(neutral, "Hmph. Mind the crabs on your way out.")
            }
        }
    }

    private suspend fun Dialogue.askWithoutPie() {
        chatPlayer(quiz, Topic.AskWithoutPie.option)
        chatNpc(angry, "Maybe I am, maybe I'm not. Who's asking, and why are they tramping sand into my hut?")
        chatPlayer(neutral, "A friend of mine needs a special sword made.")
        chatNpc(angry, "Then your friend can go and find a smith who likes visitors. I'm busy.")
        mesbox("Thurgo turns his back on you. Reldo did say the Imcando were partial to redberry pie...")
    }

    private suspend fun Dialogue.givePie() {
        chatPlayer(happy, Topic.GivePie.option)
        mesbox("Thurgo's eyes go wide. His beard twitches.")
        chatNpc(happy, "Redberry? A real redberry pie? Nobody's brought me one of those in... well. Ever.")
        if (ks.stage(player) != STAGE_RELDO || access.invDel(access.inv, REDBERRY_PIE, 1).failure) {
            return
        }
        ks.advanceTo(access, STAGE_PIE_GIVEN)
        objbox(REDBERRY_PIE, zoom = 400, "You hand over the pie. Thurgo eats it in four enormous bites, then pats his stomach.")
        chatNpc(happy, "By the anvils of my fathers, THAT was a pie! Anyone who brings a pie like that can't be all bad.")
        chatNpc(quiz, "Right then. I'm listening. What was it you wanted?")
    }

    private suspend fun Dialogue.specialSword() {
        chatPlayer(quiz, Topic.SpecialSword.option)
        chatNpc(quiz, "Special how? I don't do daggers for pirates, and I don't do anything pink.")
        chatPlayer(neutral, "It's a copy of an old Imcando sword. A squire lost it, and his knight mustn't find out.")
        chatNpc(laugh, "Ha! Lost a family heirloom, has he? Humans. All right, I'll do it - for the pie.")
        chatNpc(neutral, "But I can't copy a sword I've never seen. Bring me a picture of it, and get the details right.")
        ks.advanceTo(access, STAGE_PICTURE_NEEDED)
    }

    private suspend fun Dialogue.aboutSword() {
        chatPlayer(neutral, Topic.AboutSword.option)
        when (ks.stage(player)) {
            STAGE_PICTURE_NEEDED, STAGE_PORTRAIT_LOCATED ->
                if (PORTRAIT in access.inv) showPortrait() else needPicture()
            else -> materials()
        }
    }

    private suspend fun Dialogue.needPicture() {
        chatNpc(neutral, "Where's my picture? I'm good, but I can't forge from 'sort of long, with a pointy end'.")
    }

    private suspend fun Dialogue.showPortrait() {
        chatPlayer(happy, "I have a portrait of the knight's father holding the sword.")
        objbox(PORTRAIT, zoom = 600, "You show Thurgo the portrait.")
        chatNpc(quiz, "Let me see... ah. Ahh! That's Imcando work all right. See the pale blue in the blade? That's blurite, that is.")
        chatNpc(neutral, "Nobody but us ever bothered with it. Soft stuff, but it takes an edge like nothing else when you fold it with iron.")
        ks.advanceTo(access, STAGE_DESIGN_SHOWN)
        chatNpc(neutral, "I've got the design in my head now. Bring me one blurite ore and two iron bars and I'll make your sword.")
        chatPlayer(quiz, "Where do I find blurite?")
        chatNpc(worried, "In the ice caves, through the trapdoor east of here. You'll want a pickaxe, and to keep your wits about you.")
        chatNpc(worried, "Ice warriors and ice giants wander down there. They don't like visitors either. Stick to the southern wall and don't stop to chat.")
        chatNpc(neutral, "Oh, and take that picture back before anyone misses it. I've seen all I need.")
    }

    private suspend fun Dialogue.materials() {
        val sword = BLURITE_SWORD in access.inv
        if (sword && ks.hasForgedSword(player) && !hasMaterials()) {
            chatNpc(neutral, "You've got the sword. What are you still doing here? Go and save your squire's neck.")
            return
        }
        if (sword && hasMaterials()) {
            chatNpc(quiz, "You've already got a sword, and you've brought enough for another. Want me to make a spare?")
            if (!choice2("Yes, please.", true, "No, one is enough.", false)) {
                chatPlayer(neutral, "No, one is enough.")
                return
            }
            chatPlayer(happy, "Yes, please.")
            forge()
            return
        }
        if (!hasMaterials()) {
            missingMaterials()
            return
        }
        chatNpc(happy, "Blurite and iron! Stand back and watch a real smith at work.")
        if (!forge()) {
            return
        }
        chatNpc(happy, "There. Light as a feather and twice as sharp. Off you go to that squire of yours.")
    }

    private suspend fun Dialogue.missingMaterials() {
        val ore = access.inv.count(BLURITE_ORE) >= 1
        val bars = access.inv.count(IRON_BAR) >= IRON_BARS_NEEDED
        val missing =
            when {
                !ore && !bars -> "one blurite ore and two iron bars"
                !ore -> "a blurite ore"
                else -> "two iron bars, and I mean two"
            }
        chatNpc(neutral, "I still need $missing. No materials, no sword.")
        if (!ore) {
            chatNpc(neutral, "The blurite's in the ice caves through the trapdoor east of here. Bring a pickaxe.")
        }
    }

    private suspend fun Dialogue.anotherSword() {
        chatPlayer(happy, Topic.AnotherSword.option)
        chatNpc(quiz, "Another one? Somebody's careless. Fine: one blurite ore and two iron bars, same as before.")
        if (!hasMaterials()) {
            chatPlayer(happy, "Okay, I'll go and find them.")
            return
        }
        forge()
    }

    private fun Dialogue.hasMaterials(): Boolean =
        access.inv.count(BLURITE_ORE) >= 1 && access.inv.count(IRON_BAR) >= IRON_BARS_NEEDED

    /**
     * Takes the ore and bars and hands over the sword. The swap frees two slots, so it can't fail on
     * a full pack; the ore is put back if the bars can't be taken, so nothing is ever lost.
     */
    private suspend fun Dialogue.forge(): Boolean {
        val inv = access.inv
        if (!hasMaterials()) {
            missingMaterials()
            return false
        }
        doubleobjbox(BLURITE_ORE, IRON_BAR, "You hand Thurgo the blurite ore and two iron bars.")
        if (access.invDel(inv, BLURITE_ORE, 1).failure) {
            return false
        }
        if (access.invDel(inv, IRON_BAR, IRON_BARS_NEEDED).failure) {
            access.invAdd(inv, BLURITE_ORE, 1)
            return false
        }
        npc?.let { thurgo ->
            thurgo.lockFacing(ANVIL)
            thurgo.anim(SMITHING_SEQ)
        }
        access.soundSynth(ANVIL_SOUND)
        delay(SMITHING_TICKS)
        npc?.anim(SMITHING_SEQ)
        access.soundSynth(ANVIL_SOUND)
        delay(SMITHING_TICKS)
        npc?.clearFacingLock()
        access.invAdd(inv, BLURITE_SWORD, 1)
        ks.markSwordForged(player)
        objbox(BLURITE_SWORD, zoom = 400, "Thurgo hammers the blurite into the iron and hands you a gleaming blurite sword.")
        return true
    }

    private suspend fun Dialogue.sparePie() {
        chatPlayer(happy, Topic.SparePie.option)
        mesbox("You see Thurgo's eyes light up.")
        chatNpc(happy, "I'd never say no to a redberry pie! We Imcando live on the things.")
        if (access.invDel(access.inv, REDBERRY_PIE, 1).failure) {
            return
        }
        objbox(REDBERRY_PIE, zoom = 400, "You hand over the pie. Thurgo eats the pie. Thurgo pats his stomach.")
        chatNpc(happy, "Delicious. You're always welcome here, friend.")
    }

    private suspend fun Dialogue.cape() {
        chatPlayer(quiz, Topic.Cape.option)
        chatNpc(
            happy,
            "It's a Skillcape of Smithing. It shows that I'm a master blacksmith, but that's only " +
                "to be expected - after all, my ancestors were the greatest blacksmiths in " +
                "dwarven history.",
        )
        chatNpc(
            happy,
            "If you ever achieve level 99 Smithing you'll be able to wear a cape like this, and " +
                "receive more experience when smelting gold ore.",
        )
    }

    private suspend fun Dialogue.skillcape() {
        chatPlayer(quiz, Topic.Skillcape.option)
        chatNpc(neutral, "What do you need, human?")
        chatPlayer(
            quiz,
            "Now that I am so skilled at Smithing, can I buy a Skillcape of Smithing from you?",
        )
        chatNpc(
            happy,
            "I reckon so; we master smiths must stick together! I'll give it to you for just " +
                "99000 coins.",
        )
        if (!choice2("Sorry, that's too much money.", false, "Of course I'll pay you.", true)) {
            chatPlayer(sad, "Sorry, that's too much money.")
            chatNpc(
                neutral,
                "Too much money? A smith of your calibre should be able to make that amount in " +
                    "an hour!",
            )
            return
        }
        chatPlayer(happy, "Of course I'll pay you.")
        val inv = access.inv
        if (inv.count(COINS) < CAPE_PRICE) {
            chatPlayer(sad, "But, unfortunately, I don't have enough money with me.")
            return
        }
        if (inv.freeSpace() < 2) {
            chatNpc(
                neutral,
                "All Skillcapes come with a free hood. It's part of a deal: buy one get one " +
                    "free, you know. So you'll need to free up some inventory space before I can " +
                    "sell you one.",
            )
            return
        }
        if (access.invDel(inv, COINS, count = CAPE_PRICE).failure) {
            return
        }
        access.invAdd(inv, "obj.skillcape_smithing")
        access.invAdd(inv, "obj.skillcape_smithing_hood")
        chatNpc(happy, "Excellent! Wear that cape with pride my friend.")
    }

    private enum class Topic(val option: String) {
        GivePie("Would you like a redberry pie?"),
        AskWithoutPie("Are you an Imcando dwarf? I need a special sword."),
        SpecialSword("Can you make a special sword for me?"),
        AboutSword("About that sword..."),
        AnotherSword("Can you make me another of Sir Vyvin's swords?"),
        SparePie("Would you like a redberry pie?"),
        Skillcape("Can we talk about skillcapes?"),
        Cape("What is that cape you're wearing?"),
        Leave("I'll leave you to your work."),
    }

    private companion object {
        const val MAX_LEVEL = 99
        const val CAPE_PRICE = 99_000
        const val COINS = "obj.coins"
        const val SMITHING_SEQ = "seq.human_smithing"
        const val ANVIL_SOUND = "synth.anvil_4"
        const val SMITHING_TICKS = 2

        val ANVIL = CoordGrid(2999, 3144, 0)
    }
}
