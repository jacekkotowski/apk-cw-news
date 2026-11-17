# CW News: work packages

Precise specifications for the next steps, written so that each package can be built in its own session by the model named on it. `docs/PLAN.md` has the overview, the order and the reasons.

How to use this file: start a new session, pick the model, and say "Read `CLAUDE.md` and `docs/WORK-PACKAGES.md`, then build package P1 only." One package per session keeps the cost down.

## Rules for every package

These apply whichever model builds it.

**The code**
- No Gradle. The APK is built by `build.sh`; in a cloud session there is no Android SDK, so Android code (`MainActivity`, `CwService`, `Db`, `Refresher`, `RefreshJob`) is compiled only by GitHub Actions. Push to the working branch and check that the `android` workflow run succeeds before reporting a build.
- The classes listed in `CLAUDE.md` have no Android imports and are tested on a PC by `bash tests/run.sh`. Every new class with logic in it goes on that list, into the `javac` line of `tests/run.sh`, and gets tests.
- minSdk 26: no `List.of`, `Map.of`, `String.isBlank`, `strip`, `repeat`.
- Pure classes take a seeded `Dsp.Rng`. Never use `Math.random()` or the clock inside them, or the tests stop being repeatable.
- Audio thread (`Engine.render`, `Rx.process`, `Band.render`, `Bed.fill`): no allocation, no logging, no locks other than `Band`'s own `synchronized`, no file or network access.

**Settings pattern** (an integer setting needs four small edits)
1. UI: `<div class="seg" data-k="KEY"><button data-v="0">…</button>…</div>` in `app/assets/index.html`. The existing script wires every `.seg` to `A.set(KEY, value)` and paints the selected button from `S[KEY]`.
2. `MainActivity.Bridge.settings()`: add `o.put("KEY", prefs.getInt("KEY", DEFAULT))`.
3. `CwService.readSettings()`: read the pref and pass it to `engine` or `band`.
4. `tests/ui.py`: add the key to the fake `st` object and assert the click calls `['set','KEY',value]`.

**Tests**
- PC tests live in `tests/T.java`. Helpers: `check(name, condition)`, `eq(name, got, want)`, `near(name, got, want, tolerance)`. Add one static method per package and call it from `main`.
- UI: `tests/ui.py` needs the Python Playwright module, which the cloud session lacks. Update it anyway, and check the page with a short Node Playwright script (Chromium is installed; `require('playwright')` works). Use a fake `window.Android` object as `tests/ui.py` does.
- A package is done when `bash tests/run.sh` prints `0 failed`, the UI check passes, and the GitHub Actions run for the pushed commit succeeds.

**Delivery**
- Raise `versionCode` and `versionName` in `app/AndroidManifest.xml` once per release, not per package.
- Update `README.md` ("How it works", the version line, the permissions table if it changed) and the status table in `docs/PLAN.md`.
- State plainly what was not tested on a phone.

**When to stop and hand over to a stronger model**
- A test in the package's list cannot be made to pass in two honest attempts.
- The change needs edits in `Engine.render`, `Rx.process` or `Band.render` beyond what the package describes.
- The user reports a click, dropout or distortion after the package shipped.

Do not weaken a test to get it to pass; say what failed instead.

---

## Release 2.5: listen better

Four packages. P1 to P3 are independent of each other; P4 uses P3's noise blanker.

### P1. Follow the strongest signal, and a sleep timer

**Model:** Sonnet. **Size:** small.

**What the user gets**
- Band tab: "When it goes quiet: Stay / Strongest". With Strongest, when the contact you are listening to ends, the receiver jumps to the strongest station that can be heard. It works with the screen off.
- Settings: "Stop playing after: Off / 15 / 30 / 60 min". The sound fades out over the last 20 seconds and playback stops.

**Design**

`Band.java`
- New fields: `private long quietFrames; private boolean busyHere;`.
- In `render`, per station after its sample loop, decide once per block:
  - `inPass`: `Rx.gain(loHz, hiHz, pitch) > 0.25`, where `pitch` is the pitch of the station's current operator as `render` already computes it.
  - the station counts as a contact here if `inPass && heard(s)`;
  - it counts as keyed here if `inPass`, the current operator's `strength >= UNHEARD`, and `s.onFrames` grew during this block (remember its value before the sample loop).
