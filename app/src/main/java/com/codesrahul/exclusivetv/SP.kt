package com.codesrahul.exclusivetv


import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SP {
    // If Change channel with up and down in reversed order or not
    private const val KEY_CHANNEL_REVERSAL = "channel_reversal"

    // If use channel num to select channel or not
    private const val KEY_CHANNEL_NUM = "channel_num"

    private const val KEY_TIME = "time"

    // If start app on device boot or not
    private const val KEY_BOOT_STARTUP = "boot_startup"

    // Position in list of the selected channel item
    private const val KEY_POSITION = "position"

    private const val KEY_POSITION_GROUP = "position_group"

    private const val KEY_POSITION_SUB = "position_sub"

    private const val KEY_REPEAT_INFO = "repeat_info"

    private const val KEY_CONFIG = "config"
    private const val KEY_STANDARD_CONFIG = "standard_config" // [NEW]
    private const val KEY_PREMIUM_CONFIG = "premium_config"   // [NEW]

    private const val KEY_CONFIG_AUTO_LOAD = "config_auto_load"

    private const val KEY_CHANNEL = "channel"

    private const val KEY_LIKE = "like"

    const val KEY_EPG = "epg"

    private const val KEY_CONFIG_CHANNEL_CHECK = "config_channel_check"

    private const val KEY_MOVE_MODE = "move_mode"
    private const val KEY_WATCH_LAST = "watch_last"
    private const val KEY_FORCE_HIGH_QUALITY = "force_high_quality"
    private const val KEY_LAST_VERSION = "last_version"
    private const val KEY_LAST_CHANNEL_URL = "last_channel_url"
    private const val KEY_LAST_CHANNEL_NAME = "last_channel_name"
    private const val KEY_ETAG_MAP = "etag_map" // New Map-based system
    const val KEY_EPG_ENABLED = "epg_enabled"
    const val KEY_SHOW_DATE_IN_INFO = "show_date_in_info" // Added key
    
    // Watermark settings
    private const val KEY_WATERMARK_ENABLED = "watermark_enabled"
    private const val KEY_WATERMARK_OPACITY = "watermark_opacity"
    private const val KEY_WATERMARK_POSITION = "watermark_position"
 
    private const val KEY_EPG_SHIFT = "epg_shift"
    
    private const val KEY_LAST_UPDATE_TIME = "last_update_time"
    private const val KEY_API_HOST = "api_host"
    private const val KEY_API_DOWNLOAD_HOST = "api_download_host"
    private const val KEY_API_HOST_FALLBACK = "api_host_fallback"
    private const val KEY_API_DOWNLOAD_HOST_FALLBACK = "api_download_host_fallback"
    private const val KEY_PLAN_NAME = "plan_name" // [NEW] Persist plan for startup safety
    private const val KEY_USER_ID = "user_id" // Persist phone number replacing Firebase Auth
    private const val KEY_FAVORITE_URLS = "favorite_urls" // [NEW] For Cloud Sync

    private lateinit var sp: SharedPreferences
    private lateinit var esp: SharedPreferences
    private var initialized = false

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<OnSharedPreferenceChangeListener>()


    var lastUpdateTime: Long
        get() = sp.getLong(KEY_LAST_UPDATE_TIME, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_UPDATE_TIME, value).apply()


    // Multiple playlist URLs
    private const val KEY_PLAYLIST_URLS = "playlist_urls"

    private const val KEY_HIDDEN_GROUPS = "hidden_groups"
    private const val KEY_GROUP_ORDER = "group_order"

    // Buffer settings
    private const val KEY_BUFFER_MODE = "buffer_mode"

    // ... (existing constants)

    var channelReversal: Boolean
        get() = sp.getBoolean(KEY_CHANNEL_REVERSAL, false)
        set(value) = sp.edit().putBoolean(KEY_CHANNEL_REVERSAL, value).apply()

    var channelNum: Boolean
        get() = sp.getBoolean(KEY_CHANNEL_NUM, true)
        set(value) = sp.edit().putBoolean(KEY_CHANNEL_NUM, value).apply()

    var time: Boolean
        get() = sp.getBoolean(KEY_TIME, true)
        set(value) = sp.edit().putBoolean(KEY_TIME, value).apply()

    var bootStartup: Boolean
        get() = sp.getBoolean(KEY_BOOT_STARTUP, false)
        set(value) = sp.edit().putBoolean(KEY_BOOT_STARTUP, value).apply()

    var position: Int
        get() = sp.getInt(KEY_POSITION, 0)
        set(value) = sp.edit().putInt(KEY_POSITION, value).apply()

    var positionGroup: Int
        get() = sp.getInt(KEY_POSITION_GROUP, 0)
        set(value) = sp.edit().putInt(KEY_POSITION_GROUP, value).apply()

    var positionSub: Int
        get() = sp.getInt(KEY_POSITION_SUB, 0)
        set(value) = sp.edit().putInt(KEY_POSITION_SUB, value).apply()

    var repeatInfo: Boolean
        get() = sp.getBoolean(KEY_REPEAT_INFO, true)
        set(value) = sp.edit().putBoolean(KEY_REPEAT_INFO, value).apply()



    var bufferMode: Int
        get() = sp.getInt(KEY_BUFFER_MODE, 0) // 0: Default, 1: Low, 2: High
        set(value) = sp.edit().putInt(KEY_BUFFER_MODE, value).apply()

    /**
     * The method must be invoked as early as possible(At least before using the keys)
     */
    fun init(context: Context) {
        sp = context.getSharedPreferences(
            context.resources.getString(R.string.app_name),
            Context.MODE_PRIVATE
        )
        
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            esp = EncryptedSharedPreferences.create(
                context,
                context.resources.getString(R.string.app_name) + "_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            migrate()
        } catch (e: Exception) {
            // Fallback if hardware keystore fails (rare but possible on some TVs)
            esp = sp 
        }
        initialized = true
    }

    private fun migrate() {
        // Move sensitive keys from sp to esp if they exist
        val sensitiveKeys = listOf(
            KEY_USER_ID, KEY_API_HOST, KEY_API_DOWNLOAD_HOST, 
            KEY_API_HOST_FALLBACK, KEY_API_DOWNLOAD_HOST_FALLBACK,
            KEY_STANDARD_CONFIG, KEY_PREMIUM_CONFIG
        )
        
        val editor = sp.edit()
        val eEditor = esp.edit()
        var changed = false
        
        for (key in sensitiveKeys) {
            if (sp.contains(key)) {
                val value = sp.getString(key, null)
                if (value != null) {
                    eEditor.putString(key, value)
                    editor.remove(key)
                    changed = true
                }
            }
        }
        
        if (changed) {
            eEditor.commit() // commit to ensure saved
            editor.commit()
        }
    }

    fun setOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners(key: String) {
        listeners.forEach { it.onSharedPreferenceChanged(key) }
    }

    // ... (existing methods)

    // Deprecated: verify usages and migrate to playlistUrls
    var config: String?
        get() = sp.getString(KEY_CONFIG, "")
        set(value) = sp.edit().putString(KEY_CONFIG, value).apply()

    var standardConfig: String?
        get() = esp.getString(KEY_STANDARD_CONFIG, "")
        set(value) = esp.edit().putString(KEY_STANDARD_CONFIG, value).apply()

    var premiumConfig: String?
        get() = esp.getString(KEY_PREMIUM_CONFIG, "")
        set(value) = esp.edit().putString(KEY_PREMIUM_CONFIG, value).apply()

    var planName: String?
        get() = sp.getString(KEY_PLAN_NAME, "Standard")
        set(value) = sp.edit().putString(KEY_PLAN_NAME, value).apply()

    var userId: String?
        get() = esp.getString(KEY_USER_ID, null)
        set(value) = esp.edit().putString(KEY_USER_ID, value).apply()

    var playlistUrls: Set<String>
        get() = sp.getStringSet(KEY_PLAYLIST_URLS, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_PLAYLIST_URLS, value).apply()

    fun addPlaylistUrl(url: String) {
        val current = playlistUrls.toMutableSet()
        if (current.add(url)) {
             playlistUrls = current
             if (initialized) SyncManager.syncUp()
        }
    }

    fun removePlaylistUrl(url: String) {
        val current = playlistUrls.toMutableSet()
        if (current.remove(url)) {
             playlistUrls = current
             if (initialized) SyncManager.syncUp()
        }
    }

    var hiddenGroups: Set<String>
        get() = sp.getStringSet(KEY_HIDDEN_GROUPS, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_HIDDEN_GROUPS, value).apply()

    var groupOrder: String?
        get() = sp.getString(KEY_GROUP_ORDER, "")
        set(value) = sp.edit().putString(KEY_GROUP_ORDER, value).apply()
    
    fun toggleGroupVisibility(groupName: String) {
        val current = hiddenGroups.toMutableSet()
        if (current.contains(groupName)) {
            current.remove(groupName)
        } else {
            current.add(groupName)
        }
        hiddenGroups = current
    }
    
    // ... (rest of the file)

    var configAutoLoad: Boolean
        get() = sp.getBoolean(KEY_CONFIG_AUTO_LOAD, true)
        set(value) = sp.edit().putBoolean(KEY_CONFIG_AUTO_LOAD, value).apply()

    var channel: Int
        get() = sp.getInt(KEY_CHANNEL, 0)
        set(value) = sp.edit().putInt(KEY_CHANNEL, value).apply()

    var channelCheck: Boolean
        get() = sp.getBoolean(KEY_CONFIG_CHANNEL_CHECK, false)
        set(value) = sp.edit().putBoolean(KEY_CONFIG_CHANNEL_CHECK, value).apply()

    var moveMode: Boolean
        get() = sp.getBoolean(KEY_MOVE_MODE, false)
        set(value) = sp.edit().putBoolean(KEY_MOVE_MODE, value).apply()

    var watchLast: Boolean
        get() = sp.getBoolean(KEY_WATCH_LAST, true)
        set(value) = sp.edit().putBoolean(KEY_WATCH_LAST, value).apply()

    var forceHighQuality: Boolean
        get() = sp.getBoolean(KEY_FORCE_HIGH_QUALITY, true)
        set(value) = sp.edit().putBoolean(KEY_FORCE_HIGH_QUALITY, value).apply()

    var lastVersion: Int
        get() = sp.getInt(KEY_LAST_VERSION, 0)
        set(value) = sp.edit().putInt(KEY_LAST_VERSION, value).apply()

    var defaultAudioLanguage: String
        get() = sp.getString("default_audio_language", "") ?: ""
        set(value) = sp.edit().putString("default_audio_language", value).apply()

    var lastChannelUrl: String
        get() = sp.getString(KEY_LAST_CHANNEL_URL, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_CHANNEL_URL, value).apply()

    var lastChannelName: String
        get() = sp.getString(KEY_LAST_CHANNEL_NAME, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_CHANNEL_NAME, value).apply()

    var favoriteUrls: Set<String>
        get() = sp.getStringSet(KEY_FAVORITE_URLS, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_FAVORITE_URLS, value).apply()

    fun addFavoriteUrl(url: String) {
        val current = favoriteUrls.toMutableSet()
        if (current.add(url)) {
            favoriteUrls = current
        }
    }

    fun removeFavoriteUrl(url: String) {
        val current = favoriteUrls.toMutableSet()
        if (current.remove(url)) {
            favoriteUrls = current
        }
    }

    fun getEtag(url: String): String? {
        return sp.getString(KEY_ETAG_MAP + "_" + url.hashCode(), null)
    }

    fun setEtag(url: String, etag: String?) {
        if (etag == null) {
            sp.edit().remove(KEY_ETAG_MAP + "_" + url.hashCode()).apply()
        } else {
            sp.edit().putString(KEY_ETAG_MAP + "_" + url.hashCode(), etag).apply()
        }
    }

    var epgEnabled: Boolean
        get() = sp.getBoolean(KEY_EPG_ENABLED, false)
        set(value) {
            if (value != this.epgEnabled) {
                sp.edit().putBoolean(KEY_EPG_ENABLED, value).apply()
                notifyListeners(KEY_EPG_ENABLED)
            }
        }

    var showDateInInfo: Boolean
        get() = sp.getBoolean(KEY_SHOW_DATE_IN_INFO, true) // Default: true
        set(value) = sp.edit().putBoolean(KEY_SHOW_DATE_IN_INFO, value).apply()

    fun getLike(id: Int): Boolean {
        val stringSet = sp.getStringSet(KEY_LIKE, emptySet())
        return stringSet?.contains(id.toString()) ?: false
    }

    fun setLike(id: Int, liked: Boolean) {
        val stringSet = sp.getStringSet(KEY_LIKE, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (liked) {
            stringSet.add(id.toString())
        } else {
            stringSet.remove(id.toString())
        }

        sp.edit().putStringSet(KEY_LIKE, stringSet).apply()
    }

    fun deleteLike() {
        sp.edit().remove(KEY_LIKE).apply()
    }

    var epg: String?
        get() = sp.getString(KEY_EPG, "")
        set(value)  {
            if (value != this.epg) {
                sp.edit().putString(KEY_EPG, value).apply()
                notifyListeners(KEY_EPG)
            }
        }

    private const val KEY_AUDIO_TRACK_PREFIX = "audio_track_"

    fun getAudioTrack(channelKey: String): Int {
        return sp.getInt(KEY_AUDIO_TRACK_PREFIX + channelKey, -1)
    }

    fun setAudioTrack(channelKey: String, index: Int) {
        sp.edit().putInt(KEY_AUDIO_TRACK_PREFIX + channelKey, index).apply()
    }
    
    // Watermark settings
    var watermarkEnabled: Boolean
        get() = sp.getBoolean(KEY_WATERMARK_ENABLED, true) // Default: enabled
        set(value) = sp.edit().putBoolean(KEY_WATERMARK_ENABLED, value).apply()

    var watermarkOpacity: Int
        get() = sp.getInt(KEY_WATERMARK_OPACITY, 40) // Default: 40%
        set(value) = sp.edit().putInt(KEY_WATERMARK_OPACITY, value).apply()

    var watermarkPosition: String
        get() = sp.getString(KEY_WATERMARK_POSITION, "bottom_right") ?: "bottom_right"
        set(value) = sp.edit().putString(KEY_WATERMARK_POSITION, value).apply()

    // Dynamic URL Properties
    var apiHost: String
        get() = esp.getString(KEY_API_HOST, "") ?: ""
        set(value) = esp.edit().putString(KEY_API_HOST, value).apply()

    var apiDownloadHost: String
        get() = esp.getString(KEY_API_DOWNLOAD_HOST, "") ?: ""
        set(value) = esp.edit().putString(KEY_API_DOWNLOAD_HOST, value).apply()

    var apiHostFallback: String
        get() = esp.getString(KEY_API_HOST_FALLBACK, "") ?: ""
        set(value) = esp.edit().putString(KEY_API_HOST_FALLBACK, value).apply()

    var apiDownloadHostFallback: String
        get() = esp.getString(KEY_API_DOWNLOAD_HOST_FALLBACK, "") ?: ""
        set(value) = esp.edit().putString(KEY_API_DOWNLOAD_HOST_FALLBACK, value).apply()

 
    var epgShift: Int
        get() = sp.getInt(KEY_EPG_SHIFT, 0)
        set(value) {
            sp.edit().putInt(KEY_EPG_SHIFT, value).apply()
            notifyListeners(KEY_EPG_SHIFT)
        }

    // Audio Stabilizer
    private const val KEY_AUDIO_STABILIZER = "audio_stabilizer"
    
    var audioStabilizer: Boolean
        get() = sp.getBoolean(KEY_AUDIO_STABILIZER, false) // Default: Disabled
        set(value) = sp.edit().putBoolean(KEY_AUDIO_STABILIZER, value).apply()

    // PIP Mode
    private const val KEY_PIP_MODE = "pip_mode"

    var pipMode: Boolean
        get() = sp.getBoolean(KEY_PIP_MODE, false) // Default: Disabled
        set(value) = sp.edit().putBoolean(KEY_PIP_MODE, value).apply()

    fun reset() {
        sp.edit().clear().commit()
    }
}
