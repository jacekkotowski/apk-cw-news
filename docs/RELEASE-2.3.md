# CW News 2.3

Draft notes for a GitHub release `v2.3`, covering everything since `v2.0`. Attach `CWNews.apk` from the `CWNews-apk` artifact of the latest successful GitHub Actions run on the commit being released.

## What is new since 2.0

**A receiver, not tones over noise (2.2)**
- Everything on the band passes one filter and one AGC. A strong station pushes the hiss down, and it comes back between words.
- Filter: Wide, Normal or Narrow. AGC: On or Off.
- The dial tunes in 10 Hz steps.
- Operators key with their own fist: keyer, bug, straight key or sideswiper.
- Some signals chirp, key hard, have a rough note, drift or flutter. Now and then someone tunes up.

**A front panel (2.3)**
- Waterfall of the band; tap it to tune. Span button for the whole band or a 4 kHz window.
- S-meter.
- Tuning knob: one turn is 1 kHz, a flick coasts, with a light click every 100 Hz.
- RIT, up to 500 Hz either side.

**Contacts that differ (2.1)**
- Contacts are assembled from topic modules by operators with personalities: casual, chatty, terse, beginner, and someone on a summit or in a park.
- Skeds between friends; park, summit and contest stations working several callers.
- Repeat requests, `PSE QRS` that is honoured, interruptions, sending errors corrected with the error sign, tail-enders.
- Reports follow the signal you hear, greetings follow the other station's local time, names fit the country, weather fits season and time of day.

## Install

Android 8.0 or newer. Download `CWNews.apk` on the phone, open it and allow installs from that source. It installs over 2.0 if it is signed with the same key.

## State

- Built by GitHub Actions; the PC tests pass.
- Version code 6, version name 2.3.
- Not yet tested on a phone. The new audio was set from measurements and has not been listened to.
