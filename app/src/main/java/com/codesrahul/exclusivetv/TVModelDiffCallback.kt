package com.codesrahul.exclusivetv

import androidx.recyclerview.widget.DiffUtil
import com.codesrahul.exclusivetv.models.TVModel

class TVModelDiffCallback(
    private val oldList: List<TVModel>,
    private val newList: List<TVModel>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Assuming TV ID is unique across the app
        return oldList[oldItemPosition].tv.id == newList[newItemPosition].tv.id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        
        // Check core visual properties
        return oldItem.tv.title == newItem.tv.title &&
               oldItem.tv.logo == newItem.tv.logo &&
               oldItem.like.value == newItem.like.value &&
               oldItem.currentProgram.value?.title == newItem.currentProgram.value?.title
    }
}
