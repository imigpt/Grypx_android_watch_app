/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.grpyx_andd_watch.presentation

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.grpyx_andd_watch.network.WatchApiClient
import com.example.grpyx_andd_watch.network.SessionExpiredException
import com.example.grpyx_andd_watch.network.SessionNotFoundException
import com.example.grpyx_andd_watch.utils.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.grpyx_andd_watch.presentation.theme.Grpyx_andd_watchTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.UUID
import com.example.grpyx_andd_watch.network.WatchMatchService
import com.example.grpyx_andd_watch.network.ActiveMatchResponse

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            MainScreen()
        }
    }
}

// Authentication state enum
enum class AuthState {
    CHECKING,
    NOT_AUTHENTICATED,
    AUTHENTICATED
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val secureStorage = remember { SecureStorage(context) }
    var authState by remember { mutableStateOf(AuthState.CHECKING) }
    var userName by remember { mutableStateOf("") }

    // Check if user is authenticated
    LaunchedEffect(Unit) {
        if (secureStorage.isAuthenticated()) {
            userName = secureStorage.getUserName() ?: ""
            authState = AuthState.AUTHENTICATED
            Log.d("MainScreen", "User authenticated: $userName")
        } else {
            authState = AuthState.NOT_AUTHENTICATED
            Log.d("MainScreen", "User not authenticated")
        }
    }

    when (authState) {
        AuthState.CHECKING -> {
            LoadingScreen()
        }
        AuthState.NOT_AUTHENTICATED -> {
            QRLoginScreen(
                onAuthenticated = { name ->
                    userName = name
                    authState = AuthState.AUTHENTICATED
                }
            )
        }
        AuthState.AUTHENTICATED -> {
            AuthenticatedHomeScreen()
        }
    }
}

