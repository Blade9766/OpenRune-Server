package org.rsmod.api.player.output

import net.rsprot.compression.provider.BlockingHuffmanCodecProvider
import net.rsprot.protocol.game.outgoing.camera.CamLookAtV3
import net.rsprot.protocol.game.outgoing.camera.CamMoveToV3
import net.rsprot.protocol.game.outgoing.prot.DesktopGameMessageEncoderRepository
import net.rsprot.protocol.message.OutgoingMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.rsmod.api.testing.capture.CaptureClient
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class CameraTest {
    private val client = CaptureClient()
    private val player = Player(client = client)
    private val instanced = CoordGrid(11299, 92)

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `every camera packet has a registered desktop encoder`() {
        Camera.camReset(player)
        Camera.camLookAt(player, instanced, height = 450, rate = 2, rate2 = 10)
        Camera.camMoveTo(player, instanced, height = 1500, rate = 2, rate2 = 10)
        Camera.camLookAtV3(player, instanced, height = -20, rate = 2, rate2 = 10, true)
        Camera.camMoveToV3(player, instanced, height = -20, rate = 2, rate2 = 10, true)
        Camera.camShakeResetAll(player)
        Camera.camUnlock(player, unlock = false)

        val encoders = DesktopGameMessageEncoderRepository.build(BlockingHuffmanCodecProvider())
        for (message in client.outgoingMessages) {
            val outgoing = message as OutgoingMessage
            assertDoesNotThrow("${outgoing.javaClass.simpleName} has no encoder") {
                encoders.getEncoder(outgoing.javaClass)
            }
        }
    }

    @Test
    fun `camMoveTo sends absolute coords with an absolute height`() {
        Camera.camMoveTo(player, instanced, height = 2500, rate = 100, rate2 = 50)
        assertEquals(
            CamMoveToV3(11299, 92, 2500, 100, 50, heightRelative = false),
            client.single<CamMoveToV3>(),
        )
    }

    @Test
    fun `camLookAt sends absolute coords with an absolute height`() {
        Camera.camLookAt(player, instanced, height = 450, rate = 2, rate2 = 10)
        assertEquals(
            CamLookAtV3(11299, 92, 450, 2, 10, heightRelative = false),
            client.single<CamLookAtV3>(),
        )
    }
}
