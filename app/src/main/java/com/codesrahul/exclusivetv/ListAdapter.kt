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


class ListAdapter(
    private val context: Context,
    private val recyclerView: RecyclerView,
    var tvListModel: TVListModel,
) :
    RecyclerView.Adapter<ListAdapter.ViewHolder>() {
    private var listener: ItemListener? = null
    private var focused: View? = null
    private var defaultFocused = false
    private var defaultFocus: Int = -1

    var visible = false
    private lateinit var itemTouchHelper: ItemTouchHelper

    val application = context.applicationContext as MyTVApplication
    private var movingPosition = -1

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

    fun update(tvListModel: TVListModel) {
        recyclerView.post {
            this.tvListModel = tvListModel
            notifyDataSetChanged()
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
        val tvModel = tvListModel.getTVModel(position)!!
        val view = viewHolder.itemView

        view.isFocusable = true
        view.isFocusableInTouchMode = true
//        view.alpha = 0.8F

        viewHolder.like(tvModel.like.value as Boolean)

        viewHolder.binding.heart.setOnClickListener {
            tvModel.setLike(!(tvModel.like.value as Boolean))
            viewHolder.like(tvModel.like.value as Boolean)
        }

        if (!defaultFocused && position == defaultFocus) {
            view.requestFocus()
            defaultFocused = true
        }

        val onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            listener?.onItemFocusChange(tvModel, hasFocus)

            if (hasFocus) {
                viewHolder.focus(true)
                focused = view
                if (visible) {
                    if (position != tvListModel.position.value) {
                        tvListModel.setPosition(position)
                    }
                } else {
                    visible = true
                }
            } else {
                viewHolder.focus(false)
            }
        }

        view.onFocusChangeListener = onFocusChangeListener

        view.setOnClickListener { _ ->
            if (movingPosition == position) {
                stopMove()
            } else {
                listener?.onItemClicked(tvModel)
            }
        }

        view.setOnLongClickListener {
            showChannelOptions(position, tvModel)
            true
        }

        view.setOnKeyListener { _, keyCode, event: KeyEvent? ->
            if (event?.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && position == 0) {
                    val p = getItemCount() - 1

                    (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                        p,
                        0
                    )

                    recyclerView.postDelayed({
                        val v = recyclerView.findViewHolderForAdapterPosition(p)
                        v?.itemView?.isSelected = true
                        v?.itemView?.requestFocus()
                    }, 0)
                }

                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && position == getItemCount() - 1) {
                    val p = 0

                    (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                        p,
                        0
                    )

                    recyclerView.postDelayed({
                        val v = recyclerView.findViewHolderForAdapterPosition(p)
                        v?.itemView?.isSelected = true
                        v?.itemView?.requestFocus()
                    }, 0)
                }

                if (movingPosition != -1 && movingPosition == position) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            moveChannelUp(position)
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            moveChannelDown(position)
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

        viewHolder.bindImage(tvModel.tv.logo, tvModel.tv.id)

        viewHolder.setArrows(movingPosition == position)
        
        viewHolder.binding.arrowUp.setOnClickListener {
            moveChannelUp(position)
        }
        viewHolder.binding.arrowDown.setOnClickListener {
            moveChannelDown(position)
        }
    }

    override fun getItemCount() = tvListModel.size()

    class ViewHolder(private val context: Context, val binding: ListItemBinding) :
        RecyclerView.ViewHolder(binding.root), OnSharedPreferenceChangeListener {

        init {
            SP.setOnSharedPreferenceChangeListener(this)
        }

        fun bindTitle(text: String) {
            binding.title.text = text
        }

        fun bindDescription(text: String?) {
            if (text.isNullOrEmpty()) {
                binding.description.visibility = View.GONE
            } else {
                binding.description.text = text
                binding.description.visibility = View.VISIBLE
            }
        }

        fun bindImage(url: String?, id: Int) {
            if (url.isNullOrBlank()) {
                val width = Utils.dpToPx(40)
                val height = Utils.dpToPx(40)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                val paint = Paint().apply {
                    color = Color.WHITE
                    textSize = 32f
                    textAlign = Paint.Align.CENTER
                }
                val text = String.format("%3d", id + 1)
                val x = width / 2f
                val y = height / 2f - (paint.descent() + paint.ascent()) / 2
                canvas.drawText(text, x, y, paint)
                Glide.with(context)
                    .load(BitmapDrawable(context.resources, bitmap))
                    .centerInside()
                    .into(binding.icon)
//                binding.imageView.setImageDrawable(null)
            } else {
                Glide.with(context)
                    .load(url)
                    .centerInside()
//                    .error(BitmapDrawable(context.resources, bitmap))
                    .into(binding.icon)
            }
        }

        fun focus(hasFocus: Boolean) {
            val colorFocused = ContextCompat.getColor(context, R.color.accent_red)
            val colorWhite = ContextCompat.getColor(context, R.color.white)
            val colorTitleBlur = ContextCompat.getColor(context, R.color.title_blur)
            val colorDescriptionBlur = ContextCompat.getColor(context, R.color.description_blur)

            // Text color change
            binding.title.setTextColor(if (hasFocus) colorFocused else colorTitleBlur)
            binding.description.setTextColor(if (hasFocus) colorWhite else colorDescriptionBlur)

            // Cancel any ongoing animations to avoid clashing
            binding.root.animate().cancel()

            // Apply background immediately to avoid flicker
            binding.root.setBackgroundResource(
                if (hasFocus) R.drawable.focus_background else R.drawable.blur_background
            )

            // Animate scale and elevation
            binding.root.animate()
                .scaleX(if (hasFocus) 1.05f else 0.95f)
                .scaleY(if (hasFocus) 1.05f else 1.0f)
                .setDuration(200)
                .start()

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

        override fun onSharedPreferenceChanged(key: String) {
            Log.i(TAG, "$key changed")
            when (key) {
                SP.KEY_EPG -> {
                    if (SP.epg.isNullOrEmpty()) {
                        val constraintSet = ConstraintSet()
                        constraintSet.clone(binding.root)

                        constraintSet.connect(binding.title.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                        constraintSet.connect(binding.heart.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                        constraintSet.applyTo(binding.root)

                        binding.description.visibility = View.GONE
                    } else {
                        val constraintSet = ConstraintSet()
                        constraintSet.clone(binding.root)

                        constraintSet.clear(binding.title.id, ConstraintSet.BOTTOM)
                        constraintSet.clear(binding.heart.id, ConstraintSet.BOTTOM)

                        constraintSet.applyTo(binding.root)

                        binding.description.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    fun toPosition(position: Int) {
        recyclerView.post {
            (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                position,
                0
            )

            recyclerView.postDelayed({
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                viewHolder?.itemView?.isSelected = true
                viewHolder?.itemView?.requestFocus()
            }, 0)
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
                 tvModel.setLike(!(tvModel.like.value ?: false))
                 // Refresh handled by like observer in ViewHolder
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

    private fun stopMove() {
        val prevPosition = movingPosition
        movingPosition = -1
        notifyItemChanged(prevPosition)
    }



    private fun getCurrentChannelOrder(): MutableList<String> {
        val order = mutableListOf<String>()
        for (i in 0 until tvListModel.size()) {
            val model = tvListModel.getTVModel(i)
            if (model != null) {
                val url = model.tv.uris.firstOrNull() ?: ""
                if (url.isNotEmpty()) {
                    order.add(url)
                }
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
        
        if (index > 0 && index < currentOrder.size) { // Validate index
             // Correct index for swapping - listAdapter index matches order list index directly if no extra items
            // Assuming currentOrder matches adapter position mapping (check getCurrentChannelOrder logic)
            // getCurrentChannelOrder iterates tvListModel.
            
            Collections.swap(currentOrder, index, index - 1)
            OrderPreferenceManager.saveChannelOrder(categoryName, currentOrder)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                com.codesrahul.exclusivetv.models.TVList.refreshModels(MyTVApplication.getInstance())
            }
            
            // Get the updated model from the group model to ensure we have the fresh data
            val updatedModel = com.codesrahul.exclusivetv.models.TVList.groupModel.getTVListModel(tvListModel.getIndex())
            if (updatedModel != null) {
                update(updatedModel)
            } else {
                // Fallback if model not found (shouldn't happen usually)
                update(tvListModel)
            }
            
            movingPosition = position - 1
            recyclerView.post {
                toPosition(position - 1)
            }
        }
    }

    private fun moveChannelDown(position: Int) {
        if (position >= tvListModel.size() - 1) {
            Toast.makeText(context, "Cannot move this channel down", Toast.LENGTH_SHORT).show()
            return
        }

        val categoryName = tvListModel.getName()
        val currentOrder = getCurrentChannelOrder()
        val index = position

        if (index < currentOrder.size - 1) {
            Collections.swap(currentOrder, index, index + 1)
            OrderPreferenceManager.saveChannelOrder(categoryName, currentOrder)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                com.codesrahul.exclusivetv.models.TVList.refreshModels(MyTVApplication.getInstance())
            }
            
            // Get the updated model from the group model to ensure we have the fresh data
            val updatedModel = com.codesrahul.exclusivetv.models.TVList.groupModel.getTVListModel(tvListModel.getIndex())
            if (updatedModel != null) {
                update(updatedModel)
            } else {
                // Fallback if model not found (shouldn't happen usually)
                update(tvListModel)
            }

            movingPosition = position + 1
            recyclerView.post {
                 toPosition(position + 1)
            }
        }
    }

    companion object {
        private const val TAG = "ListAdapter"
    }
}

