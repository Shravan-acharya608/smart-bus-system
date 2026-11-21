package com.smartbus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartbus.app.data.model.UserRole
import com.smartbus.app.ui.navigation.Screen
import com.smartbus.app.ui.screens.auth.LoginScreen
import com.smartbus.app.ui.screens.auth.RegisterScreen
import com.smartbus.app.ui.screens.conductor.ConductorDashboardScreen
import com.smartbus.app.ui.screens.driver.DriverDashboardScreen
import com.smartbus.app.ui.screens.passenger.PassengerDashboardScreen
import com.smartbus.app.ui.theme.SmartBusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SmartBusApplication

        setContent {
            SmartBusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val token by app.authRepository.accessTokenFlow.collectAsState(initial = null)
                    val userRole by app.authRepository.userRoleFlow.collectAsState(initial = null)

                    val startDestination = remember(token, userRole) {
                        if (!token.isNullOrEmpty() && userRole != null) {
                            when (userRole) {
                                UserRole.PASSENGER -> Screen.PassengerDashboard.route
                                UserRole.DRIVER -> Screen.DriverDashboard.route
                                UserRole.CONDUCTOR -> Screen.ConductorDashboard.route
                                else -> Screen.Login.route
                            }
                        } else {
                            Screen.Login.route
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable(Screen.Login.route) {
                            LoginScreen(
                                onNavigateToPassenger = {
                                    navController.navigate(Screen.PassengerDashboard.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                },
                                onNavigateToDriver = {
                                    navController.navigate(Screen.DriverDashboard.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                },
                                onNavigateToConductor = {
                                    navController.navigate(Screen.ConductorDashboard.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                },
                                onNavigateToRegister = {
                                    navController.navigate(Screen.Register.route)
                                }
                            )
                        }

                        composable(Screen.Register.route) {
                            RegisterScreen(
                                onNavigateToLogin = {
                                    navController.popBackStack()
                                },
                                onRegistrationSuccess = {
                                    navController.navigate(Screen.PassengerDashboard.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Screen.PassengerDashboard.route) {
                            PassengerDashboardScreen(
                                onLogout = {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Screen.DriverDashboard.route) {
                            DriverDashboardScreen(
                                onLogout = {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Screen.ConductorDashboard.route) {
                            ConductorDashboardScreen(
                                onLogout = {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
