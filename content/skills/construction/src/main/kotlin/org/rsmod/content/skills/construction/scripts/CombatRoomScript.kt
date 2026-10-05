package org.rsmod.content.skills.construction.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.script.onOpPlayerU
import org.rsmod.content.skills.construction.data.Combat
import org.rsmod.content.skills.construction.house.HouseGames
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The combat room: its ring, racks and combat dummy.
 *
 * Ring walls are climbed over and the pedestals' magic barriers walked through, from either side;
 * the fights in a ring are [org.rsmod.content.skills.construction.house.HousePvPHook] and
 * [org.rsmod.content.skills.construction.house.HouseRingRules]'s, and a death in one only puts the
 * loser outside it. On the balance beam a player stands on a piece and uses a pugel on someone else
 * on the beam: a Strength roll against their Agility knocks them off and wins the bout.
 *
 * A rack hands out the pieces the wiki lists for it. Attach stands the dummy's npc in place of the
 * dummy; every attack on it lands at the attacker's max hit (the attack manager's rule for its npc
 * category), gives no experience, and it never runs out of hitpoints. Detach puts the dummy back.
 * Swap turns an ornate dummy into one of its other forms, paying that form's items the first time.
 */
class CombatRoomScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val games: HouseGames,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (rope in Combat.ROPES) {
            onOpLoc1(rope) { cross(it.loc) }
        }
        onOpLoc1(Combat.MAGIC_BARRIER) { cross(it.loc) }
        for (piece in Combat.BEAM) {
            onOpLoc1(piece) { standOn(it.loc) }
            onOpLoc4(piece) { getDown() }
        }
        onOpPlayerU(item(Combat.PUGEL)) { pugel(it.target) }

        for (rack in Combat.Rack.entries) {
            onOpLoc1(rack.loc) { search(rack) }
        }

        for (dummy in Combat.Dummy.entries) {
            onOpLoc1(dummy.loc) { attach(it.loc, dummy) }
            val npc = checkNotNull(ServerCacheManager.getNpc(dummy.npc.asRSCM(RSCMType.NPC))) { dummy.npc }
            onOpNpc3(dummy.npc) { detach(it.npc) }
            onOpNpc4(dummy.npc) { mes("You'll need to detach the sandbag first.") }
            onOpNpc5(dummy.npc) { mes("You'll need to detach the sandbag first.") }
            onNpcHit(npc) { this.npc.hitpoints = Combat.DUMMY_HITPOINTS }
        }
        for (form in Combat.Dummy.ORNATE_FORMS) {
            onOpLoc4(form.loc) { swap(it.loc) }
        }
    }

    // ------------------------------------------------------------------------------- ring

    /** Steps over a ring wall or through a barrier, to the tile on its other side. */
    private suspend fun ProtectedAccess.cross(loc: BoundLocInfo) {
        val (dx, dz) =
            if (loc.shape.id == WALL_CORNER_SHAPE) CORNER_STEPS[loc.angle.id] else WALL_STEPS[loc.angle.id]
        val across = loc.coords.translate(dx, dz, 0)
        val dest = if (player.coords == loc.coords) across else loc.coords
        anim(CLIMB_SEQ)
        delay(1)
        telejump(dest)
    }

    private fun ProtectedAccess.standOn(loc: BoundLocInfo) {
        val house = registry.houseAt(player.coords) ?: return
        val beam = games.of(house.owner).beam
        if (player.coords == loc.coords) {
            return
        }
        if (player !in beam) {
            beam[player] = player.coords
        }
        telejump(loc.coords)
    }

    private fun ProtectedAccess.getDown() {
        val house = registry.houseAt(player.coords) ?: return
        val from = games.of(house.owner).beam.remove(player) ?: return
        telejump(from)
    }

    private suspend fun ProtectedAccess.pugel(target: Player) {
        val house = registry.houseAt(player.coords) ?: return
        val ring = registry.ringAt(house, player.coords)
        if (ring?.ring != Combat.Ring.BEAM) {
            mes("You need to be on a balance beam to use that.")
            return
        }
        if (registry.ringAt(house, target.coords) !== ring) {
            mes("They need to be on the beam with you.")
            return
        }
        faceEntitySquare(target)
        anim(PUGEL_SEQ)
        delay(PUGEL_TICKS)
        // Either of them may have stepped off, or left the house, while the pugel swung.
        if (registry.ringAt(house, player.coords) !== ring || registry.ringAt(house, target.coords) !== ring) {
            return
        }
        val strength = stat(STRENGTH)
        val balance = target.agilityLvl
        val chance = (PUGEL_BASE + (strength - balance) * PUGEL_PER_LEVEL).coerceIn(PUGEL_MIN, PUGEL_MAX)
        if (random.of(PUGEL_ROLL) >= chance) {
            mes("${target.displayName} keeps their balance.")
            target.mes("You keep your balance.")
            return
        }
        val beam = games.of(house.owner).beam
        val down = beam.remove(target) ?: ring.exitFrom(target.coords)
        PathingEntityCommon.telejump(target, collision, down)
        target.mes("${player.displayName} knocks you off the beam!")
        mes("You knock ${target.displayName} off the beam and win the bout!")
    }

    // ------------------------------------------------------------------------------ racks

    private suspend fun ProtectedAccess.search(rack: Combat.Rack) {
        val names = rack.items.map { RSCM.getReverseMapping(RSCMType.OBJ, it.asRSCM(RSCMType.OBJ)) }
        val labels = names.map { obj -> label(obj) }
        val pick =
            when (names.size) {
                2 -> choice2(labels[0], 0, labels[1], 1, title = TITLE)
                4 -> choice4(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, title = TITLE)
                else -> choice5(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, labels[4], 4, title = TITLE)
            }
        val obj = names[pick]
        if (inv.contains(obj)) {
            mes("You already have one of those.")
            return
        }
        if (invAdd(inv, obj, 1).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        mes("You take the ${labels[pick].lowercase()} from the rack.")
    }

    /** The name a rack lists an item under; the two pairs of gloves differ only in colour. */
    private fun label(obj: String): String =
        when (obj) {
            "obj.poh_boxing_gloves_red" -> "Red boxing gloves"
            "obj.poh_boxing_gloves_blue" -> "Blue boxing gloves"
            else -> ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: obj
        }

    // ----------------------------------------------------------------------------- dummy

    private fun ProtectedAccess.attach(loc: BoundLocInfo, dummy: Combat.Dummy) {
        val house = registry.houseAt(player.coords) ?: return
        val room = games.of(house.owner)
        if (room.dummy != null) {
            return
        }
        val stand = games.stand(loc, dummy.loc, dummy.npc)
        val npc = stand.npc
        npc.baseHitpointsLvl = Combat.DUMMY_HITPOINTS
        npc.hitpoints = Combat.DUMMY_HITPOINTS
        npc.combatXpMultiplier = 0
        room.dummy = stand
        mes("You attach the sandbag to the dummy.")
    }

    private fun ProtectedAccess.detach(npc: Npc) {
        val house = registry.houseAt(player.coords) ?: return
        val room = games.of(house.owner)
        val stand = room.dummy?.takeIf { it.npc === npc } ?: return
        room.dummy = null
        games.sit(stand)
        mes("You detach the sandbag from the dummy.")
    }

    private suspend fun ProtectedAccess.swap(loc: BoundLocInfo) {
        val house = registry.houseAt(player.coords) ?: return
        if (house.owner !== player) {
            mes("Only the owner of this house can swap the dummy.")
            return
        }
        val variants = player.vars[Combat.VARIANTS_VARP]
        val current = Combat.shownForm(variants)
        val forms = Combat.Dummy.ORNATE_FORMS.filter { it != current }
        val form = pickForm(forms) ?: return
        if (!Combat.unlocked(variants, form) && !pay(form)) {
            return
        }
        VarPlayerIntMapSetter.set(player, Combat.VARIANTS_VARP, Combat.withForm(Combat.withUnlocked(variants, form), form))
        games.replace(loc, form.loc)
        mes("The dummy becomes an ${form.label} dummy.")
    }

    /** Pages the swap menu four forms at a time, with a last option for the rest. */
    private suspend fun ProtectedAccess.pickForm(forms: List<Combat.Dummy>): Combat.Dummy? {
        var page = 0
        while (true) {
            val shown = forms.drop(page * PAGE).take(PAGE)
            val more = forms.size > (page + 1) * PAGE
            val options = shown.map { it.label.replaceFirstChar(Char::uppercase) to it } +
                if (more) listOf(MORE to null) else listOf(CANCEL to null)
            val pick =
                when (options.size) {
                    2 -> choice2(options[0].first, 0, options[1].first, 1, title = SWAP_TITLE)
                    3 -> choice3(options[0].first, 0, options[1].first, 1, options[2].first, 2, title = SWAP_TITLE)
                    4 -> choice4(options[0].first, 0, options[1].first, 1, options[2].first, 2, options[3].first, 3, title = SWAP_TITLE)
                    else ->
                        choice5(
                            options[0].first, 0, options[1].first, 1, options[2].first, 2,
                            options[3].first, 3, options[4].first, 4, title = SWAP_TITLE,
                        )
                }
            val (label, form) = options[pick]
            if (form != null) {
                return form
            }
            if (label != MORE) {
                return null
            }
            page++
        }
    }

    private fun ProtectedAccess.pay(form: Combat.Dummy): Boolean {
        val item = form.unlockAny.firstOrNull { inv.count(it) >= form.unlock }
        if (item == null) {
            val names = form.unlockAny.joinToString(" or ") { label(it) }
            mes("You need ${form.unlock} x $names to make an ${form.label} dummy.")
            return false
        }
        return invDel(inv, item, form.unlock).success
    }

    private fun item(obj: String) = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))) { obj }

    private companion object {
        const val STRENGTH = "stat.strength"
        const val CLIMB_SEQ = "seq.human_jump_hurdle"
        const val PUGEL_SEQ = "seq.human_unarmedpunch"
        const val PUGEL_TICKS = 2

        /** A knock-off is a coin flip at equal Strength and Agility, a percent either way a level. */
        const val PUGEL_ROLL = 100
        const val PUGEL_BASE = 50
        const val PUGEL_PER_LEVEL = 1
        const val PUGEL_MIN = 10
        const val PUGEL_MAX = 90

        const val TITLE = "Take what?"
        const val SWAP_TITLE = "Which dummy?"
        const val PAGE = 4
        const val MORE = "More..."
        const val CANCEL = "Cancel"

        /** The other side of a straight wall, by its angle: west, north, east, south. */
        val WALL_STEPS = listOf(-1 to 0, 0 to 1, 1 to 0, 0 to -1)

        /** The diagonal past a wall corner, by its angle: north-west, north-east, south-east, south-west. */
        val CORNER_STEPS = listOf(-1 to 1, 1 to 1, 1 to -1, -1 to -1)

        const val WALL_CORNER_SHAPE = 3
    }
}
