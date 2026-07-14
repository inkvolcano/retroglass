#!/usr/bin/env bash
# Downloads the libretro cores RetroGlass bundles, from the official libretro
# nightly buildbot (arm64-v8a), into app/src/main/jniLibs/arm64-v8a/.
# These are GPL binaries and are intentionally not committed to the repo.
set -euo pipefail

BASE="https://buildbot.libretro.com/nightly/android/latest/arm64-v8a"
DIR="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$DIR"

# buildbot core name  ->  output library name (lib<name>.so)
declare -A CORES=(
  [fceumm]=fceumm
  [snes9x]=snes9x
  [genesis_plus_gx]=genesis_plus_gx
  [pcsx_rearmed]=pcsx_rearmed
  [gambatte]=gambatte
  [mgba]=mgba
  [mupen64plus_next_gles3]=mupen64plus_next
  [mednafen_pce_fast]=mednafen_pce_fast
  [mednafen_ngp]=mednafen_ngp
  [flycast]=flycast
  [play]=play
  [handy]=handy
  [stella2023]=stella2023
  [prosystem]=prosystem
  [mednafen_wswan]=mednafen_wswan
  [mednafen_vb]=mednafen_vb
  [ppsspp]=ppsspp
  [melonds]=melonds
  [opera]=opera
  [mednafen_saturn]=mednafen_saturn
  [picodrive]=picodrive
  [gearcoleco]=gearcoleco
  [freeintv]=freeintv
  [vecx]=vecx
  [pokemini]=pokemini
  [atari800]=atari800
  [bluemsx]=bluemsx
  [vice_x64]=vice_x64
  [puae]=puae
  [fuse]=fuse
  [cap32]=cap32
  [fbneo]=fbneo
)

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

for src in "${!CORES[@]}"; do
  out="${CORES[$src]}"
  echo "Fetching $src -> lib$out.so"
  curl -fsSL --retry 3 -o "$tmp/$src.zip" "$BASE/${src}_libretro_android.so.zip"
  unzip -oq "$tmp/$src.zip" -d "$tmp/$src"
  so="$(find "$tmp/$src" -name '*.so' | head -n1)"
  cp "$so" "$DIR/lib$out.so"
done

echo "Done. $(ls "$DIR" | wc -l) cores in $DIR"
