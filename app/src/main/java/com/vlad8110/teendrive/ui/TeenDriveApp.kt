package com.vlad8110.teendrive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.core.content.ContextCompat
import com.vlad8110.teendrive.data.AccountPreferences
import com.vlad8110.teendrive.data.AccountState
import com.vlad8110.teendrive.data.TeenDriveDatabase
import com.vlad8110.teendrive.data.TripRepository
import com.vlad8110.teendrive.firebase.FirebaseAccountRepository
import com.vlad8110.teendrive.firebase.ParentTeenTrip
import com.vlad8110.teendrive.firebase.ParentTripRepository
import com.vlad8110.teendrive.location.ActiveDriveTrackingService
import com.vlad8110.teendrive.location.ActiveDriveSnapshot
import com.vlad8110.teendrive.location.LocationSnapshot
import com.vlad8110.teendrive.location.TeenDriveLocationProvider
import com.vlad8110.teendrive.location.driveDurationText
import com.vlad8110.teendrive.location.oneDecimal
import com.vlad8110.teendrive.model.AccountRole
import com.vlad8110.teendrive.model.ActiveTeenDrive
import com.vlad8110.teendrive.model.ConnectedTeen
import com.vlad8110.teendrive.model.PairingPayload
import com.vlad8110.teendrive.model.RoutePoint
import com.vlad8110.teendrive.model.SafetyAlert
import com.vlad8110.teendrive.model.TeenTrip
import com.vlad8110.teendrive.sync.TeenDriveSyncScheduler
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    TeenHome("teen-home", "Home", Icons.Filled.Home),
    TeenDrive("teen-drive", "Drive", Icons.Filled.DirectionsCar),
    TeenReports("teen-reports", "Reports", Icons.Filled.Report),
    TeenProfile("teen-profile", "Profile", Icons.Filled.Person),
    ParentDashboard("parent-dashboard", "Parent", Icons.Filled.Groups),
    ParentPairing("parent-pairing", "Pair", Icons.Filled.QrCodeScanner),
}

data class CloudUiState(
    val message: String = "Ready",
    val uid: String? = null,
    val fcmPreview: String? = null,
    val error: String? = null,
    val isSyncing: Boolean = false,
)

