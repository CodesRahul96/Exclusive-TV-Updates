package com.codesrahul.exclusivetv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.codesrahul.exclusivetv.databinding.FragmentMaintenanceBinding

class MaintenanceFragment : Fragment() {
    private var _binding: FragmentMaintenanceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMaintenanceBinding.inflate(inflater, container, false)
        
        val activity = requireActivity() as MainActivity
        binding.appVersion.text = "Exclusive TV ${activity.appVersionName}"

        // No custom scaling, use XML sizes for best clarity

        // Exit App button logic (robust)
        binding.btnExit.setOnClickListener {
            it.isEnabled = false
            val act = activity
            try {
                android.widget.Toast.makeText(requireContext(), "Exiting app...", android.widget.Toast.LENGTH_SHORT).show()
                act?.finishAffinity()
                act?.finish()
                android.os.Handler().postDelayed({
                    System.exit(0)
                }, 500)
            } catch (e: Exception) {
                e.printStackTrace()
                System.exit(0)
            }
        }

        // Request focus for Exit button by default (for TV/remote)
        binding.btnExit.isFocusableInTouchMode = true
        binding.btnExit.requestFocus()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = MaintenanceFragment()
    }
}
