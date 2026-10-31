# GuideLight voice commands — 0.22.1

Press Selfie once, wait for the vibration, speak, then press it again to finish. Alternatively, use the Speak action and the Wispr Flow bubble. Say **“What can I say?”** for spoken help on the current screen.

**Say “Home navigation” to open your rooms.** You can also say “Open Home”, “Open home navigation”, or “Take me to home navigation”. Say “Back to navigation” to return.

## Keyboard behavior

Wispr's Android bubble requires a focused text field and a visible keyboard during dictation. This update keeps Wispr, as requested. GuideLight hides the keyboard when the remote releases dictation, when a completed phrase arrives, and when a command is submitted, cancelled, or its dialog closes. The field keeps its focus long enough for Wispr to insert the words. Home takes dictation directly in its existing Command box, without an extra popup. It clears focus after consuming the command so the keyboard stays closed. Manual typing remains available by tapping the command box.

Source: [Wispr hands-free requirements](https://docs.wisprflow.ai/articles/6391241694-use-flow-hands-free).

## Navigate and shared app controls

| Control | Say |
| --- | --- |
| Home navigation | “Home navigation” or “Open Home” |
| Navigate tab | “Back to navigation” or “Open navigation” |
| Camera tab | “Open camera” or “Camera view” |
| Help | “What can I say?” or “Read commands” |
| Previous screen | “Go back” |
| Close an app help/settings dialog | “Close help”, “Close settings”, “Done” |
| Clear destination | “Clear destination” |
| Change destination/results | “Change destination” |
| Find the previously entered destination | “Find place” or “Search destination” |
| Stop an active place search | “Cancel search” or “Stop searching” |
| Location information | “Location status” or “Where am I?” |
| Camera information | “Camera status” |
| Current screen/status message | “Read status” |
| Last object alert | “Repeat alert” |
| Selfie setup | “Selfie button setup” |
| Saved button preference | “Enable selfie button” or “Disable selfie button” |
| Android button-control permission page | “Accessibility settings” |
| Android app/camera permissions | “App settings” or “Camera settings” |
| Android Location page | “Location settings” |
| Online assistant setup | “Cloudflare settings” |
| Google Privacy Policy | “Open privacy policy” |
| Cancel the current command | “Cancel command” or “Stop listening” |

App actions are resolved before place search. “Home navigation” is not sent as a place query. A named place or address is still searched normally. Starting Home or changing destination during an active outdoor walk asks you to end that walk first.

## Place suggestions

Say “First one”, “Second one”, “Choose suggestion three”, another current suggestion number, or a unique place name. Say “Repeat suggestions” to hear the list again, or “Change destination” to start over.

## Outdoor walking and camera

Say “Repeat directions”, “Scan surroundings”, “Repeat alert”, “Show map”, “Recenter”, “Full route”, “Camera view”, “Location status”, “Camera status”, “Open controls”, “Close controls”, “Help”, or “End walk”. In the end-walk confirmation, say “Yes” or “Keep walking”. “Go back” cancels an open end-walk confirmation.

Walking-map actions are reported as unavailable before a walk starts; they are not searched as place names.

## Home: saved-room recognition and camera assistant

| Control | Say |
| --- | --- |
| Create a map | “Create home map” or “Start mapping” |
| Name the current room | “This is my kitchen”, or another room name |
| Save the recording | “Save map” or “Finish mapping” |
| Find position | “Find my position” or “Where am I?” |
| Hear saved rooms | “List my rooms”, “Hear my rooms”, “Read aloud” |
| Describe camera view | “Describe view” or “What do you see?” |
| Compare a saved room | “Identify room” |
| Explain uncertainty/status | “Explain my status” |
| More detailed description | “Detailed analysis” or “Describe in detail” |
| Cancel AI work | “Cancel analysis” |
| Prepare local vision | “Set up local AI” |
| Assistant settings | “More options” or “Fewer options” |
| Read the last answer/status | “Read answer”, “Repeat description”, or “Read status” |
| Stop the recognition session | “End test” or “Stop home camera” |
| Older marker-based setup | “Previous room recordings” after ending the current test |
| Return to Navigate | “Back to navigation” |

The saved-home screen remains a recognition trial; this update does not add indoor walking directions to it.

## Previous room recordings

Before the camera starts, the Selfie button can now receive “Scan home marker”. A Speak action is also available before scanning. Then say “Start recording”, “This is my kitchen”, “Finish recording”, “List my rooms”, “Go to kitchen”, “Choose room one”, “Repeat directions”, “End walk”, or “Stop camera”. Say “Home navigation” to return to the saved-home assistant, or “Back to navigation” for outdoor navigation.

## Cloudflare settings

Say “Open setup instructions”, “Open license”, “Open use policy”, “I accept the license and use policy”, “Connect and test”, “Cancel connection”, “Disconnect Cloudflare”, “Read status”, or “Go back”. Agreement is changed only by an explicit acceptance/decline command or its checkbox. Account ID and API token entry remain in their dedicated fields, and credentials are not read aloud.

Android's own permission approval and external browser pages remain controlled by Android. These commands open the relevant pages; they do not bypass Android consent or control other apps.
