#!/usr/bin/env python3
"""Reject the debug-only crash action in a production APK, without printing DEX contents.

VK-Master wave 2 (agent D, item 7): the trial's own tools (TRIAL_TOOL_MARKERS: the VK value break)
are rejected too, but in #12 trial (--allow-trial-tools, com.alal.yft.trial only).
"""

import re
import sys
import zipfile

DEBUG_MARKERS = (b"Crash now", b"YFT debug test crash", b"YFT solver canary")
TRIAL_TOOL_MARKERS = (b"VK value break",)
ALLOW_TRIAL_TOOLS = "--allow-trial-tools"


def verify(apk, allow_trial_tools=False):
    with zipfile.ZipFile(apk) as archive:
        entries = [name for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name)]
        if not entries:
            raise ValueError("APK has no DEX files")
        for name in entries:
            data = archive.read(name)
            if any(marker in data for marker in DEBUG_MARKERS):
                raise ValueError("Debug crash action is present in the release APK")
            if not allow_trial_tools and any(marker in data for marker in TRIAL_TOOL_MARKERS):
                raise ValueError("A trial tool is present in an APK that is not the trial")


def main(args):
    allow_trial_tools = ALLOW_TRIAL_TOOLS in args
    apks = [arg for arg in args if arg != ALLOW_TRIAL_TOOLS]
    if len(apks) != 1:
        raise IndexError("usage: verify-release-debug-actions.py [--allow-trial-tools] <apk>")
    verify(apks[0], allow_trial_tools)
    return "trial tools allowed (the trial)" if allow_trial_tools else "trial tools absent"


if __name__ == "__main__":
    try:
        trial = main(sys.argv[1:])
    except (IndexError, OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
    print(f"OK    debug crash action absent; {trial}")