# CW News

Hand-built Android APK, no Gradle: aapt2, javac, d8, zipalign, apksigner. WebView UI in `app/assets/index.html` with a small Java bridge. Package `pl.cwnews`. Interface text is English.

- `Morse`, `Timing`, `Keying`, `Dsp`, `Bed`, `Engine`, `TextNorm`, `FeedParser` and `PlaybackQueue` must stay free of Android imports so `tests/run.sh` can run them on a PC.
- minSdk is 26: avoid newer Java APIs such as `List.of`, `Map.of`, `String.isBlank`, `strip` and `repeat`.
- Never commit `*.keystore` or `signing.env`.
- Don't hand over a build unless it was built and tested from the current source. State plainly what was not tested on a phone.
- Raise `versionCode` and the version name with each delivered build.
