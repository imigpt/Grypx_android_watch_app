# GRYPX Android Watch App - Complete Documentation

**Version:** 1.0  
**Date:** February 5, 2026  
**Platform:** Wear OS (Android Watch)  
**Target SDK:** 35 (Android 15)  
**Min SDK:** 30 (Wear OS 4.0+)

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Features & Flow](#features--flow)
4. [Technical Stack](#technical-stack)
5. [Application Screens](#application-screens)
6. [Network Layer](#network-layer)
7. [Data Layer](#data-layer)
8. [UI/UX Components](#uiux-components)
9. [Real-time Updates](#real-time-updates)
10. [Security](#security)
11. [Build & Deployment](#build--deployment)
12. [API Integration](#api-integration)

---

## 🎯 Overview

### Purpose
The GRYPX Watch App is a companion application for Wear OS smartwatches that allows users to:
- **Login via QR Code** - Scan QR on watch with mobile app for seamless authentication
- **Live Match Scoring** - Record points in real-time during racket sport matches
- **Real-time Updates** - View live score updates via WebSocket
- **Player Selection** - Select specific players for doubles matches

### Supported Sports
- Badminton
- Tennis
- Pickleball
- Table Tennis
- Squash

### Key Highlights
✅ **Standalone App** - Works independently without phone companion  
✅ **Secure Authentication** - Encrypted token storage  
✅ **Real-time Sync** - WebSocket + REST API hybrid  
✅ **Offline Support** - Fallback polling mechanism  
✅ **Low Power Design** - Optimized for battery efficiency

---

## 🏗️ Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    GRYPX Watch App                      │
│                     (Wear OS)                           │
└─────────────────────────────────────────────────────────┘
                          │
                          │
        ┌─────────────────┴─────────────────┐
        │                                   │
        ▼                                   ▼
┌──────────────┐                    ┌──────────────┐
│  REST APIs   │                    │  WebSocket   │
│ HTTP Client  │                    │  (STOMP)     │
└──────────────┘                    └──────────────┘
        │                                   │
        └─────────────────┬─────────────────┘
                          │
                          ▼
        ┌─────────────────────────────────┐
        │    GRYPX Backend Server         │
        │    34.131.53.32:8080           │
        └─────────────────────────────────┘
```

### Layer Architecture

```
┌─────────────────────────────────────────────┐
│         PRESENTATION LAYER                  │
│   (Compose UI Screens & ViewModels)         │
├─────────────────────────────────────────────┤
│         SERVICE LAYER                       │
│   (Network, WebSocket, Match Service)       │
├─────────────────────────────────────────────┤
│         DATA LAYER                          │
│   (SecureStorage, Response Models)          │
├─────────────────────────────────────────────┤
│         INFRASTRUCTURE                      │
│   (OkHttp, Security Crypto, Coroutines)     │
└─────────────────────────────────────────────┘
```

### File Structure

```
app/src/main/
├── AndroidManifest.xml
├── res/
│   ├── drawable/
│   ├── values/
│   └── xml/
│       └── network_security_config.xml
└── java/com/streetsports/grypxwatch/
    ├── presentation/
    │   ├── MainActivity.kt                 # Main entry point & screens
    │   ├── LiveMatchScoringScreen.kt       # Live scoring UI
    │   ├── PlayerSelectionScreen.kt        # Player selection for doubles
    │   └── theme/
    │       └── Theme.kt                    # App theme
    ├── network/
    │   ├── WatchApiClient.kt              # REST API client
    │   ├── WatchMatchService.kt           # Match operations
    │   └── WatchWebSocketService.kt       # WebSocket STOMP client
    ├── utils/
    │   └── SecureStorage.kt               # Encrypted preferences
    ├── tile/
    │   └── MainTileService.kt             # Watch tile
    └── complication/
        └── MainComplicationService.kt      # Watch complication
```

---

## 🔄 Features & Flow

### 1. Application Flow Diagram

```
┌──────────────────────────────────────────────────────────┐
│                    App Launch                            │
└────────────────────┬─────────────────────────────────────┘
                     │
                     ▼
          ┌──────────────────┐
          │  Check Auth      │
          │  Status          │
          └────────┬─────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
        ▼                     ▼
   Not Authenticated    Authenticated
        │                     │
        │                     ▼
        │           ┌──────────────────┐
        │           │  Check for       │
        │           │  Active Match    │
        │           └────────┬─────────┘
        │                    │
        │         ┌──────────┴──────────┐
        │         │                     │
        │         ▼                     ▼
        │    Match Found          No Match
        │         │                     │
        ▼         ▼                     ▼
  ┌─────────┐  ┌──────────┐  ┌──────────────┐
  │   QR    │  │  Live    │  │  No Active   │
  │  Login  │  │  Match   │  │  Match       │
  │  Screen │  │  Screen  │  │  Screen      │
  └─────────┘  └──────────┘  └──────────────┘
       │            │               │
       │            │               │
       └────────────┴───────────────┘
                    │
                    ▼
              Auto-refresh &
              Poll for updates
```

### 2. QR Login Flow

```
┌─────────────────────────────────────────────────────────┐
│  Step 1: Watch App Creates Session                     │
│  POST /api/auth/watch/session                          │
│  ┌──────────────────────────────────────────┐          │
│  │ Request: {                                │          │
│  │   "deviceId": "ff2ea3711bf0be1e",        │          │
│  │   "deviceModel": "Galaxy Watch 6",        │          │
│  │   "osVersion": "Wear OS 5.0"              │          │
│  │ }                                         │          │
│  └──────────────────────────────────────────┘          │
│                      ↓                                  │
│  ┌──────────────────────────────────────────┐          │
│  │ Response: {                               │          │
│  │   "sessionToken": "wch_abc123...",        │          │
│  │   "expiresAt": "2026-02-05T...",          │          │
│  │   "qrData": { ... }                       │          │
│  │ }                                         │          │
│  └──────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────┐
│  Step 2: Generate QR Code                              │
│  ┌─────────────────────────────────────────┐           │
│  │  QR Data Format:                        │           │
│  │  {                                      │           │
│  │    "type": "GRYPX_WATCH_LOGIN",        │           │
│  │    "sessionToken": "wch_abc123...",    │           │
│  │    "deviceId": "ff2ea3711bf0be1e",     │           │
│  │    "deviceName": "Galaxy Watch 6",      │           │
│  │    "timestamp": 1707134524000,          │           │
│  │    "expiresAt": 1707134824000           │           │
│  │  }                                      │           │
│  └─────────────────────────────────────────┘           │
│                                                         │
│  Display: 500x500px QR with rounded design             │
│  Colors: White QR on #174D42 background                │
│  Branding: "GRYPX" text at bottom                      │
└─────────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────┐
│  Step 3: Poll for Authentication                       │
│  GET /api/auth/watch/session/{token}/status            │
│  Interval: Every 2.5 seconds                           │
│  Timeout: 60 attempts (2.5 minutes)                    │
│                                                         │
│  Responses:                                            │
│  ├─ PENDING → Continue polling                         │
│  ├─ AUTHENTICATED → Save token & login                 │
│  └─ EXPIRED (410) → Show error                         │
└─────────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────┐
│  Step 4: User Scans QR with Mobile App                │
│  Mobile app sends authentication to backend            │
│  Backend marks session as AUTHENTICATED                │
└─────────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────┐
│  Step 5: Watch Receives Auth Data                     │
│  ┌──────────────────────────────────────────┐          │
│  │ Response: {                               │          │
│  │   "status": "AUTHENTICATED",              │          │
│  │   "authenticated": true,                  │          │
│  │   "authToken": "eyJhbGci...",            │          │
│  │   "userId": 12345,                        │          │
│  │   "user": {                               │          │
│  │     "id": 12345,                          │          │
│  │     "name": "John Doe",                   │          │
│  │     "username": "johndoe",                │          │
│  │     "mobileNumber": "+919876543210",     │          │
│  │     "emailId": "john@example.com"        │          │
│  │   }                                       │          │
│  │ }                                         │          │
│  └──────────────────────────────────────────┘          │
│                                                         │
│  Watch saves to EncryptedSharedPreferences             │
│  Navigate to home screen                               │
└─────────────────────────────────────────────────────────┘
```

### 3. Live Match Scoring Flow

```
┌─────────────────────────────────────────────────────────┐
│  Check for Active Match                                │
│  GET /api/watch/active-match                           │
└────────────────┬────────────────────────────────────────┘
                 │
      ┌──────────┴──────────┐
      │                     │
      ▼                     ▼
  Match Found          No Match Found
      │                     │
      ▼                     ▼
┌──────────────┐    ┌────────────────┐
│  Display     │    │  Show "No      │
│  Match       │    │  Active Match" │
│  Screen      │    │  Message       │
└──────┬───────┘    └────────────────┘
       │                     │
       │                     │
       │                Poll every 10s
       │                     │
       ▼                     │
┌──────────────────┐         │
│  Connect to      │         │
│  WebSocket       │         │
│  /topic/match/   │         │
│  {matchId}       │         │
└──────┬───────────┘         │
       │                     │
       │                     │
       ▼                     │
┌──────────────────┐         │
│  Display:        │         │
│  • Teams         │         │
│  • Current Score │         │
│  • Sets Won      │         │
│  • Tournament    │         │
└──────┬───────────┘         │
       │                     │
       │                     │
   User Taps Team            │
       │                     │
       ▼                     │
┌──────────────────┐         │
│  Singles or      │         │
│  Doubles?        │         │
└──────┬───────────┘         │
       │                     │
  ┌────┴────┐                │
  │         │                │
Singles  Doubles             │
  │         │                │
  │         ▼                │
  │  ┌──────────────┐        │
  │  │  Show Player │        │
  │  │  Selection   │        │
  │  │  Screen      │        │
  │  └──────┬───────┘        │
  │         │                │
  │    Select Player         │
  │         │                │
  └────┬────┘                │
       │                     │
       ▼                     │
┌──────────────────┐         │
│  POST /api/      │         │
│  match/{id}/     │         │
│  add-point       │         │
│  {                        │
│    teamId: 123,  │         │
│    playerId: 456 │         │
│  }               │         │
└──────┬───────────┘         │
       │                     │
       ▼                     │
┌──────────────────┐         │
│  Update Score    │         │
│  Locally         │         │
└──────┬───────────┘         │
       │                     │
       ▼                     │
┌──────────────────┐         │
│  WebSocket       │         │
│  Broadcasts      │         │
│  Update to All   │         │
│  Connected       │         │
│  Clients         │         │
└──────┬───────────┘         │
       │                     │
       │                     │
   Match Complete?           │
       │                     │
  ┌────┴────┐                │
  │         │                │
 Yes        No               │
  │         │                │
  │         └────────────────┘
  │
  ▼
┌──────────────────┐
│  Show Winner     │
│  Screen          │
│  • Winner Name   │
│  • Final Score   │
│  • Trophy Icon   │
└──────────────────┘
```

---

## 💻 Technical Stack

### Core Technologies

| Component | Technology | Version |
|-----------|-----------|---------|
| **Language** | Kotlin | 1.9+ |
| **UI Framework** | Jetpack Compose for Wear OS | 1.3+ |
| **Build System** | Gradle (Kotlin DSL) | 8.5 |
| **Min SDK** | Android API 30 | Wear OS 4.0 |
| **Target SDK** | Android API 35 | Android 15 |

### Key Dependencies

```kotlin
dependencies {
    // Wear OS Compose
    implementation("androidx.wear.compose:compose-material:1.3+")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose")
    
    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // QR Code Generation
    implementation("com.google.zxing:core:3.5.2")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Security - Encrypted Storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // JSON Parsing
    implementation("org.json:json:20231013")
    
    // Wear OS Services
    implementation("com.google.android.gms:play-services-wearable")
    implementation("androidx.wear:wear:1.3.0")
}
```

### Permissions Required

```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.INTERNET" />
```

### Network Security Configuration

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">34.131.53.32</domain>
    </domain-config>
</network-security-config>
```

---

## 📱 Application Screens

### Screen 1: QR Login Screen

**Purpose:** Display QR code for authentication via mobile app

**UI Components:**
```
┌─────────────────────────────┐
│      GRYPX Watch Login      │
│                             │
│     ┌───────────────┐       │
│     │               │       │
│     │   [QR CODE]   │       │
│     │               │       │
│     └───────────────┘       │
│          GRYPX              │
│                             │
│  "Scan with GRYPX app"      │
└─────────────────────────────┘
```

**Color Scheme:**
- Background: `#174D42` (Dark teal)
- QR Border: `#FFFFFF` (White)
- QR Dots: `#FFFFFF` (White)
- Text: `#FFFFFF` (White)

**Key Features:**
- ✅ High-resolution QR (500x500px)
- ✅ Rounded corner design
- ✅ Instagram-style position markers
- ✅ Real-time status updates
- ✅ Auto-refresh on expiry

**State Management:**
```kotlin
var sessionToken by remember { mutableStateOf("") }
var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
var statusMessage by remember { mutableStateOf("Connecting...") }
var isPolling by remember { mutableStateOf(false) }
```

---

### Screen 2: No Active Match Screen

**Purpose:** Inform user no match is currently live

**UI Components:**
```
┌─────────────────────────────┐
│   Hi, John! 👋              │
│                             │
│                             │
│   No active match           │
│                             │
│   Start a match in the      │
│   GRYPX mobile app          │
│                             │
│   ⟳ Tap to refresh          │
└─────────────────────────────┘
```

**Color Scheme:**
- Background: `#0F2A2E` (Dark blue-teal)
- Greeting: `#6FE7C8` (Mint green)
- Body Text: `#FFFFFF` (White)
- Subtitle: `#9BA3A0` (Gray)
- Refresh Hint: `#8EF1D6` (Light mint)

**Key Features:**
- ✅ Personalized greeting
- ✅ Clear instructions
- ✅ Tap-to-refresh interaction
- ✅ Auto-polling every 10 seconds

---

### Screen 3: Live Match Scoring Screen

**Purpose:** Display live match and allow tap-to-score

**UI Components:**
```
┌─────────────────────────────┐
│   Delhi Tournament          │
│   Match 2 • Set 2           │
│                             │
│  ┌──────┐   21   ┌──────┐  │
│  │ 🛡️  │   -    │ 🛡️  │  │
│  │ NN   │   18   │ SC   │  │
│  └──────┘        └──────┘  │
│   Net             Street    │
│   Ninjas          Champs    │
│                             │
│  Last: Rahul +1             │
│                             │
│  ┌─────────────────────┐   │
│  │   [UNDO]   [MENU]   │   │
│  └─────────────────────┘   │
│                             │
│  ● Connected • Set 2/3      │
└─────────────────────────────┘
```

**Color Scheme:**
- Background: `#0F2A2E`
- Tournament Name: `#8EF1D6`
- Match Info: `#FFFFFF`
- Score: `#6FE7C8` (Large, bold)
- Team Names: `#FFFFFF`
- Status Indicator: `#2EE6A6` (Connected) / `#FF6B6B` (Disconnected)

**Interactive Elements:**
1. **Team Cards** - Tap to add point
   - Shows team name & icon
   - Circular border indicates tappable
   - Highlights on tap

2. **Score Display** - Central, large, bold
   - Format: `{team1Score} - {team2Score}`
   - Updates in real-time via WebSocket

3. **Last Scorer** - Shows who scored last
   - Displays for 3 seconds after point
   - Format: "Last: {playerName} +1"

4. **UNDO Button** - Remove last point
   - Confirmation dialog before undo
   - Only available if points exist

5. **Connection Indicator** - WebSocket status
   - ● Green = Connected
   - ● Red = Disconnected
   - Falls back to polling if disconnected

**State Management:**
```kotlin
var team1Score by remember { mutableStateOf(0) }
var team2Score by remember { mutableStateOf(0) }
var currentSet by remember { mutableStateOf(1) }
var team1SetsWon by remember { mutableStateOf(0) }
var team2SetsWon by remember { mutableStateOf(0) }
var wsConnected by remember { mutableStateOf(false) }
var showMatchEnded by remember { mutableStateOf(false) }
```

---

### Screen 4: Player Selection Screen (Doubles)

**Purpose:** Select which player scored in doubles matches

**UI Components:**
```
┌─────────────────────────────┐
│      Net Ninjas             │
│                             │
│                             │
│  ┌────────────────────┐     │
│  │   Rahul Sharma     │     │
│  └────────────────────┘     │
│                             │
│  ┌────────────────────┐     │
│  │   Rohit Jain       │     │
│  └────────────────────┘     │
│                             │
│                             │
│      [Back]                 │
└─────────────────────────────┘
```

**Color Scheme:**
- Background: `#0F2A2E`
- Team Name: `#8EF1D6`
- Player Buttons: Transparent with `#8EF1D6` border
- Text: `#FFFFFF`

**Key Features:**
- ✅ Only shown for doubles matches
- ✅ Tapping player registers point for them
- ✅ Back button returns to match screen
- ✅ Auto-closes after selection

---

### Screen 5: Match Completed Screen

**Purpose:** Show match winner and final score

**UI Components:**
```
┌─────────────────────────────┐
│        Match Over! 🏆       │
│                             │
│      Net Ninjas Won!        │
│                             │
│       Final Score:          │
│       21-18, 21-19          │
│                             │
│                             │
│      [View Details]         │
│         [Home]              │
└─────────────────────────────┘
```

**Color Scheme:**
- Background: `#0F2A2E`
- Trophy: `#FFD700` (Gold)
- Winner Text: `#6FE7C8`
- Score: `#FFFFFF`
- Buttons: `#2EE6A6`

---

## 🌐 Network Layer

### REST API Client (`WatchApiClient.kt`)

**Base URL:** `http://34.131.53.32:8080/api`

#### Methods:

##### 1. Create Watch Session
```kotlin
suspend fun createWatchSession(
    deviceId: String,
    deviceModel: String,
    osVersion: String
): SessionResponse
```

**Endpoint:** `POST /api/auth/watch/session`

**Request Body:**
```json
{
  "deviceId": "ff2ea3711bf0be1e",
  "deviceModel": "Galaxy Watch 6",
  "osVersion": "Wear OS 5.0"
}
```

**Response:**
```json
{
  "success": true,
  "sessionToken": "wch_BYXTjp0QhEqd7CkICfE51ErPlKXXjFWX",
  "expiresAt": "2026-02-05T10:30:00.000Z",
  "qrData": {
    "sessionToken": "wch_BYXTjp0QhEqd7CkICfE51ErPlKXXjFWX",
    "deviceId": "ff2ea3711bf0be1e",
    "timestamp": 1707134400000
  }
}
```

---

##### 2. Check Session Status
```kotlin
suspend fun checkSessionStatus(
    sessionToken: String
): SessionStatusResponse
```

**Endpoint:** `GET /api/auth/watch/session/{sessionToken}/status`

**Response (Pending):**
```json
{
  "status": "PENDING",
  "authenticated": false,
  "message": "Waiting for authentication"
}
```

**Response (Authenticated):**
```json
{
  "status": "AUTHENTICATED",
  "authenticated": true,
  "authToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": 12345,
  "user": {
    "id": 12345,
    "name": "John Doe",
    "username": "johndoe",
    "mobileNumber": "+919876543210",
    "emailId": "john@example.com",
    "profileImage": "https://..."
  }
}
```

**Error Codes:**
- `200` - Success (PENDING or AUTHENTICATED)
- `404` - Session not found
- `410` - Session expired

---

### Match Service (`WatchMatchService.kt`)

**Authentication:** Bearer token required

#### Methods:

##### 1. Get Active Match
```kotlin
suspend fun getActiveMatch(): ActiveMatchResponse?
```

**Endpoint:** `GET /api/watch/active-match`

**Headers:**
```
Authorization: Bearer {authToken}
Content-Type: application/json
```

**Response:**
```json
{
  "matchId": 12345,
  "tournamentId": 789,
  "tournamentName": "Delhi Tournament",
  "team1Id": 101,
  "team1Name": "Net Ninjas",
  "team1PlayerId": 1001,
  "team1Players": [
    {
      "id": 1001,
      "name": "Rahul Sharma",
      "username": "rahul_s"
    }
  ],
  "team2Id": 102,
  "team2Name": "Street Champs",
  "team2PlayerId": 1002,
  "team2Players": [
    {
      "id": 1002,
      "name": "Priya Kumar",
      "username": "priya_k"
    }
  ],
  "team1Score": 21,
  "team2Score": 18,
  "currentSet": 2,
  "team1SetsWon": 1,
  "team2SetsWon": 0,
  "sport": "Badminton",
  "matchFormat": "singles",
  "status": "LIVE",
  "setHistory": [
    {
      "setNumber": 1,
      "team1Score": 21,
      "team2Score": 19
    }
  ],
  "lastScorer": {
    "id": 1001,
    "name": "Rahul Sharma",
    "username": "rahul_s"
  }
}
```

**Returns:** `null` if no active match or not a racket sport

---

##### 2. Add Point
```kotlin
suspend fun addPoint(
    matchId: Long,
    teamId: Long,
    playerId: Long,
    method: String = "ACE"
): AddPointResponse
```

**Endpoint:** `POST /api/match/{matchId}/add-point`

**Request Body:**
```json
{
  "teamId": 101,
  "playerId": 1001,
  "method": "ACE"
}
```

**Response:**
```json
{
  "success": true,
  "team1Score": 22,
  "team2Score": 18,
  "currentSet": 2,
  "team1SetsWon": 1,
  "team2SetsWon": 0,
  "matchCompleted": false,
  "setCompleted": false,
  "winnerId": null,
  "winnerName": null,
  "errorMessage": null
}
```

**Response (Match Complete):**
```json
{
  "success": true,
  "team1Score": 21,
  "team2Score": 15,
  "currentSet": 2,
  "team1SetsWon": 2,
  "team2SetsWon": 0,
  "matchCompleted": true,
  "setCompleted": true,
  "winnerId": 101,
  "winnerName": "Net Ninjas",
  "errorMessage": null
}
```

---

##### 3. Get Match Score
```kotlin
suspend fun getMatchScore(matchId: Long): MatchScoreResponse?
```

**Endpoint:** `GET /api/match/{matchId}/score`

**Used as fallback when WebSocket disconnected**

---

##### 4. Undo Last Point
```kotlin
suspend fun undoLastPoint(matchId: Long): UndoResponse
```

**Endpoint:** `POST /api/match/{matchId}/undo`

**Response:**
```json
{
  "success": true,
  "team1Score": 21,
  "team2Score": 18,
  "currentSet": 2,
  "message": "Last point removed"
}
```

---

## 🔌 Real-time Updates

### WebSocket Service (`WatchWebSocketService.kt`)

**URL:** `ws://34.131.53.32:8080/ws/websocket`  
**Protocol:** STOMP over WebSocket

#### Connection Flow

```
1. Connect to WebSocket
   ws://34.131.53.32:8080/ws/websocket

2. Send STOMP CONNECT frame
   CONNECT
   accept-version:1.1,1.2
   heart-beat:10000,10000

3. Receive CONNECTED frame
   CONNECTED
   version:1.2
   heart-beat:10000,10000

4. Subscribe to match topic
   SUBSCRIBE
   id:sub-watch-{matchId}
   destination:/topic/match/{matchId}

5. Receive real-time events
   MESSAGE
   destination:/topic/match/{matchId}
   {JSON payload}
```

#### Event Types

##### 1. Score Update Event
```json
{
  "type": "SCORE_UPDATE",
  "data": {
    "team1Score": 21,
    "team2Score": 18,
    "currentSet": 2,
    "team1SetsWon": 1,
    "team2SetsWon": 0
  }
}
```

##### 2. Set Completed Event
```json
{
  "type": "SET_COMPLETED",
  "data": {
    "setNumber": 1,
    "team1Score": 21,
    "team2Score": 19,
    "winnerTeamId": 101
  }
}
```

##### 3. Match End Event
```json
{
  "type": "MATCH_COMPLETED",
  "data": {
    "winnerId": 101,
    "winnerName": "Net Ninjas",
    "finalScore": "21-19, 21-18"
  }
}
```

##### 4. Undo Event
```json
{
  "type": "UNDO",
  "data": {
    "team1Score": 20,
    "team2Score": 18,
    "currentSet": 2
  }
}
```

#### State Flows

```kotlin
// Connection state
val connectionState: StateFlow<ConnectionState>
// Values: DISCONNECTED, CONNECTING, CONNECTED, ERROR

// Score updates
val scoreUpdate: StateFlow<ScoreUpdateEvent?>

// Match end events
val matchEnded: StateFlow<MatchEndEvent?>

// Set completion events
val setCompleted: StateFlow<SetCompletedEvent?>
```

#### Fallback Mechanism

If WebSocket disconnects:
1. Set `wsConnected = false`
2. Display red indicator
3. Poll REST API every 5 seconds for score updates
4. Attempt WebSocket reconnection after 5 seconds

```kotlin
// Fallback polling
LaunchedEffect(wsConnected) {
    while (!wsConnected) {
        delay(5000)
        val score = matchService.getMatchScore(matchId)
        // Update local state
    }
}
```

---

## 🔒 Security

### Secure Storage (`SecureStorage.kt`)

**Implementation:** `EncryptedSharedPreferences` with AES-256

#### Encryption Details

```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val sharedPreferences = EncryptedSharedPreferences.create(
    context,
    "grypx_watch_secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

#### Stored Data

| Key | Type | Description |
|-----|------|-------------|
| `auth_token` | String | JWT authentication token |
| `user_id` | Int | User ID |
| `user_name` | String | Display name |
| `username` | String | Username |
| `mobile_number` | String | Phone number |
| `email_id` | String | Email address |
| `profile_image` | String | Profile image URL |

#### Methods

```kotlin
// Save authentication
fun saveAuthToken(token: String)
fun saveUserData(userId: Int, userName: String, ...)

// Retrieve data
fun getAuthToken(): String?
fun getUserName(): String?
fun isAuthenticated(): Boolean

// Clear data (logout)
fun clear()
```

### Network Security

- ✅ HTTPS preferred (falls back to HTTP for testing)
- ✅ Cleartext traffic only allowed for `34.131.53.32`
- ✅ Certificate pinning recommended for production
- ✅ JWT tokens transmitted in `Authorization` header
- ✅ Session tokens expire after 5 minutes

---

## 🎨 UI/UX Components

### Color Palette

```kotlin
object GrypxColors {
    val DarkBackground = Color(0xFF0F2A2E)      // Primary background
    val DarkTeal = Color(0xFF174D42)            // QR background
    val MintGreen = Color(0xFF6FE7C8)           // Primary accent
    val LightMint = Color(0xFF8EF1D6)           // Secondary accent
    val BrightMint = Color(0xFF2EE6A6)          // Buttons
    val White = Color(0xFFFFFFFF)               // Text
    val LightGray = Color(0xFF9BA3A0)           // Subtitles
    val ErrorRed = Color(0xFFFF6B6B)            // Errors
    val SuccessGreen = Color(0xFF4CAF50)        // Success
}
```

### Typography

```kotlin
Typography(
    displayLarge = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    ),
    displayMedium = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = Color.White
    ),
    bodyMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = Color(0xFF9BA3A0)
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        color = Color(0xFF9BA3A0)
    )
)
```

### Component Library

#### 1. Team Card
```kotlin
@Composable
fun TeamCard(
    teamName: String,
    teamIcon: String,
    onTap: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onTap() }
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0xFF6FE7C8), CircleShape)
                .background(Color(0xFF122F33))
        ) {
            Text(text = teamIcon, fontSize = 16.sp)
        }
        Text(text = teamName, fontSize = 10.sp)
    }
}
```

#### 2. Score Display
```kotlin
@Composable
fun ScoreDisplay(score1: Int, score2: Int) {
    Text(
        text = "$score1-$score2",
        color = Color(0xFF6FE7C8),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    )
}
```

#### 3. Connection Indicator
```kotlin
@Composable
fun ConnectionIndicator(connected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (connected) Color(0xFF2EE6A6) 
                    else Color(0xFFFF6B6B)
                )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (connected) "Connected" else "Offline",
            fontSize = 9.sp,
            color = Color(0xFF9BA3A0)
        )
    }
}
```

---

## 🔧 Build & Deployment

### Prerequisites

- **Android Studio:** Electric Eel or newer
- **JDK:** 11 or higher
- **Gradle:** 8.5+
- **Physical Watch:** Galaxy Watch 6 or compatible Wear OS device
- **ADB:** Android Debug Bridge installed

### Build Commands

#### Debug Build
```powershell
cd "d:\ASELEA Work\BRYPX\GrypX Frantnd\Grpyx_andd_watch"
.\gradlew assembleDebug
```

**Output:** `app/build/outputs/apk/debug/app-debug.apk`

#### Release Build
```powershell
.\gradlew assembleRelease
```

**Output:** `app/build/outputs/apk/release/app-release.apk`

### Installation

```powershell
# Install on connected watch
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Launch app
adb shell am start -n com.example.grpyx_andd_watch/.presentation.MainActivity

