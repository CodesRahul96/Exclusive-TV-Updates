package com.codesrahul.exclusivetv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codesrahul.exclusivetv.databinding.EpgGridBinding
import com.codesrahul.exclusivetv.models.TVList
import com.codesrahul.exclusivetv.models.TVModel
import com.bumptech.glide.Glide
import java.util.*
import java.text.SimpleDateFormat

class EpgGridFragment : Fragment() {

    private var _binding: EpgGridBinding? = null
    private val binding get() = _binding!!
    
    private var horizontalScrollOffset = 0
    private var lastSyncTime = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = EpgGridBinding.inflate(inflater, container, false)
        setupGrid()
        return binding.root
    }

    private fun setupGrid() {
        populateTimeHeader()
        val channels = TVList.listModel
        if (channels.isEmpty()) {
            Toast.makeText(requireContext(), "No channels loaded for Guide", Toast.LENGTH_SHORT).show()
            return
        }

        binding.epgRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.epgRecycler.adapter = EpgRowAdapter(channels)
        
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
        val now = System.currentTimeMillis()
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


    inner class EpgRowAdapter(private val channels: List<TVModel>) : RecyclerView.Adapter<EpgRowAdapter.ViewHolder>() {

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
            val channel = channels[position]
            holder.name.text = channel.tv.title
            
            Glide.with(holder.logo)
                .load(channel.tv.logo)
                .placeholder(R.drawable.bg_glass)
                .into(holder.logo)
            
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
            
            val guideStart = getGuideStartTime()
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            programs.forEach { prog ->
                if (prog.stop < guideStart) return@forEach // Past program
                
                val view = LayoutInflater.from(context).inflate(R.layout.item_epg_program, holder.programContainer, false)
                val title = view.findViewById<android.widget.TextView>(R.id.program_title)
                val time = view.findViewById<android.widget.TextView>(R.id.program_time)
                
                title.text = prog.title
                time.text = "${timeFormat.format(prog.start)} - ${timeFormat.format(prog.stop)}"
                
                val durationMins = (prog.stop - prog.start) / 60000
                val startOffsetMins = (prog.start - guideStart) / 60000
                
                // Ensure reasonable width
                val finalWidth = if (durationMins < 5) 5 * PIXELS_PER_MINUTE else (durationMins * PIXELS_PER_MINUTE).toInt()
                
                // Use RelativeLayout.LayoutParams if container is RelativeLayout?
                // Wait, item_epg_row.xml has:
                // <RelativeLayout android:id="@+id/program_container" ... />
                // So YES, use RelativeLayout.LayoutParams
                
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

        override fun getItemCount(): Int = channels.size
    }

    private fun showFocusInfo(prog: com.codesrahul.exclusivetv.models.EPGProgram) {
        binding.focusedProgramInfo.visibility = View.VISIBLE
        binding.focusedTitle.text = prog.title
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.focusedTime.text = "${timeFormat.format(prog.start)} - ${timeFormat.format(prog.stop)}"
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
