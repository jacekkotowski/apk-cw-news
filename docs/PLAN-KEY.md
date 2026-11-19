# Plan: on the air with your own key (lenient decoder + friendly partner)

## Context

The owner wants to key a real key with a buzzer next to the phone, call or answer a simulated station on the Band tab, and hold a real conversation: report, name, QTH, then weather, antenna, job, hobby, both ways. The partner must be lenient: it asks again kindly, slows down, encourages, and never fails a contact. Decoding must tolerate a poor fist and must not be heavy on the phone. It must be optional: it is on only while you transmit.

The repo already has a spec for this in `docs/WORK-PACKAGES.md`: P6 to P8 (Release 2.6, typing) and P9 to P11 (Release 2.7, key). Those packages assumed Fable for P6 and P9. Another AI proposed a DeepCW/ONNX plan. This plan merges the two and rewrites them as packages a weaker model (Sonnet) can build, with formulas, interfaces and tests named.

**Status:** approved by the owner on 2026-11-19, not started. This plan replaces P6 to P11 in `docs/WORK-PACKAGES.md`.

**Where to start (next session):**
1. Read `CLAUDE.md`, this file and the "Rules for every package" in `docs/WORK-PACKAGES.md`.
2. Write K1 to K8 below into `docs/WORK-PACKAGES.md` in full, in its style, replacing the Release 2.6 and 2.7 sections. This file is the source.
3. Build K1, one package per session.

## Verdict on the proposed DeepCW plan

The model is real and MIT-licensed. I checked `e04/deepcw-engine` (`model*.onnx.json`, `examples/python/decode_morse.py`). The proposed plan has several factual errors a builder would copy:

