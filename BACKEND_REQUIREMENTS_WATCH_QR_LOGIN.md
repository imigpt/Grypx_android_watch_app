# Backend Requirements: Watch QR Login API

## Issue Summary
The GRYPX Watch app is unable to complete QR login because the required backend API endpoints are missing.

**Error from Watch App:**
```
java.net.SocketException: socket failed: EPERM (Operation not permitted)
```

This error occurred because:
1. ✅ **FIXED** - Watch app was missing INTERNET permission (frontend issue - now resolved)
2. ❌ **PENDING** - Backend does not have watch authentication endpoints (backend issue)

---

## Required Backend Endpoints

### 1. Create Watch Session Endpoint

**Endpoint:** `POST /api/auth/watch/session`

**Purpose:** Creates a new watch authentication session and generates a session token for QR code display.

**Request Body:**
```json
{
  "deviceId": "ff2ea3711bf0be1e",
  "deviceName": "Galaxy Watch 5",
  "deviceType": "WEAR_OS",
  "appVersion": "1.0.0"
}
```

**Expected Response (Success - 200 OK):**
```json
{
  "success": true,
  "sessionToken": "wch_abc123xyz789",
  "expiresAt": "2026-01-20T21:15:24.529Z",
  "qrData": {
    "sessionToken": "wch_abc123xyz789",
    "deviceId": "ff2ea3711bf0be1e",
    "timestamp": 1737409524529
  }
}
```

**Expected Response (Error - 400/500):**
```json
{
  "success": false,
  "message": "Error message here"
}
```

**Business Logic:**
1. Validate device information
2. Generate unique session token (prefix: `wch_`)
3. Store session with status: `PENDING`
4. Set expiration time (recommended: 5 minutes)
5. Return session token and QR data
6. Session should be stored in database/cache for polling

---

### 2. Check Session Status Endpoint

**Endpoint:** `GET /api/auth/watch/session/{sessionToken}/status`

**Purpose:** Polling endpoint for watch to check if user has scanned QR and authenticated.

**Path Parameter:**
- `sessionToken` - The session token generated in step 1

**Expected Response (Pending - 200 OK):**
```json
{
  "status": "PENDING",
  "authenticated": false,
  "message": "Waiting for authentication"
}
```

**Expected Response (Authenticated - 200 OK):**
```json
{
  "status": "AUTHENTICATED",
  "authenticated": true,
  "userId": 12345,
  "authToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
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

**Expected Response (Expired/Invalid - 404/410):**
```json
{
  "status": "EXPIRED",
  "authenticated": false,
  "message": "Session expired or invalid"
}
```

**Business Logic:**
1. Validate session token exists
2. Check if session is expired
3. Check if session has been authenticated by mobile app
4. Return appropriate status
5. If authenticated, return user info and JWT token
6. Watch app will poll this endpoint every 2-3 seconds

---

### 3. Mobile App Endpoint to Authenticate Watch Session

**Endpoint:** `POST /api/auth/watch/session/{sessionToken}/authenticate`

**Purpose:** Called by mobile app when user scans QR code to authenticate the watch.

**Path Parameter:**
- `sessionToken` - The session token from QR code

**Request Headers:**
```
Authorization: Bearer <user_jwt_token>
```

**Request Body:**
```json
{
  "deviceId": "ff2ea3711bf0be1e"
}
```

**Expected Response (Success - 200 OK):**
```json
{
  "success": true,
  "message": "Watch authenticated successfully",
  "sessionToken": "wch_abc123xyz789"
}
```

**Expected Response (Error):**
```json
{
  "success": false,
  "message": "Invalid session or expired"
}
```

**Business Logic:**
1. Validate user's JWT token from Authorization header
2. Validate session token exists and not expired
3. Update session status to `AUTHENTICATED`
4. Store user ID in session
5. Generate JWT token for watch device
6. Return success
7. Watch will receive auth info on next status poll

---

## Database Schema Recommendation

**Table: `watch_sessions`**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT | Primary key |
| `session_token` | VARCHAR(100) | Unique session token |
| `device_id` | VARCHAR(100) | Watch device ID |
| `device_name` | VARCHAR(100) | Watch device name |
| `device_type` | VARCHAR(50) | Device type (WEAR_OS, etc) |
| `app_version` | VARCHAR(20) | Watch app version |
| `status` | ENUM | PENDING, AUTHENTICATED, EXPIRED |
| `user_id` | BIGINT | User ID (null until authenticated) |
| `auth_token` | TEXT | JWT token for watch (null until authenticated) |
| `created_at` | TIMESTAMP | Session creation time |
| `expires_at` | TIMESTAMP | Session expiration time |
| `authenticated_at` | TIMESTAMP | When user authenticated (nullable) |

**Indexes:**
- Unique index on `session_token`
- Index on `device_id`
- Index on `status`
- Index on `expires_at` (for cleanup)

---

## Session Flow

```
Watch App                         Backend                    Mobile App
    |                                |                              |
    |--1. POST /watch/session------->|                              |
    |<--session_token----------------|                              |
    |                                |                              |
    |--2. GET /watch/session/status->|                              |
    |<--PENDING----------------------|                              |
    |                                |                              |
    | (Display QR Code)              |                              |
    |                                |                              |
    |                                |<--3. User scans QR-----------|
    |                                |<--POST /watch/session/auth---|
    |                                |   (with JWT token)           |
    |                                |--Success-------------------->|
    |                                |                              |
    |--4. GET /watch/session/status->|                              |
    |<--AUTHENTICATED + JWT token----|                              |
    |                                |                              |
    | (Login complete)               |                              |
```

---

## Security Considerations

1. **Session Expiration**: Sessions should expire after 5 minutes
2. **One-Time Use**: Once authenticated, session should be marked as used
3. **Rate Limiting**: Limit status polling to prevent abuse (max 1 req/second)
4. **Token Format**: Use cryptographically secure random tokens
5. **HTTPS Required**: In production, use HTTPS (currently using HTTP for development)
6. **Device Validation**: Validate device ID matches in authenticate request

---

## Current Backend File Location

**Controller to Modify:**
`Grypx_backend/src/main/java/com/streetsportslive/sportsapp/controller/AuthController.java`

**Reference:**
- Watch API Client: `Grpyx_andd_watch/app/src/main/java/com/example/grpyx_andd_watch/network/WatchApiClient.kt`
- Base URL: `http://34.131.53.32:8080/api`

---

## Testing Checklist

- [ ] POST /api/auth/watch/session creates session and returns token
- [ ] GET /api/auth/watch/session/{token}/status returns PENDING initially
- [ ] POST /api/auth/watch/session/{token}/authenticate updates session (mobile app)
- [ ] GET /api/auth/watch/session/{token}/status returns AUTHENTICATED after mobile auth
- [ ] Expired sessions return appropriate error
- [ ] Invalid tokens return 404
- [ ] JWT token generated for watch works for other API calls
- [ ] Session cleanup job removes expired sessions

---

## Priority: HIGH
**Impact:** Watch app cannot login without these endpoints.
**Estimated Effort:** 4-6 hours
**Dependencies:** None - only requires existing User and JWT infrastructure

---

**Frontend Status:** ✅ Fixed - INTERNET permission added to watch app
**Backend Status:** ❌ Pending - Endpoints need to be implemented
