# CW News: plan

What is built, what comes next, and how each step will be designed and checked. Updated whenever a step ships.

## Start here (next session)

**State on 2026-11-19: version 2.9.2 is on `main` and released.** The typed and keyed contact (K1 to K6) is built and the owner has listened to 2.6 to 2.8 on a phone and found it works well; since then 2.8.0 fixed the receiver (normal-width filter, slow AGC) and made the band always busy, 2.8.1 made summer static the default weather (chosen in Settings), 2.9.0 added the chips for a whole contact by touch and 2.9.1 smaller buttons and a temperature. **Not built:** K7 (neural decoding, only if a go/no-go check fails) and K8 (feedback on sending). **Known gap:** K5's decoder misreads a very sloppy hand (see Known gaps). **Not tested on a phone:** the one-tap key set-up with a real microphone, the key through the microphone in a real contact, the 2.9.x layout. **Next:** the owner's field test with the key, saved overs ("That's not what I sent" and Export) into `tests/data/fist/`, then an element-level decoder (a stronger model) and K8.
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

- **K5, a learner's hand (stopped after two attempts, as the rules say).** `KeyEar` and `KeyDecoder` meet every other listed test on synthetic sound: keyer 5 to 30 WPM, straight key and bug at 10 dB, a beginner at 20/8 (word breaks right in 7 of 8 runs), key times within 15 ms, noise, voice and door slam silent, the speaker leak, the end of an over, the five straight-key rules, the 800 Hz shifted copy. Two targets are not met: with a learner's hand (dashes of 2.25 dots, letter gaps about as long as a dash) call, report, name and QTH are all recovered in only 43 of 200 runs (target 180), and with the severe hand 21 of 200 even with a learned profile (target 170). The profile does not help, because the decoder already clusters the whole over; it would matter for short overs. Per field the first hand gives call 72%, report 50%, name 64%, QTH 82%: timing alone cannot separate such dashes from dots (the overlap is about 5% per mark), so this needs a decoder that uses what the partner expects at element level, not a threshold change. Run them with `java -cp out_test pl.cwnews.T targets`. Until then K6 works for a tidy hand and for typed overs.

- 2.1 to 2.4 have not been tested on a phone, and the new audio has not been listened to by anyone.
- `tests/ui.py` needs the Python Playwright module, which is missing in the cloud session.
- SOTA region codes are plausible but unchecked against the real summit lists.
- POTA prefixes outside the US follow the ISO country scheme by inference; only the US change (`K-` to `US-`) is confirmed.
- The propagation model is simple and was tuned for Europe; see the limits under 2.4.
