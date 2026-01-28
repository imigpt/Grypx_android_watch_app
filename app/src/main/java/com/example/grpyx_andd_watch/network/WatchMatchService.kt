package com.example.grpyx_andd_watch.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service for watch match operations
 * Handles fetching active matches and scoring updates for racket sports
 * 
 * Base URL: http://34.131.53.32:8080
 * API Version: 1.0
 * Updated: January 22, 2026
 */
class WatchMatchService(private val authToken: String) {
    
    companion object {
        private const val TAG = "WatchMatchService"
        private const val BASE_URL = "http://34.131.53.32:8080/api"
        
        // Sport types that support watch scoring
        val RACKET_SPORTS = listOf("badminton", "tennis", "pickleball", "table_tennis", "squash")
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private fun getHeaders(): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "Authorization" to "Bearer $authToken"
    )
    
    /**
     * Get user's active live match
     * GET /api/watch/active-match
     * 
     * Returns the current live match for the authenticated user
     * Only returns racket sport matches (not football)
     */
    suspend fun getActiveMatch(): ActiveMatchResponse? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching active match for user...")
            
            val request = Request.Builder()
                .url("$BASE_URL/watch/active-match")
                .apply {
                    getHeaders().forEach { (key, value) -> addHeader(key, value) }
                }
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "Active match response: ${response.code} - $responseBody")
            
            when (response.code) {
                200 -> {
                    if (responseBody.isNullOrEmpty()) return@withContext null
                    
                    val json = JSONObject(responseBody)
                    if (!json.has("matchId")) return@withContext null
                    
                    val sport = json.optString("sport", "").lowercase()
                    
                    // Only return racket sports matches
                    if (!RACKET_SPORTS.any { sport.contains(it) }) {
                        Log.d(TAG, "Match is not a racket sport: $sport")
                        return@withContext null
                    }
                    
                    // Parse player IDs - use team IDs as fallback if not provided
                    val team1Id = json.getLong("team1Id")
                    val team2Id = json.getLong("team2Id")
                    
                    // Parse match format (singles, doubles, team)
                    val matchFormat = json.optString("matchFormat", "singles")
                    Log.d(TAG, "Match format: $matchFormat")
                    
                    // Try to get player IDs from response
                    // For doubles, backend may return player ID arrays
                    var team1PlayerId = json.optLong("team1PlayerId", 0)
                    var team2PlayerId = json.optLong("team2PlayerId", 0)
                    
                    // Try to get from player ID arrays (for doubles)
                    val team1PlayerIdsArray = json.optJSONArray("team1PlayerIds")
                    val team2PlayerIdsArray = json.optJSONArray("team2PlayerIds")
                    
                    if (team1PlayerId == 0L && team1PlayerIdsArray != null && team1PlayerIdsArray.length() > 0) {
                        team1PlayerId = team1PlayerIdsArray.optLong(0, team1Id)
                    }
                    if (team2PlayerId == 0L && team2PlayerIdsArray != null && team2PlayerIdsArray.length() > 0) {
                        team2PlayerId = team2PlayerIdsArray.optLong(0, team2Id)
                    }
                    
                    // Final fallback to team IDs
                    if (team1PlayerId == 0L) team1PlayerId = team1Id
                    if (team2PlayerId == 0L) team2PlayerId = team2Id
                    
                    Log.d(TAG, "Team1 player ID: $team1PlayerId, Team2 player ID: $team2PlayerId")
                    
                    // Parse player lists for doubles support
                    val team1Players = parsePlayerList(json.optJSONArray("team1Players"))
                    val team2Players = parsePlayerList(json.optJSONArray("team2Players"))
                    
                    // Parse last scorer
                    val lastScorerJson = json.optJSONObject("lastScorer")
                    val lastScorer = if (lastScorerJson != null) {
                        PlayerInfo(
                            id = lastScorerJson.optLong("id", 0),
                            name = lastScorerJson.optString("name", ""),
                            username = lastScorerJson.optString("username", "")
                        )
                    } else null
                    
                    ActiveMatchResponse(
                        matchId = json.getLong("matchId"),
                        tournamentId = json.optLong("tournamentId", 0),
                        tournamentName = json.optString("tournamentName", ""),
                        team1Id = team1Id,
                        team1Name = json.getString("team1Name"),
                        team1PlayerId = team1PlayerId,
                        team1Players = team1Players,
                        team2Id = team2Id,
                        team2Name = json.getString("team2Name"),
                        team2PlayerId = team2PlayerId,
                        team2Players = team2Players,
                        team1Score = json.optInt("team1Score", 0),
                        team2Score = json.optInt("team2Score", 0),
                        currentSet = json.optInt("currentSet", 1),
                        team1SetsWon = json.optInt("team1SetsWon", 0),
                        team2SetsWon = json.optInt("team2SetsWon", 0),
                        sport = json.optString("sport", "Badminton"),
                        matchFormat = matchFormat,
                        status = json.optString("status", "LIVE"),
                        setHistory = parseSetHistory(json.optJSONArray("setHistory")),
                        lastScorer = lastScorer
                    )
                }
                204, 404 -> {
                    Log.d(TAG, "No Active Match Found")
                    null
                }
                else -> {
                    Log.e(TAG, "Error fetching active match: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active match", e)
            null
        }
    }
    
    /**
     * Get match details by ID
     * GET /api/match/{matchId}
     */
    suspend fun getMatchDetails(matchId: Long): MatchDetailResponse? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching match details for matchId: $matchId")
            
            val request = Request.Builder()
                .url("$BASE_URL/match/$matchId")
                .apply {
                    getHeaders().forEach { (key, value) -> addHeader(key, value) }
                }
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "Match details response: ${response.code}")
            
            if (response.code == 200 && !responseBody.isNullOrEmpty()) {
                val json = JSONObject(responseBody)
                
                // Extract score state
                val scoreState = json.optJSONObject("scoreState")
                
                MatchDetailResponse(
                    matchId = json.getLong("matchId"),
                    team1Id = json.optLong("team1Id", 0),
                    team1Name = json.optString("team1Name", "Team 1"),
                    team2Id = json.optLong("team2Id", 0),
                    team2Name = json.optString("team2Name", "Team 2"),
                    team1Score = scoreState?.optInt("scoreA", 0) ?: json.optInt("team1Score", 0),
                    team2Score = scoreState?.optInt("scoreB", 0) ?: json.optInt("team2Score", 0),
                    currentSet = json.optInt("currentSet", 1),
                    team1SetsWon = json.optInt("team1SetsWon", 0),
                    team2SetsWon = json.optInt("team2SetsWon", 0),
                    sport = json.optString("sport", "Badminton"),
                    status = json.optString("status", "LIVE"),
                    setHistory = parseSetHistory(json.optJSONArray("setHistory"))
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching match details", e)
            null
        }
    }
    
    /**
     * Add a point for a team (racket sports)
     * POST /api/match-scoring/add-point
     * 
     * @param matchId The match ID
     * @param teamId The team that scored
     * @param playerId The player who scored (REQUIRED - use team ID as fallback)
     * @param method The scoring method (ACE, SMASH, DROP, NET, etc.)
     */
    suspend fun addPoint(
        matchId: Long,
        teamId: Long,
        playerId: Long,
        method: String = "ACE"
    ): AddPointResponse = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Adding point for team $teamId (player $playerId) in match $matchId")
            
            val requestBody = JSONObject().apply {
                put("matchId", matchId)
                put("teamId", teamId)
                put("playerId", playerId) // Must be a valid player ID
                put("method", method.uppercase())
            }
            
            Log.d(TAG, "Request body: $requestBody")
            
            val request = Request.Builder()
                .url("$BASE_URL/match-scoring/add-point")
                .apply {
                    getHeaders().forEach { (key, value) -> addHeader(key, value) }
                }
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "Add point response: ${response.code} - $responseBody")
            
            if (response.code == 200 && !responseBody.isNullOrEmpty()) {
                val json = JSONObject(responseBody)
                
                AddPointResponse(
                    success = true,
                    team1Score = json.optInt("team1Score", 0),
                    team2Score = json.optInt("team2Score", 0),
                    currentSet = json.optInt("currentSetNumber", 1),
                    team1SetsWon = json.optInt("team1SetsWon", 0),
                    team2SetsWon = json.optInt("team2SetsWon", 0),
                    setCompleted = json.optBoolean("setCompleted", false),
                    matchCompleted = json.optBoolean("matchCompleted", false),
                    winnerId = json.optLong("winnerTeamId", 0),
                    winnerName = json.optString("winnerName", null)
                )
            } else {
                AddPointResponse(
                    success = false,
                    errorMessage = "Failed to add point: ${response.code}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding point", e)
            AddPointResponse(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Undo last scoring event
     * POST /api/match-scoring/undo
     */
    suspend fun undoLastEvent(matchId: Long): UndoResponse = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Undoing last event for match $matchId")
            
            val requestBody = JSONObject().apply {
                put("matchId", matchId)
            }
            
            val request = Request.Builder()
                .url("$BASE_URL/match-scoring/undo")
                .apply {
                    getHeaders().forEach { (key, value) -> addHeader(key, value) }
                }
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "Undo response: ${response.code} - $responseBody")
            
            if (response.code == 200 && !responseBody.isNullOrEmpty()) {
                val json = JSONObject(responseBody)
                
                UndoResponse(
                    success = true,
                    team1Score = json.optInt("team1Score", 0),
                    team2Score = json.optInt("team2Score", 0),
                    currentSet = json.optInt("currentSetNumber", 1),
                    team1SetsWon = json.optInt("team1SetsWon", 0),
                    team2SetsWon = json.optInt("team2SetsWon", 0)
                )
            } else {
                UndoResponse(
                    success = false,
                    errorMessage = "Failed to undo: ${response.code}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error undoing event", e)
            UndoResponse(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Get current match score (polling endpoint)
     * GET /api/match/{matchId}
     */
    suspend fun getMatchScore(matchId: Long): MatchScoreResponse? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/match/$matchId")
                .apply {
                    getHeaders().forEach { (key, value) -> addHeader(key, value) }
                }
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.code == 200 && !responseBody.isNullOrEmpty()) {
                val json = JSONObject(responseBody)
                val scoreState = json.optJSONObject("scoreState")
                
                MatchScoreResponse(
                    matchId = json.getLong("matchId"),
                    team1Score = scoreState?.optInt("scoreA", 0) ?: 0,
                    team2Score = scoreState?.optInt("scoreB", 0) ?: 0,
                    currentSet = json.optInt("currentSet", 1),
                    team1SetsWon = json.optInt("team1SetsWon", 0),
                    team2SetsWon = json.optInt("team2SetsWon", 0),
                    status = json.optString("status", "LIVE"),
                    setHistory = parseSetHistory(json.optJSONArray("setHistory"))
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching match score", e)
            null
        }
    }
    
    private fun parseSetHistory(jsonArray: JSONArray?): List<SetScore> {
        if (jsonArray == null) return emptyList()
        
        return (0 until jsonArray.length()).mapNotNull { i ->
            try {
                val setJson = jsonArray.getJSONObject(i)
                SetScore(
                    setNumber = setJson.optInt("set", setJson.optInt("setNumber", i + 1)),
                    team1Score = setJson.optInt("team1", setJson.optInt("team1Score", 0)),
                    team2Score = setJson.optInt("team2", setJson.optInt("team2Score", 0))
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun parsePlayerList(jsonArray: JSONArray?): List<PlayerInfo> {
        if (jsonArray == null) return emptyList()
        
        return (0 until jsonArray.length()).mapNotNull { i ->
            try {
                val playerJson = jsonArray.getJSONObject(i)
                PlayerInfo(
                    id = playerJson.optLong("id", playerJson.optLong("playerId", 0)),
                    name = playerJson.optString("name", playerJson.optString("playerName", "Player ${i + 1}")),
                    username = playerJson.optString("username", "")
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing player at index $i", e)
                null
            }
        }
    }
}

// Data classes for API responses

data class ActiveMatchResponse(
    val matchId: Long,
    val tournamentId: Long = 0,
    val tournamentName: String = "",
    val team1Id: Long,
    val team1Name: String,
    val team1PlayerId: Long = 0, // Player ID for team 1 (for scoring API)
    val team1Players: List<PlayerInfo> = emptyList(), // All players in team 1 (for doubles)
    val team2Id: Long,
    val team2Name: String,
    val team2PlayerId: Long = 0, // Player ID for team 2 (for scoring API)
    val team2Players: List<PlayerInfo> = emptyList(), // All players in team 2 (for doubles)
    val team1Score: Int = 0,
    val team2Score: Int = 0,
    val currentSet: Int = 1,
    val team1SetsWon: Int = 0,
    val team2SetsWon: Int = 0,
    val sport: String = "Badminton",
    val matchFormat: String = "singles", // 'singles', 'doubles', or 'team'
    val status: String = "LIVE",
    val setHistory: List<SetScore> = emptyList(),
    val lastScorer: PlayerInfo? = null // Last player who scored (for display)
)

data class PlayerInfo(
    val id: Long,
    val name: String,
    val username: String = ""
)

data class MatchDetailResponse(
    val matchId: Long,
    val team1Id: Long,
    val team1Name: String,
    val team2Id: Long,
    val team2Name: String,
    val team1Score: Int = 0,
    val team2Score: Int = 0,
    val currentSet: Int = 1,
    val team1SetsWon: Int = 0,
    val team2SetsWon: Int = 0,
    val sport: String = "Badminton",
    val status: String = "LIVE",
    val setHistory: List<SetScore> = emptyList()
)

data class MatchScoreResponse(
    val matchId: Long,
    val team1Score: Int,
    val team2Score: Int,
    val currentSet: Int,
    val team1SetsWon: Int,
    val team2SetsWon: Int,
    val status: String,
    val setHistory: List<SetScore> = emptyList()
)

data class SetScore(
    val setNumber: Int,
    val team1Score: Int,
    val team2Score: Int
)

data class AddPointResponse(
    val success: Boolean,
    val team1Score: Int = 0,
    val team2Score: Int = 0,
    val currentSet: Int = 1,
    val team1SetsWon: Int = 0,
    val team2SetsWon: Int = 0,
    val setCompleted: Boolean = false,
    val matchCompleted: Boolean = false,
    val winnerId: Long = 0,
    val winnerName: String? = null,
    val errorMessage: String? = null
)

data class UndoResponse(
    val success: Boolean,
    val team1Score: Int = 0,
    val team2Score: Int = 0,
    val currentSet: Int = 1,
    val team1SetsWon: Int = 0,
    val team2SetsWon: Int = 0,
    val errorMessage: String? = null
)
