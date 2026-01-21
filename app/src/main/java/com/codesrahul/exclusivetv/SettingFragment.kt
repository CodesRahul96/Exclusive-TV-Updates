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

class SettingFragment : Fragment() {

    private var _binding: SettingBinding? = null
    private val binding get() = _binding!!

    private lateinit var uri: Uri
    private lateinit var updateManager: UpdateManager
    private var tvUiUtils: TvUiUtils? = null

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
        
        syncStatusUI()

        binding.config.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }

        binding.versionName.text = "v${com.codesrahul.exclusivetv.BuildConfig.VERSION_NAME}"
        
        // IP & MAC Address display
        val ip = Utils.getIPAddress(true)
        val mac = Utils.getMacAddress()
        
        val displayIp = if (ip.isEmpty()) "Unavailable" else ip
        val displayMac = if (mac.isEmpty() || mac == "02:00:00:00:00:00") "Unavailable" else mac
        
        binding.deviceInfo.text = "IP: $displayIp  |  MAC: $displayMac"
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
        binding.statusWatermark.text = if (SP.watermarkEnabled) "ON" else "OFF"

        // Set text colors based on state
        val activeColor = ContextCompat.getColor(requireContext(), R.color.accent_gold)
        val inactiveColor = Color.parseColor("#80FFFFFF")

        val statusViews = listOf(
            binding.statusChannelReversal, binding.statusChannelNum, binding.statusTime,
            binding.statusShowDateInInfo,
            binding.statusWatchLast, binding.statusForceHighQuality, binding.statusBootStartup,
            binding.statusConfigAutoLoad, binding.statusChannelCheck, binding.statusEpg,
            binding.statusWatermark, binding.statusBufferMode, binding.statusAudioStabilizer
        )

        statusViews.forEach { v ->
            v.setTextColor(if (v.text == "ON" || v.text.toString().startsWith("Mode")) activeColor else inactiveColor)
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
            binding.config,
            binding.confirmConfig,
            binding.cardChannelReversal,
            binding.cardChannelNum,
            binding.cardShowDateInInfo,
            binding.cardTime,
            binding.cardWatchLast,
            binding.cardForceHighQuality,
            binding.cardBootStartup,
            binding.cardConfigAutoLoad,
            binding.cardChannelCheck,
            binding.cardChannelCheck,
            binding.cardEpg,
            binding.cardWatermark,
            binding.cardBufferMode,
            binding.cardAudioLanguage,
            binding.cardAudioLanguage,
            binding.cardAudioStabilizer,
            binding.manageCategories,
            binding.clear,
            binding.clear,
            binding.checkVersion,
            binding.copyrightInfo,
            binding.closeMenu
        )

        focusViews.forEach { v ->
            v.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start()
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
        // Developer Link
        binding.developer.setOnClickListener {
            try {
                tvUiUtils?.playClickSound()
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://github.com/CodesRahul96"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Could not open link", Toast.LENGTH_SHORT).show()
            }
        }

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
        binding.cardWatermark.setOnClickListener { toggleSetting("watermark") }
        binding.cardBufferMode.setOnClickListener { toggleSetting("bufferMode") }
        binding.cardAudioLanguage.setOnClickListener { setupAudioLanguageDialog() }
        binding.cardAudioStabilizer.setOnClickListener { toggleSetting("audioStabilizer") }

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
            
            android.app.AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Factory Reset")
                .setMessage("This will delete ALL data, including favorites, custom URLs, and cached channels. The app will restart. Continue?")
                .setPositiveButton("Reset Everything") { _, _ ->
                    performFullReset()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.checkVersion.setOnClickListener {
            tvUiUtils?.playClickSound()
            requestInstallPermissions()
        }

        binding.copyrightInfo.setOnClickListener {
            tvUiUtils?.playClickSound()
            showCopyrightDialog()
        }

        binding.closeMenu.setOnClickListener {
            hideSelf()
        }
    }

    private fun toggleSetting(key: String) {
        tvUiUtils?.playClickSound()
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
                (activity as? MainActivity)?.updateWatermarkVisibility()
            }
            "bufferMode" -> {
                var current = SP.bufferMode
                current = (current + 1) % 3 // 0->1->2->0
                SP.bufferMode = current
                Toast.makeText(requireContext(), "Buffering: " + arrayOf("Default", "Max Stability", "Low Latency")[current] + " (Restart stream to apply)", Toast.LENGTH_SHORT).show()
            }
            "audioStabilizer" -> {
                SP.audioStabilizer = !SP.audioStabilizer
                Toast.makeText(requireContext(), if (SP.audioStabilizer) "Audio Stabilizer Enabled" else "Audio Stabilizer Disabled", Toast.LENGTH_SHORT).show()
            }
        }
        syncStatusUI()
    }

    private fun performFullReset() {
        try {
            Toast.makeText(requireContext(), "Resetting...", Toast.LENGTH_SHORT).show()
            
            // 1. Clear all Preference Managers
            try { SP.reset() } catch (e: Exception) { e.printStackTrace() }
            try { OrderPreferenceManager.resetAll() } catch (e: Exception) { e.printStackTrace() }

            // 2. Delete all local files (channels.txt, etc)
            try { deleteRecursive(requireContext().filesDir) } catch (e: Exception) { e.printStackTrace() }
            
            // 3. Delete cache (epg_cache.xml.gz, etc)
            try { deleteRecursive(requireContext().cacheDir) } catch (e: Exception) { e.printStackTrace() }
            
            // 4. Clear WebViews/Cookies if any
            try { android.webkit.WebStorage.getInstance().deleteAllData() } catch (e: Exception) {}

            Toast.makeText(requireContext(), "Factory Reset Complete. Restarting...", Toast.LENGTH_LONG).show()

            // 5. Force Restart App
            val ctx = context ?: return
            binding.root.postDelayed({
                try {
                    val pm = ctx.packageManager
                    val intent = pm.getLaunchIntentForPackage(ctx.packageName)
                    if (intent != null) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                    activity?.finishAffinity()
                    System.exit(0)
                } catch (e: Exception) {
                    e.printStackTrace()
                    System.exit(0)
                }
            }, 1000)

        } catch (e: Exception) {
            Log.e(TAG, "Reset failed", e)
            Toast.makeText(requireContext(), "Reset failed: ${e.message}", Toast.LENGTH_SHORT).show()
            // Try to crash/restart anyway to clear state
            System.exit(0) 
        }
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
            val mac = Utils.getMacAddress()
            val displayMac = if (mac.isEmpty() || mac == "02:00:00:00:00:00") "Unavailable" else mac
            
            // Show full server link as it contains the port
            binding.deviceInfo.text = "Server: http://$server  |  MAC: $displayMac"
            Log.i(TAG, "Server UI Updated: http://$server")
        }
    }

    fun setVersionName(versionName: String) {
        binding.versionName.text = versionName
    }

    private fun hideSelf() {
        // Add slide-out animation logic or just hide
        requireActivity().supportFragmentManager.beginTransaction()
            .setCustomAnimations(0, R.anim.slide_out_right)
            .hide(this)
            .commit()
        (activity as MainActivity).showTime()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            binding.container.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.slide_in_right))
            setupUI()
        }
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
            updateManager.checkAndUpdate(isManualCheck = true)
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

    companion object {
        const val TAG = "SettingFragment"
        const val PERMISSION_READ = 30
        const val PERMISSIONS_REQUEST_CODE = 1
    }
}
