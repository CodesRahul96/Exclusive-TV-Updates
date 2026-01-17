package com.codesrahul.exclusivetv

import androidx.recyclerview.widget.DiffUtil
import com.codesrahul.exclusivetv.models.TVListModel

class TVListModelDiffCallback(
    private val oldList: List<TVListModel>,
    private val newList: List<TVListModel>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Categories have names. Index might change during reordering.
        return oldList[oldItemPosition].getName() == newList[newItemPosition].getName()
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        
        // Check core properties
        return oldItem.getName() == newItem.getName() &&
               oldItem.size() == newItem.size()
    }
}