# View logs
adb logcat -c && adb logcat | findstr "QRLogin|MatchService|WebSocket"
```

### Testing

#### 1. Test QR Login
```powershell
# Monitor logs
adb logcat | findstr "QRLogin"

# Look for:
# "Session created: wch_..."
# "QR code generated successfully"
# "Authentication successful!"
```

#### 2. Test Match Scoring
```powershell
# Create test match via mobile app
# Watch should auto-detect within 10 seconds

# Monitor logs
adb logcat | findstr "AuthenticatedHome|LiveMatch"
```

#### 3. Test WebSocket
```powershell
# Monitor connection
adb logcat | findstr "WebSocket"

# Look for:
# "WebSocket opened"
# "STOMP connected"
# "Subscribing to /topic/match/{id}"
# "Score updated: X - Y"
```

### Performance Optimization

#### Battery Life
- WebSocket ping interval: 25 seconds
- Polling interval (fallback): 5 seconds
- Match check interval: 10 seconds
- Screen timeout: System default

#### Memory Management
- Bitmap caching for QR codes
- Coroutine scope cleanup on screen exit
- WebSocket disconnect on background

#### Network Efficiency
- Connection pooling via OkHttp
- Gzip compression enabled
- 30-second timeouts
- Automatic retry with exponential backoff

---

## 📊 Data Models

### Session Models

```kotlin
data class SessionResponse(
    val success: Boolean,
    val sessionToken: String,
    val expiresAt: String,
    val qrData: JSONObject
)

