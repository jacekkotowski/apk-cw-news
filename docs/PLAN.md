# CW News: plan

What is built, what comes next, and how each step will be designed and checked. Updated whenever a step ships.

## Where things stand

| Version | What it added | State |
|---|---|---|
| 1.0, 1.1 | News as Morse, feed catalogue, receiver background, ham abbreviations | Released |
| 2.0 | Band tab: tuning dial, signals that sweep in and out, generated contacts, news as the bulletin station at 7020 kHz | Released as `v2.0` |
| 2.1 | Contacts built from topic modules by operators with personalities; reports, greetings, names and weather that agree with what you hear and when | Built by CI on branch `ccr-e3a1d112-4kb7si`. Not on `main`, not released, not tested on a phone |

## To finish 2.1

1. Install the CI build on a phone and listen for ten minutes on the Band tab. Things to check:
   - Stations differ in speed, and a beginner is clearly slower.
   - After `PSE QRS` the other station slows down.
   - A weak station gets a weak report and a strong one a strong report.
   - The greeting (GM, GA, GE) fits the time of day.
   - No crackle, dropouts or stuck tones when several stations are in the filter.
2. Merge the branch to `main`.
3. Publish release `v2.1` with the APK. Draft notes are in `docs/RELEASE-2.1.md`.

## Step 3 (version 2.2): one receiver chain, filter width, fine tuning and individual fists

**What you get:** the band sounds like a receiver instead of tones laid over noise, you can widen or narrow the filter and tune finely, and you can tell operators apart by the way they key.

**Today:** `Engine.render` adds three things side by side: the news tone, the stations from `Band.render`, and the noise from `Bed`. The AGC inside `Bed.Receiver` only sees the noise. Stations are scaled by a fixed 320 Hz gain curve (`Band.response`), not passed through a filter, and the noise has its own fixed 450 Hz filter. The dial steps 50 Hz. Every station keys with machine-exact timing; only the speed differs.

**Receiver chain**
- Add a receiver stage in `Engine` (plain Java): noise plus all station tones go through one band-pass at the CW pitch and one AGC, then to the output.
- The AGC has a fast attack and a release of about 0.3 s, so a strong station pushes the noise down and the noise rushes back in the gaps.
- `Band.response` stays, but only to skip stations that are far outside the filter, so it has to follow the chosen width.
- The Cosmic background and the News-only mode stay outside the chain and sound as they do now.
- Morse timing must still depend only on sample counts. The existing "timing unchanged" tests guard this.

**Filter width**
- One setting on the Band tab with three positions. The widths are starting values to be tuned by ear:

| Position | About | What you hear |
|---|---|---|
| Wide | 1 to 2 kHz | Several stations at once at different pitches, more hiss, easy to find signals |
| Normal | 500 Hz | Close to version 2.1 |
| Narrow | 250 Hz | One station, much less noise, slight ringing, tuning must be exact |

- The filter is the one in the shared chain, so it shapes noise and stations alike.
- With the wide filter a strong neighbour pumps the AGC and pushes down the station being copied; narrowing the filter removes it.
- Switching width must not click: the filter changes over a short crossfade.
- The news bulletin at 7020 kHz is heard through the same filter when the band is on.

**Fine tuning**
- The dial moves in 10 Hz steps instead of 50 Hz. The buttons become plus and minus 10 Hz and 100 Hz.
- Dragging the slider stays coarse; a narrow filter needs the buttons or the scope tap to land on a station.
- "Next signal" still jumps exactly onto a station.

**Fists**
- New plain-Java class `Fist`, applied when `Keying` turns text into on/off runs.
- Each operator already has a key type in `Qso` (straight key, bug, paddle, sideswiper). The fist follows it:

| Key | Dits | Dahs | Spacing |
|---|---|---|---|
| Paddle and keyer | Exact | Exact | Slightly uneven between letters |
| Bug | Exact, machine-made | Long and varying | Uneven |
| Straight key | Varying | Varying | Varying, with hesitations before unusual words |
| Sideswiper | Varying | Varying, short | Rushed |

- The news bulletin stays machine-perfect.

**Signal character** (per operator, in `Band`)
- A slight chirp on some signals, key clicks on a few, slow drift.
- A rough note for homebrew rigs.
- Fading with several rhythms (seconds to tens of seconds) instead of the current slow random steps; flutter on long paths.
- Now and then a station tuning up with a carrier of a few seconds.

