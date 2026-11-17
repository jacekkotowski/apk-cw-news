# CW News

Offline-friendly Android app that fetches RSS and Atom news and sends the headlines, optionally with summaries, as audible Morse code. A built-in catalogue of 36 feeds (ham radio, world news, science, psychology) is one tap away. Ham abbreviations (AND = ES, THANKS = TNX) can replace plain words. A synthesized receiver band or Cosmic background can play underneath. The Band tab is a made-up slice of the 40 m CW band with a receiver front panel: a waterfall, an S-meter, a tuning knob, three filter widths, AGC, RIT and a CW / CW-R sideband switch. Signals sweep in and out as you tune, and generated contacts between operators with different personalities (a chatty old-timer, a terse contester, a beginner who asks you to slow down, someone on a summit), with rigs, antennas, weather, hobbies, fun facts and sometimes a headline from your feeds. Your news is the bulletin station at 7020 kHz. No account, server, telemetry or ads.

Current version: 2.5. Android 8.0 (API 26) or newer.

## Install

Copy `CWNews.apk` to the phone, open it and allow installs from that source.

Try it: Feeds > Built-in catalogue > open a group > Add. Settings > 20 WPM, effective 12 WPM, 600 Hz. Listen > Play.

## Build

Needs: Android SDK with build-tools 36.0.0 and platforms;android-36, JDK 17 or newer, zip. Tested on Linux only. There is no Gradle.

1. Set `ANDROID_SDK_ROOT`.
2. Put a keystore and `signing.env` in the project folder. `signing.env` holds `KS_FILE=...` and `KS_PASS=...`. Neither is in git.
3. Run `bash build.sh`.

A different signing key means a new version can't install over the old one.

GitHub Actions (`.github/workflows/android.yml`) runs the tests and uploads an APK. Without the `KEYSTORE_B64` and `KS_PASS` secrets it signs with a throwaway key.

## Tests

- `bash tests/run.sh`: Morse, timing, text cleaning, RSS/Atom parsing, queue, generated contacts, fists, propagation, the receiver filter and AGC, and the audio mix, on a PC.
- `python3 tests/ui.py`: the screen in Chromium with a fake Android bridge (Playwright).

## How it works

- `Morse`, `Timing`, `Keying`: text to sample-exact on/off runs. Farnsworth stretches only the letter and word gaps.
- `Engine`: CW plus background. Morse position depends only on the CW sample count. With the band on, the bulletin, the stations and the band noise are mixed first and go through one receiver.
- `Rx`: that receiver. One band-pass filter (Wide, Normal, Narrow) and one AGC with a short look-ahead, plus band noise and static. A strong signal turns the gain down, so the noise drops behind it and comes back in the gaps.
- `Fist`: how one operator's hand bends the timing. A keyer is exact, a bug makes machine dits and long dahs, a straight key varies everywhere, a sideswiper is short and rushed.
- `Bed`: the two backgrounds, generated in code with no audio files. Band is a receiver: noise through a narrow filter at the CW pitch, static ticks and an AGC that ducks after each one.
- `Qso`: makes up one contact from a seeded random source: a ragchew, a sked between friends, a park, summit or contest station working several callers, or a lone CQ. Each operator has a personality that sets speed, length and topics, and a contact is assembled from topic modules, so no two run the same way. Reports follow how well the two stations hear each other, greetings follow the other station's local time, weather follows season and time of day. To read some: `javac -cp out_test -d out_test tests/QsoDemo.java && java -cp out_test pl.cwnews.QsoDemo 20` after `tests/run.sh`.
- `Band`: the stations on the dial. Each sends in real time on its own frequency, with its own fist and fading, and sometimes a chirp, hard keying, a rough note or a drifting rig. Pitch comes from the distance to the dial; the receiver filter decides what is heard. The news bulletin is one station and waits, from the current word, while you are tuned outside the filter.
- `Prop`: a simple picture of 40 m propagation and of when people are on the air. Path loss comes from distance and from how much of the path is in daylight; a small calendar knows the big CW contest weekends. With "Your region" set, `Qso` picks stations the listener (or the other operator) can hear, and `Band` puts kinds of stations where they gather and is busier in the evening and at weekends.
- `Abbrev`: one table of CW abbreviations, used for the replacement (Off, Light, Full) and for the list in Settings.
- Following and sleeping: with "When it goes quiet: Strongest", `Band.follow` names the strongest station that can be heard once nothing has been keyed in the filter for 6 s (75 s at most) and no contact is audible there, and `CwService` retunes to it, never within 20 s of you turning the knob or while the bulletin is playing in the filter. "Stop playing after" in Settings fades the sound over the last 20 s and stops playback. Both run in the service, so they work with the screen off.
- CW and CW-R: "Sideband" decides which side of a signal the receiver listens on. In CW-R a station above the dial sounds below the pitch, so the pitch moves the other way as you tune and the passband shaded on the waterfall lies on the other side of the needle. The waterfall itself does not flip.
- Front panel (`app/assets/index.html`): the waterfall is drawn from each station's frequency, strength and keying, read about 25 times a second; the S-meter reads the receiver's level after the filter, with S9 as a full-strength station and 5 dB per S unit, the same scale the stations use in their reports. The knob is 1 kHz per turn in 10 Hz steps, geared up when spun fast.
- `app/assets/catalogue.js`: the built-in feeds.
- `TextNorm`, `FeedParser`, `Refresher`, `Db`: feeds, cleaning and the offline cache.
- `CwService`: foreground media service, notification, headset controls and audio focus.
- `RefreshJob`: periodic refresh when idle. Android decides the exact times.

## Permissions

| Permission | Why |
|---|---|
| INTERNET, ACCESS_NETWORK_STATE | Fetch feeds and tell offline from a failed fetch |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK | Keep playing with the screen off |
| POST_NOTIFICATIONS | Show the playback controls |
| RECEIVE_BOOT_COMPLETED | Keep the periodic refresh after a restart |

## Troubleshooting

- No notification controls: allow notifications for CW News in Android settings.
- Stops after a while with the screen off: set the app's battery use to Unrestricted.
- A feed shows an error: only https:// feeds are accepted, and the reason is shown under the feed.
- Summaries are short: feeds carry what the publisher provides, and the app doesn't scrape articles.

## Sources

See `THIRD_PARTY.md`. MIT licensed, see `LICENSE`.
