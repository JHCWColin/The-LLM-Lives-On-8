#!/usr/bin/env bash
# 交叉编译 libllama.so（armeabi-v7a 与 arm64-v8a）
# 用法: ./build_native.sh [NDK路径] [llama.cpp源码目录]
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
NDK="${1:-$HOME/android-ndk-r25c}"
LLAMA_DIR="${2:-$HERE/../llamacpp}"

if [ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]; then
    echo "错误: 找不到 NDK toolchain: $NDK/build/cmake/android.toolchain.cmake"
    exit 1
fi

cd "$HERE/jni"

for ABI in armeabi-v7a arm64-v8a; do
    echo ">>> 编译 $ABI"
    EXTRA=""
    [ "$ABI" = "armeabi-v7a" ] && EXTRA="-DANDROID_ARM_NEON=ON"
    rm -rf "build-$ABI"
    cmake -B "build-$ABI" \
        -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM=android-23 \
        -DCMAKE_BUILD_TYPE=Release \
        $EXTRA \
        -DLLAMA_DIR="$LLAMA_DIR" \
        .
    cmake --build "build-$ABI" -j "$(nproc)"
    "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded \
        "build-$ABI/libllama.so"

    # 复制到 jniLibs
    DST="../app/src/main/jniLibs/$ABI"
    mkdir -p "$DST"
    cp "build-$ABI/libllama.so" "$DST/libllama.so"
    echo ">>> 完成 $ABI -> $DST/libllama.so"
done

echo "全部完成"
