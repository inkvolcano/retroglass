# Downloads the libretro cores RetroGlass bundles, from the official libretro
# nightly buildbot (arm64-v8a), into app/src/main/jniLibs/arm64-v8a/.
# These are GPL binaries and are intentionally not committed to the repo.
$ErrorActionPreference = "Stop"
$base = "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a"
$jni = Join-Path $PSScriptRoot "..\app\src\main\jniLibs\arm64-v8a"
New-Item -ItemType Directory -Force $jni | Out-Null

# buildbot core name => output library name (lib<name>.so)
$cores = [ordered]@{
  "fceumm" = "fceumm"; "snes9x" = "snes9x"; "genesis_plus_gx" = "genesis_plus_gx"
  "pcsx_rearmed" = "pcsx_rearmed"; "gambatte" = "gambatte"; "mgba" = "mgba"
  "mupen64plus_next_gles3" = "mupen64plus_next"; "mednafen_pce_fast" = "mednafen_pce_fast"
  "mednafen_ngp" = "mednafen_ngp"; "flycast" = "flycast"; "play" = "play"; "handy" = "handy"
  "stella2023" = "stella2023"; "prosystem" = "prosystem"; "mednafen_wswan" = "mednafen_wswan"
  "mednafen_vb" = "mednafen_vb"; "ppsspp" = "ppsspp"; "melonds" = "melonds"; "opera" = "opera"
  "mednafen_saturn" = "mednafen_saturn"; "picodrive" = "picodrive"; "gearcoleco" = "gearcoleco"
  "freeintv" = "freeintv"; "vecx" = "vecx"; "pokemini" = "pokemini"; "atari800" = "atari800"
  "bluemsx" = "bluemsx"; "vice_x64" = "vice_x64"; "puae" = "puae"; "fuse" = "fuse"
  "cap32" = "cap32"; "fbneo" = "fbneo"
}

$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("cores_" + [System.Guid]::NewGuid().ToString("N").Substring(0,8))
New-Item -ItemType Directory -Force $tmp | Out-Null
try {
  foreach ($src in $cores.Keys) {
    $out = $cores[$src]
    Write-Host "Fetching $src -> lib$out.so"
    $zip = Join-Path $tmp "$src.zip"
    curl.exe -fsSL --retry 3 -o $zip "$base/${src}_libretro_android.so.zip"
    Expand-Archive $zip -DestinationPath (Join-Path $tmp $src) -Force
    $so = Get-ChildItem (Join-Path $tmp $src) -Filter *.so | Select-Object -First 1
    Copy-Item $so.FullName (Join-Path $jni "lib$out.so") -Force
  }
  Write-Host "Done. $((Get-ChildItem $jni -Filter *.so).Count) cores in $jni"
} finally {
  Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}
