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
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onError("User not logged in")
            return
        }

        val db = FirebaseFirestore.getInstance()
        db.collection(COLLECTION_USERS).document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    
                    // --- SECURITY PHASE 2: DEVICE BINDING CHECK ---
                    val context = com.codesrahul.exclusivetv.MyTVApplication.getInstance()
                    val currentDeviceId = SecurityUtil.getDeviceId(context)
                    val storedDeviceId = document.getString("device_id")

                    if (storedDeviceId.isNullOrEmpty()) {
                        // Scenario A: First Login - Bind the device permanently
                        android.util.Log.i("SubscriptionManager", "Binding account to Device ID: $currentDeviceId")
                        db.collection(COLLECTION_USERS).document(user.uid)
                            .update("device_id", currentDeviceId)
                            .addOnFailureListener { e ->
                                android.util.Log.e("SubscriptionManager", "Failed to bind device to Firestore: $e")
                            }
                    } else if (storedDeviceId != currentDeviceId) {
                        // Scenario B: Device Mismatch - Reject Access
                        android.util.Log.w("SubscriptionManager", "Login blocked! Account bound to $storedDeviceId. Current device is $currentDeviceId.")
                        FirebaseAuth.getInstance().signOut()
                        onError("This account is permanently bound to another device. You cannot share accounts.")
                        return@addOnSuccessListener
                    }
                    // --- END DEVICE BINDING CHECK ---

                    // Resilient field retrieval: check for "plan", "plan " (common typo), and "Plan"
                    // RESILIENT FIELD EXTRACTION: Check multiple common keys
                    val planRaw = (document.getString("plan") 
                                ?: document.getString("Plan") 
                                ?: document.getString("plan ") 
                                ?: document.getString("Plan "))?.trim()
                    
                    val plan = planRaw ?: "Standard"
                    android.util.Log.d("SubscriptionManager", "Firestore Plan: '$planRaw' -> Normalized: '$plan'")
                    
                    val expiryDate = document.getTimestamp(FIELD_EXPIRY_DATE)?.toDate()
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
                    onError("No subscription found")
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
        // [NEW] Sign out from Firebase
        FirebaseAuth.getInstance().signOut()
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
