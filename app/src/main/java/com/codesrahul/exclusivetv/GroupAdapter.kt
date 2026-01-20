package com.codesrahul.exclusivetv

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginStart
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codesrahul.exclusivetv.databinding.GroupItemBinding
import com.codesrahul.exclusivetv.models.TVGroupModel
import com.codesrahul.exclusivetv.models.TVListModel
import com.codesrahul.exclusivetv.OrderPreferenceManager
import com.codesrahul.exclusivetv.RenameDialogFragment
import com.codesrahul.exclusivetv.MyTVApplication
import android.widget.Toast
import android.view.MotionEvent
import java.util.Collections
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.DiffUtil


class GroupAdapter(
    private val context: Context,
    private val recyclerView: RecyclerView,
    private var tvGroupModel: TVGroupModel,
) :
    RecyclerView.Adapter<GroupAdapter.ViewHolder>() {
    private var internalList: List<TVListModel> = tvGroupModel.getTVListModelList()
    private var listener: ItemListener? = null
    private var updateJob: kotlinx.coroutines.Job? = null
    private var focused: View? = null
    private var defaultFocused = false
    private var defaultFocus: Int = -1
    private lateinit var itemTouchHelper: ItemTouchHelper

    val application = context.applicationContext as MyTVApplication
    var visible = false
    private var movingPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(context)
        val binding = GroupItemBinding.inflate(inflater, parent, false)

        val layoutParams = binding.title.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.marginStart = application.px2Px(binding.title.marginStart)
        layoutParams.bottomMargin = application.px2Px(binding.title.marginBottom)
        binding.title.layoutParams = layoutParams

        binding.title.textSize = application.px2PxFont(binding.title.textSize)

        binding.root.isFocusable = true
        binding.root.isFocusableInTouchMode = true
        return ViewHolder(context, binding)
    }

    fun focusable(able: Boolean) {
        recyclerView.isFocusable = able
        recyclerView.isFocusableInTouchMode = able
        if (able) {
            recyclerView.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        } else {
            recyclerView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }

    fun clear() {
        focused?.clearFocus()
        recyclerView.invalidate()
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val tvListModel = internalList.getOrNull(position) ?: return
        val view = viewHolder.itemView
        view.tag = position

        if (!defaultFocused && position == defaultFocus) {
            view.requestFocus()
            defaultFocused = true
        }

        val onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (viewHolder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                 listener?.onItemFocusChange(tvListModel, hasFocus)
            }

            if (hasFocus) {
                viewHolder.focus(true)
                focused = view
                if (visible) {
                    // Update model position if needed
                    val currentPos = viewHolder.bindingAdapterPosition
                    if (currentPos != RecyclerView.NO_POSITION && currentPos != tvGroupModel.position.value) {
                        tvGroupModel.setPosition(currentPos)
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
            val currentPos = viewHolder.bindingAdapterPosition
            if (currentPos == RecyclerView.NO_POSITION) return@setOnClickListener

            if (movingPosition == currentPos) {
                stopMove()
            } else {
                listener?.onItemClicked(currentPos)
            }
        }

        view.setOnLongClickListener {
            val currentPos = viewHolder.bindingAdapterPosition
            if (currentPos > 1) { // Prevent modifying system categories (My Collection, All channels)
                showCategoryOptions(currentPos, tvListModel)
            } else {
                 Toast.makeText(context, "System category cannot be modified", Toast.LENGTH_SHORT).show()
            }
            true
        }

        view.setOnKeyListener { _, keyCode, event: KeyEvent? ->
            val currentPos = viewHolder.bindingAdapterPosition
            if (currentPos == RecyclerView.NO_POSITION) return@setOnKeyListener false

            if (event?.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && currentPos == 0) {
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
                    return@setOnKeyListener true
                }

                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && currentPos == getItemCount() - 1) {
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
                    return@setOnKeyListener true
                }

                if (movingPosition != -1 && movingPosition == currentPos) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            moveGroupUp(currentPos)
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            moveGroupDown(currentPos)
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            stopMove()
                            return@setOnKeyListener true
                        }
                    }
                }

                return@setOnKeyListener listener?.onKey(keyCode) ?: false
            }
            false
        }

        viewHolder.bindTitle(tvListModel.getName())
        viewHolder.bindTitle(tvListModel.getName())
        viewHolder.setArrowsVisibility(movingPosition == position)
        
        viewHolder.binding.arrowUp.setOnClickListener {
            moveGroupUp(position)
        }
        viewHolder.binding.arrowDown.setOnClickListener {
            moveGroupDown(position)
        }
    }

    override fun getItemCount() = internalList.size

    class ViewHolder(private val context: Context, val binding: GroupItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindTitle(text: String) {
            binding.title.text = text
        }

        fun setArrowsVisibility(isMoving: Boolean) {
            binding.arrows.visibility = if (isMoving) View.VISIBLE else View.GONE
        }

        fun focus(hasFocus: Boolean) {
            val colorWhite = ContextCompat.getColor(context, R.color.white)
            val colorBlur = ContextCompat.getColor(context, R.color.description_blur)
            val focusBackground = R.drawable.focus_background
            binding.title.setTextColor(if (hasFocus) colorWhite else colorBlur)

            // Animate root view scale, elevation, and background change
            binding.root.animate()
                .scaleX(if (hasFocus) 1.0f else 0.95f)
                .scaleY(if (hasFocus) 1.0f else 0.95f)
                .translationZ(if (hasFocus) 8f else 0f)
                .setDuration(200)
                .withStartAction {
                    if (hasFocus) {
                        binding.root.setBackgroundResource(focusBackground)
                    }
                }
                .withEndAction {
                    if (!hasFocus) {
                        binding.root.background = null
                    }
                }
                .start()

            // Set elevation to ensure it matches the animation
            binding.root.elevation = if (hasFocus) 8f else 0f
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
        fun onItemFocusChange(tvListModel: TVListModel, hasFocus: Boolean)
        fun onItemClicked(position: Int)
        fun onKey(keyCode: Int): Boolean
    }

    fun setItemListener(listener: ItemListener) {
        this.listener = listener
    }

    fun update(newTvGroupModel: TVGroupModel) {
        updateJob?.cancel()
        val oldList = internalList
        // Take snapshot on MAIN thread to avoid race conditions with model updates
        val newList = newTvGroupModel.getTVListModelList()
        
        updateJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            val diffResult = DiffUtil.calculateDiff(TVListModelDiffCallback(oldList, newList))
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                internalList = newList
                tvGroupModel = newTvGroupModel
                diffResult.dispatchUpdatesTo(this@GroupAdapter)
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

                if (from <= 1 || to <= 1) return false

                val currentList = tvGroupModel.getTVListModelList().toMutableList()
                Collections.swap(currentList, from, to)
                tvGroupModel.setTVListModelList(currentList)

                val currentOrder = getCurrentCategoryOrder()
                OrderPreferenceManager.saveCategoryOrder(currentOrder)
                
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

    private fun showCategoryOptions(position: Int, tvListModel: TVListModel) {
        val displayName = tvListModel.getName()
        val originalName = tvListModel.getOriginalName()

        val optionsDialog = CategoryOptionsDialogFragment.newInstance(displayName)
        optionsDialog.setCategoryOptionsListener(object : CategoryOptionsDialogFragment.CategoryOptionsListener {
            override fun onMoveSelected() {
                startMove(position)
            }

            override fun onRenameSelected() {
                showRenameDialog(originalName, displayName)
            }

            override fun onHideSelected() {
                val currentHidden = OrderPreferenceManager.getHiddenCategories().toMutableSet()
                currentHidden.add(originalName)
                OrderPreferenceManager.saveHiddenCategories(currentHidden)
                
                Toast.makeText(context, "Hidden $displayName", Toast.LENGTH_SHORT).show()
                
                // Trigger refresh
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    com.codesrahul.exclusivetv.models.TVList.refreshModels(MyTVApplication.getInstance())
                }
            }

            override fun onCancelSelected() {
                // Do nothing
            }
        })
        optionsDialog.show((context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager ?: return, "CategoryOptions")
    }

    private fun startMove(position: Int) {
        if (position <= 1) {
            Toast.makeText(context, "Cannot move this category", Toast.LENGTH_SHORT).show()
            return
        }
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
        
        // Refresh models strictly after move is done to persist changes and sync everything
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            com.codesrahul.exclusivetv.models.TVList.refreshModels(MyTVApplication.getInstance())
        }
    }



    private fun getCurrentCategoryOrder(): MutableList<String> {
        val order = mutableListOf<String>()
        for (i in internalList.indices) {
            val model = internalList[i]
            if (i > 1) { // Skip "My Collection" and "All channels"
                // Get original name (before rename)
                val originalName = model.getOriginalName()
                order.add(originalName)
            }
        }
        return order
    }

    private fun showRenameDialog(originalName: String, displayName: String) {
        val renameDialog = RenameDialogFragment.newInstance(displayName, "Rename Category")
        renameDialog.setRenameListener(object : RenameDialogFragment.RenameListener {
            override fun onRenameConfirmed(newName: String) {
                if (newName == originalName) {
                    OrderPreferenceManager.removeCategoryRename(originalName)
                    Toast.makeText(context, "Category name reverted", Toast.LENGTH_SHORT).show()
                } else {
                    OrderPreferenceManager.saveCategoryRename(originalName, newName)
                    Toast.makeText(context, "Category renamed", Toast.LENGTH_SHORT).show()
                }
                // Trigger refresh to apply rename
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                com.codesrahul.exclusivetv.models.TVList.refreshModels(MyTVApplication.getInstance())
            }
                // Update the adapter
                update(tvGroupModel)
            }
        })
        renameDialog.show((context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager ?: return, "RenameCategory")
    }

    private fun moveGroupUp(position: Int) {
        if (position <= 2) {
            Toast.makeText(context, "Cannot move this category up", Toast.LENGTH_SHORT).show()
            return
        }

        val currentOrder = getCurrentCategoryOrder()
        val index = position - 2

        if (index > 0) {
            val currentList = tvGroupModel.getTVListModelList().toMutableList()
            Collections.swap(currentList, position, position - 1)
            
            // Critical: Update internal list immediately to prevent DiffUtil glitches in update()
            tvGroupModel.setTVListModelList(currentList)
            internalList = currentList // Sync local list
            
            val currentOrder = getCurrentCategoryOrder()
            OrderPreferenceManager.saveCategoryOrder(currentOrder)
            
            // REMOVED: refreshModels() call that caused focus loss/lag
            
            notifyItemMoved(position, position - 1)
            
            movingPosition = position - 1
            recyclerView.post {
                toPosition(position - 1)
            }
        }
    }

    private fun moveGroupDown(position: Int) {
        if (position >= internalList.size - 1) {
            Toast.makeText(context, "Cannot move this category down", Toast.LENGTH_SHORT).show()
            return
        }

        val currentOrder = getCurrentCategoryOrder()
        val index = position - 2

        if (index < currentOrder.size - 1) {
            val currentList = tvGroupModel.getTVListModelList().toMutableList()
            Collections.swap(currentList, position, position + 1)
            
            // Critical: Update internal list immediately
            tvGroupModel.setTVListModelList(currentList)
            internalList = currentList // Sync local list
            
            val currentOrder = getCurrentCategoryOrder()
            OrderPreferenceManager.saveCategoryOrder(currentOrder)

            // REMOVED: refreshModels() call that caused focus loss/lag
            
            notifyItemMoved(position, position + 1)
            
            movingPosition = position + 1
            recyclerView.post {
                toPosition(position + 1)
            }
        }
    }

    companion object {
        private const val TAG = "CategoryAdapter"
    }
}

