package com.codesrahul.exclusivetv.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TVListModel(private val name: String, private val originalName: String, private val index: Int) : ViewModel() {
    fun getName(): String {
        return name
    }

    fun getOriginalName(): String {
        return originalName
    }

    fun getIndex(): Int {
        return index
    }

    fun getTVModelList(): List<TVModel> {
        return _tvListModel.value ?: emptyList()
    }

    private val _tvListModel = MutableLiveData<List<TVModel>>()
    val tvListModel: LiveData<List<TVModel>>
        get() = _tvListModel

    private val _position = MutableLiveData<Int>()
    val position: LiveData<Int>
        get() = _position

    fun setPosition(position: Int) {
        _position.postValue(position)
    }

    private val _change = MutableLiveData<Boolean>()
    val change: LiveData<Boolean>
        get() = _change

    fun setChange() {
        _change.postValue(true)
    }

    fun setTVListModel(tvListModel: List<TVModel>) {
        _tvListModel.postValue(tvListModel)
    }

    fun addTVModel(tvModel: TVModel) {
        val currentList = _tvListModel.value ?: emptyList()
        val newList = currentList.toMutableList()
        newList.add(tvModel)
        _tvListModel.postValue(newList)
    }

    fun removeTVModel(id: Int) {
        val currentList = _tvListModel.value ?: return
        val newList = currentList.toMutableList()
        val iterator = newList.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().tv.id == id) {
                iterator.remove()
            }
        }
        _tvListModel.postValue(newList)
    }

    fun replaceTVModel(tvModel: TVModel) {
        val currentList = _tvListModel.value ?: emptyList()
        val newList = currentList.toMutableList()
        var exists = false
        
        // Check if the channel already exists in the list
        for (model in newList) {
            if (model.tv.id == tvModel.tv.id) {
                exists = true
                break
            }
        }
        
        // Only add if it doesn't exist
        if (!exists) {
            newList.add(tvModel)
        }
        
        // Always update the LiveData to trigger observers
        _tvListModel.postValue(newList)
    }

    fun clear() {
        _tvListModel.postValue(mutableListOf())
        setPosition(0)
    }

    fun getTVModel(): TVModel? {
        val pos = position.value ?: return null
        return getTVModel(pos)
    }

    fun getTVModel(idx: Int): TVModel? {
        val list = _tvListModel.value
        if (list == null || idx < 0 || idx >= list.size) {
            return null
        }
        return list[idx]
    }

    init {
        _position.postValue(0)
    }

    fun size(): Int {
        return _tvListModel.value?.size ?: 0
    }
}
