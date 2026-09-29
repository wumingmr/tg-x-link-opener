#!/data/data/com.termux/files/usr/bin/bash
# 无 Gradle 构建：aapt2 -> javac -> d8 -> repack -> zipalign -> apksigner
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$ROOT/build"
PROJ_NAME="tg-x-link-opener"

FRAMEWORK_JAR="${FRAMEWORK_JAR:-/data/data/com.termux/files/usr/share/aapt/android.jar}"
API_JAR="$ROOT/libs/android-stub.jar"          # android API stub，仅编译期使用
XP_JAR="$ROOT/libs/libxposed-api-102.jar"      # libxposed modern API，仅编译期使用
KEYSTORE="${KEYSTORE:-$ROOT/debug.keystore}"
MIN_SDK=26
TARGET_SDK=35

fetch() { # fetch <url> <dest>
    if [ -f "$2" ]; then return 0; fi
    echo ">> downloading $2"
    curl -fsSL -o "$2" "$1"
}

mkdir -p "$ROOT/libs"
fetch "https://repo1.maven.org/maven2/com/google/android/android/4.1.1.4/android-4.1.1.4.jar" "$API_JAR"
if [ ! -f "$XP_JAR" ]; then
    echo ">> downloading libxposed api"
    curl -fsSL -o "$OUT.tmp.aar" "https://repo1.maven.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar"
    unzip -o -q "$OUT.tmp.aar" classes.jar -d "$OUT.tmp.aar.d"
    mv "$OUT.tmp.aar.d/classes.jar" "$XP_JAR"
    rm -rf "$OUT.tmp.aar" "$OUT.tmp.aar.d"
fi

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/apk"

echo ">> aapt2 compile"
aapt2 compile --dir "$ROOT/res" -o "$OUT/res.zip"

echo ">> aapt2 link"
aapt2 link -o "$OUT/base.apk" \
    -I "$FRAMEWORK_JAR" \
    --manifest "$ROOT/AndroidManifest.xml" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --version-code 1 \
    --version-name 1.0 \
    "$OUT/res.zip"

echo ">> javac"
# shellcheck disable=SC2046
javac -encoding UTF-8 -source 17 -target 17 -nowarn \
    -classpath "$API_JAR:$XP_JAR" \
    -d "$OUT/classes" \
    $(find "$ROOT/src" -name '*.java')

echo ">> d8"
d8 --min-api "$MIN_SDK" --lib "$API_JAR" --output "$OUT/dex" \
    $(find "$OUT/classes" -name '*.class')

echo ">> pack"
python3 "$ROOT/tools/repack.py" "$OUT/base.apk" "$OUT/dex" "$ROOT/resources" "$OUT/apk/unsigned.apk"

echo ">> zipalign + sign"
zipalign -f 4 "$OUT/apk/unsigned.apk" "$OUT/apk/aligned.apk"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -alias xlink -keyalg RSA -keysize 2048 \
        -validity 10000 -storepass android -keypass android \
        -dname "CN=TG X Link Opener, O=local, C=CN"
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --out "$ROOT/$PROJ_NAME.apk" "$OUT/apk/aligned.apk"
apksigner verify "$ROOT/$PROJ_NAME.apk"
echo ">> built: $ROOT/$PROJ_NAME.apk"
