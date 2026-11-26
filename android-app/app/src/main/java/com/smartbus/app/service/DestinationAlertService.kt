package com.smartbus.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.smartbus.app.MainActivity
import com.smartbus.app.SmartBusApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

data class DestinationAlertState(
    val isActive: Boolean = false,
    val isRinging: Boolean = false,
    val stopName: String = "",
    val targetLat: Double = 0.0,
    val targetLng: Double = 0.0,
    val radiusMeters: Int = 1000,
    val isNightLoudMode: Boolean = true,
    val tripId: String? = null,
    val distanceMeters: Double? = null,
    val sourceLabel: String = "Checking GPS..."
)

class DestinationAlertService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var busPollJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_START = "ACTION_START_DEST_ALERT"
        const val ACTION_STOP = "ACTION_STOP_DEST_ALERT"
        const val ACTION_DISMISS_ALARM = "ACTION_DISMISS_DEST_ALARM"
        const val ACTION_TEST_ALARM = "ACTION_TEST_DEST_ALARM"

        const val EXTRA_STOP_NAME = "EXTRA_STOP_NAME"
        const val EXTRA_LAT = "EXTRA_LAT"
        const val EXTRA_LNG = "EXTRA_LNG"
        const val EXTRA_RADIUS = "EXTRA_RADIUS"
        const val EXTRA_NIGHT_MODE = "EXTRA_NIGHT_MODE"
        const val EXTRA_TRIP_ID = "EXTRA_TRIP_ID"

        const val MONITOR_CHANNEL_ID = "smartbus_dest_monitor"
        const val ALARM_CHANNEL_ID = "smartbus_dest_wakeup_alarm"
        const val MONITOR_NOTIF_ID = 2001
        const val ALARM_NOTIF_ID = 2002

        private val _alertState = MutableStateFlow(DestinationAlertState())
        val alertState: StateFlow<DestinationAlertState> = _alertState.asStateFlow()

        fun startAlert(
            context: Context,
            stopName: String,
            lat: Double,
            lng: Double,
            radiusMeters: Int = 1000,
            isNightLoudMode: Boolean = true,
            tripId: String? = null
        ) {
            val intent = Intent(context, DestinationAlertService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STOP_NAME, stopName)
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
                putExtra(EXTRA_RADIUS, radiusMeters)
                putExtra(EXTRA_NIGHT_MODE, isNightLoudMode)
                if (tripId != null) putExtra(EXTRA_TRIP_ID, tripId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAlert(context: Context) {
            val intent = Intent(context, DestinationAlertService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun dismissAlarm(context: Context) {
            val intent = Intent(context, DestinationAlertService::class.java).apply {
                action = ACTION_DISMISS_ALARM
            }
            context.startService(intent)
        }

        fun triggerTestAlarm(context: Context, stopName: String = "Katpadi Junction") {
            val intent = Intent(context, DestinationAlertService::class.java).apply {
                action = ACTION_TEST_ALARM
                putExtra(EXTRA_STOP_NAME, stopName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        createNotificationChannels()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                evaluateProximity(loc.latitude, loc.longitude, "Phone GPS")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val stopName = intent.getStringExtra(EXTRA_STOP_NAME) ?: "Destination Stop"
                val lat = intent.getDoubleExtra(EXTRA_LAT, 13.2848)
                val lng = intent.getDoubleExtra(EXTRA_LNG, 74.7505)
                val radius = intent.getIntExtra(EXTRA_RADIUS, 1000)
                val nightMode = intent.getBooleanExtra(EXTRA_NIGHT_MODE, true)
                val tripId = intent.getStringExtra(EXTRA_TRIP_ID)

                stopAlarmMediaAndVibrate()
                _alertState.value = DestinationAlertState(
                    isActive = true,
                    isRinging = false,
                    stopName = stopName,
                    targetLat = lat,
                    targetLng = lng,
                    radiusMeters = radius,
                    isNightLoudMode = nightMode,
                    tripId = tripId,
                    distanceMeters = null,
                    sourceLabel = "Acquiring location..."
                )

                startForeground(
                    MONITOR_NOTIF_ID,
                    buildMonitorNotification(stopName, "Monitoring arrival within ${formatRadius(radius)}")
                )
                startPhoneGpsUpdates()
                startBusLocationPolling(tripId)
            }

            ACTION_TEST_ALARM -> {
                val stopName = intent.getStringExtra(EXTRA_STOP_NAME) ?: "Katpadi Junction"
                _alertState.value = _alertState.value.copy(
                    isActive = true,
                    stopName = stopName,
                    isNightLoudMode = true,
                    distanceMeters = 450.0,
                    sourceLabel = "Night Wake-Up Test"
                )
                startForeground(
                    MONITOR_NOTIF_ID,
                    buildMonitorNotification(stopName, "Test Wake-Up Alarm Triggered!")
                )
                triggerWakeUpAlarm()
            }

            ACTION_DISMISS_ALARM -> {
                stopAlarmMediaAndVibrate()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(ALARM_NOTIF_ID)
                _alertState.value = DestinationAlertState()
                stopLocationAndPolling()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_STOP -> {
                stopAlarmMediaAndVibrate()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(ALARM_NOTIF_ID)
                _alertState.value = DestinationAlertState()
                stopLocationAndPolling()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startPhoneGpsUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 8000L)
            .setMinUpdateIntervalMillis(4000L)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            _alertState.value = _alertState.value.copy(sourceLabel = "Waiting for Bus GPS (Phone GPS permission off)")
        }
    }

    private fun startBusLocationPolling(tripId: String?) {
        busPollJob?.cancel()
        if (tripId == null) return
        busPollJob = serviceScope.launch {
            val app = application as SmartBusApplication
            while (isActive && _alertState.value.isActive) {
                try {
                    app.transitRepository.getTripById(tripId).onSuccess { trip ->
                        val loc = trip.lastLocation
                        if (loc != null) {
                            evaluateProximity(loc.latitude, loc.longitude, "Live Bus GPS")
                        }
                    }
                } catch (_: Exception) {
                }
                delay(12000L)
            }
        }
    }

    private fun evaluateProximity(lat: Double, lng: Double, source: String) {
        val state = _alertState.value
        if (!state.isActive) return

        val distMeters = haversineMeters(lat, lng, state.targetLat, state.targetLng)
        _alertState.value = state.copy(
            distanceMeters = distMeters,
            sourceLabel = source
        )

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val distText = if (distMeters >= 1000) {
            String.format("%.1f km away", distMeters / 1000.0)
        } else {
            "${distMeters.roundToInt()} m away"
        }
        nm.notify(MONITOR_NOTIF_ID, buildMonitorNotification(state.stopName, "$distText • via $source"))

        if (distMeters <= state.radiusMeters && !state.isRinging) {
            triggerWakeUpAlarm()
        }
    }

    private fun triggerWakeUpAlarm() {
        val state = _alertState.value
        if (state.isRinging) return
        _alertState.value = state.copy(isRinging = true)

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartBus:DestWakeLock").apply {
                acquire(2 * 60 * 1000L) // Max 2 minutes safety timeout
            }
        } catch (_: Exception) {
        }

        // Play Loud Alarm Ringtone (loops in Night Mode so sleeping passengers wake up!)
        try {
            val alarmUri = if (state.isNightLoudMode) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@DestinationAlertService, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(if (state.isNightLoudMode) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = state.isNightLoudMode
                prepare()
                start()
            }
        } catch (_: Exception) {
        }

        // Strong Vibration Pattern
        try {
            val pattern = longArrayOf(0, 700, 350, 700, 350, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, if (state.isNightLoudMode) 0 else -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, if (state.isNightLoudMode) 0 else -1)
            }
        } catch (_: Exception) {
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ALARM_NOTIF_ID, buildAlarmNotification(state.stopName, state.distanceMeters))
    }

    private fun stopAlarmMediaAndVibrate() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun stopLocationAndPolling() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {
        }
        busPollJob?.cancel()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val monitorChannel = NotificationChannel(
                MONITOR_CHANNEL_ID,
                "Destination Alert Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors distance to your bus destination while asleep or locked."
            }
            nm.createNotificationChannel(monitorChannel)

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Wake-Up Destination Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority night wake-up alert before reaching your stop."
                enableVibration(true)
            }
            nm.createNotificationChannel(alarmChannel)
        }
    }

    private fun buildMonitorNotification(stopName: String, content: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DestinationAlertService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setContentTitle("🛌 Stop Alarm Active: $stopName")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Turn Off", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun buildAlarmNotification(stopName: String, distanceMeters: Double?): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = PendingIntent.getService(
            this, 3,
            Intent(this, DestinationAlertService::class.java).apply { action = ACTION_DISMISS_ALARM },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val distStr = if (distanceMeters != null) "${distanceMeters.roundToInt()} m away" else "Arriving shortly"

        return NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle("⏰ WAKE UP! Approaching $stopName")
            .setContentText("Your bus stop ($stopName) is $distStr. Prepare to alight!")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "I'M AWAKE • DISMISS", dismissIntent)
            .build()
    }

    private fun formatRadius(meters: Int): String =
        if (meters >= 1000) "${meters / 1000} km" else "$meters m"

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 6371000.0 * 2 * asin(sqrt(a))
    }

    override fun onDestroy() {
        stopAlarmMediaAndVibrate()
        stopLocationAndPolling()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
