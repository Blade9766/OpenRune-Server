package org.rsmod.content.skills.prayer.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess

internal fun ProtectedAccess.exchangeNotes(note: String, unnoted: String, count: Int, fee: Int): Boolean =
    player.invTransaction(inv) {
        val target = select(inv)
        delete {
            this.from = target
            this.obj = "obj.coins".asRSCM(RSCMType.OBJ)
            this.strictCount = fee
        }
        delete {
            this.from = target
            this.obj = note.asRSCM(RSCMType.OBJ)
            this.strictCount = count
        }
        insert {
            this.into = target
            this.obj = unnoted.asRSCM(RSCMType.OBJ)
            this.strictCount = count
        }
    }.success
