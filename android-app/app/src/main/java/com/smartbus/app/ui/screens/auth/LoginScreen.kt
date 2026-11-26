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
import com.smartbus.app.data.model.JobApplicationRequest
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
    var showJobApplyDialog by remember { mutableStateOf(false) }
    var jobApplySuccessMsg by remember { mutableStateOf<String?>(null) }
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

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            if (jobApplySuccessMsg != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(
                        jobApplySuccessMsg!!,
                        color = Color(0xFF065F46),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedCard(
                onClick = { showJobApplyDialog = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFFDBEAFE),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.WorkOutline,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Looking for a Bus Driver / Conductor Job?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "Apply here — sent directly to Bus Owners for hiring",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
    }

    // Driver / Conductor Job Application Dialog
    if (showJobApplyDialog) {
        var fullName by remember { mutableStateOf("") }
        var jobRole by remember { mutableStateOf(if (selectedRole == UserRole.CONDUCTOR) "CONDUCTOR" else "DRIVER") }
        var jobPhone by remember { mutableStateOf("") }
        var jobEmail by remember { mutableStateOf("") }
        var licenseNo by remember { mutableStateOf("") }
        var badgeNo by remember { mutableStateOf("") }
        var expYears by remember { mutableStateOf("3") }
        var prefRoute by remember { mutableStateOf("Udupi – Katpadi – Shirva") }
        var expSalary by remember { mutableStateOf("23000") }
        var jobSubmitting by remember { mutableStateOf(false) }
        var jobErr by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!jobSubmitting) showJobApplyDialog = false },
            title = { Text("Driver / Conductor Job Signup", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Your profile will appear on the Bus Owner's Recruitment Board for immediate hiring.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    if (jobErr != null) {
                        Text(jobErr!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = jobRole == "DRIVER",
                            onClick = { jobRole = "DRIVER" },
                            label = { Text("Driver") }
                        )
                        FilterChip(
                            selected = jobRole == "CONDUCTOR",
                            onClick = { jobRole = "CONDUCTOR" },
                            label = { Text("Conductor") }
                        )
                    }
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name*") }, singleLine = true)
                    OutlinedTextField(value = jobPhone, onValueChange = { jobPhone = it }, label = { Text("Mobile Number*") }, singleLine = true)
                    OutlinedTextField(value = jobEmail, onValueChange = { jobEmail = it }, label = { Text("Email Address*") }, singleLine = true)
                    OutlinedTextField(value = licenseNo, onValueChange = { licenseNo = it }, label = { Text("HMV License / Conductor No.*") }, singleLine = true)
                    OutlinedTextField(value = badgeNo, onValueChange = { badgeNo = it }, label = { Text("Badge Number (Optional)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = expYears, onValueChange = { expYears = it }, label = { Text("Exp (Yrs)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = expSalary, onValueChange = { expSalary = it }, label = { Text("Salary ₹") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = prefRoute, onValueChange = { prefRoute = it }, label = { Text("Preferred Route") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    enabled = !jobSubmitting,
                    onClick = {
                        if (fullName.isBlank() || jobPhone.isBlank() || jobEmail.isBlank() || licenseNo.isBlank()) {
                            jobErr = "Please fill Name, Phone, Email and License Number."
                            return@Button
                        }
                        jobSubmitting = true
                        jobErr = null
                        scope.launch {
                            app.transitRepository.applyForJob(
                                JobApplicationRequest(
                                    fullName = fullName.trim(),
                                    role = jobRole,
                                    phone = jobPhone.trim(),
                                    email = jobEmail.trim(),
                                    licenseNumber = licenseNo.trim(),
                                    badgeNumber = badgeNo.trim().ifBlank { null },
                                    experienceYears = expYears.toIntOrNull() ?: 1,
                                    preferredRoutes = prefRoute.trim(),
                                    expectedSalary = expSalary.toDoubleOrNull() ?: 22000.0
                                )
                            ).onSuccess { msg ->
                                jobApplySuccessMsg = "✅ $msg"
                                showJobApplyDialog = false
                            }.onFailure { err ->
                                jobErr = err.message ?: "Failed to submit"
                            }
                            jobSubmitting = false
                        }
                    }
                ) {
                    Text(if (jobSubmitting) "Submitting..." else "Submit Application")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJobApplyDialog = false }) { Text("Cancel") }
            }
        )
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
                        Button(
                            onClick = { tempUrl = "https://smart-bus-backend-two.vercel.app/api/v1/" },
                            modifier = Modifier.weight(1.3f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("☁️ Cloud (Vercel)", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { tempUrl = "http://10.0.2.2:3000/api/v1/" },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Emulator", fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
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
