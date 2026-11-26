package com.smartbus.app.data.repository

import com.smartbus.app.data.model.*
import com.smartbus.app.data.remote.ApiService

class TransitRepository(private val apiService: ApiService) {

    suspend fun getRoutes(origin: String? = null, destination: String? = null): Result<List<Route>> {
        return try {
            val res = apiService.getRoutes(origin, destination)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data ?: emptyList())
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to fetch routes"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTrips(status: String? = null, routeId: String? = null): Result<List<Trip>> {
        return try {
            val res = apiService.getTrips(status, routeId)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data ?: emptyList())
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to fetch trips"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTripById(tripId: String): Result<Trip> {
        return try {
            val res = apiService.getTripById(tripId)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data!!)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Trip not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startTrip(tripId: String): Result<Trip> {
        return try {
            val res = apiService.startTrip(tripId)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data!!)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to start trip"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun endTrip(tripId: String): Result<Trip> {
        return try {
            val res = apiService.endTrip(tripId)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data!!)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to end trip"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun postLocation(tripId: String, payload: LocationPayload): Result<Unit> {
        return try {
            val res = apiService.postLocation(tripId, payload)
            if (res.isSuccessful) Result.success(Unit)
            else Result.failure(Exception(res.body()?.error?.message ?: "Failed to update location"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun bookTicket(tripId: String, boardingStopId: String, destinationStopId: String): Result<Ticket> {
        return try {
            val res = apiService.bookTicket(CreateTicketRequest(tripId, boardingStopId, destinationStopId))
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data!!)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to book ticket"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyTickets(): Result<List<Ticket>> {
        return try {
            val res = apiService.getMyTickets()
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data ?: emptyList())
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to fetch tickets"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStops(): Result<List<Stop>> {
        return try {
            val res = apiService.getStops()
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data ?: emptyList())
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to fetch stops"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchBuses(boardingStopId: String, destinationStopId: String): Result<List<BusSearchResult>> {
        return try {
            val res = apiService.searchBuses(boardingStopId, destinationStopId)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data ?: emptyList())
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to search buses"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFrequentCommutes(): Result<List<FrequentRouteItem>> {
        return try {
            val res = apiService.getFrequentCommutes()
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data ?: emptyList())
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to fetch frequent commutes"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun holdTicket(tripId: String, boardingStopId: String, destinationStopId: String): Result<Ticket> {
        return try {
            val res = apiService.holdTicket(HoldTicketRequest(tripId, boardingStopId, destinationStopId))
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data!!)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to reserve seat"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportIncident(tripId: String, severity: String, description: String, delayMinutes: Int = 0): Result<Unit> {
        return try {
            val res = apiService.reportIncident(tripId, IncidentReportRequest(severity, description, delayMinutes))
            if (res.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to report incident"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shareJourney(tripId: String, durationMinutes: Int = 180): Result<JourneyShareResponse> {
        return try {
            val res = apiService.createJourneyShare(JourneyShareRequest(tripId, durationMinutes))
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data!!)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to share journey"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun applyForJob(request: JobApplicationRequest): Result<String> {
        return try {
            val res = apiService.applyForJob(request)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()?.message ?: "Application submitted successfully!")
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to submit application"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPermanentPass(): Result<PermanentPassData> {
        return try {
            val res = apiService.getPermanentPass()
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data!!)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to load permanent pass"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun regeneratePermanentPass(): Result<PermanentPassData> {
        return try {
            val res = apiService.regeneratePermanentPass()
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data!!)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to update pass"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun topUpWallet(amount: Double): Result<Double> {
        return try {
            val res = apiService.topUpWallet(WalletTopUpRequest(amount))
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data!!.walletBalance)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Top-up failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStaffDetails(): Result<StaffDetailsResponse> {
        return try {
            val res = apiService.getStaffDetails()
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data!!)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to load staff details"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun payInstantTicket(
        tripId: String,
        boardingStopId: String,
        destinationStopId: String,
        paymentMode: String
    ): Result<InstantPayResponse> {
        return try {
            val res = apiService.payInstantTicket(
                InstantPayRequest(tripId, boardingStopId, destinationStopId, paymentMode)
            )
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data!!)
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Payment failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUdupiSchedules(query: String? = null, to: String? = null): Result<List<UdupiScheduleItem>> {
        return try {
            val res = apiService.getUdupiSchedules(query, to)
            if (res.isSuccessful && res.body()?.success == true) {
                Result.success(res.body()!!.data ?: emptyList())
            } else {
                Result.failure(Exception(res.body()?.error?.message ?: "Failed to load schedules"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
