package com.codesrahul.exclusivetv

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.codesrahul.exclusivetv.databinding.SettingBinding
import com.codesrahul.exclusivetv.models.TVList
import com.codesrahul.exclusivetv.ui.TvUiUtils
import com.codesrahul.exclusivetv.LogViewerActivity

class SettingFragment : Fragment() {

    private var _binding: SettingBinding? = null
    private val binding get() = _binding!!

    private lateinit var uri: Uri
    private lateinit var updateManager: UpdateManager
    private var tvUiUtils: TvUiUtils? = null

    private val idleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val idleRunnable = Runnable { hideSelf() }
    private val IDLE_TIMEOUT = 0L // Disabled as per user request

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SettingBinding.inflate(inflater, container, false)
        tvUiUtils = TvUiUtils(requireContext())
        tvUiUtils?.initSounds(R.raw.focus, R.raw.click)

        setupUI()
        setupListeners()
        setupFocusAnimations()

        updateManager = UpdateManager(requireContext(), com.codesrahul.exclusivetv.BuildConfig.VERSION_CODE)
        (activity as MainActivity).ready(TAG)
        
        return binding.root
    }

    private fun setupUI() {
        binding.name.text = getString(R.string.app_name)

        // [NEW] User Info
        val userPhone = SP.userId
        if (userPhone != null) {
            binding.accountPhone.text = userPhone
            val plan = SubscriptionManager.planName ?: "Standard"
            val expiryDate = SubscriptionManager.expiryDate
            val isPremium = "Premium".equals(plan, ignoreCase = true)
            
            if (isPremium && expiryDate != null) {
                val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                val formattedDate = dateFormat.format(expiryDate)
                val daysLeft = SubscriptionManager.getDaysRemaining()
                
                binding.accountStatus.text = "Plan: $plan | Expires: $formattedDate ($daysLeft days left)"
                binding.accountStatus.maxLines = 2
            } else {
                binding.accountStatus.text = "Plan: $plan"
            }
            binding.accountStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green))
        } else {
            binding.accountPhone.text = "Not Logged In"
            binding.accountStatus.text = "Guest Mode"
        }
        
        syncStatusUI()

        binding.config.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }

        // Reset timer on scroll interactions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.settingsScroll.setOnScrollChangeListener { _, _, _, _, _ -> resetIdleTimer() }
        } else {
             binding.settingsScroll.viewTreeObserver.addOnScrollChangedListener {
                 resetIdleTimer()
             }
        }
        
        binding.settingsScroll.setOnTouchListener { _, _ ->
            resetIdleTimer()
            false
        }

        binding.versionName.text = com.codesrahul.exclusivetv.BuildConfig.VERSION_NAME
        
        // IP & MAC Address display
        val ip = Utils.getIPAddress(true)
        val mac = Utils.getMacAddress(requireContext())
        
        val displayIp = if (ip.isEmpty()) "Unavailable" else ip
        val displayMac = if (mac.isEmpty() || mac == "02:00:00:00:00:00") "Unavailable" else mac
        
        binding.deviceInfo.text = "IP: $displayIp  |  MAC: $displayMac"

        // Symbols Fix: Explicitly tint icons for compatibility with Android API < 23
        val iconColorSecondary = Color.parseColor("#80FFFFFF")
        tvUiUtils?.tintTextViewDrawable(binding.confirmConfig, Color.WHITE)
        tvUiUtils?.tintTextViewDrawable(binding.managePlaylists, iconColorSecondary)
        tvUiUtils?.tintTextViewDrawable(binding.manageCategories, iconColorSecondary)
        tvUiUtils?.tintTextViewDrawable(binding.clear, iconColorSecondary)
        tvUiUtils?.tintTextViewDrawable(binding.checkVersion, iconColorSecondary)
        tvUiUtils?.tintTextViewDrawable(binding.copyrightInfo, iconColorSecondary)
        tvUiUtils?.tintTextViewDrawable(binding.appWebsite, iconColorSecondary)

        // Tint Portfolio Icons
        val goldColor = ContextCompat.getColor(requireContext(), R.color.accent_gold)
        binding.portfolioWebsite.setColorFilter(goldColor)
        binding.portfolioLinkedin.setColorFilter(goldColor)
        binding.portfolioGithub.setColorFilter(goldColor)
    }

    private fun syncStatusUI() {
        binding.statusChannelReversal.text = if (SP.channelReversal) "ON" else "OFF"
        binding.statusChannelNum.text = if (SP.channelNum) "ON" else "OFF"
        binding.statusShowDateInInfo.text = if (SP.showDateInInfo) "ON" else "OFF" // Sync status
        binding.statusTime.text = if (SP.time) "ON" else "OFF"
        binding.statusWatchLast.text = if (SP.watchLast) "ON" else "OFF"
        binding.statusForceHighQuality.text = if (SP.forceHighQuality) "ON" else "OFF"
        binding.statusBootStartup.text = if (SP.bootStartup) "ON" else "OFF"
        binding.statusConfigAutoLoad.text = if (SP.configAutoLoad) "ON" else "OFF"
        binding.statusChannelCheck.text = if (SP.channelCheck) "ON" else "OFF"
         binding.statusEpg.text = if (SP.epgEnabled) "ON" else "OFF"
         binding.statusEpgShift.text = "${SP.epgShift}h"
          binding.statusWatermark.text = if (SP.watermarkEnabled) "ON" else "OFF"
          binding.statusPipMode.text = if (SP.pipMode) "ON" else "OFF"
          binding.statusAudioStabilizer.text = if (SP.audioStabilizer) "ON" else "OFF"


        // Set text colors based on state
        val activeColor = ContextCompat.getColor(requireContext(), R.color.accent_gold)
        val inactiveColor = Color.parseColor("#80FFFFFF")

        val statusViews = listOf(
            binding.statusChannelReversal, binding.statusChannelNum, binding.statusTime,
            binding.statusShowDateInInfo,
            binding.statusWatchLast, binding.statusForceHighQuality, binding.statusBootStartup,
            binding.statusConfigAutoLoad, binding.statusChannelCheck, binding.statusEpg,
            binding.statusWatermark, binding.statusPipMode, binding.statusBufferMode, 
            binding.statusAudioStabilizer, binding.statusEpgShift
        )

        statusViews.forEach { v ->
            val text = v.text.toString()
            val isActive = text == "ON" || (text != "OFF" && text != "Default" && text != "0h")
            v.setTextColor(if (isActive) activeColor else inactiveColor)
        }

        val bufferModes = arrayOf("Default", "Max Stability", "Low Latency") // 0, 1, 2
        binding.statusBufferMode.text = bufferModes.getOrElse(SP.bufferMode) { "Default" }

        val langCode = SP.defaultAudioLanguage
        binding.statusAudioLanguage.text = if (langCode.isEmpty()) "Default" else {
            java.util.Locale(langCode).displayLanguage
        }
        binding.statusAudioLanguage.setTextColor(if (langCode.isNotEmpty()) activeColor else inactiveColor)

        binding.statusAudioStabilizer.text = if (SP.audioStabilizer) "ON" else "OFF"
        binding.statusAudioStabilizer.setTextColor(if (SP.audioStabilizer) activeColor else inactiveColor)
    }

    private fun setupFocusAnimations() {
        val focusViews = listOf(
            binding.cardChannelReversal,
            binding.cardChannelNum,
            binding.cardShowDateInInfo,
            binding.cardTime,
            binding.cardWatchLast,
            binding.cardForceHighQuality,
            binding.cardBootStartup,
            binding.cardConfigAutoLoad,
            binding.cardChannelCheck,
             binding.cardEpg,
             binding.cardEpgShift,
             binding.cardWatermark,
             binding.cardPipMode,
            binding.cardBufferMode,
            binding.cardAudioLanguage,
            binding.cardAudioStabilizer,
            binding.managePlaylists,
            binding.manageCategories,
            binding.clear,
            binding.checkVersion,
            binding.copyrightInfo,
            binding.appWebsite,
            binding.portfolioWebsite,
            binding.portfolioLinkedin,
            binding.portfolioGithub,
            binding.closeMenu
        )

        focusViews.forEach { v ->
            v.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    resetIdleTimer()
                    view.animate().scaleX(1.01f).scaleY(1.01f).setDuration(150).start()
                    if (view !is android.widget.EditText) {
                        tvUiUtils?.playFocusSound()
                    }
                } else {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }
            }
        }
    }

    private fun setupListeners() {
        // Card Toggles
        binding.cardChannelReversal.setOnClickListener { toggleSetting("channelReversal") }
        binding.cardChannelNum.setOnClickListener { toggleSetting("channelNum") }
        binding.cardShowDateInInfo.setOnClickListener { toggleSetting("showDateInInfo") }
        binding.cardTime.setOnClickListener { toggleSetting("time") }
        binding.cardWatchLast.setOnClickListener { toggleSetting("watchLast") }
        binding.cardForceHighQuality.setOnClickListener { toggleSetting("forceHighQuality") }
        binding.cardBootStartup.setOnClickListener { toggleSetting("bootStartup") }
        binding.cardConfigAutoLoad.setOnClickListener { toggleSetting("configAutoLoad") }
        binding.cardChannelCheck.setOnClickListener { toggleSetting("channelCheck") }
         binding.cardEpg.setOnClickListener { toggleSetting("epgEnabled") }
         binding.cardEpgShift.setOnClickListener { setupEpgShiftDialog() }
         binding.cardWatermark.setOnClickListener { toggleSetting("watermark") }
         binding.cardPipMode.setOnClickListener { toggleSetting("pipMode") }
        binding.cardBufferMode.setOnClickListener { toggleSetting("bufferMode") }
        binding.cardAudioLanguage.setOnClickListener { setupAudioLanguageDialog() }
        binding.cardAudioStabilizer.setOnClickListener { toggleSetting("audioStabilizer") }
        
        // [NEW] Logout Listener
        binding.btnLogout.setOnClickListener {
             tvUiUtils?.playClickSound()
             android.app.AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Logout?")
                .setMessage("Are you sure you want to logout? This will clear all app data.")
                .setPositiveButton("Logout") { _, _ ->
                    // Restart app to go back to login (logic inside performFullReset)
                    performFullReset(justRestart = true) 
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // [NEW] Change OTP Dialog Trigger
        binding.btnChangeOtpDialog.setOnClickListener {
            tvUiUtils?.playClickSound()
            showChangeOtpDialog()
        }

        binding.confirmConfig.setOnClickListener {
            tvUiUtils?.playClickSound()
            val text = binding.config.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a URL", Toast.LENGTH_SHORT).show()
            } else {
                val url = Utils.formatUrl(text)
                // Add to multi-playlist source
                SP.addPlaylistUrl(url)
                
                // Trigger update
                TVList.update(requireContext(), silent = false) // Fetch all
                
                binding.config.text = null // Clear input
                Toast.makeText(requireContext(), "Source added", Toast.LENGTH_SHORT).show()
            }
        }
        

        
        binding.managePlaylists.setOnClickListener {
             tvUiUtils?.playClickSound()
             showManagePlaylistsDialog()
        }

        binding.manageCategories.setOnClickListener {
             tvUiUtils?.playClickSound()
             showManageCategoriesDialog()
        }

            binding.clear.setOnClickListener {
            tvUiUtils?.playClickSound()

            val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            dialog.setContentView(R.layout.dialog_factory_reset)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val btnCancel = dialog.findViewById<android.widget.Button>(R.id.btnCancel)
            val btnReset = dialog.findViewById<android.widget.Button>(R.id.btnReset)

            btnCancel.setOnClickListener {
                tvUiUtils?.playClickSound()
                dialog.dismiss()
            }

            btnReset.setOnClickListener {
                tvUiUtils?.playClickSound()
                performFullReset()
                dialog.dismiss()
            }
            
            // Focus handling
            btnCancel.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                else v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
            btnReset.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                else v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }

            dialog.show()
            btnCancel.requestFocus()
        }

        binding.checkVersion.setOnClickListener {
            tvUiUtils?.playClickSound()
            requestInstallPermissions()
        }
        
        // [searchable] Hidden Log Viewer
        binding.checkVersion.setOnLongClickListener {
            tvUiUtils?.playClickSound()
             try {
                startActivity(android.content.Intent(requireContext(), LogViewerActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Log Viewer not found", Toast.LENGTH_SHORT).show()
            }
            true
        }

        binding.copyrightInfo.setOnClickListener {
            tvUiUtils?.playClickSound()
            showCopyrightDialog()
        }

        binding.appWebsite.setOnClickListener {
            openUrl("https://exclusivetv.indevs.in/")
        }

        binding.portfolioWebsite.setOnClickListener {
            openUrl("https://www.codesrahul.in")
        }

        binding.portfolioLinkedin.setOnClickListener {
            openUrl("https://www.linkedin.com/in/codesrahul")
        }

        binding.portfolioGithub.setOnClickListener {
            openUrl("https://github.com/CodesRahul96")
        }

        binding.closeMenu.setOnClickListener {
            hideSelf()
        }
    }

    private fun openUrl(url: String) {
        try {
            tvUiUtils?.playClickSound()
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleSetting(key: String) {
        tvUiUtils?.playClickSound()
        resetIdleTimer()
        when (key) {
            "channelReversal" -> SP.channelReversal = !SP.channelReversal
            "channelNum" -> SP.channelNum = !SP.channelNum
            "showDateInInfo" -> SP.showDateInInfo = !SP.showDateInInfo
            "time" -> SP.time = !SP.time
            "watchLast" -> SP.watchLast = !SP.watchLast
            "forceHighQuality" -> SP.forceHighQuality = !SP.forceHighQuality
            "bootStartup" -> SP.bootStartup = !SP.bootStartup
            "configAutoLoad" -> SP.configAutoLoad = !SP.configAutoLoad
            "channelCheck" -> SP.channelCheck = !SP.channelCheck
            "epgEnabled" -> {
                SP.epgEnabled = !SP.epgEnabled
                if (SP.epgEnabled) {
                    TVList.update(requireContext(), SP.config ?: TVList.DEFAULT_CONFIG_URL, silent = true)
                } else {
                    TVList.listModel.forEach { it.updateEPG() }
                }
            }
            "watermark" -> {
                SP.watermarkEnabled = !SP.watermarkEnabled
                (activity as? MainActivity)?.updateWatermarkVisibility()
            }
            "pipMode" -> SP.pipMode = !SP.pipMode
            "bufferMode" -> {
                var current = SP.bufferMode
                current = (current + 1) % 3 // 0->1->2->0
                SP.bufferMode = current
                Toast.makeText(requireContext(), "Buffering: " + arrayOf("Default", "Max Stability", "Low Latency")[current] + " (Restart stream to apply)", Toast.LENGTH_SHORT).show()
            }
            "audioStabilizer" -> {
                SP.audioStabilizer = !SP.audioStabilizer
                binding.statusAudioStabilizer.text = if (SP.audioStabilizer) "ON" else "OFF"
            }

        }
        syncStatusUI()
    }

    private fun performFullReset(justRestart: Boolean = false) {
        try {
            if (!justRestart) {
                Toast.makeText(requireContext(), "Resetting...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Logging out...", Toast.LENGTH_SHORT).show()
            }
            
            // Delegate comprehensive data clearing to SubscriptionManager
            SubscriptionManager.signOut(requireContext())

            Toast.makeText(requireContext(), if (justRestart) "Restarting..." else "Factory Reset Complete. Restarting...", Toast.LENGTH_LONG).show()

            // 5. Force Restart App using Phoenix Process Pattern (Professional Approach)
            val ctx = context ?: return
            binding.root.postDelayed({
                try {
                    // Trigger Phoenix Restart
                    PhoenixActivity.trigger(ctx)
                } catch (e: Exception) {
                    e.printStackTrace()
                     // Fallback to simple restart if Phoenix fails for some reason
                     val pm = ctx.packageManager
                     val launchIntent = pm.getLaunchIntentForPackage(ctx.packageName)
                     if (launchIntent != null) {
                         launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                         startActivity(launchIntent)
                     }
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(0)
                }
            }, 500)

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Reset failed: ${e.message}", Toast.LENGTH_SHORT).show()
            // Try to crash/restart anyway to clear state
            System.exit(0) 
        }
    }


    private fun requestReadPermissions() {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (list.isEmpty()) {
            TVList.parseUri(requireContext(), uri)
        } else {
            ActivityCompat.requestPermissions(requireActivity(), list.toTypedArray(), PERMISSION_READ)
        }
    }

    fun setServer(server: String) {
        // Only update if server is not empty/offline (avoid redundancy with static IP display)
        if (server.isNotEmpty() && !server.contains("offline", ignoreCase = true)) {
            val ip = Utils.getIPAddress(true)
            val mac = Utils.getMacAddress(requireContext())
            val displayMac = if (mac.isEmpty() || mac == "02:00:00:00:00:00") "Unavailable" else mac
            
            // Show full server link as it contains the port
            binding.deviceInfo.text = "Server: http://$server  |  MAC: $displayMac"
        }
    }

    fun setVersionName(versionName: String) {
        binding.versionName.text = versionName
    }

    private fun hideSelf() {
        if (!isAdded || activity == null) return
        
        try {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(0, R.anim.slide_out_right)
                .hide(this)
                .commitAllowingStateLoss()
            
            (activity as? MainActivity)?.showTime()
        } catch (e: Exception) {
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            binding.container.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.slide_in_right))
            setupUI()
            resetIdleTimer()
        } else {
            idleHandler.removeCallbacks(idleRunnable)
        }
    }

    private fun resetIdleTimer() {
        // Disabled auto-close to ensure settings stay open while in use
        idleHandler.removeCallbacks(idleRunnable)
    }

    private val updateCheckListener = object : UpdateManager.CheckListener {
        override fun onCheckStart() {
            _binding?.rlCheckingUpdate?.visibility = View.VISIBLE
            _binding?.rlCheckingUpdate?.bringToFront()
        }

        override fun onCheckEnd() {
            _binding?.rlCheckingUpdate?.visibility = View.GONE
        }

        override fun onShowResult(title: String, message: String, isUpdate: Boolean, changelog: String, force: Boolean) {
            val binding = _binding ?: return
            binding.rlMessageOverlay.visibility = View.VISIBLE
            binding.rlMessageOverlay.bringToFront()
            binding.tvOverlayTitle.text = title

            var fullMessage = message
            if (changelog.isNotEmpty()) {
                fullMessage += "\n\n$changelog"
            }
            binding.tvOverlayMessage.text = fullMessage

            if (isUpdate) {
                binding.btnOverlayAction.text = "Update Now"
                binding.btnOverlayAction.setOnClickListener {
                    binding.rlMessageOverlay.visibility = View.GONE
                    updateManager.onConfirm()
                }
                binding.btnOverlayAction.requestFocus()

                if (force) {
                    binding.btnOverlayCancel.visibility = View.GONE
                } else {
                    binding.btnOverlayCancel.visibility = View.VISIBLE
                    binding.btnOverlayCancel.setOnClickListener {
                        binding.rlMessageOverlay.visibility = View.GONE
                    }
                }
            } else {
                binding.btnOverlayAction.text = "OK"
                binding.btnOverlayAction.setOnClickListener {
                    binding.rlMessageOverlay.visibility = View.GONE
                }
                binding.btnOverlayAction.requestFocus()
                binding.btnOverlayCancel.visibility = View.GONE
            }
        }
    }

    private fun showChangeOtpDialog() {
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_change_otp)
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Convert dp to px for the width
        val widthPx = (350 * resources.displayMetrics.density).toInt()
        dialog.window?.setLayout(
            widthPx,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etCurrentOtp = dialog.findViewById<android.widget.EditText>(R.id.et_current_otp)
        val etNewOtp = dialog.findViewById<android.widget.EditText>(R.id.et_new_otp)
        val etConfirmOtp = dialog.findViewById<android.widget.EditText>(R.id.et_confirm_otp)
        val btnCancel = dialog.findViewById<android.widget.Button>(R.id.btn_cancel_otp)
        val btnUpdate = dialog.findViewById<android.widget.Button>(R.id.btn_change_otp)

        val otpTextWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val current = etCurrentOtp.text.toString()
                val newOtp = etNewOtp.text.toString()
                val confirmOtp = etConfirmOtp.text.toString()

                val isValid = current.length == 6 && newOtp.length == 6 && newOtp == confirmOtp
                btnUpdate.isEnabled = isValid
                btnUpdate.alpha = if (isValid) 1.0f else 0.5f
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        
        etCurrentOtp.addTextChangedListener(otpTextWatcher)
        etNewOtp.addTextChangedListener(otpTextWatcher)
        etConfirmOtp.addTextChangedListener(otpTextWatcher)

        btnCancel.setOnClickListener {
            tvUiUtils?.playClickSound()
            dialog.dismiss()
        }

        btnUpdate.setOnClickListener {
            tvUiUtils?.playClickSound()
            val phoneNumber = SP.userId
            if (phoneNumber == null) {
                Toast.makeText(requireContext(), "Error: Not logged in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentOtpInput = etCurrentOtp.text.toString()
            val newOtpInput = etNewOtp.text.toString()
            
            btnUpdate.isEnabled = false
            btnUpdate.text = "Verifying..."

            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(phoneNumber)

            userRef.get().addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val dbCustomOtp = document.getString("custom_otp")
                    val isValid = if (dbCustomOtp.isNullOrEmpty()) {
                        currentOtpInput == "123321"
                    } else {
                        currentOtpInput == dbCustomOtp
                    }

                    if (isValid) {
                        btnUpdate.text = "Updating..."
                        userRef.update("custom_otp", newOtpInput)
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "OTP Updated Successfully. Please login again.", Toast.LENGTH_LONG).show()
                                dialog.dismiss()
                                
                                // FORCE LOGOUT & RESTART
                                performFullReset(justRestart = true)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to update OTP", e)
                                Toast.makeText(requireContext(), "Failed to update OTP", Toast.LENGTH_SHORT).show()
                                btnUpdate.text = "Update"
                                btnUpdate.isEnabled = true
                            }
                    } else {
                        Toast.makeText(requireContext(), "Incorrect Current OTP", Toast.LENGTH_LONG).show()
                        etCurrentOtp.error = "Incorrect PIN"
                        btnUpdate.text = "Update"
                        btnUpdate.isEnabled = true
                    }
                } else {
                    Toast.makeText(requireContext(), "Profile not found", Toast.LENGTH_LONG).show()
                    btnUpdate.text = "Update"
                    btnUpdate.isEnabled = true
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch user data for OTP change", e)
                Toast.makeText(requireContext(), "Network error", Toast.LENGTH_SHORT).show()
                btnUpdate.text = "Update"
                btnUpdate.isEnabled = true
            }
        }

        // Focus handling
        btnCancel.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
            else v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }
        btnUpdate.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
            else v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }

        dialog.show()
        etCurrentOtp.requestFocus()
    }

    private fun requestInstallPermissions() {
        val permissionsList: MutableList<String> = mutableListOf()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsList.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }
        if (permissionsList.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), permissionsList.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            updateManager.checkAndUpdate(isManualCheck = true, listener = updateCheckListener)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permission granted, retry update check using reused listener
                updateManager.checkAndUpdate(isManualCheck = true, listener = updateCheckListener)
            } else {
                Toast.makeText(requireContext(), "Permission required to download update", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showManagePlaylistsDialog() {
        // Filter out main API URL to keep it hidden/private
        val urls = SP.playlistUrls.filter { it != TVList.DEFAULT_CONFIG_URL }.toTypedArray()
        if (urls.isEmpty()) {
            Toast.makeText(requireContext(), "No sources added", Toast.LENGTH_SHORT).show()
            return
        }
        
        android.app.AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Manage Sources (Tap to Remove)")
            .setItems(urls) { _, which ->
                val selectedUrl = urls[which]
                showRemoveSourceDialog(selectedUrl)
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showManageCategoriesDialog() {
        val allGroups = com.codesrahul.exclusivetv.models.TVList.groupModel.getTVListModelList()
        if (allGroups.isEmpty()) {
            Toast.makeText(requireContext(), "No categories loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val categoryPairs = allGroups.filter { it.getIndex() > 1 }.map { 
            val originalName = it.getName()
            val displayName = OrderPreferenceManager.getCategoryDisplayName(originalName)
            Triple(originalName, displayName, !OrderPreferenceManager.isCategoryHidden(originalName))
        }

        if (categoryPairs.isEmpty()) {
             Toast.makeText(requireContext(), "No custom categories found", Toast.LENGTH_SHORT).show()
             return
        }

        val displayNames = categoryPairs.map { it.second }.toTypedArray()
        val checkedItems = categoryPairs.map { it.third }.toBooleanArray()
        val currentHidden = OrderPreferenceManager.getHiddenCategories().toMutableSet()

        android.app.AlertDialog.Builder(requireContext(), android.app.AlertDialog.THEME_HOLO_DARK)
            .setTitle("Manage Category Visibility")
            .setMultiChoiceItems(displayNames, checkedItems) { _, which: Int, isChecked: Boolean ->
                val originalName = categoryPairs[which].first
                if (isChecked) {
                    currentHidden.remove(originalName)
                } else {
                    currentHidden.add(originalName)
                }
            }
            .setPositiveButton("Done") { _, _ ->
                OrderPreferenceManager.saveHiddenCategories(currentHidden)
                TVList.update(requireContext(), silent = true)
                Toast.makeText(requireContext(), "Categories updated", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Reset All") { _, _ ->
                OrderPreferenceManager.saveHiddenCategories(emptySet())
                TVList.update(requireContext(), silent = true)
                Toast.makeText(requireContext(), "All categories restored", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showRemoveSourceDialog(url: String) {
        android.app.AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Remove Source?")
            .setMessage(url)
            .setPositiveButton("Remove") { _, _ ->
                SP.removePlaylistUrl(url)
                Toast.makeText(requireContext(), "Source removed", Toast.LENGTH_SHORT).show()
                // Refresh list
                TVList.update(requireContext(), silent = false)
            }
            .setNegativeButton("Cancel") { _, _ -> showManagePlaylistsDialog() } // Re-show list
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        idleHandler.removeCallbacksAndMessages(null)
        _binding = null
    }

    private fun setupAudioLanguageDialog() {
        // Map of Display Name -> ISO 639-2/3 Code
        val languages = mapOf(
            "Default (None)" to "",
            "Hindi" to "hin",
            "English" to "eng",
            "Tamil" to "tam",
            "Telugu" to "tel",
            "Malayalam" to "mal",
            "Kannada" to "kan",
            "Bengali" to "ben",
            "Marathi" to "mar",
            "Punjabi" to "pan",
            "Gujarati" to "guj"
        )
        val languageNames = languages.keys.toTypedArray()
        val languageCodes = languages.values.toTypedArray()

        val currentCode = SP.defaultAudioLanguage
        var checkedItem = languageCodes.indexOfFirst { it == currentCode }
        if (checkedItem == -1) checkedItem = 0 // Default

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Default Audio Language")
            .setSingleChoiceItems(languageNames, checkedItem) { dialog, which ->
                val selectedCode = languageCodes[which]
                SP.defaultAudioLanguage = selectedCode
                syncStatusUI()
                
                // Show confirmation
                val display = if (selectedCode.isEmpty()) "Default" else java.util.Locale(selectedCode).displayLanguage
                Toast.makeText(context, "Audio Language set to: $display", Toast.LENGTH_SHORT).show()
                
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun showCopyrightDialog() {
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_copyright)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val btnClose = dialog.findViewById<android.widget.Button>(R.id.btn_close)
        val rootContainer = dialog.findViewById<android.view.View>(R.id.root_container)

        rootContainer.setOnClickListener {
            tvUiUtils?.playClickSound()
            dialog.dismiss()
        }
        
        btnClose.setOnClickListener {
            tvUiUtils?.playClickSound()
            dialog.dismiss()
        }
        
        btnClose.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                tvUiUtils?.playFocusSound()
            } else {
                view.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
        }
        
        dialog.show()
    }

     private fun setupEpgShiftDialog() {
         tvUiUtils?.playClickSound()
         val options = (-12..12).map { if (it >= 0) "+${it}h" else "${it}h" }.toTypedArray()
         val values = (-12..12).toList()
         
         val currentShift = SP.epgShift
         var checkedItem = values.indexOf(currentShift)
         if (checkedItem == -1) checkedItem = 12 // 0h index
 
         android.app.AlertDialog.Builder(requireContext(), android.app.AlertDialog.THEME_HOLO_DARK)
             .setTitle("Select EPG Time Shift")
             .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                 val selectedShift = values[which]
                 SP.epgShift = selectedShift
                 
                 // Refresh all EPG views
                 TVList.listModel.forEach { it.updateEPG() }
                 syncStatusUI()
                 
                 Toast.makeText(context, "EPG Shift set to: ${options[which]}", Toast.LENGTH_SHORT).show()
                 dialog.dismiss()
             }
             .setNegativeButton("Cancel", null)
             .show()
     }
 
     companion object {
        const val TAG = "SettingFragment"
        const val PERMISSION_READ = 30
        const val PERMISSIONS_REQUEST_CODE = 1
    }
}
