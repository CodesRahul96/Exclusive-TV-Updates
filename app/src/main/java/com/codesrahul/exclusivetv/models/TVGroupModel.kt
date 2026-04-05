package com.codesrahul.exclusivetv.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.codesrahul.exclusivetv.SP

class TVGroupModel : ViewModel() {
    private val _tvGroupModel = MutableLiveData<List<TVListModel>>()
    val tvGroupModel: LiveData<List<TVListModel>>
        get() = _tvGroupModel

    private val _position = MutableLiveData<Int>()
    val position: LiveData<Int>
        get() = _position

    private val _change = MutableLiveData<Boolean>()
    val change: LiveData<Boolean>
        get() = _change

    fun setPosition(position: Int) {
        _position.value = position
    }

    fun setChange() {
        _change.value = true
    }

    fun setTVListModelList(tvListModelList: List<TVListModel>) {
        _tvGroupModel.value = tvListModelList
    }

    fun addTVListModel(tvListModel: TVListModel) {
        val currentList = _tvGroupModel.value ?: emptyList()
        val newList = currentList.toMutableList()
        newList.add(tvListModel)
        _tvGroupModel.value = newList
    }

    fun clear() {
        val model0 = getTVListModel(0)
        val model1 = getTVListModel(1)
        if (model0 != null && model1 != null) {
            _tvGroupModel.value = mutableListOf(model0, model1)
            model1.clear()
        }
        setPosition(0)
    }

    fun getTVListModel(): TVListModel? {
        val pos = position.value ?: 0
        return getTVListModel(pos)
    }

    fun getTVListModel(idx: Int): TVListModel? {
        val list = _tvGroupModel.value ?: return null
        if (idx < 0 || idx >= list.size) {
            return null
        }
        return list[idx]
    }

    fun getTVListModelList(): List<TVListModel> {
        return _tvGroupModel.value ?: emptyList()
    }

    init {
        _position.value = 0
    }

    fun size(): Int {
        return _tvGroupModel.value?.size ?: 0
    }
}
