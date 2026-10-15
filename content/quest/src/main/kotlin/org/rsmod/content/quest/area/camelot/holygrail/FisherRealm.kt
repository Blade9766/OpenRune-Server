package org.rsmod.content.quest.area.camelot.holygrail

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpObj3
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.BELL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.EXCALIBUR
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.HOLY_GRAIL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_GRAIL_TAKEN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_REALM_RESTORED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.TITAN
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Fisher Realm's set pieces: the Black Knight Titan on the bridge, the grail bell that opens
 * the castle, the empty tower while the realm is sick, and the Grail once it is healed.
 *
 * The realm exists twice on the map: the dying copy (map square 43_73) and the healed copy 128
 * tiles west (41_73). Which one a player reaches is decided by the whistle, from their stage.
 *
 * The Titan cannot be walked past: his bridge tile is solid. Any weapon can wound him, but only
 * a melee blow from Excalibur may finish him; the blow that would kill him with anything else
 * heals him to full instead and says why. Beating him is remembered, so from then on he lets the
 * player across whenever they speak to him, in either direction.
 */
@Singleton
class FisherRealm
@Inject
constructor(
    private val quest: HolyGrailQuest,
    private val objRepo: ObjRepository,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val aiInteractions: AiPlayerInteractions,
    private val death: NpcDeath,
) : PluginScript() {

    private data class Blow(val uid: PlayerUid, val excalibur: Boolean)

    private val lastBlow = HashMap<Npc, Blow>()
    private val inEmptyTower = HashSet<PlayerUid>()

    override fun ScriptContext.startup() {
        val titan = npcType(TITAN)
        onOpNpc1(TITAN) { startDialogue(it.npc) { titanTalk() } }
        onModifyNpcHit(titan) {
            if (!hit.isFromPlayer || hit.damage <= 0) {
                return@onModifyNpcHit
            }
            val source = hit.sourceUid?.let { PlayerUid(it).resolve(playerList) } ?: return@onModifyNpcHit
            recordBlow(npc, source, hit.type)
        }
        onNpcQueue(titan, "queue.death") { titanFalls() }

        onOpHeld1(BELL) { ringBell() }
        onOpObj3(grailType()) { takeGrail(it.obj) }
        onPlayerCoordsChanged {
            if (!isEmptyTower(player.coords)) {
                inEmptyTower.remove(player.uid)
                return@onPlayerCoordsChanged
            }
            if (inEmptyTower.add(player.uid)) {
                emptyTower(player)
            }
        }
    }

    fun wieldsExcalibur(player: Player): Boolean =
        player.worn[Wearpos.RightHand.slot]?.id == EXCALIBUR.asRSCM(RSCMType.OBJ)

    /* The Titan */

    private suspend fun Dialogue.titanTalk() {
        if (player.titanDefeated) {
            chatNpc(neutral, "You struck me down with the king's sword. I do not forget. Cross, if you wish.")
            val cross = choice2("Let me cross the bridge.", true, "Not now.", false)
            if (cross) {
                access.crossBridge()
            }
            return
        }
        chatNpc(angry, "I am the Black Knight Titan! You must pass through me before you go deeper into this realm!")
        val ask =
            choice2(
                "Then I shall pass through you!",
                true,
                "What would it take to defeat you?",
                false,
            )
        if (ask) {
            chatPlayer(angry, "Then I shall pass through you!")
            access.ifClose()
            npc?.opPlayer2(player, aiInteractions)
            return
        }
        chatPlayer(quiz, "What would it take to defeat you?")
        chatNpc(
            laugh,
            "Cut me as you like, little knight. Any blade will bleed me. But I rise again from " +
                "every common blow. Only the sword of a true king can finish me.",
        )
    }

    suspend fun ProtectedAccess.crossBridge() {
        val dest = if (coords.x >= BRIDGE_TILE.x) BRIDGE_WEST else BRIDGE_EAST
        ifClose()
        telejump(dest, TeleportType.Exempt)
        mes("The titan steps aside and lets you cross the bridge.")
    }

    /** Remembers who struck [titan] last and whether that blow was Excalibur's, in melee. */
    fun recordBlow(titan: Npc, source: Player, type: HitType) {
        lastBlow[titan] = Blow(source.uid, type == HitType.Melee && wieldsExcalibur(source))
    }

    /**
     * Decides a titan brought to zero hitpoints: slain if [hero] dealt the last blow with
     * Excalibur, otherwise healed to full with the hint about the sword. Returns whether he died.
     */
    fun judge(titan: Npc, hero: Player?): Boolean {
        val blow = lastBlow.remove(titan)
        if (hero == null || blow == null || !blow.excalibur || blow.uid != hero.uid) {
            regenerate(titan, hero)
            return false
        }
        hero.titanDefeated = true
        hero.mes(GOLD.format("Excalibur bites deep. The titan falls, and the bridge stands open."))
        return true
    }

    private suspend fun StandardNpcAccess.titanFalls() {
        val hero = lastBlow[npc]?.uid?.resolve(playerList) ?: findHero(playerList)
        if (!judge(npc, hero) || hero == null) {
            return
        }
        death.deathWithDrops(this)
        launcher.launch(hero) {
            if (coords.chebyshevDistance(BRIDGE_TILE) <= BRIDGE_REACH && coords.x > BRIDGE_TILE.x) {
                telejump(BRIDGE_WEST, TeleportType.Exempt)
            }
        }
    }

    private fun regenerate(titan: Npc, hero: Player?) {
        titan.heal(titan.baseHitpointsLvl, showHitsplat = true)
        if (hero == null) {
            return
        }
        hero.mes(RED.format("The titan's wounds close as fast as they were made!"))
        hero.mes("Maybe you need a special sword to finish him off.")
        if (hero.isSlotAssigned) {
            titan.opPlayer2(hero, aiInteractions)
        }
    }

    /* The castle */

    private suspend fun ProtectedAccess.ringBell() {
        anim(RING_SEQ)
        if (isInHealedRealm(coords)) {
            mes("The bell rings out. From the castle comes an answering peal; its doors stand open now.")
            return
        }
        if (!isInDyingRealm(coords)) {
            mes("The bell rings sweetly. Nothing happens.")
            return
        }
        if (coords.level != 0 || coords.chebyshevDistance(BELL_SPOT) > BELL_REACH) {
            mes("The bell's note dies in the grey air. You are too far from the castle for it to be heard.")
            return
        }
        mes("The bell's note rises and does not fade.")
        delay(2)
        spotanim(ENTRY_SPOTANIM)
        telejump(CASTLE_ENTRY, TeleportType.Exempt)
        player.castleEntered = true
        mesbox(
            "A grail maiden in white opens a door you are certain was not there a moment ago, " +
                "and leads you without a word up to the Fisher King's chamber.",
        )
    }

    private fun emptyTower(player: Player) {
        val stage = quest.stage(player)
        if (stage == 0 || stage >= STAGE_REALM_RESTORED) {
            return
        }
        player.mes(
            "The top of the tower is cold and bare. Something was kept here; you can feel the " +
                "shape of its absence. It will not return while the realm is sick.",
        )
    }

    /* The Grail */

    private suspend fun ProtectedAccess.takeGrail(grail: Obj) {
        val stage = quest.stage(player)
        when {
            stage < STAGE_REALM_RESTORED -> {
                mes("Your fingers will not close on the Grail. The realm is not whole yet.")
                return
            }
            stage >= STAGE_COMPLETE -> {
                mes("The Grail you brought to Camelot is safe there. This one belongs to King Percival.")
                return
            }
            ownsAnywhere(HOLY_GRAIL) -> {
                mes("You already have the Holy Grail.")
                return
            }
            inv.isFull() -> {
                mes("You don't have enough inventory space.")
                return
            }
        }
        if (coords != grail.coords) {
            delay(1)
        }
        anim(TAKE_SEQ)
        if (!objRepo.del(grail)) {
            return
        }
        invAdd(inv, HOLY_GRAIL)
        spotanim(GRAIL_SPOTANIM)
        quest.advanceTo(this, STAGE_GRAIL_TAKEN)
        objbox(
            HOLY_GRAIL,
            "You lift the Holy Grail. It is lighter than it looks, and warm, and the whole tower " +
                "seems to breathe out.",
        )
    }

    private fun grailType() =
        ServerCacheManager.getItem(HOLY_GRAIL.asRSCM(RSCMType.OBJ)) ?: error("Missing obj: $HOLY_GRAIL")

    private fun npcType(name: String): NpcServerType =
        ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")

    companion object {
        /** The Titan's tile, the middle of the one bridge over the river; solid on the map. */
        val BRIDGE_TILE = CoordGrid(2791, 4722, 0)
        val BRIDGE_EAST = CoordGrid(2793, 4722, 0)
        val BRIDGE_WEST = CoordGrid(2789, 4722, 0)
        const val BRIDGE_REACH = 6

        /** Where the grail bell lies, on the open ground north of the castle. */
        val BELL_SPOT = CoordGrid(2762, 4694, 0)
        const val BELL_REACH = 8

        /**
         * In the Fisher King's chamber on the dying castle's first floor. The stairs come up into a
         * closed room of their own, two doors and a side passage away from him, so the maiden
         * leads the player straight to his bedside.
         */
        val CASTLE_ENTRY = CoordGrid(2762, 4686, 1)

        const val RING_SEQ = "seq.player_town_crier_bell_ring"
        const val TAKE_SEQ = "seq.human_pickuptable"
        const val ENTRY_SPOTANIM = "spotanim.smokepuff"
        const val GRAIL_SPOTANIM = "spotanim.heal_casting"

        private const val GOLD = "<col=c08000>%s</col>"
        private const val RED = "<col=ef1020>%s</col>"

        fun isInDyingRealm(coords: CoordGrid): Boolean = coords.x in 2752..2815 && coords.z in 4672..4735

        fun isInHealedRealm(coords: CoordGrid): Boolean = coords.x in 2624..2687 && coords.z in 4672..4735

        fun isInRealm(coords: CoordGrid): Boolean = isInDyingRealm(coords) || isInHealedRealm(coords)

        /** The top floor of the dying castle's eastern tower, where the Grail is missing. */
        fun isEmptyTower(coords: CoordGrid): Boolean =
            coords.level == 2 && coords.x in 2776..2780 && coords.z in 4681..4687
    }
}
