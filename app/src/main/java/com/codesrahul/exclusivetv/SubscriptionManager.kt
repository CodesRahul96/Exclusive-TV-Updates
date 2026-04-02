package com.codesrahul.exclusivetv

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

object SubscriptionManager {

    private const val COLLECTION_USERS = "users"
    private const val COLLECTION_TRIALS = "trials"
    private const val COLLECTION_MASTER_BYPASS = "master_bypass"
    private const val FIELD_EXPIRY_DATE = "expiry_date"
    private const val FIELD_LAST_DEVICE_RESET = "last_device_reset"
    
    var expiryDate: Date? = null
    var planName: String?
        get() = SP.planName
        set(value) { SP.planName = value }

    fun checkSubscription(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onDowngrade: (String) -> Unit = {},
        onTrialInfo: (Int) -> Unit = {}
    ) {
        val phoneNumber = SP.userId
        if (phoneNumber == null) {
            onError("User not logged in")
            return
        }

        val db = FirebaseFirestore.getInstance()
        // Now using phoneNumber as the Document ID directly
        db.collection(COLLECTION_USERS).document(phoneNumber).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    
                    val context = com.codesrahul.exclusivetv.MyTVApplication.getInstance()
                    // --- MULTI-DEVICE SUPPORT ---
                    val planRaw = (document.getString("plan") 
                                ?: document.getString("Plan") 
                                ?: document.getString("plan ") 
                                ?: document.getString("Plan "))?.trim()
                    val plan = planRaw ?: "Standard"
                    
                    val maxDevices = if ("Premium".equals(plan, ignoreCase = true)) 2 else 1
                    
                    val currentDeviceId = SecurityUtil.getDeviceId(context)
                    val deviceId1 = document.getString("device_id")
                    val deviceId2 = document.getString("device_id_2")
                    
                    val isMatched = if (maxDevices > 1) {
                        (currentDeviceId == deviceId1 || currentDeviceId == deviceId2)
                    } else {
                        (currentDeviceId == deviceId1)
                    }

                    // Metadata to update
                    val deviceMetadata = hashMapOf(
                        "device_name" to Utils.getDeviceName(),
                        "app_version_name" to Utils.getAppVersionName(context),
                        "app_version_code" to Utils.getAppVersionCode(context),
                        "ip_address" to Utils.getIPAddress(true),
                        "mac_id" to Utils.getMacAddress(context),
                        "last_login" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "last_update_metadata" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )

                    if (isMatched) {
                        // Scenario A: Device Matched - Update metadata
                        db.collection(COLLECTION_USERS).document(phoneNumber)
                            .update(deviceMetadata as Map<String, Any>)
                    } else {
                        // Scenario B: No Match - Attempt Binding if slots available
                        var bindField: String? = null
                        if (deviceId1.isNullOrEmpty()) {
                            bindField = "device_id"
                        } else if (maxDevices > 1 && deviceId2.isNullOrEmpty()) {
                            bindField = "device_id_2"
                        }

                        if (bindField != null) {
                            val updateData = deviceMetadata.toMutableMap()
                            updateData[bindField] = currentDeviceId
                            db.collection(COLLECTION_USERS).document(phoneNumber)
                                .update(updateData as Map<String, Any>)
                        } else {
                            // Scenario C: Device limit reached
                            val limitMsg = if (maxDevices > 1) "2 devices" else "1 device"
                            SP.userId = null // Sign out locally
                            onError("Security: This account is restricted to $limitMsg only.")
                            return@addOnSuccessListener
                        }
                    }
                    // --- END MULTI-DEVICE SUPPORT ---

                    // RESILIENT EXPIRY EXTRACTION: Check multiple common keys and types
                    val expiryTimestamp = document.getTimestamp("expiry_date")
                        ?: document.getTimestamp("Expiry Date")
                        ?: document.getTimestamp("ExpiryDate")
                        ?: document.getTimestamp("expiryDate")
                        ?: document.getTimestamp("expiry")
                        ?: document.getTimestamp("Expiry")
                    
                    var parsedExpiry = expiryTimestamp?.toDate()
                    
                    // Fallback: If it's a String (manual entry)
                    if (parsedExpiry == null) {
                        val expiryStr = (document.getString("expiry_date") 
                                    ?: document.getString("Expiry Date")
                                    ?: document.getString("ExpiryDate")
                                    ?: document.getString("expiryDate")
                                    ?: document.getString("expiry")
                                    ?: document.getString("Expiry"))?.trim()
                        
                        if (!expiryStr.isNullOrEmpty()) {
                            try {
                                // Try common formats
                                val formats = listOf("yyyy-MM-dd", "dd-MM-yyyy", "MMM dd, yyyy")
                                for (format in formats) {
                                    try {
                                        parsedExpiry = java.text.SimpleDateFormat(format, java.util.Locale.getDefault()).parse(expiryStr)
                                        if (parsedExpiry != null) break
                                    } catch (e: Exception) {}
                                }
                            } catch (e: Exception) {
                            }
                        }
                    }

                    SubscriptionManager.expiryDate = parsedExpiry

                    // DETECT PLAN CHANGE
                    val oldPlan = SP.planName
                    if (!plan.equals(oldPlan, ignoreCase = true)) {
                        com.codesrahul.exclusivetv.models.TVList.clear(com.codesrahul.exclusivetv.MyTVApplication.getInstance())
                    }
                    this.planName = plan




                    // --- SHADOW BAN CHECK ---
                    val status = (document.getString("status") ?: "active").lowercase()
                    if (status == "banned") {
                        // We simulate a successful "Standard" response so the user doesn't immediately 
                        // know they are banned, but we restrict their experience.
                        SubscriptionManager.planName = "Standard"
                        SubscriptionManager.expiryDate = null
                        onSuccess() 
                        return@addOnSuccessListener
                    }
                    
                    // PLAN VALIDATION
                    if ("Premium".equals(plan, ignoreCase = true)) {
                        val currentExpiry = SubscriptionManager.expiryDate
                        if (currentExpiry != null && currentExpiry.after(java.util.Date())) {
                            
                            // Calculate remaining days for Trial Countdown
                            val diff = currentExpiry.time - System.currentTimeMillis()
                            val days = (diff / (1000 * 60 * 60 * 24)).toInt()
                            if (days in 0..7) {
                                onTrialInfo(days)
                            }
                            
                            fetchPlaylistUrls(phoneNumber, { onSuccess() })
                        } else {
                            
                            // Check master_bypass before downgrading
                            db.collection(COLLECTION_MASTER_BYPASS).document(phoneNumber).get()
                                .addOnCompleteListener { task ->
                                    val isMaster = task.isSuccessful && task.result?.exists() == true
                                    
                                    if (isMaster) {
                                        fetchPlaylistUrls(phoneNumber) { onSuccess() }
                                    } else {
                                        
                                        // 1. Update Firestore
                                        db.collection(COLLECTION_USERS).document(phoneNumber)
                                            .update("plan", "Standard", FIELD_EXPIRY_DATE, null)
                                            .addOnSuccessListener {
                                            }
                                            .addOnFailureListener { e ->
                                            }
                                        
                                        // 2. Update Local State
                                        SubscriptionManager.planName = "Standard"
                                        SubscriptionManager.expiryDate = null
                                        
                                        // 3. Force Channel Refresh
                                        com.codesrahul.exclusivetv.models.TVList.clear(com.codesrahul.exclusivetv.MyTVApplication.getInstance())
                                        
                                        // 4. Notify UI of downgrade
                                        onDowngrade("Your premium trial has expired. You are now on the Standard plan.")
                                        
                                        // 5. Refresh Authorized Playlists
                                        fetchPlaylistUrls(phoneNumber) {
                                            onSuccess()
                                        }
                                    }
                                }
                        }
                    } else {
                        // Standard users don't need expiry validation
                        fetchPlaylistUrls(phoneNumber) { onSuccess() }
                    }
                } else {
                    // --- ADVANCED AUTO-REGISTRATION: CLOUD FUNCTIONS + INTEGRITY ---
                    val context = com.codesrahul.exclusivetv.MyTVApplication.getInstance()
                    val currentDeviceId = SecurityUtil.getDeviceId(context)
                    val robustFingerprint = SecurityUtil.getDeviceFingerprint(context)
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            // 1. Get Integrity Token
                            val integrityToken = IntegrityManager.getIntegrityToken(context)
                            
                            // 2. Call registerUser Cloud Function
                            val functions = FirebaseFunctions.getInstance()
                            val data = hashMapOf(
                                "phoneNumber" to phoneNumber,
                                "deviceFingerprint" to robustFingerprint,
                                "integrityToken" to integrityToken,
                                "deviceId" to currentDeviceId
                            )

                            functions
                                .getHttpsCallable("registerUser")
                                .call(data)
                                .addOnSuccessListener { result ->
                                    val resData = result.data as? Map<*, *>
                                    val plan = resData?.get("plan") as? String ?: "Standard"
                                    
                                    this@SubscriptionManager.planName = plan
                                    
                                    val expiryStr = resData?.get("expiry_date") as? String
                                    if (expiryStr != null) {
                                        this@SubscriptionManager.expiryDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(expiryStr)
                                    }
                                    
                                    // Ensure channel list is cleared so Premium channels load immediately
                                    com.codesrahul.exclusivetv.models.TVList.clear(context)
                                    
                                    fetchPlaylistUrls(phoneNumber) {
                                        onSuccess()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    if (e is FirebaseFunctionsException) {
                                        // Server rejected on purpose - DO NOT FALLBACK!
                                        if (e.code == FirebaseFunctionsException.Code.ALREADY_EXISTS || e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                                            onError(e.message ?: "Registration blocked by server.")
                                            return@addOnFailureListener
                                        }
                                    }
                                    performLegacyRegistration(db, phoneNumber, currentDeviceId, robustFingerprint, onSuccess, onError)
                                }
                        } catch (e: Exception) {
                            performLegacyRegistration(db, phoneNumber, currentDeviceId, robustFingerprint, onSuccess, onError)
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                onError("Verification failed: ${exception.message}")
            }
    }

