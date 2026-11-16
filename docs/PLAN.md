# CW News: plan

What is built, what comes next, and how each step will be designed and checked. Updated whenever a step ships.

## Where things stand

| Version | What it added | State |
|---|---|---|
| 1.0, 1.1 | News as Morse, feed catalogue, receiver background, ham abbreviations | Released |
| 2.0 | Band tab: tuning dial, signals that sweep in and out, generated contacts, news as the bulletin station at 7020 kHz | Released as `v2.0` |
| 2.1 | Contacts built from topic modules by operators with personalities; reports, greetings, names and weather that agree with what you hear and when | Built by CI, not released |
| 2.2 | One receiver chain with three filter widths and AGC, 10 Hz tuning, individual fists and signal faults | Built by CI, not released |
| 2.3 | Front panel: waterfall, S-meter, tuning knob, RIT | Built by CI, not released |

Versions 2.1 to 2.3 are on branch `ccr-e3a1d112-4kb7si`, not on `main`. None of them has been run on a phone, and nobody has listened to the new audio.

## To finish 2.1 to 2.3

1. Install the 2.3 build on a phone and listen for ten minutes on the Band tab. Things to check:
   - **Sound.** With AGC on, the hiss drops behind a strong station and comes back between words. No crackle, dropouts or stuck tones.
   - **Filter.** Narrow leaves one station and less noise; Wide lets several through at different pitches. Switching makes a short gap, not a click.
   - **Fists.** Operators sound different from each other; the news bulletin stays machine-perfect.
   - **Tuning.** The knob feels right: one turn is 1 kHz, a flick coasts, and there is a light click in the fingers every 100 Hz.
   - **Waterfall and meter.** Lines break up with the keying, the meter moves with the station you hear, and the screen stays smooth.
   - **Battery and heat** after half an hour with the Band tab open, since the waterfall redraws 25 times a second.
2. Decide what to tune by ear: filter widths, how hard the AGC works, how strong the fists are.
3. Merge the branch to `main` and publish a release with the APK. Draft notes are in `docs/RELEASE-2.3.md`.

## Built in 2.2: one receiver chain, filter width, fine tuning, fists

What was built, where it differs from the first plan, and what is still open.

- **Receiver chain (`Rx`).** The bulletin, the stations and the band noise are mixed first and pass one band-pass filter and one AGC. The AGC looks 4 ms ahead, so it never overshoots or thumps.
- **Filter widths.** Wide is 1.6 kHz, Normal 400 Hz, Narrow 250 Hz. They are steep (a station 300 Hz above is about 21 dB down in Normal and about 50 dB down in Narrow) and the narrow one rings a little.
- **AGC On or Off** on the Band tab, added so the two can be compared on the phone. Off compresses nothing but keeps a ceiling, so static crashes cannot blast.
- **Noise.** The band noise follows the Ionosphere background and its level in Settings. That level is what you hear in the gaps; behind a strong signal it is lower. With Cosmic or no background the receiver adds no noise.
- **Fine tuning.** 10 Hz steps, with buttons for 10 and 100 Hz.
- **Fists (`Fist`).** Keyer, bug, straight key and sideswiper, each drawn per operator; beginners are less even and pause more.
- **Signal faults.** Chirp, hard keying, a rough note, drift, fading with several rhythms and flutter. A report can now carry the tone digit and `C` for chirp. Now and then someone tunes up with a plain carrier.
- **Not built:** static that follows the season.
- **Open:** all levels and widths were set from measurements, not by ear.

## Built in 2.3: the front panel

- **Waterfall.** Each station is a line that breaks up with its keying. Tap to tune. A Span button switches between the whole band (40 kHz) and a 4 kHz window that follows the receiver; the filter's passband is shaded.
- **S-meter.** Reads the level after the filter. S9 is a full-strength station and an S unit is 5 dB, the same scale the stations use in their reports.
- **Tuning knob.** One turn is 1 kHz in 10 Hz steps, geared up when spun fast, with inertia after a flick and a haptic click every 100 Hz.
- **RIT.** Moves the receiver up to 500 Hz either side of the dial. Until you can transmit it is only a second fine-tuning control; it becomes useful with step 4.
- **Removed:** the old slider and the bar display.

## Step 4 (version 2.4): take part in a contact with typed sending

**What you get:** you answer a CQ, or call CQ yourself, and a station works you in real time.

