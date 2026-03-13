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

class SearchFragment : Fragment(), ListAdapter.ItemListener {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var listAdapter: ListAdapter
    private val searchResultsModel = TVListModel("Search Results", "Search Results", -1)

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
        
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s.toString())
                binding.btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        binding.btnClear.setOnClickListener {
            binding.searchEditText.setText("")
        }
        
        binding.searchContainer.setOnClickListener { 
            // Consume clicks to prevent bubbling to background listener
        }
        
        binding.root.setOnClickListener {
            (activity as? MainActivity)?.hideSearchFragment()
        }
        
        return binding.root
    }

    private fun filter(query: String) {
        if (query.isEmpty()) {
            searchResultsModel.setTVListModel(emptyList())
            listAdapter.update(searchResultsModel)
            binding.noResults.visibility = View.GONE
            return
        }

        val filtered = TVList.listModel.filter { model ->
            model.tv.title.contains(query, ignoreCase = true) || 
            (model.tv.id + 1).toString() == query
        }

        searchResultsModel.setTVListModel(filtered)
        listAdapter.update(searchResultsModel)
        
        binding.noResults.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    fun showKeyboard() {
        binding.searchEditText.requestFocus()
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onItemFocusChange(tvModel: TVModel, hasFocus: Boolean) {}

    override fun onItemClicked(tvModel: TVModel) {
        binding.searchEditText.setText("")
        TVList.setPositionByModel(tvModel)
        (activity as? MainActivity)?.hideSearchFragment()
    }

    override fun onKey(listAdapter: ListAdapter, keyCode: Int): Boolean {
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = SearchFragment()
    }
}