    private fun performLegacyRegistration(
        db: FirebaseFirestore,
        phoneNumber: String,
        currentDeviceId: String,
        robustFingerprint: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        
        // --- ROBUST BYPASS CHECK ---
        // If we can't check the master_bypass collection (e.g. Permission Denied), 
        // we should NOT block the entire registration. We just assume isMasterUser = false.
        db.collection(COLLECTION_MASTER_BYPASS).document(phoneNumber).get()
            .addOnCompleteListener { task ->
                val isMasterUser = if (task.isSuccessful) {
                    task.result?.exists() == true
                } else {
                    false // Proceed as non-master user
                }

                // --- ROBUST TRIAL CHECK ---
                // If we can't check the trials collection (e.g. Permission Denied), 
                // we should NOT block the entire registration. We proceed as if they haven't had a trial.
                db.collection(COLLECTION_TRIALS).document(robustFingerprint).get()
                    .addOnCompleteListener { trialTask ->
                        val hasHadTrial = if (trialTask.isSuccessful) {
                            trialTask.result?.exists() == true
                        } else {
                            false // Proceed as if no previous trial (generous)
                        }

                        // --- ROBUST MULTI-ACCOUNT CHECK ---
                        // Before allowing a new registration, ensure no other account is tied to this fingerprint or Android ID.
                        val checkTasks = mutableListOf<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>>()
                        checkTasks.add(db.collection(COLLECTION_USERS).whereEqualTo("device_fingerprint", robustFingerprint).limit(1).get())
                        if (currentDeviceId.isNotBlank() && currentDeviceId != "unknown_device") {
                            checkTasks.add(db.collection(COLLECTION_USERS).whereEqualTo("device_id", currentDeviceId).limit(1).get())
                            checkTasks.add(db.collection(COLLECTION_USERS).whereEqualTo("device_id_2", currentDeviceId).limit(1).get())
                        }
                        
                        com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(checkTasks)
                            .addOnSuccessListener { results ->
                                val isBlocked = results.any { !it.isEmpty }
                                
                                if (isBlocked) {
                                    onError("This device is already registered with another account. Please use your original login.")
                                    return@addOnSuccessListener
                                }
                                
                                val newUser = hashMapOf<String, Any>(
                                    "device_id" to currentDeviceId,
                                    "device_fingerprint" to robustFingerprint,
                                    "created_at" to FieldValue.serverTimestamp(),
                                    "phone_number" to phoneNumber,
                                    "plan" to if (isMasterUser || !hasHadTrial) "Premium" else "Standard"
                                )
                                
                                if (newUser["plan"] == "Premium") {
                                    val cal = java.util.Calendar.getInstance()
                                    cal.add(java.util.Calendar.DAY_OF_YEAR, 7) // 7-Day Trial
                                    newUser[FIELD_EXPIRY_DATE] = cal.time
                                }
                                
                                db.collection(COLLECTION_USERS).document(phoneNumber).set(newUser)
                                    .addOnSuccessListener {
                                        SubscriptionManager.planName = newUser["plan"] as String
                                        SubscriptionManager.expiryDate = newUser[FIELD_EXPIRY_DATE] as? Date
                                        
                                        // Record Trial if it was a new Premium claim
                                        if (newUser["plan"] == "Premium" && !hasHadTrial) {
                                            val trialData = hashMapOf(
                                                "claimed_by" to phoneNumber,
                                                "claimed_at" to FieldValue.serverTimestamp(),
                                                "master_bypass" to isMasterUser
                                            )
                                            // Soft record trial - if this fails, it's not critical for this session
                                            db.collection(COLLECTION_TRIALS).document(robustFingerprint).set(trialData)
                                                .addOnFailureListener { e ->
                                                }
                                        }
                                        
                                        
                                        // Ensure channel list is cleared so Premium channels load immediately
                                        com.codesrahul.exclusivetv.models.TVList.clear(com.codesrahul.exclusivetv.MyTVApplication.getInstance())
                                        
                                        fetchPlaylistUrls(phoneNumber) {
                                            onSuccess()
                                        }
                                    }
                                    .addOnFailureListener { e -> 
                                        onError("Registration failed: ${e.message}") 
                                    }
                            }
                            .addOnFailureListener { e ->
                                onError("Registration security check failed. Please try again later.")
                            }
                    }
            }
    }
    