@Composable
fun TeenDriveApp(
    accountPreferences: AccountPreferences,
    firebaseAccountRepository: FirebaseAccountRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val accountState by accountPreferences.state.collectAsState(initial = AccountState())
    var cloudState by remember { mutableStateOf(CloudUiState()) }
    val scope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    fun syncAccount(nextState: AccountState) {
        scope.launch {
            cloudState = cloudState.copy(message = "Syncing account...", error = null, isSyncing = true)
            runCatching {
                val result = if (nextState.selectedRole == AccountRole.TEEN) {
                    firebaseAccountRepository.syncTeenProfile(nextState)
                } else {
                    firebaseAccountRepository.syncParentProfile(nextState)
                }
                accountPreferences.saveAccountState(result.state)
                TeenDriveSyncScheduler.requestNow(context)
                cloudState = CloudUiState(
                    message = result.statusMessage,
                    uid = result.uid,
                    fcmPreview = result.fcmToken?.take(12),
                    isSyncing = false,
                )
            }.onFailure { exception ->
                cloudState = CloudUiState(
                    message = "Cloud sync failed",
                    error = exception.localizedMessage,
                    isSyncing = false,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (!accountState.hasSelectedRole) {
        RoleSelectionScreen(
            accountState = accountState,
            onSelectRole = { role, displayName ->
                val nextState = accountState.copy(
                    hasSelectedRole = true,
                    selectedRole = role,
                    displayName = displayName.trim(),
                )
                scope.launch { accountPreferences.saveAccountState(nextState) }
                TeenDriveSyncScheduler.requestNow(context)
                syncAccount(nextState)
            },
            modifier = modifier,
        )
    } else {
        TeenDriveNavShell(
            accountState = accountState,
            cloudState = cloudState,
            onSaveName = { name ->
                val nextState = accountState.copy(displayName = name.trim())
                scope.launch { accountPreferences.saveAccountState(nextState) }
                TeenDriveSyncScheduler.requestNow(context)
                syncAccount(nextState)
            },
            onSync = { syncAccount(accountState) },
            onClaimPairing = { payload ->
                scope.launch {
                    cloudState = cloudState.copy(message = "Claiming pairing token...", error = null, isSyncing = true)
                    runCatching {
                        val result = firebaseAccountRepository.claimPairingToken(accountState, payload)
                        accountPreferences.saveAccountState(result.state.copy(selectedRole = AccountRole.PARENT))
                        TeenDriveSyncScheduler.requestNow(context)
                        cloudState = CloudUiState(message = result.statusMessage, isSyncing = false)
                    }.onFailure { exception ->
                        cloudState = CloudUiState(
                            message = "Pairing failed",
                            error = exception.localizedMessage,
                            isSyncing = false,
                        )
                    }
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun RoleSelectionScreen(
    accountState: AccountState,
    onSelectRole: (AccountRole, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(accountState.displayName) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackgroundBrush)
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("TeenDrive", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Choose how this phone will be used.", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            RoleCard(
                title = "Teen",
                detail = "Record drives, build safety reports, and show a pairing QR.",
                icon = Icons.Filled.DirectionsCar,
                onClick = { onSelectRole(AccountRole.TEEN, name.ifBlank { "Teen" }) },
            )
            RoleCard(
                title = "Parent",
                detail = "Pair by QR, view connected teens, and monitor active drives.",
                icon = Icons.Filled.Groups,
                onClick = { onSelectRole(AccountRole.PARENT, name.ifBlank { "Parent" }) },
            )
        }
    }
}

@Composable
private fun TeenDriveNavShell(
    accountState: AccountState,
    cloudState: CloudUiState,
    onSaveName: (String) -> Unit,
    onSync: () -> Unit,
    onClaimPairing: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val startDestination = if (accountState.selectedRole == AccountRole.TEEN) Screen.TeenDrive.route else Screen.ParentDashboard.route
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: startDestination
    var driveRunningForHeader by remember { mutableStateOf(ActiveDriveTrackingService.isRunning) }
    val items = if (accountState.selectedRole == AccountRole.TEEN) {
        listOf(Screen.TeenHome, Screen.TeenDrive, Screen.TeenReports, Screen.TeenProfile)
    } else {
        listOf(Screen.ParentDashboard, Screen.ParentPairing)
    }

    LaunchedEffect(accountState.selectedRole) {
        while (accountState.selectedRole == AccountRole.TEEN) {
            driveRunningForHeader = ActiveDriveTrackingService.isRunning
            delay(500)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TeenDriveTopBar(
                title = items.firstOrNull { it.route == currentRoute }?.label ?: accountState.selectedRole.title,
                subtitle = when (currentRoute) {
                    Screen.TeenHome.route -> "${timeOfDayGreeting()}, ${accountState.displayName.ifBlank { "Teen" }}!"
                    Screen.TeenDrive.route -> if (driveRunningForHeader) "Driving" else "Not driving"
                    Screen.TeenReports.route -> "Reports sync automatically"
                    Screen.TeenProfile.route -> if (accountState.teenProfileId.isNotBlank() && accountState.familyGroupId.isNotBlank()) {
                        "Cloud account ready"
                    } else {
                        "Finish cloud setup"
                    }
                    else -> "TeenDrive ${accountState.selectedRole.title}"
                },
            )
        },
        bottomBar = {
            NavigationBar {
                items.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackgroundBrush)
                .padding(innerPadding),
        ) {
            NavHost(navController = navController, startDestination = startDestination) {
                composable(Screen.TeenHome.route) {
                    TeenHomeScreen(
                        accountState = accountState,
                        onOpenProfile = {
                            navController.navigate(Screen.TeenProfile.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable(Screen.TeenDrive.route) {
                    DriveDashboardScreen(accountState)
                }
                composable(Screen.TeenReports.route) {
                    ReportsScreen(accountState = accountState)
                }
                composable(Screen.TeenProfile.route) {
                    TeenProfileScreen(accountState, cloudState, onSaveName, onSync)
                }
                composable(Screen.ParentDashboard.route) {
                    ParentDashboardScreen(accountState, cloudState, onSync)
                }
                composable(Screen.ParentPairing.route) {
                    ParentPairingScreen(cloudState, onClaimPairing)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeenDriveTopBar(
    title: String,
    subtitle: String,
) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelMedium)
            }
        },
    )
}

@Composable
private fun TeenHomeScreen(
    accountState: AccountState,
    onOpenProfile: () -> Unit,
) {
    val context = LocalContext.current
    val tripRepository = remember {
        TripRepository(TeenDriveDatabase.getInstance(context).tripDao())
    }
    val trips by tripRepository.observeTrips().collectAsState(initial = emptyList())
    val lastTrip = trips.firstOrNull()
    val latestScore = lastTrip?.behaviorScoreBreakdown?.score ?: 100
    val averageScore = if (trips.isEmpty()) 100 else trips.map { it.behaviorScoreBreakdown.score }.average().toInt()
    val safeStreak = trips.takeWhile { it.safetyAlertCount == 0 && it.behaviorScoreBreakdown.score >= 90 }.size
    val lastDriveMinutes = lastTrip?.duration?.toMinutes() ?: 0
    val parentConnected = accountState.connectedParents.isNotEmpty()
    val focusArea = focusAreaForTrips(trips)
    HomeScreenColumn {
        HomeScoreHero(score = latestScore, hasTrip = lastTrip != null)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            HomeMetricCard(
                icon = Icons.Filled.Speed,
                title = "Last Drive",
                value = "$lastDriveMinutes min",
                detail = "Duration",
                modifier = Modifier.weight(1f),
            )
            HomeMetricCard(
                icon = Icons.Filled.DirectionsCar,
                title = "Trips",
                value = trips.size.toString(),
                detail = "Total drives",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            HomeMetricCard(
                icon = Icons.Filled.VerifiedUser,
                title = "Avg Score",
                value = averageScore.toString(),
                detail = "All trips",
                modifier = Modifier.weight(1f),
            )
            HomeMetricCard(
                icon = Icons.Filled.VerifiedUser,
                title = "Safe Streak",
                value = safeStreak.toString(),
                detail = "Safe drives",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            HomeFocusCard(focusArea = focusArea, modifier = Modifier.weight(1f))
            ParentConnectionCard(
                connected = parentConnected,
                onPairNow = onOpenProfile,
                modifier = Modifier.weight(1f),
            )
        }
        QuickInsightsCard(
            trips = trips,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun HomeScoreHero(score: Int, hasTrip: Boolean) {
    GlassCard(modifier = Modifier.height(138.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Safe Driving Score", style = MaterialTheme.typography.titleMedium, color = MutedHomeText, fontWeight = FontWeight.SemiBold)
                Text(scoreLabel(score), style = MaterialTheme.typography.titleLarge, color = TeenGreen, fontWeight = FontWeight.Bold)
                Text(
                    if (hasTrip) "You're building safe driving habits." else "Start a drive to build your first score.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedHomeText,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(TeenGreen))
                    Spacer(Modifier.width(10.dp))
                    Text("Last trip summary", color = MutedHomeText, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .border(14.dp, TeenGreen, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(score.toString(), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("/100", color = MutedHomeText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HomeMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCardColor, contentColor = Color.White),
        modifier = modifier.height(78.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(TeenGreen.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = TeenGreen)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = MutedHomeText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(detail, color = MutedHomeText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HomeFocusCard(focusArea: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.height(144.dp)) {
        Text("Top Focus Area", style = MaterialTheme.typography.titleMedium, color = MutedHomeText, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(TeenGreen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Text(focusArea, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ParentConnectionCard(
    connected: Boolean,
    onPairNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.height(144.dp)) {
        Text("Parent Connection", style = MaterialTheme.typography.titleMedium, color = MutedHomeText, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(TeenGreen.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = TeenGreen)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (connected) "Connected" else "Not connected", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (connected) "Sharing progress." else "Pair to share progress.", color = MutedHomeText, style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = onPairNow, modifier = Modifier.fillMaxWidth()) {
            Text(if (connected) "View pairing" else "Pair now")
        }
    }
}

@Composable
private fun QuickInsightsCard(
    trips: List<TeenTrip>,
    modifier: Modifier = Modifier,
) {
    val bestScore = trips.maxOfOrNull { it.behaviorScoreBreakdown.score } ?: 100
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quick Insights", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                InsightRow("No harsh braking on your last 3 trips.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InsightRow("Best drive this week: ")
                    Text(bestScore.toString(), color = TeenGreen, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TeenGreen, modifier = Modifier.size(38.dp))
            }
        }
    }
}

@Composable
private fun InsightRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = TeenGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = MutedHomeText, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DriveDashboardScreen(accountState: AccountState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationProvider = remember { TeenDriveLocationProvider(context) }
    var foregroundLocationGranted by remember {
        mutableStateOf(context.hasAnyPermission(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    var backgroundLocationGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }
    var locationSnapshot by remember { mutableStateOf<LocationSnapshot?>(null) }
    var locationStatus by remember { mutableStateOf("Location not requested yet") }
    var isDriveTracking by rememberSaveable { mutableStateOf(ActiveDriveTrackingService.isRunning) }
    var activeDriveSnapshot by remember { mutableStateOf<ActiveDriveSnapshot?>(ActiveDriveTrackingService.activeDriveSnapshot) }
    var driveClockNow by remember { mutableStateOf(Instant.now()) }
    var useSatelliteMap by rememberSaveable { mutableStateOf(true) }

    fun refreshLocation() {
        if (!foregroundLocationGranted) {
            locationStatus = "Foreground location is needed first."
            return
        }
        scope.launch {
            locationStatus = "Getting current location..."
            runCatching { locationProvider.currentSnapshot() }
                .onSuccess { snapshot ->
                    locationSnapshot = snapshot ?: ActiveDriveTrackingService.lastSnapshot
                    activeDriveSnapshot = ActiveDriveTrackingService.activeDriveSnapshot
                    locationStatus = if (snapshot == null) "No location fix yet" else "Current location ready"
                }
                .onFailure { exception ->
                    locationStatus = exception.localizedMessage ?: "Could not read location"
                }
        }
    }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        foregroundLocationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (foregroundLocationGranted) refreshLocation()
    }
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> backgroundLocationGranted = granted }

    LaunchedEffect(foregroundLocationGranted) {
        if (foregroundLocationGranted && locationSnapshot == null) {
            refreshLocation()
        }
    }

    LaunchedEffect(isDriveTracking) {
        while (isDriveTracking) {
            driveClockNow = Instant.now()
            activeDriveSnapshot = ActiveDriveTrackingService.activeDriveSnapshot
            locationSnapshot = ActiveDriveTrackingService.lastSnapshot ?: locationSnapshot
            delay(1_000)
        }
    }

    DriveScreenColumn {
        LiveDriveMapCard(
            snapshot = activeDriveSnapshot,
            locationSnapshot = locationSnapshot,
            locationStatus = locationStatus,
            foregroundLocationGranted = foregroundLocationGranted,
            backgroundLocationGranted = backgroundLocationGranted,
            useSatelliteMap = useSatelliteMap,
            onToggleMapType = { useSatelliteMap = !useSatelliteMap },
            modifier = Modifier.weight(1f),
            onRequestForeground = {
                foregroundPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
            onRequestBackground = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            },
        )
        DriveStatsStrip(
            snapshot = activeDriveSnapshot,
            isDriveTracking = isDriveTracking,
            now = driveClockNow,
        )
        Button(
            onClick = {
                if (isDriveTracking) {
                    val stoppedAt = Instant.now()
                    val completedSnapshot = ActiveDriveTrackingService.activeDriveSnapshot?.copy(updatedAt = stoppedAt)
                    ActiveDriveTrackingService.stop(context)
                    isDriveTracking = false
                    driveClockNow = stoppedAt
                    activeDriveSnapshot = completedSnapshot ?: ActiveDriveTrackingService.activeDriveSnapshot
                    locationStatus = "Active drive tracking stopped"
                } else {
                    ActiveDriveTrackingService.start(
                        context = context,
                        teenProfileId = accountState.teenProfileId,
                        familyGroupId = accountState.familyGroupId,
                        teenName = accountState.displayName.ifBlank { "Teen" },
                    )
                    isDriveTracking = true
                    driveClockNow = Instant.now()
                    activeDriveSnapshot = ActiveDriveTrackingService.activeDriveSnapshot
                    locationStatus = ActiveDriveTrackingService.NOTIFICATION_TEXT
                    refreshLocation()
                }
            },
            enabled = foregroundLocationGranted,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = TeenGreen,
                contentColor = Color(0xFF06210F),
                disabledContainerColor = HomeCardColor,
                disabledContentColor = MutedHomeText,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Filled.DirectionsCar, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(if (isDriveTracking) "Stop Drive" else "Start Drive")
        }
    }
}

@Composable
private fun DriveScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun RememberedMapMarker(
    position: LatLng,
    title: String,
    snippet: String? = null,
    hue: Float,
) {
    val markerState = remember { MarkerState(position) }
    LaunchedEffect(position) {
        markerState.position = position
    }
    Marker(
        state = markerState,
        title = title,
        snippet = snippet,
        icon = BitmapDescriptorFactory.defaultMarker(hue),
    )
}

@Composable
private fun LiveDriveMapCard(
    snapshot: ActiveDriveSnapshot?,
    locationSnapshot: LocationSnapshot?,
    locationStatus: String,
    foregroundLocationGranted: Boolean,
    backgroundLocationGranted: Boolean,
    useSatelliteMap: Boolean,
    onToggleMapType: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestForeground: () -> Unit,
    onRequestBackground: () -> Unit,
) {
    val mapScope = rememberCoroutineScope()
    val route = snapshot?.route.orEmpty()
    val currentLatLng = route.lastOrNull()?.let { LatLng(it.latitude, it.longitude) }
        ?: locationSnapshot?.let { LatLng(it.latitude, it.longitude) }
        ?: LatLng(25.7617, -80.1918)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLatLng, 15f)
    }
    LaunchedEffect(currentLatLng) {
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17211F)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = foregroundLocationGranted,
                    mapType = if (useSatelliteMap) MapType.SATELLITE else MapType.NORMAL,
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    compassEnabled = false,
                    scrollGesturesEnabled = true,
                    zoomGesturesEnabled = true,
                    rotationGesturesEnabled = true,
                    scrollGesturesEnabledDuringRotateOrZoom = true,
                    tiltGesturesEnabled = true,
                ),
            ) {
                if (route.size >= 2) {
                    Polyline(
                        points = route.map { LatLng(it.latitude, it.longitude) },
                        color = Color(0xFF37D967),
                        width = 12f,
                    )
                }
                RememberedMapMarker(
                    position = currentLatLng,
                    title = "Current",
                    snippet = "${snapshot?.currentSpeedMph?.toInt() ?: locationSnapshot?.speedMph?.toInt() ?: 0} mph",
                    hue = BitmapDescriptorFactory.HUE_GREEN,
                )
                snapshot?.safetyAlerts.orEmpty().forEach { alert ->
                    val latitude = alert.latitude
                    val longitude = alert.longitude
                    if (latitude != null && longitude != null) {
                        RememberedMapMarker(
                            position = LatLng(latitude, longitude),
                            title = alert.kind.title,
                            snippet = alert.displayText,
                            hue = BitmapDescriptorFactory.HUE_ORANGE,
                        )
                    }
                }
            }
            DriveDataMapOverlay(
                route = route,
                currentLatLng = currentLatLng,
                alerts = snapshot?.safetyAlerts.orEmpty(),
                modifier = Modifier.fillMaxSize(),
            )
            SpeedOverlay(
                speedMph = snapshot?.currentSpeedMph ?: locationSnapshot?.speedMph ?: 0.0,
                speedLimitMph = snapshot?.speedLimitMph ?: locationSnapshot?.speedLimitMph,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MapStatusPill(Icons.Filled.LocationOn, locationStatus)
                MapStatusPill(Icons.Filled.Warning, "${snapshot?.safetyAlerts?.size ?: 0} events")
            }
            OutlinedButton(
                onClick = onToggleMapType,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Text(if (useSatelliteMap) "Map" else "Satellite")
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapRoundButton(Icons.Filled.Navigation) {
                    mapScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
                    }
                }
                MapRoundButton(Icons.Filled.LocationOn) {
                    onRequestForeground()
                    mapScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
                    }
                }
            }
            if (!foregroundLocationGranted || !backgroundLocationGranted) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onRequestForeground) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (foregroundLocationGranted) "Refresh" else "Allow")
                    }
                    if (foregroundLocationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        OutlinedButton(onClick = onRequestBackground) {
                            Text(if (backgroundLocationGranted) "Background" else "Background")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveDataMapOverlay(
    route: List<RoutePoint>,
    currentLatLng: LatLng,
    alerts: List<SafetyAlert>,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .padding(18.dp),
    ) {
        val allPoints = route.map { LatLng(it.latitude, it.longitude) } + currentLatLng
        val rawMinLat = allPoints.minOf { it.latitude }
        val rawMaxLat = allPoints.maxOf { it.latitude }
        val rawMinLng = allPoints.minOf { it.longitude }
        val rawMaxLng = allPoints.maxOf { it.longitude }
        val latSpan = kotlin.math.max(0.0008, rawMaxLat - rawMinLat)
        val lngSpan = kotlin.math.max(0.0008, rawMaxLng - rawMinLng)
        val minLat = ((rawMinLat + rawMaxLat) / 2.0) - latSpan / 2.0
        val minLng = ((rawMinLng + rawMaxLng) / 2.0) - lngSpan / 2.0

        fun LatLng.toCanvasPoint(): Offset {
            val x = ((longitude - minLng) / lngSpan).toFloat() * size.width
            val y = (1f - ((latitude - minLat) / latSpan).toFloat()) * size.height
            return Offset(x.coerceIn(0f, size.width), y.coerceIn(0f, size.height))
        }

        val routePoints = if (route.isEmpty()) listOf(currentLatLng.toCanvasPoint()) else route.map { LatLng(it.latitude, it.longitude).toCanvasPoint() }
        if (routePoints.size >= 2) {
            routePoints.zipWithNext().forEach { (start, end) ->
                drawLine(Color(0xFF37D967), start = start, end = end, strokeWidth = 8f)
            }
        }

        alerts.forEach { alert ->
            val latitude = alert.latitude
            val longitude = alert.longitude
            if (latitude != null && longitude != null) {
                val point = LatLng(latitude, longitude).toCanvasPoint()
                drawCircle(Color(0xFFFFA726), radius = 10f, center = point)
                drawCircle(Color.White, radius = 14f, center = point, style = Stroke(width = 3f))
            }
        }

        val current = currentLatLng.toCanvasPoint()
        drawCircle(Color(0x5537D967), radius = 34f, center = current)
        drawCircle(Color.White, radius = 20f, center = current)
        drawCircle(Color(0xFF37D967), radius = 14f, center = current)
    }
}

@Composable
private fun SpeedOverlay(
    speedMph: Double,
    speedLimitMph: Double?,
    modifier: Modifier = Modifier,
) {
    val displayedLimit = speedLimitMph?.toInt() ?: 45
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC203E39))
            .border(1.dp, Color(0x6637D967), RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (speedLimitMph == null) {
                    Text("FALL", color = Color.Black, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("BACK", color = Color.Black, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                } else {
                    Text("SPEED", color = Color.Black, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("LIMIT", color = Color.Black, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text(displayedLimit.toString(), color = Color.Black, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("${speedMph.toInt()}", color = Color(0xFF37D967), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("mph", color = Color(0xFFD6E0DC), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DriveStatsStrip(
    snapshot: ActiveDriveSnapshot?,
    isDriveTracking: Boolean = false,
    now: Instant = Instant.now(),
) {
    val durationText = snapshot?.let {
        if (isDriveTracking) {
            java.time.Duration.between(it.startedAt, now).driveDurationText()
        } else {
            it.duration.driveDurationText()
        }
    } ?: "0:00"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE52B302E))
            .border(1.dp, Color(0x445C6964), RoundedCornerShape(8.dp)),
    ) {
        DriveStripMetric("Safety Score", snapshot?.let { 100 - (it.safetyAlerts.size * 5) }?.coerceIn(0, 100)?.toString() ?: "100", "Great", Modifier.weight(1f))
        DriveStripMetric("Distance", snapshot?.distanceMiles?.oneDecimal() ?: "0.0", "mi", Modifier.weight(1f))
        DriveStripMetric("Duration", durationText, "", Modifier.weight(1f))
    }
}

@Composable
private fun DriveStripMetric(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = Color(0xFFB9C1BE), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(if (unit.isBlank()) value else "$value $unit", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MapStatusPill(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xAA1E2926))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF37D967), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MapRoundButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(Color(0xCC28413D))
            .border(1.dp, Color(0x668AA098), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun ReportsScreen(accountState: AccountState) {
    val context = LocalContext.current
    val repository = remember {
        TripRepository(TeenDriveDatabase.getInstance(context).tripDao())
    }
    val trips by repository.observeTrips().collectAsState(initial = emptyList())
    var selectedTripId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTrip = trips.firstOrNull { it.id == selectedTripId }

    LaunchedEffect(trips.size, accountState.teenProfileId, accountState.familyGroupId) {
        TeenDriveSyncScheduler.requestNow(context)
    }

    if (selectedTrip != null) {
        TripDetailScreen(
            trip = selectedTrip,
            onBack = { selectedTripId = null },
        )
    } else {
        TripListScreen(
            trips = trips,
            onSelectTrip = { selectedTripId = it.id },
        )
    }
}

@Composable
private fun TripListScreen(
    trips: List<TeenTrip>,
    onSelectTrip: (TeenTrip) -> Unit,
) {
    ScreenColumn {
        if (trips.isEmpty()) {
            GlassCard {
                Text("No trips yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Start and stop drive tracking to create the first local report.")
            }
        } else {
            trips.forEach { trip ->
                TripRow(trip = trip, onClick = { onSelectTrip(trip) })
            }
        }
    }
}

@Composable
private fun TripRow(trip: TeenTrip, onClick: () -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${trip.behaviorScoreBreakdown.score}",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(trip.startedAt.toLocalReportText(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${trip.distanceMiles.oneDecimal()} mi • ${trip.duration.driveDurationText()} • ${trip.topSpeedMph.toInt()} mph top")
                Text("${trip.route.size} route points • ${trip.safetyAlertCount} alerts", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onClick,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = TeenGreen,
                    contentColor = Color(0xFF06210F),
                ),
            ) {
                Text("Open")
            }
        }
    }
}

@Composable
private fun TripDetailMapCard(
    trip: TeenTrip,
    useSatelliteMap: Boolean,
    onToggleMapType: () -> Unit,
) {
    val route = trip.route.map { LatLng(it.latitude, it.longitude) }
    val alertPoints = trip.displaySafetyAlerts.mapNotNull { alert ->
        val latitude = alert.latitude
        val longitude = alert.longitude
        if (latitude != null && longitude != null) alert to LatLng(latitude, longitude) else null
    }
    val center = route.firstOrNull()
        ?: alertPoints.firstOrNull()?.second
        ?: LatLng(trip.mapBounds.center.latitude, trip.mapBounds.center.longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, if (route.size > 1) 14f else 15f)
    }

    LaunchedEffect(trip.id, useSatelliteMap) {
        val points = route + alertPoints.map { it.second }
        if (points.size >= 2) {
            val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.builder()
            points.forEach(boundsBuilder::include)
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80))
        } else {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(center, 15f))
        }
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17211F)),
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(mapType = if (useSatelliteMap) MapType.SATELLITE else MapType.NORMAL),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    compassEnabled = false,
                    scrollGesturesEnabled = true,
                    zoomGesturesEnabled = true,
                    rotationGesturesEnabled = true,
                    scrollGesturesEnabledDuringRotateOrZoom = true,
                    tiltGesturesEnabled = true,
                ),
            ) {
                if (route.size >= 2) {
                    Polyline(points = route, color = TeenGreen, width = 12f)
                }
                route.firstOrNull()?.let {
                    RememberedMapMarker(
                        position = it,
                        title = "Start",
                        hue = BitmapDescriptorFactory.HUE_GREEN,
                    )
                }
                route.lastOrNull()?.let {
                    RememberedMapMarker(
                        position = it,
                        title = "End",
                        hue = BitmapDescriptorFactory.HUE_AZURE,
                    )
                }
                alertPoints.forEach { (alert, point) ->
                    RememberedMapMarker(
                        position = point,
                        title = alert.kind.title,
                        snippet = alert.displayText,
                        hue = BitmapDescriptorFactory.HUE_ORANGE,
                    )
                }
            }
            DriveDataMapOverlay(
                route = trip.route,
                currentLatLng = route.lastOrNull() ?: center,
                alerts = trip.displaySafetyAlerts,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapStatusPill(Icons.Filled.Navigation, "${trip.route.size} points")
                MapStatusPill(Icons.Filled.Warning, "${trip.safetyAlertCount} events")
            }
            OutlinedButton(
                onClick = onToggleMapType,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Text(if (useSatelliteMap) "Map" else "Satellite")
            }
        }
    }
}

@Composable
private fun TripDetailScreen(
    trip: TeenTrip,
    onBack: () -> Unit,
) {
    var useSatelliteMap by rememberSaveable { mutableStateOf(true) }
    ScreenColumn {
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onBack,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = TeenGreen,
                    contentColor = Color(0xFF06210F),
                ),
            ) {
                Text("Back")
            }
        }
        TripDetailMapCard(
            trip = trip,
            useSatelliteMap = useSatelliteMap,
            onToggleMapType = { useSatelliteMap = !useSatelliteMap },
        )
        TripStatsCard(trip)
        StatusCard("Alerts", "${trip.safetyAlertCount}", "Speed ${trip.speedLimitAlertCount} • Harsh stops ${trip.harshStopAlertCount} • Phone ${trip.phoneUseAlertCount}")
        GlassCard {
            Text("Alert breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Rapid acceleration: ${trip.rapidAccelerationAlertCount}")
            Text("Harsh cornering: ${trip.harshCorneringAlertCount}")
            Text("Night driving: ${trip.nightDrivingAlertCount}")
        }
    }
}

@Composable
private fun TripStatsCard(trip: TeenTrip) {
    GlassCard {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
            ReportStatText("Score", trip.behaviorScoreBreakdown.score.toString(), "", Modifier.weight(1f))
            ReportStatText("Distance", trip.distanceMiles.oneDecimal(), "mi", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
            ReportStatText("Duration", trip.duration.driveDurationText(), "", Modifier.weight(1f))
            ReportStatText("Top speed", trip.topSpeedMph.toInt().toString(), "mph", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReportStatText(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = MutedHomeText, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(unit, color = MutedHomeText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TeenProfileScreen(
    accountState: AccountState,
    cloudState: CloudUiState,
    onSaveName: (String) -> Unit,
    onSync: () -> Unit,
) {
    var name by rememberSaveable(accountState.displayName) { mutableStateOf(accountState.displayName) }
    val payload = PairingPayload(
        code = accountState.pairingCode,
        token = accountState.pairingToken,
        teenName = accountState.displayName,
        teenProfileId = accountState.teenProfileId,
        familyGroupId = accountState.familyGroupId,
    ).toUriString()

    ProfileScreenColumn {
        GlassCard(modifier = Modifier.height(156.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(TeenGreen.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = TeenGreen, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(accountState.selectedRole.title, color = MutedHomeText, style = MaterialTheme.typography.labelMedium)
                    Text(accountState.displayName.ifBlank { "Teen" }, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    colors = profileTextFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onSaveName(name) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = TeenGreen,
                        contentColor = Color(0xFF06210F),
                    ),
                    modifier = Modifier
                        .width(86.dp)
                        .height(56.dp),
                ) {
                    Text("Save")
                }
            }
            OutlinedButton(
                onClick = onSync,
                modifier = Modifier.fillMaxWidth().height(38.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = TeenGreen,
                ),
            ) {
                Text("Refresh QR")
            }
        }
        ProfileCloudCard(
            message = cloudState.message,
            detail = cloudState.error ?: "UID: ${cloudState.uid ?: accountState.teenProfileId.ifBlank { "pending" }}",
            isReady = accountState.teenProfileId.isNotBlank() && accountState.familyGroupId.isNotBlank(),
            modifier = Modifier.height(68.dp),
        )
        PairingPayloadCard(
            payload = payload,
            isReady = accountState.teenProfileId.isNotBlank() && accountState.familyGroupId.isNotBlank(),
            pairingCode = accountState.pairingCode,
            familyGroupId = accountState.familyGroupId,
            modifier = Modifier.weight(1f),
        )
        GlassCard(modifier = Modifier.height(66.dp)) {
            Text("Privacy & Safety", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Trips stay local first and sync when cloud is ready.",
                color = MutedHomeText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfileScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        content = content,
    )
}

@Composable
private fun profileTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = TeenGreen,
        unfocusedLabelColor = MutedHomeText,
        focusedBorderColor = TeenGreen,
        unfocusedBorderColor = Color.White.copy(alpha = 0.24f),
        cursorColor = TeenGreen,
    )

@Composable
private fun ProfileCloudCard(
    message: String,
    detail: String,
    isReady: Boolean,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isReady) TeenGreen.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null, tint = if (isReady) TeenGreen else MutedHomeText)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Cloud sync", color = MutedHomeText, style = MaterialTheme.typography.labelLarge)
                Text(message, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ParentDashboardScreen(
    accountState: AccountState,
    cloudState: CloudUiState,
    onSync: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val parentTripRepository = remember { ParentTripRepository() }
    val connectedTeens = remember(accountState.connectedTeens) {
        accountState.connectedTeens.mapNotNull(ConnectedTeen::decode)
    }
    var parentTrips by remember { mutableStateOf<List<ParentTeenTrip>>(emptyList()) }
    var activeDrives by remember { mutableStateOf<List<ActiveTeenDrive>>(emptyList()) }
    var reportStatus by rememberSaveable { mutableStateOf("Parent reports ready") }

    fun refreshParentReports() {
        scope.launch {
            reportStatus = "Loading teen status..."
            runCatching {
                parentTripRepository.fetchActiveDrivesForConnectedTeens(connectedTeens) to
                    parentTripRepository.fetchTripsForConnectedTeens(connectedTeens)
            }.onSuccess { (drives, trips) ->
                activeDrives = drives
                parentTrips = trips
                reportStatus = "Active ${drives.size} • Reports ${trips.size}"
            }.onFailure { exception ->
                reportStatus = exception.localizedMessage ?: "Could not load teen reports"
            }
        }
    }

    ScreenColumn {
        StatusCard("Connected teens", "${connectedTeens.size}", "Pair with a teen QR to populate this list.")
        StatusCard("Cloud sync", cloudState.message, cloudState.error ?: "Parent profile: ${accountState.parentProfileId.ifBlank { "pending" }}")
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Report, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recent reports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(reportStatus)
                }
                Button(onClick = ::refreshParentReports, enabled = connectedTeens.isNotEmpty()) {
                    Text("Load")
                }
            }
        }
        if (activeDrives.isNotEmpty()) {
            activeDrives.forEach { drive ->
                ActiveTeenDriveCard(drive)
            }
        }
        if (parentTrips.isEmpty()) {
            GlassCard {
                Text("No teen reports loaded", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Pair with a teen and load reports after the teen syncs completed drives.")
            }
        } else {
            parentTrips.forEach { parentTrip ->
                ParentTripCard(parentTrip)
            }
        }
        Button(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Sync, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Sync Parent Profile")
        }
    }
}

@Composable
private fun ActiveTeenDriveCard(drive: ActiveTeenDrive) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${drive.speedMph.toInt()}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("${drive.teenName} is driving", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${drive.distanceMiles.oneDecimal()} mi • ${drive.duration.driveDurationText()} • ${drive.topSpeedMph.toInt()} mph top")
                Text("Updated ${drive.updatedAt.toLocalReportText()} • ${drive.alertCount} alerts", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ParentTripCard(parentTrip: ParentTeenTrip) {
    val trip = parentTrip.trip
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${trip.behaviorScoreBreakdown.score}",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(parentTrip.teen.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(trip.startedAt.toLocalReportText())
                Text("${trip.distanceMiles.oneDecimal()} mi • ${trip.duration.driveDurationText()} • ${trip.safetyAlertCount} alerts")
            }
        }
    }
}

@Composable
private fun ParentPairingScreen(
    cloudState: CloudUiState,
    onClaimPairing: (String) -> Unit,
) {
    val context = LocalContext.current
    var payload by rememberSaveable { mutableStateOf("") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ScreenColumn {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Camera scanner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Point the camera at the teen pairing QR.")
                }
            }
            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                ) {
                    QrScannerView(
                        onQrCodeScanned = { scannedPayload ->
                            payload = scannedPayload
                            if (!cloudState.isSyncing) {
                                onClaimPairing(scannedPayload)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Camera permission", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Permission needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Allow camera access to scan the teen pairing QR.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Allow Camera")
                }
            }
        }
        GlassCard {
            Text("Scanned payload", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = payload,
                onValueChange = { payload = it },
                label = { Text("TeenDrive QR payload") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onClaimPairing(payload) },
                enabled = payload.isNotBlank() && !cloudState.isSyncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Claim Pairing Token")
            }
        }
        AnimatedVisibility(visible = cloudState.message.isNotBlank()) {
            StatusCard("Pairing status", cloudState.message, cloudState.error ?: "Waiting for a valid teen QR payload.")
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun RoleCard(title: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onClick) {
                Text("Use")
            }
        }
    }
}

@Composable
private fun PairingPayloadCard(
    payload: String,
    isReady: Boolean,
    pairingCode: String,
    familyGroupId: String,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Teen QR pairing", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (isReady) "Ready" else "Sync needed", color = if (isReady) TeenGreen else MutedHomeText, style = MaterialTheme.typography.labelLarge)
            }
            if (isReady) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    PairingQrCode(payload = payload, size = 104.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ProfileInfoChip("Code", pairingCode.ifBlank { "pending" }, Modifier.weight(1f))
                    ProfileInfoChip("Family", familyGroupId.take(8).ifBlank { "pending" }, Modifier.weight(1f))
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(14.dp),
                ) {
                    Text(
                        text = "Preparing cloud pairing QR...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedHomeText,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoChip(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, color = MutedHomeText, style = MaterialTheme.typography.labelMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusCard(title: String, value: String, detail: String) {
    GlassCard {
        StatusCardContent(title, value, detail)
    }
}

@Composable
private fun StatusCardContent(title: String, value: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCardColor, contentColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

private val TeenGreen = Color(0xFF3EE36A)
private val MutedHomeText = Color.White.copy(alpha = 0.68f)
private val HomeCardColor = Color(0xFF303A36).copy(alpha = 0.88f)

private val AppBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF1F2525),
        Color(0xFF071C17),
        Color(0xFF020606),
    ),
)

private fun timeOfDayGreeting(): String {
    val hour = LocalTime.now().hour
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun scoreLabel(score: Int): String =
    when {
        score >= 90 -> "Great job!"
        score >= 75 -> "Nice progress"
        else -> "Keep practicing"
    }

private fun focusAreaForTrips(trips: List<TeenTrip>): String {
    if (trips.isEmpty()) return "None"
    val speed = trips.sumOf { it.speedLimitAlertCount }
    val stops = trips.sumOf { it.harshStopAlertCount }
    val cornering = trips.sumOf { it.harshCorneringAlertCount }
    val acceleration = trips.sumOf { it.rapidAccelerationAlertCount }
    val phone = trips.sumOf { it.phoneUseAlertCount }
    val night = trips.sumOf { it.nightDrivingAlertCount }
    val top = listOf(
        "Speed" to speed,
        "Harsh stops" to stops,
        "Cornering" to cornering,
        "Acceleration" to acceleration,
        "Phone use" to phone,
        "Night driving" to night,
    ).maxBy { it.second }
    return if (top.second == 0) "None" else top.first
}

private fun android.content.Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun android.content.Context.hasAnyPermission(vararg permissions: String): Boolean =
    permissions.any { hasPermission(it) }

private val reportDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())

private fun Instant.toLocalReportText(): String = reportDateFormatter.format(this)
