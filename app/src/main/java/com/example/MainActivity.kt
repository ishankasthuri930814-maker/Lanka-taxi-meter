package com.example

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.LoadAdError
import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlin.random.Random

// --- Color Palette ---
val ThemeDarkBg = Color(0xFF0F1116)
val ThemeCardBg = Color(0xFF1B1E26)
val MeterScreenBg = Color(0xFF000000)
val TaxiYellow = Color(0xFFFFA000)
val TaxiYellowDim = Color(0xFF3E2723)
val AccentGreen = Color(0xFF10B981)
val AccentRed = Color(0xFFEF4444)
val PrintBlue = Color(0xFF3B82F6)
val MeterFareGreen = Color(0xFF00FF66)

// --- Meter States ---
enum class MeterState {
    READY,
    RUNNING,
    CONFIRM_END,
    RECEIPT
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Pre-create WebView js and wasm cache directories to prevent Chromium from logging "opendir: No such file or directory" errors
        try {
            val cacheDir = this.cacheDir
            val jsDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            val wasmDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!jsDir.exists()) jsDir.mkdirs()
            if (!wasmDir.exists()) wasmDir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            MobileAds.initialize(this) {
                AdMobManager.loadInterstitialAd(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setContent {
            MyApplicationTheme(darkTheme = true) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = ThemeDarkBg
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        TaxiMeterApp()
                    }
                }
            }
        }
    }
}

// --- ViewModel ---
class TaxiMeterViewModel(context: Context) : ViewModel() {
    private val prefs: SharedPreferences = context.getSharedPreferences("taxi_meter_prefs", Context.MODE_PRIVATE)

    enum class Language { SINHALA, ENGLISH }

    private val _currentLanguage = MutableStateFlow(Language.SINHALA)
    val currentLanguage: StateFlow<Language> = _currentLanguage.asStateFlow()

    fun setLanguage(language: Language) {
        _currentLanguage.value = language
    }

    // Room Database access
    private val db = AppDatabase.getDatabase(context)
    private val tripDao = db.tripDao()

    val tripHistory: StateFlow<List<TripEntity>> = tripDao.getAllTrips()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteTrip(id: Int) {
        viewModelScope.launch {
            tripDao.deleteTripById(id)
        }
    }

    fun clearAllTrips() {
        viewModelScope.launch {
            tripDao.clearAllTrips()
        }
    }

    // Rate Settings
    private val _baseFare = MutableStateFlow(prefs.getFloat("base_fare", 110.0f).toDouble())
    val baseFare: StateFlow<Double> = _baseFare.asStateFlow()

    private val _subsequentKmFare = MutableStateFlow(prefs.getFloat("subsequent_fare", 90.0f).toDouble())
    val subsequentKmFare: StateFlow<Double> = _subsequentKmFare.asStateFlow()

    private val _waitingFarePerMin = MutableStateFlow(prefs.getFloat("waiting_fare", 10.0f).toDouble())
    val waitingFarePerMin: StateFlow<Double> = _waitingFarePerMin.asStateFlow()

    // Meter State
    private val _meterState = MutableStateFlow(MeterState.READY)
    val meterState: StateFlow<MeterState> = _meterState.asStateFlow()

    // Tracking State
    private val _totalDistanceKm = MutableStateFlow(0.0)
    val totalDistanceKm: StateFlow<Double> = _totalDistanceKm.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _waitingSeconds = MutableStateFlow(0L)
    val waitingSeconds: StateFlow<Long> = _waitingSeconds.asStateFlow()

    private val _currentSpeedKmh = MutableStateFlow(0.0)
    val currentSpeedKmh: StateFlow<Double> = _currentSpeedKmh.asStateFlow()

    private val _totalFare = MutableStateFlow(0.0)
    val totalFare: StateFlow<Double> = _totalFare.asStateFlow()

    // GPS State
    private val _isGpsTracking = MutableStateFlow(true)
    val isGpsTracking: StateFlow<Boolean> = _isGpsTracking.asStateFlow()

    private val _isGpsPermissionGranted = MutableStateFlow(false)
    val isGpsPermissionGranted: StateFlow<Boolean> = _isGpsPermissionGranted.asStateFlow()

    private val _isGpsActiveAndSignal = MutableStateFlow(false)
    val isGpsActiveAndSignal: StateFlow<Boolean> = _isGpsActiveAndSignal.asStateFlow()

    private val _isGpsSettingsEnabled = MutableStateFlow(false)
    val isGpsSettingsEnabled: StateFlow<Boolean> = _isGpsSettingsEnabled.asStateFlow()

    // Receipt details
    var receiptDate by mutableStateOf("")
    var receiptTime by mutableStateOf("")
    var receiptTripId by mutableStateOf("")
    var receiptBaseDistance = 1.0
    var receiptSubsequentDistance = 0.0
    var receiptBaseFarePaid = 0.0
    var receiptSubsequentFarePaid = 0.0
    var receiptWaitingFarePaid = 0.0
    var receiptTotalFarePaid = 0.0

    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0L
    private var lastGpsUpdateTime: Long = 0L
    private var trackingStartTime: Long = 0L

    init {
        // Check initial permission state
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val isGranted = hasFine || hasCoarse
        _isGpsPermissionGranted.value = isGranted
        
        val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        _isGpsSettingsEnabled.value = try {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                    locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        } catch (e: Exception) {
            false
        }
    }

    fun updatePermissionState(isGranted: Boolean) {
        _isGpsPermissionGranted.value = isGranted
        if (!isGranted) {
            _isGpsTracking.value = false
        } else {
            _isGpsTracking.value = true
        }
    }

    fun setGpsSettingsEnabled(enabled: Boolean) {
        _isGpsSettingsEnabled.value = enabled
    }

    fun refreshGpsStatus(context: Context) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val isGranted = hasFine || hasCoarse
        _isGpsPermissionGranted.value = isGranted
        