data class SessionStatusResponse(
    val status: String,              // PENDING, AUTHENTICATED, EXPIRED
    val authenticated: Boolean,
    val message: String?,
    val authToken: String?,
    val userId: Long?,
    val user: UserData?
)

data class UserData(
    val id: Long,
    val name: String,
    val username: String,
    val mobileNumber: String?,
    val emailId: String?,
    val profileImage: String?
)
```

### Match Models

```kotlin
data class ActiveMatchResponse(
    val matchId: Long,
    val tournamentId: Long,
    val tournamentName: String,
    val team1Id: Long,
    val team1Name: String,
    val team1PlayerId: Long,
    val team1Players: List<PlayerInfo>,
    val team2Id: Long,
    val team2Name: String,
    val team2PlayerId: Long,
    val team2Players: List<PlayerInfo>,
    val team1Score: Int,
    val team2Score: Int,
    val currentSet: Int,
    val team1SetsWon: Int,
    val team2SetsWon: Int,
    val sport: String,
    val matchFormat: String,         // singles, doubles, team
    val status: String,              // LIVE, COMPLETED
    val setHistory: List<SetScore>,
    val lastScorer: PlayerInfo?
)

data class PlayerInfo(
    val id: Long,
    val name: String,
    val username: String
)

data class SetScore(
    val setNumber: Int,
    val team1Score: Int,
    val team2Score: Int
)

