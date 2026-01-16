# Exclusive TV - Channel Data Structure

This document defines the JSON structure required by the application's main API. The app parses this data into `List<TV>` objects.

## Root Object
The API must return a **JSON Array** of Channel Objects.

```json
[
  { ... },
  { ... }
]
```

## Channel Object Schema

| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `id` | Integer | No | Unique ID for the channel. Defaults to `0` if omitted. |
| `name` | String | Yes | Display name of the channel (used internally). |
| `title` | String | Yes | Title shown in the UI. Often same as `name`. |
| `description` | String | No | Optional description text. |
| `logo` | String | Yes | URL to the channel's logo image. |
| `image` | String | No | Optional alternate image URL. |
| `uris` | Array\<String\> | **YES** | List of stream URLs. Must be an array even if single URL. |
| `headers` | Object | No | Key-Value pairs for HTTP headers (e.g., `{"User-Agent": "..."}`). |
| `group` | String | Yes | Category name (e.g., "Sports", "Movies"). Used for grouping. |
| `type` | String | No | Stream type. Default: "WEB". Enum: `WEB`, `STREAM`. |
| `child` | Array | No | Recursive list of sub-channels (if any). |

## Example JSON

```json
[
  {
    "id": 101,
    "name": "HBO HD",
    "title": "HBO HD",
    "description": "Premium movies and series",
    "logo": "https://example.com/logos/hbo.png",
    "uris": [
      "https://stream-server.com/hbo/index.m3u8"
    ],
    "group": "Movies",
    "headers": {
      "User-Agent": "ExclusiveTV/1.0",
      "Referer": "https://example.com/"
    },
    "type": "STREAM"
  },
  {
    "id": 102,
    "name": "Sports 1",
    "title": "Sports 1 Live",
    "logo": "https://example.com/logos/sports1.png",
    "uris": [
      "https://live-cdn.com/sports1/stream.m3u8"
    ],
    "group": "Sports"
  },
  {
    "id": 103,
    "name": "Sony Ten 1 HD",
    "title": "Sony Ten 1 HD",
    "logo": "https://example.com/logos/sony_ten_1.png",
    "uris": [
      "https://mpd-server.com/manifest.mpd?|drmScheme=clearkey&drmLicense=1234567890abcdef1234567890abcdef:abcdef1234567890abcdef1234567890&User-Agent=Mozilla/5.0"
    ],
    "group": "Sports",
    "type": "STREAM"
  }
]
```

## URL Parameters & DRM
To pass specific headers or DRM keys **within the URL**, use the special separator `?|` (or `?%7C`) followed by standard query parameters.

### Supported Parameters
| Parameter | Description | Example |
| :--- | :--- | :--- |
| `drmScheme` | DRM Scheme to use. currently supports `clearkey`. | `drmScheme=clearkey` |
| `drmLicense` | Config for the DRM scheme. For ClearKey, use `keyId:key` format. | `drmLicense=id:key` |
| `User-Agent` | Sets the User-Agent header for this stream. | `User-Agent=MyApp/1.0` |
| `Referer` | Sets the Referer header. | `Referer=https://site.com/` |
| `Cookie` | Sets the Cookie header. | `Cookie=session=123` |
| `Origin` | Sets the Origin header. | `Origin=https://site.com` |
| `X-Forwarded-For`| Sets the X-Forwarded-For header. | `X-Forwarded-For=1.2.3.4` |

**Example URI with DRM:**
```
https://example.com/video.mpd?|drmScheme=clearkey&drmLicense=key_id_hex:key_hex&User-Agent=MyAgent
```

## Implementation Notes
1.  **URIs:** The `uris` field is strictly a JSON Array. `"uris": "http..."` will fail. Use `"uris": ["http..."]`.
2.  **Encryption:** The app expects this entire JSON string to be encrypted/encoded using the `Gua` library format if fetched from the main secure API. If loading from local plain text, standard JSON is fine.

## Common Mistakes causing "Channel format error"

> [!CAUTION]
> **CRITICAL: `uris` MUST BE AN ARRAY of strings.**

- **❌ INCORRECT (Will Crash):**
  ```json
  "uris": "https://example.com/stream.m3u8"
  ```

- **✅ CORRECT:**
  ```json
  "uris": [
    "https://example.com/stream.m3u8"
  ]
  ```
  Even if there is only one link, it **must** be inside square brackets `[]`.

- **❌ INCORRECT (Missing Group):**
  Leaving `"group"` empty or null can cause the channel to be hidden. Always provide a category name like `"Sports"`.