        val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        _isGpsSettingsEnabled.value = try {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                    locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        } catch (e: Exception) {
            false
        }
    }

    fun setGpsTrackingEnabled(enabled: Boolean, context: Context) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        _isGpsPermissionGranted.value = hasPermission
        _isGpsTracking.value = enabled
    }

    fun updateRates(base: Double, subsequent: Double, waiting: Double) {
        _baseFare.value = base
        _subsequentKmFare.value = subsequent
        _waitingFarePerMin.value = waiting

        prefs.edit()
            .putFloat("base_fare", base.toFloat())
            .putFloat("subsequent_fare", subsequent.toFloat())
            .putFloat("waiting_fare", waiting.toFloat())
            .apply()

        recalculateFare()
    }

    fun startTrip() {
        _totalDistanceKm.value = 0.0
        _elapsedSeconds.value = 0L
        _waitingSeconds.value = 0L
        _currentSpeedKmh.value = 0.0
        lastLocation = null
        lastLocationTime = 0L
        lastGpsUpdateTime = 0L
        trackingStartTime = System.currentTimeMillis()
        receiptTripId = String.format("%06d", Random.nextInt(100000, 999999))

        recalculateFare()
        _meterState.value = MeterState.RUNNING
    }

    fun requestEndTrip() {
        // Prevent accidental double-taps immediately after starting a new trip
        if (System.currentTimeMillis() - trackingStartTime < 2000L) {
            return
        }
        _meterState.value = MeterState.CONFIRM_END
    }

    fun cancelEndTrip() {
        _meterState.value = MeterState.RUNNING
    }

    fun confirmEndTrip() {
        _meterState.value = MeterState.RECEIPT
        _currentSpeedKmh.value = 0.0

        // Capture Receipt Details
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        receiptDate = sdfDate.format(Date())
        receiptTime = sdfTime.format(Date())

        receiptBaseDistance = if (_totalDistanceKm.value >= 1.0) 1.0 else _totalDistanceKm.value
        receiptSubsequentDistance = if (_totalDistanceKm.value > 1.0) _totalDistanceKm.value - 1.0 else 0.0

        receiptBaseFarePaid = _baseFare.value
        receiptSubsequentFarePaid = receiptSubsequentDistance * _subsequentKmFare.value
        receiptWaitingFarePaid = (_waitingSeconds.value / 60.0) * _waitingFarePerMin.value
        receiptTotalFarePaid = _totalFare.value

        // Auto-save Trip to local Room database
        val newTrip = TripEntity(
            tripId = receiptTripId,
            date = receiptDate,
            time = receiptTime,
            totalFare = receiptTotalFarePaid,
            distanceKm = _totalDistanceKm.value,
            waitingSeconds = _waitingSeconds.value
        )
        viewModelScope.launch {
            tripDao.insertTrip(newTrip)
        }
    }

    fun resetMeter() {
        _totalDistanceKm.value = 0.0
        _elapsedSeconds.value = 0L
        _waitingSeconds.value = 0L
        _currentSpeedKmh.value = 0.0
        _totalFare.value = 0.0
        lastLocation = null
        lastLocationTime = 0L
        _meterState.value = MeterState.READY
    }

    fun tickOneSecond() {
        if (_meterState.value != MeterState.RUNNING) return

        _elapsedSeconds.value += 1

        if (isEmulator()) {
            // Simulate realistic taxi movement on Emulator/Preview so the user can test the applet!
            _isGpsActiveAndSignal.value = true
            lastGpsUpdateTime = System.currentTimeMillis()
            
            // Every 25 seconds, let's simulate a traffic light stop for 5 seconds
            val cycleSec = _elapsedSeconds.value % 30
            if (cycleSec in 20..24) {
                // Stopped at traffic light
                _currentSpeedKmh.value = 0.0
                _waitingSeconds.value += 1
            } else {
                // Driving at around 42 km/h
                val simulatedSpeed = 40.0 + (1..5).random() * 0.5
                _currentSpeedKmh.value = (kotlin.math.round(simulatedSpeed * 10.0)) / 10.0
                // Add distance covered in 1 second: speed in km/h / 3600
                _totalDistanceKm.value += (simulatedSpeed / 3600.0)
            }
            recalculateFare()
        } else {
            // Real device behavior: waiting seconds and speed fallback are based on real GPS updates
            val now = System.currentTimeMillis()
            // If we haven't received a GPS update in 8 seconds, assume we are stationary
            if (lastGpsUpdateTime > 0L && now - lastGpsUpdateTime > 8000L) {
                _currentSpeedKmh.value = 0.0
            }

            // Real-time waiting calculation when stopped or speed is very low (< 3.5 km/h)
            if (_currentSpeedKmh.value < 3.5) {
                _waitingSeconds.value += 1
            }

            recalculateFare()
        }
    }

    fun handleGpsLocationUpdate(location: Location) {
        if (!_isGpsTracking.value) return

        _isGpsActiveAndSignal.value = true
        lastGpsUpdateTime = System.currentTimeMillis()

        if (_meterState.value != MeterState.RUNNING) return

        // 1. Filter out poor accuracy fixes immediately (greater than 80 meters)
        val hasAcc = location.hasAccuracy()
        val acc = if (hasAcc) location.accuracy else 999.0f
        if (hasAcc && acc > 80.0f) {
            return
        }

        val now = System.currentTimeMillis()
        val lastLoc = lastLocation
        if (lastLoc == null) {
            lastLocation = location
            lastLocationTime = now
            val rawSpeed = if (location.hasSpeed()) (location.speed * 3.6) else 0.0
            val roundedSpeed = (kotlin.math.round(rawSpeed * 10.0)) / 10.0
            _currentSpeedKmh.value = if (roundedSpeed < 1.0) 0.0 else roundedSpeed
            recalculateFare()
            return
        }

        // Calculate time difference from the baseline receipt time
        val timeDeltaSec = (now - lastLocationTime) / 1000.0
        if (timeDeltaSec <= 0.0) {
            return
        }

        // Calculate distance from the baseline
        val distanceMeters = lastLoc.distanceTo(location).toDouble()
        if (distanceMeters <= 0.0) {
            return
        }

        // If updates are coming too fast (less than 0.5 seconds), ignore them to let distance build up
        if (timeDeltaSec < 0.5) {
            return
        }

        // Use hardware speed if available and valid, otherwise calculate mathematically
        val deviceSpeedKmh = if (location.hasSpeed()) location.speed * 3.6 else -1.0
        val calculatedSpeedKmh = (distanceMeters / timeDeltaSec) * 3.6
        val speedKmh = if (deviceSpeedKmh >= 0.0) deviceSpeedKmh else calculatedSpeedKmh

        // Filter out extreme, unrealistic GPS jumps (> 140 km/h)
        if (speedKmh > 140.0) {
            // Reset baseline to current location to recover from the jump without accumulating bad distance
            lastLocation = location
            lastLocationTime = now
            _currentSpeedKmh.value = 0.0
            return
        }

        // To filter out GPS drift when stationary, we require a minimum distance of 2.0 meters.
        // If they moved >= 2.0 meters, it is real movement!
        if (distanceMeters >= 2.0) {
            // Accumulate distance
            _totalDistanceKm.value += (distanceMeters / 1000.0)
            
            val roundedSpeed = (kotlin.math.round(speedKmh * 10.0)) / 10.0
            _currentSpeedKmh.value = if (roundedSpeed < 1.0) 0.0 else roundedSpeed
            
            lastLocation = location
            lastLocationTime = now
        } else {
            // Stationary or very minor drift.
            _currentSpeedKmh.value = 0.0
            
            // To prevent stationary coordinates from slowly walking away over a long time (cumulative drift),
            // we reset the baseline ONLY if they've stayed in a tiny area for more than 15 seconds.
            if (timeDeltaSec > 15.0 && distanceMeters < 5.0) {
                lastLocation = location
                lastLocationTime = now
            }
        }

        recalculateFare()
    }

    fun setGpsSignalStatus(active: Boolean) {
        _isGpsActiveAndSignal.value = active
    }

    private fun recalculateFare() {
        val distance = _totalDistanceKm.value
        val base = _baseFare.value
        val subRate = _subsequentKmFare.value
        val waitRate = _waitingFarePerMin.value
        val waitSec = _waitingSeconds.value

        val distanceFare = if (distance <= 1.0) {
            base
        } else {
            base + (distance - 1.0) * subRate
        }

        val waitingFare = (waitSec / 60.0) * waitRate
        _totalFare.value = distanceFare + waitingFare
    }
}