data class AddPointResponse(
    val success: Boolean,
    val team1Score: Int,
    val team2Score: Int,
    val currentSet: Int,
    val team1SetsWon: Int,
    val team2SetsWon: Int,
    val matchCompleted: Boolean,
    val setCompleted: Boolean,
    val winnerId: Long?,
    val winnerName: String?,
    val errorMessage: String?
)
```

### WebSocket Models

```kotlin
data class ScoreUpdateEvent(
    val team1Score: Int,
    val team2Score: Int,
    val currentSet: Int,
    val team1SetsWon: Int,
    val team2SetsWon: Int
)

data class MatchEndEvent(
    val winnerId: Long,
    val winnerName: String,
    val finalScore: String
)

data class SetCompletedEvent(
    val setNumber: Int,
    val winnerTeamId: Long
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
```

---

## 📈 Error Handling

### Network Errors

| Error | Cause | Solution |
|-------|-------|----------|
| `SocketException: EPERM` | Missing INTERNET permission | ✅ Fixed in AndroidManifest.xml |
| `CLEARTEXT not permitted` | HTTP blocked | ✅ Fixed in network_security_config.xml |
| `Connection timeout` | Backend unreachable | Check backend status, verify URL |
| `410 Gone` | Session expired | Restart app to create new session |
| `404 Not Found` | Session not found | Create new session |
| `401 Unauthorized` | Invalid auth token | Re-authenticate via QR login |

### WebSocket Errors

| Error | Cause | Solution |
|-------|-------|----------|
| Connection failed | Backend WebSocket down | Fall back to REST polling |
| STOMP error | Invalid subscription | Reconnect and resubscribe |
| Timeout | Network unstable | Automatic reconnection after 5s |
| Message parse error | Invalid JSON | Log error, continue listening |

### UI Error States

```kotlin
// Loading state
if (isLoading) {
    CircularProgressIndicator()
}

// Error state
errorMessage?.let { error ->
    Text(
        text = error,
        color = Color(0xFFFF6B6B),
        fontSize = 10.sp
    )
}

// Empty state
if (activeMatch == null) {
    Text(text = "No active match")
}
```

---

## 🧪 Testing Guide

### Manual Testing Checklist

#### QR Login
- [ ] QR code displays within 2 seconds
- [ ] QR code is scannable (test with mobile app)
- [ ] Status updates show "Scan with GRYPX app"
- [ ] Authentication completes after mobile scan
- [ ] User redirected to home screen
- [ ] Auth token saved in secure storage

#### Match Detection
- [ ] "No Active Match" shows when no match exists
- [ ] Tap to refresh works
- [ ] Auto-polling detects new match within 10 seconds
- [ ] Only racket sports are detected
- [ ] Football matches are ignored

#### Live Scoring
- [ ] Match screen loads with correct team names
- [ ] Current score displays accurately
- [ ] Tapping team adds point for singles
- [ ] Player selection shows for doubles
- [ ] WebSocket connection indicator shows green
- [ ] Score updates in real-time
- [ ] UNDO removes last point
- [ ] Match completion shows winner

#### Real-time Sync
- [ ] WebSocket connects successfully
- [ ] Score updates received within 1 second
- [ ] Fallback polling works when disconnected
- [ ] Multiple devices sync correctly

#### Edge Cases
- [ ] Session expiry handled gracefully
- [ ] App restart preserves auth
- [ ] Background/foreground transitions work
- [ ] Low battery doesn't crash app
- [ ] Network loss doesn't break UI

---

## 📚 API Reference

### Base Configuration

```kotlin
BASE_URL = "http://34.131.53.32:8080/api"
WS_URL = "ws://34.131.53.32:8080/ws/websocket"
```

### Endpoints Summary

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| POST | `/auth/watch/session` | No | Create QR session |
| GET | `/auth/watch/session/{token}/status` | No | Poll auth status |
| GET | `/watch/active-match` | Yes | Get active match |
| GET | `/match/{id}` | Yes | Get match details |
| GET | `/match/{id}/score` | Yes | Get current score |
| POST | `/match/{id}/add-point` | Yes | Add point |
| POST | `/match/{id}/undo` | Yes | Undo last point |

### WebSocket Topics

| Topic | Purpose |
|-------|---------|
| `/topic/match/{matchId}` | Match events |
| `/user/queue/reply` | Personal messages |

---

## 🚀 Future Enhancements

### Planned Features
- 📊 **Match History** - View past matches on watch
- 🏆 **Tournament Bracket** - Live bracket updates
- 📈 **Player Stats** - Quick stats view
- ⚡ **Offline Mode** - Queue actions when offline
- 🎙️ **Voice Scoring** - "Add point Team A"
- 📣 **Haptic Feedback** - Vibrate on point scored
- 🌍 **Multi-language** - Support 10+ languages
- 🔔 **Match Notifications** - Alert when match starts

### Technical Improvements
- 🔐 **HTTPS Migration** - Full TLS encryption
- 📦 **Modular Architecture** - Feature modules
- 🧪 **Unit Tests** - 80% code coverage
- 🎨 **Custom Complications** - Live score widget
- 🧭 **Navigation** - Multi-screen navigation
- 💾 **Local Database** - Room for offline data

---

## 👥 Support & Troubleshooting

### Common Issues

#### 1. QR Not Scanning
**Symptoms:** Mobile app can't scan QR  
**Solutions:**
- Ensure watch screen is at full brightness
- Hold phone steady for 2 seconds
- Verify mobile app has camera permission
- Regenerate QR by restarting watch app

#### 2. Authentication Fails
**Symptoms:** Polling never completes  
**Solutions:**
- Check backend is running (http://34.131.53.32:8080)
- Verify mobile app has valid JWT token
- Ensure session hasn't expired (5 min limit)
- Check watch logs for error messages

#### 3. No Match Detected
**Symptoms:** Always shows "No Active Match"  
**Solutions:**
- Verify match is LIVE status in backend
- Ensure match is racket sport (not football)
- Check user is participant in match
- Refresh manually by tapping screen

#### 4. WebSocket Disconnects
**Symptoms:** Red indicator, no real-time updates  
**Solutions:**
- Check network connectivity
- Backend WebSocket server may be down
- App will auto-reconnect after 5 seconds
- Fallback REST polling continues to work

### Debug Logging

```powershell
# All watch app logs
adb logcat | findstr "grpyx"

# QR Login specific
adb logcat | findstr "QRLogin"

# Match service logs
adb logcat | findstr "MatchService|AuthenticatedHome"

# WebSocket logs
adb logcat | findstr "WebSocket|STOMP"

# Network calls
adb logcat | findstr "OkHttp"
```

---

## 📞 Contact & Resources

### Development Team
- **Project:** GRYPX Sports Platform
- **Component:** Wear OS Watch App
- **Backend:** http://34.131.53.32:8080
- **Mobile App:** Flutter (GRYPX)

### Documentation Files
- `WATCH_APP_INTEGRATION_COMPLETE.md` - Integration summary
- `BACKEND_REQUIREMENTS_WATCH_QR_LOGIN.md` - API specs
- `QUICK_TEST_GUIDE.md` - Quick testing guide
- `COMPLETE_WATCH_APP_DOCUMENTATION.md` - This file

### Resources
- [Wear OS Developer Guide](https://developer.android.com/training/wearables)
- [Jetpack Compose for Wear OS](https://developer.android.com/jetpack/compose/wear)
- [OkHttp Documentation](https://square.github.io/okhttp/)
- [STOMP Protocol Spec](https://stomp.github.io/)

---

## 📝 Changelog

### Version 1.0 (January 2026)
- ✅ QR login authentication
- ✅ Live match scoring for racket sports
- ✅ WebSocket real-time updates
- ✅ Player selection for doubles
- ✅ Encrypted token storage
- ✅ Match completion handling
- ✅ Undo functionality
- ✅ Fallback REST polling

---

## 📄 License

© 2026 GRYPX. All rights reserved.

---

**End of Documentation**

*Last Updated: February 5, 2026*  
*Document Version: 1.0*  
*Author: GRYPX Development Team*
