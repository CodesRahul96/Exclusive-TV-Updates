package com.codesrahul.exclusivetv

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import com.codesrahul.exclusivetv.databinding.LoginBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper
import android.content.pm.ActivityInfo

class LoginFragment : Fragment() {

    private var _binding: LoginBinding? = null
    private val binding get() = _binding!!

    private var lastPhoneNumber: String? = null // Track for resend logic
    
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = Runnable {
        if (isAdded && binding.progressBar.visibility == View.VISIBLE) {
            showLoading(false)
            binding.tvStatus.text = "Verification taking too long. Please check your internet and try again."
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initial Button States
        binding.btnSendOtp.isEnabled = false
        binding.btnVerify.isEnabled = false

        // Phone Input Validation & Auto-Focus
        binding.etPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString()?.trim() ?: ""
                val isValid = input.length == 10
                val wasEnabled = binding.btnSendOtp.isEnabled
                binding.btnSendOtp.isEnabled = isValid
                if (isValid && !wasEnabled) {
                    binding.btnSendOtp.requestFocus()
                    binding.btnSendOtp.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    binding.btnSendOtp.startAnimation(AnimationUtils.loadAnimation(requireContext(), android.R.anim.fade_in))
                    hideKeyboard()
                }
            }
        })

        binding.etPhone.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                if (binding.btnSendOtp.isEnabled) {
                    binding.btnSendOtp.performClick()
                    true
                } else false
            } else false
        }

        binding.btnSendOtp.setOnClickListener {
            val digits = binding.etPhone.text.toString().trim()
            if (digits.length == 10) {
                startPhoneNumberVerification("+91$digits")
            }
        }

        // OTP Input Validation & Auto-Focus
        binding.etOtp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString()?.trim() ?: ""
                val isValid = input.length == 6
                val wasEnabled = binding.btnVerify.isEnabled
                binding.btnVerify.isEnabled = isValid
                if (isValid && !wasEnabled) {
                    binding.btnVerify.requestFocus()
                    binding.btnVerify.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    binding.btnVerify.startAnimation(AnimationUtils.loadAnimation(requireContext(), android.R.anim.fade_in))
                    hideKeyboard()
                }
            }
        })

        binding.etOtp.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (binding.btnVerify.isEnabled) {
                    binding.btnVerify.performClick()
                    true
                } else false
            } else false
        }

        // Change Number Logic
        binding.tvChangeNumber.setOnClickListener {
            // Note: We don't reset resendToken or lastPhoneNumber here 
            // to allow ForceResendingToken to work if they type the same number again.
            binding.etPhone.isEnabled = true
            binding.etOtp.text.clear()
            binding.tvStatus.text = ""
            binding.btnSendOtp.visibility = View.VISIBLE
            binding.btnSendOtp.isEnabled = binding.etPhone.text.toString().trim().length == 10
            binding.etOtp.visibility = View.GONE
            binding.btnVerify.visibility = View.GONE
            binding.tvChangeNumber.visibility = View.GONE
            binding.etPhone.requestFocus()
        }

        binding.btnVerify.setOnClickListener {
            val code = binding.etOtp.text.toString().trim()
            if (code.isNotEmpty()) {
                verifyPhoneNumberWithCode("custom_auth_session", code)
            } else {
                Toast.makeText(requireContext(), "Enter OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPhoneNumberVerification(phoneNumber: String) {
        if (binding.progressBar.visibility == View.VISIBLE) return 
        
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "Internet connection required", Toast.LENGTH_SHORT).show()
            return
        }

        // --- REMOTE CONFIG PRE-CHECK (fast UI feedback) ---
        // SP.registrationEnabled comes from Firebase Remote Config, fetched on app start.
        // This is NOT the security gate — it's an early UX hint to avoid unnecessary Firestore reads.
        // The real bypass-proof enforcement happens server-side in verifyPhoneNumberWithCode().
        if (!SP.registrationEnabled) {
            binding.tvStatus.text = "Registrations are currently closed"
            Toast.makeText(
                requireContext(),
                "New registrations are currently closed. Please contact support.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        lastPhoneNumber = phoneNumber
        
        // Skip Firebase entirely. Just show OTP input screen immediately.
        showLoading(false)
        binding.tvStatus.text = "Enter OTP to login"
        
        // Switch UI to OTP mode
        binding.etPhone.isEnabled = false
        binding.btnSendOtp.visibility = View.GONE
        binding.etOtp.visibility = View.VISIBLE
        binding.tvChangeNumber.visibility = View.VISIBLE
        binding.btnVerify.visibility = View.VISIBLE
        binding.etOtp.requestFocus()
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }


    private fun verifyPhoneNumberWithCode(verificationId: String, code: String) {
        val phoneNumber = lastPhoneNumber ?: return
        showLoading(true)
        binding.tvStatus.text = "Verifying Code..."
        
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        db.collection("users").document(phoneNumber).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val customOtp = document.getString("custom_otp")
                    
                    // Validation Logic:
                    // 1. If custom_otp is set in DB, the user MUST enter that specific OTP.
                    // 2. If custom_otp is not set or empty, the user can log in with the default "123321".
                    val isValid = if (customOtp.isNullOrEmpty()) {
                        code == "123321"
                    } else {
                        code == customOtp
                    }
                    
                    if (isValid) {
                        signInWithPhoneAuthCredential(phoneNumber)
                    } else {
                        showLoading(false)
                        binding.tvStatus.text = "Invalid OTP"
                        Toast.makeText(requireContext(), "Incorrect OTP or PIN", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Document doesn't exist -> New user trying to log in.

                    // --- SERVER-SIDE REGISTRATION GATE ---
                    // Read app_config/settings from Firestore. This check cannot be bypassed
                    // by patching the APK — the decision comes from the server.
                    db.collection("app_config").document("settings").get()
                        .addOnSuccessListener { configDoc ->
                            // Default to true (open) if the field doesn't exist yet
                            val registrationOpen = configDoc.getBoolean("registration_enabled") ?: true

                            if (!registrationOpen) {
                                showLoading(false)
                                binding.tvStatus.text = "Registrations are currently closed"
                                Toast.makeText(
                                    requireContext(),
                                    "New registrations are currently closed. Please contact support.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                // Registration is open — verify with default OTP
                                if (code == "123321") {
                                    signInWithPhoneAuthCredential(phoneNumber)
                                } else {
                                    showLoading(false)
                                    binding.tvStatus.text = "Invalid OTP"
                                    Toast.makeText(requireContext(), "Incorrect Default OTP", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            // Firestore unreachable — fail-close (block) to prevent bypass via offline mode
                            showLoading(false)
                            binding.tvStatus.text = "Could not verify registration status"
                            Toast.makeText(
                                requireContext(),
                                "Server unreachable. Please check your connection and try again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                binding.tvStatus.text = "Verification Failed"
                Toast.makeText(requireContext(), "Network Error. Please try again later.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun signInWithPhoneAuthCredential(phoneNumber: String) {
        // We effectively bypass Firebase Auth sign in.
        // Save the phone number into SharedPreferences (SP.userId) instead.
        SP.userId = phoneNumber
        checkSubscription()
    }

    private fun checkSubscription() {
        binding.tvStatus.text = "Checking Subscription..."
        SubscriptionManager.checkSubscription(
            onSuccess = {
                // Navigate to Main Content
                showLoading(false)
                Toast.makeText(requireContext(), "Welcome Back!", Toast.LENGTH_SHORT).show()
                (activity as? MainActivity)?.onLoginSuccess()
            },
            onError = { error ->
                showLoading(false)
                binding.tvStatus.text = error
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) {
            binding.btnSendOtp.isEnabled = false
            binding.btnVerify.isEnabled = false
        } else {
            // Restore based on input length
            binding.btnSendOtp.isEnabled = binding.etPhone.text.toString().trim().length == 10
            binding.btnVerify.isEnabled = binding.etOtp.text.toString().trim().length == 6
        }
    }

    private fun hideKeyboard() {
        try {
            val view = activity?.currentFocus
            if (view != null) {
                val imm = activity?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            // Force sensor-based rotation (ignores system auto-rotate setting) for Login
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            // Restore to the app's default landscape orientation with a slight delay
            // This allows the keyboard to hide and the fragment to transition out smoothly
            // before the entire screen is forced to flip sideways.
            Handler(Looper.getMainLooper()).postDelayed({
                if (isAdded) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }, 500)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // This ensures the layout cleanly remeasures when switching between portrait/landscape
        // without destroying the fragment, utilizing the animateLayoutChanges flag in XML.
        view?.requestLayout()
    }

    override fun onPause() {
        super.onPause()
        watchdogHandler.removeCallbacks(watchdogRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "LoginFragment"
    }
}
