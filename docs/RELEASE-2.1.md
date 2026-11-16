# CW News 2.1

Draft notes for the GitHub release `v2.1`. Attach `CWNews.apk` from the `CWNews-apk` artifact of the latest successful GitHub Actions run on the commit being released.

## What is new

- Contacts on the Band tab are assembled from topic modules, so no two run the same way.
- Operators have personalities: casual, chatty, terse, beginner, and someone out on a summit or in a park. Personality sets speed, length and topics.
- New kinds of contact: skeds between friends, and park, summit and contest stations working several callers in a row.
- Things that happen on a real band: a missed call (`QRZ?`), a request to repeat name or QTH, a beginner asking `PSE QRS` and the other station slowing down, an interruption (`<AS>`), a sending error corrected with the error sign (`<HH>`), a third station calling right after the sign-off.
- Consistency:
  - The report follows how strong you hear the station.
  - GM, GA and GE follow the other station's local time.
  - Names fit the country, and nobody is called OM on a guess.
  - Weather fits the season and the time of day.
  - POTA references use the current country prefixes.

## Install

Android 8.0 or newer. Download `CWNews.apk` on the phone, open it and allow installs from that source. It installs over 2.0 if it is signed with the same key.

## State

- Built by GitHub Actions; the PC tests pass (27,894 checks).
- Version code 4, version name 2.1.
- Not yet tested on a phone.
