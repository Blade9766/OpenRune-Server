package org.rsmod.content.quest.area.camelot.merlinscrystal

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.CRYSTAL
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.EXCALIBUR
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.GLASS_BREAK_SOUND
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.MERLIN
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.SMOKE_PUFF_SPOTANIM
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_FREED_MERLIN
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_SPIRIT_BOUND
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The giant crystal at the top of Camelot's south-east tower.
 *
 * Only Excalibur can break it, and only once Thrantax has lifted Morgan's protection. Shattering
 * the crystal deletes the loc, which ends the op script with it, so Merlin's thanks are launched
 * from the world queue on the cycle after the crystal goes.
 */
@Singleton
class GiantCrystal
@Inject
constructor(
    private val quest: MerlinsCrystalQuest,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CRYSTAL) { smash(it.loc, withExcalibur = carriesExcalibur()) }
        onOpLocU(CRYSTAL, EXCALIBUR) { smash(it.loc, withExcalibur = true) }
        onOpNpc1(MERLIN) {
            startDialogue(it.npc) {
                chatNpc(
                    confused,
                    "Excuse me for rushing off like this, but I must get back to my workroom.",
                )
            }
            if (it.npc.isSlotAssigned) {
                npcRepo.del(it.npc, Int.MAX_VALUE)
            }
        }
    }

    private fun ProtectedAccess.carriesExcalibur(): Boolean =
        inv.contains(EXCALIBUR) || worn.contains(EXCALIBUR)

    private suspend fun ProtectedAccess.smash(crystal: BoundLocInfo, withExcalibur: Boolean) {
        arriveDelay()
        faceSquare(crystal.coords)
        when {
            quest.isComplete(player) -> {
                mes("You have already freed Merlin from the giant crystal.")
                return
            }
            quest.stage(player) == STAGE_FREED_MERLIN -> {
                mesbox(
                    "You have already freed Merlin from the crystal.<br>" +
                        "Go and see King Arthur for your reward.",
                )
                return
            }
        }
        mes("You attempt to smash the crystal...")
        if (!withExcalibur) {
            mes("... but you fail to have any effect on it.")
            return
        }
        if (quest.stage(player) < STAGE_SPIRIT_BOUND) {
            mes("... but it seems to be protected by a dark force.")
            return
        }
        mes("... and it shatters under the force of Excalibur!")
        soundSynth(GLASS_BREAK_SOUND)
        spotanimMap(worldRepo, SMOKE_PUFF_SPOTANIM, crystal.coords, SHARD_HEIGHT)
        quest.advanceTo(this, STAGE_FREED_MERLIN)

        val uid = player.uid
        locRepo.del(crystal, SHATTER_TICKS)
        worldQueues.add(1) { thankYou(uid) }
    }

    /**
     * Merlin steps out of the wreckage, thanks whoever let him out and hurries off to his
     * workroom. He is his own npc for the length of the scene; nothing else in Camelot has him.
     */
    private fun thankYou(uid: PlayerUid) {
        val player: Player = uid.resolve(playerList) ?: return
        val merlin = Npc(MERLIN, MERLIN_COORDS)
        merlin.respawns = false
        npcRepo.add(merlin, SCENE_TICKS)
        merlin.facePlayer(player)
        launcher.launch(player) {
            startDialogue(merlin) {
                chatNpc(happy, "Thank you! Thank you! Thank you!")
                chatNpc(shocked, "It's not fun being trapped in a giant crystal!")
                chatNpc(happy, "Go speak to King Arthur, I'm sure he'll reward you!")
                mesbox("You have set Merlin free. Now talk to King Arthur.")
                chatNpc(
                    confused,
                    "Excuse me for rushing off like this, but I must get back to my workroom.",
                )
            }
            if (merlin.isSlotAssigned) {
                npcRepo.del(merlin, Int.MAX_VALUE)
            }
        }
    }

    private companion object {
        const val SHATTER_TICKS = 100
        const val SCENE_TICKS = 100
        const val SHARD_HEIGHT = 60

        /** Just west of the crystal's footprint, on the tower's top floor. */
        val MERLIN_COORDS = CoordGrid(2766, 3493, 2)
    }
}