@Composable
fun LoadingScreen() {
    Grpyx_andd_watchTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F2A2E)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    indicatorColor = Color(0xFF6FE7C8),
                    trackColor = Color(0xFF1A3F44)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Loading...",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun QRLoginScreen(onAuthenticated: (String) -> Unit) {
    val context = LocalContext.current
    val apiClient = remember { WatchApiClient(context) }
    val secureStorage = remember { SecureStorage(context) }
    val scope = rememberCoroutineScope()
    
    var sessionToken by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var statusMessage by remember { mutableStateOf("Connecting...") }
    var isPolling by remember { mutableStateOf(false) }
    var expiresAt by remember { mutableStateOf(0L) }

    // Generate device ID
    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString()
    }
    val deviceModel = remember { android.os.Build.MODEL }
    val osVersion = remember { "Wear OS ${android.os.Build.VERSION.RELEASE}" }

    // Create session and generate QR code
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                statusMessage = "Creating session..."
                Log.d("QRLogin", "Creating watch session for device: $deviceId")
                
                // Call API to create session (using new API format)
                val response = apiClient.createWatchSession(
                    deviceId = deviceId,
                    deviceModel = deviceModel,
                    osVersion = osVersion
                )
                
                sessionToken = response.sessionToken
                Log.d("QRLogin", "Session created: $sessionToken")
                Log.d("QRLogin", "Expires at: ${response.expiresAt}")
                
                // Parse expiry timestamp from ISO 8601 format
                // expiresAt format: "2026-01-21T12:53:49.992693736Z"
                try {
                    val expiryStr = response.expiresAt
                    // Simple parsing - convert to timestamp (5 minutes from now as fallback)
                    expiresAt = System.currentTimeMillis() + (5 * 60 * 1000)
                } catch (e: Exception) {
                    // Default to 5 minutes
                    expiresAt = System.currentTimeMillis() + (5 * 60 * 1000)
                    Log.e("QRLogin", "Error parsing expiry, using default", e)
                }
                
                // Generate QR code data in the format expected by Flutter app
                val qrDataFormatted = JSONObject().apply {
                    put("type", "GRYPX_WATCH_LOGIN")
                    put("sessionToken", response.sessionToken)
                    put("deviceId", deviceId)
                    put("deviceName", deviceModel)
                    put("timestamp", response.qrData.getLong("timestamp"))
                    put("expiresAt", expiresAt)
                }
                
                val qrDataString = qrDataFormatted.toString()
                Log.d("QRLogin", "QR data generated: $qrDataString")
                val writer = QRCodeWriter()
                // Increase resolution for better quality
                val qrSize = 500
                val bitMatrix = writer.encode(qrDataString, BarcodeFormat.QR_CODE, qrSize, qrSize)
                val width = bitMatrix.width
                val height = bitMatrix.height
                
                // Create high-quality bitmap with padding for text and border
                val padding = 80  // Extra padding for "Grypx" text at bottom
                val borderPadding = 20  // Padding for rounded border
                val totalWidth = width + (borderPadding * 2)
                val totalHeight = height + padding + (borderPadding * 2)
                val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                
                // Fill entire background with dark color
                canvas.drawColor(android.graphics.Color.parseColor("#174D42"))
                
                // Draw rounded rectangle border around QR
                val borderPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE  // White
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 8f
                    isAntiAlias = true
                }
                val borderRect = android.graphics.RectF(
                    4f,
                    4f,
                    (width + borderPadding * 2 - 4).toFloat(),
                    (height + borderPadding * 2 - 4).toFloat()
                )
                canvas.drawRoundRect(borderRect, 24f, 24f, borderPaint)
                
                // Translate canvas to account for border padding
                canvas.translate(borderPadding.toFloat(), borderPadding.toFloat())
                
                // Paint for rounded dots
                val dotPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE  // White for dark background
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }
                
                // Draw Instagram-style rounded dots with solid black
                val dotSize = 0.90f  // Larger dots for better visibility
                val dotRadius = 1.5f  // Less rounded for more solid appearance
                
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        if (bitMatrix.get(x, y)) {
                            // Draw solid black rounded rectangle
                            val rect = android.graphics.RectF(
                                x.toFloat() + (1 - dotSize) / 2,
                                y.toFloat() + (1 - dotSize) / 2,
                                x.toFloat() + (1 + dotSize) / 2,
                                y.toFloat() + (1 + dotSize) / 2
                            )
                            canvas.drawRoundRect(rect, dotRadius, dotRadius, dotPaint)
                        }
                    }
                }
                
                // Draw Instagram-style position markers with rounded corners
                val markerSize = 7
                val markerStrokeWidth = 5f
                val markerRadius = 8f
                val innerMarkerRadius = 4f
                
                val outerMarkerPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE  // White for dark background
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = markerStrokeWidth
                    isAntiAlias = true
                }
                
                val innerMarkerPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE  // White for dark background
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }
                
                // Top-left marker
                canvas.drawRoundRect(
                    android.graphics.RectF(0f, 0f, (markerSize * 7).toFloat(), (markerSize * 7).toFloat()),
                    markerRadius, markerRadius, outerMarkerPaint
                )
                canvas.drawRoundRect(
                    android.graphics.RectF(
                        (markerSize * 2).toFloat(), 
                        (markerSize * 2).toFloat(), 
                        (markerSize * 5).toFloat(), 
                        (markerSize * 5).toFloat()
                    ),
                    innerMarkerRadius, innerMarkerRadius, innerMarkerPaint
                )
                
                // Top-right marker
                canvas.drawRoundRect(
                    android.graphics.RectF(
                        (width - markerSize * 7).toFloat(), 
                        0f, 
                        width.toFloat(), 
                        (markerSize * 7).toFloat()
                    ),
                    markerRadius, markerRadius, outerMarkerPaint
                )
                canvas.drawRoundRect(
                    android.graphics.RectF(
                        (width - markerSize * 5).toFloat(), 
                        (markerSize * 2).toFloat(), 
                        (width - markerSize * 2).toFloat(), 
                        (markerSize * 5).toFloat()
                    ),
                    innerMarkerRadius, innerMarkerRadius, innerMarkerPaint
                )
                
                // Bottom-left marker
                canvas.drawRoundRect(
                    android.graphics.RectF(
                        0f, 
                        (height - markerSize * 7).toFloat(), 
                        (markerSize * 7).toFloat(), 
                        height.toFloat()
                    ),
                    markerRadius, markerRadius, outerMarkerPaint
                )
                canvas.drawRoundRect(
                    android.graphics.RectF(
                        (markerSize * 2).toFloat(), 
                        (height - markerSize * 5).toFloat(), 
                        (markerSize * 5).toFloat(), 
                        (height - markerSize * 2).toFloat()
                    ),
                    innerMarkerRadius, innerMarkerRadius, innerMarkerPaint
                )
                
                // Reset translation for text drawing
                canvas.translate(-borderPadding.toFloat(), -borderPadding.toFloat())
                
                // Draw "GRYPX" text at bottom in Instagram style
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE  // White
                    textSize = 42f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    letterSpacing = 0.15f
                }
                
                canvas.drawText(
                    "GRYPX",
                    (totalWidth / 2).toFloat(),
                    height + borderPadding + 55f,
                    textPaint
                )
                
                qrBitmap = bitmap.asImageBitmap()
                statusMessage = "Scan with GRYPX app"
                isPolling = true
                Log.d("QRLogin", "QR code generated successfully")
                
            } catch (e: Exception) {
                Log.e("QRLogin", "Error creating session", e)
                statusMessage = "Error: ${e.message}"
            }
        }
    }

    // Poll for authentication status
    LaunchedEffect(isPolling, sessionToken) {
        if (!isPolling || sessionToken.isEmpty()) return@LaunchedEffect

        scope.launch {
            var pollCount = 0
            while (isPolling) {
                try {
                    // Poll every 2.5 seconds (recommended interval)
                    delay(2500)
                    pollCount++
                    
                    Log.d("QRLogin", "Polling session status (attempt $pollCount)...")
                    val statusResponse = apiClient.checkSessionStatus(sessionToken)
                    
                    Log.d("QRLogin", "Status: ${statusResponse.status}, Authenticated: ${statusResponse.authenticated}")
                    
                    when (statusResponse.status) {
                        "AUTHENTICATED" -> {
                            if (statusResponse.authenticated && statusResponse.user != null && statusResponse.authToken != null) {
                                Log.d("QRLogin", "Authentication successful!")
                                Log.d("QRLogin", "User: ${statusResponse.user.name} (${statusResponse.user.username})")
                                
                                // Save authentication data
                                secureStorage.saveAuthToken(statusResponse.authToken)
                                secureStorage.saveUserData(
                                    userId = statusResponse.user.id.toInt(),
                                    userName = statusResponse.user.name,
                                    username = statusResponse.user.username,
                                    mobileNumber = statusResponse.user.mobileNumber,
                                    emailId = statusResponse.user.emailId,
                                    profileImage = statusResponse.user.profileImage
                                )
                                
                                statusMessage = "Login successful!"
                                isPolling = false
                                
                                // Navigate to authenticated screen
                                delay(500)
                                onAuthenticated(statusResponse.user.name)
                            } else {
                                Log.e("QRLogin", "Invalid authentication response - missing data")
                                statusMessage = "Authentication error"
                                isPolling = false
                            }
                        }
                        
                        "PENDING" -> {
                            // Still waiting - continue polling
                            statusMessage = "Scan with GRYPX app"
                            Log.d("QRLogin", "Still pending... (poll #$pollCount)")
                        }
                        
                        "EXPIRED" -> {
                            Log.d("QRLogin", "Session expired by server")
                            statusMessage = "Session expired"
                            isPolling = false
                        }
                        
                        else -> {
                            Log.w("QRLogin", "Unknown status: ${statusResponse.status}")
                        }
                    }
                    
                } catch (e: SessionExpiredException) {
                    Log.d("QRLogin", "Session expired (410)")
                    statusMessage = "QR expired. Restart app."
                    isPolling = false
                } catch (e: SessionNotFoundException) {
                    Log.e("QRLogin", "Session not found (404)")
                    statusMessage = "Session not found"
                    isPolling = false
                } catch (e: Exception) {
                    Log.e("QRLogin", "Polling error (attempt $pollCount)", e)
                    // Continue polling on network errors (may be transient)
                    if (pollCount > 60) {
                        // Stop after 60 attempts (2.5 minutes)
                        statusMessage = "Connection timeout"
                        isPolling = false
                    }
                }
            }
        }
    }

    Grpyx_andd_watchTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF174D42)),  // Dark background
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // QR Code with clean white design
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    if (qrBitmap != null) {
                        // Clean dark container for QR code
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF174D42)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap!!,
                                contentDescription = "Login QR Code",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                    } else {
                        // Loading state with dark background
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF174D42))
                                .border(
                                    width = 1.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    indicatorColor = Color.White,
                                    trackColor = Color(0xFF174D42),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Generating...",
                                    color = Color.White,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper function to save auth data
fun saveAuthData(context: Context, authToken: String, userName: String, userId: Int) {
    val prefs = context.getSharedPreferences("grypx_watch_auth", Context.MODE_PRIVATE)
    prefs.edit().apply {
        putString("auth_token", authToken)
        putString("user_name", userName)
        putInt("user_id", userId)
        apply()
    }
}

// Helper function to clear auth data (for logout)
fun clearAuthData(context: Context) {
    val prefs = context.getSharedPreferences("grypx_watch_auth", Context.MODE_PRIVATE)
    prefs.edit().clear().apply()
}

// Screen state for authenticated user
enum class WatchScreenState {
    CHECKING_MATCH,
    NO_ACTIVE_MATCH,
    LIVE_MATCH
}

/**
 * Authenticated Home Screen - checks for active match and shows appropriate UI
 */
@Composable
fun AuthenticatedHomeScreen() {
    val context = LocalContext.current
    val secureStorage = remember { SecureStorage(context) }
    val scope = rememberCoroutineScope()
    
    var screenState by remember { mutableStateOf(WatchScreenState.CHECKING_MATCH) }
    var activeMatch by remember { mutableStateOf<ActiveMatchResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val matchService = remember {
        val authToken = secureStorage.getAuthToken() ?: ""
        WatchMatchService(authToken)
    }
    
    // Check for active match on launch
    LaunchedEffect(Unit) {
        Log.d("AuthenticatedHome", "Checking for active match...")
        try {
            val match = matchService.getActiveMatch()
            if (match != null) {
                Log.d("AuthenticatedHome", "Found active match: ${match.matchId}")
                activeMatch = match
                screenState = WatchScreenState.LIVE_MATCH
            } else {
                Log.d("AuthenticatedHome", "No active match found")
                screenState = WatchScreenState.NO_ACTIVE_MATCH
            }
        } catch (e: Exception) {
            Log.e("AuthenticatedHome", "Error checking for match", e)
            errorMessage = e.message
            screenState = WatchScreenState.NO_ACTIVE_MATCH
        }
    }
    
    // Periodically check for new matches (every 10 seconds when no match)
    LaunchedEffect(screenState) {
        if (screenState == WatchScreenState.NO_ACTIVE_MATCH) {
            while (true) {
                delay(10000) // 10 seconds
                try {
                    val match = matchService.getActiveMatch()
                    if (match != null) {
                        Log.d("AuthenticatedHome", "New match detected: ${match.matchId}")
                        activeMatch = match
                        screenState = WatchScreenState.LIVE_MATCH
                        break
                    }
                } catch (e: Exception) {
                    Log.e("AuthenticatedHome", "Error polling for match", e)
                }
            }
        }
    }
    
    when (screenState) {
        WatchScreenState.CHECKING_MATCH -> {
            CheckingMatchScreen()
        }
        WatchScreenState.NO_ACTIVE_MATCH -> {
            NoActiveMatchScreen(
                userName = secureStorage.getUserName() ?: "User",
                errorMessage = errorMessage,
                onRefresh = {
                    scope.launch {
                        screenState = WatchScreenState.CHECKING_MATCH
                        try {
                            val match = matchService.getActiveMatch()
                            if (match != null) {
                                activeMatch = match
                                screenState = WatchScreenState.LIVE_MATCH
                            } else {
                                screenState = WatchScreenState.NO_ACTIVE_MATCH
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message
                            screenState = WatchScreenState.NO_ACTIVE_MATCH
                        }
                    }
                }
            )
        }
        WatchScreenState.LIVE_MATCH -> {
            activeMatch?.let { match ->
                LiveMatchScoringScreen(
                    matchData = match,
                    onMatchEnd = {
                        activeMatch = null
                        screenState = WatchScreenState.NO_ACTIVE_MATCH
                    },
                    onBack = {
                        screenState = WatchScreenState.NO_ACTIVE_MATCH
                    }
                )
            } ?: run {
                screenState = WatchScreenState.NO_ACTIVE_MATCH
            }
        }
    }
}

@Composable
fun CheckingMatchScreen() {
    Grpyx_andd_watchTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F2A2E)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    indicatorColor = Color(0xFF6FE7C8),
                    trackColor = Color(0xFF1A3F44),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Checking for\nactive match...",
                    color = Color.White,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun NoActiveMatchScreen(
    userName: String,
    errorMessage: String?,
    onRefresh: () -> Unit
) {
    Grpyx_andd_watchTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F2A2E))
                .clickable { onRefresh() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Greeting
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hi, ${userName.split(" ").firstOrNull() ?: "there"}!",
                        color = Color(0xFF6FE7C8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // No match message
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No active match",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Start a match in the\nGRYPX mobile app",
                        color = Color(0xFF9BA3A0),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFF6B6B),
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    // Tap anywhere hint
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tap to refresh",
                        color = Color(0xFF8EF1D6),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun TournamentMatchScreen() {
    var currentScreen by remember { mutableStateOf("match") }
    var selectedTeam by remember { mutableStateOf("Net Ninja") }

    when (currentScreen) {
        "team" -> TeamRosterScreen(
            teamName = selectedTeam,
            onBack = { currentScreen = "match" }
        )
        else -> MatchScreen(
            onTeamClick = { teamName ->
                selectedTeam = teamName
                currentScreen = "team"
            }
        )
    }
}

@Composable
fun MatchScreen(onTeamClick: (String) -> Unit) {
    Grpyx_andd_watchTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F2A2E)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Section - Tournament Info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Delhi Tournament",
                        color = Color(0xFF8EF1D6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Match 2",
                        color = Color(0xFFFFFFFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Set 2",
                            color = Color(0xFF9BA3A0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal
                        )
                }

                // Middle Section - Teams and Score
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Team
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onTeamClick("Net Ninja") }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFF6FE7C8), CircleShape)
                                .background(Color(0xFF122F33)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🛡️",
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Net Ninja",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    // Score
                    Text(
                        text = "3-2",
                        color = Color(0xFF6FE7C8),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Right Team
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onTeamClick("Net Ninja") }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFF6FE7C8), CircleShape)
                                .background(Color(0xFF122F33)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🛡️",
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Net Ninja",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                // Bottom Section - UNDO Button (smaller)
                Button(
                    onClick = { /* Handle click */ },
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .size(width = 50.dp, height = 25.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF2EE6A6)
                    )
                ) {
                    Text(
                        text = "UNDO",
                        color = Color(0xFF0B2C28),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TeamRosterScreen(teamName: String, onBack: () -> Unit) {
    Grpyx_andd_watchTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F2A2E)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),

                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Team Name Header
                Text(
                    text = teamName,
                    color = Color(0xFF6FE7C8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )

                // Player List
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlayerButton("Rahul Sharma")
                    PlayerButton("Rohit Jain")
                }

                // Back Button
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(bottom = 7.dp)
                        .size(width = 48.dp, height = 20.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF2EE6A6)
                    )
                ) {
                    Text(
                        text = "BACK",
                        color = Color(0xFF0B2C28),
                        fontSize = 6.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerButton(playerName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFF6FE7C8), RoundedCornerShape(18.dp))
            .background(Color(0xFF0F2A2E))
            .clickable { /* Handle player click */ },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = playerName,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    TournamentMatchScreen()
}