package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color as GColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.LankaTaxiMeterTheme
import com.example.ui.theme.MeterFareGreen
import com.example.ui.theme.PrintBlue
import com.example.ui.theme.TaxiYellow
import com.example.ui.theme.TaxiYellowDim
import com.example.ui.theme.ThemeCardBg
import com.example.ui.theme.ThemeDarkBg
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: TaxiMeterViewModel by viewModels()
    private var locationManager: LocationManager? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            viewModel.handleGpsLocationUpdate(location)
        }
        override fun onProviderEnabled(provider: String) {
            viewModel.setGpsSettingsEnabled(true)
        }
        override fun onProviderDisabled(provider: String) {
            viewModel.setGpsSettingsEnabled(false)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize AdMob
        AdMobManager.initialize(this)
        AdMobManager.loadInterstitial(this)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            LankaTaxiMeterTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                        val granted = fineGranted || coarseGranted
                        viewModel.updatePermissionState(granted)
                        if (granted) {
                            startLocationUpdates()
                        } else {
                            Toast.makeText(this, "GPS location permission is required for meter tracking.", Toast.LENGTH_LONG).show()
                        }
                    }

                    LaunchedEffect(Unit) {
                        val hasFine = ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        viewModel.updatePermissionState(hasFine || hasCoarse)

                        if (hasFine || hasCoarse) {
                            startLocationUpdates()
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }

                    TaxiMeterApp(viewModel)
                }
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            locationManager?.let { mgr ->
                val isGpsEnabled = mgr.isProviderEnabled(LocationManager.GPS_PROVIDER)
                viewModel.setGpsSettingsEnabled(isGpsEnabled)

                if (isGpsEnabled) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    ) {
                        mgr.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            1000L, // 1 second interval
                            1.0f,  // 1 meter minimum distance
                            locationListener
                        )
                        viewModel.setGpsTrackingEnabled(true)
                    }
                } else {
                    Toast.makeText(this, "Please enable GPS/Location settings for tracking.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting location updates: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error removing updates: ${e.message}")
        }
    }
}

// --- Localized String Resource Map (In-Code Localization for Simplicity) ---

