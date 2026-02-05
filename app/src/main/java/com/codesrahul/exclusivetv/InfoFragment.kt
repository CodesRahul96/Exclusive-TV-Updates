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
import java.util.*


class InfoFragment : Fragment() {
    private var _binding: InfoBinding? = null
    private val binding get() = _binding!!
    private var tvUiUtils: TvUiUtils? = null

    private val handler = Handler()
    private val delay: Long = 5000

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InfoBinding.inflate(inflater, container, false)
        tvUiUtils = TvUiUtils(requireContext())

        val application = requireActivity().applicationContext as MyTVApplication

        // Legacy layout code removed. Sizing is now handled by XML ConstraintLayout.

        _binding!!.root.visibility = View.GONE
        return binding.root
    }

    fun show(tvViewModel: TVModel) {
        val b = _binding ?: return
        b.channelNumber.text = String.format("%03d", tvViewModel.tv.id + 1)
        b.title.text = tvViewModel.tv.title

        LogoUtil.loadLogo(requireContext(), b.logo, tvViewModel.tv.logo, tvViewModel.tv.title)

        // Fix for symbols/icons not showing correctly on API < 23 (XML tint ignored)
        tvUiUtils?.tintTextViewDrawable(b.videoBadge, android.graphics.Color.WHITE)
        tvUiUtils?.tintTextViewDrawable(b.audioBadge, android.graphics.Color.WHITE)

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
            // Current Program
            val program: EPGProgram? = tvViewModel.currentProgram.value
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
                
                // --- Progress Calculation ---
                val now = System.currentTimeMillis()
                val start = program.start
                val stop = program.stop
                if (now in start..stop) {
                    val total = stop - start
                    val elapsed = now - start
                    val progress = (elapsed.toFloat() / total.toFloat() * 100).toInt()
                    b.programProgress.progress = progress
                    b.programProgress.visibility = View.VISIBLE
                } else {
                    b.programProgress.visibility = View.GONE
                }
                
                // Enable Marquee
                b.desc.isSelected = true
                b.programTitle.isSelected = true
            } else {
                b.programTitle.visibility = View.GONE
                b.programTime.visibility = View.GONE
                b.programProgress.visibility = View.GONE
                b.desc.text = "No current program info\nStatus: ${EPGManager.epgStatus}"
                b.desc.visibility = View.VISIBLE
                b.desc.isSelected = false
            }

            // Upcoming Program
            val nextProg: EPGProgram? = tvViewModel.upcomingProgram.value
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
        } else {
            b.programTitle.visibility = View.GONE
            b.programTime.visibility = View.GONE
            b.desc.text = tvViewModel.tv.group
            b.desc.visibility = View.VISIBLE
            b.nextProgramLabel.visibility = View.GONE
            b.nextProgramTitle.visibility = View.GONE
            b.nextProgramTime.visibility = View.GONE
        }
        // -------------------

        handler.removeCallbacks(removeRunnable)
        view?.visibility = View.VISIBLE
        handler.postDelayed(removeRunnable, delay)
    }


    override fun onResume() {
        super.onResume()
        handler.postDelayed(removeRunnable, delay)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(removeRunnable)
    }

    private val removeRunnable = Runnable {
        view?.visibility = View.GONE
        handler.removeCallbacks(timeRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    companion object {
        private const val TAG = "InfoFragment"
    }
}
