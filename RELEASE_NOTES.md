# ExclusiveTV App v1.0.55

This hotfix release addresses a critical issue with the in-app update mechanism.

## 🐛 Critical Fixes
*   **Update Server Connection**: Resolved the "Could not connect to update server" error.
    *   Switching to a more compatible HTTP client (`UnsafeHttpClient`) to bypass SSL certificate verification issues common on older Android TV boxes.
    *   Added robust URL handling to prevent crashes from malformed remote configuration values.

## 🚀 Key Features (from v1.0.54)
*   **Mobile UI Optimization**: Redesigned dialogs and list items for better mobile compatibility.
*   **Favorites System**: Fixed "My Collection" persistence and display logic.
*   **Visual Polish**: Compact buttons and smoother animations.

## Technical Details
*   **Version Code**: 16791041
*   **Build**: Debug
