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
)
# => 23/32 systems Play-ready (15 rebuilt here + 8 already aligned from the buildbot).

# STILL 4 KB (need dedicated per-core work — build errors with NDK r28's clang or
# heavy CMake projects; not in this loop):
#   fuse            -> needs a generated config.h (autotools)
#   bluemsx         -> cascade of old-C errors (implicit decls, incompatible fn ptrs)
#   vice_x64, puae  -> std::auto_ptr removed in C++17 / other C++ errors
#   melonds (DS)    -> libc++ macro clash in its jni; use its CMake build instead
#   gearcoleco      -> no jni/Android.mk (make platform=android)
#   mednafen_saturn -> hangs on one heavy compilation unit with NDK r28
#   ppsspp (PSP), play (PS2) -> large CMake projects with submodules

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
  ( cd "$jni" && "$NDK_BUILD" APP_ABI=arm64-v8a APP_PLATFORM=android-26 -j"$(nproc)" )
  so="$(find "$dir" -path '*/libs/arm64-v8a/*.so' | head -n1)"
  cp "$so" "$JNI_OUT/lib$name.so"
  echo "  -> lib$name.so"
done

echo "Done. Verify with: python scripts/check_16k.py"
