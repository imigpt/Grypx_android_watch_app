package com.streetsports.grypxwatch.presentation

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.streetsports.grypxwatch.network.*
import com.streetsports.grypxwatch.presentation.theme.Grpyx_andd_watchTheme
import com.streetsports.grypxwatch.utils.SecureStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "LiveMatchScoring"

/**
 * Convert numeric score to tennis format
 * 0 -> "0", 1 -> "15", 2 -> "30", 3 -> "40", 4+ -> "Game"
 */
private fun getTennisScore(score: Int, opponentScore: Int): String {
    return when {
        score >= 4 && opponentScore >= 4 -> {
            when {
                score == opponentScore -> "40" // Deuce (show as 40)
                score > opponentScore -> "Ad" // Advantage
                else -> "40"
            }
        }
        score >= 4 -> "Game"
        else -> when (score) {
            0 -> "0"
            1 -> "15"
            2 -> "30"
            3 -> "40"
            else -> score.toString()
        }
    }
}

/**
 * Format score for display based on sport type
 */
private fun formatScore(score: Int, opponentScore: Int, sport: String): String {
    return if (sport.equals("Tennis", ignoreCase = true)) {
        getTennisScore(score, opponentScore)
    } else {
        score.toString()
    }
}

/**
 * Live Match Scoring Screen for Watch
 * 
 * Displays real-time match score and allows users to tap on teams to add points
 * Only for racket sports (Badminton, Tennis, Pickleball, etc.)
 */
