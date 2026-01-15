package com.codesrahul.exclusivetv


import android.content.Context
import android.content.SharedPreferences

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

    private const val KEY_CONFIG_AUTO_LOAD = "config_auto_load"

    private const val KEY_CHANNEL = "channel"

    private const val KEY_LIKE = "like"

    const val KEY_EPG = "epg"

    private const val KEY_CONFIG_CHANNEL_CHECK = "config_channel_check"

    private const val KEY_MOVE_MODE = "move_mode"
    private const val KEY_WATCH_LAST = "watch_last"
    private const val KEY_FORCE_HIGH_QUALITY = "force_high_quality"

    private lateinit var sp: SharedPreferences

    private var listener: OnSharedPreferenceChangeListener? = null

    /**
     * The method must be invoked as early as possible(At least before using the keys)
     */
    fun init(context: Context) {
        sp = context.getSharedPreferences(
            context.resources.getString(R.string.app_name),
            Context.MODE_PRIVATE
        )
    }

    fun setOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        this.listener = listener
    }

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

    var config: String?
        get() = sp.getString(KEY_CONFIG, "")
        set(value) = sp.edit().putString(KEY_CONFIG, value).apply()

    var configAutoLoad: Boolean
        get() = sp.getBoolean(KEY_CONFIG_AUTO_LOAD, true)
        set(value) = sp.edit().putBoolean(KEY_CONFIG_AUTO_LOAD, value).apply()

    var channel: Int
        get() = sp.getInt(KEY_CHANNEL, 0)
        set(value) = sp.edit().putInt(KEY_CHANNEL, value).apply()

    var channelCheck: Boolean
        get() = sp.getBoolean(KEY_CONFIG_CHANNEL_CHECK, true)
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
                listener?.onSharedPreferenceChanged(KEY_EPG)
            }
        }

    private const val KEY_AUDIO_TRACK_PREFIX = "audio_track_"

    fun getAudioTrack(channelKey: String): Int {
        return sp.getInt(KEY_AUDIO_TRACK_PREFIX + channelKey, -1)
    }

    fun setAudioTrack(channelKey: String, index: Int) {
        sp.edit().putInt(KEY_AUDIO_TRACK_PREFIX + channelKey, index).apply()
    }
}
