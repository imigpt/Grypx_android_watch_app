package com.streetsports.grypxwatch.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * API Client for GRYPX Watch QR Login
 * Base URL: https://grypx.co
 * API Version: 1.0
 * Updated: January 21, 2026
 */
class WatchApiClient(private val context: Context) {
    
    private val baseUrl = "https://api.grypx.co/api"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Create a new watch session and get session token for QR code
     * POST /api/auth/watch/session
     * 
     * Request: { deviceId, deviceModel, osVersion }
     * Response: { success, sessionToken, expiresAt, qrData }
     */
    suspend fun createWatchSession(
        deviceId: String,
        deviceModel: String,
        osVersion: String = "Wear OS 4.0"
    ): SessionResponse = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("deviceId", deviceId)
            put("deviceModel", deviceModel)
            put("osVersion", osVersion)
        }
        
        val request = Request.Builder()
            .url("$baseUrl/auth/watch/session")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        
        if (!response.isSuccessful) {
            val error = JSONObject(responseBody)
            throw Exception(error.optString("message", "Failed to create session"))
        }
        
        val json = JSONObject(responseBody)
        SessionResponse(
            success = json.getBoolean("success"),
            sessionToken = json.getString("sessionToken"),
            expiresAt = json.getString("expiresAt"),
            qrData = json.getJSONObject("qrData")
        )
    }
    
    /**
     * Check session authentication status (polling endpoint)
     * GET /api/auth/watch/session/{sessionToken}/status
     * 
     * Poll this every 2-3 seconds until status = AUTHENTICATED
     * 
     * Response Statuses:
     * - PENDING: Waiting for mobile app to scan QR
     * - AUTHENTICATED: Mobile app authenticated, user data available
     * - EXPIRED: Session expired (410 error)
     */
    suspend fun checkSessionStatus(sessionToken: String): SessionStatusResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/auth/watch/session/$sessionToken/status")
            .get()
            .build()
        
        val response = client.newCall(request).execute()
        
        when (response.code) {
            200 -> {
                val responseBody = response.body?.string() ?: throw Exception("Empty response")
                val json = JSONObject(responseBody)
                val status = json.getString("status")
                val authenticated = json.getBoolean("authenticated")
                
                if (status == "AUTHENTICATED" && authenticated) {
                    // User authenticated - extract user data and auth token
                    val userJson = json.getJSONObject("user")
                    SessionStatusResponse(
                        status = status,
                        authenticated = true,
                        message = json.optString("message", null),
                        authToken = json.getString("authToken"),
                        userId = json.getLong("userId"),
                        user = UserData(
                            id = userJson.getLong("id"),
                            name = userJson.getString("name"),
                            username = userJson.getString("username"),
                            mobileNumber = userJson.optString("mobileNumber", null),
                            emailId = userJson.optString("emailId", null),
                            profileImage = userJson.optString("profileImage", null)
                        )
                    )
                } else {
                    // Still pending
                    SessionStatusResponse(
                        status = status,
                        authenticated = false,
                        message = json.optString("message", "Waiting for authentication"),
                        authToken = null,
                        userId = null,
                        user = null
                    )
                }
            }
            410 -> {
                // Session expired
                throw SessionExpiredException("Session expired or invalid")
            }
            404 -> {
                // Session not found
                throw SessionNotFoundException("Session not found")
            }
            else -> {
                val errorBody = response.body?.string()
                throw Exception("Failed to check status: ${response.code} - $errorBody")
            }
        }
    }
}

/**
 * Data classes for API responses
 * Based on Backend API Specification v1.0
 */

/**
 * Response from POST /api/auth/watch/session
 */
data class SessionResponse(
    val success: Boolean,
    val sessionToken: String,
    val expiresAt: String,
    val qrData: JSONObject
)

/**
 * Response from GET /api/auth/watch/session/{token}/status
 */
data class SessionStatusResponse(
    val status: String,              // PENDING, AUTHENTICATED, EXPIRED
    val authenticated: Boolean,      // true if AUTHENTICATED
    val message: String? = null,     // Optional message
    val authToken: String? = null,   // JWT token (only when authenticated)
    val userId: Long? = null,        // User ID (only when authenticated)
    val user: UserData? = null       // User data (only when authenticated)
)

/**
 * User data returned when authenticated
 */
data class UserData(
    val id: Long,
    val name: String,
    val username: String,
    val mobileNumber: String? = null,
    val emailId: String? = null,
    val profileImage: String? = null
)

/**
 * Custom exceptions for session handling
 */
class SessionExpiredException(message: String) : Exception(message)
class SessionNotFoundException(message: String) : Exception(message)
