package com.codesrahul.exclusivetv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
import android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.marginStart
import androidx.core.view.setPadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codesrahul.exclusivetv.databinding.ListItemBinding
import com.codesrahul.exclusivetv.models.TVListModel
import com.codesrahul.exclusivetv.models.TVModel
import com.codesrahul.exclusivetv.OrderPreferenceManager
import com.codesrahul.exclusivetv.RenameDialogFragment
import com.codesrahul.exclusivetv.MyTVApplication
import android.widget.Toast
import android.view.MotionEvent
import java.util.Collections
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.DiffUtil
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.Spannable


class ListAdapter(
    private val context: Context,
    private val recyclerView: RecyclerView,
    var tvListModel: TVListModel,
) :
    RecyclerView.Adapter<ListAdapter.ViewHolder>() {
    private var internalList: List<TVModel> = tvListModel.getTVModelList()
    private var listener: ItemListener? = null
    private var updateJob: kotlinx.coroutines.Job? = null
    private var focused: View? = null
    private var defaultFocused = false
    private var defaultFocus: Int = -1

    var visible = false
    private lateinit var itemTouchHelper: ItemTouchHelper

    val application = context.applicationContext as MyTVApplication
    private var movingPosition = -1
    private var searchQuery: String = ""

    fun setSearchQuery(query: String) {
        this.searchQuery = query
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(context)
        val binding = ListItemBinding.inflate(inflater, parent, false)

        binding.icon.layoutParams.width = application.px2Px(binding.icon.layoutParams.width)
        binding.icon.layoutParams.height = application.px2Px(binding.icon.layoutParams.height)
        binding.icon.setPadding(application.px2Px(binding.icon.paddingTop))

        val layoutParams = binding.title.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.marginStart = application.px2Px(binding.title.marginStart)
        binding.title.layoutParams = layoutParams

        binding.heart.layoutParams.width = application.px2Px(binding.heart.layoutParams.width)
        binding.heart.layoutParams.height = application.px2Px(binding.heart.layoutParams.height)

        binding.title.textSize = application.px2PxFont(binding.title.textSize)

        val layoutParamsHeart = binding.heart.layoutParams as ViewGroup.MarginLayoutParams
        layoutParamsHeart.marginStart = application.px2Px(binding.heart.marginStart)
        binding.heart.layoutParams = layoutParamsHeart

        binding.description.textSize = application.px2PxFont(binding.description.textSize)
        
        // Scale channel number for TV - REMOVED manual scaling to support WRAP_CONTENT and auto-sizing
        // binding.channelNumber.layoutParams.width = application.px2Px(binding.channelNumber.layoutParams.width)
        // binding.channelNumber.layoutParams.height = application.px2Px(binding.channelNumber.layoutParams.height)
        binding.channelNumber.textSize = application.px2PxFont(binding.channelNumber.textSize)

        return ViewHolder(context, binding)
    }

    fun focusable(able: Boolean) {
        recyclerView.isFocusable = able
        recyclerView.isFocusableInTouchMode = able
        if (able) {
            recyclerView.descendantFocusability = FOCUS_BEFORE_DESCENDANTS
        } else {
            recyclerView.descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        }
    }

    fun update(newTvListModel: TVListModel, onUpdateComplete: (() -> Unit)? = null) {
        // OPTIMIZATION: If it's the same model instance, we can check if it actually changed
        // but for now, the simplest is to check if it's the exact same reference and we already have it.
        if (this.tvListModel === newTvListModel && internalList === newTvListModel.getTVModelList()) {
            onUpdateComplete?.invoke()
            return
        }

        updateJob?.cancel()
        val oldList = internalList
        // Take snapshot on MAIN thread to avoid race conditions with model updates
        val newList = newTvListModel.getTVModelList()
        
        updateJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            val diffResult = DiffUtil.calculateDiff(TVModelDiffCallback(oldList, newList))
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                internalList = newList
                tvListModel = newTvListModel
                diffResult.dispatchUpdatesTo(this@ListAdapter)
                onUpdateComplete?.invoke()
            }
        }
    }

    fun attachItemTouchHelper() {
        val callback = object : ItemTouchHelper.Callback() {
            override fun isLongPressDragEnabled() = false
            override fun isItemViewSwipeEnabled() = false

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition

                val categoryName = tvListModel.getName()
                val currentOrder = getCurrentChannelOrder()
                Collections.swap(currentOrder, from, to)
                OrderPreferenceManager.saveChannelOrder(categoryName, currentOrder)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    com.codesrahul.exclusivetv.models.TVList.refreshModels(MyTVApplication.getInstance())
                }
                notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // no swipe
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    fun clear() {
        focused?.clearFocus()
        recyclerView.invalidate()
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val tvModel = internalList.getOrNull(position) ?: return
        
        viewHolder.bind(tvModel, movingPosition, position, searchQuery)

        // Reset selected state to prevent sticky yellow borders from recycled views
        viewHolder.itemView.isSelected = false

        val view = viewHolder.itemView
        view.isFocusable = true
        view.isFocusableInTouchMode = true

        viewHolder.binding.heart.setOnClickListener {
            val currentLike = tvModel.like.value ?: false
            if (!currentLike && SP.favoriteUrls.size >= 10) {
                Toast.makeText(context, "Maximum 10 favorites allowed", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvModel.setLike(!currentLike)
            viewHolder.like(!currentLike)
            
            // Trigger refresh to update "My Collection" group
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                com.codesrahul.exclusivetv.models.TVList.refreshModels(MyTVApplication.getInstance())
            }
        }

        if (!defaultFocused && position == defaultFocus) {
            view.requestFocus()
            defaultFocused = true
        }

        val onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            val pos = viewHolder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@OnFocusChangeListener
            listener?.onItemFocusChange(tvModel, hasFocus)

            if (hasFocus) {
                view.post { viewHolder.focus(true) }
                focused = view
                // CENTER SELECTION ON NAVIGATION
                scrollToCenter(pos)
                
                if (visible) {
                    if (pos != tvListModel.position.value) {
                        tvListModel.setPosition(pos)
                    }
                } else {
                    visible = true
                }
            } else {
                view.post { viewHolder.focus(false) }
            }
        }

        view.onFocusChangeListener = onFocusChangeListener

        view.setOnClickListener { _ ->
            val pos = viewHolder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (movingPosition == pos) {
                stopMove()
            } else {
                listener?.onItemClicked(tvModel)
            }
        }

        view.setOnLongClickListener {
            val pos = viewHolder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                showChannelOptions(pos, tvModel)
            }
            true
        }

        view.setOnKeyListener { _, keyCode, event: KeyEvent? ->
            val pos = viewHolder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnKeyListener false
            
            if (event?.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && pos == 0) {
                    val p = getItemCount() - 1

                    (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                        p,
                        0
                    )

                    recyclerView.postDelayed({
                        val v = recyclerView.findViewHolderForAdapterPosition(p)
//                        v?.itemView?.isSelected = true
                        v?.itemView?.requestFocus()
                    }, 0)
                    return@setOnKeyListener true
                }

                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && pos == getItemCount() - 1) {
                    val p = 0

                    (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                        p,
                        0
                    )

                    recyclerView.postDelayed({
                        val v = recyclerView.findViewHolderForAdapterPosition(p)
//                        v?.itemView?.isSelected = true
                        v?.itemView?.requestFocus()
                    }, 0)
                    return@setOnKeyListener true
                }

                if (movingPosition != -1 && movingPosition == pos) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            moveChannelUp(pos)
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            moveChannelDown(pos)
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            stopMove()
                            return@setOnKeyListener true
                        }
                    }
                }



                return@setOnKeyListener listener?.onKey(this, keyCode) ?: false
            }
            false
        }

        viewHolder.bindTitle(tvModel.tv.title)

        // Bind EPG description
        if (SP.epgEnabled) {
            viewHolder.bindDescription(tvModel.currentProgram.value?.title)
        } else {
            viewHolder.bindDescription(null)
        }

        viewHolder.bindImage(tvModel.tv.logo, tvModel.tv.title)

        viewHolder.setArrows(movingPosition == position)
        
        viewHolder.binding.arrowDown.setOnClickListener {
            val pos = viewHolder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                moveChannelDown(pos)
            }
        }
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.attachListeners()
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.detachListeners()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun getItemCount() = internalList.size

    class ViewHolder(private val context: Context, val binding: ListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentTvModel: TVModel? = null
        private var isAttached = false

        private val epgObserver = androidx.lifecycle.Observer<com.codesrahul.exclusivetv.models.EPGProgram?> { program ->
            if (SP.epgEnabled) {
                bindDescription(program?.title)
            } else {
                bindDescription(null)
            }
        }

        private val likeObserver = androidx.lifecycle.Observer<Boolean> { liked ->
            like(liked)
        }

        fun bind(tvModel: TVModel, movePos: Int, currentPos: Int, query: String = "") {
            // Unsubscribe from old model
            if (isAttached) {
                currentTvModel?.currentProgram?.removeObserver(epgObserver)
                currentTvModel?.like?.removeObserver(likeObserver)
            }
            
            currentTvModel = tvModel
            
            // Subscribe to new model if attached
            if (isAttached) {
                currentTvModel?.currentProgram?.observeForever(epgObserver)
                currentTvModel?.like?.observeForever(likeObserver)
            }
            
            bindTitle(tvModel.tv.title, query)
            
            // Initial EPG bind
            if (SP.epgEnabled) {
                bindDescription(tvModel.currentProgram.value?.title)
            } else if (query.isNotEmpty() && tvModel.tv.group.isNotEmpty()) {
                // Show Group Tag in search results if EPG is off/empty
                bindDescription("Category: ${tvModel.tv.group}")
            } else {
                bindDescription(null)
            }

            bindImage(tvModel.tv.logo, tvModel.tv.title)
            
            // Bind channel number (position + 1 for 1-based indexing)
            binding.channelNumber.text = (tvModel.tv.id + 1).toString()
            
            setArrows(movePos == currentPos)
            like(tvModel.like.value ?: false)
            updateConstraints()
        }

        fun attachListeners() {
            isAttached = true
            currentTvModel?.currentProgram?.observeForever(epgObserver)
            currentTvModel?.like?.observeForever(likeObserver)
        }

        fun detachListeners() {
            isAttached = false
            currentTvModel?.currentProgram?.removeObserver(epgObserver)
            currentTvModel?.like?.removeObserver(likeObserver)
        }

        fun bindTitle(text: String, query: String = "") {
            if (query.isEmpty()) {
                binding.title.text = text
                return
            }

            val spannable = SpannableString(text)
            val keywords = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            
            for (keyword in keywords) {
                var index = text.indexOf(keyword, ignoreCase = true)
                while (index >= 0) {
                    spannable.setSpan(
                        ForegroundColorSpan(Color.parseColor("#FFD700")), // Gold
                        index,
                        index + keyword.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    index = text.indexOf(keyword, index + keyword.length, ignoreCase = true)
                }
            }
            binding.title.text = spannable
        }

        fun bindDescription(text: String?) {
            if (text.isNullOrEmpty()) {
                binding.description.visibility = View.GONE
            } else {
                binding.description.text = text
                binding.description.visibility = View.VISIBLE
            }
        }

        fun bindImage(url: String?, name: String?) {
            LogoUtil.loadLogo(context, binding.icon, url, name)
        }

        fun focus(hasFocus: Boolean) {
            val colorWhite = ContextCompat.getColor(context, R.color.white)
            val colorTitleBlur = ContextCompat.getColor(context, R.color.title_blur)
            val colorDescriptionBlur = ContextCompat.getColor(context, R.color.description_blur)
            binding.title.setTextColor(if (hasFocus) colorWhite else colorTitleBlur)
            binding.description.setTextColor(if (hasFocus) colorWhite else colorDescriptionBlur)

            // Apply background immediately to avoid flicker
            binding.root.setBackgroundResource(
                if (hasFocus) R.drawable.focus_background else R.drawable.blur_background
            )

            // Set elevation (not animated—applied directly)
            binding.root.elevation = if (hasFocus) 10f else 0f
        }



        fun like(liked: Boolean) {
            if (liked) {
                binding.heart.setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        R.drawable.ic_heart
                    )
                )
            } else {
                binding.heart.setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        R.drawable.ic_heart_empty
                    )
                )
            }
        }
        
        fun setArrows(isMoving: Boolean) {
            binding.arrows.visibility = if (isMoving) View.VISIBLE else View.GONE
        }

        private fun updateConstraints() {
            val constraintSet = ConstraintSet()
            constraintSet.clone(binding.root)
            if (!SP.epgEnabled || SP.epg.isNullOrEmpty()) {
                constraintSet.connect(binding.title.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                constraintSet.connect(binding.heart.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                binding.description.visibility = View.GONE
            } else {
                constraintSet.clear(binding.title.id, ConstraintSet.BOTTOM)
                constraintSet.clear(binding.heart.id, ConstraintSet.BOTTOM)
                binding.description.visibility = View.VISIBLE
            }
            constraintSet.applyTo(binding.root)
        }
    }

     fun scrollToCenter(position: Int) {
         if (position < 0 || position >= itemCount) return
         
         val fH = recyclerView.height
         val itemHeight = application.px2Px(60) // Fallback height
         val offset = (fH / 2) - (itemHeight / 2)
         (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, if (offset > 0) offset else 0)
     }

     fun toPosition(position: Int) {
         if (position < 0 || position >= itemCount) return

         recyclerView.post {
             scrollToCenter(position)
 
             // Multiple attempts to ensure focus on recycled views
             val focusRunnable = object : Runnable {
                 var attempts = 0
                 override fun run() {
                     val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                     if (viewHolder != null) {
                         viewHolder.itemView.requestFocus()
                         // Removed viewHolder.itemView.isSelected = true to prevent sticky selected items
                     } else if (attempts < 5) {
                         attempts++
                         recyclerView.postDelayed(this, 30) // Retry after short delay
                     }
                 }
             }
             recyclerView.postDelayed(focusRunnable, 10)
         }
     }

    interface ItemListener {
        fun onItemFocusChange(tvModel: TVModel, hasFocus: Boolean)
        fun onItemClicked(tvModel: TVModel)
        fun onKey(listAdapter: ListAdapter, keyCode: Int): Boolean
    }

    fun setItemListener(listener: ItemListener) {
        this.listener = listener
    }


    private fun showChannelOptions(position: Int, tvModel: TVModel) {
        val channelName = tvModel.tv.title
        val isFavorite = tvModel.like.value ?: false

        val optionsDialog = ChannelOptionsDialogFragment.newInstance(channelName, isFavorite)
        optionsDialog.setChannelOptionsListener(object : ChannelOptionsDialogFragment.ChannelOptionsListener {
            override fun onMoveSelected() {
                startMove(position)
            }

            override fun onRenameSelected() {
                showRenameDialog(tvModel)
            }

            override fun onFavoriteSelected() {
                 val currentLike = tvModel.like.value ?: false
                 if (!currentLike && SP.favoriteUrls.size >= 10) {
                     Toast.makeText(context, "Maximum 10 favorites allowed", Toast.LENGTH_SHORT).show()
                     return
                 }
                 tvModel.setLike(!currentLike)
                 // Refresh handled by like observer in ViewHolder (UI only)
                 // Trigger full model refresh to update "My Collection" group
                 kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                     com.codesrahul.exclusivetv.models.TVList.refreshModels(context)
                 }
            }

            override fun onCancelSelected() {
                // Do nothing
            }
        })
        optionsDialog.show((context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager ?: return, "ChannelOptions")
    }

    private fun startMove(position: Int) {
        if (movingPosition != -1 && movingPosition != position) {
            notifyItemChanged(movingPosition)
        }
        movingPosition = position
        notifyItemChanged(position)
    }

     fun stopMove() {
         val prevPosition = movingPosition
         if (prevPosition == -1) return
         movingPosition = -1
         notifyItemChanged(prevPosition)
     }



    private fun getCurrentChannelOrder(): MutableList<String> {
        val order = mutableListOf<String>()
        for (model in internalList) {
            val url = model.tv.uris.firstOrNull() ?: ""
            if (url.isNotEmpty()) {
                order.add(url)
            }
        }
        return order
    }

    private fun showRenameDialog(tvModel: TVModel) {
        val channelUrl = tvModel.tv.uris.firstOrNull() ?: ""
        val currentName = tvModel.tv.title
        
        val renameDialog = RenameDialogFragment.newInstance(currentName, "Rename Channel")
        renameDialog.setRenameListener(object : RenameDialogFragment.RenameListener {
            override fun onRenameConfirmed(newName: String) {
                if (channelUrl.isNotEmpty()) {
                    OrderPreferenceManager.saveChannelRename(channelUrl, newName)
                    Toast.makeText(context, "Channel renamed", Toast.LENGTH_SHORT).show()
                    // Trigger refresh to apply rename
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        com.codesrahul.exclusivetv.models.TVList.refreshModels(MyTVApplication.getInstance())
                    }
                    // Update the adapter
                    update(tvListModel)
                }
            }
        })
        renameDialog.show((context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager ?: return, "RenameChannel")
    }

    private fun moveChannelUp(position: Int) {
        if (position <= 0) {
            Toast.makeText(context, "Cannot move this channel up", Toast.LENGTH_SHORT).show()
            return
        }

        val categoryName = tvListModel.getName()
        val currentOrder = getCurrentChannelOrder()
        val index = position
        
         if (index > 0 && index < currentOrder.size) {
             Collections.swap(currentOrder, index, index - 1)
             OrderPreferenceManager.saveChannelOrder(categoryName, currentOrder)
             
             // Update local list for immediate response
             val newList = internalList.toMutableList()
             Collections.swap(newList, index, index - 1)
             internalList = newList
             
             notifyItemMoved(index, index - 1)
             
             movingPosition = index - 1
             toPosition(index - 1)
             
             // Background sync after immediate UI feedback
             kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                 com.codesrahul.exclusivetv.models.TVList.refreshModels(MyTVApplication.getInstance())
             }
         }
    }

    private fun moveChannelDown(position: Int) {
        if (position >= internalList.size - 1) {
            Toast.makeText(context, "Cannot move this channel down", Toast.LENGTH_SHORT).show()
            return
        }

        val categoryName = tvListModel.getName()
        val currentOrder = getCurrentChannelOrder()
        val index = position

         if (index < currentOrder.size - 1) {
             Collections.swap(currentOrder, index, index + 1)
             OrderPreferenceManager.saveChannelOrder(categoryName, currentOrder)
 
             // Update local list
             val newList = internalList.toMutableList()
             Collections.swap(newList, index, index + 1)
             internalList = newList
 
             notifyItemMoved(index, index + 1)
 
             movingPosition = index + 1
             toPosition(index + 1)
 
             kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                 com.codesrahul.exclusivetv.models.TVList.refreshModels(MyTVApplication.getInstance())
             }
         }
    }

    companion object {
        private const val TAG = "ListAdapter"
    }

}
