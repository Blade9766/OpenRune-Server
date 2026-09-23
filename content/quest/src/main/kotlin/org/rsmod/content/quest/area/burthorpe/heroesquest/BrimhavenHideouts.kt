package org.rsmod.content.quest.area.burthorpe.heroesquest

import dev.openrune.types.ObjectServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_DOOR
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.PHOENIX_ALFONSE
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.PHOENIX_CHARLIE
import org.rsmod.content.quest.area.burthorpe.heroesquest.npcs.ALFONSE
import org.rsmod.content.quest.area.burthorpe.heroesquest.npcs.GRUBOR
import org.rsmod.content.quest.area.burthorpe.heroesquest.npcs.gruborAtDoor
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The gangs' ways in on Brimhaven: the Shrimp and Parrot's kitchen door and the secret panel from
 * the kitchen into Mr Olbors' garden (Phoenix Gang), and the Black Arm hideout's barred door on
 * Palm Street. Every one of them lets a player on the inside back out.
 */
class BrimhavenHideouts
@Inject
constructor(
    private val heroes: HeroesQuest,
    private val passages: GenericPassageScript,
    private val search: NpcSearch,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(KITCHEN_DOOR) { kitchenDoor(it.vis, it.type) }
        onOpLoc1(KITCHEN_PANEL) { kitchenPanel(it.vis, it.type) }
        onOpLoc1(HIDEOUT_DOOR) { hideoutDoor(it.vis, it.type) }
    }

    private suspend fun ProtectedAccess.kitchenDoor(door: BoundLocInfo, type: ObjectServerType) {
        val leaving = coords.z > door.coords.z
        if (leaving || heroes.phoenixAt(player, PHOENIX_ALFONSE)) {
            with(passages) { walkThrough(door, type) }
            return
        }
        arriveDelay()
        val alfonse = npcFind(coords, ALFONSE, ALFONSE_RANGE, HuntVis.Off, search)
        if (alfonse == null) {
            mes("This door seems to be locked...")
            return
        }
        startDialogue(alfonse) {
            chatNpc(angry, "Hey! You can't go through there! That's private property!")
        }
    }

    private suspend fun ProtectedAccess.kitchenPanel(panel: BoundLocInfo, type: ObjectServerType) {
        val fromGarden = coords.x < panel.coords.x
        if (fromGarden || heroes.phoenixAt(player, PHOENIX_CHARLIE)) {
            with(passages) { walkThrough(panel, type) }
            return
        }
        arriveDelay()
        mes(constants.dm_default)
    }

    private suspend fun ProtectedAccess.hideoutDoor(door: BoundLocInfo, type: ObjectServerType) {
        val leaving = coords.x >= door.coords.x
        if (leaving || heroes.blackArmAt(player, BLACKARM_DOOR)) {
            with(passages) { walkThrough(door, type) }
            return
        }
        arriveDelay()
        val grubor = npcFind(coords, GRUBOR, GRUBOR_RANGE, HuntVis.Off, search)
        if (grubor == null) {
            soundSynth(LOCKED_SOUND)
            mes("The door is locked.")
            return
        }
        startDialogue(grubor) { gruborAtDoor(heroes) }
    }

    private companion object {
        const val KITCHEN_DOOR = "loc.herokitchendoor"
        const val KITCHEN_PANEL = "loc.herokitchenpanel"
        const val HIDEOUT_DOOR = "loc.grubordoor"

        const val ALFONSE_RANGE = 10
        const val GRUBOR_RANGE = 10

        const val LOCKED_SOUND = "synth.locked"
    }
}
