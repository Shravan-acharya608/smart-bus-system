package com.smartbus.app.data.repository

import com.smartbus.app.data.local.OfflineEventDao
import com.smartbus.app.data.model.*
import com.smartbus.app.data.remote.ApiService
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ConductorRepository(
    private val apiService: ApiService,
    private val offlineEventDao: OfflineEventDao
) {
    val pendingSyncCount: Flow<Int> = offlineEventDao.getPendingCountFlow()

    suspend fun verifyTicket(ticketCode: String, tripId: String): Result<VerifyTicketResponse> {
        return try {
            val res = apiService.verifyTicket(VerifyTicketRequest(ticketCode, tripId))
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data!!)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Ticket verification failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordBoarding(ticketId: String, tripId: String, stopId: String?): Result<String> {
        val clientEventId = "board_${UUID.randomUUID()}"
        return try {
            val res = apiService.recordBoarding(ticketId, BoardingExitRequest(stopId, clientEventId))
            if (res.isSuccessful) {
                Result.success("Boarding recorded online")
            } else {
                // If network/server error, queue in Room database for offline sync
                offlineEventDao.insertEvent(
                    OfflineEventEntity(
                        clientEventId = clientEventId,
                        eventType = "BOARDING",
                        tripId = tripId,
                        ticketId = ticketId,
                        boardingStopId = stopId,
                        timestamp = java.time.Instant.now().toString(),
                        isSynced = false
                    )
                )
                Result.success("Network unavailable. Boarding saved locally for offline sync.")
            }
        } catch (_: Exception) {
            // Queue offline
            offlineEventDao.insertEvent(
                OfflineEventEntity(
                    clientEventId = clientEventId,
                    eventType = "BOARDING",
                    tripId = tripId,
                    ticketId = ticketId,
                    boardingStopId = stopId,
                    timestamp = java.time.Instant.now().toString(),
                    isSynced = false
                )
            )
            Result.success("Offline: Boarding queued locally")
        }
    }

    suspend fun recordExit(ticketId: String, tripId: String, stopId: String?): Result<String> {
        val clientEventId = "exit_${UUID.randomUUID()}"
        return try {
            val res = apiService.recordExit(ticketId, BoardingExitRequest(stopId, clientEventId))
            if (res.isSuccessful) {
                Result.success("Exit recorded online")
            } else {
                offlineEventDao.insertEvent(
                    OfflineEventEntity(
                        clientEventId = clientEventId,
                        eventType = "EXIT",
                        tripId = tripId,
                        ticketId = ticketId,
                        destinationStopId = stopId,
                        timestamp = java.time.Instant.now().toString(),
                        isSynced = false
                    )
                )
                Result.success("Network unavailable. Exit saved locally for offline sync.")
            }
        } catch (_: Exception) {
            offlineEventDao.insertEvent(
                OfflineEventEntity(
                    clientEventId = clientEventId,
                    eventType = "EXIT",
                    tripId = tripId,
                    ticketId = ticketId,
                    destinationStopId = stopId,
                    timestamp = java.time.Instant.now().toString(),
                    isSynced = false
                )
            )
            Result.success("Offline: Exit queued locally")
        }
    }

    suspend fun issueOnboardTicket(
        tripId: String,
        boardingStopId: String,
        destinationStopId: String,
        fare: Double,
        paymentMethod: String = "CASH"
    ): Result<String> {
        val clientEventId = "ticket_${UUID.randomUUID()}"
        return try {
            val res = apiService.issueOnboardTicket(
                IssueOnboardTicketRequest(
                    tripId = tripId,
                    boardingStopId = boardingStopId,
                    destinationStopId = destinationStopId,
                    fare = fare,
                    paymentMethod = paymentMethod,
                    clientEventId = clientEventId
                )
            )
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success("Onboard ticket issued and boarding recorded")
            } else {
                // Offline fallback
                offlineEventDao.insertEvent(
                    OfflineEventEntity(
                        clientEventId = clientEventId,
                        eventType = "OFFLINE_TICKET",
                        tripId = tripId,
                        boardingStopId = boardingStopId,
                        destinationStopId = destinationStopId,
                        fare = fare,
                        paymentMethod = paymentMethod,
                        timestamp = java.time.Instant.now().toString(),
                        isSynced = false
                    )
                )
                Result.success("Offline cash ticket issued & stored locally.")
            }
        } catch (_: Exception) {
            offlineEventDao.insertEvent(
                OfflineEventEntity(
                    clientEventId = clientEventId,
                    eventType = "OFFLINE_TICKET",
                    tripId = tripId,
                    boardingStopId = boardingStopId,
                    destinationStopId = destinationStopId,
                    fare = fare,
                    paymentMethod = paymentMethod,
                    timestamp = java.time.Instant.now().toString(),
                    isSynced = false
                )
            )
            Result.success("Offline cash ticket stored locally")
        }
    }

    suspend fun syncOfflineRecords(): Result<SyncSummary> {
        return try {
            val unsynced = offlineEventDao.getUnsyncedEvents()
            if (unsynced.isEmpty()) {
                return Result.success(SyncSummary(0, 0, 0, 0))
            }

            val payload = SyncPayload(
                events = unsynced.map {
                    OfflineSyncItem(
                        clientEventId = it.clientEventId,
                        eventType = it.eventType,
                        tripId = it.tripId,
                        ticketId = it.ticketId,
                        ticketCode = it.ticketCode,
                        boardingStopId = it.boardingStopId,
                        destinationStopId = it.destinationStopId,
                        fare = it.fare,
                        paymentMethod = it.paymentMethod,
                        timestamp = it.timestamp
                    )
                }
            )

            val res = apiService.syncOfflineEvents(payload)
            if (res.isSuccessful && res.body()?.success == true) {
                val data = res.body()!!.data!!
                // Mark processed or skipped items as synced
                data.results.forEach { resultItem ->
                    if (resultItem.status == "PROCESSED" || resultItem.status == "SKIPPED_DUPLICATE") {
                        offlineEventDao.markAsSynced(resultItem.clientEventId)
                    }
                }
                Result.success(data.summary)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Sync failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPassengerCount(tripId: String): Result<PassengerCount> {
        return try {
            val res = apiService.getPassengerCount(tripId)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data!!)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to get passenger count"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
