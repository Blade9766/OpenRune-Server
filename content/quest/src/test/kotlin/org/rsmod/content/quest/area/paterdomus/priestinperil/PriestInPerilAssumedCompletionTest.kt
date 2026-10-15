package org.rsmod.content.quest.area.paterdomus.priestinperil

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
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_VARP
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PriestInPerilAssumedCompletionTest {
    private val quest = PriestInPerilQuest()

    @Test fun `an untouched quest counted done shows the client drezel in the mausoleum`() = under(QuestRequirementMode.AssumeCompleted) {
        val (player, client) = player()
        assertTrue(quest.isAssumedComplete(player))
        quest.showAssumedCompletion(player)
        assertEquals(listOf(STAGE_COMPLETE), client.stageWrites(), "the client alone sees the endstate")
        assertEquals(0, quest.stage(player), "the saved stage stays untouched")
    }

    @Test fun `a started quest keeps its real stage on the client`() = under(QuestRequirementMode.AssumeCompleted) {
        val (player, client) = player()
        quest.quest.jumpToStage(player, PriestInPerilQuest.STAGE_STARTED)
        client.messages.clear()
        assertFalse(quest.isAssumedComplete(player))
        quest.showAssumedCompletion(player)
        assertEquals(emptyList<Int>(), client.stageWrites())
    }

    @Test fun `the server accepts talking to drezel in the mausoleum before his form resolves`() {
        val base = checkNotNull(ServerCacheManager.getNpc(PriestInPerilQuest.DREZEL_MAUSOLEUM.asRSCM(RSCMType.NPC)))
        assertTrue(base.hasOp(1), "a stage that resolves no form falls back to the base type's ops")
    }

    @Test fun `respecting progress leaves an untouched quest untouched`() = under(QuestRequirementMode.RespectProgress) {
        val (player, client) = player()
        assertFalse(quest.isAssumedComplete(player))
        quest.showAssumedCompletion(player)
        assertEquals(emptyList<Int>(), client.stageWrites())
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
