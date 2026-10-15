package org.rsmod.content.quest.area.falador.knightssword

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.PORTRAIT
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.SIR_VYVIN
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_DESIGN_SHOWN
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_PORTRAIT_LOCATED
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Sir Vyvin's room on the top floor of the White Knights' Castle, where the portrait of his father
 * is kept in a cupboard.
 *
 * Sir Vyvin keeps a fixed, readable routine: he stands at his desk facing the cupboard for
 * [Post.DESK]'s ticks, announces he needs some air, then looks out of the west window for
 * [Post.WINDOW]'s ticks with his back to the room, and warns that he is going back to work shortly
 * before he turns round. He sees a player in the 90 degree cone in front of him, within
 * [VIEW_RANGE] tiles, when nothing blocks the line between them, so walls and doors hide the
 * player too. Opening, searching or putting something back in the cupboard in his sight is
 * stopped with a telling-off; nothing is lost, and the player can simply try again.
 */
@Singleton
class VyvinsRoom
@Inject
constructor(
    private val ks: KnightsSwordQuest,
    private val locRepo: LocRepository,
    private val npcList: NpcList,
    collision: CollisionFlagMap,
) : PluginScript() {

    private val rays = RayCastValidator(collision)

    private var vyvin: Npc? = null
    var post = Post.DESK
        private set
    private var postTicks = 0
    private var walkTicks = 0

    /** The way Sir Vyvin is looking, which is all his sight depends on. */
    var facing: Direction = Post.DESK.facing
        private set

    override fun ScriptContext.startup() {
        onEvent<NpcStateEvents.Create> {
            if (npc.isType(SIR_VYVIN)) {
                attach(npc)
            }
        }
        onAiTimer(SIR_VYVIN) { patrol(npc) }
        onOpLoc1(CUPBOARD_SHUT) { openCupboard(it.loc) }
        onOpLoc1(CUPBOARD_OPEN) { searchCupboard() }
        onOpLoc2(CUPBOARD_OPEN) { shutCupboard(it.loc) }
        onOpLocU(CUPBOARD_SHUT, PORTRAIT) { returnPortrait() }
        onOpLocU(CUPBOARD_OPEN, PORTRAIT) { returnPortrait() }
        onOpHeld1(PORTRAIT) { lookAtPortrait() }
    }

    fun attach(npc: Npc) {
        vyvin = npc
        npc.mode = NpcMode.None
        post = Post.DESK
        postTicks = 0
        walkTicks = 0
        npc.aiTimer(1)
    }

    /** Sir Vyvin if he can see [player] right now, else null. */
    fun watcher(player: Player): Npc? {
        val npc = currentVyvin() ?: return null
        return npc.takeIf { it.isVisible && sees(rays, it.coords, facing, player.coords) }
    }

    fun isLookingAway(): Boolean = post == Post.WINDOW && facing == Post.WINDOW.facing

    fun patrol(npc: Npc) {
        npc.aiTimer(1)
        if (npc !== vyvin) {
            attach(npc)
        }
        if (npc.coords != post.tile) {
            walkTowardPost(npc)
            return
        }
        walkTicks = 0
        facing = post.facing
        npc.lockFacingDirection(post.facing)
        postTicks++
        if (post == Post.WINDOW && postTicks == post.ticks - WARNING_TICKS) {
            npc.say("Well, those reports won't write themselves...")
        }
        if (postTicks >= post.ticks) {
            post = post.next()
            postTicks = 0
            if (post == Post.WINDOW) {
                npc.say("Phew. I need some fresh air.")
            }
            walkTowardPost(npc)
        }
    }

    private fun walkTowardPost(npc: Npc) {
        if (walkTicks++ > MAX_WALK_TICKS) {
            npc.clearFacingLock()
            npc.walk(post.tile)
            walkTicks = 0
            return
        }
        facing = stepToward(npc.coords, post.tile)
        if (walkTicks == 1) {
            npc.clearFacingLock()
            npc.walk(post.tile)
        }
    }

    private fun currentVyvin(): Npc? {
        vyvin?.takeIf { it.isSlotAssigned }?.let { return it }
        val found = npcList.firstOrNull { it != null && it.isType(SIR_VYVIN) } ?: return null
        attach(found)
        return found
    }

    private suspend fun ProtectedAccess.openCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        if (caught()) {
            return
        }
        anim(OPEN_SEQ)
        soundSynth(OPEN_SOUND)
        locRepo.change(cupboard, CUPBOARD_OPEN, CUPBOARD_TICKS)
    }

    private suspend fun ProtectedAccess.shutCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        anim(CLOSE_SEQ)
        soundSynth(CLOSE_SOUND)
        locRepo.change(cupboard, CUPBOARD_SHUT, CUPBOARD_TICKS)
    }

    private suspend fun ProtectedAccess.searchCupboard() {
        arriveDelay()
        if (caught()) {
            return
        }
        mes("You search through the cupboard...")
        anim(SEARCH_SEQ)
        delay(SEARCH_TICKS)
        if (caught()) {
            return
        }
        val stage = ks.stage(player)
        when {
            stage < STAGE_PORTRAIT_LOCATED ->
                mes("You find spare tabards, a polishing cloth and a great many socks. Nothing you need.")
            PORTRAIT in inv -> mes("You already have the portrait.")
            stage >= STAGE_DESIGN_SHOWN ->
                mes("You find nothing you need. Thurgo has already seen the portrait.")
            inv.isFull() ->
                objbox(PORTRAIT, zoom = 600, "You find a small portrait, but you don't have room to carry it.")
            else -> {
                invAdd(inv, PORTRAIT)
                objbox(
                    PORTRAIT,
                    zoom = 600,
                    "Behind the tabards is a small portrait of a stern old knight. He grips a sword " +
                        "with a pale blue blade and a gold-wrapped hilt. You tuck it away.",
                )
            }
        }
    }

    private suspend fun ProtectedAccess.lookAtPortrait() {
        objbox(
            PORTRAIT,
            zoom = 600,
            "A stern, grey-bearded knight in White Knight armour. His sword has a pale blue blade, " +
                "a gold-wrapped grip and a flared crossguard.",
        )
    }

    private suspend fun ProtectedAccess.returnPortrait() {
        arriveDelay()
        if (ks.stage(player) < STAGE_DESIGN_SHOWN) {
            mes("You should show the portrait to Thurgo before putting it back.")
            return
        }
        if (caught()) {
            return
        }
        anim(SEARCH_SEQ)
        if (invDel(inv, PORTRAIT, 1).success) {
            mes("You slip the portrait back behind the tabards. Nobody will ever know.")
        }
    }

    private suspend fun ProtectedAccess.caught(): Boolean {
        val npc = watcher(player) ?: return false
        npc.facePlayer(player)
        npc.say("HEY!")
        startDialogue(npc) {
            chatNpc(angry, "Just WHAT do you think you are doing? Keep your hands OUT of my cupboard!")
            chatPlayer(worried, "Sorry! I thought it was... the privy.")
            chatNpc(angry, "The privy is downstairs, like every other privy in Falador.")
        }
        mes("Sir Vyvin saw you. Wait until he looks away before trying again.")
        npc.resetFaceEntity()
        return true
    }

    private fun stepToward(from: CoordGrid, to: CoordGrid): Direction {
        val dx = Integer.signum(to.x - from.x)
        val dz = Integer.signum(to.z - from.z)
        return Direction.entries.firstOrNull { it.xOff == dx && it.zOff == dz } ?: facing
    }

    enum class Post(val tile: CoordGrid, val facing: Direction, val ticks: Int) {
        DESK(CoordGrid(2983, 3334, 2), Direction.NorthEast, 25),
        WINDOW(CoordGrid(2981, 3335, 2), Direction.West, 17);

        fun next(): Post = entries[(ordinal + 1) % entries.size]
    }

    companion object {
        const val CUPBOARD_SHUT = "loc.vyvincupboardshut"
        const val CUPBOARD_OPEN = "loc.vyvincupboardopen"

        const val VIEW_RANGE = 8
        const val WARNING_TICKS = 4
        const val MAX_WALK_TICKS = 10
        const val SEARCH_TICKS = 2
        const val CUPBOARD_TICKS = 100

        const val OPEN_SEQ = "seq.human_opencupboard"
        const val CLOSE_SEQ = "seq.human_closecupboard"
        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val OPEN_SOUND = "synth.cupboard_open"
        const val CLOSE_SOUND = "synth.cupboard_close"

        val CUPBOARD = CoordGrid(2984, 3336, 2)

        /**
         * Whether someone at [from] looking [facing] sees [target]: inside the 90 degree cone in
         * front of them, within [VIEW_RANGE] tiles, with nothing blocking the line between them.
         */
        fun sees(rays: RayCastValidator, from: CoordGrid, facing: Direction, target: CoordGrid): Boolean =
            inCone(from, facing, target) && rays.hasLineOfSight(from, target)

        private fun inCone(from: CoordGrid, facing: Direction, target: CoordGrid): Boolean {
            if (from.level != target.level || from == target || from.chebyshevDistance(target) > VIEW_RANGE) {
                return false
            }
            val dx = target.x - from.x
            val dz = target.z - from.z
            val dot = facing.xOff * dx + facing.zOff * dz
            if (dot <= 0) {
                return false
            }
            val length = facing.xOff * facing.xOff + facing.zOff * facing.zOff
            return 2 * dot * dot >= (dx * dx + dz * dz) * length
        }
    }
}
