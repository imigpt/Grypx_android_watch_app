# 🔑 GrypX Watch App Keystore Information

**Last Updated:** February 7, 2026  
**Document Version:** 1.0  
**Platform:** Android Wear OS

---

## ⌚ Watch App Keystore

### **Keystore File Details**

| Property | Value |
|----------|-------|
| **File Name** | `grypx-watch-release.keystore` |
| **Location** | `app/grypx-watch-release.keystore` |
| **Store Password** | `grypx123456` |
| **Key Password** | `grypx123456` |
| **Key Alias** | `grypx-watch` |
| **Store Type** | JKS (Java KeyStore) |

### **Certificate Information**

```
Owner:            CN=Grypx Mobile, OU=Development, O=Grypx, L=City, ST=State, C=US
Issuer:           CN=Grypx Mobile, OU=Development, O=Grypx, L=City, ST=State, C=US
Creation Date:    February 6, 2026
Expiry Date:      June 24, 2053 (27+ years validity)
Key Algorithm:    2048-bit RSA
Signature:        SHA256withRSA
Serial Number:    7bfddda680af53e1
```

### **Certificate Fingerprints**

#### SHA-1 (Used by Firebase, Google Play Console)
```
E7:49:63:61:EF:57:A1:98:A5:3E:D2:4F:C9:45:2F:48:5A:DC:F2:6E
```

#### SHA-256
```
6E:C6:51:1A:06:15:1B:84:8B:1A:BF:7E:D1:C5:17:EB:6F:D9:12:AF:EF:A9:FF:C8:42:DC:B9:44:2F:E5:CA:A7
```

---

## 📄 Configuration

**File:** `app/build.gradle.kts`

The keystore configuration is **hardcoded** directly in the build file:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("grypx-watch-release.keystore")
        storePassword = "grypx123456"
        keyAlias = "grypx-watch"
        keyPassword = "grypx123456"
    }
}

buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        signingConfig = signingConfigs.getByName("release")
    }
}
```

---

## 📱 Application Details

### Build Configuration

| Property | Value |
|----------|-------|
| **Package Name** | `com.grypxmobile.watch` |
| **Namespace** | `com.streetsports.grypxwatch` |
| **Min SDK** | 30 (Android 11 - Wear OS 3.0) |
| **Target SDK** | 35 (Android 15) |
| **Compile SDK** | 35 |
| **Version Code** | 5 |
| **Version Name** | 1.0.5 |

### Release Build Settings
- ✅ **ProGuard:** Enabled (minifyEnabled = true)
- ✅ **Resource Shrinking:** Enabled (isShrinkResources = true)
- ✅ **Signing:** Configured with release keystore
- ✅ **Optimization:** Full optimization enabled

---

## 🛠️ Usage

### Building Release APK
```bash
cd Grpyx_andd_watch
./gradlew assembleRelease
```

**Output Location:**  
`app/build/outputs/apk/release/app-release.apk`

### Building Release App Bundle (Play Store)
```bash
cd Grpyx_andd_watch
./gradlew bundleRelease
```

**Output Location:**  
`app/build/outputs/bundle/release/app-release.aab`

### Installing on Watch via ADB
```bash
adb -s <WATCH_DEVICE_ID> install app/build/outputs/apk/release/app-release.apk
```

### Extracting Keystore Information
```bash
keytool -list -v -keystore app/grypx-watch-release.keystore -alias grypx-watch -storepass grypx123456
```

### Extracting SHA-1 Fingerprint Only
```bash
keytool -list -v -keystore app/grypx-watch-release.keystore -alias grypx-watch -storepass grypx123456 | grep SHA1
```

---

## 🔐 Security Guidelines

### ✅ DO:
- ✅ Keep `grypx-watch-release.keystore` in a secure location
- ✅ Backup the keystore file to multiple secure locations
- ✅ Store passwords in a password manager
- ✅ Restrict access to keystore files
- ✅ Consider moving passwords to `local.properties` or environment variables

### ❌ DON'T:
- ❌ Commit keystore files to Git (add to `.gitignore`)
- ❌ Share keystore passwords in plain text
- ❌ Store keystore in public repositories
- ❌ Email keystore files without encryption
- ❌ Lose the keystore - you cannot update Play Store apps without it

### 🔴 SECURITY WARNING:
The keystore password is currently **hardcoded** in `build.gradle.kts`. This is a security risk if the repository is public or shared widely.

**Recommended Improvement:**

1. Create `local.properties` file (add to `.gitignore`):
```properties
watchKeystore.storePassword=grypx123456
watchKeystore.keyPassword=grypx123456
watchKeystore.keyAlias=grypx-watch
watchKeystore.storeFile=grypx-watch-release.keystore
```

2. Update `build.gradle.kts`:
```kotlin
// Load keystore properties
val keystorePropertiesFile = rootProject.file("local.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

signingConfigs {
    create("release") {
        storeFile = file(keystoreProperties["watchKeystore.storeFile"] as String)
        storePassword = keystoreProperties["watchKeystore.storePassword"] as String
        keyAlias = keystoreProperties["watchKeystore.keyAlias"] as String
        keyPassword = keystoreProperties["watchKeystore.keyPassword"] as String
    }
}
```

---

## 📦 Google Play Console Integration

### Application Details
- **Package Name:** `com.grypxmobile.watch`
- **App Type:** Wear OS App
- **Signing Certificate:** SHA-1 fingerprint registered
- **Companion App:** GrypX Mobile App (`com.Grypx.app`)

### Firebase Configuration
The SHA-1 fingerprint should be registered in Firebase Console for:
- Phone Authentication (OTP)
- Secure connection with mobile app
- Cloud messaging (if used)

**Firebase Console Location:**  
Project Settings → Your Apps → Android App (Wear OS) → Add Fingerprint

### Wear OS Requirements
- ✅ Targets Wear OS 3.0+ (SDK 30+)
- ✅ Optimized for small screens
- ✅ Includes Wear OS specific permissions
- ✅ Companion app integration available

---

## ⚠️ Important Warnings

### 1. Keystore Loss = Cannot Update App
If you lose `grypx-watch-release.keystore` or forget the password, you **CANNOT** publish updates to the existing Play Store app. You would need to:
- Create a new app listing
- Lose all existing users, reviews, and ratings
- Change the package name

### 2. Password Security
The passwords are currently **hardcoded in build.gradle.kts**:
- ⚠️ Anyone with access to the code can see the passwords
- ⚠️ If repository becomes public, passwords are exposed
- ✅ Move to `local.properties` (see Security Guidelines section above)

### 3. JKS Deprecation
Java KeyStore (JKS) format is deprecated. Consider migrating to PKCS12:

```bash
keytool -importkeystore \
  -srckeystore app/grypx-watch-release.keystore \
  -destkeystore app/grypx-watch-release.keystore \
  -deststoretype pkcs12
```

No code changes needed - PKCS12 is backward compatible.

---

## 📋 Backup Checklist

- [ ] Keystore file (`grypx-watch-release.keystore`) backed up
- [ ] Passwords stored in password manager
- [ ] SHA-1 fingerprint documented
- [ ] SHA-256 fingerprint documented
- [ ] Certificate details documented
- [ ] Backup location documented (secure cloud/drive)
- [ ] Team members have access to backups
- [ ] Backup tested for restoration

---

## 🔄 Keystore Management

### When to Create a New Keystore
- First time publishing a new watch app
- Creating a separate app variant (e.g., beta, enterprise)
- Starting a completely new project

### How to Generate New Keystore
```bash
keytool -genkey -v \
  -keystore grypx-watch-new.keystore \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias grypx-watch-new
```

**Note:** For existing apps, **NEVER** change the keystore. It will break updates.

---

## 🔗 Integration with Mobile App

The GrypX Watch app connects to the mobile app:

| Component | Watch App | Mobile App |
|-----------|-----------|------------|
| Package Name | `com.grypxmobile.watch` | `com.Grypx.app` |
| Keystore | `grypx-watch-release.keystore` | `vedaant.jks` |
| SHA-1 | `E7:49:63:...` | `AE:48:BD:...` |

**Important:** Both SHA-1 fingerprints must be registered in Firebase for proper authentication between apps.

---

## 📞 Emergency Contacts

### If Keystore is Lost:
1. Check all backup locations immediately
2. Contact Google Play Console support
3. Consider Play App Signing (Google manages keys)
4. Check with team members for backup copies

### If Password is Forgotten:
- **Cannot be recovered** - JKS passwords are not recoverable
- You must have a backup with known password
- This is why backups are critical

---

## 🧪 Testing & Deployment

### Testing on Physical Watch
```bash
# List connected Wear OS devices
adb devices

# Install on specific watch
adb -s <DEVICE_ID> install app/build/outputs/apk/release/app-release.apk

# View logs
adb -s <DEVICE_ID> logcat | grep GrypxWatch
```

### Pre-Deployment Checklist
- [ ] Build signed with release keystore
- [ ] Tested on physical Wear OS device
- [ ] ProGuard rules verified (no crashes)
- [ ] Resource shrinking tested
- [ ] Connection to mobile app tested
- [ ] QR code login flow tested
- [ ] 4-digit code login flow tested
- [ ] Active match synchronization tested

---

## 🔗 Related Documentation

- [Watch App Integration Guide](WATCH_APP_INTEGRATION_COMPLETE.md)
- [Backend Requirements - Watch QR Login](BACKEND_REQUIREMENTS_WATCH_QR_LOGIN.md)
- [Mobile App Keystore](../grypx_app/KEYSTORE_INFORMATION.md)
- [Complete Watch App Documentation](COMPLETE_WATCH_APP_DOCUMENTATION.md)

---

## 📝 Maintenance Log

| Date | Action | By |
|------|--------|-----|
| Feb 6, 2026 | Keystore created | Development Team |
| Feb 7, 2026 | Documentation created | Development Team |

---

## 🏗️ Build Environment

### Requirements
- **Kotlin:** 1.9.0+
- **Gradle:** 8.0+
- **Android Studio:** Hedgehog (2023.1.1) or newer
- **JDK:** 17+
- **Wear OS SDK:** API 30+

### Build Commands Reference

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK (signed)
./gradlew assembleRelease

# Build release AAB for Play Store
./gradlew bundleRelease

# Check dependencies
./gradlew dependencies

# Lint check
./gradlew lint
```

---

**⚠️ CONFIDENTIAL - DO NOT SHARE PUBLICLY**

**🔒 Security Notice:** This document contains sensitive credentials and should only be shared with authorized team members. Do not commit to public repositories or share via insecure channels.
