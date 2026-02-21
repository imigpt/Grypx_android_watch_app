package com.streetsports.grypxwatch.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket service for real-time match updates on watch
 * Uses STOMP protocol over WebSocket to receive live scoring events
 * 
 * Subscribes to: /topic/match/{matchId}
 */
class WatchWebSocketService {
    
    companion object {
        private const val TAG = "WatchWebSocket"
        private const val WS_URL = "wss://grypx.co/ws/websocket"
    }
    
    private var webSocket: WebSocket? = null
    private var currentMatchId: Long? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
    
    // State flows for UI updates
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _scoreUpdate = MutableStateFlow<ScoreUpdateEvent?>(null)
    val scoreUpdate: StateFlow<ScoreUpdateEvent?> = _scoreUpdate
    
    private val _matchEnded = MutableStateFlow<MatchEndEvent?>(null)
    val matchEnded: StateFlow<MatchEndEvent?> = _matchEnded
    
    private val _setCompleted = MutableStateFlow<SetCompletedEvent?>(null)
    val setCompleted: StateFlow<SetCompletedEvent?> = _setCompleted
    
    val isConnected: Boolean
        get() = _connectionState.value == ConnectionState.CONNECTED
    
    private var subscriptionId: String = ""
    
    /**
     * Connect to WebSocket and subscribe to match events
     */
    fun connect(matchId: Long) {
        if (currentMatchId == matchId && isConnected) {
            Log.d(TAG, "Already connected to match $matchId")
            return
        }
        
        disconnect()
        currentMatchId = matchId
        
        Log.d(TAG, "Connecting to WebSocket for match $matchId...")
        _connectionState.value = ConnectionState.CONNECTING
        
        val request = Request.Builder()
            .url(WS_URL)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                
                // Send STOMP CONNECT frame
                val connectFrame = buildStompFrame("CONNECT", mapOf(
                    "accept-version" to "1.1,1.2",
                    "heart-beat" to "10000,10000"
                ))
                webSocket.send(connectFrame)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Message received: ${text.take(200)}")
                handleStompMessage(text)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _connectionState.value = ConnectionState.ERROR
                
                // Attempt reconnection after 5 seconds
                scope.launch {
                    delay(5000)
                    currentMatchId?.let { connect(it) }
                }
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
    }
    
