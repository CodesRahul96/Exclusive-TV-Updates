package com.codesrahul.exclusivetv

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages persistent storage for category and channel ordering and renaming
 */
object OrderPreferenceManager {
    private const val PREFS_NAME = "order_preferences"
    private const val KEY_CATEGORY_ORDER = "category_order"
    private const val KEY_CHANNEL_ORDER_PREFIX = "channel_order_"
    private const val KEY_CATEGORY_RENAME = "category_rename"
    private const val KEY_CHANNEL_RENAME = "channel_rename"
    private const val KEY_HIDDEN_CATEGORIES = "hidden_categories"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Category Order Management
    fun saveCategoryOrder(categoryNames: List<String>) {
        val json = gson.toJson(categoryNames)
        prefs.edit().putString(KEY_CATEGORY_ORDER, json).apply()
    }

    fun getCategoryOrder(): List<String>? {
        val json = prefs.getString(KEY_CATEGORY_ORDER, null) ?: return null
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    // Channel Order Management (by URL as key)
    fun saveChannelOrder(categoryName: String, channelUrls: List<String>) {
        val key = "$KEY_CHANNEL_ORDER_PREFIX$categoryName"
        val json = gson.toJson(channelUrls)
        prefs.edit().putString(key, json).apply()
    }

    fun getChannelOrder(categoryName: String): List<String>? {
        val key = "$KEY_CHANNEL_ORDER_PREFIX$categoryName"
        val json = prefs.getString(key, null) ?: return null
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    // Category Rename Management
    fun saveCategoryRename(originalName: String, newName: String) {
        val renames = getCategoryRenames().toMutableMap()
        renames[originalName] = newName
        val json = gson.toJson(renames)
        prefs.edit().putString(KEY_CATEGORY_RENAME, json).apply()
    }

    fun removeCategoryRename(originalName: String) {
        val renames = getCategoryRenames().toMutableMap()
        if (renames.containsKey(originalName)) {
            renames.remove(originalName)
            val json = gson.toJson(renames)
            prefs.edit().putString(KEY_CATEGORY_RENAME, json).apply()
        }
    }

    fun getCategoryRenames(): Map<String, String> {
        val json = prefs.getString(KEY_CATEGORY_RENAME, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, String>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getCategoryDisplayName(originalName: String): String {
        return getCategoryRenames()[originalName] ?: originalName
    }

    // Category Visibility Management
    fun saveHiddenCategories(hiddenNames: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN_CATEGORIES, hiddenNames).apply()
    }

    fun getHiddenCategories(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN_CATEGORIES, emptySet()) ?: emptySet()
    }

    fun isCategoryHidden(name: String): Boolean {
        return getHiddenCategories().contains(name)
    }

    // Channel Rename Management (by URL as key)
    fun saveChannelRename(url: String, newName: String) {
        val renames = getChannelRenames().toMutableMap()
        renames[url] = newName
        val json = gson.toJson(renames)
        prefs.edit().putString(KEY_CHANNEL_RENAME, json).apply()
    }

    fun getChannelRenames(): Map<String, String> {
        val json = prefs.getString(KEY_CHANNEL_RENAME, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, String>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getChannelDisplayName(url: String, originalName: String): String {
        return getChannelRenames()[url] ?: originalName
    }

    // Reset all order and rename data
    fun resetAll() {
        prefs.edit().clear().apply()
    }

    // Reset only order (keep renames)
    fun resetOrder() {
        prefs.edit().remove(KEY_CATEGORY_ORDER).apply()
        // Remove all channel order keys
        val allPrefs = prefs.all
        for (key in allPrefs.keys) {
            if (key.startsWith(KEY_CHANNEL_ORDER_PREFIX)) {
                prefs.edit().remove(key).apply()
            }
        }
    }

    // Reset only renames (keep order)
    fun resetRenames() {
        prefs.edit().remove(KEY_CATEGORY_RENAME).remove(KEY_CHANNEL_RENAME).apply()
    }
}
