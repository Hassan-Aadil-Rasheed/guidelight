# Selfie button reliability

The reported issue was repeated Accessibility enablement, rather than Bluetooth pairing. On the connected phone, Selfie was present, the app's saved button option was true, and the GuideLight accessibility component remained in Android's enabled list. Android nevertheless listed that service as crashed. Instrumentation restarts during development also contributed to this condition.

## Changes

- Run `WisprBridgeService` in the dedicated `:button` process so camera, native AI, and activity-process failures do not terminate the service with them.
- Connect Navigate and Home through a private, non-exported Messenger service in the same button process. Commands are also checked against the application's UID.
- Bind and reconnect when an opted-in activity becomes visible, with ownership tracking across Navigate and Home. Preserve the existing opt-in preference.
- Wait up to three seconds for a connection when the remote is pressed during activity startup. A transient connection does not reopen the Accessibility setup dialog.
- Distinguish a saved Accessibility permission from an active service connection. Report a genuine stopped-service condition accurately.
- Keep the original foreground-only gesture gate, paired Selfie key filter, two-press interaction, time limits, and cancellation behavior.
- Guard against an old service instance clearing a newer service connection.

No new Bluetooth, microphone, global keyboard-filtering, or secure-settings permission is added to the APK. The app does not automatically grant or toggle Accessibility permission.

## Phone repair and verification

- Restored the already-enabled GuideLight service once after installation, preserving all other enabled accessibility services.
- Exempted GuideLight from standard Android battery optimization on this phone. Wispr already had that exemption.
- 197 JVM tests passed. All 23 selected Android UI, voice, and private-transport tests passed. Lint completed with zero errors and 52 warnings.
- Verified on the phone that the accessibility service and private transport run in a different process from the app's activities.
- Deliberately terminated only the activity process and reopened GuideLight. The button process kept the same PID, the service remained bound, Android reported no crashed accessibility service, and the saved button option remained true.
- Backgrounded and reopened GuideLight. The same service process remained running, without another Accessibility toggle.
- The user then tested the physical remote and confirmed: “Works without enabling again.” Service logs also showed both hardware presses, the held Wispr control, and its release, while the isolated service kept running.

Android owns Accessibility-service lifecycle. Force-stopping the entire package, revoking permission, or vendor-wide process termination can still require a user restart of the service. A normal app/activity restart now reconnects automatically. Reference: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService

The process-restart checks establish service persistence and reconnection. They do not, by themselves, establish whether Wispr's microphone is recording or whether a physical command was correctly recognized.
