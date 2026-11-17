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
| 2.4 | A band that follows the clock: propagation, your region, band geography, activity by hour and weekday, contest weekends | Built by CI, not released |

Versions 2.1 to 2.4 are on branch `ccr-e3a1d112-4kb7si`, not on `main`. None of them has been run on a phone, and nobody has listened to the new audio.

## To finish 2.1 to 2.4

1. Install the 2.4 build on a phone and listen for ten minutes on the Band tab. Things to check:
   - **Sound.** With AGC on, the hiss drops behind a strong station and comes back between words. No crackle, dropouts or stuck tones.
   - **Filter.** Narrow leaves one station and less noise; Wide lets several through at different pitches. Switching makes a short gap, not a click.
   - **Fists.** Operators sound different from each other; the news bulletin stays machine-perfect.
   - **Tuning.** The knob feels right: one turn is 1 kHz, a flick coasts, and there is a light click in the fingers every 100 Hz.
   - **Waterfall and meter.** Lines break up with the keying, the meter moves with the station you hear, and the screen stays smooth.
   - **The clock.** Set your region in Settings. By day you should hear only stations within about 1500 km; after dark some DX, and fewer near neighbours.
   - **Battery and heat** after half an hour with the Band tab open, since the waterfall redraws 25 times a second.
2. Decide what to tune by ear: filter widths, how hard the AGC works, how strong the fists are.
3. Merge the branch to `main` and publish a release with the APK. Draft notes are in `docs/RELEASE-2.4.md`.

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

## Built in 2.4: a band that follows the clock

- **Your region** in Settings: Europe (the default), North America east or west, Japan, Australia and New Zealand, South America, southern Africa.
- **Propagation (`Prop`).** Path loss comes from distance and from how much of the path is in daylight. By day the band carries a few hundred to about 1500 km; at night long paths open and stations within about 500 km drop into the skip zone.
- **Who is on.** Stations are picked so that you can hear at least one side. Big countries come up more often, and places where it is the middle of the night less often.
- **One-sided contacts.** The other station may be too far, or too near at night. It is not played, and the text view says it is not heard here.
- **Reports** now describe the path between the two stations. This is often close to what you hear, but no longer always.
- **Band geography.** Contest and fast operators near the bottom, park, summit and QRP stations around 7030, beginners at the top.
- **Activity.** The activity setting is the evening level; nights and working hours are quieter, weekends busier.
- **Contest weekends.** A small calendar knows CQ WW CW, ARRL DX CW, CQ WPX CW, IARU HF, WAE DX CW and SP DX. On those days most stations are in the contest and the Band tab says so.
- **Limits.** The model was tuned for Europe. The station table has few countries outside Europe, so other regions hear a thinner band; from Japan by day there is only Japan. Seasons change the hours of darkness but not the skip distance.

## What comes next

Three releases. The exact specification of every package is in `docs/WORK-PACKAGES.md`; this section gives the overview, the order and who builds what.

| Release | Package | What you get | Built by | Status |
|---|---|---|---|---|
| **2.5 Listen better** | P1 | Jump to the strongest signal when a contact ends; a sleep timer | Sonnet | Built in 2.5 |
| | P2 | CW and CW-R: choose which side of the signal you listen on | Sonnet | Built in 2.5 |
| | P3 | Passband shift, peak filter, noise blanker, AGC Slow or Fast | Sonnet, with care | Skipped: two listed tests contradict each other (see below) |
| | P4 | Weather on the band: thunderstorm static, a storm nearby, aurora, solar flare fade-out | Sonnet | Skipped: its blanker test needs P3 |
| | P5 | Real space weather from NOAA (optional, off by default) | Sonnet | Skipped: needs P4 |
| **2.6 Your first contacts** | P6 | The forgiving partner: the conversation logic, live stations, your transmitter | **Fable** | Not started |
| | P7 | Your station (call, name, QTH, rig) and a logbook | Sonnet | Not started |
| | P8 | Taking part by typing, with hints and ready-made phrases | Sonnet | Not started |
| **2.7 Your own key** | P9 | The phone hears your key's buzzer through the microphone and reads your Morse | **Fable** | Not started |
| | P10 | Microphone set-up screens, warm-up, "You sent" line | Sonnet | Not started |
| | P11 | Kind feedback on your sending | Sonnet | Not started |
| After each release | | Version, README, release notes, plan status | Haiku | |