fun getLocalizedText(key: String, language: TaxiMeterViewModel.Language): String {
    return when (language) {
        TaxiMeterViewModel.Language.ENGLISH -> when (key) {
            "title" -> "LANKA TAXI METER"
            "tuk" -> "Three Wheel"
            "car" -> "Car"
            "van" -> "Van"
            "ready" -> "READY TO START"
            "running" -> "TRIP IN PROGRESS"
            "paused" -> "METER PAUSED"
            "confirm_end" -> "END TRIP?"
            "receipt" -> "TRIP RECEIPT"
            "total_fare" -> "TOTAL FARE"
            "distance" -> "Distance"
            "waiting" -> "Waiting Time"
            "speed" -> "Speed"
            "gps_signal" -> "GPS Signal"
            "gps_ok" -> "ACTIVE"
            "gps_no" -> "NO SIGNAL"
            "surcharge" -> "Night Surcharge (+20%)"
            "edit_rates" -> "Edit Rates"
            "trip_history" -> "Trip History"
            "settings" -> "Settings"
            "start" -> "START"
            "pause" -> "PAUSE"
            "resume" -> "RESUME"
            "end" -> "END"
            "reset" -> "RESET"
            "confirm" -> "CONFIRM"
            "cancel" -> "CANCEL"
            "done" -> "DONE"
            "rate_base" -> "Base Fare (LKR)"
            "rate_sub" -> "Subsequent Km Fare (LKR)"
            "rate_wait" -> "Waiting Fare per Min (LKR)"
            "dark_mode" -> "Dark Dashboard UI"
            "sounds" -> "Meter Sound Effects"
            "voice" -> "Voice Announcements"
            "no_history" -> "No completed trips yet"
            "print" -> "Print Receipt"
            "share" -> "Share Receipt"
            "pdf_export" -> "Export History to PDF"
            "export_success" -> "Trip logs exported to PDF successfully"
            else -> key
        }
        TaxiMeterViewModel.Language.SINHALA -> when (key) {
            "title" -> "ලංකා ටැක්සි මීටරය"
            "tuk" -> "ත්‍රීවීල්"
            "car" -> "කාර්"
            "van" -> "වෑන්"
            "ready" -> "ආරම්භ කිරීමට සූදානම්"
            "running" -> "ධාවනය වෙමින් පවතී"
            "paused" -> "මීටරය නතර කර ඇත"
            "confirm_end" -> "ගමන අවසන් කරන්නද?"
            "receipt" -> "ගමන් රිසිට්පත"
            "total_fare" -> "මුළු ගාස්තුව"
            "distance" -> "දුර ප්‍රමාණය"
            "waiting" -> "රඳවා ගැනීමේ කාලය"
            "speed" -> "වේගය"
            "gps_signal" -> "GPS සංඥා"
            "gps_ok" -> "සක්‍රීයයි"
            "gps_no" -> "සංඥා නැත"
            "surcharge" -> "රාත්‍රී අමතර ගාස්තුව (+20%)"
            "edit_rates" -> "ගාස්තු සංශෝධනය"
            "trip_history" -> "ගමන් වාර්තා"
            "settings" -> "සැකසුම්"
            "start" -> "ආරම්භ කරන්න"
            "pause" -> "නතර කරන්න"
            "resume" -> "නැවත අරඹන්න"
            "end" -> "අවසන් කරන්න"
            "reset" -> "යළි පිහිටුවන්න"
            "confirm" -> "තහවුරු කරන්න"
            "cancel" -> "අවලංගු කරන්න"
            "done" -> "අවසන්"
            "rate_base" -> "මූලික ගාස්තුව (LKR)"
            "rate_sub" -> "පසු කි.මී. ගාස්තුව (LKR)"
            "rate_wait" -> "විනාඩියකට රඳවා ගැනීමේ ගාස්තුව (LKR)"
            "dark_mode" -> "අඳුරු මීටර් පුවරුව"
            "sounds" -> "මීටර් ශබ්ද ප්‍රයෝග"
            "voice" -> "කටහඬ නිවේදන"
            "no_history" -> "තවම ගමන් වාර්තා නොමැත"
            "print" -> "මුද්‍රණය කරන්න"
            "share" -> "හුවමාරු කරන්න"
            "pdf_export" -> "ගමන් වාර්තා PDF කරන්න"
            "export_success" -> "වාර්තා සාර්ථකව PDF පත්‍රිකාවකට අපනයනය කෙරිණි"
            else -> key
        }
        TaxiMeterViewModel.Language.TAMIL -> when (key) {
            "title" -> "லங்கா டாக்ஸி மீட்டர்"
            "tuk" -> "த்ரீ வீலர்"
            "car" -> "கார்"
            "van" -> "வேன்"
            "ready" -> "தொடங்குவதற்கு தயார்"
            "running" -> "பயணம் தொடர்கிறது"
            "paused" -> "மீட்டர் நிறுத்தப்பட்டுள்ளது"
            "confirm_end" -> "பயணத்தை முடிக்கவா?"
            "receipt" -> "பயண ரசீது"
            "total_fare" -> "மொத்த கட்டணம்"
            "distance" -> "தூர அளவு"
            "waiting" -> "காத்திருப்பு நேரம்"
            "speed" -> "வேகம்"
            "gps_signal" -> "GPS சமிக்ஞை"
            "gps_ok" -> "செயலில் உள்ளது"
            "gps_no" -> "சமிக்ஞை இல்லை"
            "surcharge" -> "இரவு கூடுதல் கட்டணம் (+20%)"
            "edit_rates" -> "கட்டண திருத்தம்"
            "trip_history" -> "பயண வரலாறு"
            "settings" -> "அமைப்புகள்"
            "start" -> "தொடங்கு"
            "pause" -> "நிறுத்து"
            "resume" -> "மீண்டும் தொடங்கு"
            "end" -> "முடி"
            "reset" -> "மீட்டமை"
            "confirm" -> "உறுதி செய்"
            "cancel" -> "ரத்து செய்"
            "done" -> "முடிந்தது"
            "rate_base" -> "அடிப்படை கட்டணம் (LKR)"
            "rate_sub" -> "அடுத்தடுத்த கி.மீ. கட்டணம் (LKR)"
            "rate_wait" -> "நிமிட காத்திருப்பு கட்டணம் (LKR)"
            "dark_mode" -> "இருண்ட மீட்டர் பலகை"
            "sounds" -> "மீட்டர் ஒலி விளைவுகள்"
            "voice" -> "குரல் அறிவிப்புகள்"
            "no_history" -> "பயண பதிவுகள் எதுவும் இல்லை"
            "print" -> "ரசீது அச்சிடு"
            "share" -> "பகிர்ந்து கொள்"
            "pdf_export" -> "வரலாற்றை PDF ஆக ஏற்று"
            "export_success" -> "பதிவுகள் வெற்றிகரமாக PDF ஆக மாற்றப்பட்டது"
            else -> key
        }
    }
}

