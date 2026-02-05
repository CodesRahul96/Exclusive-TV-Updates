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
        
        // Scale UI for Android TV if needed (using existing px2Px logic if applicable)
        val application = requireActivity().applicationContext as MyTVApplication
        binding.maintenanceIcon.layoutParams.width = application.px2Px(120)
        binding.maintenanceIcon.layoutParams.height = application.px2Px(120)
        binding.maintenanceTitle.textSize = application.px2PxFont(32f)
        binding.maintenanceMessage.textSize = application.px2PxFont(18f)
        
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