- After the station loop: `quietFrames = keyedHere ? 0 : quietFrames + frames; busyHere = contactHere;`.
- New method:
  ```java
  /** Where to go when this frequency has gone quiet, in kHz, or NaN to stay. */
  synchronized double follow(double rxKhz, double quietS, double deadS)
  ```
  - Stay (return `NaN`) unless `quietFrames >= quietS * rate && !busyHere`, or `quietFrames >= deadS * rate`.
  - Candidates: stations with `qso != null`, `heard(s)`, and more than 0.25 kHz from `rxKhz`.
  - Score: the strongest heard operator's `level(o)`, times 0.3 for `Qso.CQ` and `Qso.TUNE` kinds, times 0.5 if fewer than two transmissions remain (`s.qso.tx.size() - s.next < 2`).
  - No candidate: return `NaN`. Otherwise set `quietFrames = 0` and return the winner's `khz`.

`Engine.java`
- `boolean newsHeard()`: true when a band is set, a story is loaded and not finished, and the bulletin is inside the filter (the existing `off` flag is false).

`CwService.java`
- `static volatile long lastUserTune;` and `static volatile int follow;`.
- In the audio loop, every 50th block: if `follow == 1`, a band exists, playback is not paused, `!engine.newsHeard()`, and 20 s have passed since `lastUserTune`, call `band.follow(rxKhz(), 6, 75)`. On a number, set `dial = f - rit / 1000.0` and save it with `prefs.edit().putFloat("dial", …).apply()`.
- Sleep timer: `readSettings` caches `prefs.getLong("sleepAt", 0)`. In the loop, when `sleepAt > 0`: during the last 20 s scale `engine.volume` linearly to zero; at the time, clear `sleepAt` and `sleep` in prefs and stop as the STOP command does.

`MainActivity.java`
- `setDial`, `setRit` and `nextSignal` set `CwService.lastUserTune = System.currentTimeMillis()`.
- `set("sleep", minutes)` also stores `sleepAt` (`0` for Off, else now plus the minutes).
- `settings()` adds `follow` (default 0) and `sleep` (default 0); `state()` adds `sleepLeft` in whole minutes.

UI
- Band tab, under the RIT row: the `follow` seg.
- Settings, new heading "Sleep": the `sleep` seg. The Listen tab's `#msg` shows "Stops in N min" while a timer runs.

**Tests** (`follow()` in `tests/T.java`)
- A band with a seed, tuned with `next()` onto a station: `follow` returns `NaN` while that contact is in progress.
- Keep rendering until that station's slot is free: within 7 s of audio `follow` returns a frequency.
- The returned frequency is one of the stations in `activityJson()`, and it is not the frequency just left.
- With nothing else on the band (activity 0 and all slots idle) `follow` returns `NaN`.
- After a jump, an immediate second call returns `NaN`.

**Done when** the tests pass, the two segs call the bridge, and a note in the report says the screen-off behaviour is untested on a phone.

**Watch out**
- A one-sided contact has long silences while the unheard station sends. That is why "quiet" alone is not enough and `busyHere` exists.
- Do not jump while the user is turning the knob: honour `lastUserTune`.

### P2. CW and CW-R (which side of the signal you listen on)

**Model:** Sonnet. **Size:** small.

**Background for the user's question.** The rule "lower sideband below 10 MHz" is for voice. A CW signal has no sideband; the receiver chooses which side to listen from. Rigs differ in their default (Yaesu's manuals describe CW Reverse as the lower-side setting, so its normal CW is the upper side) and all offer CW-R to swap. Swapping reverses which way the pitch moves as you tune and puts an interfering station on the other side of yours, where the filter may remove it. Today the app behaves like the upper side.

**What the user gets:** Band tab, Receiver: "Sideband: CW / CW-R".

