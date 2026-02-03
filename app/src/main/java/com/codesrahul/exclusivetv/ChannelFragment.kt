package com.codesrahul.exclusivetv

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.marginEnd
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import com.codesrahul.exclusivetv.databinding.ChannelBinding
import com.codesrahul.exclusivetv.models.TVModel

class ChannelFragment : Fragment() {
    private var _binding: ChannelBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler()
    private val delay: Long = 3000
    private var channel = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ChannelBinding.inflate(inflater, container, false)
        _binding!!.root.visibility = View.GONE

        val application = requireActivity().applicationContext as MyTVApplication



        val layoutParams = binding.channel.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.topMargin = application.px2Px(binding.channel.marginTop)
        layoutParams.marginEnd = application.px2Px(binding.channel.marginEnd)
        binding.channel.layoutParams = layoutParams

        binding.content.textSize = application.px2PxFont(binding.content.textSize)


        binding.main.layoutParams.width = application.shouldWidthPx()
        binding.main.layoutParams.height = application.shouldHeightPx()

        return binding.root
    }

    fun show(tvViewModel: TVModel) {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(playRunnable)
        binding.content.text = (tvViewModel.tv.id.plus(1)).toString()
        view?.visibility = View.VISIBLE
        handler.postDelayed(hideRunnable, delay)
    }

    fun show(channel: String) {
        if (binding.content.text.length >= 4) {
            return
        }
        
        val currentText = binding.content.text.toString()
        val textValue = "$currentText$channel"
        this.channel = try { textValue.toInt() } catch (e: Exception) { 0 }
        
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(playRunnable)
        
        binding.content.text = textValue
        view?.visibility = View.VISIBLE
        handler.postDelayed(playRunnable, delay)
    }

    fun isNumberEntering(): Boolean {
        return view?.visibility == View.VISIBLE && binding.content.text.isNotEmpty()
    }

    fun playNow() {
        handler.removeCallbacks(playRunnable)
        playRunnable.run()
    }

    override fun onResume() {
        super.onResume()
        if (view?.visibility == View.VISIBLE) {
            handler.postDelayed(hideRunnable, delay)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(playRunnable)
    }

    private val hideRunnable = Runnable {
        _binding?.let {
            it.content.text = ""
            view?.visibility = View.GONE
        }
    }


    private val playRunnable = Runnable {
        val currentActivity = activity as? MainActivity
        if (currentActivity != null && _binding != null) {
            currentActivity.play(channel - 1)
            handler.postDelayed(hideRunnable, delay)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }


    companion object {
        private const val TAG = "ChannelFragment"
    }
}