    /**
     * Fetches authorized playlist URLs from the server using the getPlaylist Cloud Function.
     * This ensures the logic of which URL to use is strictly enforced on the server.
     */
    private fun fetchPlaylistUrls(phoneNumber: String, onComplete: () -> Unit) {
        val functions = FirebaseFunctions.getInstance()
        val data = hashMapOf(
            "phoneNumber" to phoneNumber
        )

        functions.getHttpsCallable("getPlaylist")
            .call(data)
            .addOnSuccessListener { result ->
                val response = result.data as? Map<*, *>
                val url = response?.get("url") as? String
                val plan = response?.get("plan") as? String
                
                if (url != null) {
                    if ("Premium".equals(plan, ignoreCase = true)) {
                        SP.premiumConfig = url
                    } else {
                        SP.standardConfig = url
                    }
                }
                onComplete()
            }
            .addOnFailureListener { e ->
                // Fallback to local logic (already in TVList)
                onComplete()
            }
    }

    fun resetBoundDevices(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val phoneNumber = SP.userId ?: return onError("User not logged in")
        val db = FirebaseFirestore.getInstance()
        
        db.collection(COLLECTION_USERS).document(phoneNumber).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val lastReset = document.getTimestamp(FIELD_LAST_DEVICE_RESET)?.toDate()
                    val now = java.util.Date()
                    
                    if (lastReset != null) {
                        val diff = now.time - lastReset.time
                        val daysSinceReset = diff / (1000 * 60 * 60 * 24)
                        
                        if (daysSinceReset < 30) {
                            val daysRemaining = 30 - daysSinceReset
                            onError("Security: Device reset is restricted to once every 30 days. Please try again in $daysRemaining day(s).")
                            return@addOnSuccessListener
                        }
                    }
                    
                    // Proceed with reset
                    val updates = hashMapOf<String, Any?>(
                        "device_id" to null,
                        "device_id_2" to null,
                        FIELD_LAST_DEVICE_RESET to FieldValue.serverTimestamp()
                    )
                    
                    db.collection(COLLECTION_USERS).document(phoneNumber).update(updates)
                        .addOnSuccessListener {
                            val plan = document.getString("plan") ?: "Standard"
                            val slots = if ("Premium".equals(plan, ignoreCase = true)) "2 slots" else "1 slot"
                            onSuccess("Success: Bound devices cleared. $slots are now available for this account.")
                        }
                        .addOnFailureListener { e ->
                            onError("Reset failed: ${e.message}")
                        }
                } else {
                    onError("User data not found.")
                }
            }
            .addOnFailureListener { e ->
                onError("Verification failed: ${e.message}")
            }
    }

    fun signOut(context: android.content.Context) {
        clearAppData(context)
    }

    private fun clearAppData(context: android.content.Context) {
        // [NEW] Clear local SP login state
        SP.userId = null
        expiryDate = null
        planName = null
        
        // 1. Clear all Preference Managers (Synchronously using commit())
        try { SP.reset() } catch (e: Exception) { e.printStackTrace() }
        try { OrderPreferenceManager.resetAll() } catch (e: Exception) { e.printStackTrace() }

        // 2. Delete all local files (legacy channels.txt, etc)
        try { deleteRecursive(context.filesDir) } catch (e: Exception) { e.printStackTrace() }
        
        // 4. Delete cache (epg_cache.xml.gz, etc)
        try { deleteRecursive(context.cacheDir) } catch (e: Exception) { e.printStackTrace() }
        
        // 5. Clear WebViews/Cookies if any
        try { 
            android.webkit.WebStorage.getInstance().deleteAllData()
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush() // Force write
        } catch (e: Exception) { e.printStackTrace() }

        com.codesrahul.exclusivetv.models.TVList.clear(com.codesrahul.exclusivetv.MyTVApplication.getInstance())
    }

    private fun deleteRecursive(fileOrDirectory: java.io.File?) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return
        
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        fileOrDirectory.delete()
    }

    fun getDaysRemaining(): Long {
        val expiry = expiryDate ?: return 0
        val diff = expiry.time - System.currentTimeMillis()
        return if (diff > 0) java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff) else 0
    }
}
