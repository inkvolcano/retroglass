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
)

# STILL 4 KB (need dedicated work — not in the simple loop):
#   fuse, bluemsx, picodrive  -> ndk-build compile errors, need patching
#   gearcoleco                -> no jni/Android.mk (different build layout)
#   mednafen_saturn           -> builds but extremely slow
#   mgba, melonds             -> CMake (use android.toolchain.cmake)
#   mupen64plus_next, vice_x64, puae -> make-based, per-core flags
#   ppsspp, play              -> large CMake projects with submodules

for entry in "${CORES[@]}"; do
  IFS='|' read -r name repo sub <<<"$entry"
  echo "=== $name ==="
  dir="$WORK/$name"
  [ -d "$dir" ] || git clone --depth 1 "$repo" "$dir"
  jni="$dir/$sub/jni"
  if [ ! -f "$jni/Android.mk" ]; then jni="$dir/jni"; fi
  if [ ! -f "$jni/Android.mk" ]; then echo "  no jni/Android.mk, skipping"; continue; fi
  ( cd "$jni" && "$NDK_BUILD" APP_ABI=arm64-v8a APP_PLATFORM=android-26 -j"$(nproc)" )
  so="$(find "$dir" -path '*/libs/arm64-v8a/*.so' | head -n1)"
  cp "$so" "$JNI_OUT/lib$name.so"
  echo "  -> lib$name.so"
done

echo "Done. Verify with: python scripts/check_16k.py"
