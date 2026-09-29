package org.rsmod.content.skills.hunter.guild

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.wildernessLevel
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.table.QuetzalRow
import org.rsmod.content.skills.hunter.rumours.RumourTracker
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.utils.bits.getBits
import org.rsmod.utils.bits.withBits

/**
 * Quetzal whistles from the Hunter Guild: Soar Leader Pitri hands out blueprints and recharges
 * whistles with raw hunter meat, blueprints are crafted into whistles, and a whistle's Signal
 * opens the Quetzal Transport System map. The map's icons are created client-side from
 * `dbtable.quetzal` rows with a coord, so a button comsub indexes [sites].
 */
class QuetzalWhistleScript
@Inject
constructor(private val rumours: RumourTracker, private val areaChecker: AreaChecker) :
    PluginScript() {
    private val sites: List<QuetzalRow> by lazy { QuetzalRow.all() }

    override fun ScriptContext.startup() {
        onOpNpc1(PITRI) { talkToPitri(it.npc) }
        onOpNpc3(PITRI) { recharge() }

        for (blueprint in Blueprint.entries) {
            onOpHeld1(blueprint.obj) { mes(blueprint.checkText) }
            onOpHeld2(blueprint.obj) { craft(blueprint) }
        }
        for (whistle in Whistle.entries) {
            onOpHeld1(whistle.obj) { signal(it.slot) }
            onOpHeld3(whistle.obj) { rumour() }
            onOpHeld4(whistle.obj) { lastDestination(it.slot) }
            if (whistle.capacity != UNLIMITED_CHARGES) {
                onOpHeld2(whistle.obj) { check(it.obj) }
            }
        }
        onIfModalButton(MAP_ICONS) { travel(it.comsub) }
    }

    private suspend fun ProtectedAccess.talkToPitri(npc: Npc) =
        startDialogue(npc) {
            chatNpc(
                happy,
                "The quetzals are the pride of the guild. With a whistle, one will carry you " +
                    "anywhere the Quetzal Transport System reaches.",
            )
            val completed = rumours.completed(player)
            if (completed < BLUEPRINT_RUMOURS) {
                chatNpc(
                    neutral,
                    "Complete $BLUEPRINT_RUMOURS rumours for the guild and I'll teach you how to " +
                        "make a whistle of your own. You've completed $completed so far.",
                )
                return@startDialogue
            }
            val claimed = player.vars[BLUEPRINT_CLAIMED] != 0
            val ask =
                choice2(
                    if (claimed) "Can I buy another whistle blueprint? ($BLUEPRINT_PRICE coins)"
                    else "Can I have a whistle blueprint?",
                    true,
                    "Never mind.",
                    false,
                )
            if (!ask) {
                return@startDialogue
            }
            if (player.inv.isFull()) {
                chatNpc(neutral, "You'll need a free space in your inventory first.")
                return@startDialogue
            }
            if (claimed) {
                if (access.invDel(player.inv, COINS, BLUEPRINT_PRICE).failure) {
                    chatNpc(neutral, "That'll be $BLUEPRINT_PRICE coins, which you don't have.")
                    return@startDialogue
                }
            } else {
                VarPlayerIntMapSetter.set(player, BLUEPRINT_CLAIMED, 1)
            }
            access.invAdd(player.inv, Blueprint.Basic.obj)
            chatNpc(
                happy,
                "Here you go. Craft it with some willow logs and a knife, then bring the whistle " +
                    "back to me with raw hunter meat and I'll charge it up.",
            )
        }

    private fun ProtectedAccess.recharge() {
        val slot = inv.indexOfFirst { obj -> Whistle.forObj(obj)?.let { it.capacity > 0 } == true }
        if (slot == -1) {
            mes("You don't have a quetzal whistle that needs charging.")
            return
        }
        val obj = inv[slot] ?: return
        val whistle = Whistle.forObj(obj) ?: return
        var charges = charges(obj)
        var added = 0
        for ((meat, value) in MEAT_CHARGES) {
            while (charges < whistle.capacity && inv.contains(meat)) {
                invDel(inv, meat)
                charges = minOf(whistle.capacity, charges + value)
                added += value
            }
        }
        if (added == 0) {
            if (charges >= whistle.capacity) {
                mes("Your quetzal whistle is already fully charged.")
            } else {
                mes("Soar Leader Pitri needs raw hunter meat or quetzal feed to charge your whistle.")
            }
            return
        }
        setCharges(slot, charges)
        mes("Soar Leader Pitri charges your quetzal whistle. It now has $charges charges.")
    }

    private fun ProtectedAccess.craft(blueprint: Blueprint) {
        if (!inv.contains(KNIFE)) {
            mes("You need a knife to craft the whistle.")
            return
        }
        if (!inv.contains(blueprint.logs)) {
            mes("You need ${objName(blueprint.logs).lowercase()} to craft the whistle.")
            return
        }
        val baseSlot = blueprint.base?.let { base -> inv.indexOfFirst { it?.id == base.id() } }
        if (baseSlot == -1) {
            mes("You need a ${objName(blueprint.base!!.obj).lowercase()} to upgrade.")
            return
        }
        val carried = baseSlot?.let { inv[it]?.let(::charges) } ?: 0
        if (baseSlot != null) {
            inv[baseSlot] = null
        }
        invDel(inv, blueprint.obj)
        invDel(inv, blueprint.logs)
        val slot = inv.indexOfFirst { it == null }
        inv[slot] = InvObj(blueprint.product.obj, vars = withCharges(0, carried))
        mes("You craft a ${objName(blueprint.product.obj).lowercase()}.")
    }

    private fun ProtectedAccess.check(obj: InvObj) {
        val charges = charges(obj)
        mes("Your quetzal whistle has $charges charge${if (charges == 1) "" else "s"} remaining.")
    }

    private fun ProtectedAccess.rumour() {
        val rumour = rumours.activeRumour(player)
        val hunter = rumours.activeHunter(player)
        if (rumour == null || hunter == null) {
            mes("You don't currently have a rumour to follow.")
            return
        }
        mes(
            "Your current rumour target is a ${rumour.displayName}. You'll need to bring back " +
                "a ${objName(rumour.part).lowercase()}. ${hunter.displayName} was the source of " +
                "the rumour."
        )
    }

    private fun ProtectedAccess.signal(slot: Int) {
        if (!canUse(slot)) {
            return
        }
        ifOpenMainModal(MAP_INTERFACE)
        ifSetEvents(MAP_ICONS, 0 until sites.size, IfEvent.Op1)
    }

    private suspend fun ProtectedAccess.lastDestination(slot: Int) {
        val last = sites.firstOrNull { it.id == player.vars[LAST_DESTINATION] }
        if (last == null) {
            mes("You haven't flown anywhere with a quetzal whistle yet.")
            return
        }
        if (player.coords.chebyshev(last.coord) <= NEARBY_DISTANCE) {
            mes("You're already at ${last.name}.")
            return
        }
        if (!canUse(slot)) {
            return
        }
        fly(slot, last)
    }

    private suspend fun ProtectedAccess.travel(comsub: Int) {
        val site = sites.getOrNull(comsub) ?: return
        ifClose()
        val slot = usableWhistleSlot()
        if (slot < 0) {
            return
        }
        if (!isUnlocked(site)) {
            mes("That landing site hasn't been built yet.")
            return
        }
        if (player.coords.chebyshev(site.coord) <= NEARBY_DISTANCE) {
            mes("You're already at ${site.name}.")
            return
        }
        fly(slot, site)
    }

    private suspend fun ProtectedAccess.fly(slot: Int, site: QuetzalRow) {
        val obj = inv[slot] ?: return
        val whistle = Whistle.forObj(obj) ?: return
        if (whistle.capacity != UNLIMITED_CHARGES) {
            setCharges(slot, charges(obj) - 1)
        }
        VarPlayerIntMapSetter.set(player, LAST_DESTINATION, site.id)
        anim(WHISTLE_SEQ)
        spotanim(WHISTLE_SPOT)
        delay(FLIGHT_DELAY)
        telejump(site.coord)
        mes("The quetzal flies you to ${site.name}.")
    }

    private fun ProtectedAccess.canUse(slot: Int): Boolean {
        val obj = inv[slot] ?: return false
        val whistle = Whistle.forObj(obj) ?: return false
        if (player.coords.wildernessLevel(areaChecker) > MAX_WILDERNESS) {
            mes("Unfortunately, large birds can't just fly you to Varlamore from here.")
            mes("You'll need to be below level $MAX_WILDERNESS wilderness.")
            return false
        }
        if (whistle.capacity != UNLIMITED_CHARGES && charges(obj) <= 0) {
            mes("Your quetzal whistle has no charges left. Soar Leader Pitri can recharge it.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.isUnlocked(site: QuetzalRow): Boolean =
        site.autoUnlocked ||
            (site.id <= MAX_UNLOCK_BIT && (player.vars[UNLOCKED_SITES] shr site.id) and 1 != 0)

    private fun ProtectedAccess.setCharges(slot: Int, charges: Int) {
        val obj = inv[slot] ?: return
        inv[slot] = obj.copy(vars = withCharges(obj.vars, charges))
    }

    private fun charges(obj: InvObj): Int = obj.vars.getBits(chargeBits)

    private fun withCharges(vars: Int, charges: Int): Int = vars.withBits(chargeBits, charges)

    private val chargeBits by lazy {
        requireNotNull(ServerCacheManager.getVarObj(CHARGES.asRSCM(RSCMType.VAROBJ))).bits
    }

    private fun ProtectedAccess.usableWhistleSlot(): Int {
        val infinite = inv.indexOfFirst { Whistle.forObj(it) == Whistle.Imbued }
        if (infinite != -1) {
            return infinite
        }
        return inv.indexOfFirst { obj -> Whistle.forObj(obj) != null && charges(obj!!) > 0 }
    }

    private fun objName(obj: String): String =
        ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: obj

    private fun CoordGrid.chebyshev(other: CoordGrid): Int =
        maxOf(kotlin.math.abs(x - other.x), kotlin.math.abs(z - other.z))

    enum class Whistle(val obj: String, val capacity: Int) {
        Basic("obj.hg_quetzalwhistle_basic", 5),
        Enhanced("obj.hg_quetzalwhistle_enhanced", 20),
        Perfected("obj.hg_quetzalwhistle_perfected", 50),
        Imbued("obj.hg_quetzalwhistle_perfected_infinite", UNLIMITED_CHARGES);

        fun id(): Int = obj.asRSCM(RSCMType.OBJ)

        companion object {
            fun forObj(obj: InvObj?): Whistle? = obj?.let { o -> entries.firstOrNull { it.id() == o.id } }
        }
    }

    enum class Blueprint(
        val obj: String,
        val logs: String,
        val base: Whistle?,
        val product: Whistle,
        val checkText: String,
    ) {
        Basic(
            "obj.hg_whistle_blueprint_1",
            "obj.willow_logs",
            null,
            Whistle.Basic,
            "Crafting a basic quetzal whistle needs this blueprint, willow logs and a knife.",
        ),
        Enhanced(
            "obj.hg_whistle_blueprint_2",
            "obj.yew_logs",
            Whistle.Basic,
            Whistle.Enhanced,
            "Crafting an enhanced quetzal whistle needs this blueprint, a basic quetzal whistle, " +
                "yew logs and a knife.",
        ),
        EnhancedTorn(
            "obj.hg_whistle_blueprint_2_torn",
            "obj.yew_logs",
            Whistle.Basic,
            Whistle.Enhanced,
            "Crafting an enhanced quetzal whistle needs this blueprint, a basic quetzal whistle, " +
                "yew logs and a knife.",
        ),
        Perfected(
            "obj.hg_whistle_blueprint_3",
            "obj.redwood_logs",
            Whistle.Enhanced,
            Whistle.Perfected,
            "Crafting a perfected quetzal whistle needs this blueprint, an enhanced quetzal " +
                "whistle, redwood logs and a knife.",
        ),
        PerfectedTorn(
            "obj.hg_whistle_blueprint_3_torn",
            "obj.redwood_logs",
            Whistle.Enhanced,
            Whistle.Perfected,
            "Crafting a perfected quetzal whistle needs this blueprint, an enhanced quetzal " +
                "whistle, redwood logs and a knife.",
        ),
    }

    private companion object {
        const val PITRI = "npc.hunter_guild_pitri"
        const val MAP_INTERFACE = "interface.quetzalwhistle_menu"
        const val MAP_ICONS = "component.quetzalwhistle_menu:icons"
        const val CHARGES = "varobj.quetzal_whistle_charges"
        const val BLUEPRINT_CLAIMED = "varbit.hunter_whistle_blueprint_claimed"
        const val LAST_DESTINATION = "varbit.quetzal_last_destination"
        const val UNLOCKED_SITES = "varp.quetzals_unlocked"
        const val COINS = "obj.coins"
        const val KNIFE = "obj.knife"
        const val WHISTLE_SEQ = "seq.human_quetzal_whistle"
        const val WHISTLE_SPOT = "spotanim.spotanim_whistle_quetzal_backpack01"
        const val BLUEPRINT_RUMOURS = 10
        const val BLUEPRINT_PRICE = 500
        const val MAX_WILDERNESS = 20
        const val MAX_UNLOCK_BIT = 30
        const val NEARBY_DISTANCE = 10
        const val FLIGHT_DELAY = 4

        val MEAT_CHARGES =
            listOf(
                "obj.hg_seedsack" to 1,
                "obj.huntingbeast_wild_meat" to 1,
                "obj.hunting_larupia_meat" to 1,
                "obj.huntingbeast_barbed_meat" to 1,
                "obj.hunting_graahk_meat" to 2,
                "obj.hunting_kyatt_meat" to 2,
                "obj.hunting_fennecfox_meat" to 2,
                "obj.hunting_antelopesun_meat" to 3,
                "obj.huntingbeast_speedy2_meat" to 3,
                "obj.hunting_antelopemoon_meat" to 3,
            )
    }
}

private const val UNLIMITED_CHARGES = -1
