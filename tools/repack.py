#!/usr/bin/env python3
"""把 classes.dex 与 META-INF/xposed/* 合进 aapt2 link 产出的 APK。

用法: repack.py <aapt2产出的base.apk> <dex目录> <元数据根目录(resources/)> <输出apk>
"""
import os
import sys
import zipfile


def add_tree(zf, base, prefix=""):
    for root, _dirs, files in os.walk(base):
        for name in sorted(files):
            full = os.path.join(root, name)
            rel = os.path.relpath(full, base)
            arc = os.path.join(prefix, rel) if prefix else rel
            zf.write(full, arc)


def main():
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    src_apk, dex_dir, meta_root, out_apk = sys.argv[1:5]
    with zipfile.ZipFile(src_apk) as zin, zipfile.ZipFile(out_apk, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if item.filename.startswith("META-INF/xposed/"):
                continue
            zout.writestr(item, zin.read(item.filename))
        zout.write(os.path.join(dex_dir, "classes.dex"), "classes.dex")
        add_tree(zout, meta_root)
    print("packed " + out_apk)


if __name__ == "__main__":
    main()
