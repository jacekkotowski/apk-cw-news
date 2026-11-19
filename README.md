# CW News

Offline-friendly Android app that fetches RSS and Atom news and sends the headlines, optionally with summaries, as audible Morse code. A built-in catalogue of 36 feeds (ham radio, world news, science, psychology) is one tap away. Ham abbreviations (AND = ES, THANKS = TNX) can replace plain words. A synthesized receiver band or Cosmic background can play underneath. The Band tab is a made-up slice of the 40 m CW band with a receiver front panel: a waterfall, an S-meter, a tuning knob, three filter widths, AGC, RIT and a CW / CW-R sideband switch. Signals sweep in and out as you tune, and generated contacts between operators with different personalities (a chatty old-timer, a terse contester, a beginner who asks you to slow down, someone on a summit), with rigs, antennas, weather, hobbies, fun facts and sometimes a headline from your feeds. Your news is the bulletin station at 7020 kHz. No account, server, telemetry or ads. The app contacts only your feeds, and, if you switch on "Use real space weather" in Settings (off by default), `services.swpc.noaa.gov` for NOAA's public K index, once per refresh; nothing about you is sent.

Current version: 2.9.0. Android 8.0 (API 26) or newer.

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
- `Profile` and the Log tab: Settings, "Your station" holds your callsign, name, QTH, rig, antenna, power, sending speed, how patient partners are and "Send with" (typing or, later, your key). Text is upper-cased and limited to what Morse can send; a callsign must look like a callsign. The Log tab lists logged contacts, newest first, with a line "N contacts, M countries" (countries are counted from callsign prefixes, so it is approximate). Nothing writes to the log yet; contacts arrive with the Band tab's contact card in a later release. The database is now version 2 and gains the `log` table on upgrade without touching feeds or articles.
- Taking part (Band tab, Contact card): **Answer** takes over a station that is calling CQ in the filter; **Call CQ** plays your CQ on a clear frequency and a station answers on it. You type your overs, with buttons that fill in Call, Report, R, AGN?, QRS and 73 from your station settings; your text is played as a sidetone, and while you send, the receiver is ducked by 20 dB (back 0.4 s after the last element). Under the hint line the card suggests a ready text to send (`Partner.suggest`), built from your station settings and from what the partner just asked (call, report, name, QTH, weather, rig, and so on), and ends with 73 when the contact can finish; **Use** copies it into the typing line, and you can key it or edit it first. The partner (`Partner`) is forgiving: it reads what you sent with `Heard`, asks for one missing thing at a time, slows down when you ask or when it cannot read you, prompts kindly after silence and signs off without ever failing the contact. It answers at your sending speed or slower. "Show their text" is After each over (default), Never or As they send, and the hint line can be switched off in Settings. **Net** puts the dial on the partner and clears RIT; **End** closes the card. The contact is added to the Log, unfinished ones too. With "Send with: My key" (Settings > Your station) the phone can also listen to your key's buzzer: on the Band tab, **Set up my key** (hold the key, stay quiet, let the band play) finds the buzzer's pitch and warns if the band leaks into the microphone; **Learn my fist** learns how long your dashes and gaps are from three sends of a CQ; **Warm up** listens for a minute and scores what it read. In a contact the line "You sent: ..." fills in as letters are read (`KeyEar`, `KeyDecoder`, `Heard`), a light follows the buzzer, and the end of your over is found from a turn marker (K, KN, BK, SK) and 1.5 s of quiet, or a longer silence. "That's not what I sent" keeps the timings of the last over, never sound, with an Export button; they can be added to `tests/data/fist/`. Listening happens only while the Band tab is on screen with a contact or set-up open.
- Whole contact by touch (2.9.0): under the transcript the Contact card has chips to build an over without typing: **RST**, **Name**, **QTH**, **Weather** (a menu), **Rig**, **Ant** add their part (from your station settings) to the line, and **Call**, **R**, **AGN?**, **QRS**, **73** say the usual things. **Send** adds the call signs around the line; below it, the suggested text can be sent with one tap (**Send this**) or edited. The key box comes below, so you can also key the suggestion.
- Fixed receiver and band (2.8.0): the filter is always the normal one (400 Hz, never narrow), CW side, slow AGC, no shift, peak filter or blanker; the band is always busy and its weather is chosen in Settings, Summer static by default (static crackle, no NOAA fetch); RIT and the ±10/±100 Hz buttons are gone, so you tune with the knob, a tap on the waterfall (tap the frequency scale to zoom) and the Signal buttons, and **Net** puts the dial on a contact. "Band / News only" and "When a contact ends: Stay / Go to the strongest" moved to Settings. **Set up my key** is one tap: it records the held key (3 s), the quiet room (2 s) and, if the band is playing, the band (3 s) by itself, so it is quick to repeat when the noise changes (for example when a generator is started).
- A tidier Band tab (2.7.2): the receiver controls (filter, sideband, AGC, shift, peak filter, noise blanker, follow, waterfall span, band activity) are folded into "Receiver and band settings"; fine tuning (±10 and ±100 Hz) and RIT into "Fine tuning and RIT"; the phrase buttons and the typing line into "Type instead" (open by default only when you send by typing); the key's set-up buttons, corrections and help into "Set up and tools". While a contact is running the Answer and Call CQ choices are hidden, Net appears only when the station is off your frequency, and "Show their text" moved to Settings.
- `Abbrev`: one table of CW abbreviations, used for the replacement (Off, Light, Full) and for the list in Settings.
- Following and sleeping: with "When it goes quiet: Strongest", `Band.follow` names the strongest station that can be heard once nothing has been keyed in the filter for 2.5 s (75 s at most) and no contact is audible there, and `CwService` retunes to it, never within 20 s of you turning the knob or while the bulletin is playing in the filter. "Stop playing after" in Settings fades the sound over the last 20 s and stops playback. Both run in the service, so they work with the screen off.
- CW and CW-R: "Sideband" decides which side of a signal the receiver listens on. In CW-R a station above the dial sounds below the pitch, so the pitch moves the other way as you tune and the passband shaded on the waterfall lies on the other side of the needle. The waterfall itself does not flip.
- Receiver controls: "Shift" moves the passband up or down by up to 300 Hz without changing the pitch; the peak filter lifts a narrow band at the pitch by 6 dB; the noise blanker replaces a sample that stands out from both neighbours (a static tick) by their average; AGC is Off, Slow (0.5 s release) or Fast (0.12 s). A change of any of these uses the same fade, swap and fade-in as the filter width, so it does not click.
- `Sky`: the weather on the band. Auto follows the season, the time of day and chance: summer afternoons and evenings bring thunderstorm static, a storm can come close for 5 to 25 minutes (loud crashes that swell the noise, which the blanker cannot take out), and rarely an aurora makes signals on northern paths flutter and weaken, or a solar flare fades out every path through daylight. Settings has Calm, Summer static, Storm nearby, Aurora and Solar flare to hold one state. The Band tab says what is going on, and operators sometimes mention it.
- `SpaceWx`: reads the latest planetary K index from NOAA's JSON text. `Refresher` fetches it after the feeds, over HTTPS, only when real space weather is on, and stores it; a value over six hours old is ignored. With a K of 5 or more, Auto weather uses an aurora of level (K − 4) / 5 instead of chance. A failed fetch changes nothing.
- Front panel (`app/assets/index.html`): the waterfall is drawn from each station's frequency, strength and keying, read about 25 times a second; the S-meter reads the receiver's level after the filter, with S9 as a full-strength station and 5 dB per S unit, the same scale the stations use in their reports. The knob is 1 kHz per turn in 10 Hz steps, geared up when spun fast.
- `app/assets/catalogue.js`: the built-in feeds.
- `TextNorm`, `FeedParser`, `Refresher`, `Db`: feeds, cleaning and the offline cache.
- `CwService`: foreground media service, notification, headset controls and audio focus.
- `RefreshJob`: periodic refresh when idle. Android decides the exact times.

## Permissions

| Permission | Why |
|---|---|
| INTERNET, ACCESS_NETWORK_STATE | Fetch feeds, tell offline from a failed fetch, and (only if you switch it on) read NOAA's K index |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK | Keep playing with the screen off |
| RECORD_AUDIO | Only if you choose "Send with: My key": to hear your key's buzzer. The sound is analysed on the phone while the Band tab is on screen, and is never stored or sent. |
| POST_NOTIFICATIONS | Show the playback controls |
| RECEIVE_BOOT_COMPLETED | Keep the periodic refresh after a restart |

## Troubleshooting

- No notification controls: allow notifications for CW News in Android settings.
- Stops after a while with the screen off: set the app's battery use to Unrestricted.
- A feed shows an error: only https:// feeds are accepted, and the reason is shown under the feed.
- Summaries are short: feeds carry what the publisher provides, and the app doesn't scrape articles.

## Sources

See `THIRD_PARTY.md`. MIT licensed, see `LICENSE`.
