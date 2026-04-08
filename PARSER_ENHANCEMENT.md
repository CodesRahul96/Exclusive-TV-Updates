# M3U Playlist Parser Enhancement for DishHome Nepal Format

## Overview
The M3UParser has been enhanced to support the DishHome Nepal playlist format (and similar formats) while maintaining full backward compatibility with all existing M3U, Kodi, and custom formats.

**Commit ID:** `2e59a41`

## What Changed

### Problem
The original parser would immediately save and reset channel state when encountering header lines like `#KODIPROP`, `#EXTVLCOPT`, or `#EXTHTTP`. This caused issues with playlists where:
- Multiple header lines appear consecutively before the stream URL
- Headers accumulate rather than appear in the #EXTINF metadata line
- Different providers structure their playlists differently

### Solution
Modified the parser to **accumulate headers** instead of immediately resetting state when non-URL lines are encountered.

## Key Enhancements

### 1. **Header Accumulation** (No Premature Reset)
**Before:**
```kotlin
if (trimmedLine.startsWith("#KODIPROP:")) {
    if (currentUris.isNotEmpty()) saveAndReset()  // ← Premature reset!
    // Parse header...
}
```

**After:**
```kotlin
if (trimmedLine.startsWith("#KODIPROP:")) {
    // ← No saveAndReset() - accumulate headers!
    // Parse header and add to currentHeaders map...
}
```

**Benefit:** Allows multiple consecutive header lines to accumulate before URL appears

### 2. **Enhanced #EXTHTTP JSON Parsing**
**Added Support:**
- Nested `headers` object extraction
- Custom JSON fields (cookie, defaultDirection, language, etc.)
- Better error handling with try-catch blocks
- Graceful fallback if JSON parsing fails

```kotlin
val jsonObject = org.json.JSONObject(jsonStr)

// Handle nested "headers" object if exists
val headersObj = jsonObject.optJSONObject("headers")
val sourceObj = if (headersObj != null) headersObj else jsonObject

// Extract all properties dynamically
val keys = sourceObj.keys()
while (keys.hasNext()) {
    val key = keys.next()
    val value = sourceObj.getString(key)
    currentHeaders[normalizeHeaderKey(key)] = value
}
```

### 3. **Multi-line Header Support**
- `#KODIPROP:` lines can now appear multiple times without triggering reset
- `#EXTVLCOPT:` lines accumulate all VLC options
- `#EXTHTTP:` JSON headers merge into single header map

### 4. **Improved Error Handling**
- Wrapped JSON parsing in try-catch blocks
- Logs parsing failures instead of crashing
- Continues with original data on parse failure

### 5. **Format-Agnostic Design**
- No hardcoded playlist provider names
- Works with ANY format using header-before-URL pattern
- Automatically detects and accumulates headers

## Supported Formats

### ✓ DishHome Nepal Format
```
#EXTINF:-1 tvg-id="..." tvg-name="..." tvg-logo="..." group-title="...",...channelname
#KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
#KODIPROP:inputstream.adaptive.license_key={"keys":[...]}
#EXTHTTP:{"cookie":"...","defaultDirection":"..."}
#EXTVLCOPT:http-user-agent=...
#EXTVLCOPT:http-referer=...
https://ottlive.dishhome.com.np/protected/*/dash/manifest.mpd
```

### ✓ Standard M3U Format (Backward Compatible)
```
#EXTINF:-1 tvg-id="..." tvg-logo="..." user-agent="...",...channelname
https://example.com/stream.m3u8
```

### ✓ Kodi Format (Backward Compatible)
```
#EXTINF:-1 tvg-id="..." tvg-logo="...",...channelname
#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
#KODIPROP:inputstream.adaptive.license_key=https://lic.example.com/
https://example.com/dash/manifest.mpd
```

### ✓ Pipe-delimited Headers (E.g., some IPTV links)
```
https://example.com/stream.m3u8|User-Agent=Mozilla&Referer=https://example.com
```

### ✓ Query String Headers
```
https://example.com/stream.m3u8?user-agent=Mozilla&referer=https://example.com
```

## DRM Scheme Support

All schemes handled generically:
- **Widevine:** `com.widevine.alpha`, `widevine`
- **PlayReady:** `com.microsoft.playready`, `playready`
- **ClearKey:** `org.w3.clearkey`, `com.clearkey.alpha`, `clearkey`
- Others: Custom schemes passed through as-is

## Header Properties Extracted

| Source | Properties |
|--------|-----------|
| #EXTINF | tvg-id, tvg-chno, tvg-name, tvg-language, tvg-logo, group-title |
| #KODIPROP | DRM scheme, license key, stream headers |
| #EXTVLCOPT | User-Agent, Referer, Origin, Cookie |
| #EXTHTTP | All JSON fields (cookie, headers, language, direction, etc.) |
| Query String | user-agent, referer, cookie, origin |

## No Hardcoding - Pure Generic Enhancement

This enhancement is completely **format-agnostic**:
- ❌ No provider-specific logic
- ❌ No hardcoded playlist URLs
- ❌ No special cases for specific services
- ✅ Works with ANY playlist using the pattern: headers → URL

The parser intelligently detects header lines and URL lines, accumulating headers until a URL is found, then moving to the next EXTINF block.

## Build Status
- ✓ Compiles without errors
- ✓ All existing tests pass
- ✓ No API changes
- ✓ Full backward compatibility

## Testing Recommendations

Test with:
1. DishHome Nepal format (dishhomenp.m3u)
2. Standard M3U playlists
3. Kodi playlists with KODIPROP
4. Mixed format playlists
5. Old-style simple M3U files

## Files Modified

- `app/src/main/java/com/codesrahul/exclusivetv/models/M3UParser.kt`
  - Enhanced #EXTHTTP parsing (with nested headers support)
  - Removed premature saveAndReset() from header line handlers
  - Improved #KODIPROP accumulation (allows multiple lines)
  - Improved #EXTVLCOPT accumulation (allows multiple lines)
  - Better error handling for JSON parsing
