package org.rsmod.content.quest.area.varrock.shieldofarrav

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpPlayerU
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BLACKARM_CERTIFICATE
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.CROSSBOW
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_CERTIFICATE
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.WEAPON_STORE_KEY
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Shield of Arrav needs two players to swap items, and some can't trade (ironmen, new accounts).
 * Using the weapon store key, a Phoenix crossbow or a half-certificate on another player drops it
 * at the user's feet where only that player can see it.
 */
class ArravHandover @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        for ((obj, label) in HANDOVERS) {
            onOpPlayerU(objType(obj)) { handOver(it.target, obj, label) }
        }
    }

    private suspend fun ProtectedAccess.handOver(target: Player, obj: String, label: String) {
        val confirmed =
            choice2("Yes.", true, "No.", false, title = "Drop your $label for ${target.displayName} to take?")
        if (!confirmed || inv.count(obj) == 0) {
            return
        }
        invDel(inv, obj)
        objRepo.add(obj, coords, DROP_TICKS, receiver = target, reveal = DROP_TICKS)
        soundSynth(DROP_SOUND)
        target.mes("<col=ff0000>${player.displayName} has dropped something for you to take.</col>")
    }

    private companion object {
        const val DROP_TICKS = 200
        const val DROP_SOUND = "synth.put_down"

        val HANDOVERS =
            listOf(
                WEAPON_STORE_KEY to "key",
                CROSSBOW to "crossbow",
                PHOENIX_CERTIFICATE to "certificate",
                BLACKARM_CERTIFICATE to "certificate",
            )

        fun objType(obj: String): ItemServerType =
            ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: error("Missing obj: $obj")
    }
}
