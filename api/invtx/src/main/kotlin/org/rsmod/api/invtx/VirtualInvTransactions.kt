package org.rsmod.api.invtx

import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.inv.storage.VirtualItemConsumePolicy
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.objtx.TransactionResult
import org.rsmod.objtx.TransactionResultList

internal lateinit var cachedPlayerItemStorage: PlayerItemStorage

internal fun Player.invDelWithVirtualStorage(
    inv: Inventory,
    type: String,
    count: Int,
    slot: Int?,
    strict: Boolean,
    placehold: Boolean,
    autoCommit: Boolean,
    ignoreVirtualStorage: Boolean,
    raw: (
        inv: Inventory,
        type: String,
        count: Int,
        slot: Int?,
        strict: Boolean,
        placehold: Boolean,
        autoCommit: Boolean,
    ) -> TransactionResultList<InvObj>,
): TransactionResultList<InvObj> {
    val storage = cachedPlayerItemStorage
    if (ignoreVirtualStorage || !storage.isManaged(this, inv, type)) {
        return raw(inv, type, count, slot, strict, placehold, autoCommit)
    }

    val physical = inv.physicalCount(type)
    val virtual = storage.additionalCount(this, inv, type)
    val total = physical + virtual

    if (strict && total < count) {
        return raw(inv, type, count, slot, strict, placehold, autoCommit)
    }

    val policy = storage.consumePolicy(this, inv, type)
    val (fromInv, fromStorage) =
        when (policy) {
            VirtualItemConsumePolicy.InventoryFirst -> {
                minOf(count, physical) to (count - minOf(count, physical))
            }
            VirtualItemConsumePolicy.StorageFirst -> {
                val fromStore = minOf(count, virtual)
                (count - fromStore) to fromStore
            }
        }

    if (fromStorage <= 0) {
        return raw(inv, type, fromInv, slot, true, placehold, autoCommit)
    }

    val removed = storage.removeFromStorage(this, inv, type, fromStorage)
    if (removed < fromStorage) {
        storage.storeIncoming(this, inv, type, removed)
        return raw(inv, type, count, slot, strict, placehold, autoCommit)
    }

    val result =
        if (fromInv > 0) {
            val invResult = raw(inv, type, fromInv, slot, true, placehold, false)
            if (invResult.failure) {
                storage.storeIncoming(this, inv, type, removed)
                return invResult
            }
            invResult.withCompletedCount(count)
        } else {
            virtualOnlyTransactionResult(count)
        }
    if (autoCommit) {
        result.commitAll()
    } else {
        storage.storeIncoming(this, inv, type, removed)
        result.onCommit { storage.removeFromStorage(this, inv, type, fromStorage) }
    }
    return result
}

internal fun Player.invAddWithVirtualStorage(
    inv: Inventory,
    type: String,
    count: Int,
    vars: Int,
    slot: Int?,
    strict: Boolean,
    cert: Boolean,
    uncert: Boolean,
    autoCommit: Boolean,
    ignoreVirtualStorage: Boolean,
    raw: (
        inv: Inventory,
        type: String,
        count: Int,
        vars: Int,
        slot: Int?,
        strict: Boolean,
        cert: Boolean,
        uncert: Boolean,
        autoCommit: Boolean,
    ) -> TransactionResultList<InvObj>,
): TransactionResultList<InvObj> {
    val storage = cachedPlayerItemStorage
    if (ignoreVirtualStorage || !storage.isManaged(this, inv, type)) {
        return raw(inv, type, count, vars, slot, strict, cert, uncert, autoCommit)
    }

    val redirected = storage.storeIncoming(this, inv, type, count)
    if (redirected <= 0) {
        return raw(inv, type, count, vars, slot, strict, cert, uncert, autoCommit)
    }

    val toInv = count - redirected
    val result =
        if (toInv > 0) {
            val invResult = raw(inv, type, toInv, vars, slot, strict, cert, uncert, false)
            if (invResult.failure) {
                storage.removeFromStorage(this, inv, type, redirected)
                return invResult
            }
            invResult.withCompletedCount(redirected + invResult.completed())
        } else {
            virtualOnlyTransactionResult(count)
        }
    if (autoCommit) {
        result.commitAll()
    } else {
        storage.removeFromStorage(this, inv, type, redirected)
        result.onCommit { storage.storeIncoming(this, inv, type, redirected) }
    }
    return result
}

private fun virtualOnlyTransactionResult(count: Int): TransactionResultList<InvObj> =
    TransactionResultList(
        output = { null },
        inventories = emptyList(),
        results = listOf(TransactionResult.Ok(requested = count, completed = count)),
    )

private fun TransactionResultList<InvObj>.withCompletedCount(
    completed: Int,
): TransactionResultList<InvObj> {
    val requested = results.firstOrNull() as? TransactionResult.Ok ?: return this
    return TransactionResultList(
        output = output,
        inventories = inventories,
        results = listOf(TransactionResult.Ok(requested = requested.requested, completed = completed)),
        err = err,
    )
}
