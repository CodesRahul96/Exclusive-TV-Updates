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
import com.codesrahul.exclusivetv.databinding.FragmentOnboardingBinding

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private var currentStep = 0
    private var handAnimator: ObjectAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }

        binding.btnNext.setOnClickListener {
            nextStep()
        }

        binding.btnPrev.setOnClickListener {
            prevStep()
        }

        startTutorial()
    }

    private fun startTutorial() {
        currentStep = 0
        updateStep()
    }

    private fun nextStep() {
        currentStep++
        if (currentStep > 8) { // Updated to 8 steps
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
        binding.ivHand.visibility = View.INVISIBLE

        // Control button visibility
        binding.btnPrev.visibility = if (currentStep > 0) View.VISIBLE else View.GONE
        binding.btnNext.text = "Next" // Reset default

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
                animateHandLongPress(0.5f, 0.5f)
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
                binding.ivHand.visibility = View.GONE
            }
            8 -> {
                binding.tvInstruction.text = "You're All Set!"
                binding.tvSubInstruction.text = "You can replay this tutorial anytime from the Settings menu."
                binding.btnNext.text = "Get Started"
                binding.ivHand.visibility = View.GONE
            }
        }
    }

    private fun animateHandTap(relX: Float, relY: Float, isDoubleTap: Boolean = false) {
        val rootWidth = binding.root.width.toFloat()
        val rootHeight = binding.root.height.toFloat()
        if (rootWidth == 0f || rootHeight == 0f) {
             binding.root.post { animateHandTap(relX, relY, isDoubleTap) }
             return
        }

        binding.ivHand.x = rootWidth * relX - binding.ivHand.width/2
        binding.ivHand.y = rootHeight * relY - binding.ivHand.height/2
        binding.ivHand.visibility = View.VISIBLE
        binding.ivHand.alpha = 0f

        val pulse = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f, 1f)
        val pulseY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f, 1f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f, 1f, 0f)

        handAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.ivHand, pulse, pulseY, alpha).apply {
            duration = if (isDoubleTap) 800 else 1500
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun animateHandSwipeVertical(relX: Float, relYStart: Float, relYEnd: Float) {
        val rootWidth = binding.root.width.toFloat()
        val rootHeight = binding.root.height.toFloat()
        if (rootWidth == 0f || rootHeight == 0f) {
             binding.root.post { animateHandSwipeVertical(relX, relYStart, relYEnd) }
             return
        }

        binding.ivHand.x = rootWidth * relX - binding.ivHand.width/2
        binding.ivHand.visibility = View.VISIBLE
        binding.ivHand.alpha = 1f
        
        val startY = rootHeight * relYStart
        val endY = rootHeight * relYEnd

        handAnimator = ObjectAnimator.ofFloat(binding.ivHand, "y", startY, endY).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun animateHandSwipeEdge(relX: Float, relYStart: Float, relYEnd: Float) {
        val rootWidth = binding.root.width.toFloat()
        val rootHeight = binding.root.height.toFloat()
        if (rootWidth == 0f || rootHeight == 0f) {
             binding.root.post { animateHandSwipeEdge(relX, relYStart, relYEnd) }
             return
        }

        binding.ivHand.x = rootWidth * relX - binding.ivHand.width/2
        binding.ivHand.visibility = View.VISIBLE
        
        val startY = rootHeight * relYStart
        val endY = rootHeight * relYEnd

        handAnimator = ObjectAnimator.ofFloat(binding.ivHand, "y", startY, endY).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    binding.ivHand.alpha = 1f
                }
            })
            start()
        }
    }

    private fun animateHandLongPress(relX: Float, relY: Float) {
        val rootWidth = binding.root.width.toFloat()
        val rootHeight = binding.root.height.toFloat()
        if (rootWidth == 0f || rootHeight == 0f) {
             binding.root.post { animateHandLongPress(relX, relY) }
             return
        }

        binding.ivHand.x = rootWidth * relX - binding.ivHand.width/2
        binding.ivHand.y = rootHeight * relY - binding.ivHand.height/2
        binding.ivHand.visibility = View.VISIBLE
        
        val scaleDown = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.8f, 1f)
        val scaleDownY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.8f, 1f)

        handAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.ivHand, scaleDown, scaleDownY).apply {
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
        _binding = null
    }
}
