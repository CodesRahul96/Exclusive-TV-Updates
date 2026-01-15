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
        
        if (progress > 0 && progress < 10) {
            binding.tvPercent.text = "Connecting..."
        } else {
            binding.tvPercent.text = "$progress%"
        }
        binding.progressBar.progress = progress
    }

    fun animateProgress(from: Int, to: Int, duration: Long = 500) {
        if (_binding == null) return
        
        val progressBar = binding.progressBar
        val currentProgress = progressBar.progress
        
        // Don't animate backwards unless explicitly resetting
        if (to > currentProgress || (from == 0 && to == 0)) {
            val animation = ObjectAnimator.ofInt(progressBar, "progress", from, to)
            animation.duration = duration
            animation.interpolator = DecelerateInterpolator()
            animation.addUpdateListener { 
                val value = it.animatedValue as Int
                if (value > 0 && value < 10) {
                     binding.tvPercent.text = "Connecting..."
                } else {
                     binding.tvPercent.text = "$value%"
                }
            }
            animation.start()
        }
    }
}