@Composable
fun LiveMatchScoringScreen(
    matchData: ActiveMatchResponse,
    onMatchEnd: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val secureStorage = remember { SecureStorage(context) }
    val scope = rememberCoroutineScope()
    
    // State
    var team1Score by remember { mutableStateOf(matchData.team1Score) }
    var team2Score by remember { mutableStateOf(matchData.team2Score) }
    var currentSet by remember { mutableStateOf(matchData.currentSet) }
    var team1SetsWon by remember { mutableStateOf(matchData.team1SetsWon) }
    var team2SetsWon by remember { mutableStateOf(matchData.team2SetsWon) }
    var isLoading by remember { mutableStateOf(false) }
    var showUndoConfirm by remember { mutableStateOf(false) }
    var wsConnected by remember { mutableStateOf(false) }
    var showMatchEnded by remember { mutableStateOf(false) }
    var winnerName by remember { mutableStateOf("") }
    var showPlayerSelectionScreen by remember { mutableStateOf(false) }
    var selectedTeamId by remember { mutableStateOf(0L) }
    var selectedTeamName by remember { mutableStateOf("") }
    var selectedTeamPlayers by remember { mutableStateOf<List<PlayerInfo>>(emptyList()) }
    var lastScorerName by remember { mutableStateOf("") }
    var lastLocalUpdate by remember { mutableStateOf(0L) } // Timestamp of last local score update
    var confirmedScore by remember { mutableStateOf(Pair(matchData.team1Score, matchData.team2Score)) } // Last confirmed score
    
    // Services
    val matchService = remember {
        val authToken = secureStorage.getAuthToken() ?: ""
        WatchMatchService(authToken)
    }
    
    val webSocketService = remember { WatchWebSocketService() }
    
    // Collect WebSocket updates
    val scoreUpdate by webSocketService.scoreUpdate.collectAsState()
    val matchEnded by webSocketService.matchEnded.collectAsState()
    val setCompleted by webSocketService.setCompleted.collectAsState()
    val connectionState by webSocketService.connectionState.collectAsState()
    
    // Update wsConnected when connection state changes
    LaunchedEffect(connectionState) {
        wsConnected = connectionState == ConnectionState.CONNECTED
    }
    
    // Handle score updates from WebSocket
    LaunchedEffect(scoreUpdate) {
        scoreUpdate?.let { update ->
            Log.d(TAG, "WebSocket score update: ${update.team1Score} - ${update.team2Score}, current: $team1Score - $team2Score")
            
            // After a local update, BLOCK ALL WebSocket updates for 10 seconds
            val timeSinceLocalUpdate = System.currentTimeMillis() - lastLocalUpdate
            if (timeSinceLocalUpdate < 10000) {
                Log.w(TAG, "Blocking ALL WebSocket updates for ${10000 - timeSinceLocalUpdate}ms after local update")
                return@LaunchedEffect
            }
            
            // NEVER accept 0-0 if current score is higher, unless it's a new set
            val is00Reset = update.team1Score == 0 && update.team2Score == 0
            val hasCurrentScore = team1Score > 0 || team2Score > 0
            val isNewSet = update.currentSet > currentSet
            
            if (is00Reset && hasCurrentScore && !isNewSet) {
                Log.w(TAG, "BLOCKING 0-0 reset - current: $team1Score-$team2Score, set: $currentSet")
                return@LaunchedEffect
            }
            
            // Additional validation: scores should never decrease unless it's an undo
            val scoreDecreased = update.team1Score < confirmedScore.first || update.team2Score < confirmedScore.second
            if (scoreDecreased && !isNewSet) {
                Log.w(TAG, "BLOCKING score decrease from ${confirmedScore.first}-${confirmedScore.second} to ${update.team1Score}-${update.team2Score}")
                return@LaunchedEffect
            }
            
            // Update the state
            team1Score = update.team1Score
            team2Score = update.team2Score
            currentSet = update.currentSet
            team1SetsWon = update.team1SetsWon
            team2SetsWon = update.team2SetsWon
            
            // Update confirmed score
            confirmedScore = Pair(update.team1Score, update.team2Score)
            
            Log.d(TAG, "✓ Score updated from WebSocket: $team1Score - $team2Score")
        }
    }
    
    // Update last scorer from match data
    LaunchedEffect(matchData.lastScorer) {
        matchData.lastScorer?.let { scorer ->
            lastScorerName = scorer.name
            delay(3000) // Show for 3 seconds
            lastScorerName = ""
        }
    }
    
    // Handle match end from WebSocket
    LaunchedEffect(matchEnded) {
        matchEnded?.let { endEvent ->
            Log.d(TAG, "Match ended: ${endEvent.winnerName}")
            winnerName = endEvent.winnerName ?: "Unknown"
            showMatchEnded = true
        }
    }
    
    // Connect to WebSocket on launch
    LaunchedEffect(matchData.matchId) {
        Log.d(TAG, "Connecting to WebSocket for match ${matchData.matchId}")
        webSocketService.connect(matchData.matchId)
        
        // Also poll for score updates as fallback (every 5 seconds)
        while (true) {
            delay(5000)
            if (!wsConnected) {
                try {
                    val score = matchService.getMatchScore(matchData.matchId)
                    score?.let {
                        // Check if we recently made a local update
                        val timeSinceLocalUpdate = System.currentTimeMillis() - lastLocalUpdate
                        
                        // Block polling updates for 10 seconds after local update
                        if (timeSinceLocalUpdate < 10000) {
                            Log.d(TAG, "Skipping poll update due to recent local update")
                        } else {
                            // Never accept 0-0 if we have a score
                            val is00 = it.team1Score == 0 && it.team2Score == 0
                            val hasScore = team1Score > 0 || team2Score > 0
                            
                            if (!(is00 && hasScore)) {
                                team1Score = it.team1Score
                                team2Score = it.team2Score
                                currentSet = it.currentSet
                                team1SetsWon = it.team1SetsWon
                                team2SetsWon = it.team2SetsWon
                                confirmedScore = Pair(it.team1Score, it.team2Score)
                            
                                if (it.status.uppercase() in listOf("COMPLETED", "ENDED", "FINISHED")) {
                                    showMatchEnded = true
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                }
            }
        }
    }
    
    // Function to add point for a team
    fun addPointForTeam(teamId: Long, teamName: String, playerId: Long, playerName: String = "") {
        if (isLoading) return
        
        scope.launch {
            isLoading = true
            Log.d(TAG, "Adding point for $teamName (teamId: $teamId, playerId: $playerId, player: $playerName)")
            
            try {
                val response = matchService.addPoint(
                    matchId = matchData.matchId,
                    teamId = teamId,
                    playerId = playerId,
                    method = "ACE" // Use ACE as default method for racket sports
                )
                
                if (response.success) {
                    // Update local state immediately and mark timestamp
                    lastLocalUpdate = System.currentTimeMillis()
                    
                    team1Score = response.team1Score
                    team2Score = response.team2Score
                    currentSet = response.currentSet
                    team1SetsWon = response.team1SetsWon
                    team2SetsWon = response.team2SetsWon
                    
                    // Update confirmed score
                    confirmedScore = Pair(response.team1Score, response.team2Score)
                    
                    // Show who scored
                    if (playerName.isNotEmpty()) {
                        lastScorerName = playerName
                    }
                    
                    if (response.matchCompleted) {
                        winnerName = response.winnerName ?: teamName
                        showMatchEnded = true
                    }
                    
                    Log.d(TAG, "✓ Point added successfully: $team1Score - $team2Score")
                } else {
                    Log.e(TAG, "Failed to add point: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding point", e)
            } finally {
                isLoading = false
            }
        }
    }
    
    // Function to show player selection for doubles
    fun showPlayerSelectionForTeam(teamId: Long, teamName: String, players: List<PlayerInfo>) {
        if (matchData.matchFormat == "doubles" && players.size > 1) {
            // Show player selection screen for doubles
            selectedTeamId = teamId
            selectedTeamName = teamName
            selectedTeamPlayers = players
            showPlayerSelectionScreen = true
        } else {
            // Singles - directly add point
            val playerId = players.firstOrNull()?.id ?: teamId
            addPointForTeam(teamId, teamName, playerId)
        }
    }
    
    // Function to undo last point
    fun undoLastPoint() {
        if (isLoading) return
        
        scope.launch {
            isLoading = true
            Log.d(TAG, "Undoing last point")
            
            try {
                val response = matchService.undoLastEvent(matchData.matchId)
                
                if (response.success) {
                    // Update local state immediately and mark timestamp
                    lastLocalUpdate = System.currentTimeMillis()
                    
                    team1Score = response.team1Score
                    team2Score = response.team2Score
                    currentSet = response.currentSet
                    team1SetsWon = response.team1SetsWon
                    team2SetsWon = response.team2SetsWon
                    
                    // Update confirmed score
                    confirmedScore = Pair(response.team1Score, response.team2Score)
                    
                    Log.d(TAG, "✓ Undo successful: $team1Score - $team2Score")
                } else {
                    Log.e(TAG, "Failed to undo: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error undoing point", e)
            } finally {
                isLoading = false
                showUndoConfirm = false
            }
        }
    }
    
    // Show player selection screen (doubles)
    if (showPlayerSelectionScreen) {
        PlayerSelectionScreen(
            teamName = selectedTeamName,
            players = selectedTeamPlayers,
            onPlayerSelected = { player ->
                showPlayerSelectionScreen = false
                addPointForTeam(selectedTeamId, selectedTeamName, player.id, player.name)
            },
            onBack = {
                showPlayerSelectionScreen = false
            }
        )
        return
    }
    
    // Show match ended screen
    if (showMatchEnded) {
        MatchEndedScreen(
            winnerName = winnerName,
            setsScore = "$team1SetsWon - $team2SetsWon",
            onDismiss = {
                showMatchEnded = false
                onMatchEnd()
            }
        )
        return
    }
    
    // Main scoring UI
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: Tournament name and set info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    if (matchData.tournamentName.isNotEmpty()) {
                        Text(
                            text = matchData.tournamentName,
                            color = Color(0xFF2EE6A6),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.9f),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Text(
                        text = "Match ${matchData.matchId}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    
                    Text(
                        text = "Set $currentSet",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                
                // Middle: Teams and score with circular team display
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Team 1 - Circular display with logo
                    TeamCircularDisplay(
                        teamName = matchData.team1Name,
                        onClick = {
                            val players = if (matchData.team1Players.isNotEmpty()) {
                                matchData.team1Players
                            } else {
                                listOf(PlayerInfo(matchData.team1PlayerId, matchData.team1Name))
                            }
                            showPlayerSelectionForTeam(matchData.team1Id, matchData.team1Name, players)
                        },
                        isLoading = isLoading
                    )
                    
                    // Center score
                    Text(
                        text = "${formatScore(team1Score, team2Score, matchData.sport)}-${formatScore(team2Score, team1Score, matchData.sport)}",
                        color = Color(0xFF2EE6A6),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Team 2 - Circular display with logo
                    TeamCircularDisplay(
                        teamName = matchData.team2Name,
                        onClick = {
                            val players = if (matchData.team2Players.isNotEmpty()) {
                                matchData.team2Players
                            } else {
                                listOf(PlayerInfo(matchData.team2PlayerId, matchData.team2Name))
                            }
                            showPlayerSelectionForTeam(matchData.team2Id, matchData.team2Name, players)
                        },
                        isLoading = isLoading
                    )
                }
                
                // Bottom: Undo button
                Button(
                    onClick = { showUndoConfirm = true },
                    enabled = !isLoading,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .width(70.dp)
                        .height(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF2EE6A6),
                        disabledBackgroundColor = Color(0xFF1A3F44)
                    ),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text(
                        text = "Undo",
                        color = Color(0xFF0F2A2E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Loading overlay
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        indicatorColor = Color(0xFF6FE7C8),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        
        // Undo confirmation dialog
        if (showUndoConfirm) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable { showUndoConfirm = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A3F44))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Undo last point?",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showUndoConfirm = false },
                            modifier = Modifier.size(width = 45.dp, height = 26.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF4A5568)
                            )
                        ) {
                            Text("No", fontSize = 9.sp, color = Color.White)
                        }
                        Button(
                            onClick = { undoLastPoint() },
                            modifier = Modifier.size(width = 45.dp, height = 26.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF2EE6A6)
                            )
                        ) {
                            Text("Yes", fontSize = 9.sp, color = Color(0xFF0B2C28))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamCircularDisplay(
    teamName: String,
    onClick: () -> Unit,
    isLoading: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = !isLoading) { onClick() }
            .width(50.dp)
    ) {
        // Circular team logo container
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F2A2E))
                .padding(2.dp)
                .clip(CircleShape)
                .background(Color(0xFF2EE6A6).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            // Inner circle with darker background
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A2F33)),
                contentAlignment = Alignment.Center
            ) {
                // Team initial or icon placeholder
                Text(
                    text = teamName.take(2).uppercase(),
                    color = Color(0xFF2EE6A6),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(3.dp))
        
        // Team name below circle
        Text(
            text = teamName.take(10),
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TeamScoreButton(
    teamName: String,
    score: Int,
    setsWon: Int,
    isLoading: Boolean,
    onClick: () -> Unit,
    sport: String = "Badminton",
    opponentScore: Int = 0
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF122F33))
            .clickable(enabled = !isLoading) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Team name
        Text(
            text = teamName.take(10),
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(3.dp))
        
        // Score (large)
        Text(
            text = formatScore(score, opponentScore, sport),
            color = Color(0xFF6FE7C8),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(2.dp))
        
        // Sets won
        Row(
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(setsWon) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6FE7C8))
                )
                if (it < setsWon - 1) {
                    Spacer(modifier = Modifier.width(2.dp))
                }
            }
        }
    }
}

@Composable
fun MatchEndedScreen(
    winnerName: String,
    setsScore: String,
    onDismiss: () -> Unit
) {
    Grpyx_andd_watchTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F2A2E))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "MATCH OVER",
                    color = Color(0xFF6FE7C8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = winnerName,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "WINS!",
                    color = Color(0xFF8EF1D6),
                    fontSize = 9.sp
                )
                
                Spacer(modifier = Modifier.height(3.dp))
                
                Text(
                    text = "Sets: $setsScore",
                    color = Color(0xFF9BA3A0),
                    fontSize = 9.sp
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = "Tap to continue",
                    color = Color(0xFF9BA3A0),
                    fontSize = 7.sp
                )
            }
        }
    }
}
