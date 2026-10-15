package org.rsmod.content.quest.area.mortton.shades

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.SERUM_207
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.SERUM_208
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.VIAL_EMPTY
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Serum 207 and 208 on the afflicted of Mort'ton. A dose turns the villager back into a Mort'ton
 * local for a while, for everyone, and the grateful local hands over a small gift; Serum 208's
 * gifts are richer. Razmire and Ulsquire are cured through [cure] too but reward the player with
 * their conversation instead.
 */
@Singleton
class SerumCures
@Inject
constructor(private val random: GameRandom, private val world: WorldRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        for ((afflicted, local) in LOCALS) {
            onOpNpc1(afflicted) { startDialogue(it.npc) { chatNpc(confused, random.pick(MUMBLES)) } }
            onOpNpc1(local) { startDialogue(it.npc) { chatNpc(neutral, random.pick(LOCAL_LINES)) } }
            for (serum in SERUM_207 + SERUM_208) {
                onOpNpcU(afflicted, serum) { cureLocal(it.npc, local, serum) }
                onOpNpcU(local, serum) { mes("They don't seem to need any more serum.") }
            }
        }
    }

    /** Spends one dose of [serum] and returns whether it was Serum 208. */
    fun spendDose(access: ProtectedAccess, serum: String): Boolean {
        val permanent = serum in SERUM_208
        val doses = if (permanent) SERUM_208 else SERUM_207
        val index = doses.indexOf(serum)
        val remaining = if (index == 0) VIAL_EMPTY else doses[index - 1]
        access.invReplace(access.inv, serum, 1, remaining)
        return permanent
    }

    fun cure(npc: Npc, into: String, ticks: Int = CURE_TICKS) {
        npc.transmog(npcType(into), ticks)
        world.soundArea(npc, CURE_SOUND)
    }

    private suspend fun ProtectedAccess.cureLocal(npc: Npc, local: String, serum: String) {
        val permanent = spendDose(this, serum)
        anim(POUR_SEQ)
        cure(npc, local)
        delay(1)
        val (gift, count) = gift(permanent)
        startDialogue(npc) {
            chatNpc(happy, "Oh! My head is clear at last! Thank you, thank you so much!")
            chatNpc(happy, "Please, take this as a token of my thanks.")
        }
        if (invAdd(inv, gift, count, strict = false).success) {
            objbox(gift, "The villager gives you a gift.")
        }
    }

    private fun gift(permanent: Boolean): Pair<String, Int> {
        val table = if (permanent) PERMANENT_GIFTS else TEMPORARY_GIFTS
        val (obj, range) = random.pick(table)
        return obj to random.of(range)
    }

    private fun npcType(name: String): NpcServerType =
        requireNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { "Missing npc: $name" }

    companion object {
        /** How long a dose keeps the afflicted clear-headed: about three minutes. */
        const val CURE_TICKS = 300

        const val POUR_SEQ = "seq.human_pickuptable"
        const val CURE_SOUND = "synth.vial_mix"

        val LOCALS =
            mapOf(
                "npc.mort_afflicted_man" to "npc.mort_man",
                "npc.mort_afflicted_woman" to "npc.mort_woman",
                "npc.mort_afflicted_man2" to "npc.mort_man2",
                "npc.mort_afflicted_woman2" to "npc.mort_woman2",
            )

        val MUMBLES =
            listOf(
                "Ughhhh... mmmm... Mort'ton...",
                "Uuurrrggghhh... shades... everywhere...",
                "Mmmmrrr... cold... so cold...",
                "Nnnngh... go away... go away...",
                "Haaa... the mist... the mist...",
            )

        val LOCAL_LINES =
            listOf(
                "It's so good to be able to think straight again, but I can feel the mist " +
                    "creeping back already.",
                "Thank you for the serum, stranger. Watch out for the Shades!",
                "Mort'ton used to be a lovely place, you know. Before the mists.",
            )

        val TEMPORARY_GIFTS =
            listOf(
                "obj.coins" to 10..50,
                "obj.oliveoil1" to 1..1,
                "obj.tarromin" to 1..1,
                "obj.swamppaste" to 1..3,
                "obj.ashes" to 1..1,
            )

        val PERMANENT_GIFTS =
            listOf(
                "obj.coins" to 50..200,
                "obj.oliveoil4" to 1..1,
                "obj.tarromin" to 1..2,
                "obj.swamppaste" to 5..10,
                "obj.shadekey_bronze_bloodred" to 1..1,
            )
    }
}
