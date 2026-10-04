#!/data/data/com.termux/files/usr/bin/bash
# Daymark build script - Termux only, no Gradle, no AndroidX.
# Pipeline: aapt2 compile -> aapt2 link -> kotlinc -> (javac) -> d8 -> zip -> apksigner

set -u

PROJECT="$HOME/Daymark"
WORK="$HOME/Daymark-build"
ANDROID_JAR="$HOME/android-framework/android.jar"
APPNAME="Daymark"
OUT_APK="$PROJECT/Daymark.apk"

echo "=== Daymark build ==="

if [ ! -f "$ANDROID_JAR" ]; then
    echo "[!] android.jar not found at $ANDROID_JAR"
    exit 1
fi

echo "[*] Cleaning $WORK"
rm -rf "$WORK"
mkdir -p "$WORK/classes" "$WORK/dex" "$WORK/apk"

# --- 1. aapt2 compile ---
echo "[*] aapt2 compile"
aapt2 compile --dir "$PROJECT/res" -o "$WORK/res.zip" || exit 1

# --- 2. aapt2 link ---
echo "[*] aapt2 link"
aapt2 link -o "$WORK/apk/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$PROJECT/AndroidManifest.xml" \
    "$WORK/res.zip" || exit 1

# --- 3. kotlinc ---
echo "[*] kotlinc"
STDLIB="$PREFIX/opt/kotlin/lib/kotlin-stdlib.jar"
if [ ! -f "$STDLIB" ]; then
    STDLIB="$(find "$PREFIX" -name 'kotlin-stdlib.jar' 2>/dev/null | head -n1)"
fi
if [ -z "$STDLIB" ] || [ ! -f "$STDLIB" ]; then
    echo "[!] kotlin-stdlib.jar not found. Install 'kotlin' via pkg."
    exit 1
fi
echo "    stdlib: $STDLIB"

KT_FILES="$(find "$PROJECT/src" -name '*.kt')"
if [ -z "$KT_FILES" ]; then
    echo "[!] no .kt files found under $PROJECT/src"
    exit 1
fi

kotlinc -J-Xmx1536m -J-Xss4m \
    -jvm-target 1.8 \
    -classpath "$ANDROID_JAR" \
    -d "$WORK/classes" \
    $KT_FILES || exit 1

# --- 4. javac (only if .java files exist) ---
JAVA_FILES="$(find "$PROJECT/src" -name '*.java')"
if [ -n "$JAVA_FILES" ]; then
    echo "[*] javac"
    javac -source 8 -target 8 -Xlint:-options \
        -classpath "$ANDROID_JAR:$STDLIB:$WORK/classes" \
        -d "$WORK/classes" \
        $JAVA_FILES || exit 1
else
    echo "[*] javac skipped (no .java files)"
fi

# --- 5. d8 ---
echo "[*] d8"
CLASS_FILES="$(find "$WORK/classes" -name '*.class')"
if [ -z "$CLASS_FILES" ]; then
    echo "[!] no .class files produced"
    exit 1
fi

d8 --lib "$ANDROID_JAR" \
   --min-api 26 \
   --output "$WORK/dex" \
   $CLASS_FILES "$STDLIB" || exit 1

# --- 6. add dex files to APK ---
echo "[*] Adding dex to APK"
FOUND_DEX=0
for dex in "$WORK/dex"/*.dex; do
    if [ -f "$dex" ]; then
        echo "    + $(basename "$dex")"
        zip -q -uj "$WORK/apk/base.apk" "$dex" || exit 1
        FOUND_DEX=1
    fi
done
if [ "$FOUND_DEX" -eq 0 ]; then
    echo "[!] d8 produced no .dex files"
    exit 1
fi

# --- 7. zipalign (optional) ---
if command -v zipalign >/dev/null 2>&1; then
    echo "[*] zipalign"
    zipalign -f 4 "$WORK/apk/base.apk" "$WORK/apk/aligned.apk" || exit 1
    APK_IN="$WORK/apk/aligned.apk"
else
    echo "[*] zipalign not present - skipping"
    APK_IN="$WORK/apk/base.apk"
fi

# --- 8. keytool (debug keystore) ---
KS="$HOME/.android/debug.keystore"
if [ ! -f "$KS" ]; then
    echo "[*] Generating debug keystore"
    mkdir -p "$HOME/.android"
    keytool -genkeypair \
        -keystore "$KS" \
        -storepass android -keypass android \
        -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" || exit 1
else
    echo "[*] Using existing debug keystore"
fi

# --- 9. apksigner sign ---
echo "[*] apksigner sign"
rm -f "$OUT_APK"
apksigner sign \
    --ks "$KS" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$OUT_APK" \
    "$APK_IN" || exit 1

# --- 10. apksigner verify ---
echo "[*] apksigner verify"
apksigner verify "$OUT_APK" || exit 1

echo ""
echo "=== BUILD OK ==="
ls -lh "$OUT_APK"
echo "APK: $OUT_APK"
