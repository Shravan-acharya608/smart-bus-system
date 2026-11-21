package com.smartbus.app.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object PassengerDashboard : Screen("passenger_dashboard")
    object DriverDashboard : Screen("driver_dashboard")
    object ConductorDashboard : Screen("conductor_dashboard")
}