// --- Compose AdMob Banner Wrapper ---

@Composable
fun AdMobBanner(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdMobManager.getBannerAdId(context)
                loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
            }
        }
    )
}

// --- Helper function to format duration ---

fun formatDuration(seconds: Int): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

// --- Composable: Top-level App Layout ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxiMeterApp(viewModel: TaxiMeterViewModel) {
    val context = LocalContext.current
    val language by viewModel.currentLanguage.collectAsState()
    val state by viewModel.meterState.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showEditRatesDialog by remember { mutableStateOf(false) }
    var showRatesAndHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            AdMobBanner(modifier = Modifier.background(MaterialTheme.colorScheme.background))
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section
            HeaderSection(
                viewModel = viewModel,
                onSettingsClicked = { showSettingsDialog = true },
                onHistoryClicked = { showRatesAndHistoryDialog = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Body Area (Swaps based on MeterState)
            AnimatedVisibility(
                visible = state != MeterState.RECEIPT,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MeterDisplayCard(viewModel)
                    Spacer(modifier = Modifier.height(16.dp))
                    ControlPanel(viewModel, onEndTripTransition = {
                        // Show Interstitial ad before showing receipt screen
                        AdMobManager.showInterstitial(context as Activity) {
                            viewModel.confirmEndTrip()
                        }
                    })
                }
            }

            AnimatedVisibility(
                visible = state == MeterState.RECEIPT,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)),
                modifier = Modifier.weight(1f)
            ) {
                ReceiptScreen(viewModel)
            }

            // Quick rates indicator at the bottom
            RatesSystemPanel(viewModel, onEditRatesClicked = { showEditRatesDialog = true })
        }
    }

    // Dialogs
    if (showSettingsDialog) {
        SettingsDialog(viewModel = viewModel, onDismiss = { showSettingsDialog = false })
    }

    if (showEditRatesDialog) {
        EditRatesDialog(viewModel = viewModel, onDismiss = { showEditRatesDialog = false })
    }

    if (showRatesAndHistoryDialog) {
        Dialog(onDismissRequest = { showRatesAndHistoryDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 550.dp)
            ) {
                TripHistorySection(viewModel = viewModel, onDismiss = { showRatesAndHistoryDialog = false })
            }
        }
    }
}

// --- Composable UI Components ---

