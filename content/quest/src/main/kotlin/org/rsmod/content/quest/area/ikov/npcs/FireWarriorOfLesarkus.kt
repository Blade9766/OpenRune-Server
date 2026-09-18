package org.rsmod.content.quest.area.ikov.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.quiver
import org.rsmod.api.player.righthand
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.FIRE_WARRIOR
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.ICE_ARROWS
import org.rsmod.content.quest.area.ikov.ikovFireWarriorSlain
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Fire Warrior of Lesarkus, who holds the door at the north end of his corridor.
 *
 * He is a thing of fire and nothing forged touches him: only an arrow of ice off the icy path
 * takes a point off him, whether it is nocked on a bow or carried in the quiver behind a set of
 * knives. Every other blow, spell and arrow lands for nothing.
 */
class FireWarriorOfLesarkus
@Inject
constructor(
    private val death: NpcDeath,
    private val ammo: RangedAmmoManager,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    private val warriorType: NpcServerType =
        ServerCacheManager.getNpc(FIRE_WARRIOR.asRSCM(RSCMType.NPC))
            ?: error("Missing npc: $FIRE_WARRIOR")

    override fun ScriptContext.startup() {
        onOpNpc1(FIRE_WARRIOR) { startDialogue(it.npc) { challenge() } }
        onModifyNpcHit(warriorType) {
            val source = hit.sourceUid?.let { PlayerUid(it).resolve(playerList) }
            if (hit.isFromPlayer && source?.holdingIceArrows() != true) {
                hit.damage = 0
            }
        }
        onNpcQueue(warriorType, "queue.death") { warriorDefeated() }
    }

    private suspend fun Dialogue.challenge() {
        chatNpc(angry, "Halt! The temple is closed. Who art thou?")
        val answer = choice2("A mighty hero!", true, "A humble pilgrim.", false)
        if (answer) {
            chatPlayer(happy, "A mighty hero!")
            chatNpc(
                angry,
                "Then thou wilt burn like every other hero who has stood where thou standest. " +
                    "Thou shalt not pass!",
            )
        } else {
            chatPlayer(neutral, "A humble pilgrim.")
            chatNpc(
                bored,
                "Pilgrims are turned away at the gate. The temple is closed, and I am the closing " +
                    "of it. Go back.",
            )
        }
    }

    /** The one thing that hurts him: ice arrows on the string, or in the quiver behind anything. */
    private fun Player.holdingIceArrows(): Boolean {
        if (quiver?.isType(ICE_ARROWS) == true) {
            return true
        }
        val weapon: ItemServerType = righthand?.let { ServerCacheManager.getItem(it.id) } ?: return false
        return ammo.activeAmmo(this, weapon)?.isType(ICE_ARROWS) == true
    }

    private suspend fun StandardNpcAccess.warriorDefeated() {
        val killer = findHero(playerList)
        death.deathWithDrops(this)
        if (killer == null) {
            return
        }
        launcher.launch(killer) {
            player.ikovFireWarriorSlain = true
            soundSynth(TempleOfIkovQuest.SOUND_FIRE_TELEPORT)
            mes("The Fire Warrior gutters out, and the door behind him swings loose.")
        }
    }
}
