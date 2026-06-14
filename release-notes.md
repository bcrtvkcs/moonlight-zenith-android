# Moonlight Zenith v1.0.9

## New Features & Improvements
- **Native Touch Mode: Long-Press to Right-Click**: You can now perform a long-press in native touch mode to trigger a right-click. This eliminates the need to awkwardly double-tap or use on-screen buttons for a simple right-click action on desktop environments like KDE Plasma 6 Wayland!
- **Configurable Right-Click Delay**: We added a brand new preferences menu that lets you adjust the right-click detection delay (300ms, 500ms, 800ms, or 1000ms) to fit your personal workflow.
- **Toggleable Native Right-Click**: Don't want the app to handle long-press? You can easily disable it in the settings, falling back to your device's native touch behavior.

## Under the Hood
- Ensured bulletproof memory safety by clearing touch callbacks properly during the app lifecycle.
- Tuned touch deadzones to strictly mirror Moonlight's stylus behavior for improved consistency.
