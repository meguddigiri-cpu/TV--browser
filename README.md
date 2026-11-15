# NOVA Browser for Android TV

A lightweight, remote-first WebView browser designed for Android TV / Google TV.

## Current features
- Dark NOVA homepage with Google Search, YouTube and Wikipedia shortcuts
- Address/search bar with URL detection and Google search fallback
- TV remote / D-pad focus navigation and Back key handling
- Multiple tabs: create, switch and close
- Bookmarks stored locally on the device
- Recent browsing history (up to 300 entries); private tabs are excluded from app history
- Find text on the current page and clear match highlights
- Share the current page using Android's share chooser
- Downloads through Android DownloadManager, with an in-app status list
- Voice search through the Android speech-recognition provider when available
- Desktop-site mode and adjustable page text zoom
- Clear WebView cache, cookies, form data and app history from Settings

## Important limitations
- Built on Android System WebView; website compatibility and video playback depend on the WebView version and the TV device.
- Private tabs avoid app-level history persistence, disable DOM storage and form-data saving, and bypass the normal WebView cache. They do **not** provide a separate cookie store or network-level anonymity. Cookies are shared until cleared in Settings.
- Browser extensions, full desktop-browser developer tools, guaranteed DRM/HD streaming, and full browser-profile isolation are not provided.
- Voice search requires a compatible speech-recognition service installed on the TV.
- Download destination and file-opening support depend on Android TV's file manager and system DownloadManager.

## Build
The GitHub Actions workflow builds a debug APK with JDK 17 and Gradle 8.9 on every push to `main`. The generated APK is uploaded as the `TV-Browser-APK` workflow artifact.

## Install
Download the APK artifact from the latest successful GitHub Actions run, transfer it to the TV, allow installation from the file manager when prompted, and install it.
