#!/usr/bin/env bash
# Termux-friendly fallback build for environments without Gradle.
# The Gradle workflow remains the canonical CI build.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
PROJECT="$SCRIPT_DIR/app/src/main"
WORK="$SCRIPT_DIR/.daymark-build"
OUT_APK="$SCRIPT_DIR/Daymark.apk"
ANDROID_JAR="${ANDROID_JAR:-${ANDROID_HOME:-}/platforms/android-34/android.jar}"

echo "=== Daymark fallback build ==="
for tool in aapt2 kotlinc d8 apksigner zip; do
    command -v "$tool" >/dev/null 2>&1 || { echo "[!] Missing required tool: $tool"; exit 1; }
done
if [[ ! -f "$ANDROID_JAR" ]]; then
    echo "[!] android.jar not found at $ANDROID_JAR"
    echo "    Set ANDROID_JAR to an API 34 android.jar path."
    exit 1
fi

rm -rf "$WORK"
mkdir -p "$WORK/classes" "$WORK/r" "$WORK/dex" "$WORK/apk"

echo "[*] aapt2 compile"
aapt2 compile --dir "$PROJECT/res" -o "$WORK/res.zip"

echo "[*] aapt2 link"
aapt2 link -o "$WORK/apk/base.apk" \
    -I "$ANDROID_JAR" \
    --java "$WORK/r" \
    --manifest "$PROJECT/AndroidManifest.xml" \
    "$WORK/res.zip"

STDLIB="${KOTLIN_STDLIB:-}"
if [[ -z "$STDLIB" ]]; then
    STDLIB="$(find "${PREFIX:-/data/data/com.termux/files/usr}" -name kotlin-stdlib.jar -print -quit 2>/dev/null || true)"
fi
[[ -f "$STDLIB" ]] || { echo "[!] kotlin-stdlib.jar not found; set KOTLIN_STDLIB"; exit 1; }

echo "[*] compile generated resources"
R_FILES="$(find "$WORK/r" -name '*.java' -print)"
javac -source 8 -target 8 -Xlint:-options -classpath "$ANDROID_JAR" -d "$WORK/classes" $R_FILES

echo "[*] kotlinc"
KT_FILES="$(find "$PROJECT/kotlin" -name '*.kt' -print)"
[[ -n "$KT_FILES" ]] || { echo "[!] no Kotlin sources found"; exit 1; }
kotlinc -J-Xmx1536m -J-Xss4m -jvm-target 1.8 \
    -classpath "$ANDROID_JAR:$STDLIB:$WORK/classes" \
    -d "$WORK/classes" $KT_FILES

echo "[*] d8"
CLASS_FILES="$(find "$WORK/classes" -name '*.class' -print)"
d8 --lib "$ANDROID_JAR" --min-api 26 --output "$WORK/dex" $CLASS_FILES "$STDLIB"

echo "[*] package and sign"
for dex in "$WORK/dex"/*.dex; do zip -q -uj "$WORK/apk/base.apk" "$dex"; done
APK_IN="$WORK/apk/base.apk"
if command -v zipalign >/dev/null 2>&1; then
    zipalign -f 4 "$APK_IN" "$WORK/apk/aligned.apk"
    APK_IN="$WORK/apk/aligned.apk"
fi

KEYSTORE="${DAYMARK_KEYSTORE:-$SCRIPT_DIR/.daymark-debug.keystore}"
if [[ ! -f "$KEYSTORE" ]]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi
rm -f "$OUT_APK"
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey --out "$OUT_APK" "$APK_IN"
apksigner verify "$OUT_APK"
echo "=== BUILD OK ==="
ls -lh "$OUT_APK"
