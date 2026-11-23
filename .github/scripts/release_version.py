#!/usr/bin/env python3
"""Pick the version, tag, build code and changelog for a Mochy nightly or stable release.

Run from a full clone (tags fetched). Prints GitHub Actions outputs (key=value lines).

  stable:  one patch above the newer of app/build.gradle.kts's x.y.z and the newest vX.Y.Z tag,
           so with no release yet 8.4.3 ships as v8.4.4, then v8.4.5 ...
  nightly: built on that newest stable (or the gradle x.y.z before the first one) with a fourth
           number counting up: v8.4.3.1, v8.4.3.2, and after v8.4.4 ships v8.4.4.1. The app's
           parseFullVersion reads that fourth number as the nightly, newer than the x.y.z it sits on.
  version code: the floor in app/build.gradle.kts plus minutes since 1970, so it only grows,
           across both channels, and stays far below Android's limit (2^31 - 1) for millennia.
"""

import re
import subprocess
import sys
import time
from pathlib import Path

STABLE_TAG = re.compile(r"v(\d+)\.(\d+)\.(\d+)")
NIGHTLY_TAG = re.compile(r"v(\d+)\.(\d+)\.(\d+)\.(\d+)")
GRADLE = Path("app/build.gradle.kts")


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout.strip()


def tag_commit(tag: str) -> str:
    return git("rev-parse", f"{tag}^{{commit}}")


def is_ancestor(tag: str, head: str) -> bool:
    return subprocess.run(["git", "merge-base", "--is-ancestor", tag_commit(tag), head]).returncode == 0


def read_gradle() -> tuple[tuple[int, int, int], int]:
    text = GRADLE.read_text()
    name = re.search(r'^\s*versionName = .*?"(\d+)\.(\d+)\.(\d+)', text, re.M)
    floor = re.search(r"^\s*val versionCodeFloor = (\d+)", text, re.M)
    if not name or not floor:
        raise SystemExit("Could not read versionName or versionCodeFloor from app/build.gradle.kts")
    return tuple(int(p) for p in name.groups()), int(floor.group(1))


def plan(channel: str, base: tuple[int, int, int], tags: list[str]) -> tuple[str, str | None]:
    """The new version, and the tag of the same channel it follows (None for the first one)."""
    stables = {tuple(int(p) for p in m.groups()): t for t in tags if (m := STABLE_TAG.fullmatch(t))}
    nightlies = {tuple(int(p) for p in m.groups()): t for t in tags if (m := NIGHTLY_TAG.fullmatch(t))}
    last_stable = max([*stables, base])
    if channel == "stable":
        major, minor, patch = last_stable
        previous = stables[max(stables)] if stables else None
        return f"{major}.{minor}.{patch + 1}", previous
    on_this = [n[3] for n in nightlies if n[:3] == last_stable]
    version = ".".join(map(str, (*last_stable, max(on_this, default=0) + 1)))
    previous = nightlies[max(nightlies)] if nightlies else None
    return version, previous


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in ("nightly", "stable"):
        raise SystemExit("Usage: release_version.py nightly|stable")
    channel = sys.argv[1]
    base, floor = read_gradle()
    tags = git("tag", "--list", "v*").splitlines()
    head = git("rev-parse", "HEAD")

    version, previous = plan(channel, base, tags)
    code = floor + int(time.time()) // 60
    if not floor < code <= 2**31 - 1:
        raise SystemExit(f"version code {code} is out of Android's range")

    # nothing new since the last one of this channel: no release
    unchanged = previous is not None and tag_commit(previous) == head

    since = previous if previous and is_ancestor(previous, head) else None
    log_range = [f"{since}..{head}"] if since else ["-n", "30", head]
    subjects = git("log", "--no-merges", "--format=- %s (%h)", *log_range).splitlines()[:60]

    print(f"version={version}")
    print(f"tag_name=v{version}")
    print(f"version_name={version} build#{code}")
    print(f"version_code={code}")
    print(f"commit_sha={head}")
    print(f"previous_tag={previous or ''}")
    print(f"unchanged={'true' if unchanged else 'false'}")
    Path("release-notes.md").write_text(
        (f"Changes since {since}:\n\n" if since else "Recent changes:\n\n")
        + ("\n".join(subjects) or "- no code changes")
        + "\n"
    )


if __name__ == "__main__":
    main()
