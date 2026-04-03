package com.codesrahul.exclusivetv

import android.util.Log
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import android.view.KeyEvent
import androidx.recyclerview.widget.LinearLayoutManager
import com.codesrahul.exclusivetv.databinding.FragmentSearchBinding
import com.codesrahul.exclusivetv.models.TVList
import com.codesrahul.exclusivetv.models.TVListModel
import com.codesrahul.exclusivetv.models.TVModel
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.speech.RecognizerIntent
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Color
import android.widget.TextView
import android.widget.LinearLayout
import java.util.*
import kotlin.comparisons.compareByDescending
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener

class SearchFragment : Fragment(), ListAdapter.ItemListener {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var listAdapter: ListAdapter
    private val searchResultsModel = TVListModel("Search Results", "Search Results", -1)
    
    private val voiceSearchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.getOrNull(0)
            if (!spokenText.isNullOrEmpty()) {
                binding.searchEditText.setText(spokenText)
                binding.searchEditText.setSelection(spokenText.length)
                filter(spokenText)
                
                // Professional: If exact match found, auto-select it
                val results = searchResultsModel.getTVModelList()
                if (results.size == 1) {
                    onItemClicked(results[0])
                }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            performVoiceSearch()
        } else {
            Toast.makeText(requireContext(), "Permission denied: Voice search requires microphone access.", Toast.LENGTH_LONG).show()
        }
    }
    
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        
        val context = requireContext()
        listAdapter = ListAdapter(context, binding.searchResults, searchResultsModel)
        binding.searchResults.adapter = listAdapter
        binding.searchResults.layoutManager = LinearLayoutManager(context)
        
        listAdapter.setItemListener(this)
        
        // Initialize Native Speech Recognizer
        if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            setupSpeechListener()
        }
        
        // [PROFESSIONAL] Optimized Focus for TV vs Mobile
        val hasTouch = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
        if (!hasTouch) {
            // TV Mode: Just request focus, don't force hide elements that might be toggled
            binding.searchEditText.requestFocus()
            // Optimized for FireTV: focus state handled by XML selector (addStatesFromChildren)
        } else {
            // Mobile/Touch: Keypad-First
            binding.numPadContainer.visibility = View.VISIBLE
            binding.searchEditText.clearFocus()
        }
        
        updateHistoryChips()
        showSuggestions()
        
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                binding.btnClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                
                if (query.isEmpty()) {
                    showSuggestions()
                    return
                }

                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable { filter(query) }
                searchHandler.postDelayed(searchRunnable!!, 300)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // [PROFESSIONAL] Manual Focus Navigation for TV Remote
        binding.searchEditText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // Move focus to first visible button on the right
                when {
                    binding.btnVoice.visibility == View.VISIBLE -> binding.btnVoice.requestFocus()
                    binding.btnKeypad.visibility == View.VISIBLE -> binding.btnKeypad.requestFocus()
                    binding.btnClear.visibility == View.VISIBLE -> binding.btnClear.requestFocus()
                }
                true
            } else false
        }

        binding.btnVoice.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                binding.searchEditText.requestFocus()
                true
            } else false
        }

        binding.btnKeypad.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (binding.btnVoice.visibility == View.VISIBLE) binding.btnVoice.requestFocus()
                else binding.searchEditText.requestFocus()
                true
            } else false
        }

        binding.btnClear.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (binding.btnKeypad.visibility == View.VISIBLE) binding.btnKeypad.requestFocus()
                else if (binding.btnVoice.visibility == View.VISIBLE) binding.btnVoice.requestFocus()
                else binding.searchEditText.requestFocus()
                true
            } else false
        }
        
        binding.btnClear.setOnClickListener {
            binding.searchEditText.setText("")
        }

        binding.btnVoice.visibility = if (SP.voiceSearch) View.VISIBLE else View.GONE
        binding.btnVoice.setOnClickListener {
            startVoiceSearch()
        }
        
        binding.searchContainer.setOnClickListener { 
            // Consume clicks to prevent bubbling to background listener
        }
        
        binding.root.setOnClickListener {
            (activity as? MainActivity)?.hideSearchFragment()
        }

        setupKeypad()

        return binding.root
    }

    private fun setupKeypad() {
        binding.btnKeypad.setOnClickListener {
            if (binding.numPadContainer.visibility == View.VISIBLE) {
                binding.numPadContainer.visibility = View.GONE
                showKeyboard()
            } else {
                binding.numPadContainer.visibility = View.VISIBLE
                hideKeyboard()
            }
        }

        val buttons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6, binding.btn7,
            binding.btn8, binding.btn9
        )

        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                appendSearchDigit(index.toString())
            }
        }

        binding.btnDel.setOnClickListener {
            val s = binding.searchEditText.text.toString()
            if (s.isNotEmpty()) {
                binding.searchEditText.setText(s.substring(0, s.length - 1))
                binding.searchEditText.setSelection(binding.searchEditText.text.length)
            }
        }

        binding.btnOk.setOnClickListener {
            val results = searchResultsModel.getTVModelList()
            if (results.isNotEmpty()) {
                onItemClicked(results[0])
            }
        }
    }

    private fun appendSearchDigit(digit: String) {
        val current = binding.searchEditText.text.toString()
        if (current.length >= 10) return // Increased limit for T9/Name search
        
        val newVal = current + digit
        binding.searchEditText.setText(newVal)
        binding.searchEditText.setSelection(newVal.length)
        
        // Smart Zap: If it's a 4-digit number and we have an exact match, play instantly
        if (newVal.length == 4) {
            filter(newVal)
            val results = searchResultsModel.getTVModelList()
            if (results.size == 1) {
                if ((results[0].tv.id + 1).toString() == newVal) {
                    onItemClicked(results[0])
                }
            }
        }
    }

    private fun updateHistoryChips() {
        binding.historyChips.removeAllViews()
        val history = SP.searchHistory
        if (history.isEmpty()) {
            binding.historyChipsScroll.visibility = View.GONE
            return
        }
        binding.historyChipsScroll.visibility = View.VISIBLE
        
        history.take(8).forEach { query ->
            // Use a custom styled TextView instead of Chip for maximum compatibility
            val chip = TextView(requireContext()).apply {
                text = query
                setPadding(24, 12, 24, 12)
                setBackgroundResource(R.drawable.bg_badge)
                setTextColor(android.graphics.Color.WHITE)
                textSize = 14f
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 16, 0)
                layoutParams = params
                isClickable = true
                isFocusable = true
                nextFocusUpId = binding.searchEditText.id
                nextFocusDownId = binding.searchResults.id
                
                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.scaleX = 1.1f
                        v.scaleY = 1.1f
                        v.setBackgroundResource(R.drawable.tv_button_bg)
                    } else {
                        v.scaleX = 1.0f
                        v.scaleY = 1.0f
                        v.setBackgroundResource(R.drawable.bg_badge)
                    }
                }

                setOnClickListener {
                    binding.searchEditText.setText(query)
                    binding.searchEditText.setSelection(query.length)
                    filter(query)
                }
            }
            binding.historyChips.addView(chip)
        }
    }


    private fun filter(query: String) {
        if (query.isEmpty()) {
            showSuggestions()
            return
        }

        val keywords = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val isNumeric = query.trim().all { it.isDigit() }
        
        val filtered = TVList.listModel.filter { model ->
            val title = model.tv.title.lowercase()
            
            // 1. Exact match for channel number
            val numMatch = (model.tv.id + 1).toString() == query.trim()
            
            // 2. Multi-word match for title
            val titleMatch = keywords.all { keyword -> 
                title.contains(keyword.lowercase()) 
            }
            
            // 3. Professional T9 Match (only if query is numeric)
            val t9Match = if (isNumeric && query.trim().length >= 2) {
                isT9Match(query.trim(), title)
            } else false
            
            numMatch || titleMatch || t9Match
        }.sortedWith(compareByDescending<TVModel> { model ->
            // Sort Priority: Exact Number > Starts With > T9 Match > Partial
            when {
                (model.tv.id + 1).toString() == query.trim() -> 100
                model.tv.title.startsWith(query, ignoreCase = true) -> 80
                isT9Match(query.trim(), model.tv.title.lowercase()) -> 50
                else -> 10
            }
        })

        listAdapter.setSearchQuery(query)
        searchResultsModel.setTVListModel(filtered)
        listAdapter.update(searchResultsModel)
        
        binding.noResults.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun isT9Match(numericQuery: String, title: String): Boolean {
        if (numericQuery.isEmpty() || !numericQuery.all { it.isDigit() }) return false
        
        // Simplified T9 mapping
        val t9Map = mapOf(
            '2' to "[abc]", '3' to "[def]", '4' to "[ghi]", '5' to "[jkl]",
            '6' to "[mno]", '7' to "[pqrs]", '8' to "[tuv]", '9' to "[wxyz]",
            '0' to "[ ]", '1' to "[ ]"
        )
        
        val regexStr = numericQuery.map { t9Map[it] ?: it.toString() }.joinToString("")
        return try {
            Regex(regexStr, RegexOption.IGNORE_CASE).containsMatchIn(title)
        } catch (e: Exception) {
            false
        }
    }

    fun showKeyboard() {
        binding.searchEditText.requestFocus()
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_IMPLICIT)
        binding.numPadContainer.visibility = View.GONE
    }

    fun hideKeyboard() {
        binding.searchEditText.clearFocus()
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }

    override fun onItemFocusChange(tvModel: TVModel, hasFocus: Boolean) {}

    override fun onItemClicked(tvModel: TVModel) {
        val query = binding.searchEditText.text.toString()
        if (query.isNotEmpty()) {
            SP.addSearchHistory(query)
            updateHistoryChips()
        }
        
        binding.searchEditText.setText("")
        TVList.setPositionByModel(tvModel)
        (activity as? MainActivity)?.hideSearchFragment()
    }

    override fun onKey(listAdapter: ListAdapter, keyCode: Int): Boolean {
        return false
    }

    private fun showSuggestions() {
        val recentlyWatched = SP.recentlyWatchedUrls
        val history = SP.searchHistory
        
        val suggestedChannels = mutableListOf<TVModel>()
        
        // Add Recently Watched first
        recentlyWatched.forEach { url ->
            TVList.listModel.find { it.tv.uris.contains(url) }?.let { 
                if (!suggestedChannels.contains(it)) suggestedChannels.add(it)
            }
        }
        
        // Add matching channels from Search History keywords if any (Optional, let's keep it simple for now)
        // For now, let's just show "Recently Watched" as the initial state
        
        searchResultsModel.updateMetadata(
            if (suggestedChannels.isEmpty()) "Search History" else "Recently Watched",
            -1
        )
        searchResultsModel.setTVListModel(suggestedChannels)
        listAdapter.setSearchQuery("") // Clear highlighting
        listAdapter.update(searchResultsModel)
        binding.noResults.visibility = View.GONE
    }

    private fun startVoiceSearch() {
        Log.d("EXCL_VOICE", "startVoiceSearch() called")
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            performVoiceSearch()
        } else {
            Log.d("EXCL_VOICE", "Requesting Microphone Permission...")
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun triggerVoiceSearch() {
        Log.d("EXCL_VOICE", "triggerVoiceSearch() called")
        if (SP.voiceSearch) {
            if (speechRecognizer != null && android.speech.SpeechRecognizer.isRecognitionAvailable(requireContext())) {
                startVoiceSearch()
            } else {
                Log.d("EXCL_VOICE", "Native Speech unavailable - Launching System Speech Dialog")
                try {
                    val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak channel name...")
                    }
                    requireActivity().startActivityForResult(intent, 1001)
                } catch (e: Exception) {
                    Log.e("EXCL_VOICE", "Failed to launch system speech dialog", e)
                    Toast.makeText(requireContext(), "Voice search not supported on this device", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.d("EXCL_VOICE", "Voice Search is DISABLED in settings")
        }
    }

    fun applyExternalQuery(query: String) {
        if (!isAdded) return
        binding.searchEditText.setText(query)
        binding.searchEditText.setSelection(query.length)
        filter(query)
    }

    private fun setupSpeechListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("EXCL_VOICE", "onReadyForSpeech")
                isListening = true
                binding.btnVoice.setColorFilter(android.graphics.Color.YELLOW)
                Toast.makeText(requireContext(), "Listening...", Toast.LENGTH_SHORT).show()
            }

            override fun onBeginningOfSpeech() {
                Log.d("EXCL_VOICE", "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Potential: Animate mic based on volume levels for premium feel
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("EXCL_VOICE", "onEndOfSpeech")
                isListening = false
                binding.btnVoice.clearColorFilter()
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Voice Search failed: $error"
                }
                Log.e("EXCL_VOICE", "Speech Error: $message ($error)")
                isListening = false
                binding.btnVoice.clearColorFilter()
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.getOrNull(0)
                if (!spokenText.isNullOrEmpty()) {
                    Log.d("EXCL_VOICE", "Voice Result: $spokenText")
                    binding.searchEditText.setText(spokenText)
                    binding.searchEditText.setSelection(spokenText.length)
                    filter(spokenText)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer?.destroy()
        _binding = null
    }

    private fun performVoiceSearch() {
        Log.d("EXCL_VOICE", "performVoiceSearch() - Starting Native Listener")
        
        if (speechRecognizer == null) {
            Log.d("EXCL_VOICE", "SpeechRecognizer is NULL - Prompting for System Alexa")
            Toast.makeText(requireContext(), "Voice ready - Press your remote's Alexa button to speak!", Toast.LENGTH_LONG).show()
            return
        }

        if (isListening) {
            speechRecognizer?.stopListening()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("EXCL_VOICE", "Exception in startListening: ${e.message}", e)
            Toast.makeText(requireContext(), "Voice search failed to start", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun newInstance() = SearchFragment()
    }
}
