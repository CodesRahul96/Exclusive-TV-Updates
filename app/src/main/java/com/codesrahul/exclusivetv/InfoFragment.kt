package com.codesrahul.exclusivetv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.codesrahul.exclusivetv.databinding.InfoBinding
import com.codesrahul.exclusivetv.models.TVModel
import com.codesrahul.exclusivetv.models.EPGProgram
import com.codesrahul.exclusivetv.models.TVList
import com.codesrahul.exclusivetv.models.TVListModel
import com.codesrahul.exclusivetv.ui.TvUiUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class InfoFragment : Fragment() {
    private var _binding: InfoBinding? = null
    private val binding get() = _binding!!
    private var tvUiUtils: TvUiUtils? = null

    private var onChannelClickListener: (() -> Unit)? = null

    private val handler = Handler()
    private val delay: Long = 5000

    fun setOnChannelClickListener(listener: () -> Unit) {
        this.onChannelClickListener = listener
    }

    fun isShowing(): Boolean {
        return view?.visibility == View.VISIBLE
    }

    fun dismiss() {
        handler.removeCallbacks(removeRunnable)
        removeRunnable.run()
    }

    private val timeRunnable = object : Runnable {
        override fun run() {
            _binding?.let {
                val sdf = SimpleDateFormat("EEEE | MMM dd, hh:mm a", Locale.getDefault())
                it.dateTime.text = sdf.format(Date())
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            try {
                if (!isShowing()) return
                val act = activity as? MainActivity ?: return
                val webFragment = act.webFragment
                
                if (webFragment.isLive()) {
                    // LIVE TV MODE: Show EPG Progress
                    val tvModel = TVList.getTVModel() ?: return
                    val program = tvModel.currentProgram.value
                    
                    _binding?.let { b ->
                        if (SP.epgEnabled && program != null) {
                            b.progressContainer.visibility = View.VISIBLE
                            b.programProgress.visibility = View.VISIBLE
                            b.currentTimeLabel.visibility = View.GONE
                            b.totalTimeLabel.visibility = View.GONE
                            b.programProgress.isEnabled = false // Disable seeking for Live TV

                            val epgShiftMs = SP.epgShift * 3600_000L
                            val now = (Utils.getDateTimestamp() * 1000L) - epgShiftMs
                            val start = program.start
                            val stop = program.stop
                            
                            if (now in start until stop) {
                                val total = stop - start
                                val elapsed = now - start
                                val progress = (elapsed.toFloat() / total.toFloat() * 100).toInt()
                                b.programProgress.max = 100
                                b.programProgress.progress = progress.coerceIn(0, 100)
                            } else {
                                b.programProgress.progress = 0
                            }
                        } else {
                            b.progressContainer.visibility = View.GONE
                        }
                    }
                } else {
                    // VOD MODE: Show Video Progress & Enable Seeking
                    val duration = webFragment.getDuration()
                    val current = webFragment.getCurrentPosition()
                    
                    if (duration > 0) {
                        _binding?.let { b ->
                            b.progressContainer.visibility = View.VISIBLE
                            b.programProgress.visibility = View.VISIBLE
                            b.currentTimeLabel.visibility = View.VISIBLE
                            b.totalTimeLabel.visibility = View.VISIBLE
                            
                            // Enable seeking only for VOD on touch devices
                            val hasTouch = requireContext().packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
                            b.programProgress.isEnabled = hasTouch 

                            b.programProgress.max = duration.toInt()
                            if (!isSeeking) {
                                b.programProgress.progress = current.toInt()
                            }
                            
                            b.currentTimeLabel.text = Utils.formatTime(current)
                            b.totalTimeLabel.text = Utils.formatTime(duration)
                        }
                    }
                }
                
                handler.postDelayed(this, 1000)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private var isSeeking = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InfoBinding.inflate(inflater, container, false)
        tvUiUtils = TvUiUtils(requireContext())

        // Legacy layout code removed. Sizing is now handled by XML ConstraintLayout.

        _binding!!.root.visibility = View.GONE
        
        // Tap outside to close
        _binding!!.root.setOnClickListener {
            dismiss()
        }

        // Prevent dismissal when clicking the card itself
        _binding!!.infoCard.setOnClickListener {
            resetAutoHide()
        }
        
        // Handle touch events on the card to reset timer
        _binding!!.infoCard.setOnTouchListener { _, _ ->
            resetAutoHide()
            false // Continue to allow other events (like SeekBar)
        }
        
        // SeekBar Listener
        _binding!!.programProgress.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    _binding?.currentTimeLabel?.text = Utils.formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                isSeeking = true
                handler.removeCallbacks(removeRunnable) // Pause auto-hide while seeking
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                isSeeking = false
                if (seekBar != null) {
                    (requireActivity() as MainActivity).webFragment.seekTo(seekBar.progress.toLong())
                }
                handler.postDelayed(removeRunnable, delay) // Restart auto-hide
            }
        })
        
        return binding.root
    }

    private fun resetAutoHide() {
        handler.removeCallbacks(removeRunnable)
        handler.postDelayed(removeRunnable, delay)
    }

    fun show(tvViewModel: TVModel) {
        val b = _binding ?: return
        b.channelNumber.text = String.format("%04d", tvViewModel.tv.id + 1)
        b.title.text = tvViewModel.tv.title

        // Load Logo using universal LogoUtil
        LogoUtil.loadLogo(requireContext(), b.logo, tvViewModel.tv.logo, tvViewModel.tv.title)

        // Fix for symbols/icons not showing correctly on API < 23 (XML tint ignored)
        tvUiUtils?.tintTextViewDrawable(b.languageBadge, android.graphics.Color.WHITE)
        tvUiUtils?.tintTextViewDrawable(b.videoBadge, android.graphics.Color.WHITE)
        tvUiUtils?.tintTextViewDrawable(b.audioBadge, android.graphics.Color.WHITE)

        // Language Display
        if (!tvViewModel.tv.language.isNullOrEmpty()) {
            b.languageBadge.text = tvViewModel.tv.language
            b.languageBadge.visibility = View.VISIBLE
        } else {
            b.languageBadge.visibility = View.GONE
        }

        tvViewModel.videoQuality.observe(viewLifecycleOwner) {
            if (!it.isNullOrEmpty()) {
                _binding?.videoBadge?.text = it
                _binding?.videoBadge?.visibility = View.VISIBLE
            } else {
                _binding?.videoBadge?.visibility = View.GONE
            }
        }

        tvViewModel.audioQuality.observe(viewLifecycleOwner) {
            if (!it.isNullOrEmpty()) {
                _binding?.audioBadge?.text = it
                _binding?.audioBadge?.visibility = View.VISIBLE
            } else {
                _binding?.audioBadge?.visibility = View.GONE
            }
        }

        // --- Date and Time ---
        if (SP.showDateInInfo) {
            b.dateTime.visibility = View.VISIBLE
            handler.removeCallbacks(timeRunnable)
            handler.post(timeRunnable)
        } else {
            b.dateTime.visibility = View.GONE
            handler.removeCallbacks(timeRunnable)
        }

        // --- EPG BINDING ---
        if (SP.epgEnabled) {
            // Current Program Observation
            tvViewModel.currentProgram.removeObservers(viewLifecycleOwner)
            tvViewModel.currentProgram.observe(viewLifecycleOwner) { program ->
                if (program != null) {
                    b.programTitle.text = program.title
                    b.programTitle.visibility = View.VISIBLE
                    
                     val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                     val shiftMs = SP.epgShift * 3600_000L
                     val timeRange = "${timeSdf.format(Date(program.start + shiftMs))} - ${timeSdf.format(Date(program.stop + shiftMs))}"
                    b.programTime.text = timeRange
                    b.programTime.visibility = View.VISIBLE
                    
                    b.desc.text = program.description
                    b.desc.visibility = if (program.description.isNotEmpty()) View.VISIBLE else View.GONE
                    
                    // Enable Marquee
                    b.desc.isSelected = true
                    b.programTitle.isSelected = true
                } else {
                    b.programTitle.visibility = View.GONE
                    b.programTime.visibility = View.GONE
                    b.desc.text = "No current program info\nStatus: ${EPGManager.epgStatus}"
                    b.desc.visibility = View.VISIBLE
                    b.desc.isSelected = false
                    
                    // Hide progress if no EPG
                    b.progressContainer.visibility = View.GONE
                }
            }

            // Upcoming Program Observation
            tvViewModel.upcomingProgram.removeObservers(viewLifecycleOwner)
            tvViewModel.upcomingProgram.observe(viewLifecycleOwner) { nextProg ->
                if (nextProg != null) {
                    b.nextProgramLabel.visibility = View.VISIBLE
                    b.nextProgramTitle.text = nextProg.title
                    b.nextProgramTitle.visibility = View.VISIBLE
                    
                     val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                     val shiftMs = SP.epgShift * 3600_000L
                     b.nextProgramTime.text = timeSdf.format(Date(nextProg.start + shiftMs))
                    b.nextProgramTime.visibility = View.VISIBLE
                } else {
                    b.nextProgramLabel.visibility = View.GONE
                    b.nextProgramTitle.visibility = View.GONE
                    b.nextProgramTime.visibility = View.GONE
                }
            }
        } else {
            b.programTitle.visibility = View.GONE
            b.programTime.visibility = View.GONE
            b.desc.text = tvViewModel.tv.group
            b.desc.visibility = View.VISIBLE
            b.nextProgramLabel.visibility = View.GONE
            b.nextProgramTitle.visibility = View.GONE
            b.nextProgramTime.visibility = View.GONE
            b.progressContainer.visibility = View.GONE
        }
        // -------------------

        handler.removeCallbacks(removeRunnable)
        handler.removeCallbacks(updateProgressRunnable)
        view?.visibility = View.VISIBLE
        resetAutoHide()
        handler.post(updateProgressRunnable) // Start updates
    }


    override fun onResume() {
        super.onResume()
        handler.postDelayed(removeRunnable, delay)
        handler.post(updateProgressRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(removeRunnable)
        handler.removeCallbacks(timeRunnable)
        handler.removeCallbacks(updateProgressRunnable)
    }

    private val removeRunnable = Runnable {
        view?.visibility = View.GONE
        handler.removeCallbacks(timeRunnable)
        handler.removeCallbacks(updateProgressRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    companion object {
        private const val TAG = "InfoFragment"
    }
}
