package com.codesrahul.exclusivetv.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.codesrahul.exclusivetv.SP
import com.codesrahul.exclusivetv.EPGManager
import com.codesrahul.exclusivetv.SyncManager

class TVModel(var tv: TV) : ViewModel() {

    // Helper for init to start with 0 index safely
    private fun getInitialUrl(): String {
        if (tv.uris.isEmpty()) return ""
        return tv.uris.getOrElse(0) { "" }
    }

    // Constructor initialization ensuring non-null immediate values (Thread Safe)
    private val _position = MutableLiveData<Int>(0)
    val position: LiveData<Int> get() = _position

    private val _videoIndex = MutableLiveData<Int>(0)
    
    // We initialize videoUrl with the first URL explicitly
    private val _videoUrl = MutableLiveData<String>(getInitialUrl())
    val videoUrl: LiveData<String> get() = _videoUrl

    private val _like = MutableLiveData<Boolean>(SP.getLike(tv.id))
    val like: LiveData<Boolean> get() = _like

    private val _program = MutableLiveData<MutableList<Program>>(mutableListOf())
    val program: LiveData<MutableList<Program>> get() = _program

    private val _currentProgram = MutableLiveData<EPGProgram?>()
    val currentProgram: LiveData<EPGProgram?> get() = _currentProgram

    private val _upcomingProgram = MutableLiveData<EPGProgram?>()
    val upcomingProgram: LiveData<EPGProgram?> get() = _upcomingProgram

    private val _errInfo = MutableLiveData<String>("")
    val errInfo: LiveData<String> get() = _errInfo

    private val _videoQuality = MutableLiveData<String>("")
    val videoQuality: LiveData<String> get() = _videoQuality

    private val _audioQuality = MutableLiveData<String>("")
    val audioQuality: LiveData<String> get() = _audioQuality

    private val _ready = MutableLiveData<Boolean>()
    val ready: LiveData<Boolean> get() = _ready

    // Mutable Variables
    var retryTimes = 0
    var retryMaxTimes = 8
    var programUpdateTime = 0L

    var groupIndex = 0
    var listIndex = 0

    init {
        updateEPG()
    }

    // Methods

    fun setErrInfo(info: String) {
        _errInfo.postValue(info)
    }

    fun setVideoUrl(url: String) {
        _videoUrl.postValue(url)
    }

    fun setLike(liked: Boolean) {
        _like.postValue(liked)
        SP.setLike(tv.id, liked)
        
        // Phase 4: URL-based favorites for Cloud Sync
        val url = tv.uris.firstOrNull() ?: ""
        if (url.isNotEmpty()) {
            if (liked) {
                SP.addFavoriteUrl(url)
            } else {
                SP.removeFavoriteUrl(url)
            }
            // Trigger Cloud Sync
            SyncManager.pushFavoriteChange()
        }
        
        TVList.notifyLikeChanged(this, liked)
    }

    fun setReady() {
        _ready.postValue(true)
    }

    fun setVideoQuality(q: String) {
        _videoQuality.postValue(q)
    }

    fun setAudioQuality(q: String) {
        _audioQuality.postValue(q)
    }

    fun updateEPG() {
        if (SP.epgEnabled) {
            _currentProgram.postValue(EPGManager.getCurrentProgram(tv.name, tv.apiId))
            _upcomingProgram.postValue(EPGManager.getUpcomingProgram(tv.name, tv.apiId))
        } else {
            _currentProgram.postValue(null)
            _upcomingProgram.postValue(null)
        }
    }

    fun update(t: TV) {
        tv = t
        updateEPG()
    }

    fun nextVideoUrl(): Boolean {
        val current = _videoIndex.value ?: 0
        if (current + 1 < tv.uris.size) {
            val nextIndex = current + 1
            _videoIndex.postValue(nextIndex)
            val nextUrl = tv.uris.getOrElse(nextIndex) { "" }
            _videoUrl.postValue(nextUrl)
            return true
        }
        return false
    }

    companion object {
        private const val TAG = "TVModel"
    }
}