// --- Main App Composable ---
@Composable
fun TaxiMeterApp() {
    val context = LocalContext.current
    val viewModel: TaxiMeterViewModel = viewModel { TaxiMeterViewModel(context) }

    var showSimulatedInterstitial by remember { mutableStateOf(false) }
    var onInterstitialDismissedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val bottomPadding = 80.dp

    val meterState by viewModel.meterState.collectAsState()
    val baseFare by viewModel.baseFare.collectAsState()
    val subsequentKmFare by viewModel.subsequentKmFare.collectAsState()
    val waitingFarePerMin by viewModel.waitingFarePerMin.collectAsState()

    val totalFare by viewModel.totalFare.collectAsState()
    val totalDistanceKm by viewModel.totalDistanceKm.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val waitingSeconds by viewModel.waitingSeconds.collectAsState()
    val currentSpeedKmh by viewModel.currentSpeedKmh.collectAsState()

    val isGpsTracking by viewModel.isGpsTracking.collectAsState()
    val isGpsPermissionGranted by viewModel.isGpsPermissionGranted.collectAsState()
    val isGpsActiveAndSignal by viewModel.isGpsActiveAndSignal.collectAsState()
    val isGpsSettingsEnabled by viewModel.isGpsSettingsEnabled.collectAsState()

    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val tripHistory by viewModel.tripHistory.collectAsState()

    var showRateDialog by remember { mutableStateOf(false) }
    var showRatesAndHistoryDialog by remember { mutableStateOf(false) }

    // Location Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        viewModel.updatePermissionState(fineGranted || coarseGranted)
        if (fineGranted || coarseGranted) {
            viewModel.setGpsTrackingEnabled(true, context)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Dynamic Location Listening using FusedLocationProviderClient with raw LocationManager fallback (Lifecycle-aware)
    DisposableEffect(isGpsTracking, isGpsPermissionGranted, lifecycleOwner) {
        var fusedLocationClient: FusedLocationProviderClient? = null
        var locationCallback: LocationCallback? = null
        var locationManager: LocationManager? = null
        var locationListener: LocationListener? = null
        var isRegistered = false
        var fusedRegistered = false
        var nativeRegistered = false

        fun startLocationTracking() {
            if (isRegistered) return
            if (isGpsTracking && isGpsPermissionGranted) {
                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) {
                    isRegistered = true // Mark registered immediately to lock concurrent entries

                    // Check if Google Play Services / GMS is available
                    val gmsAvailable = try {
                        com.google.android.gms.common.GoogleApiAvailability.getInstance()
                            .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
                    } catch (e: Throwable) {
                        false
                    }

                    fun registerNativeLocationManager() {
                        if (!isRegistered) return
                        try {
                            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                            val listener = object : LocationListener {
                                override fun onLocationChanged(location: Location) {
                                    if (isRegistered) {
                                        viewModel.handleGpsLocationUpdate(location)
                                    }
                                }
                                @Deprecated("Deprecated in Java")
                                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                                override fun onProviderEnabled(provider: String) {
                                    if (isRegistered) {
                                        viewModel.setGpsSignalStatus(true)
                                    }
                                }
                                override fun onProviderDisabled(provider: String) {
                                    if (!isRegistered) return
                                    val gpsOn = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
                                    val netOn = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
                                    if (!gpsOn && !netOn) {
                                        viewModel.setGpsSignalStatus(false)
                                    }
                                }
                            }
                            locationListener = listener

                            var nativeOk = false
                            if (hasFine && locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                                locationManager?.requestLocationUpdates(
                                    LocationManager.GPS_PROVIDER,
                                    1000L,
                                    0.0f,
                                    listener,
                                    Looper.getMainLooper()
                                )
                                nativeOk = true
                            } else if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                                locationManager?.requestLocationUpdates(
                                    LocationManager.NETWORK_PROVIDER,
                                    1000L,
                                    0.0f,
                                    listener,
                                    Looper.getMainLooper()
                                )
                                nativeOk = true
                            }

                            if (nativeOk && isRegistered) {
                                nativeRegistered = true
                                viewModel.setGpsSignalStatus(true)
                            } else {
                                locationManager?.removeUpdates(listener)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (gmsAvailable) {
                        try {
                            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                            
                            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                                .setMinUpdateIntervalMillis(500L)
                                .build()

                            val callback = object : LocationCallback() {
                                override fun onLocationResult(locationResult: LocationResult) {
                                    if (isRegistered) {
                                        for (location in locationResult.locations) {
                                            viewModel.handleGpsLocationUpdate(location)
                                        }
                                    }
                                }
                            }
                            locationCallback = callback

                            fusedLocationClient?.requestLocationUpdates(
                                locationRequest,
                                callback,
                                Looper.getMainLooper()
                            )?.addOnSuccessListener {
                                if (isRegistered) {
                                    fusedRegistered = true
                                    viewModel.setGpsSignalStatus(true)
                                } else {
                                    fusedLocationClient?.removeLocationUpdates(callback)
                                }
                            }?.addOnFailureListener {
                                if (isRegistered) {
                                    fusedRegistered = false
                                    registerNativeLocationManager()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            registerNativeLocationManager()
                        }
                    } else {
                        registerNativeLocationManager()
                    }
                } else {
                    viewModel.updatePermissionState(false)
                }
            }
        }

        fun stopLocationTracking() {
            isRegistered = false
            try {
                if (fusedRegistered || locationCallback != null) {
                    locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
                }
            } catch (e: Exception) {
                // ignore
            }
            try {
                if (nativeRegistered || locationListener != null) {
                    locationListener?.let { locationManager?.removeUpdates(it) }
                }
            } catch (e: Exception) {
                // ignore
            }
            fusedRegistered = false
            nativeRegistered = false
        }

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshGpsStatus(context)
                startLocationTracking()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                stopLocationTracking()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        // Initial check: if lifecycle is already resumed, start location tracking
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            viewModel.refreshGpsStatus(context)
            startLocationTracking()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopLocationTracking()
        }
    }

    // General 1-second ticker coroutine
    LaunchedEffect(meterState) {
        while (meterState == MeterState.RUNNING) {
            delay(1000L)
            viewModel.tickOneSecond()
        }
    }

    // Polling effect to keep GPS permission and active status in sync in real-time
    LaunchedEffect(Unit) {
        while (true) {
            val hasFine = ContextCompat.checkSelfPermission(context.applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context.applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val isGranted = hasFine || hasCoarse
            
            viewModel.updatePermissionState(isGranted)
            
            if (isGranted) {
                val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val isGpsEnabled = try {
                    locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                            locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
                } catch (e: Exception) {
                    false
                }
                viewModel.setGpsSettingsEnabled(isGpsEnabled)
            } else {
                viewModel.setGpsSettingsEnabled(false)
                viewModel.setGpsSignalStatus(false)
            }
            delay(1500L)
        }
    }

    // Main Screen with Pinned Bottom Banner Ad
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeDarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding) // Make dynamic space so scrollable content isn't covered by the adaptive stacked banners
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (meterState != MeterState.RECEIPT) {
                // --- Header Status ---
                HeaderSection(
                    isGpsActive = isGpsActiveAndSignal,
                    isPermissionGranted = isGpsPermissionGranted,
                    currentLanguage = currentLanguage,
                    onLanguageToggle = {
                        val newLang = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) {
                            TaxiMeterViewModel.Language.SINHALA
                        } else {
                            TaxiMeterViewModel.Language.ENGLISH
                        }
                        viewModel.setLanguage(newLang)
                    },
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )

                if (!isGpsPermissionGranted || !isGpsSettingsEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0x26EF4444)),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "GPS Alert",
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) 
                                        "GPS Connection Required" 
                                    else 
                                        "GPS ක්‍රියාත්මක කිරීම අවශ්‍යයි",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Text(
                                text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH)
                                    "For accurate fare calculation, please allow location permission and turn on phone GPS."
                                else
                                    "නිවැරදිව ධාවන දුර සහ කුලිය ගණනය කිරීමට කරුණාකර දුරකථනයේ GPS ස්ථාන සේවාව ක්‍රියාත්මක කරන්න.",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isGpsPermissionGranted) {
                                    Button(
                                        onClick = {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        },
                                        modifier = Modifier.weight(1.1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Grant Permission" else "අවසර ලබා දෙන්න",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                Button(
                                    onClick = {
                                        try {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C313D)),
                                    border = BorderStroke(1.dp, Color(0xFF4B5563)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "GPS Settings" else "GPS සැකසුම්",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

        // --- Meter Screen or Receipt ---
        if (meterState != MeterState.RECEIPT) {
            // Live Meter View
            MeterDisplayCard(
                fare = totalFare,
                statusText = when (meterState) {
                    MeterState.READY -> "සූදානම් / READY"
                    MeterState.RUNNING -> "ධාවනය වේ / RUNNING"
                    MeterState.CONFIRM_END -> "තහවුරු කරන්න / CONFIRM"
                    else -> "සූදානම්"
                },
                isFlashing = meterState == MeterState.RUNNING
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Grid Detail Panel
            InfoGrid(
                distanceKm = totalDistanceKm,
                waitingSec = waitingSeconds,
                speedKmh = currentSpeedKmh,
                totalSec = elapsedSeconds
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Control Actions
            ControlPanel(
                state = meterState,
                isGpsTracking = isGpsTracking,
                isPermissionGranted = isGpsPermissionGranted,
                onStartTrip = { viewModel.startTrip() },
                onEndTrip = { viewModel.requestEndTrip() },
                onGpsToggle = { enabled ->
                    if (enabled && !isGpsPermissionGranted) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        viewModel.setGpsTrackingEnabled(enabled, context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Rates & Trip History Button Hub
            Button(
                onClick = { showRatesAndHistoryDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ThemeCardBg),
                border = BorderStroke(1.5.dp, TaxiYellow),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Rates & History",
                        tint = TaxiYellow,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Rates & Trip History" else "ගාස්තු සහ ධාවන ඉතිහාසය",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Receipt / Final Fare View
            LaunchedEffect(Unit) {
                AdMobManager.loadInterstitialAd(context)
            }

            ReceiptScreen(
                date = viewModel.receiptDate,
                time = viewModel.receiptTime,
                tripId = viewModel.receiptTripId,
                baseDistance = viewModel.receiptBaseDistance,
                subsequentDistance = viewModel.receiptSubsequentDistance,
                baseFare = viewModel.receiptBaseFarePaid,
                subsequentFare = viewModel.receiptSubsequentFarePaid,
                waitingMin = viewModel.waitingSeconds.collectAsState().value / 60.0,
                waitingFare = viewModel.receiptWaitingFarePaid,
                totalFare = viewModel.receiptTotalFarePaid,
                totalDist = totalDistanceKm,
                totalTimeStr = formatDuration(elapsedSeconds),
                currentLanguage = currentLanguage,
                onReset = {
                    val activity = context.findActivity()
                    if (activity != null) {
                        AdMobManager.showInterstitialAd(
                            activity = activity,
                            onShowMock = {
                                onInterstitialDismissedAction = { viewModel.resetMeter() }
                                showSimulatedInterstitial = true
                            },
                            onAdDismissed = {
                                viewModel.resetMeter()
                            }
                        )
                    } else {
                        viewModel.resetMeter()
                    }
                },
                onPrint = {
                    printReceipt(
                        context = context,
                        date = viewModel.receiptDate,
                        time = viewModel.receiptTime,
                        tripId = viewModel.receiptTripId,
                        baseFare = viewModel.receiptBaseFarePaid,
                        subsequentKmRate = subsequentKmFare,
                        subsequentDist = viewModel.receiptSubsequentDistance,
                        subsequentFare = viewModel.receiptSubsequentFarePaid,
                        waitingMin = viewModel.waitingSeconds.value / 60.0,
                        waitingFare = viewModel.receiptWaitingFarePaid,
                        totalFare = viewModel.receiptTotalFarePaid,
                        totalDist = totalDistanceKm,
                        totalTime = formatDuration(elapsedSeconds)
                    )
                }
            )
        }
        }
        
        // --- Pinned Bottom AdMob Banner (Single) ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            AdMobBanner(
                adUnitId = if (isEmulator()) "ca-app-pub-3940256099942544/6300978111" else "ca-app-pub-7472113156561687/3463243021",
                mockTitle = "LANKA TAXI METER PREMIUM",
                mockSub = "ඇප් එක දැන්ම යාවත්කාලීන කරගන්න!"
            )
        }
    }

    // --- End Trip Confirmation Dialog ---
    if (meterState == MeterState.CONFIRM_END) {
        EndTripConfirmDialog(
            totalFare = totalFare,
            onConfirm = { viewModel.confirmEndTrip() },
            onCancel = { viewModel.cancelEndTrip() }
        )
    }

    // --- Edit Rate Settings Dialog ---
    if (showRateDialog) {
        EditRatesDialog(
            currentBase = baseFare,
            currentSubsequent = subsequentKmFare,
            currentWaiting = waitingFarePerMin,
            onSave = { b, s, w ->
                viewModel.updateRates(b, s, w)
                showRateDialog = false
            },
            onDismiss = { showRateDialog = false }
        )
    }

    // --- Rates and History Dialog ---
    if (showRatesAndHistoryDialog) {
        RatesAndHistoryDialog(
            baseFare = baseFare,
            subsequentKmFare = subsequentKmFare,
            waitingFarePerMin = waitingFarePerMin,
            editable = meterState == MeterState.READY,
            onEditClick = { showRateDialog = true },
            trips = tripHistory,
            currentLanguage = currentLanguage,
            onDeleteTrip = { id -> viewModel.deleteTrip(id) },
            onClearAll = { viewModel.clearAllTrips() },
            onPrintTrip = { trip ->
                printReceipt(
                    context = context,
                    date = trip.date,
                    time = trip.time,
                    tripId = trip.tripId,
                    baseFare = baseFare,
                    subsequentKmRate = subsequentKmFare,
                    subsequentDist = if (trip.distanceKm > 1.0) trip.distanceKm - 1.0 else 0.0,
                    subsequentFare = if (trip.distanceKm > 1.0) (trip.distanceKm - 1.0) * subsequentKmFare else 0.0,
                    waitingMin = trip.waitingSeconds / 60.0,
                    waitingFare = (trip.waitingSeconds / 60.0) * waitingFarePerMin,
                    totalFare = trip.totalFare,
                    totalDist = trip.distanceKm,
                    totalTime = formatDuration(trip.waitingSeconds)
                )
            },
            onDismiss = { showRatesAndHistoryDialog = false }
        )
    }

    // --- Simulated Interstitial Full Screen Ad Dialog ---
    if (showSimulatedInterstitial) {
        Dialog(
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            onDismissRequest = {
                showSimulatedInterstitial = false
                onInterstitialDismissedAction?.invoke()
            }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F172A)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE2E8F0).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ADVERTISEMENT / දැන්වීම්",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                showSimulatedInterstitial = false
                                onInterstitialDismissedAction?.invoke()
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Ad",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(TaxiYellow.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                .border(2.dp, TaxiYellow, RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalTaxi,
                                contentDescription = "Taxi Premium Logo",
                                tint = TaxiYellow,
                                modifier = Modifier.size(56.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "LANKA TAXI METER PREMIUM",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "ඇප් එක දැන්ම යාවත්කාලීන කරගන්න!\nUpgrade to premium for offline GPS tracking, ad-free experience, custom vehicle rates, and digital receipts!",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                showSimulatedInterstitial = false
                                onInterstitialDismissedAction?.invoke()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "INSTALL NOW / බාගත කරගන්න",
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Composable: Header Section ---
@Composable
fun HeaderSection(
    isGpsActive: Boolean,
    isPermissionGranted: Boolean,
    currentLanguage: TaxiMeterViewModel.Language,
    onLanguageToggle: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
        border = BorderStroke(1.dp, Color(0xFF2C313D))
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Lanka Tuk Taxi Meter" else "ලංකා ටුක් ටැක්සි මීටරය",
                    color = TaxiYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Sri Lankan GPS Tuk Tuk Meter" else "ශ්‍රී ලංකා GPS ටුක් රථ මීටරය",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Language Toggle Pill Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLanguageToggle() },
                    color = Color(0xFF2C313D),
                    border = BorderStroke(1.dp, Color(0xFF4B5563))
                ) {
                    Text(
                        text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "සිංහල" else "EN",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // GPS Permission Status Badge
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { if (!isPermissionGranted) onRequestPermission() },
                    color = if (isGpsActive) AccentGreen.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.15f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isGpsActive) AccentGreen else AccentRed
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isGpsActive) AccentGreen else AccentRed, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isGpsActive) (if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "GPS ACTIVE" else "GPS සක්‍රියයි") else (if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "GPS INACTIVE" else "GPS අක්‍රියයි"),
                            color = if (isGpsActive) AccentGreen else AccentRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// --- Composable: Meter Display ---
@Composable
fun MeterDisplayCard(
    fare: Double,
    statusText: String,
    isFlashing: Boolean
) {
    var tickState by remember { mutableStateOf(true) }

    if (isFlashing) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(750L)
                tickState = !tickState
            }
        }
    } else {
        tickState = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(3.dp, MeterFareGreen), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MeterScreenBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (isFlashing && tickState) MeterFareGreen else Color(0xFF1B5E20),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        color = if (isFlashing) MeterFareGreen else Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "CAB METER MK-V",
                    color = Color.DarkGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "ශුද්ධ මුදල / TOTAL FARE",
                color = MeterFareGreen.copy(alpha = 0.9f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Large Fare LED display (Splitting whole and decimal part cleanly using String.format)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "LKR ",
                    color = MeterFareGreen,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val formattedFare = String.format(Locale.US, "%.2f", fare)
                val parts = formattedFare.split(".")
                val wholePart = parts[0]
                val decimalPart = if (parts.size > 1) parts[1] else "00"

                Text(
                    text = wholePart,
                    color = MeterFareGreen,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-2).sp
                )

                Text(
                    text = ".$decimalPart",
                    color = MeterFareGreen,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// --- Composable: Info Grid ---
@Composable
fun InfoGrid(
    distanceKm: Double,
    waitingSec: Long,
    speedKmh: Double,
    totalSec: Long
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoCard(
                icon = Icons.Default.DirectionsCar,
                sinhalaLabel = "දුර කි.මී.",
                englishLabel = "DISTANCE",
                value = String.format(Locale.US, "%.2f", distanceKm),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            InfoCard(
                icon = Icons.Default.HourglassEmpty,
                sinhalaLabel = "රැඳී සිටීම",
                englishLabel = "WAITING",
                value = formatDuration(waitingSec),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoCard(
                icon = Icons.Default.Speed,
                sinhalaLabel = "වේගය",
                englishLabel = "SPEED (km/h)",
                value = String.format(Locale.US, "%.1f", speedKmh),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            InfoCard(
                icon = Icons.Default.Timer,
                sinhalaLabel = "මුළු කාලය",
                englishLabel = "TOTAL TIME",
                value = formatDuration(totalSec),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun InfoCard(
    icon: ImageVector,
    sinhalaLabel: String,
    englishLabel: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
        border = BorderStroke(1.dp, Color(0xFF2C313D))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                color = Color(0xFF2A2E39),
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = englishLabel,
                        tint = TaxiYellow,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sinhalaLabel,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = englishLabel,
                        color = Color.Gray,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// --- Composable: Rates System Panel ---
@Composable
fun RatesSystemPanel(
    base: Double,
    subsequent: Double,
    waiting: Double,
    editable: Boolean,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
        border = BorderStroke(1.dp, Color(0xFF2C313D))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Fare Info",
                        tint = TaxiYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "නිල ගාස්තු ක්‍රමවේදය / FARE SYSTEM",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                if (editable) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Rates",
                            tint = TaxiYellow,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RateColumn(
                    sinhalaLabel = "පළමු කිලෝමීටරය",
                    englishLabel = "1st Kilometer",
                    amount = String.format(Locale.US, "LKR %.2f", base),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp)
                        .background(Color(0xFF2C313D))
                )
                RateColumn(
                    sinhalaLabel = "ඉන්පසු සෑම කි.මී.",
                    englishLabel = "Subsequent Km",
                    amount = String.format(Locale.US, "LKR %.2f", subsequent),
                    modifier = Modifier.weight(1.5f)
                )
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp)
                        .background(Color(0xFF2C313D))
                )
                RateColumn(
                    sinhalaLabel = "රැඳී සිටීම (විනාඩියක)",
                    englishLabel = "Waiting / Min",
                    amount = String.format(Locale.US, "LKR %.2f", waiting),
                    modifier = Modifier.weight(1.2f)
                )
            }
        }
    }
}

@Composable
fun RateColumn(
    sinhalaLabel: String,
    englishLabel: String,
    amount: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = sinhalaLabel,
            color = Color.LightGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = englishLabel,
            color = Color.Gray,
            fontSize = 8.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = amount,
            color = TaxiYellow,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}

// --- Composable: Control Actions ---
@Composable
fun ControlPanel(
    state: MeterState,
    isGpsTracking: Boolean,
    isPermissionGranted: Boolean,
    onStartTrip: () -> Unit,
    onEndTrip: () -> Unit,
    onGpsToggle: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (state == MeterState.READY) {
            Button(
                onClick = onStartTrip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ගමන අරඹන්න 🚖 / START TRIP",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (state == MeterState.RUNNING) {
            Button(
                onClick = onEndTrip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ගමන නවතන්න 🛑 / END TRIP",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// --- Composable: End Trip Confirmation Dialog ---
@Composable
fun EndTripConfirmDialog(
    totalFare: Double,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF2C313D))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(AccentRed.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = AccentRed,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ගමන අවසන් කරන්නද?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Are you sure you want to end this trip?",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ශුද්ධ මුදල / Total Fare",
                    color = MeterFareGreen.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format(Locale.US, "LKR %,.2f", totalFare),
                    color = MeterFareGreen,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C313D)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "නැත / No", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "ඔව් / Yes", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- Composable: Edit Rates Dialog ---
@Composable
fun EditRatesDialog(
    currentBase: Double,
    currentSubsequent: Double,
    currentWaiting: Double,
    onSave: (Double, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var baseText by remember { mutableStateOf(currentBase.toString()) }
    var subsequentText by remember { mutableStateOf(currentSubsequent.toString()) }
    var waitingText by remember { mutableStateOf(currentWaiting.toString()) }

    var hasError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF2C313D))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ගාස්තු සැකසුම් / Edit Rates",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = baseText,
                    onValueChange = { baseText = it },
                    label = { Text("පළමු කි.මී. ගාස්තුව / Base Fare (LKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TaxiYellow,
                        focusedLabelColor = TaxiYellow
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = subsequentText,
                    onValueChange = { subsequentText = it },
                    label = { Text("ඉන්පසු සෑම කි.මී. ගාස්තුව / Subsequent Km (LKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TaxiYellow,
                        focusedLabelColor = TaxiYellow
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = waitingText,
                    onValueChange = { waitingText = it },
                    label = { Text("රැඳී සිටීම විනාඩියකට / Waiting per Min (LKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TaxiYellow,
                        focusedLabelColor = TaxiYellow
                    )
                )

                if (hasError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "කරුණාකර නිවැරදි අගයන් ඇතුළත් කරන්න / Please enter valid numeric rates.",
                        color = AccentRed,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C313D)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "අවලංගු කරන්න",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Cancel",
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Normal,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val b = baseText.toDoubleOrNull()
                            val s = subsequentText.toDoubleOrNull()
                            val w = waitingText.toDoubleOrNull()

                            if (b != null && s != null && w != null && b > 0 && s > 0 && w >= 0) {
                                onSave(b, s, w)
                            } else {
                                hasError = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "සුරකින්න",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Save",
                                color = Color.Black.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Normal,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Composable: Receipt / Final Fare Screen ---
@Composable
fun ReceiptScreen(
    date: String,
    time: String,
    tripId: String,
    baseDistance: Double,
    subsequentDistance: Double,
    baseFare: Double,
    subsequentFare: Double,
    waitingMin: Double,
    waitingFare: Double,
    totalFare: Double,
    totalDist: Double,
    totalTimeStr: String,
    currentLanguage: TaxiMeterViewModel.Language,
    onReset: () -> Unit,
    onPrint: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, TaxiYellow), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(AccentGreen.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = AccentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column {
                    Text(
                        text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Trip Completed!" else "ගමන සාර්ථකව අවසන්!",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Receipt is ready to print" else "මුද්‍රණය කිරීමට සූදානම්",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MeterScreenBg),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF2C313D))
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "TOTAL FARE PAID" else "ගෙවිය යුතු මුළු මුදල / TOTAL FARE PAID",
                        color = MeterFareGreen.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(Locale.US, "LKR %,.2f", totalFare),
                        color = MeterFareGreen,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "INVOICE BREAKDOWN" else "ගෙවීම් විස්තරය / INVOICE BREAKDOWN",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(2.dp))

            ReceiptRow(
                sinhala = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Base Fare" else "පළමු 1.0 කි.මී. (Base)",
                english = "Base Distance Charge",
                amount = String.format(Locale.US, "LKR %.2f", baseFare)
            )
            ReceiptRow(
                sinhala = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) String.format(Locale.US, "Subsequent (%.2f km)", subsequentDistance) else String.format(Locale.US, "ඉන්පසු දුර (%.2f km)", subsequentDistance),
                english = "Subsequent Distance Charge",
                amount = String.format(Locale.US, "LKR %.2f", subsequentFare)
            )
            ReceiptRow(
                sinhala = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) String.format(Locale.US, "Waiting (%.1f min)", waitingMin) else String.format(Locale.US, "රැඳී සිටීම (%.1f min)", waitingMin),
                english = "Waiting Charge",
                amount = String.format(Locale.US, "LKR %.2f", waitingFare)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(1.dp)
                    .background(Color(0xFF2C313D))
            )

            ReceiptRow(
                sinhala = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Total Distance" else "මුළු දුර",
                english = "Total Distance",
                amount = String.format(Locale.US, "%.2f km", totalDist),
                isHighlight = true
            )
            ReceiptRow(
                sinhala = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Total Duration" else "මුළු ධාවන කාලය",
                english = "Total Duration",
                amount = totalTimeStr,
                isHighlight = true
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(1.dp)
                    .background(Color(0xFF2C313D))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Trip ID: #$tripId", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                Text(text = "Date: $date $time", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onReset,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "New", tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "New Trip" else "නව ගමනක්",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onPrint,
                    modifier = Modifier
                        .weight(0.9f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrintBlue),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = "Print", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Print" else "මුද්‍රණය",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptRow(
    sinhala: String,
    english: String,
    amount: String,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = sinhala,
                color = if (isHighlight) Color.White else Color.LightGray,
                fontSize = if (isHighlight) 11.sp else 10.sp,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = english,
                color = Color.Gray,
                fontSize = 8.sp
            )
        }

        Text(
            text = amount,
            color = if (isHighlight) TaxiYellow else Color.White,
            fontSize = if (isHighlight) 13.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// --- Composable: Trip History Section ---
@Composable
fun TripHistorySection(
    trips: List<TripEntity>,
    currentLanguage: TaxiMeterViewModel.Language,
    onDeleteTrip: (Int) -> Unit,
    onClearAll: () -> Unit,
    onPrintTrip: (TripEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
        border = BorderStroke(1.dp, Color(0xFF2C313D))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = TaxiYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Trip History" else "ධාවන ඉතිහාසය / Trip History",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trips.isNotEmpty()) {
                        IconButton(onClick = onClearAll) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear All",
                                tint = AccentRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle Expansion",
                            tint = Color.LightGray
                        )
                    }
                }
            }

            if (trips.isEmpty()) {
                AnimatedVisibility(visible = expanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "No saved trips yet." else "සුරකින ලද ගමන් නොමැත.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        trips.forEach { trip ->
                            TripHistoryRow(
                                trip = trip,
                                currentLanguage = currentLanguage,
                                onDelete = { onDeleteTrip(trip.id) },
                                onPrint = { onPrintTrip(trip) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripHistoryRow(
    trip: TripEntity,
    currentLanguage: TaxiMeterViewModel.Language,
    onDelete: () -> Unit,
    onPrint: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF20242F)),
        border = BorderStroke(1.dp, Color(0xFF2C313D))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ID: #${trip.tripId}",
                        color = TaxiYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${trip.date}  ${trip.time}",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }

                Text(
                    text = String.format(Locale.US, "LKR %,.2f", trip.totalFare),
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Dist: " else "දුර: ",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f km", trip.distanceKm),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Wait: " else "රැඳී සිටීම: ",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                    Text(
                        text = formatDuration(trip.waitingSeconds),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row {
                    IconButton(
                        onClick = onPrint,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print Trip",
                            tint = PrintBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Trip",
                            tint = AccentRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- Helper: Format duration in mm:ss or hh:mm:ss ---
fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

// --- Helper: Print System Integration ---
fun printReceipt(
    context: Context,
    date: String,
    time: String,
    tripId: String,
    baseFare: Double,
    subsequentKmRate: Double,
    subsequentDist: Double,
    subsequentFare: Double,
    waitingMin: Double,
    waitingFare: Double,
    totalFare: Double,
    totalDist: Double,
    totalTime: String
) {
    val htmlContent = """
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { font-family: 'Courier New', Courier, monospace; padding: 20px; color: #000; background-color: #fff; line-height: 1.4; }
          .center { text-align: center; }
          .bold { font-weight: bold; }
          .divider { border-top: 1.5px dashed #000; margin: 12px 0; }
          .row { display: flex; justify-content: space-between; margin: 5px 0; font-size: 14px; }
          .total { font-size: 1.4em; font-weight: bold; margin-top: 12px; }
          .header { font-size: 1.3em; letter-spacing: 1px; }
          .footer { font-size: 12px; color: #333; margin-top: 20px; }
        </style>
        </head>
        <body>
          <div class="center bold header">LANKA GPS TAXI METER</div>
          <div class="center">ශ්‍රී ලංකා ටැක්සි මීටර් සේවාව</div>
          <div class="center">Colombo, Sri Lanka</div>
          <div class="divider"></div>
          <div class="row"><span>Date:</span> <span>$date</span></div>
          <div class="row"><span>Time:</span> <span>$time</span></div>
          <div class="row"><span>Trip ID:</span> <span>#$tripId</span></div>
          <div class="divider"></div>
          <div class="row bold"><span>Item Description</span><span>Qty/Val</span><span>Amount</span></div>
          <div class="divider"></div>
          <div class="row"><span>Base Fare (1st Km)</span> <span>1.0 km</span> <span>LKR ${String.format(Locale.US, "%.2f", baseFare)}</span></div>
          <div class="row"><span>Subsequent Km</span> <span>${String.format(Locale.US, "%.2f", subsequentDist)} km</span> <span>LKR ${String.format(Locale.US, "%.2f", subsequentFare)}</span></div>
          <div class="row"><span>Waiting Charge</span> <span>${String.format(Locale.US, "%.1f", waitingMin)} min</span> <span>LKR ${String.format(Locale.US, "%.2f", waitingFare)}</span></div>
          <div class="divider"></div>
          <div class="row bold total"><span>TOTAL FARE</span> <span>LKR ${String.format(Locale.US, "%.2f", totalFare)}</span></div>
          <div class="divider"></div>
          <div class="row"><span>Total Distance:</span> <span>${String.format(Locale.US, "%.2f", totalDist)} km</span></div>
          <div class="row"><span>Total Duration:</span> <span>$totalTime</span></div>
          <div class="divider"></div>
          <div class="center bold footer" style="margin-top: 25px;">THANK YOU FOR RIDING WITH US!</div>
          <div class="center bold footer">සුභ ගමන්! (Safe Journey!)</div>
        </body>
        </html>
    """.trimIndent()

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        @Deprecated("Deprecated in Java")
        override fun onPageFinished(view: WebView, url: String) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = webView.createPrintDocumentAdapter("Lanka_Taxi_Receipt_$tripId")
            val jobName = "Lanka Taxi Meter Receipt $tripId"
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
}

@Composable
fun AdMobBanner(
    adUnitId: String,
    mockTitle: String,
    mockSub: String,
    modifier: Modifier = Modifier
) {
    var isAdLoaded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color(0xFF11141B))
            .border(BorderStroke(1.dp, Color(0xFF2C313D)), RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isAdLoaded) 50.dp else 1.dp),
            factory = { context ->
                try {
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        this.adUnitId = adUnitId
                        adListener = object : com.google.android.gms.ads.AdListener() {
                            override fun onAdLoaded() {
                                isAdLoaded = true
                            }
                            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                                isAdLoaded = false
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    // Return a dummy safe View if AdMob library crashes on devices without play services
                    android.view.View(context)
                }
            },
            update = { adView -> }
        )

        if (!isAdLoaded) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(TaxiYellow, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "AD",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mockTitle,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mockSub,
                        color = Color.LightGray,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { /* Simulated Click */ },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = "BUY NOW",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// --- Helper: Detect if App is Running on Emulator/Preview ---
fun isEmulator(): Boolean {
    return try {
        val buildModel = android.os.Build.MODEL
        val buildProduct = android.os.Build.PRODUCT
        val buildFingerprint = android.os.Build.FINGERPRINT
        val buildHardware = android.os.Build.HARDWARE
        val buildBrand = android.os.Build.BRAND
        val buildDevice = android.os.Build.DEVICE
        
        buildModel.contains("google_sdk") ||
                buildModel.contains("Emulator") ||
                buildModel.contains("Android SDK built for x86") ||
                buildProduct.contains("sdk") ||
                buildProduct.contains("google_sdk") ||
                buildHardware.contains("goldfish") ||
                buildHardware.contains("ranchu") ||
                buildBrand.startsWith("generic") ||
                buildDevice.startsWith("generic") ||
                buildFingerprint.startsWith("generic") ||
                buildFingerprint.startsWith("unknown")
    } catch (e: Exception) {
        false
    }
}

fun getBannerAdId(): String {
    return if (isEmulator()) {
        "ca-app-pub-3940256099942544/6300978111" // AdMob Test Banner ID for Emulator/Preview
    } else {
        "ca-app-pub-7472113156561687/3463243021" // Real Banner ID for physical phones
    }
}

fun getInterstitialAdId(): String {
    return if (isEmulator()) {
        "ca-app-pub-3940256099942544/1033173712" // AdMob Test Interstitial ID for Emulator/Preview
    } else {
        "ca-app-pub-7472113156561687/5392791426" // Real Interstitial ID for physical phones
    }
}

// --- Helper Extensions & AdMob Manager for Interstitial Ads ---
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

object AdMobManager {
    private var mInterstitialAd: InterstitialAd? = null
    private var isAdLoading = false

    fun loadInterstitialAd(context: Context) {
        if (mInterstitialAd != null || isAdLoading) return
        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        try {
            InterstitialAd.load(
                context.applicationContext,
                getInterstitialAdId(),
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        mInterstitialAd = null
                        isAdLoading = false
                    }

                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        mInterstitialAd = interstitialAd
                        isAdLoading = false
                    }
                }
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            isAdLoading = false
        }
    }

    fun showInterstitialAd(activity: Activity, onShowMock: () -> Unit, onAdDismissed: () -> Unit = {}) {
        val ad = mInterstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mInterstitialAd = null
                    loadInterstitialAd(activity)
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    mInterstitialAd = null
                    loadInterstitialAd(activity)
                    onAdDismissed()
                }
            }
            ad.show(activity)
        } else {
            loadInterstitialAd(activity)
            onShowMock()
        }
    }
}

// --- Composable: Rates and History Dialog ---
@Composable
fun RatesAndHistoryDialog(
    baseFare: Double,
    subsequentKmFare: Double,
    waitingFarePerMin: Double,
    editable: Boolean,
    onEditClick: () -> Unit,
    trips: List<TripEntity>,
    currentLanguage: TaxiMeterViewModel.Language,
    onDeleteTrip: (Int) -> Unit,
    onClearAll: () -> Unit,
    onPrintTrip: (TripEntity) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp), // Safe area inset feel for top notch
            color = ThemeDarkBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Elegant Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "Rates & Trip History" else "ගාස්තු සහ ධාවන ඉතිහාසය",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (currentLanguage == TaxiMeterViewModel.Language.ENGLISH) "View official fares and past journey logs" else "ගාස්තු සැකසුම් සහ පෙර ධාවන වාර්තා",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color(0xFF1E222B), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Divider(color = Color(0xFF2C313D), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content wrapper
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. Rates Panel & Settings
                    RatesSystemPanel(
                        base = baseFare,
                        subsequent = subsequentKmFare,
                        waiting = waitingFarePerMin,
                        editable = editable,
                        onEditClick = onEditClick
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 2. Trip History Section
                    TripHistorySection(
                        trips = trips,
                        currentLanguage = currentLanguage,
                        onDeleteTrip = onDeleteTrip,
                        onClearAll = onClearAll,
                        onPrintTrip = onPrintTrip
                    )

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}

