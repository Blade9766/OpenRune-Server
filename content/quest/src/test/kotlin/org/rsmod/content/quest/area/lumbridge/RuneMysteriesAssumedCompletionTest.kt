package org.rsmod.content.quest.area.lumbridge

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest.Companion.STAGE_VARP
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RuneMysteriesAssumedCompletionTest {
    private val quest = RuneMysteriesQuest()

    @Test fun `an untouched quest counted done shows the client the teleport ops`() = under(QuestRequirementMode.AssumeCompleted) {
        val (player, client) = player()
        assertTrue(quest.isAssumedComplete(player))
        assertTrue(quest.isUnlocked(player))
        quest.showAssumedCompletion(player)
        assertEquals(listOf(STAGE_COMPLETE), client.stageWrites(), "the client alone sees the endstate")
        assertEquals(0, quest.stage(player), "the saved stage stays untouched")
    }

    @Test fun `a started quest keeps its real stage on the client`() = under(QuestRequirementMode.AssumeCompleted) {
        val (player, client) = player()
        quest.quest.jumpToStage(player, RuneMysteriesQuest.STAGE_TALISMAN_GIVEN)
        client.messages.clear()
        assertFalse(quest.isAssumedComplete(player))
        quest.showAssumedCompletion(player)
        assertEquals(emptyList<Int>(), client.stageWrites())
    }

    @Test fun `respecting progress leaves an untouched quest untouched`() = under(QuestRequirementMode.RespectProgress) {
        val (player, client) = player()
        assertFalse(quest.isAssumedComplete(player))
        quest.showAssumedCompletion(player)
        assertEquals(emptyList<Int>(), client.stageWrites())
    }

    @Test fun `the server accepts teleport on the forms a saved stage of 0 resolves`() {
        val ops = mapOf("npc.aubury_2op" to 4, "npc.head_wizard_1op" to 3, "npc.guild_wizard_1op" to 3, "npc.gnome_brimstail_1op" to 3)
        for ((form, op) in ops) {
            val type = checkNotNull(ServerCacheManager.getNpc(form.asRSCM(RSCMType.NPC))) { form }
            assertTrue(type.hasOp(op), form)
            assertTrue(type.hasOp(1), "$form keeps its Talk-to")
        }
    }

    private fun player(): Pair<Player, RecordingClient> {
        val client = RecordingClient()
        return Player().apply { this.client = client } to client
    }

    private fun under(mode: QuestRequirementMode, block: () -> Unit) {
        val previous = QuestRequirements.activePolicy()
        QuestRequirements.install(QuestRequirementPolicy(mode))
        try {
            block()
        } finally {
            QuestRequirements.install(previous)
        }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        private val varpId by lazy { STAGE_VARP.asRSCM(RSCMType.VARP) }

        fun stageWrites(): List<Int> = messages.filterIsInstance<VarpSmall>().filter { it.id == varpId }.map { it.value }

        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
