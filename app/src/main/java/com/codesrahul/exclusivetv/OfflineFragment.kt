package com.codesrahul.exclusivetv

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.codesrahul.exclusivetv.ui.TvUiUtils

class OfflineFragment : Fragment() {

    private var tvUiUtils: TvUiUtils? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_offline, container, false)
        
        tvUiUtils = TvUiUtils(requireContext())
        tvUiUtils?.initSounds(R.raw.focus, R.raw.click)

        val btnRetry = view.findViewById<Button>(R.id.btn_retry)
        val btnSettings = view.findViewById<Button>(R.id.btn_settings)

        setupButton(btnRetry) {
            tvUiUtils?.playClickSound()
            if (isNetworkAvailable()) {
                (activity as? MainActivity)?.hideOfflineScreen()
            } else {
                Toast.makeText(requireContext(), "Still no connection...", Toast.LENGTH_SHORT).show()
            }
        }

        setupButton(btnSettings) {
            tvUiUtils?.playClickSound()
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (e: Exception) {
                // Fallback for some TV boxes that don't support WIFI_SETTINGS intent
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        return view
    }

    private fun setupButton(button: Button, action: () -> Unit) {
        button.setOnClickListener { action() }
        button.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                tvUiUtils?.playFocusSound()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
