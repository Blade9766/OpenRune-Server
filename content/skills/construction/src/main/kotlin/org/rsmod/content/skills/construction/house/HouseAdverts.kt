package org.rsmod.content.skills.construction.house

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.skills.construction.data.HouseLocation
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

/**
 * The houses listed on the house advertisement boards, server-wide.
 *
 * As the wiki has it, an owner of level 50 or more lists their house for 30 minutes, or until they
 * log out, and may only list it once every 30 minutes. Every cycle someone is in an unlocked
 * listed house marks it active ([markActive]); once it has stood empty, or locked, for 15 minutes
 * its listing comes down the next time a board is read. The wiki's warnings at three, two and one
 * minutes before that are not given.
 */
@Singleton
class HouseAdverts
@Inject
constructor(
    private val store: HouseStore,
    private val clock: MapClock,
) {
    class Advert(val owner: Player, val since: Int, var lastHome: Int)

    /** What a listed house has, as the board's columns show it. */
    data class Features(
        val gildedAltar: Boolean,
        val nexus: Int,
        val jewelleryBox: Int,
        val pool: Int,
        val spellbookAltar: Int,
        val armourStand: Boolean,
    )

    private val listed = LinkedHashMap<Long, Advert>()
    private val lastListed = HashMap<Long, Int>()

    fun isListed(player: Player): Boolean = player.key in listed

    /** Lists [owner]'s house, or says why it can't be. */
    fun add(owner: Player): String? {
        val now = clock.cycle
        val last = lastListed[owner.key]
        return when {
            !store.state(owner).owned -> "You don't have a house to advertise."
            owner.constructionLvl < MIN_LEVEL -> "You need a Construction level of $MIN_LEVEL to advertise your house."
            isListed(owner) -> "Your house is already being advertised."
            last != null && now - last < LIFETIME -> "You can only advertise your house once every 30 minutes."
            else -> {
                listed[owner.key] = Advert(owner, now, now)
                lastListed[owner.key] = now
                VarPlayerIntMapSetter.set(owner, ADVERTISING_VARBIT, 1)
                null
            }
        }
    }

    /** Someone is in [owner]'s house this cycle, which keeps its listing alive unless it is locked. */
    fun markActive(owner: Player) {
        val advert = listed[owner.key] ?: return
        if (owner.vars[HouseVisitors.LOCKED_VARBIT] == 0) {
            advert.lastHome = clock.cycle
        }
    }

    fun remove(owner: Player): Boolean {
        val removed = listed.remove(owner.key) != null
        VarPlayerIntMapSetter.set(owner, ADVERTISING_VARBIT, 0)
        return removed
    }

    /** The listings still standing, after taking down any that have run out or gone quiet. */
    fun current(): List<Advert> {
        val now = clock.cycle
        val iterator = listed.values.iterator()
        while (iterator.hasNext()) {
            val advert = iterator.next()
            val why =
                when {
                    now - advert.since >= LIFETIME -> "Your house advertisement has run out."
                    now - advert.lastHome >= INACTIVE ->
                        "Your house advertisement has been taken down, as your house has been empty or locked."
                    else -> null
                } ?: continue
            iterator.remove()
            VarPlayerIntMapSetter.set(advert.owner, ADVERTISING_VARBIT, 0)
            advert.owner.mes(why)
        }
        return listed.values.toList()
    }

    /** The board's line for [advert]: name, town, level, then the six feature columns. */
    fun line(advert: Advert): String {
        val owner = advert.owner
        val state = store.state(owner)
        return line(owner.displayName, state.location, owner.constructionLvl, features(state))
    }

    companion object {
        const val MIN_LEVEL: Int = 50

        /** 30 minutes, in cycles. */
        const val LIFETIME: Int = 3000

        /** 15 minutes, in cycles. */
        const val INACTIVE: Int = 1500

        const val ADVERTISING_VARBIT: String = "varbit.poh_board_advertising"

        private val Player.key: Long
            get() = requireNotNull(uuid) { "Player has no uuid: $this" }

        fun line(name: String, location: HouseLocation, level: Int, features: Features): String =
            listOf(
                    name,
                    location.boardId.toString(),
                    level.toString(),
                    yesNo(features.gildedAltar),
                    features.nexus.toString(),
                    features.jewelleryBox.toString(),
                    features.pool.toString(),
                    features.spellbookAltar.toString(),
                    yesNo(features.armourStand),
                )
                .joinToString("|")

        /**
         * Reads the board's columns off the furniture built in [state]: the gilded altar, the tier
         * of nexus (1-3), jewellery box (1-3) and superior garden pool (1-5), the spellbook altar
         * (ancient 1, lunar 2, dark 3, occult 4) and the armour stand.
         */
        fun features(state: HouseState): Features {
            fun built(type: RoomType, key: String): List<Int> =
                state.rooms.values.filter { it.type == type }.mapNotNull { it.furniture[key] }

            fun label(type: RoomType, key: String, option: Int): String? =
                type.hotspot(key)?.options?.getOrNull(option)?.label

            val altar = built(RoomType.CHAPEL, "altar").any { label(RoomType.CHAPEL, "altar", it) == "Gilded altar" }
            val spellbook =
                built(RoomType.ACHIEVEMENT_GALLERY, "altar").maxOfOrNull { option ->
                    if (label(RoomType.ACHIEVEMENT_GALLERY, "altar", option) == "Occult altar") 4 else option + 1
                } ?: 0
            val armour = built(RoomType.WORKSHOP, "repair").any { label(RoomType.WORKSHOP, "repair", it) == "Armour stand" }
            return Features(
                gildedAltar = altar,
                nexus = tier(built(RoomType.PORTAL_NEXUS, "nexus")),
                jewelleryBox = tier(built(RoomType.ACHIEVEMENT_GALLERY, "jewellery_box")),
                pool = tier(built(RoomType.SUPERIOR_GARDEN, "pool")),
                spellbookAltar = spellbook,
                armourStand = armour,
            )
        }

        private fun tier(options: List<Int>): Int = options.maxOfOrNull { it + 1 } ?: 0

        private fun yesNo(value: Boolean): String = if (value) "Y" else "N"
    }
}
