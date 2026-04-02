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
import com.codesrahul.exclusivetv.models.TVList
import com.codesrahul.exclusivetv.models.TVModel

class ChannelFragment : Fragment() {
    private var _binding: ChannelBinding? = null
    private val binding get() = _binding!!

    private val zapHandler = Handler(android.os.Looper.getMainLooper())
    private var pendingChannelIndex = 0
    private var isManualEntry = false

    companion object {
        private const val TAG = "ChannelFragment"
        private const val ZAP_DELAY_MS: Long = 3000
        private const val MAX_DIGITS = 4
    }

    fun isShowing(): Boolean {
        return view?.visibility == View.VISIBLE
    }

    fun dismiss() {
        zapHandler.removeCallbacks(hideRunnable)
        zapHandler.removeCallbacks(playRunnable)
        _binding?.let {
            it.content.text = ""
            isManualEntry = false
            view?.visibility = View.GONE
        }
    }

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

        setupKeypad()

        return binding.root
    }

    private fun setupKeypad() {
        val buttons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6, binding.btn7,
            binding.btn8, binding.btn9
        )

        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                show(index.toString())
            }
        }

        binding.btnDel.setOnClickListener {
            val currentText = binding.content.text.toString()
            if (currentText.isNotEmpty()) {
                val newText = currentText.substring(0, currentText.length - 1)
                binding.content.text = newText
                this.pendingChannelIndex = try { newText.toInt() } catch (e: Exception) { 0 }
                updatePreview()
                resetAutoHide()
            }
        }

        binding.btnOk.setOnClickListener {
            if (binding.content.text.isNotEmpty()) {
                playNow()
            }
        }
    }

    fun show(tvViewModel: TVModel) {
        zapHandler.removeCallbacks(hideRunnable)
        zapHandler.removeCallbacks(playRunnable)
        binding.content.text = String.format("%04d", tvViewModel.tv.id + 1)
        isManualEntry = false
        updatePreview()
        view?.visibility = View.VISIBLE
        zapHandler.postDelayed(hideRunnable, ZAP_DELAY_MS)
    }

    fun show(digit: String) {
        if (!isManualEntry) {
            binding.content.text = ""
            isManualEntry = true
        }

        if (binding.content.text.length >= MAX_DIGITS) {
            return
        }
        
        val currentText = binding.content.text.toString()
        val textValue = "$currentText$digit"
        this.pendingChannelIndex = try { textValue.toInt() } catch (e: Exception) { 0 }
        
        zapHandler.removeCallbacks(hideRunnable)
        zapHandler.removeCallbacks(playRunnable)
        
        binding.content.text = textValue
        view?.visibility = View.VISIBLE
        updatePreview()

        // Smart Zapping: If max digits entered, play immediately
        if (textValue.length >= MAX_DIGITS) {
            playNow()
        } else {
            resetAutoHide()
            zapHandler.postDelayed(playRunnable, ZAP_DELAY_MS)
        }
    }

    private fun updatePreview() {
        val index = pendingChannelIndex - 1
        if (index >= 0 && index < TVList.listModel.size) {
            val model = TVList.listModel[index]
            binding.previewTitle.text = model.tv.title
            LogoUtil.loadLogo(requireContext(), binding.previewLogo, model.tv.logo, model.tv.title)
            binding.previewContainer.visibility = View.VISIBLE
        } else {
            binding.previewContainer.visibility = View.GONE
        }
    }

    fun showKeypad() {
        binding.numPad.visibility = View.VISIBLE
        if (!isManualEntry) {
            binding.content.text = ""
            isManualEntry = true
        }
        view?.visibility = View.VISIBLE
        resetAutoHide()
    }

    private fun resetAutoHide() {
        zapHandler.removeCallbacks(hideRunnable)
        zapHandler.postDelayed(hideRunnable, ZAP_DELAY_MS)
    }

    fun isNumberEntering(): Boolean {
        return view?.visibility == View.VISIBLE && binding.content.text.isNotEmpty()
    }

    fun playNow() {
        zapHandler.removeCallbacks(playRunnable)
        playRunnable.run()
    }

    override fun onResume() {
        super.onResume()
        if (view?.visibility == View.VISIBLE) {
            zapHandler.postDelayed(hideRunnable, ZAP_DELAY_MS)
        }
    }

    override fun onPause() {
        super.onPause()
        zapHandler.removeCallbacks(hideRunnable)
        zapHandler.removeCallbacks(playRunnable)
    }

    private val hideRunnable = Runnable {
        _binding?.let {
            it.content.text = ""
            it.numPad.visibility = View.GONE
            it.previewContainer.visibility = View.GONE
            isManualEntry = false
            view?.visibility = View.GONE
        }
    }


    private val playRunnable = Runnable {
        val currentActivity = activity as? MainActivity
        if (currentActivity != null && _binding != null) {
            currentActivity.play(pendingChannelIndex - 1)
            zapHandler.postDelayed(hideRunnable, ZAP_DELAY_MS)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        zapHandler.removeCallbacksAndMessages(null)
        _binding = null
    }
}
