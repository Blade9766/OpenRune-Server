package org.rsmod.content.other.bots.world

import kotlin.random.Random
import org.rsmod.content.other.cheatmenu.AppearanceColour
import org.rsmod.content.other.cheatmenu.AppearancePart
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.Appearance

/** Gives each bot its own look, seeded by its id so it stays the same across restarts. */
object BotAppearance {
    private const val BEARDLESS_CHANCE = 3
    private const val THEY_CHANCE = 10

    fun randomize(player: Player, botId: Long) {
        val random = Random(botId)
        val appearance = player.appearance
        val bodyType = if (random.nextBoolean()) Appearance.BODY_TYPE_A else Appearance.BODY_TYPE_B
        appearance.bodyType = bodyType
        appearance.pronoun =
            when {
                random.nextInt(THEY_CHANCE) == 0 -> Appearance.PRONOUN_THEY
                bodyType == Appearance.BODY_TYPE_A -> Appearance.PRONOUN_HE
                else -> Appearance.PRONOUN_SHE
            }
        for (part in AppearancePart.entries) {
            val beardless = part.optional && random.nextInt(BEARDLESS_CHANCE) == 0
            val style = if (beardless) Appearance.NO_IDENT_KIT else part.stylesFor(bodyType).random(random)
            appearance.setIdentKit(part.slot, style)
        }
        for (colour in AppearanceColour.entries) {
            appearance.setColour(colour.index, random.nextInt(colour.paletteSize))
        }
    }
}
