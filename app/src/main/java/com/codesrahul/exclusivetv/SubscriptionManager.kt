package com.codesrahul.exclusivetv

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

object SubscriptionManager {

    private const val COLLECTION_USERS = "users"
    private const val FIELD_EXPIRY_DATE = "expiry_date"
    
    var expiryDate: Date? = null
    var planName: String?
        get() = SP.planName
        set(value) { SP.planName = value }

    fun checkSubscription(onSuccess: () -> Unit, onError: (String) -> Unit) {
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
                        android.util.Log.d("SubscriptionManager", "Device match confirmed. Updating metadata.")
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
                            android.util.Log.i("SubscriptionManager", "Binding new device slot ($bindField) for account $phoneNumber")
                            val updateData = deviceMetadata.toMutableMap()
                            updateData[bindField] = currentDeviceId
                            db.collection(COLLECTION_USERS).document(phoneNumber)
                                .update(updateData as Map<String, Any>)
                        } else {
                            // Scenario C: Device limit reached
                            val limitMsg = if (maxDevices > 1) "2 devices" else "1 device"
                            android.util.Log.w("SubscriptionManager", "ACCESS DENIED: Device limit ($maxDevices) reached for $phoneNumber.")
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
                    
                    var expiryDate = expiryTimestamp?.toDate()
                    
                    // Fallback: If it's a String (manual entry)
                    if (expiryDate == null) {
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
                                        expiryDate = java.text.SimpleDateFormat(format, java.util.Locale.getDefault()).parse(expiryStr)
                                        if (expiryDate != null) break
                                    } catch (e: Exception) {}
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SubscriptionManager", "Failed to parse expiry string: $expiryStr")
                            }
                        }
                    }

                    this.expiryDate = expiryDate

                    // DETECT PLAN CHANGE
                    val oldPlan = SP.planName
                    if (!plan.equals(oldPlan, ignoreCase = true)) {
                        android.util.Log.i("SubscriptionManager", "Plan switched from $oldPlan to $plan. Clearing data.")
                        com.codesrahul.exclusivetv.models.TVList.clear(com.codesrahul.exclusivetv.MyTVApplication.getInstance())
                    }
                    this.planName = plan




                    // PLAN VALIDATION
                    if ("Premium".equals(plan, ignoreCase = true)) {
                        if (expiryDate != null && expiryDate.after(Date())) {
                            android.util.Log.d("SubscriptionManager", "Premium Validated. Expires: $expiryDate")
                            onSuccess()
                        } else {
                            android.util.Log.w("SubscriptionManager", "Premium Expired: $expiryDate")
                            onError("Subscription expired on ${expiryDate ?: "Unknown"}")
                        }
                    } else {
                        // Standard users don't need expiry validation
                        android.util.Log.d("SubscriptionManager", "Standard User Validated")
                        onSuccess()
                    }
                } else {
                    // Auto-register new user as "Standard"
                    val context = com.codesrahul.exclusivetv.MyTVApplication.getInstance()
                    val currentDeviceId = SecurityUtil.getDeviceId(context)
                    val newUser = hashMapOf(
                        "plan" to "Standard",
                        "device_id" to currentDeviceId,
                        "created_at" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "phone_number" to phoneNumber // Storing redundantly inside document for convenience
                    )
                    db.collection(COLLECTION_USERS).document(phoneNumber)
                        .set(newUser)
                        .addOnSuccessListener {
                            android.util.Log.i("SubscriptionManager", "Auto-registered new user: $phoneNumber")
                            this.planName = "Standard"
                            onSuccess()
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("SubscriptionManager", "Failed to auto-register user: $e")
                            onError("Failed to create account. Please try again.")
                        }
                }
            }
            .addOnFailureListener { exception ->
                onError("Verification failed: ${exception.message}")
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

        // 2. Clear Room Database
        try { 
            val db = com.codesrahul.exclusivetv.db.AppDatabase.getDatabase(context)
            db.clearAllTables() 
        } catch (e: Exception) { e.printStackTrace() }

        // 3. Delete all local files (legacy channels.txt, etc)
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
