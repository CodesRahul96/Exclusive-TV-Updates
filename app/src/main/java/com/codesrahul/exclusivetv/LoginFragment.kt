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

class LoginFragment : Fragment() {

    private var _binding: LoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private var storedVerificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var lastPhoneNumber: String? = null // Track for resend logic
    
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = Runnable {
        if (isAdded && binding.progressBar.visibility == View.VISIBLE) {
            Log.w(TAG, "Watchdog timeout triggered - no response from Firebase after 45s")
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
        auth = FirebaseAuth.getInstance()

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
            storedVerificationId = null
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
            if (code.isNotEmpty() && storedVerificationId != null) {
                verifyPhoneNumberWithCode(storedVerificationId!!, code)
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

        showLoading(true)
        binding.tvStatus.text = "Initializing verification..."
        Log.d(TAG, "Attempting OTP for: $phoneNumber (Last: $lastPhoneNumber)")

        // Start 45s watchdog timer
        watchdogHandler.removeCallbacks(watchdogRunnable)
        watchdogHandler.postDelayed(watchdogRunnable, 45000)

        try {
            val builder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(requireActivity())
                .setCallbacks(callbacks)
            
            // If same number, use resend token to bypass throttling/duplicate detection
            if (phoneNumber == lastPhoneNumber && resendToken != null) {
                Log.d(TAG, "Using ForceResendingToken for $phoneNumber")
                builder.setForceResendingToken(resendToken!!)
            }
            
            lastPhoneNumber = phoneNumber
            
            Log.d(TAG, "Calling PhoneAuthProvider.verifyPhoneNumber...")
            PhoneAuthProvider.verifyPhoneNumber(builder.build())
            Log.d(TAG, "PhoneAuthProvider.verifyPhoneNumber call dispatched.")
            
        } catch (e: Exception) {
            Log.e(TAG, "Crash starting verification: ${e.message}", e)
            watchdogHandler.removeCallbacks(watchdogRunnable)
            showLoading(false)
            binding.tvStatus.text = "Error: ${e.message}"
            Toast.makeText(requireContext(), "Verification failed to start", Toast.LENGTH_SHORT).show()
        }
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

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            watchdogHandler.removeCallbacks(watchdogRunnable)
            // Auto-retrieval or instant verification
            Log.d(TAG, "onVerificationCompleted:$credential")
            showLoading(false)
            binding.tvStatus.text = "Auto-verified!"
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            watchdogHandler.removeCallbacks(watchdogRunnable)
            Log.w(TAG, "onVerificationFailed", e)
            showLoading(false)
            binding.tvStatus.text = "Fail: ${e.message}"
            Toast.makeText(requireContext(), "Verification failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            watchdogHandler.removeCallbacks(watchdogRunnable)
            Log.d(TAG, "onCodeSent:$verificationId")
            storedVerificationId = verificationId
            resendToken = token

            showLoading(false)
            binding.tvStatus.text = "OTP Sent Successfully"
            
            // Switch UI to OTP mode
            binding.etPhone.isEnabled = false
            binding.btnSendOtp.visibility = View.GONE
            binding.etOtp.visibility = View.VISIBLE
            binding.tvChangeNumber.visibility = View.VISIBLE
            binding.btnVerify.visibility = View.VISIBLE
            binding.etOtp.requestFocus()
        }

        override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
            watchdogHandler.removeCallbacks(watchdogRunnable)
            Log.d(TAG, "onCodeAutoRetrievalTimeOut:$verificationId")
            if (binding.etOtp.visibility == View.VISIBLE) {
                // Already sent, just auto-retrieval timed out
                return
            }
            showLoading(false)
            binding.tvStatus.text = "OTP Retrieval Timed Out"
        }
    }

    private fun verifyPhoneNumberWithCode(verificationId: String, code: String) {
        showLoading(true)
        binding.tvStatus.text = "Verifying Code..."
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    checkSubscription()
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    showLoading(false)
                    binding.tvStatus.text = "Sign In Failed"
                    Toast.makeText(requireContext(), "Authentication Failed", Toast.LENGTH_SHORT).show()
                }
            }
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
