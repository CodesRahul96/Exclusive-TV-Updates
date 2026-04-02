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
        if (current.length >= 4) return // Max 4 digits for zap
        
        val newVal = current + digit
        binding.searchEditText.setText(newVal)
        binding.searchEditText.setSelection(newVal.length)
        
        // Smart Zap: If it's a 4-digit number and we have a match, play instantly
        if (newVal.length == 4) {
            filter(newVal)
            val results = searchResultsModel.getTVModelList()
            if (results.size == 1) {
                // Check if it's an exact numeric match
                if ((results[0].tv.id + 1).toString() == newVal) {
                    onItemClicked(results[0])
                }
            }
        }
    }


    private fun filter(query: String) {
        if (query.isEmpty()) {
            showSuggestions()
            return
        }

        val keywords = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        
        val filtered = TVList.listModel.filter { model ->
            val title = model.tv.title
            
            // 1. Exact match for channel number
            val numMatch = (model.tv.id + 1).toString() == query.trim()
            
            // 2. Multi-word match for title (ALL keywords must be present)
            val titleMatch = keywords.all { keyword -> 
                title.contains(keyword, ignoreCase = true) 
            }
            
            numMatch || titleMatch
        }

        listAdapter.setSearchQuery(query)

        searchResultsModel.setTVListModel(filtered)
        listAdapter.update(searchResultsModel)
        
        binding.noResults.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
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
