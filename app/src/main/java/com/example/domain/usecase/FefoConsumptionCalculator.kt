package com.example.domain.usecase

data class BatchAllocationInput(
    val batchId: String,
    val remainingQuantity: Double,
    val expiryDateMillis: Long?,
    val createdAtMillis: Long
)

data class BatchAllocationItem(
    val batchId: String,
    val consumedQuantity: Double,
    val newRemainingQuantity: Double,
    val isExhausted: Boolean
)

data class FefoAllocationResult(
    val allocations: List<BatchAllocationItem>,
    val totalConsumed: Double,
    val unfulfilledQuantity: Double
)

/**
 * Implements FEFO (First Expired, First Out).
 * Batches with an expiry date are ordered ascending by expiryDateMillis (earliest first),
 * followed by batches without an expiry date ordered by creation time (FIFO tie-breaker).
 */
object FefoConsumptionCalculator {
    fun allocate(
        batches: List<BatchAllocationInput>,
        requestedQuantity: Double
    ): FefoAllocationResult {
        require(requestedQuantity > 0.0) { "La quantità richiesta deve essere maggiore di zero." }

        val ordered = batches
            .filter { it.remainingQuantity > 0.0 }
            .sortedWith(
                compareBy<BatchAllocationInput>(
                    { it.expiryDateMillis == null },
                    { it.expiryDateMillis ?: Long.MAX_VALUE },
                    { it.createdAtMillis }
                )
            )

        var remainingToAllocate = requestedQuantity
        val allocations = mutableListOf<BatchAllocationItem>()

        for (batch in ordered) {
            if (remainingToAllocate <= 1e-9) break
            val take = minOf(batch.remainingQuantity, remainingToAllocate)
            val updatedRemaining = (batch.remainingQuantity - take).coerceAtLeast(0.0)
            val exhausted = updatedRemaining <= 1e-6
            allocations.add(
                BatchAllocationItem(
                    batchId = batch.batchId,
                    consumedQuantity = take,
                    newRemainingQuantity = if (exhausted) 0.0 else updatedRemaining,
                    isExhausted = exhausted
                )
            )
            remainingToAllocate -= take
        }

        val totalConsumed = allocations.sumOf { it.consumedQuantity }
        val unfulfilled = (requestedQuantity - totalConsumed).coerceAtLeast(0.0)

        return FefoAllocationResult(
            allocations = allocations,
            totalConsumed = totalConsumed,
            unfulfilledQuantity = if (unfulfilled <= 1e-6) 0.0 else unfulfilled
        )
    }
}
