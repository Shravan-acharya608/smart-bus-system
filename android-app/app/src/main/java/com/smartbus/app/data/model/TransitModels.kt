package com.smartbus.app.data.model

data class Bus(
    val id: String,
    val busNumber: String,
    val registrationNo: String,
    val capacity: Int,
    val status: String = "ACTIVE",
    val make: String? = null,
    val model: String? = null
)

data class Stop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)

data class RouteStop(
    val id: String,
    val stopId: String,
    val stopOrder: Int,
    val distanceFromStart: Double = 0.0,
    val fareFromStart: Double = 0.0,
    val stop: Stop
)

data class Route(
    val id: String,
    val name: String,
    val code: String,
    val description: String? = null,
    val isActive: Boolean = true,
    val stops: List<RouteStop> = emptyList()
)
