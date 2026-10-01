package org.rsmod.content.quest.area.camelot.holygrail.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.camelot.holygrail.FisherRealm
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.FEATHER
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.PERCIVAL_SACKS
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.SIR_PERCIVAL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_FEATHER
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_PERCIVAL_SENT
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.WHISTLE
import org.rsmod.content.quest.area.camelot.holygrail.percivalFound
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sir Percival, found tied in a sack in Goblin Village after the goblins sold him a story about
 * the golden boots of Arkaneeses, and the magic gold feather that leads the player to him.
 *
 * Only Open, with the feather in the pack, gets him out; Prod just makes him yelp. He climbs out
 * as a temporary npc kept per player, so opening the sacks again reuses him rather than adding a
 * second Percival. He goes home only when the player can spare a whistle - two carried - so the
 * player is never left without their own way into the realm.
 */
@Singleton
class SirPercival
@Inject
constructor(
    private val quest: HolyGrailQuest,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    private val emerged = HashMap<PlayerUid, Npc>()

    override fun ScriptContext.startup() {
        onOpLoc1(PERCIVAL_SACKS) { prod() }
        onOpLoc2(PERCIVAL_SACKS) { open() }
        onOpHeld1(FEATHER) { blowOnFeather() }
        onOpNpc1(SIR_PERCIVAL) { startDialogue(it.npc) { percival(it.npc) } }
    }

    private suspend fun ProtectedAccess.prod() {
        arriveDelay()
        if (quest.stage(player) == STAGE_FEATHER && FEATHER in inv) {
            mes("You prod the sacks. Something inside says \"Ow!\" in a very well-bred voice.")
            mes("Prodding won't get it out. You could try opening the sacks.")
            return
        }
        mes("You prod the sacks. They are lumpy and smell of goblin.")
    }

    private suspend fun ProtectedAccess.open() {
        arriveDelay()
        val stage = quest.stage(player)
        if (stage >= STAGE_PERCIVAL_SENT) {
            mes("The sacks are empty now, apart from a knightly smell of polish.")
            return
        }
        if (stage != STAGE_FEATHER) {
            mes("You open the sacks. Goblin rubbish: mouldy bread, a bent spear tip and a lot of string.")
            return
        }
        if (FEATHER !in inv) {
            mes("You open the sacks and rummage, but find nothing you were looking for.")
            mes("Without Arthur's feather to guide you, it is all just goblin rubbish.")
            return
        }
        anim(OPEN_SEQ)
        val percival = summon()
        player.percivalFound = true
        startDialogue(percival) { percival(percival) }
    }

    private fun ProtectedAccess.summon(): Npc {
        val existing = emerged[player.uid]
        if (existing != null && existing.isSlotAssigned) {
            existing.facePlayer(player)
            return existing
        }
        val npc = Npc(SIR_PERCIVAL, PERCIVAL_TILE)
        npc.respawns = false
        npcRepo.add(npc, PERCIVAL_TICKS)
        npc.facePlayer(player)
        emerged[player.uid] = npc
        mes("A dishevelled knight in dented armour climbs out of the sack.")
        return npc
    }

    private suspend fun Dialogue.percival(npc: Npc) {
        val stage = quest.stage(player)
        if (stage != STAGE_FEATHER) {
            chatNpc(happy, "Well met! Nothing to see here.")
            return
        }
        if (!player.percivalFound) {
            chatNpc(neutral, "Sir Percival, at your service. Have we met?")
            return
        }
        chatNpc(
            shifty,
            "Ah. Hello. You'll be wondering why a knight of the Round Table was in a sack. The " +
                "goblins said the golden boots of Arkaneeses were in it.",
        )
        chatNpc(sad, "They were not. Then they tied the top.")
        chatPlayer(neutral, "King Arthur sent me. I have news about your father.")
        chatNpc(confused, "My father? I never knew him. I was a foundling.")
        chatPlayer(
            neutral,
            "He is the Fisher King, keeper of the Holy Grail. He is dying, and his land with him. " +
                "Only you can save it.",
        )
        chatNpc(shocked, "A king? Me? But how would I even reach him?")
        val choice =
            choice2(
                "Come with me, I shall make you a king.",
                true,
                "I'll come back for you.",
                false,
            )
        if (!choice) {
            chatPlayer(neutral, "I'll come back for you.")
            chatNpc(neutral, "I'll be here. Not in the sack, mind.")
            return
        }
        chatPlayer(happy, "Come with me, I shall make you a king.")
        giveWhistle(npc)
    }

    private suspend fun Dialogue.giveWhistle(npc: Npc) {
        val carried = player.inv.count(WHISTLE)
        if (carried == 0) {
            chatPlayer(neutral, "The way there is a magic whistle. I... don't have one with me.")
            chatNpc(neutral, "Then fetch one, and I will follow you anywhere.")
            return
        }
        if (carried == 1) {
            chatPlayer(
                worried,
                "You need a magic whistle to get there. I have only one, and I need it to follow " +
                    "you. I must fetch another first.",
            )
            chatNpc(neutral, "Then fetch another. I'll wait right here.")
            return
        }
        access.invDel(player.inv, WHISTLE)
        quest.advanceTo(access, STAGE_PERCIVAL_SENT)
        objbox(WHISTLE, "You give Sir Percival one of your magic whistles.")
        chatPlayer(
            neutral,
            "Blow it beneath the watchtower north-west of Brimhaven. I'll follow with my own.",
        )
        chatNpc(happy, "A king. Ha! Wait until Lancelot hears.")
        depart(npc)
    }

    private fun Dialogue.depart(npc: Npc) {
        emerged.remove(player.uid)
        if (!npc.isSlotAssigned) {
            return
        }
        access.spotanimMap(worldRepo, DEPART_SPOTANIM, npc.coords, DEPART_HEIGHT)
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun ProtectedAccess.blowOnFeather() {
        anim(BLOW_SEQ)
        val stage = quest.stage(player)
        if (stage >= STAGE_PERCIVAL_SENT || stage < STAGE_FEATHER) {
            mes("You blow on the feather. It lies still; there is nothing it wants to find.")
            return
        }
        mes(featherPointing(coords))
    }

    companion object {
        /** The house on the east side of Goblin Village, where Percival is tied in the sacks. */
        val SACKS = CoordGrid(2962, 3506, 0)

        /** Beside the sacks, where Percival stands once he is out. */
        val PERCIVAL_TILE = CoordGrid(2961, 3506, 0)

        const val PERCIVAL_TICKS = 200
        const val OPEN_SEQ = "seq.human_pickuptable"
        const val BLOW_SEQ = "seq.brain_player_blow_whistle"
        const val DEPART_SPOTANIM = "spotanim.smokepuff"
        const val DEPART_HEIGHT = 124

        private const val NEARBY = 3
        private const val UNDERGROUND_Z = 6400
        private const val CLOSE = 40

        /** What the feather shows from [from]: which way the sacks lie and roughly how far. */
        fun featherPointing(from: CoordGrid): String {
            if (from.level != SACKS.level || from.z > UNDERGROUND_Z || percivalUnreachable(from)) {
                return "You blow on the feather. It spins slowly, lost; what it seeks is not in this world."
            }
            val dx = SACKS.x - from.x
            val dz = SACKS.z - from.z
            val distance = max(abs(dx), abs(dz))
            if (distance <= NEARBY) {
                return "You blow on the feather. It points straight down at the sacks beside you."
            }
            val far = if (distance <= CLOSE) "It quivers eagerly; you are close." else "It strains far into the distance."
            return "You blow on the feather. It turns to point ${direction(dx, dz)}. $far"
        }

        private fun percivalUnreachable(from: CoordGrid): Boolean =
            FisherRealm.isInRealm(from)

        private fun direction(dx: Int, dz: Int): String {
            val north = dz > abs(dx) / 2
            val south = -dz > abs(dx) / 2
            val east = dx > abs(dz) / 2
            val west = -dx > abs(dz) / 2
            return when {
                north && east -> "north-east"
                north && west -> "north-west"
                south && east -> "south-east"
                south && west -> "south-west"
                north -> "north"
                south -> "south"
                east -> "east"
                else -> "west"
            }
        }
    }
}
