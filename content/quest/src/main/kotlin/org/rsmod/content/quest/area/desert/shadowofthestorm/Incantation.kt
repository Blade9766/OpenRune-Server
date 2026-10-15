package org.rsmod.content.quest.area.desert.shadowofthestorm

import org.rsmod.game.entity.Player

/**
 * The five words of Agrith-Naar's summoning, and the order this player's copy of it runs in.
 *
 * `varbit.agrith_incantation_1` to `_5` hold the order written in the demonic tome, one word
 * index per varbit. Denath hands the player the same five words in reverse and calls it the
 * summoning, which is why the first ritual unmakes him instead: read backwards, the incantation
 * is a banishment. The second ritual uses the tome's order and summons him for real.
 */
internal object Incantation {
    val WORDS = listOf("Caldar", "Nahudu", "Agrith-Naar", "Camerinthum", "Tarren")

    val LENGTH = WORDS.size

    /** The order written in the tome: the true summoning. */
    fun tomeOrder(player: Player): List<Int> =
        listOf(
            player.incantationWord1,
            player.incantationWord2,
            player.incantationWord3,
            player.incantationWord4,
            player.incantationWord5,
        )

    /** The order Denath dictates, which banishes rather than summons. */
    fun denathOrder(player: Player): List<Int> = tomeOrder(player).reversed()

    /** True once Denath has dictated an order to this player. */
    fun assigned(player: Player): Boolean = tomeOrder(player).toSet().size == LENGTH

    /** Rolls a fresh order for this player. Every player gets their own permutation. */
    fun assign(player: Player, order: List<Int>) {
        require(order.size == LENGTH) { "Incantation must be $LENGTH words long: $order" }
        player.incantationWord1 = order[0]
        player.incantationWord2 = order[1]
        player.incantationWord3 = order[2]
        player.incantationWord4 = order[3]
        player.incantationWord5 = order[4]
    }

    fun words(order: List<Int>): List<String> = order.map(WORDS::get)

    fun render(order: List<Int>): String = words(order).joinToString(", ")
}
