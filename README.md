# ScreenTextCopier 📱✂️

**ScreenTextCopier** is a lightweight native Android tool designed primarily for **developers**. It is particularly useful for **vibe coding** and AI-assisted development: tap a floating button once to copy text exposed by the currently visible app into the system clipboard. Quickly paste **shell commands, terminal output, logs, error messages, and code snippets** from apps such as Termux into an editor or AI chat.

**Text fidelity is the priority, especially for shell commands and logs.** ScreenTextCopier reads text directly from Android's Accessibility view hierarchy rather than recognizing characters in screenshots. This avoids OCR-specific character recognition mistakes, such as confusing `0` with `O` or `1` with `l`. However, the result depends on what the source app exposes through Accessibility; text embedded in images and off-screen terminal scrollback are not automatically available.

**Zero Network by design:** the app does not request the `android.permission.INTERNET` permission, contains no network or analytics SDKs, and does not use OCR.

---

## 🚀 Features

1. **One-Tap Screen Text Copying (On-Demand Extraction)**
   - Tap the small, semi-transparent floating button to inspect the currently visible Accessibility view hierarchy (`AccessibilityNodeInfo`).
   - Read available text (`node.text`) and accessibility labels (`node.contentDescription`).
   - Remove identical duplicate strings while preserving extraction order, then join the results with newline characters in the system clipboard (`ClipboardManager`). Some apps may expose partially overlapping text, so duplicate phrases are still possible.
   - Show a brief toast with the number of copied lines.

2. **Privacy and Security (Zero Network)**
   - The Android manifest deliberately omits `android.permission.INTERNET`.
   - No network, analytics, or telemetry libraries; only Android SDK and AndroidX components.
   - Passive Accessibility event subscription is disabled (`eventTypes = 0`, empty `onAccessibilityEvent`). The service reads the active screen only after the user taps the floating button.
   - On Android 13+ (API 33+), clipboard content is marked using `ClipDescription.EXTRA_IS_SENSITIVE` so compatible system UIs hide its preview. This does not prevent other authorized apps from accessing the clipboard.
   - The in-app clipboard preview is masked by default and requires a deliberate tap to reveal its content.

3. **Floating Button (`TYPE_ACCESSIBILITY_OVERLAY`)**
   - Uses `TYPE_ACCESSIBILITY_OVERLAY` and `FLAG_NOT_FOCUSABLE` without requesting `SYSTEM_ALERT_WINDOW`.
   - Supported gestures:
     - **Tap:** copy accessible text from the active screen.
     - **Drag:** move the button within the visible screen bounds.
     - **Snap to edge:** dock the button to the nearest left or right edge after dragging.
     - **Long press:** open `MainActivity` to manage settings or hide the button.

4. **Simple Controls (`MainActivity`)**
   - Shortcut to Android Accessibility settings (`Settings.ACTION_ACCESSIBILITY_SETTINGS`).
   - A `SharedPreferences`-backed switch to hide the floating button without disabling the Accessibility service.
   - Guidance for Android 13+ Restricted Settings, when applicable.
   - Privacy-conscious clipboard inspection with a masked preview.

---

## 🛡️ Android 13+ Restricted Settings

Android 13 and later may restrict the activation of Accessibility services in apps installed outside an app store. Google Play Protect may also block sideloaded APKs that request sensitive permissions. These are Android security protections; standard installation is **not guaranteed** on every device.

If your device offers the **Allow restricted settings** option for an installed app, the process may be:

1. Open **Settings → Apps → ScreenTextCopier**, or use **App info** in ScreenTextCopier.
2. Open the three-dot menu (**⋮**) in the app information screen.
3. Select **Allow restricted settings**, if available, and confirm your identity.
4. Open **Accessibility** settings and enable ScreenTextCopier.

Never disable device protection merely to install an untrusted APK. The project is tracking Google Play distribution in [Issue #2](https://github.com/krzysztofcal/screen-text-copier/issues/2).

---

## 🧪 Smoke Test Plan (Physical Android Device)

Verify the following behavior on a real Android device:

| Test | Tester action | Expected result |
| :--- | :--- | :--- |
| **ST-01** | Install `ScreenTextCopier-debug.apk` or a signed release APK on a device that permits installation. | Installation succeeds and ScreenTextCopier appears in the launcher. |
| **ST-02** | Launch `MainActivity` for the first time. | The service is shown as inactive. On Android 13+, guidance for Restricted Settings is shown when the service is inactive. |
| **ST-03** | Open Accessibility settings and enable ScreenTextCopier. | The floating button appears and the app shows the service as active. |
| **ST-04** | Drag the floating button across the screen. | The button stays within the visible bounds and snaps to the nearest edge when released. |
| **ST-05** | Tap the button while a browser or another supported app displays text. | A toast reports the number of lines copied. |
| **ST-06** | Paste the clipboard into a notes app. | The available text is pasted as newline-separated lines. Identical extracted strings are deduplicated. |
| **ST-07** | Tap **Inspect clipboard**, **Show content**, then **Clear preview** in `MainActivity`. | The app displays masked line/character counts, reveals content only when requested, then clears the preview. |
| **ST-08** | Long-press the floating button for approximately half a second. | `MainActivity` opens. |
| **ST-09** | Toggle **Floating button on screen** off and on. | The button disappears and returns without disabling the Accessibility service. |
| **ST-10** | Open Termux, tap the floating button, and paste the result. | Visible commands and terminal output are copied in their accessible text form, without OCR substitutions. |

---

## 🛠️ Signing and CI/CD

- **CI builds:** [`.github/workflows/build.yml`](.github/workflows/build.yml) runs unit tests (`testDebugUnitTest`) and builds APKs. The non-release build runs without release signing secrets.
- **Secure release signing:** private signing keys are not stored in the current source tree. The release configuration in `app/build.gradle.kts` obtains its credentials from environment variables or Gradle properties:
  - `RELEASE_KEYSTORE_PATH`
  - `RELEASE_KEYSTORE_PASSWORD`
  - `RELEASE_KEY_ALIAS`
  - `RELEASE_KEY_PASSWORD`
  Without the required release signing configuration, a locally built release APK remains unsigned.
- **GitHub Releases:** the `publish-release` job runs for version tags (for example, `v1.0.1`), rather than ordinary pushes to `main`. The pipeline verifies the signed release APK against the pinned signer certificate fingerprint before publication.

Download published packages and checksums from [GitHub Releases](https://github.com/krzysztofcal/screen-text-copier/releases).

The source code is publicly available. Adding an explicit open-source license is tracked in [Issue #2](https://github.com/krzysztofcal/screen-text-copier/issues/2).