**How it is checked**
- Timing tests unchanged; level and peak bounds unchanged.
- New test: with a strong signal keyed, the noise between elements drops by a measurable amount and recovers within the release time.
- New test: for each filter width, a tone at the centre passes at full level and a tone one and a half widths away is down by a set amount; noise power grows with the width.
- New test: switching width while a tone plays leaves no step in the output.
- UI test: the width control and the 10 Hz buttons call the bridge with the right values.
- New test: fist statistics (keyer exact, bug dahs longer than three dits on average, straight key jitter within set limits).
- The soak test (hours of band audio) still passes.
- `tests/BandDemo.java` writes a WAV to listen to on a PC before any phone test.

**Risks and decisions**
- CPU cost on the phone: measure render time per block in a test and keep a wide margin.
- AGC pumping can be tiring over a long session. Decision for you: always on, or a setting (Clean / Real).
- A narrow filter that rings too much is unpleasant to copy through. The amount of ringing is tuned by ear from the WAV demo.
- This step is larger than first planned. If it grows too far, the fists move to their own version.

## Step 4 (version 2.3): take part in a contact with typed sending

**What you get:** you answer a CQ, or call CQ yourself, and a station works you in real time.

**Settings**
- Your callsign, name, QTH, rig and power. The bridge only stores numbers today (`set(String, int)`), so it needs a text setter.
- Decision for you: your real callsign or a made-up one.

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

## Step 5 (version 2.4): hand keying

**What you get:** you send with your own fist, on the screen or with a real key.

- Start with a test screen that measures tap-to-tone delay on your phone. Go ahead only if it is under about 40 ms.
- A second, low-latency audio output just for the sidetone.
- Touch straight key and iambic paddle as a native view, not in the WebView.
- A decoder with adaptive timing turns your keying into text for `Partner`. Sloppy spacing is copied as sent, so the partner may ask `?`.
- External keys: a paddle or straight key through a USB or Bluetooth keyboard or mouse adapter.
- After each over the app can show what it copied from you, as sending practice.

## Step 6 (version 2.5): a band that follows the clock

**What you get:** the band changes through the day, and where you tune matters.

- Setting: your own QTH, as a region.
- Propagation: nearby stations by day, DX at night and around sunrise and sunset. Distance and time set each operator's strength, using the per-operator strength that already exists.
- One-sided contacts: the other operator is in the skip zone and you hear only half.
- Band geography: fast DX operators near 7000 to 7010, QRP around 7030, slower operators higher up.
- Activity: busier evenings and weekends, contest weekends from a small calendar.

## Step 7 (version 2.6): reasons to come back

- Recurring stations: the same operator returns on later days with the same rig and QTH, and greets you by name once you have worked them.
- Copy practice: after listening to a contact, fill in call, name, QTH and report; the app scores it against what was sent.
- Awards from the logbook: countries, QRP contacts, summits and parks.
- Pile-up mode: you are the park activator, stations call you, and the score is rate and accuracy.

## Step 8 (version 2.7): the front panel

- A scrolling waterfall and an S-meter. The screen polls every 500 ms today, so this needs a faster display path.
- A large VFO knob with inertia and haptic clicks.
- RIT.

## Ideas, not scheduled

- Nets and round tables with three or more stations.
- A DX station working split with a pile-up.
- Real solar data and real weather for the operators' towns. Both need the network, so they must stay optional.

## Order, and why

1. Receiver chain and fists first: it is mostly audio and improves everything heard afterwards. Filter width and fine tuning ride along because they come almost free once there is one filter, and the narrow filter is unusable without fine tuning.
2. Typed contacts before hand keying: the partner logic is the hard part, and typing avoids the audio-delay problem.
3. Hand keying only after a delay measurement on a real phone.
4. Clock-driven band, recurring stations and the front panel build on the earlier steps and can be reordered.

## How every step is finished

1. `bash tests/run.sh` passes on a PC.
2. GitHub Actions builds `CWNews-apk` from the pushed commit.
3. Version code and name are raised.
4. You install it and listen on a phone. A step is not done before that.
5. This plan and the README are updated.

## Known gaps

- 2.1 has not been tested on a phone.
- `tests/ui.py` needs the Python Playwright module, which is missing in the cloud session.
- SOTA region codes are plausible but unchecked against the real summit lists.
- POTA prefixes outside the US follow the ISO country scheme by inference; only the US change (`K-` to `US-`) is confirmed.
- There is no propagation model yet: a Danish station can chat with Argentina at noon.
