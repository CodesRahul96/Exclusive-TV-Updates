package com.codesrahul.exclusivetv

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.fragment.app.Fragment
// Use manual binding since ViewBinding might not be regenerated yet for the new layout
// import com.codesrahul.exclusivetv.databinding.FragmentImportProgressBinding 

class ImportProgressFragment : Fragment() {

    // private var _binding: FragmentImportProgressBinding? = null
    // private val binding get() = _binding!!

    private var tvStatus: TextView? = null
    private var tvPercent: TextView? = null
    private var progressBar: android.widget.ProgressBar? = null
    private var cardView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the new top-right card layout
        val view = inflater.inflate(R.layout.card_update_notification, container, false)
        
        tvStatus = view.findViewById(R.id.tv_status)
        tvPercent = view.findViewById(R.id.tv_percent)
        progressBar = view.findViewById(R.id.progress_bar)
        cardView = view.findViewById(R.id.notification_card)
        
        return view
    }
    
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            // Slide IN from Top
            cardView?.startAnimation(AnimationUtils.loadAnimation(context, R.anim.slide_in_top))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tvStatus = null
        tvPercent = null
        progressBar = null
        cardView = null
    }

    fun setProgress(progress: Int) {
        if (!isAdded) return
        
        tvPercent?.text = "$progress%"
        
        val current = progressBar?.progress ?: 0
        if (progressBar != null) {
            val pb = progressBar!!
            if (progress > current) {
                val animation = ObjectAnimator.ofInt(pb, "progress", current, progress)
                animation.duration = 300
                animation.interpolator = DecelerateInterpolator()
                animation.start()
            } else {
                pb.progress = progress
            }
        }
    }

    fun setStatus(status: String) {
        if (!isAdded) return
        tvStatus?.text = status
    }
    
    // Compatibility
    fun animateProgress(from: Int, to: Int, duration: Long = 500) {
        setProgress(to)
    }
}
