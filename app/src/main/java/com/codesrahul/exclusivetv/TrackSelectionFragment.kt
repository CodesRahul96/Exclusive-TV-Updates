package com.codesrahul.exclusivetv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codesrahul.exclusivetv.databinding.FragmentTrackSelectionBinding

class TrackSelectionFragment : Fragment() {
    private var _binding: FragmentTrackSelectionBinding? = null
    private val binding get() = _binding!!

    private var tracks: List<WebFragment.AudioTrack> = emptyList()
    private var listener: TrackSelectionListener? = null

    interface TrackSelectionListener {
        fun onTrackSelected(index: Int)
        fun onDismiss()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackSelectionBinding.inflate(inflater, container, false)
        
        binding.trackList.layoutManager = LinearLayoutManager(context)
        binding.trackList.adapter = TrackAdapter(tracks)
        binding.trackList.isFocusable = true
        binding.trackList.isFocusableInTouchMode = true

        binding.backgroundOverlay.setOnClickListener {
            listener?.onDismiss()
        }

        return binding.root
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            binding.trackList.requestFocus()
            // Try to focus the first item
            binding.trackList.postDelayed({
                val v = binding.trackList.findViewHolderForAdapterPosition(0)
                v?.itemView?.requestFocus()
            }, 100)
        }
    }

    fun setTracks(tracks: List<WebFragment.AudioTrack>, listener: TrackSelectionListener) {
        this.tracks = tracks
        this.listener = listener
        if (_binding != null) {
            binding.trackList.adapter = TrackAdapter(tracks)
        }
    }

    private inner class TrackAdapter(private val tracks: List<WebFragment.AudioTrack>) :
        RecyclerView.Adapter<TrackAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.track_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val track = tracks[position]
            holder.name.text = track.name
            holder.check.visibility = if (track.isSelected) View.VISIBLE else View.INVISIBLE
            
            holder.itemView.isFocusable = true
            holder.itemView.isFocusableInTouchMode = true
            
            holder.itemView.setOnClickListener {
                listener?.onTrackSelected(track.index)
            }
            
            // Focus handling is now done via selector_item_focus drawable
            holder.itemView.setOnFocusChangeListener { _, _ -> }
        }

        override fun getItemCount() = tracks.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.track_name)
            val check: ImageView = view.findViewById(R.id.check_icon)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TrackSelectionFragment()
    }
}
