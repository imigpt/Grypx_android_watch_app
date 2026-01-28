# Watch QR Login - Quick Test Guide

## 🚀 Quick Start (5 Steps)

### 1. Build Watch App
```powershell
cd "d:\ASELEA Work\BRYPX\GrypX Frantnd\Grpyx_andd_watch"
.\gradlew assembleDebug
```

### 2. Install on Watch
```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 3. Launch & Monitor
```powershell
# Terminal 1: Watch logs
adb logcat -c && adb logcat | findstr "QRLogin|Error"

# Terminal 2: Launch app
adb shell am start -n com.example.grpyx_andd_watch/.presentation.MainActivity
```

### 4. Verify QR Display
- ✅ Watch shows "GRYPX Watch" title
- ✅ QR code displays (white square with black pattern)
- ✅ Status: "Scan with GRYPX app"
- ✅ Logs show: "Session created: wch_..."

### 5. Test with Mobile App
- Open GRYPX mobile app
- Navigate to QR scanner
- Scan watch QR code
- Watch should auto-login within 3 seconds

---

## 📊 Expected Log Output

### Success Flow:
```
QRLogin: Creating watch session for device: ff2ea3711bf0be1e
QRLogin: Session created: wch_BYXTjp0QhEqd7CkICfE51ErPlKXXjFWX
QRLogin: Expires at: 2026-01-21T12:53:49.992693736Z
QRLogin: QR code generated successfully
QRLogin: Polling session status (attempt 1)...
QRLogin: Status: PENDING, Authenticated: false
QRLogin: Still pending... (poll #1)
QRLogin: Polling session status (attempt 2)...
QRLogin: Status: PENDING, Authenticated: false
QRLogin: Still pending... (poll #2)
[User scans QR with mobile app]
QRLogin: Polling session status (attempt 3)...
QRLogin: Status: AUTHENTICATED, Authenticated: true
QRLogin: ✅ Authentication successful!
QRLogin: User: User 175152 (GRYPX#017)
```

---

## ❌ Troubleshooting

### Issue: "CLEARTEXT communication not permitted"
**Status:** ✅ FIXED  
**Solution:** network_security_config.xml added

### Issue: "socket failed: EPERM"
**Status:** ✅ FIXED  
**Solution:** INTERNET permission added to AndroidManifest.xml

### Issue: "Session expired (410)"
**Cause:** Session older than 5 minutes  
**Solution:** Restart app to create new session

### Issue: QR code not displaying
**Check:**
```
# In logs, look for:
QRLogin: Error creating session
```
**Solution:** Verify backend is running at http://34.131.53.32:8080

### Issue: Polling never completes
**Check:**
```
# In logs, should see every 2.5 seconds:
QRLogin: Polling session status (attempt N)...
```
**Solution:** 
- Verify mobile app scanned correct QR
- Check mobile app has valid JWT token
- Test backend authenticate endpoint manually

---

## 🧪 Manual API Testing

### Test 1: Create Session
```powershell
curl -X POST http://34.131.53.32:8080/api/auth/watch/session `
  -H "Content-Type: application/json" `
  -d '{\"deviceId\":\"test-123\",\"deviceModel\":\"Galaxy Watch\",\"osVersion\":\"Wear OS 4.0\"}'
```

**Expected Response:**
```json
{
  "success": true,
  "sessionToken": "wch_...",
  "expiresAt": "2026-01-21T...",
  "qrData": { ... }
}
```

### Test 2: Check Status
```powershell
curl http://34.131.53.32:8080/api/auth/watch/session/wch_ABC123/status
```

**Expected Response (before scan):**
```json
{
  "status": "PENDING",
  "authenticated": false,
  "message": "Waiting for authentication"
}
```

---

## 📱 Mobile App Requirements

The mobile app MUST implement:

1. **QR Code Scanner** (ML Kit or CameraX)
2. **Parse QR Data**
   ```kotlin
   val qrData = JSONObject(scannedText)
   val sessionToken = qrData.getString("sessionToken")
   val deviceId = qrData.getString("deviceId")
   ```

3. **Authenticate Endpoint**
   ```kotlin
   POST /api/auth/watch/session/{sessionToken}/authenticate
   Header: Authorization: Bearer <user_jwt>
   Body: { "deviceId": "..." }
   ```

---

## ✅ Success Indicators

### Watch App
- [x] Compiles without errors
- [x] Requests INTERNET permission
- [x] Displays QR code
- [x] Polls backend every 2.5 seconds
- [x] Shows "Login successful!" after scan
- [x] Navigates to tournament screen
- [x] Stays logged in after app restart

### Backend
- [x] POST /api/auth/watch/session returns session token
- [x] GET /api/auth/watch/session/{token}/status returns PENDING
- [x] After mobile auth, returns AUTHENTICATED with user data
- [x] Sessions expire after 5 minutes

### Integration
- [ ] Watch QR displays correctly
- [ ] Mobile app can scan QR
- [ ] Mobile app authenticates session
- [ ] Watch receives authentication
- [ ] User data saved securely
- [ ] End-to-end flow works

---

## 📞 Quick Reference

**Backend:** http://34.131.53.32:8080  
**Session Duration:** 5 minutes  
**Poll Interval:** 2.5 seconds  
**Max Poll Attempts:** 60 (2.5 minutes total)

**Key Files:**
- `WatchApiClient.kt` - API calls
- `MainActivity.kt` - QR login UI
- `SecureStorage.kt` - Save credentials
- `AndroidManifest.xml` - Permissions

**ADB Commands:**
```powershell
# View logs
adb logcat | findstr QRLogin

# Clear and restart
adb shell pm clear com.example.grpyx_andd_watch
adb shell am start -n com.example.grpyx_andd_watch/.presentation.MainActivity

# Check permissions
adb shell dumpsys package com.example.grpyx_andd_watch | findstr permission
```

---

**Status:** ✅ Ready for Testing  
**Version:** 1.0  
**Date:** January 21, 2026
