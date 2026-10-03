package com.bolohisab.data.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `version 1 files still decode, with no products or history`() {
        val v1 = """
            {"version":1,"exportedAt":1,"customers":[{"id":1,"name":"রহিম","phone":null,"createdAt":1}],
             "entries":[{"id":7,"customerId":1,"type":"CREDIT_SALE","totalPoisha":32000,"paidPoisha":10000,
             "balanceDelta":22000,"note":null,"transcript":"t","createdAt":2,"deletedAt":null,"items":[]}]}
        """.trimIndent()
        val payload = json.decodeFromString(BackupPayload.serializer(), v1)
        assertEquals(1, payload.version)
        assertEquals(1, payload.entries.size)
        assertTrue(payload.products.isEmpty())
        assertTrue(payload.history.isEmpty())
    }

    @Test
    fun `products and history round-trip`() {
        val payload = BackupPayload(
            version = BACKUP_FORMAT_VERSION,
            exportedAt = 1,
            customers = emptyList(),
            entries = emptyList(),
            products = listOf(BackupProduct(3, "চাল", "KG", 12.5, 5.0, 1)),
            history = listOf(BackupHistory(7, 2, "CREDIT_SALE", 32000, 10000, 22000, null, "t")),
        )
        val decoded = json.decodeFromString(BackupPayload.serializer(), json.encodeToString(BackupPayload.serializer(), payload))
        assertEquals(payload, decoded)
    }
}
