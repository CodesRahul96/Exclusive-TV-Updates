# Complete Parser & Audio Format Enhancement Documentation

**Commit Date:** April 8, 2026  
**Version:** Complete Parser Enhancement + Audio Format Support

## Executive Summary

Comprehensive enhancement of the ExclusiveTV parser system to:
1. **Support all major playlist formats** (M3U, JSON, PLS, STRM, CSV, TSV, ASX, XSPF, TVPL)
2. **Detect audio and video formats** (Dolby Digital, Dolby Atmos, DTS, AAC, Opus, etc.)
3. **Ensure device compatibility** (Fire TV Stick 4K, Android TV, Mobile, Web)
4. **Maintain 100% backward compatibility** with all existing formats

## What's New

### 1. Extended TV Data Model (`TV.kt`)
Added optional fields for audio/video metadata:
```kotlin
audioFormats: Set<String>              // e.g., ["AAC", "AC3", "EAC3-JOC"]
audioCodec: String?                    // Specific codec name
videoCodec: String?                    // H.264, H.265, AV1, etc.
dolbyDigital: Boolean                  // AC3/EAC3 support
dolbyAtmos: Boolean                    // Dolby Atmos (EAC3-JOC)
dolbyTrueHD: Boolean                   // Dolby TrueHD support
resolution: String?                    // 1080p, 4K, 720p
bitrate: String?                       // 5000k, 10Mbps
frameRate: String?                     // 29.97, 60
hdrEnabled: Boolean                    // HDR support
hdrType: String?                       // HDR10, Dolby Vision, HLG
framePacking: String?                  // 3D support
subtitles: List<SubtitleTrack>        // Subtitle list
compatibleDevices: Set<String>         // Target device types
```

**Non-Breaking:** All new fields have sensible defaults. Existing code works unchanged.

### 2. New Audio/Video Format System

#### AudioFormat Enum (`AudioVideoFormats.kt`)
Comprehensive codec support:
- **AAC variants:** AAC, AAC-LC, AAC-HE, AAC-HE v2
- **Dolby:** AC3, EAC3, EAC3-JOC (Atmos), TrueHD
- **DTS:** DTS, DTS-HD, DTS:X
- **Other:** Opus, FLAC, Vorbis, MP3, ALAC
- **Channels:** Mono, Stereo, 5.1, 7.1

#### DeviceType Enum
Device-specific audio support:
- **Fire TV Stick 4K:** AC3, EAC3, AAC, FLAC, Opus, MP3
- **Fire TV Cube:** Above + DTS, DTS-HD, DTS:X, TrueHD
- **Android TV:** AAC, AC3, EAC3, Opus, FLAC, MP3
- **Android TV 11+:** Above + TrueHD, DTS, DTS-HD
- **Mobile:** AAC, Opus, FLAC, MP3
- **Smart TV:** AAC, AC3, EAC3, Opus, FLAC, MP3
- **Web Browser:** AAC, Opus, Vorbis, MP3

#### SubtitleTrack Data Class
Subtitle information for streams with language, URL, format (srt, vtt, ass, ssa)

### 3. Format Detection System (`FormatDetection.kt`)

#### AudioFormatDetector
Detects audio formats from:
- **Stream URLs** - File extensions, codec parameters
- **HLS/DASH manifests** - #EXT-X-MEDIA tags, DASH codecs
- **Codec strings** - RFC 6381 codec notation (mp4a.40.2, ac-3, ec-3, etc.)
- **M3U metadata** - #EXTINF, #KODIPROP tags

#### DeviceCompatibilityChecker
- `getCompatibleFormats()` - Filter formats for device
- `isDeviceCompatible()` - Check content compatibility
- `getRecommendedDevices()` - Find best devices for content
- `getCompatibilityScore()` - 0-100 compatibility rating

### 4. Enhanced ParserFactory (`ParserFactory.kt`)

Now supports:
- **M3U/M3U8** ✓ (existing)
- **JSON** ✓ (existing)
- **PLS** ✓ (existing)
- **STRM** ✓ (Kodi format)
- **CSV/TSV** ✓ (tabular data)
- **TVPL** ✓ (name|url format)
- **ASX** ✓ (Windows Media)
- **XSPF** ✓ (Spotify-like playlist)

**Auto-detection** handles format-specific headers and patterns.

### 5. Enhanced All Parsers with Audio Detection

#### M3UParser
- Auto-detects audio formats from URLs
- Sets Dolby Digital/Atmos flags
- Determines device compatibility
- Maintains DishHome Nepal format support

#### KodiParser
- Same audio detection as M3UParser
- Preserves KODIPROP DRM handling
- Auto-populates audio format fields

#### GenericJsonParser  
- Detects formats from stream URLs
- Handles all JSON playlist variations
- Sets device compatibility automatically

#### SimpleListParser
- Format detection for plain URL lists
- Basic device compatibility

#### PlsParser
- Audio format detection for PLS files
- Device compatibility flags

## DishHome Nepal Playlist Support

