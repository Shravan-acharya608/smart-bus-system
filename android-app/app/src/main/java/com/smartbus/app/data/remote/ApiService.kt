package com.smartbus.app.data.remote

import com.smartbus.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthResponseData>>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<AuthResponseData>>

    @POST("auth/logout")
    suspend fun logout(@Body body: Map<String, String>): Response<ApiResponse<Any>>

    // Routes
    @GET("routes")
    suspend fun getRoutes(
        @Query("origin") origin: String? = null,
        @Query("destination") destination: String? = null,
        @Query("search") search: String? = null
    ): Response<ApiResponse<List<Route>>>

    @GET("routes/{id}")
    suspend fun getRouteById(@Path("id") id: String): Response<ApiResponse<Route>>

    // Trips & GPS Tracking
    @GET("trips")
    suspend fun getTrips(
        @Query("status") status: String? = null,
        @Query("routeId") routeId: String? = null,
        @Query("date") date: String? = null
    ): Response<ApiResponse<List<Trip>>>

    @GET("trips/{id}")
    suspend fun getTripById(@Path("id") id: String): Response<ApiResponse<Trip>>

    @POST("trips/{id}/start")
    suspend fun startTrip(@Path("id") id: String): Response<ApiResponse<Trip>>

    @POST("trips/{id}/end")
    suspend fun endTrip(@Path("id") id: String): Response<ApiResponse<Trip>>

    @POST("trips/{id}/location")
    suspend fun postLocation(
        @Path("id") id: String,
        @Body payload: LocationPayload
    ): Response<ApiResponse<Any>>

    @GET("trips/{id}/location")
    suspend fun getTripLocation(@Path("id") id: String): Response<ApiResponse<LocationUpdate>>

    @GET("trips/{id}/passenger-count")
    suspend fun getPassengerCount(@Path("id") id: String): Response<ApiResponse<PassengerCount>>

    // Tickets
    @POST("tickets")
    suspend fun bookTicket(@Body request: CreateTicketRequest): Response<ApiResponse<Ticket>>

    @GET("tickets/my-tickets")
    suspend fun getMyTickets(): Response<ApiResponse<List<Ticket>>>

    @GET("tickets/{id}")
    suspend fun getTicketById(@Path("id") id: String): Response<ApiResponse<Ticket>>

    @POST("tickets/verify")
    suspend fun verifyTicket(@Body request: VerifyTicketRequest): Response<ApiResponse<VerifyTicketResponse>>

    @POST("tickets/{id}/board")
    suspend fun recordBoarding(
        @Path("id") id: String,
        @Body request: BoardingExitRequest
    ): Response<ApiResponse<Any>>

    @POST("tickets/{id}/exit")
    suspend fun recordExit(
        @Path("id") id: String,
        @Body request: BoardingExitRequest
    ): Response<ApiResponse<Any>>

    @POST("tickets/onboard")
    suspend fun issueOnboardTicket(
        @Body request: IssueOnboardTicketRequest
    ): Response<ApiResponse<Ticket>>

    // Offline Bulk Synchronization
    @POST("events/sync")
    suspend fun syncOfflineEvents(@Body payload: SyncPayload): Response<ApiResponse<SyncResponseData>>

    // Stops & Search
    @GET("routes/stops")
    suspend fun getStops(): Response<ApiResponse<List<Stop>>>

    @GET("routes/search-buses")
    suspend fun searchBuses(
        @Query("boardingStopId") boardingStopId: String,
        @Query("destinationStopId") destinationStopId: String
    ): Response<ApiResponse<List<BusSearchResult>>>

    // Frequent Commutes
    @GET("commutes/frequent")
    suspend fun getFrequentCommutes(): Response<ApiResponse<List<FrequentRouteItem>>>

    // Hold Ticket
    @POST("tickets/hold")
    suspend fun holdTicket(@Body request: HoldTicketRequest): Response<ApiResponse<Ticket>>

    // Incidents & Delays
    @POST("incidents/trips/{tripId}/report")
    suspend fun reportIncident(
        @Path("tripId") tripId: String,
        @Body request: IncidentReportRequest
    ): Response<ApiResponse<Any>>

    // Family Journey Sharing
    @POST("journey-sharing")
    suspend fun createJourneyShare(
        @Body request: JourneyShareRequest
    ): Response<ApiResponse<JourneyShareResponse>>

    @DELETE("journey-sharing/{id}")
    suspend fun revokeJourneyShare(@Path("id") id: String): Response<ApiResponse<Any>>

    // Job Recruitment, Permanent Pass, Wallet, Staff Details & Instant Pay
    @POST("jobs/apply")
    suspend fun applyForJob(@Body request: JobApplicationRequest): Response<ApiResponse<Any>>

    @GET("auth/pass")
    suspend fun getPermanentPass(): Response<ApiResponse<PermanentPassData>>

    @POST("auth/pass/regenerate")
    suspend fun regeneratePermanentPass(): Response<ApiResponse<PermanentPassData>>

    @POST("auth/wallet/topup")
    suspend fun topUpWallet(@Body request: WalletTopUpRequest): Response<ApiResponse<WalletBalanceData>>

    @GET("auth/staff-details")
    suspend fun getStaffDetails(): Response<ApiResponse<StaffDetailsResponse>>

    @POST("tickets/pay-instant")
    suspend fun payInstantTicket(@Body request: InstantPayRequest): Response<ApiResponse<InstantPayResponse>>

    @GET("routes/udupi-schedules")
    suspend fun getUdupiSchedules(
        @Query("q") q: String? = null,
        @Query("to") to: String? = null
    ): Response<ApiResponse<List<UdupiScheduleItem>>>
}