**Settings**
- Your callsign, name, QTH, rig and power. The bridge only stores numbers today (`set(String, int)`), so it needs a text setter.
- Decision for you: your real callsign or a made-up one.
- RIT, built in 2.3, then does its real job: listening beside your transmit frequency.

**Band tab**
- An **Answer** button appears when you are tuned to a station calling CQ. **Call CQ** appears on a clear frequency.
- A text line plus macro buttons: your call, report with name and QTH, rig and antenna and weather, `AGN?`, `QRS`, `73`.
- What you send is keyed as sidetone at your speed. The receiver mutes while you send, as on a real rig with semi break-in.

**Partner** (new plain-Java class `Partner`)
- A small state machine: waiting for a call, exchange, chat, goodbye.
- It reads what you typed for callsigns, a report, `NAME`, `QTH`, `RIG`, `ANT`, `WX`, questions (`NAME?`, `QTH?`, `HW?`), `QRS`, `AGN` and `73`.
- It answers with the same phrase modules the generated contacts use, so `Qso` is reworked to produce one side's overs on demand.
- Whether it hears you depends on how close you are to its frequency, your power and its noise. A partial copy gives `SP9?` or `AGN`; far off frequency gives no answer.
- It matches your speed, and slows down when asked.
- Text it does not understand gets `R FB` and its own next topic.

**Logbook, first version**
- Each completed contact is stored (call, name, QTH, reports both ways, time) and listed.

**How it is checked**
- `Partner` runs on a PC against scripted input: a full contact, a missing callsign, `QRS`, off frequency, you going silent (it calls again, then gives up).

**Risks**
- Audio delay: the output buffer is at least 150 ms. That is fine for typed sending and too slow for hand keying, which is why hand keying is a separate step.

## Step 5 (version 2.5): hand keying

**What you get:** you send with your own fist, on the screen or with a real key.

- Start with a test screen that measures tap-to-tone delay on your phone. Go ahead only if it is under about 40 ms.
- A second, low-latency audio output just for the sidetone.
- Touch straight key and iambic paddle as a native view, not in the WebView.
- A decoder with adaptive timing turns your keying into text for `Partner`. Sloppy spacing is copied as sent, so the partner may ask `?`.
- External keys: a paddle or straight key through a USB or Bluetooth keyboard or mouse adapter.
- After each over the app can show what it copied from you, as sending practice.

## Step 6 (version 2.6): a band that follows the clock

**What you get:** the band changes through the day, and where you tune matters.

- Setting: your own QTH, as a region.
- Propagation: nearby stations by day, DX at night and around sunrise and sunset. Distance and time set each operator's strength, using the per-operator strength that already exists.
- One-sided contacts: the other operator is in the skip zone and you hear only half.
- Band geography: fast DX operators near 7000 to 7010, QRP around 7030, slower operators higher up.
- Activity: busier evenings and weekends, contest weekends from a small calendar.

## Step 7 (version 2.7): reasons to come back

- Recurring stations: the same operator returns on later days with the same rig and QTH, and greets you by name once you have worked them.
- Copy practice: after listening to a contact, fill in call, name, QTH and report; the app scores it against what was sent.
- Awards from the logbook: countries, QRP contacts, summits and parks.
- Pile-up mode: you are the park activator, stations call you, and the score is rate and accuracy.

## Ideas, not scheduled

- Nets and round tables with three or more stations.
- A DX station working split with a pile-up.
- Real solar data and real weather for the operators' towns. Both need the network, so they must stay optional.

## Order, and why

1. Typed contacts before hand keying: the partner logic is the hard part, and typing avoids the audio-delay problem.
2. Hand keying only after a delay measurement on a real phone.
3. The clock-driven band and the reasons to come back build on the earlier steps and can be reordered.

## How every step is finished

1. `bash tests/run.sh` passes on a PC.
2. GitHub Actions builds `CWNews-apk` from the pushed commit.
3. Version code and name are raised.
4. You install it and listen on a phone. A step is not done before that.
5. This plan and the README are updated.

## Known gaps

- 2.1, 2.2 and 2.3 have not been tested on a phone, and the new audio has not been listened to by anyone.
- `tests/ui.py` needs the Python Playwright module, which is missing in the cloud session.
- SOTA region codes are plausible but unchecked against the real summit lists.
- POTA prefixes outside the US follow the ISO country scheme by inference; only the US change (`K-` to `US-`) is confirmed.
- There is no propagation model yet: a Danish station can chat with Argentina at noon.
