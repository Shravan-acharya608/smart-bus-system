package com.smartbus.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_events")
data class OfflineEventEntity(
    @PrimaryKey
    val clientEventId: String,
    val eventType: String, // "OFFLINE_TICKET", "BOARDING", "EXIT"
    val tripId: String,
    val ticketId: String? = null,
    val ticketCode: String? = null,
    val boardingStopId: String? = null,
    val destinationStopId: String? = null,
    val fare: Double? = null,
    val paymentMethod: String = "CASH",
    val timestamp: String,
    val isSynced: Boolean = false
)

data class SyncPayload(
    val events: List<OfflineSyncItem>
)

data class OfflineSyncItem(
    val clientEventId: String,
    val eventType: String,
    val tripId: String,
    val ticketId: String? = null,
    val ticketCode: String? = null,
    val boardingStopId: String? = null,
    val destinationStopId: String? = null,
    val fare: Double? = null,
    val paymentMethod: String = "CASH",
    val timestamp: String
)

data class SyncResponseData(
    val summary: SyncSummary,
    val results: List<SyncResultItem>
)

data class SyncSummary(
    val total: Int,
    val processed: Int,
    val skippedDuplicates: Int,
    val errors: Int
)

data class SyncResultItem(
    val clientEventId: String,
    val eventType: String,
    val status: String,
    val message: String? = null
)
