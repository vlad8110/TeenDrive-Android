package com.vlad8110.teendrive.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.vlad8110.teendrive.MainActivity
import com.vlad8110.teendrive.R
import com.vlad8110.teendrive.data.AccountState
import com.vlad8110.teendrive.data.TeenDriveDatabase
import com.vlad8110.teendrive.data.TripRepository
import com.vlad8110.teendrive.firebase.ActiveDriveSyncRepository
import com.vlad8110.teendrive.firebase.FirebaseTripSyncRepository
import com.vlad8110.teendrive.firebase.NotificationEventRepository
import com.vlad8110.teendrive.model.SafetyAlert
import com.vlad8110.teendrive.location.speedLimitMetersPerSecondFromExtras
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ActiveDriveTrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(applicationContext)
    }
    private val tripRepository by lazy {
        TripRepository(TeenDriveDatabase.getInstance(applicationContext).tripDao())
    }
    private val activeDriveSyncRepository by lazy { ActiveDriveSyncRepository() }
    private val notificationEventRepository by lazy { NotificationEventRepository() }
    private val tripSyncRepository by lazy { FirebaseTripSyncRepository() }
    private var teenProfileId: String = ""
    private var familyGroupId: String = ""
    private var teenName: String = "Teen"
    private var pendingPhoneUnlockWhileMoving: Boolean = false
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT && isRunning) {
                pendingPhoneUnlockWhileMoving = true
            }
        }
    }
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            lastSnapshot = result.lastLocation?.let {
                val speedMetersPerSecond = if (it.hasSpeed()) it.speed.toDouble() else 0.0
                val phoneUnlockedWhileMoving = pendingPhoneUnlockWhileMoving && speedMetersPerSecond >= PHONE_UNLOCK_MOVING_SPEED_MPS
                if (phoneUnlockedWhileMoving || speedMetersPerSecond < PHONE_UNLOCK_MOVING_SPEED_MPS) {
                    pendingPhoneUnlockWhileMoving = false
                }
                LocationSnapshot(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    speedMetersPerSecond = speedMetersPerSecond,
                    accuracyMeters = if (it.hasAccuracy()) it.accuracy else null,
                    provider = it.provider,
                    speedLimitMetersPerSecond = it.speedLimitMetersPerSecondFromExtras(),
                    phoneUnlockedWhileMoving = phoneUnlockedWhileMoving,
                )
            }?.also {
                val previousAlertIds = ActiveDriveSessionStore.current()
                    ?.safetyAlerts
                    ?.map { alert -> alert.id }
                    ?.toSet()
                    ?: emptySet()
                val updatedSnapshot = ActiveDriveSessionStore.append(it)
                activeDriveSnapshot = updatedSnapshot
                publishActiveDrive(updatedSnapshot)
                publishParentSafetyAlerts(
                    updatedSnapshot.safetyAlerts.filter { alert -> alert.id !in previousAlertIds },
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                teenProfileId = intent?.getStringExtra(EXTRA_TEEN_PROFILE_ID).orEmpty()
                familyGroupId = intent?.getStringExtra(EXTRA_FAMILY_GROUP_ID).orEmpty()
                teenName = intent?.getStringExtra(EXTRA_TEEN_NAME).orEmpty().ifBlank { "Teen" }
                isRunning = true
                activeDriveSnapshot = ActiveDriveSessionStore.start()
                startInForeground()
                startLocationUpdates()
                return START_STICKY
            }
        }
    }

    private fun publishActiveDrive(snapshot: ActiveDriveSnapshot?) {
        val activeSnapshot = snapshot ?: return
        if (teenProfileId.isBlank() || familyGroupId.isBlank()) return
        serviceScope.launch {
            runCatching {
                ensureParentLinkedFamilyGroup()
                activeDriveSyncRepository.publishActiveDrive(
                    snapshot = activeSnapshot,
                    teenId = teenProfileId,
                    familyGroupId = familyGroupId,
                    teenName = teenName,
                )
            }
        }
    }

    private fun clearActiveDrive() {
        if (teenProfileId.isBlank() || familyGroupId.isBlank()) return
        serviceScope.launch {
            runCatching {
                ensureParentLinkedFamilyGroup()
                activeDriveSyncRepository.clearActiveDrive(
                    teenId = teenProfileId,
                    familyGroupId = familyGroupId,
                )
            }
        }
    }

    private fun publishParentSafetyAlerts(alerts: List<SafetyAlert>) {
        if (alerts.isEmpty() || teenProfileId.isBlank() || familyGroupId.isBlank()) return
        serviceScope.launch {
            runCatching {
                ensureParentLinkedFamilyGroup()
                alerts.forEach { alert ->
                    notificationEventRepository.writeParentSafetyAlertEvent(
                        alert = alert,
                        teenId = teenProfileId,
                        familyGroupId = familyGroupId,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        stopTracking()
        runCatching { unregisterReceiver(userPresentReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasForegroundLocationPermission()) {
            stopSelf()
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(FASTEST_LOCATION_INTERVAL_MILLIS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isRunning = false
        lastSnapshot = null
        val completedSnapshot = ActiveDriveSessionStore.stop()
        activeDriveSnapshot = completedSnapshot
        if (completedSnapshot != null && completedSnapshot.route.isNotEmpty()) {
            serviceScope.launch {
                runCatching {
                    val trip = tripRepository.saveCompletedDrive(completedSnapshot)
                    lastCompletedTripId = trip.id
                    syncCompletedTripsIfCloudReady()
                }
            }
        }
        clearActiveDrive()
    }

    private suspend fun syncCompletedTripsIfCloudReady() {
        if (teenProfileId.isBlank() || familyGroupId.isBlank()) return
        ensureParentLinkedFamilyGroup()
        tripSyncRepository.syncLocalTrips(
            accountState = AccountState(
                hasSelectedRole = true,
                teenProfileId = teenProfileId,
                familyGroupId = familyGroupId,
                displayName = teenName,
            ),
            tripRepository = tripRepository,
        )
    }

    private suspend fun ensureParentLinkedFamilyGroup() {
        if (teenProfileId.isBlank() || familyGroupId.isBlank()) return
        val resolvedFamilyGroupId = tripSyncRepository.resolveParentLinkedFamilyGroupId(
            teenId = teenProfileId,
            fallbackFamilyGroupId = familyGroupId,
        )
        if (resolvedFamilyGroupId.isNotBlank()) {
            familyGroupId = resolvedFamilyGroupId
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = Intent(this, ActiveDriveTrackingService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("TeenDrive")
            .setContentText(NOTIFICATION_TEXT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(NOTIFICATION_TEXT))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.mipmap.ic_launcher, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active drive tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = NOTIFICATION_TEXT
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun hasForegroundLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val NOTIFICATION_TEXT = "Teen Drive is tracking an active drive"
        private const val CHANNEL_ID = "active_drive_tracking"
        private const val NOTIFICATION_ID = 8110
        private const val ACTION_STOP = "com.vlad8110.teendrive.action.STOP_ACTIVE_DRIVE"
        private const val LOCATION_INTERVAL_MILLIS = 5_000L
        private const val FASTEST_LOCATION_INTERVAL_MILLIS = 2_000L
        private const val MIN_DISTANCE_METERS = 5f
        private const val PHONE_UNLOCK_MOVING_SPEED_MPS = 2.2
        private const val EXTRA_TEEN_PROFILE_ID = "teenProfileId"
        private const val EXTRA_FAMILY_GROUP_ID = "familyGroupId"
        private const val EXTRA_TEEN_NAME = "teenName"

        var isRunning: Boolean = false
            private set

        var lastSnapshot: LocationSnapshot? = null
            private set

        var activeDriveSnapshot: ActiveDriveSnapshot? = null
            private set

        var lastCompletedTripId: String? = null
            private set

        fun start(
            context: Context,
            teenProfileId: String,
            familyGroupId: String,
            teenName: String,
        ) {
            val intent = Intent(context, ActiveDriveTrackingService::class.java)
                .putExtra(EXTRA_TEEN_PROFILE_ID, teenProfileId)
                .putExtra(EXTRA_FAMILY_GROUP_ID, familyGroupId)
                .putExtra(EXTRA_TEEN_NAME, teenName)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ActiveDriveTrackingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
