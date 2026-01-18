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
import java.text.SimpleDateFormat
import java.util.*


class InfoFragment : Fragment() {
    private var _binding: InfoBinding? = null
    private val binding get() = _binding!!

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
                val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
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

        val application = requireActivity().applicationContext as MyTVApplication

        // Legacy layout code removed. Sizing is now handled by XML ConstraintLayout.

        _binding!!.root.visibility = View.GONE
        return binding.root
    }

    fun show(tvViewModel: TVModel) {
        binding.title.text = tvViewModel.tv.title

        when (tvViewModel.tv.title) {
            else -> {
                if (tvViewModel.tv.logo.isNullOrBlank()) {
                    val width = Utils.dpToPx(100)
                    val height = Utils.dpToPx(60)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)

                    val paint = Paint().apply {
                        color = ContextCompat.getColor(context!!, R.color.blur)
                        textSize = 100f
                        textAlign = Paint.Align.CENTER
                    }
                    val text = "${tvViewModel.tv.id + 1}"
                    val x = width / 2f
                    val y = height / 2f - (paint.descent() + paint.ascent()) / 2
                    canvas.drawText(text, x, y, paint)

                    Glide.with(this)
                        .load(BitmapDrawable(context?.resources, bitmap))
//                        .centerInside()
                        .into(binding.logo)
                } else {
                    Glide.with(this)
                        .load(tvViewModel.tv.logo)
//                        .centerInside()
                        .into(binding.logo)
                }
            }
        }

        tvViewModel.videoQuality.observe(viewLifecycleOwner) {
            if (!it.isNullOrEmpty()) {
                binding.videoBadge.text = it
                binding.videoBadge.visibility = View.VISIBLE
            } else {
                binding.videoBadge.visibility = View.GONE
            }
        }

        tvViewModel.audioQuality.observe(viewLifecycleOwner) {
            if (!it.isNullOrEmpty()) {
                binding.audioBadge.text = it
                binding.audioBadge.visibility = View.VISIBLE
            } else {
                binding.audioBadge.visibility = View.GONE
            }
        }

        // --- Date and Time ---
        if (SP.showDateInInfo) {
            binding.dateTime.visibility = View.VISIBLE
            handler.removeCallbacks(timeRunnable)
            handler.post(timeRunnable)
        } else {
            binding.dateTime.visibility = View.GONE
            handler.removeCallbacks(timeRunnable)
        }

        // --- EPG BINDING ---
        if (SP.epgEnabled) {
            // Current Program
            val program: EPGProgram? = tvViewModel.currentProgram.value
            if (program != null) {
                binding.programTitle.text = program.title
                binding.programTitle.visibility = View.VISIBLE
                
                val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val timeRange = "${timeSdf.format(Date(program.start))} - ${timeSdf.format(Date(program.stop))}"
                binding.programTime.text = timeRange
                binding.programTime.visibility = View.VISIBLE
                
                binding.desc.text = program.description
                binding.desc.visibility = if (program.description.isNotEmpty()) View.VISIBLE else View.GONE
            } else {
                binding.programTitle.visibility = View.GONE
                binding.programTime.visibility = View.GONE
                binding.desc.text = "No current program info\nStatus: ${EPGManager.epgStatus}"
                binding.desc.visibility = View.VISIBLE
            }

            // Upcoming Program
            val nextProg: EPGProgram? = tvViewModel.upcomingProgram.value
            if (nextProg != null) {
                binding.nextProgramLabel.visibility = View.VISIBLE
                binding.nextProgramTitle.text = nextProg.title
                binding.nextProgramTitle.visibility = View.VISIBLE
                
                val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                binding.nextProgramTime.text = timeSdf.format(Date(nextProg.start))
                binding.nextProgramTime.visibility = View.VISIBLE
            } else {
                binding.nextProgramLabel.visibility = View.GONE
                binding.nextProgramTitle.visibility = View.GONE
                binding.nextProgramTime.visibility = View.GONE
            }
        } else {
            binding.programTitle.visibility = View.GONE
            binding.programTime.visibility = View.GONE
            binding.desc.text = tvViewModel.tv.group
            binding.desc.visibility = View.VISIBLE
            binding.nextProgramLabel.visibility = View.GONE
            binding.nextProgramTitle.visibility = View.GONE
            binding.nextProgramTime.visibility = View.GONE
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
