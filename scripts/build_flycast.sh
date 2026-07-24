#!/usr/bin/env bash
# Builds Flycast (Dreamcast / NAOMI / Atomiswave) from source for arm64 Android, with the
# one patch that makes it survive Android 11+.
#
# Why not the buildbot binary: the libretro CMake target never links libandroid, so the weak
# reference to ASharedMemory_create (core/linux/posix_vmem.cpp) resolves to null at runtime.
# The core then falls back to /dev/ashmem — removed in Android 11 — vmem allocation fails with
# errno 13, nvmem is disabled, and the dynarec segfaults in addrspace::write32 on the first
# guest RAM write. Diagnosed on a Galaxy Z Flip (Android 16), 2026-07-24; the app-side story is
# in docs/gap-analysis.md under "Known issue: Dreamcast".
#
# The patch (scripts/patches/flycast-android-sharedmem.patch) adds
# target_link_libraries(android) to the libretro Android branch. Upstream-worthy.
#
# NDK r28+ links 16 KB-aligned by default and -Wl,-z,max-page-size=16384 is passed anyway, so
# the output stays Play-compliant. Verify with scripts/check_16k.py afterwards.
#
# Windows note: clone to a SHORT path (C:\tmp\fc). Deep NDK object paths have hit MAX_PATH
# on this machine before.
set -euo pipefail

: "${ANDROID_SDK_ROOT:=$LOCALAPPDATA/Android/Sdk}"
NDK="$ANDROID_SDK_ROOT/ndk/28.2.13676358"
CMAKE="$ANDROID_SDK_ROOT/cmake/3.22.1/bin/cmake.exe"
NINJA="$ANDROID_SDK_ROOT/cmake/3.22.1/bin/ninja.exe"
SRC="${FLYCAST_SRC:-/c/tmp/fc}"
OUT="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/jniLibs/arm64-v8a"
PATCH="$(cd "$(dirname "$0")" && pwd)/patches/flycast-android-sharedmem.patch"

if [ ! -d "$SRC/.git" ]; then
  git clone --depth 1 https://github.com/flyinghead/flycast.git "$SRC"
fi
cd "$SRC"
# Pin the submodules the libretro build uses to the commits the tree expects — a shallow
# recursive init checks out branch tips instead, which is silent dependency drift.
git submodule update --init --recursive --force -- \
  core/deps/Vulkan-Headers core/deps/VulkanMemoryAllocator core/deps/glslang \
  core/deps/libchdr core/deps/asio core/deps/tinygettext core/deps/luabridge \
  core/deps/rcheevos core/deps/libjuice core/deps/libadrenotools

git apply --check "$PATCH" 2>/dev/null && git apply "$PATCH" && echo "patch applied" \
  || echo "patch already applied (or upstream fixed it — check manually)"

"$CMAKE" -S "$SRC" -B "$SRC/build" -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$NINJA" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
  -DCMAKE_BUILD_TYPE=Release -DLIBRETRO=ON \
  -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384"
"$NINJA" -C "$SRC/build" flycast_libretro

cp "$SRC/build/flycast_libretro.so" "$OUT/libflycast.so"
echo "installed $OUT/libflycast.so (source commit: $(git -C "$SRC" rev-parse --short HEAD))"
echo "now run: python scripts/check_16k.py"
