#!/usr/bin/env bash
# 构建并签名 APK
# 用法: ./build_apk.sh [gradle路径] [sdk目录] [keystore密码]
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
GRADLE="${1:-$HOME/gradle-8.7/bin/gradle}"
SDK="${2:-$HERE/../sdk}"
KS_PASS="${3:-ereader123}"

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export JAVA_HOME="${JAVA_HOME:-$(dirname $(dirname $(readlink -f $(which java))))}"

echo "sdk.dir=$SDK" > "$HERE/local.properties"

cd "$HERE"

# 若无 keystore 则生成
if [ ! -f ereader.keystore ]; then
    keytool -genkeypair -v -keystore ereader.keystore -alias ereader \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -dname "CN=EInkChat, OU=Dev, O=EInk, L=Beijing, ST=Beijing, C=CN"
fi

echo ">>> Gradle 打包"
"$GRADLE" assembleRelease --no-daemon

APK_UNSIGNED="$HERE/app/build/outputs/apk/release/app-release-unsigned.apk"
APK_ALIGNED="$HERE/app/build/outputs/apk/release/app-release-aligned.apk"
APK_OUT="$HERE/app/build/outputs/apk/release/EInkChat.apk"
BT="$SDK/build-tools/34.0.0"

"$BT/zipalign" -f -p 4 "$APK_UNSIGNED" "$APK_ALIGNED"
"$BT/apksigner" sign --ks ereader.keystore \
    --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --out "$APK_OUT" "$APK_ALIGNED"
"$BT/apksigner" verify "$APK_OUT"

echo ">>> 完成: $APK_OUT"
