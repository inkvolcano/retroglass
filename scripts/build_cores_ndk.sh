#!/usr/bin/env bash
# Builds libretro cores FROM SOURCE with a current Android NDK (r28+), which
# produces 16 KB-page-aligned .so files — required for Google Play (Android 15+).
#
# The prebuilt cores on the libretro nightly buildbot are (as of mid-2026) largely
# 4 KB-aligned because that CI still uses NDK r27 where 16 KB alignment is opt-in
# per core. Rebuilding with r28 aligns them by default. Verified on snes9x and
# genesis_plus_gx (both flip from 0x1000 to 0x4000 LOAD alignment).
#
# Requires: ANDROID_NDK_HOME pointing at NDK r28+, git.
# Output goes to app/src/main/jniLibs/arm64-v8a/lib<name>.so
set -euo pipefail

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to an NDK r28+ install}"
NDK_BUILD="$ANDROID_NDK_HOME/ndk-build"
JNI_OUT="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/jniLibs/arm64-v8a"
WORK="${WORK:-$(mktemp -d)}"
mkdir -p "$JNI_OUT" "$WORK"

# name | git repo | path to the folder containing jni/Android.mk (relative to repo root)
# These 12 build cleanly via the standard ndk-build path and come out 16 KB-aligned
# (verified). Together with the 8 cores the buildbot already ships aligned (fceumm,
# gambatte, pcsx_rearmed, flycast, fbneo, mednafen_pce_fast, mednafen_ngp, prosystem)
# that is 20/32 systems Play-ready.
CORES=(
  "snes9x|https://github.com/libretro/snes9x|libretro"
  "genesis_plus_gx|https://github.com/libretro/Genesis-Plus-GX|libretro"
  "handy|https://github.com/libretro/libretro-handy|."
  "vecx|https://github.com/libretro/libretro-vecx|."
  "freeintv|https://github.com/libretro/FreeIntv|."
  "mednafen_wswan|https://github.com/libretro/beetle-wswan-libretro|."
  "mednafen_vb|https://github.com/libretro/beetle-vb-libretro|."
  "atari800|https://github.com/libretro/libretro-atari800|."
  "cap32|https://github.com/libretro/libretro-cap32|."
  "opera|https://github.com/libretro/opera-libretro|."
  "pokemini|https://github.com/libretro/PokeMini|."
  "stella2023|https://github.com/libretro/stella|."
  "mgba|https://github.com/libretro/mgba|."
  "mupen64plus_next|https://github.com/libretro/mupen64plus-libretro-nx|."
  "picodrive|https://github.com/libretro/picodrive|."
  "puae|https://github.com/libretro/libretro-uae|."
  "gearcoleco|https://github.com/drhelius/Gearcoleco|."
)
# => 20/32 build with the plain loop above. Four more need extra flags/prep — run
# them by hand with APP_SHORT_COMMANDS=true (all verified 16 KB-aligned):
#   vice_x64  (github.com/libretro/vice-libretro):   APP_CPPFLAGS="-std=gnu++14 -fpermissive"
#   bluemsx   (github.com/libretro/blueMSX-libretro): APP_CFLAGS="-Wno-implicit-function-declaration \
#                -Wno-incompatible-function-pointer-types -Wno-int-conversion"
#   fuse      (github.com/libretro/fuse-libretro):    first copy the config headers the Makefile's
#                `cp` can't create on Windows: src/config_fuse.h -> fuse/config.h and
#                src/config_libspectrum.h -> libspectrum/config.h
# => 28/32 systems Play-ready total.

# Built outside this loop (all verified 16 KB-aligned) — see notes:
#   melonds (DS)  -> ndk-build with APP_SHORT_COMMANDS=true APP_CPPFLAGS=-DHAVE_WIFI APP_CFLAGS=-DHAVE_WIFI,
#                    plus a 1-line patch to src/dolphin/CommonFuncs.cpp so Android takes the GNU
#                    strerror_r branch: `#if defined(__ANDROID__) || (defined(__GLIBC__) && ...)`
#   ppsspp (PSP)  -> CMake: clone --recurse-submodules; configure with
#                    -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake
#                    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DLIBRETRO=ON ; build -> ppsspp_libretro_android.so
#   play (PS2)    -> same CMake flow with -DBUILD_LIBRETRO_CORE=ON -DBUILD_PLAY=OFF ; the test target
#                    (CodeGenTestSuite) fails but play_libretro_android.so links fine before it. (PS2 is experimental.)
#
# STILL 4 KB — the lone holdout:
#   mednafen_saturn -> ndk-build hangs on one heavy compilation unit with NDK r28's optimizer;
#                      retry with APP_CFLAGS=-O1 APP_CPPFLAGS=-O1 to dodge it.
# => 31/32 systems Play-ready.

for entry in "${CORES[@]}"; do
  IFS='|' read -r name repo sub <<<"$entry"
  echo "=== $name ==="
  dir="$WORK/$name"
  # --recurse-submodules matters: several cores (e.g. picodrive -> libchdr) fail
  # to build without their submodules.
  [ -d "$dir" ] || git clone --depth 1 --recurse-submodules "$repo" "$dir"
  # Find a jni/Android.mk anywhere in the tree (some cores nest it, e.g. mgba).
  jni="$(dirname "$(find "$dir" -path '*/jni/Android.mk' | head -n1)" 2>/dev/null)"
  if [ -z "$jni" ] || [ ! -f "$jni/Android.mk" ]; then echo "  no jni/Android.mk, skipping"; continue; fi
  # APP_SHORT_COMMANDS avoids the Windows link command-length limit for big cores
  # (harmless elsewhere).
  ( cd "$jni" && "$NDK_BUILD" APP_ABI=arm64-v8a APP_PLATFORM=android-26 \
      APP_SHORT_COMMANDS=true -j"$(nproc)" )
  so="$(find "$dir" -path '*/libs/arm64-v8a/*.so' | head -n1)"
  cp "$so" "$JNI_OUT/lib$name.so"
  echo "  -> lib$name.so"
done

echo "Done. Verify with: python scripts/check_16k.py"
