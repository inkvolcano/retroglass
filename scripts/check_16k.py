#!/usr/bin/env python3
"""Reports 16 KB-page alignment of the bundled arm64 cores.

Google Play (Android 15+) requires native libraries with LOAD segments aligned to
16 KB (0x4000). Cores built with NDK r28+ satisfy this; older buildbot binaries are
often 4 KB (0x1000). Run after fetch_cores.sh or build_cores_ndk.sh.
"""
import struct, glob, os

def load_align(path):
    with open(path, "rb") as f:
        data = f.read()
    if data[:4] != b"\x7fELF" or data[4] != 2:
        return None
    e_phoff = struct.unpack_from("<Q", data, 0x20)[0]
    e_phentsize = struct.unpack_from("<H", data, 0x36)[0]
    e_phnum = struct.unpack_from("<H", data, 0x38)[0]
    aligns = [
        struct.unpack_from("<Q", data, e_phoff + i * e_phentsize + 0x30)[0]
        for i in range(e_phnum)
        if struct.unpack_from("<I", data, e_phoff + i * e_phentsize)[0] == 1  # PT_LOAD
    ]
    return min(aligns) if aligns else 0

jni = os.path.normpath(os.path.join(
    os.path.dirname(__file__), "..", "app", "src", "main", "jniLibs", "arm64-v8a"))
ok, bad = [], []
for so in sorted(glob.glob(os.path.join(jni, "*.so"))):
    a = load_align(so)
    (ok if isinstance(a, int) and a >= 0x4000 else bad).append((os.path.basename(so), a))

for n, a in ok:
    print(f"  OK   {n}  (0x{a:x})")
for n, a in bad:
    print(f"  BAD  {n}  (0x{a:x})  <- rebuild with NDK r28+ for Play")
print(f"\n{len(ok)}/{len(ok)+len(bad)} cores are 16 KB-aligned.")
