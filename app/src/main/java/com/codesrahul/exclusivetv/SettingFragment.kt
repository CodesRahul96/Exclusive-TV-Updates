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

        val currentConfig = SP.config ?: ""
        binding.config.text = Editable.Factory.getInstance().newEditable(
            if (currentConfig == TVList.DEFAULT_CONFIG_URL) "" else currentConfig
        )

        binding.config.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    private fun syncStatusUI() {
        binding.statusChannelReversal.text = if (SP.channelReversal) "ON" else "OFF"
        binding.statusChannelNum.text = if (SP.channelNum) "ON" else "OFF"
        binding.statusTime.text = if (SP.time) "ON" else "OFF"
        binding.statusWatchLast.text = if (SP.watchLast) "ON" else "OFF"
        binding.statusForceHighQuality.text = if (SP.forceHighQuality) "ON" else "OFF"
        binding.statusBootStartup.text = if (SP.bootStartup) "ON" else "OFF"
        binding.statusConfigAutoLoad.text = if (SP.configAutoLoad) "ON" else "OFF"
        binding.statusChannelCheck.text = if (SP.channelCheck) "ON" else "OFF"
        binding.statusEpg.text = if (SP.epgEnabled) "ON" else "OFF"

        // Set text colors based on state
        val activeColor = ContextCompat.getColor(requireContext(), R.color.accent_gold)
        val inactiveColor = Color.parseColor("#80FFFFFF")

        val statusViews = listOf(
            binding.statusChannelReversal, binding.statusChannelNum, binding.statusTime,
            binding.statusWatchLast, binding.statusForceHighQuality, binding.statusBootStartup,
            binding.statusConfigAutoLoad, binding.statusChannelCheck, binding.statusEpg
        )

        statusViews.forEach { v ->
            v.setTextColor(if (v.text == "ON") activeColor else inactiveColor)
        }
    }

    private fun setupFocusAnimations() {
        val focusViews = listOf(
            binding.config,
            binding.confirmConfig,
            binding.cardChannelReversal,
            binding.cardChannelNum,
            binding.cardTime,
            binding.cardWatchLast,
            binding.cardForceHighQuality,
            binding.cardBootStartup,
            binding.cardConfigAutoLoad,
            binding.cardChannelCheck,
            binding.cardEpg,
            binding.clear,
            binding.checkVersion,
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
        // Card Toggles
        binding.cardChannelReversal.setOnClickListener { toggleSetting("channelReversal") }
        binding.cardChannelNum.setOnClickListener { toggleSetting("channelNum") }
        binding.cardTime.setOnClickListener { toggleSetting("time") }
        binding.cardWatchLast.setOnClickListener { toggleSetting("watchLast") }
        binding.cardForceHighQuality.setOnClickListener { toggleSetting("forceHighQuality") }
        binding.cardBootStartup.setOnClickListener { toggleSetting("bootStartup") }
        binding.cardConfigAutoLoad.setOnClickListener { toggleSetting("configAutoLoad") }
        binding.cardChannelCheck.setOnClickListener { toggleSetting("channelCheck") }
        binding.cardEpg.setOnClickListener { toggleSetting("epgEnabled") }

        binding.confirmConfig.setOnClickListener {
            tvUiUtils?.playClickSound()
            val text = binding.config.text.toString().trim()
            if (text.isEmpty()) {
                SP.config = TVList.DEFAULT_CONFIG_URL
                TVList.update(requireContext(), TVList.DEFAULT_CONFIG_URL)
                Toast.makeText(requireContext(), "Configuration reset", Toast.LENGTH_SHORT).show()
            } else {
                val url = Utils.formatUrl(text)
                uri = Uri.parse(url)
                if (uri.scheme.isNullOrEmpty()) uri = uri.buildUpon().scheme("http").build()
                if (uri.isAbsolute) {
                    if (uri.scheme == "file") requestReadPermissions()
                    else TVList.parseUri(uri)
                } else {
                    binding.config.error = "Invalid address"
                }
            }
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

        binding.closeMenu.setOnClickListener {
            hideSelf()
        }
    }

    private fun toggleSetting(key: String) {
        tvUiUtils?.playClickSound()
        when (key) {
            "channelReversal" -> SP.channelReversal = !SP.channelReversal
            "channelNum" -> SP.channelNum = !SP.channelNum
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
        }
        syncStatusUI()
    }

    private fun performFullReset() {
        try {
            // 1. Clear all Preference Managers
            SP.reset()
            OrderPreferenceManager.resetAll()

            // 2. Delete all local files (channels.txt, etc)
            deleteRecursive(requireContext().filesDir)
            
            // 3. Delete cache (epg_cache.xml.gz, etc)
            deleteRecursive(requireContext().cacheDir)

            Toast.makeText(requireContext(), "Factory Reset Complete. Restarting...", Toast.LENGTH_LONG).show()

            // 4. Force Restart App
            binding.root.postDelayed({
                requireActivity().finishAffinity()
                val intent = requireActivity().packageManager.getLaunchIntentForPackage(requireActivity().packageName)
                startActivity(intent)
            }, 1000)

        } catch (e: Exception) {
            Log.e(TAG, "Reset failed", e)
            Toast.makeText(requireContext(), "Reset failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
            TVList.parseUri(uri)
        } else {
            ActivityCompat.requestPermissions(requireActivity(), list.toTypedArray(), PERMISSION_READ)
        }
    }

    fun setServer(server: String) {
        binding.server.text = "http://$server"
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
            updateManager.checkAndUpdate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingFragment"
        const val PERMISSION_READ = 30
        const val PERMISSIONS_REQUEST_CODE = 1
    }
}
