package com.codesrahul.exclusivetv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codesrahul.exclusivetv.databinding.EpgGridBinding
import com.codesrahul.exclusivetv.models.TVList
import com.codesrahul.exclusivetv.models.TVModel
import java.text.SimpleDateFormat
import java.util.*

class EpgGridFragment : Fragment() {

    private var _binding: EpgGridBinding? = null
    private val binding get() = _binding!!
    
    private var horizontalScrollOffset = 0
    private var lastSyncTime = 0L
    
    private var allChannels: List<TVModel> = emptyList()
    private var filteredChannels: MutableList<TVModel> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = EpgGridBinding.inflate(inflater, container, false)
        setupGrid()
        setupSearch()
        return binding.root
    }

    private fun setupSearch() {
        binding.epgSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChannels(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun filterChannels(query: String) {
        val q = query.lowercase().trim()
        // Take a snapshot to avoid ConcurrentModificationException during list refresh
        val result = if (q.isEmpty()) {
            allChannels.toMutableList()
        } else {
            allChannels.filter { channel ->
                channel.tv.title.lowercase().contains(q)
            }.toMutableList()
        }
        filteredChannels.clear()
        filteredChannels.addAll(result)
        binding.epgRecycler.adapter?.notifyDataSetChanged()
    }

    private fun setupGrid() {
        populateTimeHeader()
        allChannels = TVList.listModel
        filteredChannels.addAll(allChannels)
        
        if (allChannels.isEmpty()) {
            return
        }

        binding.epgRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.epgRecycler.adapter = EpgRowAdapter(filteredChannels)
        
        // Sync header scroll with rows
        binding.timeScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            if (horizontalScrollOffset != scrollX) {
                horizontalScrollOffset = scrollX
                syncVisibleRows(scrollX)
            }
        }

        binding.root.postDelayed({
            scrollToNow()
        }, 500)
    }
    
     private fun scrollToNow() {
         val now = Utils.getDateTimestamp() * 1000L
         val guideStart = getGuideStartTime()
        val offsetMins = (now - guideStart) / 60000
        val scrollX = (offsetMins * PIXELS_PER_MINUTE).toInt() - 200 // Offset slightly to show a bit of past
        
        binding.timeScroll.smoothScrollTo(if (scrollX > 0) scrollX else 0, 0)
    }

    private fun syncVisibleRows(scrollX: Int) {
        val now = System.currentTimeMillis()
        if (now - lastSyncTime < 16) return // Limit to ~60fps sync to reduce layout overhead
        lastSyncTime = now
        
        val layoutManager = binding.epgRecycler.layoutManager as? LinearLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return

        for (i in first..last) {
            val holder = binding.epgRecycler.findViewHolderForAdapterPosition(i) as? EpgRowAdapter.ViewHolder
            holder?.rowScroll?.scrollX = scrollX
        }
    }
    
    private fun populateTimeHeader() {
        val guideStart = getGuideStartTime()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val container = binding.timeContainer
        container.removeAllViews()
        
        // Populate 24 hours in 30-minute blocks
        for (i in 0 until 48) {
            val view = LayoutInflater.from(requireContext()).inflate(R.layout.item_epg_time, container, false)
            val timeText = view.findViewById<android.widget.TextView>(R.id.time_label)
            
            val blockTime = guideStart + (i * 30 * 60000L)
            timeText.text = timeFormat.format(blockTime)
            
            val lp = LinearLayout.LayoutParams((30 * PIXELS_PER_MINUTE).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            view.layoutParams = lp
            container.addView(view)
        }
    }


    inner class EpgRowAdapter(private val displayList: List<TVModel>) : RecyclerView.Adapter<EpgRowAdapter.ViewHolder>() {

        // Cache heavy objects once per adapter, not per bind
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val guideStart = getGuideStartTime()

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name = view.findViewById<android.widget.TextView>(R.id.channel_name)
            val logo = view.findViewById<android.widget.ImageView>(R.id.channel_logo)
            val rowScroll = view.findViewById<HorizontalScrollView>(R.id.row_scroll)
            val programContainer = view.findViewById<ViewGroup>(R.id.program_container)
            val channelInfo = view.findViewById<View>(R.id.channel_info_container)
            var currentChannelUrl: String? = null
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_epg_row, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val channel = displayList[position]
            holder.name.text = channel.tv.title
            
            LogoUtil.loadLogo(holder.logo.context, holder.logo, channel.tv.logo, channel.tv.title)
            
            // Click header to play
            holder.channelInfo.setOnClickListener {
                 (activity as? MainActivity)?.playChannel(channel)
            }

            val channelUrl = channel.tv.uris.firstOrNull()
            // Sync initial scroll
            holder.rowScroll.post {
                holder.rowScroll.scrollX = horizontalScrollOffset
            }

            if (holder.currentChannelUrl == channelUrl && holder.programContainer.childCount > 0) {
                 return // Skip repopulation if already showing this channel
            }
            holder.currentChannelUrl = channelUrl
            
            // Sync scroll movements
            holder.rowScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
                if (horizontalScrollOffset != scrollX) {
                    horizontalScrollOffset = scrollX
                    binding.timeScroll.scrollX = scrollX
                    // We don't sync all other rows here to avoid feedback loops and lag
                    // Instead, rows sync on bind.
                }
            }
            
            // Populate programs
            val programs = EPGManager.getProgramsForChannel(channel.tv.title, channel.tv.id.toString())
            holder.programContainer.removeAllViews()
            // (guideStart and timeFormat are cached at adapter level)

             programs.forEach { prog ->
                 val shiftMs = SP.epgShift * 3600_000L
                 val shiftedStart = prog.start + shiftMs
                 val shiftedStop = prog.stop + shiftMs
                 
                 if (shiftedStop < guideStart) return@forEach // Past program
                 
                 val view = LayoutInflater.from(context).inflate(R.layout.item_epg_program, holder.programContainer, false)
                 val title = view.findViewById<android.widget.TextView>(R.id.program_title)
                 val time = view.findViewById<android.widget.TextView>(R.id.program_time)
                 
                 title.text = prog.title
                 time.text = "${timeFormat.format(shiftedStart)} - ${timeFormat.format(shiftedStop)}"
                 
                 val durationMins = (shiftedStop - shiftedStart) / 60000
                 val startOffsetMins = (shiftedStart - guideStart) / 60000
                
                // Ensure reasonable width
                val finalWidth = if (durationMins < 5) 5 * PIXELS_PER_MINUTE else (durationMins * PIXELS_PER_MINUTE).toInt()
                
                val lp = android.widget.RelativeLayout.LayoutParams(finalWidth, ViewGroup.LayoutParams.MATCH_PARENT)
                lp.marginStart = (startOffsetMins * PIXELS_PER_MINUTE).toInt()
                view.layoutParams = lp
                
                // Interaction:
                // 1. Focus -> Show Info
                // 2. Click -> Play Channel AND Show Info (or just play)
                
                view.setOnClickListener {
                     (activity as? MainActivity)?.playChannel(channel)
                }
                
                view.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) showFocusInfo(prog)
                }

                holder.programContainer.addView(view)
            }
        }

        override fun getItemCount(): Int = displayList.size
    }

     private fun showFocusInfo(prog: com.codesrahul.exclusivetv.models.EPGProgram) {
         binding.focusedProgramInfo.visibility = View.VISIBLE
         binding.focusedTitle.text = prog.title
         val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
         val shiftMs = SP.epgShift * 3600_000L
         binding.focusedTime.text = "${timeFormat.format(prog.start + shiftMs)} - ${timeFormat.format(prog.stop + shiftMs)}"
         binding.focusedDescription.text = prog.description
     }

    private fun getGuideStartTime(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        const val TAG = "EpgGridFragment"
        const val PIXELS_PER_MINUTE = 5 // 5dp per minute
    }
}
