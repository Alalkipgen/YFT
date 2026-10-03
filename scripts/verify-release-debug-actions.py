#!/usr/bin/env python3
"""Reject the debug-only crash action in a production APK, without printing DEX contents."""

import re
import sys
import zipfile

DEBUG_MARKERS = (b"Crash now", b"YFT debug test crash")


def verify(apk):
    with zipfile.ZipFile(apk) as archive:
        entries = [name for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name)]
        if not entries:
            raise ValueError("APK has no DEX files")
        for name in entries:
            data = archive.read(name)
            if any(marker in data for marker in DEBUG_MARKERS):
                raise ValueError("Debug crash action is present in the release APK")


if __name__ == "__main__":
    try:
        verify(sys.argv[1])
    except (IndexError, OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
    print("OK    debug crash action absent")