**Design**
- A sign `s`: `+1` for CW (today's behaviour), `-1` for CW-R. Pref `side` (0 or 1).
- `Engine`: `volatile int side;`. In `render`: `pitch = toneHz + sign * (Band.NEWS - dialKhz) * 1000` (keep the existing clamp), and pass the sign to `Band.render`.
- `Band.render` gets a seventh parameter `int sign` and uses `hz = toneHz + sign * (s.khz + o.offsetKhz + o.drifted - dialKhz) * 1000`. The four- and six-argument overloads pass `+1`. Store the sign in a field so `tuned()` uses the same formula.
- New pure helper in `Rx`:
  ```java
  /** The range of station frequencies heard, in Hz relative to where the receiver listens. */
  static double[] heard(int width, double tone, int sign)
  ```
  For `+1` it is `{lo - tone, hi - tone}`; for `-1` it is `{tone - hi, tone - lo}`.
- `MainActivity.band()` uses `Rx.heard` for `passLo` and `passHi`. The page needs no change: it already draws the passband from those two numbers.

**Tests**
- With sign `-1`, a station 300 Hz above the dial is rendered at `tone - 300` (measure with the existing `goertzel` helper), and at `tone + 300` with `+1`.
- The bulletin still waits when tuned outside the filter, in both settings.
- `Rx.heard` for both signs and all three widths.
- A station just above the wanted one that is audible in CW with the Wide filter is at least 20 dB weaker in CW-R, because Wide extends 1200 Hz above the pitch and only 400 Hz below.

**Watch out:** the waterfall must not flip. Only the shaded passband moves to the other side of the needle.

### P3. Filter controls: shift, peak filter, noise blanker, AGC speed

**Model:** Sonnet, with care. **Size:** medium. This edits `Rx.process`; follow the formulas and stop if a test will not pass.

**What the user gets** (Band tab, Receiver)
- "Shift" slider, minus 300 to plus 300 Hz in steps of 50: moves the passband up or down without changing the pitch, to push an interfering station out.
- "Peak filter: Off / On" (APF): lifts the station exactly at your pitch above the rest.
- "Noise blanker: Off / On": removes static ticks.
- "AGC: Off / Slow / Fast": Fast recovers quickly after a static crash; Slow is steadier on a clean signal.

**Design**

Shift
- Pref `shift` (Hz). `Engine.shift` volatile int. Where `Engine.render` computes `lo` and `hi`, add the shift to both, keeping `lo >= 100`.
- `Rx.heard` (from P2) takes the shift as well; if P2 is not built yet, add the helper here.

Peak filter
- Pref `apf` (0 or 1). In `Rx`: one `Dsp.Biquad` per channel set with `bandpass(tone, tone / 80.0, rate)` (0 dB at the centre, about 80 Hz wide).
- In `process`, after the band-pass cascade and before the envelope is measured: `l += apf.step(l)` (same for `r`). A tone at the pitch doubles (+6 dB); a tone 200 Hz away gains under 1 dB.
- `Rx` needs the pitch: add it to `tune(loHz, hiHz, toneHz)` and retune the peak filter with the others.

Noise blanker
- Pref `nb` (0 or 1). It works on the signal before the filter, where a static tick is a single-sample spike.
- Keep one sample of look-ahead per channel. For each sample compute `d = |x[n] - 0.5 * (x[n-1] + x[n+1])|` and a slow average `e` of `d` (time constant 50 ms). If `d > 4.5 * e`, replace `x[n]` with `0.5 * (x[n-1] + x[n+1])` and do not feed that `d` into `e`.
- The one-sample delay applies whether the blanker is on or off, so switching it does not shift the audio.

AGC speed
- Pref `agc`: 0 Off, 1 Slow, 2 Fast. Existing installs have 1, which stays "Slow".
- Release time: Slow 0.5 s, Fast 0.12 s. Replace the constant `RELEASE_S` with a field; recompute `releaseKeep` when it changes.
- `Engine.agc` becomes an `int`. `Rx.process` takes the mode instead of a boolean.

**Tests**
- Shift +200: `Rx.gain` with the shifted edges is 1 at `tone + 200` and below 0.1 at `tone - 250` (Normal width).
- Peak filter on, AGC off, tone of amplitude 0.3 at the pitch: output within 5% of 0.6. Same tone 200 Hz above: under 0.34.
- Blanker: noise only, AGC off, 60 s. Count output samples above four times the RMS; with the blanker on the count falls by at least 70%. A clean 600 Hz tone passes within 1% of its level with the blanker on.
- AGC: after a 1 s tone of amplitude 1 over noise, the time for the noise to return to within 3 dB of its setting is under 0.25 s in Fast and over 0.4 s in Slow.
- Every existing `receiver()` test still passes, and "busy band stays clear of full scale" passes for all AGC modes.

**Watch out**
- Changing the peak filter or the shift while audio plays must use the existing fade-out, swap, settle, fade-in in `Rx.apply`, or it will click.
- The blanker must be skipped for the delay line's first sample after `apply()` resets state.

### P4. Weather on the band

**Model:** Sonnet for `Sky`, the settings and the wording; the `Rx` noise change is small but follows P3's rules. **Size:** medium. **Needs:** P3 (the blanker) to be worth hearing.

**What the user gets**
- The band's noise is no longer the same every day. Summer afternoons and evenings bring thunderstorm static; a storm can come close for a while; rarely the band flutters under an aurora or fades out after a solar flare.
- Settings, Band: "Band weather: Auto / Calm / Summer static / Storm nearby / Aurora / Solar flare". Auto follows season, time and chance; the others hold one state so each can be heard.
- The Band tab says what is going on in one line, and stations mention it.

**What each kind sounds like** (from operators' reports; see the sources in `docs/PLAN.md`)

| Kind | Sound | Where it bites |
|---|---|---|
| Distant thunderstorms | Steady crackle of small ticks, a crash every few seconds | Summer, afternoon to late evening; all year in the tropics |
| Storm nearby | Loud crashes that swamp signals, with rumbling bursts; the AGC pumps | Short spells of 5 to 25 minutes |
| Aurora (geomagnetic storm) | Signals on northern paths flutter quickly and get weaker | Night, high latitudes, a few days a year |
| Solar flare fade-out | Every signal on the sunlit side sinks within minutes and returns over half an hour | Daytime only |

**Design**

New pure class `Sky.java`
```java
final class Sky {
    static final int AUTO = 0, CALM = 1, STATIC = 2, STORM = 3, AURORA = 4, FLARE = 5;
    Sky(long seed);
    /** Moves time on. month 0-11, localHour 0-24 at the listener, latitude in degrees. */
    void advance(double seconds, int month, double localHour, double latitude, int mode);
    double ticksPerSecond();   // 2.5 calm, up to 25 in static, 40 in a storm
    double crashEverySeconds();// 40 calm, 6 static, 1.5 storm
    double crashGain();        // 1 calm and static, 2.5 storm
    double aurora();           // 0 to 1
    double flare();            // 0 to 1
    String label();            // "" when calm, else a short sentence for the Band tab
}
```
- States for thunderstorms: CALM, STATIC, STORM. In Auto, the chance per hour of leaving CALM is `0.05 + 0.5 * season * daytime`, where `season` is 1 in the local summer months, 0.5 in spring and autumn, 0.1 in winter, and 1 all year below 25 degrees latitude; `daytime` is 1 from 13 to 23 local, else 0.3. STATIC lasts 20 to 90 minutes; from it, a STORM starts with chance 0.3 per hour and lasts 5 to 25 minutes, then falls back to STATIC.
- Aurora in Auto: starts with chance 0.004 per hour, lasts 2 to 6 hours, level rises and falls over 30 minutes.
- Flare in Auto: only when `localHour` is between 7 and 17; chance 0.003 per hour; rises over 3 minutes, holds 5 to 15, recovers over 30.
- All outputs change smoothly: move each toward its target by a small step per call, never jump.

`Rx.java`
- The four static constants (`TICKS_PER_S`, `CRASH_EVERY_S`, and a new crash gain) become fields set by `void weather(double ticksPerS, double crashEveryS, double crashGain)`.
- A crash gains a second part that the blanker cannot remove: for the length of the crash, multiply the noise deviation by `1 + 3 * crashGain * envelope`, where the envelope rises in 10 ms and decays over the crash.

`Band.java`
- Owns a `Sky`; `render` calls `sky.advance(frames / (double) rate, month, hourNow() + lon / 15, lat, mode)`.
- `double[] weather()` returns the three noise numbers for `Engine` to pass to `rx.weather(...)` each block.
- Per operator, at spawn: `northern` = true when the listener, the operator, or the path's midpoint is above 55 degrees north or south (add a small pure helper to `Prop`). In `gain(o)`: when `northern`, multiply by `1 - 0.5 * aurora * (0.5 + 0.5 * sin(o.pf))` using a flutter rate of 12 to 20 Hz, and apply `-12 * aurora` dB.
- Flare: every operator whose path is in daylight (`Prop.sunElevation` above 0 at the midpoint) gets `-30 * flare` dB.
- `String sky()` for the bridge.

`Qso.java`
- `Qso.Scene` gains `double qrn` (0 to 1) and `boolean aurora`. In the `CONDX` topic: with `qrn > 0.4` say one of `QRN VY BAD HR`, `LOTS OF STATIC TODAY`, `QRN FROM TSTORMS HR`; with `aurora` say `AURORA TONITE SIGS FLUTTERY` or `CONDX POOR K INDEX HIGH`.

Bridge and UI
- Pref `weather` (0 to 5). `MainActivity.band()` adds `"sky": "<label>"`. The Band tab shows it in a new `#sky` message line.

**Tests**
- `Sky` in each fixed mode returns that mode's numbers after 60 s of `advance`.
- Auto over 2000 simulated summer evenings at 50 degrees north is in STATIC or STORM for 25 to 60% of the time; over winter nights under 10%.
- No output changes by more than 5% of its range between two calls 10 ms apart.
- Flare never rises when the local hour is 22.
- `Rx` with storm numbers: output noise RMS is at least 6 dB above calm, and the blanker (P3) brings ticks down but leaves at least half of that rise.
- Aurora at level 1: an operator at 62 degrees north is at least 10 dB weaker than the same operator with aurora 0; one at 40 degrees is unchanged.
- "busy band stays clear of full scale" still passes in STORM mode.

**Not in this package:** real space-weather data (P5), rain static (no good description of its sound was found).

### P5. Real space weather (optional)

**Model:** Sonnet. **Size:** small. **Needs:** P4.

- Settings: "Use real space weather: Off / On" (default Off, so the app stays quiet on the network unless asked).
- When On and online, `Refresher` fetches NOAA's planetary K index once per refresh and stores the latest value in prefs. Candidate address, to be verified by fetching a sample before coding: `https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json`.
- `Sky` in Auto: with a stored Kp of 5 or more, aurora level is `(Kp - 4) / 5`, capped at 1, in place of the random aurora.
- The fetch must use the same HTTPS-only path as the feeds, fail silently, and never block playback. Add the host to the README's privacy notes.
- Test the parser on a saved sample file, including an empty and a malformed one.

---

## Release 2.6: your first contacts

The partner logic is the hard part, and it is where a mistake would spoil the experience. P6 is for Fable; P7 and P8 are routine and can be built by Sonnet once P6 exists.

### What "forgiving" means

These rules are the heart of the release. They come from what learners say stops them: fear of making mistakes on the air ("key fright"), and partners who are too fast.

1. **Nothing the user sends ends the contact.** There is no failure state and no score during a contact.
2. **One request at a time.** The partner asks only for the most important missing thing (callsign, then report, then name, then QTH), in the shortest form: `CALL?`, `UR NAME?`.
3. **Two misses, then move on.** If the same item is still unclear after two requests, the partner says `NO PROBLEM` and continues without it. The callsign gets three.
4. **Close is good enough.** A callsign within two letters of the user's own is taken as the user's. The partner sends it back for confirmation once (`SP9ABC?`) and accepts `R`, `C`, `YES`, or a repeat.
5. **The partner slows down, never the user.** It answers at the user's measured speed or slower, widening the gaps between letters before slowing the letters themselves. `QRS` slows it by a quarter each time.
6. **Patience.** It waits 25 seconds of silence before a gentle prompt (the user's call and `?`), and prompts at most three times. Then it signs off kindly (`NIL HR 73 CUL`) and the contact is logged as incomplete, not failed.
7. **Encouragement, in moderation.** At most one short phrase per over, chosen by what just happened:
   - after the user corrects an error: `NO PROBLEM`, `NP`
   - after a repeat that worked: `GUD COPY NW`, `TNX FER RPT`
   - to a user with fewer than five logged contacts: `UR CW IS FB`, `NICE FIST`, `TAKE UR TIME`
   - at the end: `TNX FER QSO UR DOING FB`, `HPE CU AGN`
8. **Unreadable sending gets help, not silence.** First `SRI NO COPY PSE AGN`; second `PSE AGN SLOWLY`, and the partner itself slows down; third, the partner asks for exactly one thing (`PSE UR CALL?`).
9. **A minimal contact counts.** Callsigns both ways and one report make a logged contact. Name, QTH and chat are extra.
10. **Important things are sent twice.** The partner repeats its name, QTH and the report, as real operators do for a weak or new station.

Three levels, chosen in Settings as "Partners are: Patient / Normal / Realistic". Patient is the default and follows every rule above. Normal waits less and asks for repeats once. Realistic adds real conditions: the partner may not hear you if you are off its frequency, weak or covered by noise, and may copy letters wrong.

Three kinds of contact, offered easiest first: a park or summit activator (four short overs: call, report, thanks), a standard contact (report, name, QTH), and a chat.

### P6. The conversation core

**Model:** Fable. **Size:** large. Everything here is pure Java or audio-thread code, tested on a PC with scripted text. No UI.

**Parts**

1. `Heard`: what one over of the user's contained. `static Heard parse(String text, String myCall, String theirCall)`.
   - Finds callsigns, a report (`599`, `5NN`, `579C`), `NAME`/`OP`, `QTH`, `RIG`, `PWR`, `ANT`, `WX` values, questions (`NAME?`, `QTH?`, `RIG?`, `HW?`), requests (`QRS`, `QRQ`, `AGN`, `?`, `RPT`), closings (`73`, `SK`, `TU`), turn markers (`K`, `KN`, `BK`), the error sign, and a greeting.
   - Keywords of three or more letters match within one wrong letter. Callsigns are compared by edit distance.
   - Letters that ran together or split apart are a common sending fault: try the obvious re-segmentations of a failed word against the expected words before giving up.
2. `Partner`: one side of a contact, driven over by over.
   ```java
   static Partner calling(Dsp.Rng r, Qso.Scene scene, Profile me, int manners, int kind);   // the partner calls CQ
   static Partner answering(Dsp.Rng r, Qso.Scene scene, Profile me, int manners);           // answers the user's CQ
   static Partner from(Qso contact, int operator, Profile me, int manners);                 // takes over a station already on the band
   Qso.Tx first();
   Qso.Tx hear(String sent, double seconds, double offsetHz, double wpm);  // the user's over ended
   Qso.Tx silence(double seconds);                                         // null means keep waiting
   boolean done();
   String hint();        // what would be natural to send now, in plain words
   Contact result();     // for the log and the feedback screen
   ```
   - Stages: CALL, EXCHANGE, CHAT, BYE, DONE.
   - It keeps what it knows about the user, how often each item was missed, and its current speed.
   - Its words come from the phrase modules in `Qso.Gen` (`basics`, `say`, `react`, `goodbye`, `bye73`), so a live partner sounds like the stations already on the band.
3. `Qso` refactor: `Gen` builds a whole contact in one pass today. Split it so one over can be produced at a time with the conversation state (`said`, `pending`, turns left) held in an object. The generated contacts must not change: record a hash of the text of 500 contacts from fixed seeds before the refactor and assert it afterwards.
4. `Band`: a live station.
   - `boolean takeOver(double rxKhz, Partner p)`: turns the station being listened to into a live one if it is calling CQ and nobody has answered. It keeps its frequency, strength and fist.
   - `boolean answerMe(double txKhz, Partner p)`: a new live station appears on the user's frequency.
   - `void userOver(String text, double seconds, double wpm, double txKhz)`: passes the over to the live partner and queues its reply with a natural pause.
   - A live station is not replaced or timed out while the contact runs, and `follow` (P1) never leaves it.
   - `String liveJson()`: stage, partner's call, hint, transcript.
5. `Engine`: the user's transmitter.
   - `void send(Keying k)`: plays a sidetone at the CW pitch for typed text.
   - `void key(boolean down)`: for a real key (P9): no sidetone by default.
   - While the user sends, the receiver is ducked by 20 dB within 5 ms and released 0.4 s after the last element, as with semi break-in. No clicks: reuse the gate ramps.
   - The transmit frequency is the dial; the receiver listens at the dial plus RIT. This is where RIT becomes useful.

**Tests** (scripted, on a PC)
- A minimal contact in four overs is logged as complete.
- A full standard contact; a chat of at least eight overs.
- The user's callsign sent with one wrong letter is confirmed and accepted.
- Three unreadable overs in a row: the partner's three replies follow rule 8, its speed drops, and the contact continues.
- Silence: prompts at 25 s, three times, then a kind sign-off; the result is "incomplete".
- `QRS` twice: the partner's speed falls to about 56% and never rises again.
- The error sign in the user's over is followed by `NO PROBLEM` or `NP` at most once.
- Never more than one encouragement and one request in a single over, across 2000 random scripted contacts.
- Realistic manners, 300 Hz off frequency: the partner does not answer; Patient manners: it does.
- The partner's effective speed never exceeds the user's measured speed in Patient manners.
- Generated (non-live) contacts are unchanged: the hash matches.
- Sidetone and receiver ducking produce no step larger than the existing "no click" limit.

### P7. Your station and the logbook

**Model:** Sonnet. **Size:** small to medium. Can be built before P6.

- Settings, new heading "Your station": callsign, name, QTH, rig (text fields), power (5, 10, 50, 100 W), sending speed for typed text, "Partners are" (the three manners).
- Bridge: `setText(String key, String value)` for `mycall`, `myname`, `myqth`, `myrig`. Upper-case the value, keep only characters `Morse.supported` accepts, limit the length (call 10, others 20). A callsign must match `[A-Z0-9]{1,3}[0-9][A-Z0-9]{0,3}[A-Z](/[A-Z0-9]+)?`; otherwise return a message for the page to show.
- A pure class `Profile` holds these values, with a made-up but valid default so that contacts work before anything is entered.
- `Db`: version 2. New table `log(id INTEGER PRIMARY KEY, at INTEGER, call TEXT, name TEXT, qth TEXT, rst_sent TEXT, rst_rcvd TEXT, khz REAL, complete INTEGER, overs INTEGER)`. `onUpgrade` must create it for version 1 databases without touching feeds or articles. Methods `addLog(...)`, `logJson(int limit)`, `logCount()`.
- A fifth tab, "Log": newest first, with date, call, name, QTH, reports; incomplete contacts shown plainly, not as errors. A line at the top: "N contacts, M countries".
- Tests: text validation on a PC (move the rules into `Profile`), the page with a fake bridge, and the upgrade path described in the report since it cannot run on a PC.

### P8. Taking part by typing

**Model:** Sonnet. **Size:** medium. **Needs:** P6 and P7.

- Band tab, a "Contact" card below the tuning controls.
  - **Answer** is enabled when the station in the filter is calling CQ. **Call CQ** is enabled on a clear frequency.
  - During a contact: the partner's call, a hint line (from `Partner.hint()`, switchable off in Settings), and the transcript.
  - "Show their text: After each over / Never / As they send". The default is after each over, because copying by ear is the point and the text is the check.
  - A text line with buttons that fill it in from the profile and can be edited before sending: **Call** (`{their} DE {my} {my} K`), **Report** (`{their} DE {my} <BT> TNX FER CALL <BT> UR RST 579 579 <BT> NAME {name} {name} <BT> QTH {qth} {qth} <BT> HW? {their} DE {my} <KN>`), **R**, **AGN?**, **QRS**, **73** (`{their} DE {my} TNX FER QSO 73 <SK>`).
  - A zero-beat line: how far the station is from your transmit frequency, and a **Net** button that puts the dial on it and clears RIT.
  - **End** closes the card; the contact is logged with whatever was exchanged.
- Bridge: `liveState()`, `answer()`, `callCq()`, `sendText(String)`, `net()`, `endContact()`.
- When a contact finishes, a short summary: who, what was exchanged, and one kind sentence. No score.
- Tests: the page with a fake bridge (buttons enable and fill correctly, the transcript honours the show-text setting, Net calls the bridge), and `tests/ui.py` updated.

---

## Release 2.7: your own key

The user has a key with a buzzer. The phone listens to the buzzer through its microphone and reads the Morse. This also removes the audio-delay problem: the buzzer is the sidetone, so the app's own delay does not matter.

Others have done this in a browser (a sending-practice page that listens through the microphone to a practice oscillator and draws a timing chart), so the approach is known to work. Their advice applies here too: a cable from the oscillator to the phone is more reliable than the room microphone when there is noise.

### P9. Hearing the key

**Model:** Fable. **Size:** large. Signal processing that must work in a real room, plus a decoder that has to be tolerant of a learner's timing.

**Parts**

1. `ToneDetector` (pure Java). Input: mono samples at 44.1 or 48 kHz.
   - Calibration: with the key held, find the buzzer's pitch between 300 and 4000 Hz (piezo buzzers are high) and its level; with the key up, measure the room; with the app's band playing, measure how much of it reaches the microphone at that pitch.
   - Running: a band-pass at the buzzer's pitch, an envelope with about 2 ms rise and 4 ms fall, and a threshold with hysteresis set from the calibration. Output: key-down and key-up times, to the sample.
   - It reports when the leak from the speaker is too close to the buzzer's level, so the page can recommend headphones or a lower volume.
2. `Decoder` (pure Java). Input: the key times.
   - Estimates the dot length from the recent marks and keeps adapting; separates dots from dashes and the three kinds of gap by clusters, not fixed ratios, so that Farnsworth spacing, a bug's long dashes and a beginner's hesitations are all read.
   - Emits letters, word breaks, prosigns and the error sign.
   - Decides the over has ended: `K`, `KN`, `BK` or `SK` followed by 1.5 s of silence, or 4 s of silence in Patient manners.
   - Keeps the raw timings for the feedback screen (P11).
3. `Mic` (Android). `AudioRecord` on its own thread, started by `MainActivity` only while the Band tab is visible, the contact card is open and "My key" is chosen; stopped in `onPause`. Because capture only happens while the app is on screen, no microphone foreground-service type is needed; confirm this against the current Android documentation when building.
   - Audio source `UNPROCESSED` where the device supports it, else `VOICE_RECOGNITION`, so the phone's own gain control and noise suppression do not chop the tone.
   - New permission `RECORD_AUDIO`, requested the first time "My key" is chosen, with a sentence saying that the sound is analysed on the phone and never stored or sent.
4. Wiring: key-down and key-up go to `Engine.key(...)` for the receiver ducking; a finished over goes to `Band.userOver(...)` with the decoded text and the measured speed.

**Tests** (on a PC, with sound made by `Keying` and `Fist` at a buzzer pitch)
- Keyer timing, 20 dB above the room noise, 5 to 30 WPM: at least 99% of letters right.
- Straight key and bug fists from `Fist.of`, 12 to 22 WPM, 10 dB above noise: at least 92%.
- A beginner's fist with Farnsworth spacing at 20/8: at least 90%, and word breaks in the right places.
- Noise alone, a spoken-voice-like burst, and a door slam (a single loud click): no letters.
- Buzzer pitches of 500, 800, 2400 and 3200 Hz are all found within 3%.
- The app's band audio leaking at 12 dB below the buzzer: no false keying while the user is silent.
- Key-down is reported within 15 ms.
- The end of an over is found for all four endings, and not in the middle of a slow sender's word.

**Open questions for the phone:** how loud the user's buzzer is at the microphone, and whether the phone's `UNPROCESSED` source is truly unprocessed. Both are answered by the calibration screen in P10.

### P10. The microphone screens

**Model:** Sonnet. **Size:** medium. **Needs:** P9.

- Settings, "Your station": "Send with: Typing / My key (microphone)".
- A three-step set-up, run the first time and available again from Settings:
  1. "Hold your key down for two seconds." Shows the pitch found and a level bar.
  2. "Release it and stay quiet for two seconds."
  3. "Now the band plays for three seconds." If too much reaches the microphone: "Use headphones, or turn the volume down."
- In the contact card: a key light that follows the buzzer, and a line "You sent: …" that fills in as letters are read. The typing line stays available as a fallback.
- **Warm up**: a button that listens for 60 seconds with no partner, showing what is read. Going on the air cold produces errors, and this gives the hand a minute first.
- A short "It does not hear my key" help text: move the phone closer, recalibrate, use headphones, or connect the buzzer by cable.
- README: add `RECORD_AUDIO` to the permissions table with the reason.
- Tests: the page with a fake bridge for all three steps and both outcomes of step 3; `tests/ui.py` updated.

### P11. Feedback on sending

**Model:** Sonnet. **Size:** small. **Needs:** P9.

- After a contact or a warm-up, a card with three numbers from the decoder's timings: speed in WPM, dash length in dots (3 is the standard), and evenness of the gaps between letters.
- A small timing strip: the standard timing on the top row and the user's on the bottom, for the last word sent.
- One sentence, always phrased as the next thing to try, never as a fault: "Your dashes are a little short; try holding them for a count of three."
- A pure class computes the numbers and picks the sentence, so both are tested on a PC.

---

## Housekeeping

**Model:** Haiku. Version bumps, the README, release notes, and the status table in `docs/PLAN.md` after each release. Opening the pull request and merging when asked.
