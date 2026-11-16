#!/bin/bash
# Сборка APK без Gradle. Нужны пакеты Ubuntu: android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner
set -e
cd "$(dirname "$0")"
SDK=/usr/lib/android-sdk
JAR=$SDK/platforms/android-23/android.jar
KS=${KEYSTORE:-$HOME/.careshare-debug.keystore}
rm -rf build && mkdir -p build/classes build/gen
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -A assets -I $JAR -F build/unsigned.apk
javac -source 8 -target 8 -bootclasspath $JAR -classpath $JAR -d build/classes $(find src build/gen -name '*.java') 2>&1 | grep -v 'bootstrap\|^1 warning\|^warning' || true
dalvik-exchange --dex --output=build/classes.dex build/classes
(cd build && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f 4 build/unsigned.apk build/aligned.apk
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias careshare -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=CareShare" >/dev/null 2>&1
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out CareShare.apk build/aligned.apk
apksigner verify CareShare.apk && echo "Готово: $(pwd)/CareShare.apk"