**Release 2.5 outcome.** P1 and P2 are built. P3 was stopped on the rule "do not weaken a test": the existing `receiver()` test needs the noise back within 3 dB about 0.3 s after a strong signal, while the new test needs Slow AGC to take more than 0.4 s. Slow release times of 0.5, 0.42 and 0.35 s each failed one of the two (0.45, 0.30 and 0.30 s measured). Separately, the noise blanker as specified (replace a sample that differs from its neighbours' average by more than 4.5 times the usual amount) removed only about 30% of the static spikes, not 70%: the likely cause (not verified) is that the sample before a spike has the spike as a neighbour and is replaced too, which spreads the spike instead of removing it. A stronger model should choose which test gives way and judge each sample by its difference from the nearer neighbour only. P4 and P5 were not started because P4's blanker test and P5's dependency need P3.

Nine of the eleven packages are for Sonnet. Fable is needed for two.

## Which model builds what, and why

- **Fable for P6 and P9.**
  - P6 decides how a partner reacts to anything you might send. It also reworks the contact generator without changing the contacts it produces, and adds your transmitter to the audio path. The design matters more than the typing, and a wrong design would make contacts frustrating.
  - P9 is signal processing that has to work in a real room with an unknown buzzer, plus a decoder that must read a learner's uneven timing.
- **Sonnet for the rest.** Each of those packages names the classes, methods, formulas and tests. The tests are the safety net: a package is not done until they pass.
- **Sonnet "with care" on P3.** It edits the receiver's audio code. The formulas are given, and the package says to stop and hand over if a listed test cannot be made to pass.
- **Haiku for housekeeping.**
- **A middle option.** Opus 5.5 costs half of Fable. It might manage P6 and P9, but it has not been tried on this project, so the plan assumes Fable.

Relative cost, from the API list prices per million tokens (input / output) as of early November 2026. A subscription plan may count usage differently, but the ratios are a fair guide.

| Model | Input | Output | Compared with Sonnet |
|---|---|---|---|
| Fable 5.2 | $8.00 | $40.00 | 4 times |
| Opus 5.5 | $4.00 | $20.00 | 2 times |
| Sonnet 5.5 | $2.00 | $10.00 | 1 |
| Haiku 5.5 | $0.10 | $0.50 | one twentieth |

Three habits keep the cost down:

1. **One package per session.** Start fresh, name the package, and let the model read only what it needs.
2. **Switch the model before you start**, with `/model`, not in the middle of a package.
3. **Use Fable for a short review, not a rebuild**, if a Sonnet package sounds wrong on the phone. Describe what you hear and ask for the cause.

This assignment is a judgement. No package has been built by Sonnet on this project yet; P1 is a cheap first test of how well the specifications work.

## What learners say they want

Searched before writing the packages. These are recurring themes from clubs, operators' write-ups and a podcast, not a survey.

| What comes up | Where it went in the plan |
|---|---|
| "Key fright": fear of mistakes, strongest just before the first contact. The advice is that you do not need to be perfect to make a contact, and the fear fades after a few. | The forgiving partner (ten rules in the work packages): nothing you send ends a contact, and there is no score during one. |
| Praying the other operator sends slowly enough. | The partner answers at your speed or slower and never speeds up. |
| Receiving is harder than sending; copy in your head; let a missed letter go. | The partner sends important things twice. Text is shown after each over, as a check on what you copied by ear. |
| Learn letters as sounds at about 20 WPM with wide gaps (20/5), not slow letters. | The partner widens the gaps before it slows the letters. |
| Park and summit activators are patient, and their contacts are short. | An activator is the first and easiest kind of contact offered. |
| Start with the common phrases: CQ, DE, RST, 73. | The hint line and the ready-made phrase buttons. |
| Going on the air cold causes errors. | A one-minute warm-up before a contact (P10). |
| Sending practice with a timing chart and tolerant scoring exists as a web page that listens through the microphone to a practice oscillator. | P9 to P11 use the same idea. The same source advises a cable when the room is noisy, and the set-up screen says so. |

Sources:
- [Jitters, Anxiety, and your first QSO (ditdit.fm)](https://ditdit.libsyn.com/jitters-anxiety-and-your-first-qso)
- [Controlling Frustration and Nervousness (CWops)](https://cwops.org/wp-content/uploads/2023/06/Controlling-Frustration-and-Nervousness.pdf)
- [My CW Journey, AF4O](https://uparc.org/wp-content/uploads/2026/05/My-CW-Journey-AF4O-PDF.pdf)
- [Learning CW for the beginner: hear the music, not the math](https://hamradio.my/learning-cw-for-the-beginner-amateur-radio-operator-hear-the-music-not-the-math/)
- [How to start your first CW QSO](https://hamradio.my/?p=9414)
- [Common CW mistakes and how to fix them](https://morsecodegenerator.pages.dev/blog/common-cw-mistakes-and-how-to-fix-them/)
- [Long Island CW Club: FAQ](https://longislandcwclub.org/faq/) and [Morse code training](https://longislandcwclub.org/morse-code-training/)
- [Sending Practice, experimental (morsecode.world)](https://morsecode.world/labs/sending-practice/)
- [Mic-based sending trainer (rfconsult.uk)](https://rfconsult.uk/morse)

Sources for the receiver and the weather packages:
- [Yaesu FT-950 manual, CW Reverse](https://www.manualslib.com/manual/381794/Yaesu-Ft-950.html?page=77) and [Icom IC-7000 manual, CW Reverse](https://www.manualslib.com/manual/75722/Icom-Ic-7000.html?page=52)
- [Summer thunderstorms (Handiham)](https://handiham.org/remotebase/?p=587) and [QRN and QRM, how to deal with it (Essex Ham)](https://www.essexham.co.uk/help-advice/qrnqrm-how-to-deal-with-it)
- [Adding a DSP noise blanker (ZL2IK)](https://zl2ik.blogspot.com/2016/01/things-i-have-learned-about-spectrumlab_9.html)
- [HF systems: effects of ionospheric disturbances (Australian Space Weather Services)](https://www.sws.bom.gov.au/HF_Systems/6/2/2)
- [D-layer absorption (Ham Radio School)](https://www.hamradioschool.com/articles/d-layer-absorption.html)
- [NOAA planetary K index](https://swpc.noaa.gov/products/planetary-k-index)
- [Android foreground service types](https://developer.android.com/guide/components/fg-service-types)

## Decisions for you

None of these blocks P1 to P5.

1. **Order.** The table follows the order you listed. The first-contacts release (2.6) could come before the weather package (P4) if you want to take part sooner.
2. **Your callsign.** Real or made up, for the contacts in the app (needed for P7).
3. **Microphone.** The app would ask for microphone permission. The sound is analysed on the phone and never stored or sent. P9 needs your agreement to add it.
4. **Headphones.** With the phone's speaker, the microphone may hear the band and mistake it for your key. The set-up screen checks this; headphones are the sure fix.
5. **Real space weather (P5).** It makes one small request to NOAA when feeds refresh. Off unless you turn it on.
6. **Filter controls (P3).** Four new controls is a lot for one screen. Say if you want fewer.

## Later

- Recurring stations: the same operator returns on later days with the same rig and QTH, and greets you by name once you have worked them.
- Copy practice: after listening to a contact, fill in call, name, QTH and report; the app scores it against what was sent.
- Awards from the logbook: countries, QRP contacts, summits and parks.
- Pile-up mode: you are the park activator and stations call you.
- A touch key or paddle on the screen, for when there is no key at hand. It needs a delay measurement on the phone first.
- Nets and round tables; a DX station working split.
- Real weather for the operators' towns.

## How every step is finished

1. `bash tests/run.sh` passes on a PC.
2. GitHub Actions builds `CWNews-apk` from the pushed commit.
3. Version code and name are raised.
4. You install it and listen on a phone. A step is not done before that.
5. This plan and the README are updated.

## Known gaps

- 2.1 to 2.4 have not been tested on a phone, and the new audio has not been listened to by anyone.
- `tests/ui.py` needs the Python Playwright module, which is missing in the cloud session.
- SOTA region codes are plausible but unchecked against the real summit lists.
- POTA prefixes outside the US follow the ISO country scheme by inference; only the US change (`K-` to `US-`) is confirmed.
- The propagation model is simple and was tuned for Europe; see the limits under 2.4.
