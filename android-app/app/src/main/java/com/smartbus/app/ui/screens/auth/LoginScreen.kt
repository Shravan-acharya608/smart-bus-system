package com.smartbus.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartbus.app.SmartBusApplication
import com.smartbus.app.data.model.UserRole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onNavigateToPassenger: () -> Unit,
    onNavigateToDriver: () -> Unit,
    onNavigateToConductor: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SmartBusApplication
    val scope = rememberCoroutineScope()

    var selectedRole by remember { mutableStateOf(UserRole.PASSENGER) }
    var email by remember { mutableStateOf("passenger@smartbus.local") }
    var password by remember { mutableStateOf("Passenger@123456") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showServerConfigDialog by remember { mutableStateOf(false) }
    val savedBaseUrl by app.authRepository.baseUrlFlow.collectAsState(initial = com.smartbus.app.BuildConfig.BASE_URL)
    var currentBaseUrl by remember { mutableStateOf(savedBaseUrl) }

    LaunchedEffect(savedBaseUrl) {
        currentBaseUrl = savedBaseUrl
    }

    LaunchedEffect(selectedRole) {
        // Pre-fill demo accounts for convenience
        when (selectedRole) {
            UserRole.PASSENGER -> {
                email = "passenger@smartbus.local"
                password = "Passenger@123456"
            }
            UserRole.DRIVER -> {
                email = "driver@smartbus.local"
                password = "Driver@123456"
            }
            UserRole.CONDUCTOR -> {
                email = "conductor@smartbus.local"
                password = "Conductor@123456"
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartBus Transit", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showServerConfigDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Server Config")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header icon
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.DirectionsBus,
                        contentDescription = "Bus Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Welcome to SmartBus",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Select your role to sign in",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Role selection tabs
            TabRow(
                selectedTabIndex = when (selectedRole) {
                    UserRole.PASSENGER -> 0
                    UserRole.DRIVER -> 1
                    UserRole.CONDUCTOR -> 2
                    else -> 0
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.background(Color.Transparent, RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedRole == UserRole.PASSENGER,
                    onClick = { selectedRole = UserRole.PASSENGER },
                    text = { Text("Passenger", fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedRole == UserRole.DRIVER,
                    onClick = { selectedRole = UserRole.DRIVER },
                    text = { Text("Driver", fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.DriveEta, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedRole == UserRole.CONDUCTOR,
                    onClick = { selectedRole = UserRole.CONDUCTOR },
                    text = { Text("Conductor", fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error banner
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Input fields
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter both email and password."
                        return@Button
                    }
                    errorMessage = null
                    isLoading = true
                    scope.launch {
                        val result = app.authRepository.login(email.trim(), password, selectedRole)
                        isLoading = false
                        result.onSuccess { user ->
                            when (user.role) {
                                UserRole.PASSENGER -> onNavigateToPassenger()
                                UserRole.DRIVER -> onNavigateToDriver()
                                UserRole.CONDUCTOR -> onNavigateToConductor()
                                else -> {
                                    errorMessage = "Please use the Web Portal for Admin/Owner access."
                                }
                            }
                        }.onFailure { err ->
                            errorMessage = err.message ?: "Login failed. Check server connection."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Sign In as ${selectedRole.name.lowercase().capitalize()}", fontWeight = FontWeight.SemiBold)
                }
            }

            if (selectedRole == UserRole.PASSENGER) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onNavigateToRegister) {
                    Text("New passenger? Create an account")
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Driver & Conductor accounts are provisioned by the Bus Administrator.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }

    // Server Config Dialog
    if (showServerConfigDialog) {
        var tempUrl by remember { mutableStateOf(currentBaseUrl) }
        AlertDialog(
            onDismissRequest = { showServerConfigDialog = false },
            title = { Text("Server Configuration") },
            text = {
                Column {
                    Text(
                        "Quick Presets for Demo:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { tempUrl = "http://192.168.43.196:3000/api/v1/" },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Wi-Fi IP", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { tempUrl = "http://127.0.0.1:3000/api/v1/" },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("USB / ADB", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { tempUrl = "http://10.0.2.2:3000/api/v1/" },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Emulator", fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("Backend URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    currentBaseUrl = tempUrl
                    app.updateBaseUrl(tempUrl)
                    scope.launch { app.authRepository.setBaseUrl(tempUrl) }
                    showServerConfigDialog = false
                }) {
                    Text("Save & Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerConfigDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