@Composable
fun HeaderSection(
    viewModel: TaxiMeterViewModel,
    onSettingsClicked: () -> Unit,
    onHistoryClicked: () -> Unit
) {
    val language by viewModel.currentLanguage.collectAsState()
    val isGpsActive by viewModel.isGpsActiveAndSignal.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Branding
        Column {
            Text(
                text = getLocalizedText("title", language),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TaxiYellow,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            // GPS signal status
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isGpsActive) AccentGreen else AccentRed)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = getLocalizedText("gps_signal", language) + ": " +
                            getLocalizedText(if (isGpsActive) "gps_ok" else "gps_no", language),
                    fontSize = 11.sp,
                    color = if (isGpsActive) AccentGreen else Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Action Buttons
        Row {
            IconButton(
                onClick = onHistoryClicked,
                modifier = Modifier.testTag("history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Trip History",
                    tint = Color.White
                )
            }
            IconButton(
                onClick = onSettingsClicked,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun MeterDisplayCard(viewModel: TaxiMeterViewModel) {
    val distance by viewModel.totalDistanceKm.collectAsState()
    val fare by viewModel.totalFare.collectAsState()
    val elapsedSecs by viewModel.elapsedSeconds.collectAsState()
    val waitingSecs by viewModel.waitingSeconds.collectAsState()
    val speed by viewModel.currentSpeedKmh.collectAsState()
    val state by viewModel.meterState.collectAsState()
    val language by viewModel.currentLanguage.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .border(2.dp, TaxiYellow, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBg)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status overlay
            val stateTextKey = when (state) {
                MeterState.READY -> "ready"
                MeterState.RUNNING -> "running"
                MeterState.PAUSED -> "paused"
                MeterState.CONFIRM_END -> "confirm_end"
                else -> "ready"
            }
            val stateColor = when (state) {
                MeterState.RUNNING -> MeterFareGreen
                MeterState.PAUSED -> TaxiYellow
                MeterState.CONFIRM_END -> AccentRed
                else -> Color.Gray
            }

            Text(
                text = getLocalizedText(stateTextKey, language).uppercase(),
                color = stateColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier
                    .background(stateColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Main Fare Display
            Text(
                text = getLocalizedText("total_fare", language).uppercase(),
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            
            Text(
                text = String.format(Locale.US, "LKR %.2f", fare),
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MeterFareGreen,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

            // Stats grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Distance column
                InfoCard(
                    title = getLocalizedText("distance", language),
                    value = String.format(Locale.US, "%.2f km", distance),
                    icon = Icons.Default.LocationOn,
                    tint = TaxiYellow
                )

                // Duration column
                InfoCard(
                    title = getLocalizedText("waiting", language),
                    value = formatDuration(waitingSecs),
                    icon = Icons.Default.Refresh,
                    tint = Color.Cyan
                )

                // Speed column
                InfoCard(
                    title = getLocalizedText("speed", language),
                    value = String.format(Locale.US, "%.1f km/h", speed),
                    icon = Icons.Default.PlayArrow,
                    tint = AccentGreen
                )
            }
        }
    }
}

@Composable
fun InfoCard(title: String, value: String, icon: ImageVector, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = title, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = title, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Normal)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun ControlPanel(
    viewModel: TaxiMeterViewModel,
    onEndTripTransition: () -> Unit
) {
    val state by viewModel.meterState.collectAsState()
    val language by viewModel.currentLanguage.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        when (state) {
            MeterState.READY -> {
                Button(
                    onClick = { viewModel.startTrip() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_button")
                ) {
                    Text(text = getLocalizedText("start", language), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            MeterState.RUNNING -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = { viewModel.pauseTrip() },
                        colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("pause_button")
                    ) {
                        Text(text = getLocalizedText("pause", language), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { viewModel.requestEndTrip() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("end_button")
                    ) {
                        Text(text = getLocalizedText("end", language), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            MeterState.PAUSED -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = { viewModel.resumeTrip() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("resume_button")
                    ) {
                        Text(text = getLocalizedText("resume", language), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { viewModel.requestEndTrip() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("end_button")
                    ) {
                        Text(text = getLocalizedText("end", language), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            MeterState.CONFIRM_END -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = { viewModel.cancelEndTrip() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("cancel_end_button")
                    ) {
                        Text(text = getLocalizedText("cancel", language), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = onEndTripTransition,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("confirm_end_button")
                    ) {
                        Text(text = getLocalizedText("confirm", language), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
fun RatesSystemPanel(
    viewModel: TaxiMeterViewModel,
    onEditRatesClicked: () -> Unit
) {
    val vehicleType by viewModel.vehicleType.collectAsState()
    val baseFare by viewModel.baseFare.collectAsState()
    val subsequentFare by viewModel.subsequentKmFare.collectAsState()
    val waitFare by viewModel.waitingFarePerMin.collectAsState()
    val language by viewModel.currentLanguage.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBg.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rate description
                Text(
                    text = "${getLocalizedText("tuk", language)}: LKR $baseFare / LKR $subsequentFare / LKR $waitFare",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                
                // Edit button
                IconButton(
                    onClick = onEditRatesClicked,
                    modifier = Modifier.size(24.dp).testTag("edit_rates_button")
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
    }
}

// --- Composable: Trip History Dialog Section ---

@Composable
fun TripHistorySection(viewModel: TaxiMeterViewModel, onDismiss: () -> Unit) {
    val history by viewModel.tripHistory.collectAsState(initial = emptyList())
    val language by viewModel.currentLanguage.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getLocalizedText("trip_history", language),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TaxiYellow
            )
            TextButton(onClick = onDismiss) {
                Text(text = getLocalizedText("done", language), color = TaxiYellow)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getLocalizedText("no_history", language),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { trip ->
                    TripHistoryRow(trip = trip, onDelete = { viewModel.deleteTrip(trip.id) })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Export to PDF button
            Button(
                onClick = {
                    exportTripHistoryToPdf(context, history)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_pdf_button"),
                colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "PDF")
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = getLocalizedText("pdf_export", language), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TripHistoryRow(trip: TripEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = trip.tripId,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TaxiYellow,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${trip.date} | ${trip.time}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Text(
                    text = String.format(Locale.US, "%.2f km | Waiting: %s", trip.distanceKm, formatDuration(trip.waitingSeconds)),
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(Locale.US, "LKR %.2f", trip.totalFare),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = MeterFareGreen,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = AccentRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// --- Composable: Receipt Screen ---

@Composable
fun ReceiptScreen(viewModel: TaxiMeterViewModel) {
    val language by viewModel.currentLanguage.collectAsState()
    val tripId by viewModel.receiptTripId.collectAsState()
    val date by viewModel.receiptDate.collectAsState()
    val time by viewModel.receiptTime.collectAsState()
    val distance by viewModel.totalDistanceKm.collectAsState()
    val waitingSecs by viewModel.waitingSeconds.collectAsState()
    val baseFare by viewModel.receiptBaseFarePaid.collectAsState()
    val subDistance by viewModel.receiptSubsequentDistance.collectAsState()
    val subFare by viewModel.receiptSubsequentFarePaid.collectAsState()
    val waitFare by viewModel.receiptWaitingFarePaid.collectAsState()
    val totalFare by viewModel.receiptTotalFarePaid.collectAsState()
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = Icons.Default.Print, contentDescription = "Receipt Icon", tint = TaxiYellow, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = getLocalizedText("receipt", language).uppercase(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TaxiYellow,
                letterSpacing = 1.sp
            )

            Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

            // Meta row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "ID: $tripId", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                Text(text = "$date $time", fontSize = 11.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bill Breakdown
            ReceiptRow(label = "Base Fare (1.0 km)", value = String.format(Locale.US, "LKR %.2f", baseFare))
            ReceiptRow(label = String.format(Locale.US, "Subsequent Dist (%.2f km)", subDistance), value = String.format(Locale.US, "LKR %.2f", subFare))
            ReceiptRow(label = "Waiting Time (${waitingSecs / 60} min)", value = String.format(Locale.US, "LKR %.2f", waitFare))

            Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

            // Total row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "TOTAL PAID", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                Text(
                    text = String.format(Locale.US, "LKR %.2f", totalFare),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MeterFareGreen,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Print & Share actions
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        viewModel.resetMeter()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("receipt_done_button"),
                    border = borderStrokeForButton(Color.Gray),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = getLocalizedText("done", language), color = Color.White)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        printReceipt(context, tripId, date, time, baseFare, subDistance, subFare, waitingSecs, waitFare, totalFare)
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("receipt_print_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PrintBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = "Print")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = getLocalizedText("print", language), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = Color.LightGray)
        Text(text = value, fontSize = 13.sp, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}

fun borderStrokeForButton(color: Color) = androidx.compose.foundation.BorderStroke(1.dp, color)

// --- Composable: Settings Dialog ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: TaxiMeterViewModel,
    onDismiss: () -> Unit
) {
    val language by viewModel.currentLanguage.collectAsState()
    val isNightSurcharge by viewModel.isNightSurchargeEnabled.collectAsState()
    val isSoundEffects by viewModel.isSoundEffectsEnabled.collectAsState()
    val isVoiceAnnouncement by viewModel.isVoiceAnnouncementEnabled.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val vehicleType by viewModel.vehicleType.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = getLocalizedText("settings", language),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TaxiYellow,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Language selector
                Text(text = "Language / භාෂාව / மொழி", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(
                        TaxiMeterViewModel.Language.ENGLISH to "ENG",
                        TaxiMeterViewModel.Language.SINHALA to "සිංහල",
                        TaxiMeterViewModel.Language.TAMIL to "தமிழ்"
                    ).forEach { (lang, label) ->
                        OutlinedButton(
                            onClick = { viewModel.setLanguage(lang) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (language == lang) TaxiYellowDim else Color.Transparent
                            ),
                            border = borderStrokeForButton(if (language == lang) TaxiYellow else Color.Gray)
                        ) {
                            Text(text = label, color = if (language == lang) TaxiYellow else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Vehicle selection
                Text(text = "Vehicle Type", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(
                        TaxiMeterViewModel.VehicleType.THREE_WHEEL to "Tuk",
                        TaxiMeterViewModel.VehicleType.CAR to "Car",
                        TaxiMeterViewModel.VehicleType.VAN to "Van"
                    ).forEach { (vType, label) ->
                        OutlinedButton(
                            onClick = { viewModel.selectVehicleType(vType) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (vehicleType == vType) TaxiYellowDim else Color.Transparent
                            ),
                            border = borderStrokeForButton(if (vehicleType == vType) TaxiYellow else Color.Gray)
                        ) {
                            Text(text = label, color = if (vehicleType == vType) TaxiYellow else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Switch Rows
                SettingsSwitchRow(
                    label = getLocalizedText("surcharge", language),
                    checked = isNightSurcharge,
                    onCheckedChange = { viewModel.setNightSurchargeEnabled(it) }
                )

                SettingsSwitchRow(
                    label = getLocalizedText("sounds", language),
                    checked = isSoundEffects,
                    onCheckedChange = { viewModel.setSoundEffectsEnabled(it) }
                )

                SettingsSwitchRow(
                    label = getLocalizedText("voice", language),
                    checked = isVoiceAnnouncement,
                    onCheckedChange = { viewModel.setVoiceAnnouncementEnabled(it) }
                )

                SettingsSwitchRow(
                    label = getLocalizedText("dark_mode", language),
                    checked = isDarkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().testTag("close_settings_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = getLocalizedText("done", language), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, color = Color.White)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TaxiYellow,
                checkedTrackColor = TaxiYellowDim
            )
        )
    }
}

// --- Composable: Edit Rates Dialog ---

@Composable
fun EditRatesDialog(
    viewModel: TaxiMeterViewModel,
    onDismiss: () -> Unit
) {
    val baseFare by viewModel.baseFare.collectAsState()
    val subsequentFare by viewModel.subsequentKmFare.collectAsState()
    val waitingFare by viewModel.waitingFarePerMin.collectAsState()
    val language by viewModel.currentLanguage.collectAsState()

    var baseText by remember { mutableStateOf(baseFare.toString()) }
    var subText by remember { mutableStateOf(subsequentFare.toString()) }
    var waitText by remember { mutableStateOf(waitingFare.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth()
            ) {
                Text(
                    text = getLocalizedText("edit_rates", language),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TaxiYellow,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = baseText,
                    onValueChange = { baseText = it },
                    label = { Text(getLocalizedText("rate_base", language)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("rate_base_input")
                )

                OutlinedTextField(
                    value = subText,
                    onValueChange = { subText = it },
                    label = { Text(getLocalizedText("rate_sub", language)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("rate_sub_input")
                )

                OutlinedTextField(
                    value = waitText,
                    onValueChange = { waitText = it },
                    label = { Text(getLocalizedText("rate_wait", language)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("rate_wait_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = getLocalizedText("cancel", language), color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val b = baseText.toDoubleOrNull() ?: baseFare
                            val s = subText.toDoubleOrNull() ?: subsequentFare
                            val w = waitText.toDoubleOrNull() ?: waitingFare
                            viewModel.updateRates(b, s, w)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("save_rates_button")
                    ) {
                        Text(text = getLocalizedText("confirm", language), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- Printing Integration using Android PrintManager ---

fun printReceipt(
    context: Context,
    tripId: String,
    date: String,
    time: String,
    baseFare: Double,
    subDistance: Double,
    subFare: Double,
    waitingSecs: Int,
    waitFare: Double,
    totalFare: Double
) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "LankaTaxiMeter_Receipt_$tripId"

        // Generate clean receipt HTML string
        val htmlContent = """
            <html>
            <head>
                <style>
                    body { font-family: 'Courier New', Courier, monospace; padding: 10px; color: #000; }
                    .header { text-align: center; margin-bottom: 15px; }
                    .title { font-size: 18px; font-weight: bold; }
                    .divider { border-top: 1px dashed #000; margin: 10px 0; }
                    .row { display: flex; justify-content: space-between; font-size: 13px; margin: 3px 0; }
                    .total { font-size: 16px; font-weight: bold; margin-top: 10px; }
                    .footer { text-align: center; margin-top: 20px; font-size: 11px; }
                </style>
            </head>
            <body>
                <div class="header">
                    <div class="title">LANKA TAXI METER</div>
                    <div>Safe & Honest Rides</div>
                    <div>Tel: +94 (GPS Meter)</div>
                </div>
                <div class="divider"></div>
                <div class="row"><span>TRIP ID:</span> <span>$tripId</span></div>
                <div class="row"><span>DATE:</span> <span>$date</span></div>
                <div class="row"><span>TIME:</span> <span>$time</span></div>
                <div class="divider"></div>
                <div class="row"><span>Base Fare (1.0 km)</span> <span>LKR ${String.format(Locale.US, "%.2f", baseFare)}</span></div>
                <div class="row"><span>Subsequent (${String.format(Locale.US, "%.2f", subDistance)} km)</span> <span>LKR ${String.format(Locale.US, "%.2f", subFare)}</span></div>
                <div class="row"><span>Waiting (${waitingSecs / 60} min)</span> <span>LKR ${String.format(Locale.US, "%.2f", waitFare)}</span></div>
                <div class="divider"></div>
                <div class="row total"><span>TOTAL FARE</span> <span>LKR ${String.format(Locale.US, "%.2f", totalFare)}</span></div>
                <div class="divider"></div>
                <div class="footer">
                    <div>Thank you for riding with us!</div>
                    <div>Powered by AI Studio Build</div>
                </div>
            </body>
            </html>
        """.trimIndent()

        // Create a temporary WebView to load HTML and print
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)

    } catch (e: Exception) {
        Toast.makeText(context, "Error printing receipt: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// --- Local PDF History Export using Android PdfDocument ---

fun exportTripHistoryToPdf(context: Context, trips: List<TripEntity>) {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size: 595 x 842 points
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()
        paint.color = GColor.BLACK
        paint.textSize = 18f
        paint.isFakeBoldText = true

        // Draw title
        canvas.drawText("LANKA TAXI METER - TRIP HISTORY LOGS", 50f, 60f, paint)

        // Draw subtitle
        paint.textSize = 10f
        paint.isFakeBoldText = false
        paint.color = GColor.DKGRAY
        canvas.drawText("Generated on local Android device via PDF Engine", 50f, 80f, paint)

        // Draw table header
        paint.textSize = 11f
        paint.isFakeBoldText = true
        paint.color = GColor.BLACK
        var yPos = 120f
        canvas.drawText("TRIP ID", 50f, yPos, paint)
        canvas.drawText("DATE & TIME", 160f, yPos, paint)
        canvas.drawText("DISTANCE", 300f, yPos, paint)
        canvas.drawText("WAITING", 400f, yPos, paint)
        canvas.drawText("FARE (LKR)", 480f, yPos, paint)

        // Draw header line
        paint.strokeWidth = 1.5f
        canvas.drawLine(50f, yPos + 6f, 550f, yPos + 6f, paint)
        yPos += 24f

        // Draw rows
        paint.isFakeBoldText = false
        trips.forEach { trip ->
            if (yPos > 800) return@forEach // Basic multi-page protection placeholder
            canvas.drawText(trip.tripId, 50f, yPos, paint)
            canvas.drawText("${trip.date} ${trip.time}", 160f, yPos, paint)
            canvas.drawText(String.format(Locale.US, "%.2f km", trip.distanceKm), 300f, yPos, paint)
            canvas.drawText(formatDuration(trip.waitingSeconds), 400f, yPos, paint)
            canvas.drawText(String.format(Locale.US, "%.2f", trip.totalFare), 480f, yPos, paint)
            yPos += 20f
        }

        // Draw total summary
        val grandTotal = trips.sumOf { it.totalFare }
        yPos += 15f
        paint.isFakeBoldText = true
        canvas.drawLine(50f, yPos - 10f, 550f, yPos - 10f, paint)
        canvas.drawText("TOTAL EARNINGS:", 320f, yPos, paint)
        canvas.drawText(String.format(Locale.US, "LKR %.2f", grandTotal), 480f, yPos, paint)

        pdfDocument.finishPage(page)

        // Save file to external cache
        val file = File(context.cacheDir, "TaxiMeter_History_Logs.pdf")
        val fileOutputStream = FileOutputStream(file)
        pdfDocument.writeTo(fileOutputStream)
        pdfDocument.close()
        fileOutputStream.close()

        // Share the PDF
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Trip History Logs via"))

    } catch (e: Exception) {
        Toast.makeText(context, "Error exporting history: ${e.message}", Toast.LENGTH_LONG).show()
        Log.e("MainActivity", "PDF Export failed: ${e.message}")
    }
}
