package com.smartbus.app.data.model

data class JobApplicationRequest(
    val fullName: String,
    val role: String = "DRIVER",
    val phone: String,
    val email: String,
    val licenseNumber: String,
    val badgeNumber: String? = null,
    val experienceYears: Int = 1,
    val preferredRoutes: String = "Udupi – Katpadi – Shirva",
    val expectedSalary: Double = 22000.0
)

data class PermanentPassData(
    val passCode: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val walletBalance: Double,
    val qrPayload: String
)

data class WalletTopUpRequest(
    val amount: Double
)

data class WalletBalanceData(
    val walletBalance: Double
)

data class InstantPayRequest(
    val tripId: String,
    val boardingStopId: String,
    val destinationStopId: String,
    val paymentMode: String = "RAZORPAY",
    val razorpayRef: String? = null
)

data class InstantPayResponse(
    val ticket: Ticket,
    val walletBalance: Double
)

data class StaffProfileInfo(
    val fullName: String,
    val email: String,
    val phone: String,
    val role: String,
    val licenseNo: String,
    val badgeNo: String,
    val assignedBus: String,
    val assignedRoute: String,
    val shiftTiming: String,
    val rating: Double,
    val bloodGroup: String,
    val emergencyContact: String,
    val joinedDate: String
)

data class SalaryHistoryEntry(
    val month: String,
    val netAmount: Double,
    val status: String,
    val paidOn: String
)

data class StaffSalaryInfo(
    val month: String,
    val baseSalary: Double,
    val dailyBhatta: Double,
    val dutyDays: Int,
    val totalBhatta: Double,
    val tripIncentive: Double,
    val deductions: Double,
    val netPayable: Double,
    val status: String,
    val lastPaidDate: String,
    val paymentMethod: String,
    val history: List<SalaryHistoryEntry>
)

data class StaffDetailsResponse(
    val profile: StaffProfileInfo,
    val salary: StaffSalaryInfo
)

data class UdupiStopCoord(
    val order: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val stopId: String? = null
)

data class UdupiScheduleItem(
    val slNo: Int,
    val busNumber: String,
    val tripId: String? = null,
    val tripStatus: String = "SCHEDULED",
    val from: String,
    val to: String,
    val serviceClass: String,
    val via: String,
    val departureTime: String,
    val stops: List<UdupiStopCoord> = emptyList()
)
