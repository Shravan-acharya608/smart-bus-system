package com.smartbus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.firstOrNull

sealed interface InitialAuth {
    object Checking : InitialAuth
    data class Resolved(val destination: String) : InitialAuth
}

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
                    var initialAuth by remember { mutableStateOf<InitialAuth>(InitialAuth.Checking) }

                    LaunchedEffect(Unit) {
                        val token = app.authRepository.accessTokenFlow.firstOrNull()
                        val role = app.authRepository.userRoleFlow.firstOrNull()

                        val destination = if (!token.isNullOrEmpty() && role != null) {
                            when (role) {
                                UserRole.PASSENGER -> Screen.PassengerDashboard.route
                                UserRole.DRIVER -> Screen.DriverDashboard.route
                                UserRole.CONDUCTOR -> Screen.ConductorDashboard.route
                                else -> Screen.Login.route
                            }
                        } else {
                            Screen.Login.route
                        }
                        initialAuth = InitialAuth.Resolved(destination)
                    }

                    when (val state = initialAuth) {
                        is InitialAuth.Checking -> {
                            SmartBusSplashScreen()
                        }
                        is InitialAuth.Resolved -> {
                            val navController = rememberNavController()

                            NavHost(
                                navController = navController,
                                startDestination = state.destination
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
    }
}

@Composable
fun SmartBusSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_smartbus_logo),
                contentDescription = "SmartBus Logo",
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "SmartBus Transit",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Connected Public Transit System",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8)
                )
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                color = Color(0xFF0284C7),
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
