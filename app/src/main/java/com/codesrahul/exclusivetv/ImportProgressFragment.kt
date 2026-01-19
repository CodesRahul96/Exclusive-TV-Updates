package com.codesrahul.exclusivetv

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.codesrahul.exclusivetv.databinding.FragmentImportProgressBinding

class ImportProgressFragment : Fragment() {

    private var _binding: FragmentImportProgressBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImportProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun setProgress(progress: Int) {
        if (_binding == null) return
        binding.tvPercent.text = "$progress%"
        
        // Smooth progress animation
        val current = binding.progressBar.progress
        if (progress > current) {
             val animation = ObjectAnimator.ofInt(binding.progressBar, "progress", current, progress)
             animation.duration = 300
             animation.interpolator = DecelerateInterpolator()
             animation.start()
        } else {
             binding.progressBar.progress = progress
        }
    }

    fun setStatus(status: String) {
        if (_binding == null) return
        binding.tvStatus.text = status
    }

    // Deprecated / unused animation method removed for clarity as we use internal logic now
    fun animateProgress(from: Int, to: Int, duration: Long = 500) {
        // Kept empty compatibility or remove if safe. Let's redirect to setProgress.
        setProgress(to)
    }
}
