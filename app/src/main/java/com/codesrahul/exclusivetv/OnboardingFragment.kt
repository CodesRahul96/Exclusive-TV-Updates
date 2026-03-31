package com.codesrahul.exclusivetv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.fragment.app.Fragment
import android.widget.ImageView
import com.codesrahul.exclusivetv.databinding.FragmentOnboardingBinding

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private var currentStep = 0
    private var handAnimator: ObjectAnimator? = null
    private var highlighterAnimator: ObjectAnimator? = null
    private var usageAnimator: ObjectAnimator? = null
    private var tvUiUtils: com.codesrahul.exclusivetv.ui.TvUiUtils? = null

    // Check if device is a TV
    private fun isTvDevice(): Boolean {
        val uiModeManager = requireContext().getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        tvUiUtils = com.codesrahul.exclusivetv.ui.TvUiUtils(requireContext())
        tvUiUtils?.initSounds(R.raw.focus, R.raw.click)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFocusListeners()

        binding.btnSkip.setOnClickListener {
            tvUiUtils?.playClickSound()
            finishOnboarding()
        }

        binding.btnNext.setOnClickListener {
            tvUiUtils?.playClickSound()
            nextStep()
        }

        binding.btnPrev.setOnClickListener {
            tvUiUtils?.playClickSound()
            prevStep()
        }

        startTutorial()
        binding.btnNext.requestFocus()
    }

    private fun setupFocusListeners() {
        val focusViews = listOf(
            binding.btnSkip,
            binding.btnNext,
            binding.btnPrev
        )

        focusViews.forEach { v ->
            v.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                    tvUiUtils?.playFocusSound()
                } else {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }
            }
        }
    }

    private fun startTutorial() {
        currentStep = 0
        updateStep()
    }

    private fun nextStep() {
        currentStep++
        val maxSteps = if (isTvDevice()) 6 else 8
        if (currentStep > maxSteps) {
            finishOnboarding()
        } else {
            updateStep()
        }
    }

    private fun prevStep() {
        if (currentStep > 0) {
            currentStep--
            updateStep()
        }
    }

    fun resetAndShow() {
        currentStep = 0
        updateStep()
        view?.visibility = View.VISIBLE
    }

    private fun updateStep() {
        handAnimator?.cancel()
        highlighterAnimator?.cancel()
        usageAnimator?.cancel()
        
        // Safety check for TV layout views which might be missing in Mobile layout
        val viewHighlight = view?.findViewById<View>(R.id.view_highlight)
        val ivHand = view?.findViewById<View>(R.id.iv_hand)
        val usageContainer = view?.findViewById<View>(R.id.usage_callout_container)
        val ivUsageIcon = view?.findViewById<ImageView>(R.id.iv_usage_icon)
        
        viewHighlight?.visibility = View.INVISIBLE
        ivHand?.visibility = View.INVISIBLE
        usageContainer?.visibility = View.INVISIBLE

        // Control button visibility
        binding.btnPrev.visibility = if (currentStep > 0) View.VISIBLE else View.GONE
        binding.btnNext.text = "Next"
        binding.btnNext.requestFocus()

        if (isTvDevice()) {
            updateTVStep(viewHighlight, usageContainer, ivUsageIcon)
        } else {
            updateMobileStep(ivHand)
        }
    }

    private fun updateTVStep(viewHighlight: View?, usageContainer: View?, ivUsageIcon: ImageView?) {
        when (currentStep) {
            0 -> {
                binding.tvInstruction.text = "Welcome to Exclusive TV"
                binding.tvSubInstruction.text = "Discover the ultimate streaming experience on your Fire TV."
                binding.btnNext.text = "Start Walkthrough"
            }
            1 -> {
                binding.tvInstruction.text = "Channel Navigation"
                binding.tvSubInstruction.text = "Use UP or DOWN to change channels instantly."
                ivUsageIcon?.setImageResource(R.drawable.ic_usage_dpad)
                animateTVCallout(usageContainer, viewHighlight, 0.5f, 0.32f, true)
            }
            2 -> {
                binding.tvInstruction.text = "Info & Search"
                binding.tvSubInstruction.text = "Press CENTER for current info, or HOLD for Global Search."
                ivUsageIcon?.setImageResource(R.drawable.ic_usage_dpad)
                animateTVCallout(usageContainer, viewHighlight, 0.5f, 0.32f)
            }
            3 -> {
                binding.tvInstruction.text = "Side Menu"
                binding.tvSubInstruction.text = "Press LEFT to open Categories and Live TV Guide."
                ivUsageIcon?.setImageResource(R.drawable.ic_usage_dpad)
                animateTVCallout(usageContainer, viewHighlight, 0.43f, 0.32f)
            }
            4 -> {
                binding.tvInstruction.text = "App Settings"
                binding.tvSubInstruction.text = "Hold RIGHT for 3 seconds to access Settings and customization."
                ivUsageIcon?.setImageResource(R.drawable.ic_usage_dpad)
                animateTVCallout(usageContainer, viewHighlight, 0.57f, 0.32f)
            }
            5 -> {
                binding.tvInstruction.text = "Go Back"
                binding.tvSubInstruction.text = "The BACK button dismisses menus or returns to the previous screen."
                ivUsageIcon?.setImageResource(R.drawable.ic_usage_back)
                animateTVCallout(usageContainer, viewHighlight, 0.43f, 0.46f)
            }
            6 -> {
                binding.tvInstruction.text = "Ready to Watch!"
                binding.tvSubInstruction.text = "You can replay this tutorial anytime from the Settings menu."
                binding.btnNext.text = "Get Started"
            }
        }
    }

    private fun animateTVCallout(usageContainer: View?, viewHighlight: View?, x: Float, y: Float, isDPad: Boolean = false) {
        if (usageContainer == null || viewHighlight == null) return
        
        usageContainer.visibility = View.VISIBLE
        usageContainer.alpha = 0f
        usageContainer.animate().alpha(1f).translationY(0f).setDuration(500).start()
        
        // Floating animation for usage area
        usageAnimator = ObjectAnimator.ofFloat(usageContainer, View.TRANSLATION_Y, -10f, 10f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }

        animateRemoteHighlight(viewHighlight, x, y, isDPad)
    }

    private fun updateMobileStep(ivHand: View?) {
        if (ivHand == null) return
        when (currentStep) {
            0 -> {
                binding.tvInstruction.text = "Welcome to Exclusive TV"
                binding.tvSubInstruction.text = "Let's show you how to navigate the app with simple gestures."
                binding.btnNext.text = "Start Walkthrough"
            }
            1 -> {
                binding.tvInstruction.text = "Open Channels & Categories"
                binding.tvSubInstruction.text = "Single tap on the left side of the screen to open the Menu."
                animateHandTap(0.12f, 0.5f)
            }
            2 -> {
                binding.tvInstruction.text = "Browse Fast"
                binding.tvSubInstruction.text = "Swipe up or down in the center of the screen to change channels."
                animateHandSwipeVertical(0.5f, 0.3f, 0.7f)
            }
            3 -> {
                binding.tvInstruction.text = "Volume & Brightness"
                binding.tvSubInstruction.text = "Swipe up/down on the right for Volume, and on the left for Brightness."
                animateHandSwipeEdge(0.9f, 0.3f, 0.7f)
            }
            4 -> {
                binding.tvInstruction.text = "Global Search"
                binding.tvSubInstruction.text = "Long press in the center of the screen to open Global Search."
                animateHandTap(0.5f, 0.5f) // Corrected for mobile search trigger
            }
            5 -> {
                binding.tvInstruction.text = "Settings Menu"
                binding.tvSubInstruction.text = "Double tap or long press (5s) on the right side of the screen to open Settings."
                animateHandTap(0.88f, 0.5f, isDoubleTap = true)
            }
            6 -> {
                binding.tvInstruction.text = "Audio & Language"
                binding.tvSubInstruction.text = "Hold for 3 seconds on the right side to quickly change the Audio Track."
                animateHandLongPress(0.88f, 0.4f)
            }
            7 -> {
                binding.tvInstruction.text = "Picture-in-Picture"
                binding.tvSubInstruction.text = "Use PIP mode from settings to watch while using other applications."
            }
            8 -> {
                binding.tvInstruction.text = "You're All Set!"
                binding.tvSubInstruction.text = "You can replay this tutorial anytime from the Settings menu."
                binding.btnNext.text = "Get Started"
            }
        }
    }

    private fun animateRemoteHighlight(viewHighlight: View?, xPercent: Float, yPercent: Float, isDPad: Boolean = false) {
        if (viewHighlight == null) return
        
        val container = view?.findViewById<View>(R.id.remote_container) ?: return
        
        container.post {
            val width = container.width.toFloat()
            val height = container.height.toFloat()
            
            viewHighlight.x = width * xPercent - viewHighlight.width / 2
            viewHighlight.y = height * yPercent - viewHighlight.height / 2
            viewHighlight.visibility = View.VISIBLE
            
            if (isDPad) {
                // Pulse size for navigation buttons
                val scale = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.4f, 1f)
                val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.4f, 1f)
                highlighterAnimator = ObjectAnimator.ofPropertyValuesHolder(viewHighlight, scale, scaleY).apply {
                    duration = 1000
                    repeatCount = ObjectAnimator.INFINITE
                    start()
                }
            } else {
                // Fade in/out pulse
                highlighterAnimator = ObjectAnimator.ofFloat(viewHighlight, View.ALPHA, 0.3f, 1f, 0.3f).apply {
                    duration = 1000
                    repeatCount = ObjectAnimator.INFINITE
                    start()
                }
            }
        }
    }

    private fun animateHandTap(relX: Float, relY: Float, isDoubleTap: Boolean = false) {
        val ivHand = view?.findViewById<View>(R.id.iv_hand) ?: return
        val rootWidth = binding.root.width.toFloat()
        val rootHeight = binding.root.height.toFloat()
        if (rootWidth == 0f || rootHeight == 0f) {
             binding.root.post { animateHandTap(relX, relY, isDoubleTap) }
             return
        }

        ivHand.x = rootWidth * relX - ivHand.width/2
        ivHand.y = rootHeight * relY - ivHand.height/2
        ivHand.visibility = View.VISIBLE
        ivHand.alpha = 0f

        val pulse = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f, 1f)
        val pulseY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f, 1f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f, 1f, 0f)

        handAnimator = ObjectAnimator.ofPropertyValuesHolder(ivHand, pulse, pulseY, alpha).apply {
            duration = if (isDoubleTap) 800 else 1500
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun animateHandSwipeVertical(relX: Float, relYStart: Float, relYEnd: Float) {
        val ivHand = view?.findViewById<View>(R.id.iv_hand) ?: return
        val rootWidth = binding.root.width.toFloat()
        val rootHeight = binding.root.height.toFloat()
        if (rootWidth == 0f || rootHeight == 0f) {
             binding.root.post { animateHandSwipeVertical(relX, relYStart, relYEnd) }
             return
        }

        ivHand.x = rootWidth * relX - ivHand.width/2
        ivHand.visibility = View.VISIBLE
        ivHand.alpha = 1f
        
        val startY = rootHeight * relYStart
        val endY = rootHeight * relYEnd

        handAnimator = ObjectAnimator.ofFloat(ivHand, "y", startY, endY).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun animateHandSwipeEdge(relX: Float, relYStart: Float, relYEnd: Float) {
        val ivHand = view?.findViewById<View>(R.id.iv_hand) ?: return
        val rootWidth = binding.root.width.toFloat()
        val rootHeight = binding.root.height.toFloat()
        if (rootWidth == 0f || rootHeight == 0f) {
             binding.root.post { animateHandSwipeEdge(relX, relYStart, relYEnd) }
             return
        }

        ivHand.x = rootWidth * relX - ivHand.width/2
        ivHand.visibility = View.VISIBLE
        
        val startY = rootHeight * relYStart
        val endY = rootHeight * relYEnd

        handAnimator = ObjectAnimator.ofFloat(ivHand, "y", startY, endY).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    ivHand.alpha = 1f
                }
            })
            start()
        }
    }

    private fun animateHandLongPress(relX: Float, relY: Float) {
        val ivHand = view?.findViewById<View>(R.id.iv_hand) ?: return
        val rootWidth = binding.root.width.toFloat()
        val rootHeight = binding.root.height.toFloat()
        if (rootWidth == 0f || rootHeight == 0f) {
             binding.root.post { animateHandLongPress(relX, relY) }
             return
        }

        ivHand.x = rootWidth * relX - ivHand.width/2
        ivHand.y = rootHeight * relY - ivHand.height/2
        ivHand.visibility = View.VISIBLE
        
        val scaleDown = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.8f, 1f)
        val scaleDownY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.8f, 1f)

        handAnimator = ObjectAnimator.ofPropertyValuesHolder(ivHand, scaleDown, scaleDownY).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun finishOnboarding() {
        SP.hasCompletedOnboarding = true
        (activity as? MainActivity)?.hideOnboarding()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handAnimator?.cancel()
        highlighterAnimator?.cancel()
        usageAnimator?.cancel()
        _binding = null
    }
}
