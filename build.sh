#!/bin/bash
# Builds CWNews.apk. Needs ANDROID_SDK_ROOT and signing.env (see README.md).
set -e
cd "$(dirname "$0")"
S=${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to your Android SDK}
[ -f signing.env ] || { echo "signing.env is missing, see README.md"; exit 1; }
. ./signing.env
BT=$S/build-tools/36.0.0; J=$S/platforms/android-36/android.jar; O=out; rm -rf $O; mkdir -p $O/classes $O/res
$BT/aapt2 compile --dir app/res -o $O/res/res.zip
$BT/aapt2 link -o $O/base.apk -I $J --manifest app/AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 36 -A app/assets $O/res/res.zip
javac -nowarn --release 11 -encoding UTF-8 -classpath $J -d $O/classes app/src/pl/cwnews/*.java 2>&1 | grep -v "^Picked up\|^Note" || true
[ -n "$(find $O/classes -name 'MainActivity.class')" ] || { echo "Java compile failed"; exit 1; }
$BT/d8 --release --lib $J --min-api 26 --output $O $(find $O/classes -name '*.class') 2>&1 | grep -v "^Picked up" || true
cp $O/base.apk $O/u.apk; (cd $O && zip -q u.apk classes.dex)
$BT/zipalign -f -p 4 $O/u.apk $O/a.apk
$BT/apksigner sign --ks "$KS_FILE" --ks-pass "pass:$KS_PASS" --out CWNews.apk $O/a.apk 2>&1 | grep -v "^Picked up" || true
$BT/apksigner verify CWNews.apk 2>&1 | grep -v "^Picked up\|WARNING" || true
ls -la CWNews.apk
