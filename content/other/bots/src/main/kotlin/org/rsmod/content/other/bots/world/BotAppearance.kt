package org.rsmod.content.other.bots.world

import kotlin.random.Random
import org.rsmod.content.other.bots.core.SavedAppearance
import org.rsmod.content.other.cheatmenu.AppearanceColour
import org.rsmod.content.other.cheatmenu.AppearancePart
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.Appearance

/** Gives each bot its own look, seeded by its id so it stays the same across restarts. */
object BotAppearance {
    private const val BEARDLESS_CHANCE = 3
    private const val THEY_CHANCE = 10

    fun randomize(player: Player, botId: Long) = randomize(player, Random(botId), bodyType = null)

    /** A random look; [bodyType] keeps the current one, or null picks one at random too. */
    fun randomize(player: Player, random: Random, bodyType: Int?) {
        val appearance = player.appearance
        val type = bodyType ?: if (random.nextBoolean()) Appearance.BODY_TYPE_A else Appearance.BODY_TYPE_B
        randomizeAs(player, random, type)
    }

    fun snapshot(player: Player): SavedAppearance {
        val appearance = player.appearance
        return SavedAppearance(
            bodyType = appearance.bodyType,
            pronoun = appearance.pronoun,
            kits = appearance.identKitSnapshot().map { it.toInt() },
            colours = appearance.coloursSnapshot().map { it.toInt() },
        )
    }

    /**
     * Puts a saved look on [player], checking every value against the cache's style lists; a style
     * or colour that does not fit is replaced by the body type's default, so a hand-edited save can
     * never send the client an invalid model.
     */
    fun apply(player: Player, saved: SavedAppearance) {
        val appearance = player.appearance
        val bodyType = if (saved.bodyType == Appearance.BODY_TYPE_B) Appearance.BODY_TYPE_B else Appearance.BODY_TYPE_A
        appearance.bodyType = bodyType
        appearance.pronoun = saved.pronoun.takeIf { it in Appearance.PRONOUN_HE..Appearance.PRONOUN_THEY }
            ?: if (bodyType == Appearance.BODY_TYPE_A) Appearance.PRONOUN_HE else Appearance.PRONOUN_SHE
        for (part in AppearancePart.entries) {
            val style = saved.kits.getOrNull(part.slot) ?: Appearance.NO_IDENT_KIT
            appearance.setIdentKit(part.slot, if (part.accepts(style, bodyType)) style else part.defaultFor(bodyType))
        }
        for (colour in AppearanceColour.entries) {
            val value = saved.colours.getOrNull(colour.index) ?: 0
            appearance.setColour(colour.index, if (value in 0 until colour.paletteSize) value else 0)
        }
    }

    private fun randomizeAs(player: Player, random: Random, bodyType: Int) {
        val appearance = player.appearance
        appearance.bodyType = bodyType
        appearance.pronoun =
            when {
                random.nextInt(THEY_CHANCE) == 0 -> Appearance.PRONOUN_THEY
                bodyType == Appearance.BODY_TYPE_A -> Appearance.PRONOUN_HE
                else -> Appearance.PRONOUN_SHE
            }
        for (part in AppearancePart.entries) {
            val beardless = part.optional && random.nextInt(BEARDLESS_CHANCE) == 0
            val style = if (beardless) Appearance.NO_IDENT_KIT else part.classicStylesFor(bodyType).random(random)
            appearance.setIdentKit(part.slot, style)
        }
        for (colour in AppearanceColour.entries) {
            appearance.setColour(colour.index, random.nextInt(colour.paletteSize))
        }
    }
}