| Proposed | Actual |
|---|---|
| `model_narrow.onnx` is about 14 MB, bins 32 to 96 (400 to 1200 Hz, 65 bins) | narrow is **1.2 MB, 3 bins at 787.5 to 812.5 Hz**, so the tone must sit at 800 Hz within about ±10 Hz. The 65-bin, 15 MB file is `model.onnx` (wide). |
| CTC blank index 0 | **`blank_index` 41** (last class). 42 classes: `,./0-9?A-Z` and space |
| log1p, then divide by q90 | divide magnitude by `noise = max(q90 / sqrt(-2 ln 0.1), peak·1e-3, 1e-5)`, **then** log1p |
| input `[1,frames,65]` | input `spectrogram` `[1,1,T,F]`; output `log_probs` `[1,T/2,42]` (`output_time_stride` 2) |
| decimate the microphone directly to 3200 Hz | a piezo buzzer (2 to 4 kHz) lies above the 1600 Hz Nyquist limit and would be lost. The buzzer pitch must be **shifted to 800 Hz first** |
| one `libonnxruntime.so` | ONNX Runtime Android also needs `libonnxruntime4j_jni.so` |
| `zip -r … res AndroidManifest.xml`, `zip -0` and mmap | `build.sh` uses `aapt2 link` → base.apk. A 1.2 MB model loads from a byte array; no mmap or uncompressed asset is needed |
| the ONNX decoder runs in `tests/run.sh` | it cannot: ONNX Runtime is native. Only the pre- and post-processing can be pure Java |
| record in `CwService` | that is a mediaPlayback foreground service, and Android 14+ needs a microphone service type to record from it. Record in the Activity, on screen only (owner's choice) |
| text out only | the partner, receiver muting, speed estimate, end-of-over and feedback all need **key timings**. A tone front end is needed anyway |
| 12 s listening window | contradicts the forgiving rules (25 s patience, three prompts) |
| re-decode a 15 s window every second | the reference uses a 12 s window, a 1 s step and a posterior stitcher (`PosteriorStitcher`), which must be ported |
| (unstated) | the vocabulary has **no prosigns and no error sign**: `<BT>`, `<KN>`, `<SK>` come out as letter pairs, and run-on words like `73AE0QDE` occur |

## Judgement: simple first, neural only if needed

The network is not heavy to run. The narrow model is small, and its compute on a 3-bin input is light. What makes it heavy is everything around it:
- 15 to 20 MB of native runtime in an APK that is small today.
- Downloads, two native libraries and a second test pipeline (Python golden files in CI).
- Code that cannot be tested on a PC.

That is a lot of complexity for a weaker model to get right.

The neural net is also strong at the wrong problem. Its gains come from weak signals, fading and noise. A buzzer beside the phone is a loud, clean tone at one known pitch. Your difficulty is **timing**: dashes too short, gaps uneven, letters running together. A timing decoder can handle that well when it:
1. learns *your* hand (your own dot, dash and gap lengths, measured from text you send on purpose), and
2. lets the partner's expectations fix what timing alone cannot decide. If it expects `NAME` and hears `NAMR`, that is `NAME`.

So the chosen design is:
- **Front end:** one cheap pure-Java tone detector, `KeyEar`.
- **Decoder:** a pure-Java decoder that adapts to your hand, `KeyDecoder`, seeded from a personal fist profile.
- **Reader:** a context reader, `Heard`.
- **Partner:** one that asks again kindly instead of guessing.

Everything is tested on a PC with a deliberately bad straight-key fist. Real overs you send can be saved as timings and added to the tests.

**DeepCW (K7) is a contingency.** It is built only if a measured go/no-go check after K6 fails (see K7). Nothing in K1 to K6 depends on it. `KeyEar` already produces the shifted 800 Hz stream it would need, so the option stays open at no cost.

## Package order and model

| Pkg | What | Model | Pure Java? | Ships as |
|---|---|---|---|---|
| K1 | Your station + logbook (old P7, plus `ant` field) | Sonnet | `Profile` yes | |
| K2 | `Heard`: lenient reader of one over | Sonnet, with care | yes | |
| K3 | `Partner`: the friendly operator | Sonnet, with care | yes | |
| K4 | Live station on the band, TX muting, contact card with typing line | Sonnet | Band/Engine yes | **2.6.0**: first phone test (typing) |
| K5 | `KeyEar` + `KeyDecoder` (DSP, timing) | Sonnet, with care | yes | |
| K6 | Microphone, calibration, "learn my fist", key light, "You sent", warm-up, save over | Sonnet | no (Android + UI) | **2.7.0**: phone test with key |
| K8 | Feedback on sending (old P11, unchanged) | Sonnet | yes | 2.8.0 |
| K7 | *Contingency:* neural decoding (DeepCW narrow), only if the go/no-go check fails | Sonnet, with care, stop rules | pre/post yes, runner no | only if needed |

All rules in "Rules for every package" stay: no Android imports in pure classes, every new pure class added to `CLAUDE.md` and to the `javac` line in `tests/run.sh`, seeded `Dsp.Rng` only, no allocation in `Engine.render` / `Rx.process` / `Band.render`, minSdk 26 APIs, `0 failed`, CI build green, version raised per delivered build, and say plainly what was not heard on a phone. Stop and hand over if a listed test fails twice.

---

## K1. Your station and the logbook
Take old P7 as written (Settings "Your station", `Bridge.setText`, `Profile`, `Db` v2 `log` table, Log tab). Changes:
- `Profile` fields: `call, name, qth, rig, ant, power, sendWpm, manners`. Add `ant` (20 chars). The default is a made-up valid call (`SP0CWN`; it must match the regex in P7).
- Settings also gets "Send with: Typing / My key (microphone)" (`sendwith`, int 0/1), using the 4-edit settings pattern.

## K2. `Heard`: reading one over leniently
New file `app/src/pl/cwnews/Heard.java` (pure). Add a reverse table to `Morse`: `static String letter(String code)`, returning the char or prosign name (`BT KN SK AR AS HH`), or null.

**Input:** `static Heard parse(String text, Words expect)`. `text` is the decoded over: letters, spaces, `<BT>`-style prosigns and `*` for unknown. `Expect` lists the words the partner expects now: its own call, the user's call (if known), its name and city, the user's profile values, and the fixed vocabulary below. Also `static Heard parse(List<Letter> letters, Words expect)`, where `Letter` (from K5) carries alternatives, so ambiguous timing can be resolved here.

**Fixed vocabulary:** `CQ DE K KN BK SK AR BT R RR OK TU TNX FB UR RST NAME OP QTH RIG ANT WX PWR HR ES GM GA GE GN 73 88 CUL HPE AGN PSE QRS QRQ QSB QRM QRN QSL HW CPY SRI NIL NW AGE HOBBY HOBBIES JOB WORK OCC OCCUPATION KEY CONDX YES NO 5NN 599`, plus every abbreviation in `Abbrev`.

**Steps (in order):**
1. **Normalise.** Upper-case. Map neural spellings to prosigns when they stand alone: `BT`→`<BT>`, `KN`→`<KN>`, `SK`/`VA`→`<SK>`, `AR`→`<AR>`. A run of 6 or more of `E I S H 5` = the error sign `<HH>`.
2. **Split run-on words.** Split any token longer than 4 letters that is not in the vocabulary by dynamic programming (word break). Minimise the total cost, where a vocabulary word costs 0, a callsign-shaped piece (regex from P7) costs 0.5, and any other letter costs 1 each. Example: `73AE0QDE` → `73 AE0Q DE`.
3. **Morse-aware fuzzy match** of each token to `Expect`, by weighted edit distance. A substitution costs **0.5** when the two letters' codes differ by one element added, removed or flipped (E/T, I/S, S/H, A/N, A/W, U/V, D/B, R/L, K/C, M/O …), else 1. Insertion and deletion cost 1. Accept when the cost is 0, or ≤1 for words of 3+ letters, or ≤2 for 6+. Callsigns: accept the user's call at cost ≤2 and mark it `callUnsure` if the cost is >0.
4. **Re-segmentation from timing** (only with `List<Letter>`). For a token that failed step 3, try the alternatives K5 recorded: each ambiguous gap can be an inner gap or a letter gap, and each ambiguous mark a dit or a dah. Cap this at 10 ambiguous items (1024 candidates). Keep the best candidate by step-3 cost if it is accepted.
5. **Extract fields.** Callsigns (`X DE Y`, or alone), report (`[1-5][1-9N][1-9N]C?`, treating `N`=9 and `T`=0, also `5NN`), and the value after `NAME`/`OP`, `QTH`, `RIG`, `ANT`, `WX`, `PWR` (up to the next `<BT>` or keyword, at most 4 words). Questions: keyword then `?`, or `UR`/`WHAT`/`HW` before the keyword, for `NAME QTH RST RIG PWR ANT WX AGE HOBBY JOB KEY QSL CONDX`. Requests: `QRS QRQ AGN RPT ?` (a lone `?` = repeat). Closings: `73 SK TU GB CUL`. Turn markers: `K KN BK`. Also the error sign, and greetings (`GM GA GE`).
6. **Readability:** `known = accepted tokens / tokens`. The over is `unreadable` when `known < 0.3` and no call or report was found.

**Tests (T.java `heard()`):**
- `SP9ABX DE DL1ZZ` with user call `SP9ABC` is accepted with `callUnsure`.
- `UR RST 5NN 5NN` → report 599.
- `73AE0QDE` splits as above.
- `NAME JACEK JACEK` and `NAMR JACEK` (one wrong element in E→R?) both give name `JACEK`.
- `WX?`, `UR ANT?`, `WHAT JOB?`, `HOBBIES?` each give the right question.
- `EEEEEEE` → error sign.
- Random letters → `unreadable`.
- For each test pair, the Morse-aware cost is lower than the plain cost.

## K3. `Partner`: the friendly operator
New file `Partner.java` (pure). It **reuses the phrase code in `Qso.Gen`** instead of refactoring the generator:
- Make `Qso.Op` and `Qso.Gen` package-private (remove `private`), with no logic change. **Guard:** before the change, record a hash of the `Tx` texts of 500 contacts from seeds 1 to 500 in `T.java`, and assert that it is unchanged afterwards.
- `Partner` holds a `Gen g` (with its own `List<Tx>` sink), `Op self = g.op(style)`, and `Op you`, built from `Profile` by a new `Qso.userOp(Profile, Scene)`. Unknown fields stay null or 0. `react`/`say` must skip a topic whose field is null, so add null guards there.
- API (from old P6): `calling(...)`, `answering(...)`, `from(Qso, op, ...)`, `first()`, `hear(Heard h, double seconds, double wpm)`, `silence(double s)`, `done()`, `hint()`, `result()`. Each reply is a `Qso.Tx` with `who=0`.

**State:** stage `CALL → EXCHANGE → CHAT → BYE → DONE`; known user fields; `missCount[item]`; `unreadableRun`; `prompts`; `speed`; `askedTopics`; `pendingAnswers` (questions the user asked); `overs`.

**Reply builder (per user over).** Segments are joined with `<BT>`, like `Gen.join`, and added in this order:
1. **Acknowledge** something real, never a bare "wrong". `R R {name}`, `FB {name}`, or `TNX FER RPT` if a report was just received. On the first over after the call, use `Gen.basics(self, you, true)`. It already gives greeting, thanks, the report twice, name and QTH, and it repeats important things.
2. **Callsign confirm** when `callUnsure`: `{call}?` once. Then accept `R`, `C`, `YES`, `OK` or a repeat of the call.
3. **Answers** to the user's questions, at most 2 per over (the rest wait). `WX→say(WX)`, `ANT→say(ANT)`, `RIG/PWR→say(RIG)`, `HOBBY→say(HOBBY)`, `JOB→say(WORK)`, `AGE→say(AGE)`, `KEY→say(KEY)`, `QSL→say(QSL)`, `CONDX→say(CONDX)`, `NAME→g.name(self)`, `QTH→g.qth(self)`, `RST→g.report(self,you)` (sent twice).
4. **Reactions** to what the user told: `react(topic, self, you)` for topics the user just sent (WX, RIG, ANT, HOBBY, KEY, AGE). At most 1, or 2 for CHATTY.
5. **Own content** for the stage. In CHAT: 1 or 2 topics from `self.plan` via `say`.
6. **At most one request:** the most important missing item, in the order call → report → name → QTH. Use the shortest form (`CALL?`, `UR RST?`, `UR NAME?`, `UR QTH?`). After 2 misses (3 for the call), send `NO PROBLEM` and drop it.
7. **At most one question** to keep the chat going, in CHAT only and only when no request was made. Pick a topic not yet known: `UR WX?`, `WHAT RIG ES ANT?`, `ANY OTHER HOBBIES?`, `WHAT DO U DO FER WORK?`, `HW LONG HAM?`.
8. **At most one encouragement** (old rule 7 list), chosen by what just happened.
9. **Turn ending:** `{you} DE {self} KN`. In BYE use `Gen.goodbye` style: `TNX FER QSO {name} … 73 … <SK>`, then `E E`.

**Rules kept from old P6 "What forgiving means" (all ten).** Unreadable sequence: `SRI NO COPY PSE AGN` → `PSE AGN SLOWLY` (and the partner slows 25%) → `PSE UR CALL?`. Silence: a gentle prompt `{you}?` at 25 s, at most three, then `NIL HR 73 CUL`; the result is "incomplete". Speed: `≤ min(userWpm, settings)`, widening the Farnsworth gap first. `QRS` gives ×0.75 each time, and the partner never speeds up again. Minimal contact = both calls plus one report. Manners: Patient, Normal or Realistic (from P7).

**Contact kinds:** activator (call → report → TU 73), standard (report, name, QTH), chat (standard plus 3 to 6 CHAT overs). The user picks the kind with Answer/Call CQ; a CQ answerer's style is drawn as `CASUAL`/`CHATTY`.

**Tests (scripted, in T.java `partner()`):** all the tests listed under old P6 (minimal contact logged complete, full standard contact, chat of 8+ overs, one wrong letter in the call confirmed, three unreadable overs, silence ×3 then incomplete, QRS twice to about 56%, the error sign answered with NP at most once, ≤1 encouragement / ≤1 request / ≤1 question per over across 2000 random scripts, Realistic off-frequency gets no answer, partner speed ≤ user speed, generated-contacts hash unchanged), **plus:**
- `WX? ANT? HOBBY? JOB?` sent in one over: two are answered now and two in the next over, each with text from `say`.
- The user sends `WX HR SUNNY 25C`: the partner's next over may react, and never asks `UR WX?` afterwards.
- No reply ever contains `WRONG`, `ERROR` or `FAIL`.

## K4. A live station, TX muting, the contact card (typing fallback)
**`Qso`:** add `static Qso live(Op self, …)`, a one-operator `Qso` with the arrays filled, kind `RAGCHEW`. Partner replies are appended to `qso.tx`.

**`Band`** (as specified in old P6 item 4):
- Add `Partner live` on `Station`. `takeOver(rxKhz, p)` turns the CQ station being listened to into a live one. `answerMe(txKhz, p)` spawns a station on the user's frequency, with strength from `Prop` and a fist from `Fist.of`.
- `endTx`: when `live != null` and the transmissions have run out, do not clear the `qso`. Just wait.
- `userOver(Heard h, double seconds, double wpm, double txKhz)` is `synchronized`. It runs on the caller's thread (never the audio thread): it calls `live.hear`, appends the reply with its pause, and sets `s.wait`.
- `tickLive(double nowS)` is called once a second from MainActivity and passes silence to `live.silence`.
- `follow()` skips while a live station exists. `liveJson()` returns stage, partner call, hint, transcript and `sending` (whether the partner is keying now).

**`Engine`:**
- `volatile boolean txKey` and `key(boolean down)`. In `render`, a duck gain ramps to −20 dB over 5 ms while the key is down. It holds for 0.4 s after the last key-up (counted in samples), then ramps back over 5 ms using the same gate ramp style as `GATE_S`. Apply it to `sig` after `rx.process`, before the volume. No allocation.
- Typed overs: `send(Keying k)` plays a sidetone at `toneHz`, mixed after the duck.
- The transmit frequency is the dial; the receiver listens at dial + RIT.

**UI and bridge:** the contact card from old P8, reduced:
- **Answer** (enabled when a CQ is in the filter) and **Call CQ** (on a clear frequency).
- The partner's call, the hint line, and a transcript shown "after each over".
- A typing line with **Call / Report / R / AGN? / QRS / 73** fill buttons from `Profile`, **Send**, **End**, and the zero-beat line with **Net**.
- Bridge: `liveState() answer() callCq() sendText(s) net() endContact()`. `sendText` runs `Heard.parse` with the partner's expectations, then `Band.userOver(…, wpm = Profile.sendWpm)`.
- The contact is logged to `Db.addLog` when the partner is done, or on End.

**Tests:**
- Band: `answerMe` + 4 scripted overs reach DONE, and the station is never replaced meanwhile. `follow()` returns NaN while live.
- Engine: a key-down step stays under the existing no-click limit, the duck is −20 dB within 5 ms, and it is released 0.4 s after key-up.
- UI: a Node Playwright check with a fake bridge (Answer enables, fill buttons, Send calls `sendText`), and `tests/ui.py` updated.

**Delivery:** 2.6.0. The owner holds a typed contact on the phone.

## K5. `KeyEar` and `KeyDecoder` (pure DSP)
**`KeyEar.java`** handles mono PCM at `rate` (16000 by default; 48000 must work too).
- **Calibration** `findPitch(float[] keyDown2s)`: a 4096-point Hann FFT (write a small radix-2 FFT in `Dsp`) is averaged over the frames. Take the peak in 300 to 4000 Hz, refined by parabolic interpolation. Also measure `levelOn` (key held), `levelOff` (2 s quiet) and `leak` (2 s with the band playing) as median envelopes. Report `snrDb = 20 log10(levelOn/levelOff)` and `leakOk = leak < 0.5·thrOn`.
- **Running.** Quadrature mix to baseband: `I = x·cos(2πf_b t)`, `Q = −x·sin(2πf_b t)`, each through two cascaded `Dsp.Biquad.lowpass(200 Hz, 0.707)`. Envelope `e = sqrt(I²+Q²)`; its rise is about 2 to 3 ms.
- **Hysteresis.** `thrOn = sqrt(levelOn·levelOff)`, `thrOff = 0.7·thrOn`. Debounce by dropping marks < 12 ms and spaces < 8 ms (merge them).
- **Events:** `onKey(boolean down, long sampleIndex)`.
- **AFC:** during key-down runs longer than 40 ms, `df = atan2(Q·I₋₁ − I·Q₋₁, I·I₋₁ + Q·Q₋₁)·rate/2π` averaged over the run, then `f_b += 0.1·df`.
- **Shifted output for K7:** decimate I/Q to 3200 Hz (every 5th sample at 16 kHz; at 48 kHz every 15th) and upconvert: `y = I·cos(2π·800t) − Q·sin(2π·800t)`. The buzzer is then exactly at 800 Hz with no images, whatever its pitch. Write `y` to a ring buffer of 12 s (`float[38400]`), with no allocation per block.

**`KeyDecoder.java`** takes mark and space durations in ms.
- **Fist profile** (`KeyDecoder.Fist5`, five numbers: `dot, dash, inner, letter, word` in ms), saved as a string in prefs. When one exists, it seeds the clusters; otherwise use the next bullet. `static Fist5 learn(List<Long> marks, List<Long> gaps, String knownText)` aligns your timings to the known text. It walks `Morse.parse(knownText)` element by element; if the mark count differs, it aligns by dynamic programming on dit/dah labels. It returns the mean of each class, for K6's "Learn my fist".
- **Initial dot** without a profile = `1200 / Profile.sendWpm`, then 2-cluster k-means on the log durations of the first 8 marks.
- **Adaptive clusters:** marks into `dotMean`/`dashMean`, gaps into `innerMean`/`letterMean`/`wordMean`. Classify against the geometric-mean boundaries, then EMA-update the chosen mean with α = 0.15. Constraints: `dash ≥ 2·dot`, `letter ≥ 2·inner`, `word ≥ 1.6·letter`.
- **Ambiguity:** an item within ±20% (in log) of its boundary is marked ambiguous, and both readings are kept in `Letter.alternatives`.
- **Rules for a straight key** (common learner faults, each with its own test):
  - A mark longer than `1.6·dashMean` is still a dash, not an error.
  - A gap shorter than `0.5·innerMean` is merged into the mark (bounce).
  - A letter gap below the boundary but with an unknown code on both sides: also try it as a letter gap (that is the alternatives mechanism, forced on).
  - A word gap is never inferred from one long gap of less than `1.3·letterMean` (hesitation inside a word).
  - A single isolated `E` or `T` between two letter gaps that makes the code unknown when joined: keep both readings.
- **Output:** `List<Letter>` (code, char/prosign or `*`, alternatives, start/end ms), a live string, and `wpm() = 1200/dotMean`.
- **Saved overs** (from K6): `static String timingsJson(...)` and `fromJson(...)` (marks/gaps in ms plus the text you meant), so real overs can become test data in `tests/data/fist/*.json`.
- **End of over:** the last token is `K KN BK SK AR` or `?` and silence ≥ 1.5 s; or silence ≥ `max(4 s, 3·wordMean)` in Patient manners (2.5 s in Normal).
- Keep the raw timings for K8.

**Test sound:** `tests/BuzzerSynth.java` (a test helper, not shipped). It renders `Keying` with a `Fist` as a buzzer: a square-ish wave (fundamental plus 2nd and 3rd harmonics at −10/−16 dB), 3 ms ramps, a room echo (comb at 23 ms, gain 0.3), white noise at a set SNR, optionally the band leaking at 600 Hz, and a door-slam click. A "learner" fist is made directly with the `Fist` constructor: `dah 0.75` (dashes 2.25 dots), `ditJitter 0.25`, `gapJitter 0.35`, `charGap 0.6`, `hesitate 0.2`.

**Tests** (old P9 list, unchanged thresholds):
- Keyer 5 to 30 WPM at 20 dB SNR: ≥99%. Straight key and bug at 12 to 22 WPM, 10 dB: ≥92%. Beginner at 20/8 Farnsworth: ≥90%, with word breaks in the right places.
- Noise, voice-like bursts and a click alone produce no letters.
- The pitch is found within 3% at 500, 800, 2400 and 3200 Hz. With the leak 12 dB below the buzzer, the decoder stays silent while the key is idle. Key-down is reported within 15 ms.
- The over ending is detected for all of `K KN BK SK`, and never inside a slow sender's word.
- **New:** the learner fist read through `KeyDecoder` + `Heard` against a fixed QSO script recovers call, report, name and QTH in ≥90% of 200 seeded runs.
- **New:** with a severe learner fist (`dah 0.67` = 2-dot dashes, `gapJitter 0.45`, `charGap 0.5`), `Fist5.learn` from 3 sends of the warm-up text, then `KeyDecoder` + `Heard`: call, report and name recovered in ≥85% of 200 runs. Without the profile, at least 10 points lower (this proves the profile helps).
- **New:** each straight-key rule above has a test that fails if the rule is removed.
- **New:** every file in `tests/data/fist/` (empty at first) is decoded and checked against its meant text with `Heard`. Callsign and report must match. The test reports the character error rate.
- **New:** the shifted output's spectral peak is within ±3 Hz of 800 Hz for buzzer pitches 500 to 3500 Hz (kept for K7).

## K6. Microphone, calibration and the key in the contact card (Android + UI)
- **`Mic`** is an inner class of `MainActivity`, running `AudioRecord` on its own thread. 16000 Hz mono PCM16, reading 20 ms blocks. Audio source `UNPROCESSED` when `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)` is `"true"`, else `VOICE_RECOGNITION`.
- It runs **only** while the Band tab is visible, a contact card is open (or warm-up runs) and "Send with: My key" is set. It is stopped in `onPause`. `FLAG_KEEP_SCREEN_ON` is set while it runs, and no microphone foreground-service type is needed.
- **Wiring:**
  - `KeyEar` events go to `CwService.engine.key(down)` (expose `engine` as a static volatile, like `band`) and to `KeyDecoder`.
  - The end of an over gives `Heard.parse(letters, partner.expect())` → `Band.userOver(...)`.
  - `Band.tickLive` runs every second.
  - While `liveJson().sending` is true, ignore decoded letters unless 3 or more marks arrive with an envelope ≥ 2× `thrOn` (break-in). In that case the partner stops at the end of its word: add `Band.breakIn()`, which truncates the current `Keying` at the next `wordStart`.
- **Permission:** add `RECORD_AUDIO` to the manifest. Ask for it the first time "My key" is chosen, with the sentence: "The sound is analysed on the phone and never stored or sent."
- **UI:**
  - A 3-step set-up from old P10 (hold 2 s, quiet 2 s, band 3 s). It shows the pitch found, the SNR and leak warnings, and the advice "use headphones, a different buzzer pitch, or a cable".
  - In the card: a key light, the line "You sent: …" filling live, and the typing line kept as a fallback.
  - **Learn my fist** (step 4 of the set-up, repeatable from Settings):
    - "Send this three times at your own pace": `CQ CQ DE {mycall} {mycall} K`, then `UR RST 599 NAME {myname}`, then `PARIS PARIS`.
    - Result: `Fist5.learn` → saved, plus one friendly line ("Your dashes are about 2.4 dots; I will read them that way").
  - **Warm up**: 60 s with no partner, showing what is read, and a score against a suggested text.
  - **"That's not what I sent"** button under "You sent". You type what you meant. The over's timings and the meant text are kept locally (up to 50, oldest dropped), with an **Export** action that shares the JSON. These are timings only, never audio. Exported files go into `tests/data/fist/` to improve the decoder, and they are the evidence for the K7 go/no-go check.
  - Help text "It does not hear my key".
- **README** permissions table: RECORD_AUDIO and its reason.
- **Tests:** a Playwright check with a fake bridge for the set-up steps and both leak outcomes, and `tests/ui.py` updated. The Android parts are compiled only by CI. State that they were not heard on a phone.
- **Delivery:** 2.7.0.

## K7. Contingency: neural decoding (DeepCW narrow)

**Go/no-go check.** After K6, the owner holds at least 5 key contacts and does one 60 s warm-up after each, with the fist profile learned. Build K7 only if one of these holds:
- the warm-up score stays below **85%** of characters;
- the partner had to ask again (a request or "SRI NO COPY") in more than **1 over in 4** because of decoding, not because something was missing;
- the owner judges it frustrating.

Before K7, try two cheap fixes (each a short Sonnet session):
1. Add the exported overs to `tests/data/fist/` and tune the `KeyDecoder` constants until they read.
2. Relax `Heard` thresholds for the Patient level.

If K7 is built, add Settings "Neural decoding: Off / On" (`neural`, off by default). Everything below applies only then.

Pin **ONNX Runtime 1.24.3**, the version web-deep-cw-decoder uses. Check on Maven Central that `com.microsoft.onnxruntime:onnxruntime-android:1.24.3` and `…:onnxruntime:1.24.3` (desktop) exist; if not, use the nearest later patch.

**Files:**
- `app/assets/deepcw_narrow.onnx` (1.2 MB) and `deepcw_narrow.json`, committed, with MIT notices in `THIRD_PARTY.md`.
- The AAR is **not** committed. A new `tools/fetch-ort.sh` downloads it to `third_party/` (gitignored) with a pinned SHA-256. It unpacks `classes.jar` and `jni/arm64-v8a/libonnxruntime.so` and `libonnxruntime4j_jni.so`.

**Pure Java parts** (in `tests/run.sh`), ported function by function from `examples/python/decode_morse.py`:
- `DeepCwPre`: reflect-pad 128, periodic Hann 256, hop 48. The DFT is taken only at bins `ceil(787.5/12.5)=63` to `floor(812.5/12.5)=65` (3 Goertzel-style sums per hop), as magnitudes. Then the noise normalisation exactly as in the reference, and log1p.
- `DeepCwText`: log-softmax, the 1-frame dilation, greedy CTC with blank 41, the space/word-boundary rule, and `PosteriorStitcher` (12 s window, 1 s step, left context 4 s, freeze guard 2 s, ramps). Constants are copied verbatim.

**Android part:** `NeuralReader` (Android-only). It loads the model from assets as `byte[]`, then `OrtEnvironment.getEnvironment().createSession(bytes, opts)` with `intraOpNumThreads=1`. A worker thread, every 1 s **and only if `KeyEar` saw key activity in the last 12 s**, takes the ring buffer, runs `DeepCwPre` → session → `DeepCwText` and publishes text. It is enabled only when the `neural` setting is on, the device ABI includes arm64-v8a and loading succeeded (catch `UnsatisfiedLinkError`/`OrtException`; on failure, show "Neural decoding not available on this phone").

**Combining:** at the end of an over, `Heard` gets both the KeyDecoder letters and the neural text. It scores each against `Expect` (sum of step-3 costs) and uses the lower one, with ties going to neural. Over-end detection always comes from KeyDecoder timing.

**`build.sh`:**
- `javac -classpath $J:third_party/ort/classes.jar`, and d8 adds `third_party/ort/classes.jar`.
- After `aapt2 link`, copy the two `.so` files to `$O/lib/arm64-v8a/` and add them with `(cd $O && zip -q u.apk classes.dex lib/arm64-v8a/*.so)`, before `zipalign -p 4`.
- `build.sh` requires `third_party/ort`: it stops with a clear message if the folder is missing. CI runs `tools/fetch-ort.sh` first. `NeuralReader` is the only class that imports `ai.onnxruntime`.

**Golden tests (CI only):** a new workflow step does `pip install onnxruntime==1.24.3 numpy`, writes 10 WAVs (3200 Hz) from `BuzzerSynth` through `KeyEar`'s shifted output, and runs `decode_morse.py --model model_narrow.onnx` → `tests/data/deepcw-golden/*.txt`. Then `tests/deepcw.sh` runs the Java pipeline with the desktop ORT jar and asserts the same text. It also reports CER for narrow vs wide on learner-fist audio. If narrow is more than 3 points worse, say so and let the owner decide about the 15 MB wide model.

**Stop rules:** if the Java spectrogram or posteriors differ from Python beyond 1e-4 after two honest attempts, stop and report. Report the APK size before and after, measured from CI.

**Delivery:** the next version after K8. State plainly: CPU load and battery not yet measured on a phone.

## K8. Feedback on sending
Old P11 as written, using `KeyDecoder` timings.

---

## Critical files
- New, pure: `Heard.java`, `Partner.java`, `KeyEar.java`, `KeyDecoder.java`, `Profile.java`. Test helper `tests/BuzzerSynth.java`, test data folder `tests/data/fist/`. Only if K7 is built: `DeepCwPre.java`, `DeepCwText.java`.
- New, Android: `Mic` inside `MainActivity.java`. Only if K7 is built: `NeuralReader.java`, `tools/fetch-ort.sh`.
- Edited:
  - `Qso.java` (visibility, `live`, `userOp`, null guards), `Band.java` (live station), `Engine.java` (`key`, duck, `send`), `Morse.java` (`letter`), `Dsp.java` (FFT)
  - `CwService.java` (static `engine`, settings), `MainActivity.java` (bridge, mic, lifecycle), `Db.java` (log), `app/assets/index.html` (card, set-up, Log tab), `AndroidManifest.xml` (RECORD_AUDIO, version)
  - `build.sh`, `.github/workflows/android.yml`, `tests/run.sh`, `tests/T.java`, `tests/ui.py`, `CLAUDE.md` (pure class list), `README.md`, `THIRD_PARTY.md`, `docs/PLAN.md`, `docs/WORK-PACKAGES.md`
- Reused: `Gen.say`, `react`, `basics`, `report`, `name`, `qth`, `bye73`, `reason`, `goodbye`, `greeting`, `rst` (Qso.java); `Keying.build` + `Fist.of` (test audio and partner sending); `Dsp.Biquad.lowpass/bandpass`; `Rx.gain`; `Prop.pathLossDb`; `Abbrev` table; the settings pattern.

## Verification
- After each package: `bash tests/run.sh` prints `0 failed`. For UI packages, a Node Playwright check with a fake `window.Android`. Push, and the `android` workflow is green.
- K4 on the phone: answer a CQ by typing, and complete a minimal contact and a chat. Ask `WX?` and `HOBBY?` and get answers. Send garbage three times and get kind help. Check the Log tab.
- K6 on the phone: calibrate with your buzzer (speaker, then headphones). Warm up, then a key contact. Watch the "You sent" line, the muting while keying and the end-of-over timing.
- After K6, run the K7 go/no-go check and record the result in `docs/PLAN.md`.
- K7 (only if built): the CI golden test passes. On the phone, switch neural on and compare "You sent" with it on and off. Note the APK size and how warm the phone gets after 10 minutes.

## Not covered / open
- The web runtime alternative (onnxruntime-web inside the WebView) is not chosen. It is the same weight class (WASM of about 10 MB, size not measured; the CDN was blocked here). It would need a virtual https origin for WASM loading and cannot be tested in Java on a PC.
- A pure-Java port of the Conformer network (+1.2 MB only, no runtime) is possible but too risky for a weaker model. It is a candidate for a later Fable/Opus package, using K7's golden tests as its safety net.
- Interpretation: "antenna occupation" is read as two topics, antenna and occupation (job). Both are covered.
