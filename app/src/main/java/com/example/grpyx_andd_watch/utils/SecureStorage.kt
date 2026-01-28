package com.example.grpyx_andd_watch.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage for authentication tokens
 * Uses EncryptedSharedPreferences for security
 */
class SecureStorage(context: Context) {
    
    private val sharedPreferences: SharedPreferences
    
    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        sharedPreferences = try {
            EncryptedSharedPreferences.create(
                context,
                "grypx_watch_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails
            context.getSharedPreferences("grypx_watch_auth", Context.MODE_PRIVATE)
        }
    }
    
    // Save authentication token
    fun saveAuthToken(token: String) {
        sharedPreferences.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }
    
    // Get authentication token
    fun getAuthToken(): String? {
        return sharedPreferences.getString(KEY_AUTH_TOKEN, null)
    }
    
    // Save user data
    fun saveUserData(
        userId: Int,
        userName: String,
        username: String,
        mobileNumber: String? = null,
        emailId: String? = null,
        profileImage: String? = null
    ) {
        sharedPreferences.edit().apply {
            putInt(KEY_USER_ID, userId)
            putString(KEY_USER_NAME, userName)
            putString(KEY_USERNAME, username)
            putString(KEY_MOBILE_NUMBER, mobileNumber)
            putString(KEY_EMAIL_ID, emailId)
            putString(KEY_PROFILE_IMAGE, profileImage)
        }.apply()
    }
    
    // Get user data
    fun getUserId(): Int = sharedPreferences.getInt(KEY_USER_ID, -1)
    fun getUserName(): String? = sharedPreferences.getString(KEY_USER_NAME, null)
    fun getUsername(): String? = sharedPreferences.getString(KEY_USERNAME, null)
    fun getMobileNumber(): String? = sharedPreferences.getString(KEY_MOBILE_NUMBER, null)
    fun getEmailId(): String? = sharedPreferences.getString(KEY_EMAIL_ID, null)
    fun getProfileImage(): String? = sharedPreferences.getString(KEY_PROFILE_IMAGE, null)
    
    // Check if authenticated
    fun isAuthenticated(): Boolean {
        return !getAuthToken().isNullOrEmpty() && getUserId() != -1
    }
    
    // Clear all data (logout)
    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
    
    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USERNAME = "username"
        private const val KEY_MOBILE_NUMBER = "mobile_number"
        private const val KEY_EMAIL_ID = "email_id"
        private const val KEY_PROFILE_IMAGE = "profile_image"
    }
}
