package com.codesrahul.exclusivetv

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
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
            }
        }
    }
    
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

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
        
        // [PROFESSIONAL] Keypad-First for Mobile
        binding.numPadContainer.visibility = View.VISIBLE
        binding.searchEditText.clearFocus() 
        
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
        
        binding.btnClear.setOnClickListener {
            binding.searchEditText.setText("")
        }

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
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak channel name...")
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (e: Exception) {
            // Voice search not supported
        }
    }

    companion object {
        fun newInstance() = SearchFragment()
    }
}
