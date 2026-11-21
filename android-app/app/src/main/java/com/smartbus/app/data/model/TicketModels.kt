package com.smartbus.app.data.model

data class Ticket(
    val id: String,
    val ticketCode: String,
    val passengerId: String,
    val tripId: String,
    val boardingStopId: String,
    val destinationStopId: String,
    val fare: Double,
    val status: String,
    val paymentMethod: String,
    val isOfflineIssued: Boolean = false,
    val qrData: String? = null,
    val createdAt: String,
    val trip: Trip? = null
)

data class CreateTicketRequest(
    val tripId: String,
    val boardingStopId: String,
    val destinationStopId: String,
    val paymentMethod: String = "ONLINE"
)

data class VerifyTicketRequest(
    val ticketCode: String,
    val tripId: String
)

data class VerifyTicketResponse(
    val ticketId: String,
    val ticketCode: String,
    val passengerName: String,
    val fare: Double,
    val status: String,
    val paymentStatus: String?,
    val paymentMethod: String,
    val isValid: Boolean,
    val isHeldPaymentRequired: Boolean? = false,
    val amountToCollect: Double? = 0.0
)

data class BusLocationInfo(
    val lat: Double,
    val lng: Double,
    val timestamp: String? = null
)

data class StopInfo(
    val id: String,
    val name: String,
    val order: Int = 0
)

data class BusSearchResult(
    val tripId: String,
    val busId: String,
    val busNumber: String,
    val routeName: String,
    val routeCode: String,
    val status: String,
    val scheduledStart: String,
    val seatsAvailable: Int,
    val seatsTotal: Int,
    val fare: Double,
    val lastLocation: BusLocationInfo? = null,
    val boardingStop: StopInfo,
    val destinationStop: StopInfo
)

data class FrequentRouteItem(
    val id: String,
    val passengerId: String,
    val boardingStopId: String,
    val destinationStopId: String,
    val routeId: String? = null,
    val tripCount: Int = 1,
    val lastUsedAt: String? = null,
    val customLabel: String? = null,
    val isPinned: Boolean = false,
    val boardingStop: Stop,
    val destinationStop: Stop
)

data class IncidentReportRequest(
    val severity: String,
    val description: String,
    val delayMinutes: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class HoldTicketRequest(
    val tripId: String,
    val boardingStopId: String,
    val destinationStopId: String,
    val clientEventId: String? = null
)

data class IssueOnboardTicketRequest(
    val tripId: String,
    val boardingStopId: String,
    val destinationStopId: String,
    val fare: Double,
    val paymentMethod: String = "CASH",
    val clientEventId: String? = null
)

data class BoardingExitRequest(
    val stopId: String? = null,
    val clientEventId: String? = null
)

data class JourneyShareRequest(
    val tripId: String,
    val durationMinutes: Int = 180,
    val sharedWithEmail: String? = null
)

data class JourneyShareResponse(
    val shareId: String,
    val shareToken: String,
    val shareUrl: String,
    val expiresAt: String
)
