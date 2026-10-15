package org.rsmod.content.quest.area.karamja.legendsquest

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.ARDRIGAL_MIXTURE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL_PURE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BRAVERY_POTION
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.DARK_DAGGER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.ENCHANTED_VIAL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GLOWING_DAGGER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWL_PURE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWL_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HEART_CRYSTAL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HEART_CRYSTAL_GLOWING
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HOLY_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.SKETCH
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.SNAKEWEED_MIXTURE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_ASKED_HOLY_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_FIRE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_GERMINATED_SEEDS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_TALKED_GUJUO_POOL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.YOMMI_SEEDS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.YOMMI_SEEDS_GERMINATED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The quest's own items: the golden bowl forged from two gold bars and the waters it holds, the
 * bravery potion Gujuo describes, and the notes and crystals that only have something to say.
 */
class LegendsItems @Inject constructor(private val legends: LegendsQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLocCategoryU(ANVIL_CATEGORY, GOLD_BAR) { forgeBowl() }

        for ((full, empty) in EMPTYING) {
            onOpHeld4(full) {
                invDel(inv, full, 1)
                invAdd(inv, empty, 1)
                mes(EMPTY_MESSAGES.getValue(full))
            }
        }
        onOpHeldU(BLESSED_BOWL_PURE, EMPTY_VIAL) { decant(enchanted = false) }
        onOpHeldU(BLESSED_BOWL_PURE, ENCHANTED_VIAL) { decant(enchanted = true) }
        onOpHeldU(BLESSED_BOWL_PURE, YOMMI_SEEDS) { germinate() }

        onOpHeldU(VIAL_OF_WATER, SNAKE_WEED) { mix(SNAKE_WEED, SNAKEWEED_MIXTURE, "You mix the snake weed into the vial of water.") }
        onOpHeldU(VIAL_OF_WATER, ARDRIGAL) {
            if (legends.stage(player) < STAGE_TALKED_GUJUO_POOL) {
                mes("You're not really sure what you would make out of this.")
                return@onOpHeldU
            }
            mix(ARDRIGAL, ARDRIGAL_MIXTURE, "You mix the Ardrigal into the water.")
        }
        onOpHeldU(SNAKEWEED_MIXTURE, ARDRIGAL) { brewBravery(SNAKEWEED_MIXTURE, ARDRIGAL) }
        onOpHeldU(ARDRIGAL_MIXTURE, SNAKE_WEED) { brewBravery(ARDRIGAL_MIXTURE, SNAKE_WEED) }
        onOpHeld1(BRAVERY_POTION) { drinkBravery() }

        onOpHeld1(SKETCH) {
            objbox(
                SKETCH,
                "This is a crudely drawn picture of some kind of vessel, it looks vaguely bowl " +
                    "shaped - this is what Gujuo was referring to when he said that you needed to " +
                    "make a vessel made from the metal of the sun.",
            )
        }
        onOpHeld1(YOMMI_SEEDS) {
            objbox(YOMMI_SEEDS, "These seeds look magical. They need to be germinated in pure sacred water.")
        }
        onOpHeld1(YOMMI_SEEDS_GERMINATED) {
            objbox(YOMMI_SEEDS_GERMINATED, "These seeds have been germinated in pure sacred water and are ready to be planted in fertile soil.")
        }
        onOpHeld1(HEART_CRYSTAL) {
            objbox(HEART_CRYSTAL, "As the crystal touches your hands a voice inside of your head says.. <col=ff0000>'Bring life to the dragons eye.'</col>")
        }
        onOpHeld1(HEART_CRYSTAL_GLOWING) {
            objbox(HEART_CRYSTAL_GLOWING, "This crystal seems to glow and has a very mystical aura about it. It seems to be vaguely heart shaped.")
        }
        onOpHeld4(DARK_DAGGER) {
            objbox(DARK_DAGGER, "A black obsidian dagger. You feel a chill as you turn it over in your hands; it seems to have been made for one purpose only.")
        }
        onOpHeld4(GLOWING_DAGGER) {
            objbox(GLOWING_DAGGER, "The dark dagger glows with a strange light. Something seems to be trapped inside it.")
        }
    }

    private suspend fun ProtectedAccess.forgeBowl() {
        arriveDelay()
        if (legends.stage(player) < STAGE_ASKED_HOLY_WATER) {
            mes("Nothing interesting happens.")
            return
        }
        if (player.smithingLvl < REQUIRED_SMITHING) {
            mesbox("You need a Smithing level of at least 50 to work Gold.")
            return
        }
        if (HAMMERS.none { inv.count(it) > 0 }) {
            mesbox("You need a hammer to work the metal with.")
            return
        }
        if (!choice2("Yes", true, "No", false, title = "Would you like to make a golden bowl?")) {
            return
        }
        ifClose()
        if (inv.count(GOLD_BAR) < 2) {
            mesbox("You need two Gold Bars to make a bowl.")
            return
        }
        anim(SMITHING_SEQ)
        soundSynth(ANVIL_SOUND)
        delay(4)
        if (!statRandom(SMITHING, BOWL_LOW, BOWL_HIGH, 0)) {
            mes("You make a mistake forging the bowl.")
            if (random.of(256) < MOLTEN_SPILL_CHANCE) {
                mes("You pour molten gold all over the floor.")
                invDel(inv, GOLD_BAR, 2)
            } else {
                invDel(inv, GOLD_BAR, 1)
            }
            return
        }
        mes("You forge a beautiful bowl out of solid gold.")
        invDel(inv, GOLD_BAR, 2)
        invAdd(inv, GOLD_BOWL, 1)
        statAdvance(SMITHING, BOWL_XP)
    }

    /** Pouring from the blessed bowl; ten measures come out of each fill. */
    private suspend fun ProtectedAccess.decant(enchanted: Boolean) {
        val vial = if (enchanted) ENCHANTED_VIAL else EMPTY_VIAL
        invDel(inv, vial, 1)
        invAdd(inv, if (enchanted) HOLY_WATER else VIAL_OF_WATER, 1)
        mes(
            if (enchanted) {
                "You decant some water from the golden bowl into the enchanted vial."
            } else {
                "You decant some water from the golden bowl into the vial."
            },
        )
        val uses = player.legendsBowlUses
        if (uses >= BOWL_USES - 1) {
            invDel(inv, BLESSED_BOWL_PURE, 1)
            invAdd(inv, BLESSED_BOWL, 1)
            player.legendsBowlUses = 0
            mes("The pure water in the golden bowl has run out...")
            return
        }
        player.legendsBowlUses = uses + 1
        if (!enchanted) {
            mes("The water doesn't seem as effervescent as it was in the bowl.")
        }
    }

    private suspend fun ProtectedAccess.germinate() {
        doubleobjbox(YOMMI_SEEDS, BLESSED_BOWL_PURE, "You place the seeds into the golden bowl of pure sacred water.")
        legends.advanceFrom(this, STAGE_DEFEATED_NEZIKCHENED_FIRE, STAGE_GERMINATED_SEEDS)
        val seeds = inv.count(YOMMI_SEEDS)
        invDel(inv, YOMMI_SEEDS, seeds)
        invAdd(inv, YOMMI_SEEDS_GERMINATED, seeds)
        for (container in listOf(inv, bank)) {
            val bowls = container.count(BLESSED_BOWL_PURE)
            if (bowls > 0) {
                invDel(container, BLESSED_BOWL_PURE, bowls)
                invAdd(container, BLESSED_BOWL, bowls)
            }
        }
        player.legendsBowlUses = 0
        mesbox("You start to see little shoots growing on the seeds. These seeds look as if they're germinated now.")
    }

    private suspend fun ProtectedAccess.mix(herb: String, mixture: String, message: String) {
        anim(MIX_SEQ)
        soundSynth(MIX_SOUND)
        invDel(inv, VIAL_OF_WATER, 1)
        invDel(inv, herb, 1)
        invAdd(inv, mixture, 1)
        mes(message)
        delay(1)
    }

    private suspend fun ProtectedAccess.brewBravery(mixture: String, herb: String) {
        if (legends.stage(player) < STAGE_TALKED_GUJUO_POOL) {
            doubleobjbox(herb, mixture, "You're not sure what mixing these two things together would do. You decide against experimenting.")
            return
        }
        doubleobjbox(herb, mixture, "You mix the two ingredients together")
        anim(MIX_SEQ)
        soundSynth(MIX_SOUND)
        invDel(inv, mixture, 1)
        invDel(inv, herb, 1)
        invAdd(inv, BRAVERY_POTION, 1)
        objbox(
            BRAVERY_POTION,
            "It makes a heady brew. This must be what Gujuo was talking about. You certainly would " +
                "need to be brave to drink this!",
        )
    }

    private suspend fun ProtectedAccess.drinkBravery() {
        objbox(
            BRAVERY_POTION,
            "This bravery potion bubbles with a strange effervescence, but it smells very strange, " +
                "like oily burnt oranges. Are you sure you want to drink it?",
        )
        val drink =
            choice2(
                "Yes, I'll bravely drink the bravery potion.", true,
                "Actually, let me consider this a bit longer first; it smells a bit odd.", false,
                title = "Drink the so-called 'Bravery Potion'?",
            )
        if (!drink) {
            objbox(
                BRAVERY_POTION,
                "You sensibly stopper the vial and place it back in your inventory. You consider that " +
                    "things aren't so desperate yet that you need to rely on strange jungle potions to " +
                    "make you brave.",
            )
            return
        }
        anim(DRINK_SEQ)
        soundSynth(DRINK_SOUND)
        invDel(inv, BRAVERY_POTION, 1)
        invAdd(inv, EMPTY_VIAL, 1)
        player.legendsBravery = true
        objbox(
            BRAVERY_POTION,
            "You bravely swig down the entire contents of the vial and then wait for some sort of " +
                "internal explosion. After a few seconds, you realise that you actually feel quite okay.",
        )
    }

    private companion object {
        const val ANVIL_CATEGORY = "category.anvil"
        const val GOLD_BAR = "obj.gold_bar"
        val HAMMERS = listOf("obj.hammer", "obj.imcando_hammer", "obj.imcando_hammer_offhand")
        const val EMPTY_VIAL = "obj.vial_empty"
        const val VIAL_OF_WATER = "obj.vial_water"
        const val SNAKE_WEED = "obj.snake_weed"
        const val ARDRIGAL = "obj.ardrigal"

        val EMPTYING =
            mapOf(
                GOLD_BOWL_WATER to GOLD_BOWL,
                GOLD_BOWL_PURE to GOLD_BOWL,
                BLESSED_BOWL_WATER to BLESSED_BOWL,
                BLESSED_BOWL_PURE to BLESSED_BOWL,
            )
        val EMPTY_MESSAGES =
            mapOf(
                GOLD_BOWL_WATER to "You empty the plain water out of the golden bowl.",
                GOLD_BOWL_PURE to "You empty the pure water out of the golden bowl.",
                BLESSED_BOWL_WATER to "You empty the plain water out of the blessed golden bowl.",
                BLESSED_BOWL_PURE to "You empty the pure water out of the blessed golden bowl.",
            )

        const val SMITHING = "stat.smithing"
        const val REQUIRED_SMITHING = 50
        const val BOWL_LOW = 31
        const val BOWL_HIGH = 256
        const val MOLTEN_SPILL_CHANCE = 135
        const val BOWL_XP = 30.0
        const val BOWL_USES = 10

        const val SMITHING_SEQ = "seq.human_smithing"
        const val MIX_SEQ = "seq.human_herbing_vial"
        const val DRINK_SEQ = "seq.human_eat"
        const val ANVIL_SOUND = "synth.anvil_4"
        const val MIX_SOUND = "synth.vial_mix"
        const val DRINK_SOUND = "synth.drink"
    }
}
