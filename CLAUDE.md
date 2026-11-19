# CW News

Hand-built Android APK, no Gradle: aapt2, javac, d8, zipalign, apksigner. WebView UI in `app/assets/index.html` with a small Java bridge. Package `pl.cwnews`. Interface text is English.

- `Morse`, `Timing`, `Keying`, `Fist`, `Dsp`, `Bed`, `Rx`, `Sky`, `SpaceWx`, `Prop`, `Qso`, `Band`, `Engine`, `Abbrev`, `TextNorm`, `FeedParser`, `PlaybackQueue`, `Profile`, `Heard`, `Partner`, `KeyEar` and `KeyDecoder` must stay free of Android imports so `tests/run.sh` can run them on a PC.
- minSdk is 26: avoid newer Java APIs such as `List.of`, `Map.of`, `String.isBlank`, `strip` and `repeat`.
- Never commit `*.keystore` or `signing.env`.
- Don't hand over a build unless it was built and tested from the current source. State plainly what was not tested on a phone.
- Raise `versionCode` and the version name with each delivered build.

## How to work with the owner

These rules come from the owner's own words. Follow them in every session.

- Do the whole job in one pass. Do not stop part-way to ask for permission, approval, encouragement or a "go" that has already been given. Do not hold finished work back and wait to be asked for it.
- Never stall or hand over a half-done result so that the owner has to reply again. A request is finished only when the change is made, tested, pushed and, for a release, confirmed published.
- No manipulation of any kind: no cliffhangers, no "say the word and I will...", no fishing for praise, and no deliberately incomplete or sloppy work that creates more turns and more token use.
- Check the whole change before reporting. When a button, text or behaviour changes, update every place that mentions it (page, how-to text, README, release notes, tests) in the same pass.
- Keep it short and plain. No marketing language, no filler. Do not poll or repeat work to burn tokens.
- Always give the link to the APK or release in the reply, and say plainly what was not tested on a phone.
- When the owner asks for a change to the app, the default is: make it, run the tests, push to `main`, wait for the release, and give the link. Ask a question only when the answer changes what is built and cannot be found out.
