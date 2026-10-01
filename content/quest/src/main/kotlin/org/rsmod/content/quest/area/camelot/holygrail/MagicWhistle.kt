package org.rsmod.content.quest.area.camelot.holygrail

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.DEAD_ARRIVAL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_ENTRANA
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_PERCIVAL_SENT
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_REALM_ENTERED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_REALM_RESTORED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.TOWER
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.TOWER_RADIUS
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.WHISTLE
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Blowing a magic whistle. Beneath the Brimhaven watchtower it carries the player into the Fisher
 * Realm: the dying copy of the map until Percival has gone home, the healed copy from then on.
 * Inside either realm it carries them back to the tower. Anywhere else it only makes a thin note,
 * and says so, so nobody is left wondering whether the whistle is broken.
 *
 * The first arrival in each realm is the moment the stage moves on, which is also what keeps the
 * restoration from being shown twice.
 */
class MagicWhistle @Inject constructor(private val quest: HolyGrailQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(WHISTLE) { blow() }
    }

    suspend fun ProtectedAccess.blow() {
        anim(BLOW_SEQ)
        if (FisherRealm.isInRealm(coords)) {
            mes("You blow the whistle. The realm thins around you like mist.")
            travel(TOWER)
            mes("You are standing beneath the watchtower north-west of Brimhaven.")
            return
        }
        val nearTower = coords.level == 0 && coords.chebyshevDistance(TOWER) <= TOWER_RADIUS
        if (!nearTower) {
            mes("You blow the whistle. A thin, sweet note drifts away, and nothing else happens.")
            if (quest.stage(player) >= STAGE_ENTRANA) {
                mes("The crone said it must be blown beneath the tower north-west of Brimhaven.")
            }
            return
        }
        val realm = quest.realmFor(player)
        if (realm == null) {
            mes("You blow the whistle beneath the tower. The air shivers, then settles. You are not expected.")
            return
        }
        mes("You blow the whistle. The note hangs in the air, and the world folds around it.")
        travel(realm)
        if (realm == DEAD_ARRIVAL) arriveInDyingRealm() else arriveInHealedRealm()
    }

    private suspend fun ProtectedAccess.travel(dest: CoordGrid) {
        delay(TRAVEL_DELAY)
        spotanim(TRAVEL_SPOTANIM)
        telejump(dest, TeleportType.Exempt)
        delay(1)
    }

    private suspend fun ProtectedAccess.arriveInDyingRealm() {
        if (quest.stage(player) >= STAGE_REALM_ENTERED) {
            mes("You are back in the grey country of the Fisher King.")
            return
        }
        quest.advanceTo(this, STAGE_REALM_ENTERED)
        mesbox(
            "Grey light, no birdsong. The grass crumbles underfoot and the trees stand bare. " +
                "Somewhere to the west, across a bridge, a castle broods over a slow, dark river.",
        )
    }

    private suspend fun ProtectedAccess.arriveInHealedRealm() {
        if (quest.stage(player) != STAGE_PERCIVAL_SENT) {
            mes("Birdsong and warm light: the Fisher Realm, as King Percival keeps it.")
            return
        }
        quest.advanceTo(this, STAGE_REALM_RESTORED)
        mesbox(
            "You hardly know the place. The fields are green and high, the river runs clear and " +
                "bright, and sheep graze where there was only ash.",
        )
        mesbox("Even the light is warmer. The black titan is gone from the bridge. The realm is healed.")
    }

    companion object {
        const val BLOW_SEQ = "seq.brain_player_blow_whistle"
        const val TRAVEL_SPOTANIM = "spotanim.smokepuff"
        const val TRAVEL_DELAY = 2
    }
}