    private fun handleStompMessage(message: String) {
        val lines = message.split("\n")
        if (lines.isEmpty()) return
        
        val command = lines[0].trim()
        
        when (command) {
            "CONNECTED" -> {
                Log.d(TAG, "STOMP connected, subscribing to match topic...")
                _connectionState.value = ConnectionState.CONNECTED
                subscribeToMatch()
            }
            "MESSAGE" -> {
                // Find the body (after blank line)
                val bodyIndex = message.indexOf("\n\n")
                if (bodyIndex > 0) {
                    val body = message.substring(bodyIndex + 2).trim().removeSuffix("\u0000")
                    if (body.isNotEmpty()) {
                        parseAndHandleMessage(body)
                    }
                }
            }
            "ERROR" -> {
                Log.e(TAG, "STOMP error: $message")
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }
    
    private fun subscribeToMatch() {
        val matchId = currentMatchId ?: return
        subscriptionId = "sub-watch-$matchId"
        
        val subscribeFrame = buildStompFrame("SUBSCRIBE", mapOf(
            "id" to subscriptionId,
            "destination" to "/topic/match/$matchId"
        ))
        
        Log.d(TAG, "Subscribing to /topic/match/$matchId")
        webSocket?.send(subscribeFrame)
    }
    
    private fun parseAndHandleMessage(payload: String) {
        try {
            val json = JSONObject(payload)
            val eventType = json.optString("type", "").uppercase()
            val eventData = json.optJSONObject("data") ?: json
            
            Log.d(TAG, "Event type: $eventType")
            
            when (eventType) {
                "POINT", "GOAL", "SCORING_EVENT", "SCORE_UPDATE" -> {
                    handleScoreUpdate(eventData)
                }
                "SET_COMPLETED" -> {
                    handleSetCompleted(eventData)
                }
                "MATCH_COMPLETED", "MATCH_END" -> {
                    handleMatchEnd(eventData)
                }
                "UNDO", "UNDO_SCORE" -> {
                    handleScoreUpdate(eventData)
                }
                else -> {
                    // Try to extract score data anyway
                    if (eventData.has("team1Score") || eventData.has("scoreA") || 
                        eventData.has("scoreState")) {
                        handleScoreUpdate(eventData)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}")
        }
    }
    
    private fun handleScoreUpdate(data: JSONObject) {
        val scoreState = data.optJSONObject("scoreState")
        
        // Try to extract scores from various possible fields
        val team1Score = when {
            scoreState?.has("scoreA") == true -> scoreState.getInt("scoreA")
            data.has("team1Score") -> data.getInt("team1Score")
            data.has("scoreA") -> data.getInt("scoreA")
            else -> null // Don't default to 0, keep null if no score found
        }
        
        val team2Score = when {
            scoreState?.has("scoreB") == true -> scoreState.getInt("scoreB")
            data.has("team2Score") -> data.getInt("team2Score")
            data.has("scoreB") -> data.getInt("scoreB")
            else -> null
        }
        
        // Only update if we have valid score data
        if (team1Score == null && team2Score == null) {
            Log.w(TAG, "No valid score data in update, ignoring")
            return
        }
        
        val currentSet = data.optInt("currentSetNumber", data.optInt("currentSet", 1))
        val team1SetsWon = data.optInt("team1SetsWon", 0)
        val team2SetsWon = data.optInt("team2SetsWon", 0)
        
        val finalTeam1Score = team1Score ?: 0
        val finalTeam2Score = team2Score ?: 0
        
        val currentValue = _scoreUpdate.value
        
        // AGGRESSIVE 0-0 blocking: Never send 0-0 if we have existing non-zero scores in the same set
        if (currentValue != null) {
            val is00 = finalTeam1Score == 0 && finalTeam2Score == 0
            val hasCurrentScore = currentValue.team1Score > 0 || currentValue.team2Score > 0
            val sameSet = currentSet == currentValue.currentSet
            
            if (is00 && hasCurrentScore && sameSet) {
                Log.w(TAG, "BLOCKED: 0-0 update while current score is ${currentValue.team1Score}-${currentValue.team2Score} (Set $currentSet)")
                return
            }
            
            // Also block if scores DECREASE (unless new set)
            val scoreDecreased = (finalTeam1Score < currentValue.team1Score || finalTeam2Score < currentValue.team2Score)
            val isNewSet = currentSet > currentValue.currentSet
            
            if (scoreDecreased && !isNewSet) {
                Log.w(TAG, "BLOCKED: Score decrease from ${currentValue.team1Score}-${currentValue.team2Score} to $finalTeam1Score-$finalTeam2Score")
                return
            }
        }
        
        _scoreUpdate.value = ScoreUpdateEvent(
            team1Score = finalTeam1Score,
            team2Score = finalTeam2Score,
            currentSet = currentSet,
            team1SetsWon = team1SetsWon,
            team2SetsWon = team2SetsWon
        )
        
        Log.d(TAG, "\u2713 Score updated: $finalTeam1Score - $finalTeam2Score (Set $currentSet)")
    }
    
    private fun handleSetCompleted(data: JSONObject) {
        val setNumber = data.optInt("setNumber", data.optInt("set", 1))
        val team1SetScore = data.optInt("team1SetScore", data.optInt("team1Score", 0))
        val team2SetScore = data.optInt("team2SetScore", data.optInt("team2Score", 0))
        val team1SetsWon = data.optInt("team1SetsWon", 0)
        val team2SetsWon = data.optInt("team2SetsWon", 0)
        
        _setCompleted.value = SetCompletedEvent(
            setNumber = setNumber,
            team1SetScore = team1SetScore,
            team2SetScore = team2SetScore,
            team1SetsWon = team1SetsWon,
            team2SetsWon = team2SetsWon
        )
        
        // ONLY reset to 0-0 for new set if explicitly a set completion event
        // Check if the next set has actually started with score data
        val nextSetScore1 = data.optInt("nextSetScore1", -1)
        val nextSetScore2 = data.optInt("nextSetScore2", -1)
        
        if (nextSetScore1 >= 0 && nextSetScore2 >= 0) {
            // Use provided next set scores
            _scoreUpdate.value = ScoreUpdateEvent(
                team1Score = nextSetScore1,
                team2Score = nextSetScore2,
                currentSet = setNumber + 1,
                team1SetsWon = team1SetsWon,
                team2SetsWon = team2SetsWon
            )
        } else {
            // Reset to 0-0 for new set
            _scoreUpdate.value = ScoreUpdateEvent(
                team1Score = 0,
                team2Score = 0,
                currentSet = setNumber + 1,
                team1SetsWon = team1SetsWon,
                team2SetsWon = team2SetsWon
            )
        }
        
        Log.d(TAG, "Set $setNumber completed: $team1SetScore - $team2SetScore (Next set: ${setNumber + 1})")
    }
    
    private fun handleMatchEnd(data: JSONObject) {
        val winnerId = data.optLong("winnerTeamId", 0)
        val winnerName = data.optString("winnerName", null)
        val setsScore = data.optString("setsScore", "")
        
        _matchEnded.value = MatchEndEvent(
            winnerId = winnerId,
            winnerName = winnerName,
            setsScore = setsScore
        )
        
        Log.d(TAG, "Match ended! Winner: $winnerName")
    }
    
    private fun buildStompFrame(command: String, headers: Map<String, String>, body: String = ""): String {
        val builder = StringBuilder()
        builder.append(command).append("\n")
        headers.forEach { (key, value) ->
            builder.append("$key:$value\n")
        }
        builder.append("\n")
        builder.append(body)
        builder.append("\u0000")
        return builder.toString()
    }
    
    /**
     * Disconnect from WebSocket
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket...")
        
        // Send STOMP DISCONNECT
        if (subscriptionId.isNotEmpty()) {
            val unsubscribeFrame = buildStompFrame("UNSUBSCRIBE", mapOf("id" to subscriptionId))
            webSocket?.send(unsubscribeFrame)
        }
        
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        currentMatchId = null
        subscriptionId = ""
        _connectionState.value = ConnectionState.DISCONNECTED
    }
    
    /**
     * Reset state flows (for when navigating away)
     */
    fun resetState() {
        _scoreUpdate.value = null
        _matchEnded.value = null
        _setCompleted.value = null
    }
}

// State and event classes

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class ScoreUpdateEvent(
    val team1Score: Int,
    val team2Score: Int,
    val currentSet: Int,
    val team1SetsWon: Int,
    val team2SetsWon: Int
)

data class SetCompletedEvent(
    val setNumber: Int,
    val team1SetScore: Int,
    val team2SetScore: Int,
    val team1SetsWon: Int,
    val team2SetsWon: Int
)

data class MatchEndEvent(
    val winnerId: Long,
    val winnerName: String?,
    val setsScore: String
)
