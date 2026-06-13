# Moonlight Zenith

Moonlight Zenith is an enhanced fork of [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) focused on bringing a robust and bulletproof keyboard typing experience across multiple languages and layouts.

## What's New in Zenith?
- **Host PC Keyboard Layout Selector**: Choose between Auto, Turkish (QWERTY), German (QWERTZ), French (AZERTY), Spanish (QWERTY) and Italian (QWERTY) right from the Input settings.
- **Live Update**: Changed your keyboard layout in the settings? It applies instantly during gameplay without needing to restart the stream!
- **Bulletproof Physical Key Injection**: Bypasses the problematic UTF-8 text injection (which caused `u15f` or `Ctrl+Shift+U` bugs on Linux/Sunshine) and translates characters dynamically to their precise hardware-level Virtual-Key (`VK_`) codes on standard ISO/European layouts.
- **Accurate Modifier Handling**: Flawless handling of `VK_SHIFT` state for accented and dead keys (like `ı`, `İ`, `ç`, `ö`, `ß`, `é` etc.), meaning you no longer have to press Space after a special character to make it register!

## Credits
- Enhanced and modernized by [Google Antigravity](https://antigravity.google) in collaboration with [me](https://github.com/bcrtvkcs).
- Based on the brilliant open-source work of the original [Moonlight Android](#moonlight-android) team.
- Special thanks to the authors of the forks & commits we merged:
  - [athlan20](https://github.com/athlan20/moonlight-android-L3R3) for the L3/R3 button support.
  - [Attect](https://github.com/Attect/moonlight-android-Attect) for their foundational work and contributions to the Android branch.
- Deep thanks to the [Sunshine](https://github.com/LizardByte/Sunshine) project for enabling this ecosystem.

---

# Moonlight Android


[![AppVeyor Build Status](https://ci.appveyor.com/api/projects/status/232a8tadrrn8jv0k/branch/master?svg=true)](https://ci.appveyor.com/project/cgutman/moonlight-android/branch/master)
[![Translation Status](https://hosted.weblate.org/widgets/moonlight/-/moonlight-android/svg-badge.svg)](https://hosted.weblate.org/projects/moonlight/moonlight-android/)

[Moonlight for Android](https://moonlight-stream.org) is an open source client for NVIDIA GameStream and [Sunshine](https://github.com/LizardByte/Sunshine).

Moonlight for Android will allow you to stream your full collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Moonlight also has a [PC client](https://github.com/moonlight-stream/moonlight-qt) and [iOS/tvOS client](https://github.com/moonlight-stream/moonlight-ios).

You can follow development on our [Discord server](https://moonlight-stream.org/discord) and help translate Moonlight into your language on [Weblate](https://hosted.weblate.org/projects/moonlight/moonlight-android/).

## Downloads
* [Google Play Store](https://play.google.com/store/apps/details?id=com.limelight)
* [Amazon App Store](https://www.amazon.com/gp/product/B00JK4MFN2)
* [F-Droid](https://f-droid.org/packages/com.limelight)
* [APK](https://github.com/moonlight-stream/moonlight-android/releases)

## Building
* Install Android Studio and the Android NDK
* Run ‘git submodule update --init --recursive’ from within moonlight-android/
* In moonlight-android/, create a file called ‘local.properties’. Add an ‘ndk.dir=’ property to the local.properties file and set it equal to your NDK directory.
* Build the APK using Android Studio or gradle

## Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).
