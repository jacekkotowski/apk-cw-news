#!/bin/bash
# Compiles the Android-free classes with the tests and runs them.
set -e
cd "$(dirname "$0")/.."
S=app/src/pl/cwnews; O=out_test; rm -rf $O; mkdir -p $O
javac -nowarn --release 11 -encoding UTF-8 -d $O $S/Morse.java $S/Timing.java $S/Keying.java $S/Dsp.java $S/Bed.java $S/Engine.java $S/TextNorm.java $S/FeedParser.java $S/PlaybackQueue.java tests/T.java 2>&1 | grep -v "^Picked up" || true
java -cp $O pl.cwnews.T 2>&1 | grep -v "^Picked up"