**Complete Format Support:**
```
#EXTINF:metadata
#KODIPROP:inputstream.adaptive.license_type=...
#KODIPROP:inputstream.adaptive.license_key=...
#EXTHTTP:{"cookie":"...", "header":"..."}
#EXTVLCOPT:http-user-agent=...
#EXTVLCOPT:http-referer=...
https://ottlive.dishhome.com.np/.../dash/manifest.mpd
```

**Tested Scenarios:**
- Multiple KODIPROP lines accumulate correctly
- JSON headers in EXTHTTP are parsed
- VLC options accumulate without reset
- All headers present when URL is added
- Backward compatible with old M3U formats

## Implementation Details

### Key Design Decisions

1. **Format-Agnostic Audio Detection**
   - No hardcoded provider logic
   - Works with any URL pattern
   - Graceful degradation if detection fails

2. **Backward Compatibility**
   - All new fields are optional
   - Default sensible values
   - Existing parsers work unchanged
   - No API breaking changes

3. **Device Compatibility**
   - Matches content to device capabilities
   - Automatic restriction for premium formats (Atmos, TrueHD)
   - Configurable per-stream

### Code Quality
- ✓ Type-safe with proper null handling
- ✓ Comprehensive error handling with try-catch
- ✓ Logging for debugging
- ✓ RFC 6381 codec string support
- ✓ HLS and DASH manifest parsing ready

## Testing

### Test File: `DishHomePlaylistTest.kt`
Comprehensive tests for:
- DishHome playlist parsing (4 channels)
- Audio format detection accuracy
- Device compatibility logic
- Header preservation
- DRM scheme detection
- Backward compatibility verification

Run tests via: `DishHomePlaylistTest.runDishHomeTests()`

## Device Support Matrix

| Device | AAC | AC3 | EAC3 | Atmos | DTS | TrueHD | Opus | FLAC |
|--------|-----|-----|------|-------|-----|--------|------|------|
| Fire TV 4K | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ | ✓ | ✓ |
| Fire TV Cube | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Android TV | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ | ✓ | ✓ |
| Android TV 11+ | ✓ | ✓ | ✓ | ✗ | ✓ | ✓ | ✓ | ✓ |
| Mobile | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ |
| Smart TV | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ | ✓ | ✓ |
| Web | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✗ |

## Files Modified

1. **Core Model Enhancement:**
   - `TV.kt` - Added audio/video metadata fields

2. **New Format System:**
   - `AudioVideoFormats.kt` - Enums for codecs and devices
   - `FormatDetection.kt` - Detection and compatibility logic

3. **Parser Enhancements:**
   - `M3UParser.kt` - Audio detection in createTV()
   - `KodiParser.kt` - Audio detection in createTV()
   - `GenericJsonParser.kt` - Audio detection in parsing
   - `SimpleListParser.kt` - Audio detection for URLs
   - `PlsParser.kt` - Audio detection for PLS
   - `ParserFactory.kt` - New format support (CSV, TSV, ASX, XSPF, TVPL)

4. **Testing:**
   - `DishHomePlaylistTest.kt` - Comprehensive format verification

## Build Status
✓ **BUILD SUCCESSFUL** - No errors, warnings only for deprecated Android APIs

## Backward Compatibility Verification
- ✓ Old M3U files work unchanged
- ✓ JSON playlists work unchanged
- ✓ PLS files work unchanged
- ✓ Simple URL lists work unchanged
- ✓ Kodi KODIPROP format works unchanged
- ✓ All DRM schemes work unchanged
- ✓ All header handling works unchanged

## Usage Examples

### Using audio format detection:
```kotlin
val tv = parsedChannels[0]
if (tv.dolbyAtmos) {
    Log.d("Audio", "This stream has Dolby Atmos!")
}
if (AudioFormat.OPUS in (tv.audioFormats.mapNotNull { AudioFormat.fromString(it) })) {
    Log.d("Audio", "Opus audio available")
}
```

### Checking device compatibility:
```kotlin
val compatibility = DeviceCompatibilityChecker.getCompatibilityScore(
    DeviceType.FIRETV_STICK_4K,
    tv
)
Log.d("Device", "Compatibility score: $compatibility/100")
```

### Getting compatible formats:
```kotlin
val compatibleFormats = DeviceCompatibilityChecker.getCompatibleFormats(
    DeviceType.MOBILE_DEVICE,
    tv.audioFormats.mapNotNull { AudioFormat.fromString(it) }.toSet()
)
```

## Performance Impact
- ✓ Lazy format detection (only when needed)
- ✓ Efficient regex patterns (compiled once)
- ✓ Minimal memory overhead
- ✓ No blocking I/O operations
- ✓ Parser throughput unchanged

## Future Enhancements
1. Subtitle track extraction from manifests
2. Video codec quality ratings
3. Bitrate recommendations per device
4. CDN optimization tips
5. Cache-aware format selection

## Support

For DishHome playlist or other format issues:
1. Verify playlist format matches documentation
2. Check device compatibility with DeviceCompatibilityChecker
3. Review audio format detection via AudioFormatDetector.detectFromUrl()
4. Run DishHomePlaylistTest.runDishHomeTests() for verification
