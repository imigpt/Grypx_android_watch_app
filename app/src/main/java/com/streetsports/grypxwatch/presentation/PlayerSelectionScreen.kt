package com.example.grpyx_andd_watch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.example.grpyx_andd_watch.network.PlayerInfo
import com.example.grpyx_andd_watch.presentation.theme.Grpyx_andd_watchTheme

/**
 * Player Selection Screen for Doubles Matches
 * 
 * Shows list of players from selected team
 * User taps player name to register point for that player
 */
@Composable
fun PlayerSelectionScreen(
    teamName: String,
    players: List<PlayerInfo>,
    onPlayerSelected: (PlayerInfo) -> Unit,
    onBack: () -> Unit
) {
    Grpyx_andd_watchTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F2A2E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Text(
                        text = teamName,
                        color = Color(0xFF8EF1D6),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Player List
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    players.forEach { player ->
                        PlayerButton(
                            playerName = player.name,
                            onClick = { onPlayerSelected(player) }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                
                // Back button
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(60.dp)
                        .height(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF4A5568)
                    ),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text(
                        text = "Back",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerButton(
    playerName: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(35.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(Color(0xFF0F2A2E))
            .border(
                width = 2.dp,
                color = Color(0xFF8EF1D6),
                shape = RoundedCornerShape(21.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = playerName,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}
