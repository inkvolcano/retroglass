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
#
# On Windows, two things will waste an afternoon if you do not know them:
#
#   * Build from a SHORT path (C:	mp\<core>, not a deep temp dir). ndk-build appends
#     obj/local/arm64-v8a/objs/... to every source path, and past MAX_PATH it dies with an
#     access violation (0xC0000005) and a zero-byte log - no error message at all. It looks
#     like a toolchain crash; it is the path length.
#   * The NDK ships only ndk-build.cmd, and invoking it from git-bash segfaults. Drive it
#     through cmd.exe.
#
# Per-core flags matter as much as the alignment. mupen64plus_next needs GLES3=1: without it
# the makefile falls through to the GLES2 path, and that core asks the frontend for an
# OPENGLES2 context, gets ES 3.x, and renders a black screen at a perfect 60fps. Check the
# result links libGLESv3 and not libGLESv2 before trusting it.
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
  "opera|https://github.com/libretro/opera-libretro|."
  "pokemini|https://github.com/libretro/PokeMini|."
  "stella2023|https://github.com/libretro/stella|."
  "mgba|https://github.com/libretro/mgba|."
  # NOTE: needs GLES3=1 - see the header. The plain loop below builds the GLES2
  # variant, which renders black.
  "mupen64plus_next|https://github.com/libretro/mupen64plus-libretro-nx|."
  "picodrive|https://github.com/libretro/picodrive|."
  "gearcoleco|https://github.com/drhelius/Gearcoleco|."
)
# => 20/32 build with the plain loop above. Four more need extra flags/prep — run
# them by hand with APP_SHORT_COMMANDS=true (all verified 16 KB-aligned):
#                -Wno-incompatible-function-pointer-types -Wno-int-conversion"
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
# Saturn — the former holdout, now DONE:
#   mednafen_saturn -> ndk-build hung on one heavy compilation unit with NDK r28's default
#                      optimizer; building with APP_CFLAGS=-O1 APP_CPPFLAGS=-O1 dodges the
#                      hang and still comes out 16 KB-aligned (0x4000). ~10 MB .so.
# => 32/32 systems Play-ready. 🎉

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
