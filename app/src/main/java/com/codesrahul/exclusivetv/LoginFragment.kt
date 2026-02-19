package com.codesrahul.exclusivetv

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.codesrahul.exclusivetv.databinding.LoginBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class LoginFragment : Fragment() {

    private var _binding: LoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private var storedVerificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

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

        binding.btnSendOtp.setOnClickListener {
            val rawInput = binding.etPhone.text.toString().trim()
            
            // Remove any non-digit characters (except + if they typed it)
            val digits = rawInput.replace(Regex("[^0-9]"), "")
            
            var validNumber = ""
            
            if (digits.length == 10) {
                // Correct 10-digit Indian number being entered
                validNumber = "+91$digits"
            } else if (digits.length == 12 && digits.startsWith("91")) {
                // User typed 91999...
                validNumber = "+$digits"
            } else {
                // Invalid length for India
                Toast.makeText(requireContext(), "Please enter a valid 10-digit Indian mobile number", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            startPhoneNumberVerification(validNumber)
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
        showLoading(true)
        binding.tvStatus.text = "Sending OTP..."

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)       // Phone number to verify
            .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
            .setActivity(requireActivity())    // Activity (for callback binding)
            .setCallbacks(callbacks)          // OnVerificationStateChangedCallbacks
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto-retrieval or instant verification
            Log.d(TAG, "onVerificationCompleted:$credential")
            showLoading(false)
            binding.tvStatus.text = "Auto-verifying..."
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.w(TAG, "onVerificationFailed", e)
            showLoading(false)
            binding.tvStatus.text = "Verification Failed: ${e.message}"
            Toast.makeText(requireContext(), "Verification Failed", Toast.LENGTH_SHORT).show()
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            Log.d(TAG, "onCodeSent:$verificationId")
            storedVerificationId = verificationId
            resendToken = token

            showLoading(false)
            binding.tvStatus.text = "OTP Sent"
            
            // Switch UI to OTP mode
            binding.etPhone.isEnabled = false
            binding.btnSendOtp.isEnabled = false
            binding.etOtp.visibility = View.VISIBLE
            binding.btnVerify.visibility = View.VISIBLE
            binding.etOtp.requestFocus()
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
        binding.btnSendOtp.isEnabled = !isLoading
        binding.btnVerify.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "LoginFragment"
    }
}
