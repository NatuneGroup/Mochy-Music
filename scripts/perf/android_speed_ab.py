#!/usr/bin/env python3
"""Measure several builds of the app on one emulator, the same way, one after the other.

For every APK under --apks (one folder per commit, as the Android speed A/B workflow downloads them):
  1. install it fresh, start it once, then mark the first-run setup as done (adb root: the settings
     file and the install marker the app checks), so every commit opens straight on Home
  2. find the Settings tab and the Appearance row on screen (uiautomator), compile the app the way a
     phone does after install (speed-profile), and run two unmeasured warm-up rounds
  3. --runs measured rounds, each: force-stop, `am start -W` (TotalTime = cold start), wait for Home
     to settle, tap Settings, then tap Appearance; each tap is followed by `dumpsys gfxinfo framestats`
     over the frames it caused
  4. one more round under Perfetto, kept as a trace

Per tap it records:
  tap_to_frame_ms   from the tap's HandleInputStart to the end of the frame that handled that input,
                    so it is the tap's own latency and not whatever jank followed it
  post_tap_longest_ms, post_tap_longest_end_ms   the slowest frame in the window and when it ended,
                    reported separately so jank after the screen appears cannot pass for latency
  screen_seen       whether the destination's marker text appeared after the tap; a round without it
                    is rejected instead of scored
  janky_pct, p50/p90/p95/p99, slow_ui   gfxinfo's own numbers for the frames since the tap

Writes results.json, summary.md, screenshots and traces into --out.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import statistics
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ADB = os.environ.get("ADB", "adb")
AAPT2 = os.environ.get("AAPT2", "aapt2")
TP_PYTHON = os.environ.get("TP_PYTHON", "")

SETTLE_AFTER_START = float(os.environ.get("SETTLE_AFTER_START", "7"))
SETTLE_AFTER_TAP = float(os.environ.get("SETTLE_AFTER_TAP", "3"))


def run(args: list[str], timeout: int = 180, check: bool = True) -> str:
    r = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"{' '.join(args)} failed ({r.returncode}): {r.stdout[-2000:]} {r.stderr[-2000:]}")
    return r.stdout


DROPPED = ("device offline", "no devices", "device not found", "device still authorizing")


def adb(*args: str, timeout: int = 180, check: bool = True) -> str:
    # the emulator occasionally drops off mid-run (seen as "device offline" during a fresh install);
    # reconnect and retry rather than losing the whole comparison to one flaky half minute
    for attempt in range(3):
        try:
            return run([ADB, *args], timeout=timeout, check=check)
        except RuntimeError as e:
            if attempt == 2 or not any(d in str(e) for d in DROPPED):
                raise
            log(f"device dropped mid-command, reconnecting (attempt {attempt + 1})")
            subprocess.run([ADB, "reconnect"], capture_output=True, text=True, timeout=60)
            subprocess.run([ADB, "wait-for-device"], capture_output=True, text=True, timeout=180)
            time.sleep(5)
    raise RuntimeError("unreachable")


def sh(cmd: str, timeout: int = 180, check: bool = True) -> str:
    return adb("shell", cmd, timeout=timeout, check=check)


def log(msg: str) -> None:
    print(time.strftime("%H:%M:%S"), msg, flush=True)


# ---------------------------------------------------------------------------------------------- setup

def package_of(apk: Path) -> str:
    out = run([AAPT2, "dump", "badging", str(apk)])
    m = re.search(r"package: name='([^']+)'", out)
    if not m:
        raise RuntimeError("no package name in " + str(apk))
    return m.group(1)


def launcher_of(pkg: str) -> str:
    out = sh(f"cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER {pkg}")
    lines = [l.strip() for l in out.splitlines() if "/" in l]
    if not lines:
        raise RuntimeError("no launcher activity for " + pkg + ": " + out)
    return lines[-1]


def apk_facts(apk: Path) -> dict:
    """How the APK stores compose resources: the claim is that every .cvr is deflated."""
    facts = {"size": apk.stat().st_size, "cvr_files": 0, "cvr_deflated": 0, "cvr_bytes": 0,
             "default_cvr": None, "compose_xml_drawables": 0}
    with zipfile.ZipFile(apk) as z:
        for info in z.infolist():
            name = info.filename
            if name.endswith(".cvr"):
                facts["cvr_files"] += 1
                facts["cvr_bytes"] += info.file_size
                if info.compress_type == zipfile.ZIP_DEFLATED:
                    facts["cvr_deflated"] += 1
                if re.search(r"/values/[^/]+\.cvr$", name) and "app_strings" in name:
                    facts["default_cvr"] = {"name": name, "size": info.file_size,
                                            "stored": info.compress_type == zipfile.ZIP_STORED}
            elif name.startswith("assets/composeResources/") and "/drawable/" in name:
                facts["compose_xml_drawables"] += 1
    return facts


SETUP_DONE_PREFS = (
    # androidx.datastore PreferenceMap { "setupCompleted": true, "hideUpdateNotice": true }; the second
    # keeps the update sheet (an older build sees a newer release) from covering Home and the tab bar
    b"\x0a\x14\x0a\x0esetupCompleted\x12\x02\x08\x01"
    b"\x0a\x16\x0a\x10hideUpdateNotice\x12\x02\x08\x01"
)


def mark_setup_done(pkg: str, work: Path) -> None:
    data = f"/data/data/{pkg}"
    owner = sh(f"stat -c %u:%g {data}").strip()
    local = work / "settings.preferences_pb"
    local.write_bytes(SETUP_DONE_PREFS)
    sh(f"mkdir -p {data}/files/datastore {data}/no_backup")
    adb("push", str(local), f"{data}/files/datastore/settings.preferences_pb")
    sh(f"touch {data}/no_backup/setup_wizard_completed")
    sh(f"chown -R {owner} {data}/files {data}/no_backup")
    sh(f"restorecon -R {data}", check=False)


def save_app_log(pkg: str, out: Path) -> str:
    """Keeps the app's crash and log lines for a start that didn't reach Home, and says what it found.

    Mochy's own handler logs a crash under MochyCrash (main buffer) and kills itself, so the crash
    buffer alone can be empty.
    """
    crash = sh("logcat -d -b crash", check=False, timeout=60)
    main = sh("logcat -d -b main -v time", check=False, timeout=60)
    pid_alive = sh(f"pidof {pkg}", check=False).strip()
    (out / "app.log").write_text(f"pidof: {pid_alive or 'not running'}\n\n== crash buffer ==\n{crash}\n\n== main ==\n{main[-400000:]}")
    lines = [l for l in (crash + main).splitlines() if "MochyCrash" in l or "FATAL EXCEPTION" in l or "ANR in" in l]
    for l in lines[:12]:
        log(f"  app: {l}")
    if lines:
        return "crashed: " + lines[0][-160:]
    return "still running" if pid_alive else "not running, no crash line"


def force_stop(pkg: str) -> None:
    sh(f"am force-stop {pkg}")


def cold_start(component: str) -> dict:
    out = sh(f"am start -W -n {component}", timeout=120)
    vals = dict(re.findall(r"^(\w+): (\S+)", out, re.MULTILINE))
    return {"total_ms": int(vals.get("TotalTime", "-1")), "wait_ms": int(vals.get("WaitTime", "-1")),
            "status": vals.get("Status", "?"), "launch_state": vals.get("LaunchState", "?")}


def screenshot(path: Path) -> None:
    try:
        png = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
        path.write_bytes(png)
    except Exception as e:  # a missing picture never fails a measurement
        log(f"screenshot failed: {e}")


def ui_nodes(save: Path | None = None) -> list[dict]:
    for attempt in range(4):
        out = sh("uiautomator dump /sdcard/ui.xml", check=False, timeout=60)
        if "dumped to" in out:
            xml = adb("exec-out", "cat", "/sdcard/ui.xml")
            if save:
                save.write_text(xml)
            root = ET.fromstring(xml)
            nodes = []
            for n in root.iter("node"):
                m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
                if not m:
                    continue
                x1, y1, x2, y2 = map(int, m.groups())
                nodes.append({"text": n.get("text", ""), "desc": n.get("content-desc", ""),
                              "clickable": n.get("clickable") == "true",
                              "x": (x1 + x2) // 2, "y": (y1 + y2) // 2, "w": x2 - x1, "h": y2 - y1})
            return nodes
        log(f"uiautomator dump failed ({out.strip()}), retrying")
        time.sleep(2)
    return []


def find(nodes: list[dict], label: str, prefer_bottom: bool = False) -> tuple[int, int] | None:
    hits = [n for n in nodes if label in (n["text"], n["desc"]) and n["w"] > 0 and n["h"] > 0]
    if not hits:
        return None
    hit = max(hits, key=lambda n: n["y"]) if prefer_bottom else min(hits, key=lambda n: n["y"])
    return hit["x"], hit["y"]


def tap(xy: tuple[int, int]) -> None:
    sh(f"input tap {xy[0]} {xy[1]}")


def scroll_profile() -> None:
    """The same fixed drag every time, so the rows land at the same place in every round."""
    size = re.search(r"(\d+)x(\d+)", sh("wm size"))
    w, h = (int(size.group(1)), int(size.group(2))) if size else (1080, 2400)
    sh(f"input swipe {w // 2} {int(h * 0.75)} {w // 2} {int(h * 0.30)} 400")
    time.sleep(1.5)


def wait_for_label(label: str, timeout: float = 5.0, save: Path | None = None) -> bool:
    """True once a node with this exact text/description is on screen; False if it never appears.

    This is a validity gate, not the timing: uiautomator costs a few hundred ms per dump, so the
    elapsed time here would be far too coarse to score. gfxinfo supplies the latency.
    """
    end = time.time() + timeout
    while time.time() < end:
        nodes = ui_nodes(save)
        if find(nodes, label) is not None:
            return True
        time.sleep(0.2)
    return False


# ------------------------------------------------------------------------------------------- gfxinfo

def gfx_reset(pkg: str) -> None:
    sh(f"dumpsys gfxinfo {pkg} reset")


def gfx_read(pkg: str) -> dict:
    out = sh(f"dumpsys gfxinfo {pkg} framestats", timeout=60)
    head = out.split("---PROFILEDATA---", 1)[0]

    def num(pattern: str, default=None):
        m = re.search(pattern, head)
        return float(m.group(1)) if m else default

    res = {
        "frames_total": num(r"Total frames rendered: (\d+)", 0),
        "janky": num(r"Janky frames: (\d+)", 0),
        "janky_pct": num(r"Janky frames: \d+ \(([\d.]+)%\)", 0.0),
        "p50": num(r"50th percentile: (\d+)ms"),
        "p90": num(r"90th percentile: (\d+)ms"),
        "p95": num(r"95th percentile: (\d+)ms"),
        "p99": num(r"99th percentile: (\d+)ms"),
        "slow_ui": num(r"Number Slow UI thread: (\d+)", 0),
    }
    rows = []
    for block in out.split("---PROFILEDATA---")[1::2]:
        lines = [l.strip().rstrip(",") for l in block.strip().splitlines() if l.strip()]
        if not lines:
            continue
        cols = lines[0].split(",")
        for line in lines[1:]:
            vals = line.split(",")
            if len(vals) < len(cols) or not vals[0].isdigit():
                continue
            rows.append(dict(zip(cols, map(int, vals[: len(cols)]))))
    rows = [r for r in rows if r.get("FrameCompleted", 0) > 0 and r.get("IntendedVsync", 0) > 0]
    rows.sort(key=lambda r: r["IntendedVsync"])
    res["frames"] = len(rows)
    if rows:
        def dur(r):
            return (r["FrameCompleted"] - r["IntendedVsync"]) / 1e6

        # the tap itself has to be in the data, or there is no latency to report
        inputs = [r for r in rows if r.get("InputEventId", 0) != 0]
        res["input_frame_found"] = bool(inputs)
        if not inputs:
            return res
        # the frame that consumed this tap: earliest input frame, timed from when the app started
        # handling it. Taking the slowest frame instead let unrelated jank after the screen was drawn
        # pass for tap latency, which is what this run is supposed to measure.
        frame = inputs[0]
        t0 = frame.get("HandleInputStart", 0) or frame["IntendedVsync"]
        res["tap_to_frame_ms"] = round((frame["FrameCompleted"] - t0) / 1e6, 1)

        after = [r for r in rows if r["IntendedVsync"] >= frame["IntendedVsync"]]
        slowest = max(after, key=dur)
        res["post_tap_longest_ms"] = round(dur(slowest), 1)
        res["post_tap_longest_end_ms"] = round((slowest["FrameCompleted"] - frame["FrameCompleted"]) / 1e6, 1)
        res["frames_over_16ms"] = sum(1 for r in rows if dur(r) > 16.7)
        res["frame_ms"] = [round(dur(r), 1) for r in rows[:40]]
    return res


# ------------------------------------------------------------------------------------------- perfetto

PERFETTO_CONFIG = """
buffers: {{ size_kb: 131072 fill_policy: DISCARD }}
buffers: {{ size_kb: 4096 fill_policy: DISCARD }}
data_sources: {{ config {{ name: "linux.ftrace" target_buffer: 0 ftrace_config {{
  ftrace_events: "sched/sched_switch"
  ftrace_events: "sched/sched_wakeup"
  ftrace_events: "sched/sched_blocked_reason"
  atrace_categories: "am"
  atrace_categories: "wm"
  atrace_categories: "view"
  atrace_categories: "gfx"
  atrace_categories: "input"
  atrace_categories: "dalvik"
  atrace_categories: "res"
  atrace_categories: "disk"
  atrace_apps: "{pkg}"
  buffer_size_kb: 16384
  drain_period_ms: 250
}} }} }}
data_sources: {{ config {{ name: "linux.process_stats" target_buffer: 1 process_stats_config {{ scan_all_processes_on_start: true }} }} }}
data_sources: {{ config {{ name: "track_event" target_buffer: 0 }} }}
duration_ms: 30000
write_into_file: true
file_write_period_ms: 2000
"""


def traced_round(pkg: str, component: str, taps: dict, out: Path) -> Path | None:
    cfg = out / "perfetto.pbtx"
    cfg.write_text(PERFETTO_CONFIG.format(pkg=pkg))
    adb("push", str(cfg), "/data/misc/perfetto-configs/ab.pbtx")
    sh("rm -f /data/misc/perfetto-traces/ab.pftrace", check=False)
    force_stop(pkg)
    # composable names in the trace (the profileable build bundles androidx tracing-perfetto)
    sh(f"am broadcast -a androidx.tracing.perfetto.action.ENABLE_TRACING_COLD_START "
       f"-n {pkg}/androidx.tracing.perfetto.TracingReceiver", check=False)
    pid = sh("perfetto --background --txt -c /data/misc/perfetto-configs/ab.pbtx "
             "-o /data/misc/perfetto-traces/ab.pftrace", check=False).strip().splitlines()
    pid = pid[-1].strip() if pid else ""
    time.sleep(2)
    t = time.time()
    cold_start(component)
    time.sleep(SETTLE_AFTER_START)
    if taps["settings"] != taps["tab"]:
        tap(taps["tab"])
        time.sleep(SETTLE_AFTER_TAP)
        scroll_profile()
    tap(taps["settings"])
    time.sleep(SETTLE_AFTER_TAP)
    tap(taps["appearance"])
    time.sleep(SETTLE_AFTER_TAP)
    if pid.isdigit():
        sh(f"kill -TERM {pid}", check=False)
        for _ in range(30):
            if sh(f"kill -0 {pid} 2>/dev/null && echo alive", check=False).strip() != "alive":
                break
            time.sleep(1)
    dest = out / "trace.pftrace"
    adb("pull", "/data/misc/perfetto-traces/ab.pftrace", str(dest), check=False)
    log(f"trace: {dest.stat().st_size if dest.exists() else 0} bytes after {time.time() - t:.0f}s")
    return dest if dest.exists() and dest.stat().st_size > 0 else None


TRACE_QUERY = r'''
import json, sys
from perfetto.trace_processor import TraceProcessor
tp = TraceProcessor(trace=sys.argv[1])
pkg = sys.argv[2]
def q(sql):
    return [dict(r.__dict__) for r in tp.query(sql)]
proc = q(f"select upid, pid from process where name = '{pkg}' order by upid desc limit 1")
res = {}
if proc:
    upid, pid = proc[0]["upid"], proc[0]["pid"]
    main = q(f"select utid from thread where upid = {upid} and tid = {pid}")
    utid = main[0]["utid"] if main else -1
    base = f"from slice join thread_track tt on slice.track_id = tt.id where tt.utid = {utid}"
    res["main_thread_top"] = q(f"select name, count(*) n, sum(dur)/1e6 ms {base} and depth <= 40 group by name order by ms desc limit 40")
    res["frames_over_32ms"] = q(f"select ts, dur/1e6 ms {base} and name like 'Choreographer#doFrame%' and dur > 32e6 order by ts")
    res["compose_resources"] = q(f"select name, count(*) n, sum(dur)/1e6 ms {base} and (name like '%compose.resources%' or name like '%stringResource%' or name like '%painterResource%' or name like '%vectorResource%') group by name order by ms desc limit 20")
    res["blocking"] = q(f"select name, count(*) n, sum(dur)/1e6 ms {base} and (name like '%runBlocking%' or name like '%AssetManager%' or name like '%Inflat%') group by name order by ms desc limit 20")
    res["all_tracks_compose_resources"] = q("select name, count(*) n, sum(dur)/1e6 ms from slice where name like '%compose.resources%' or name like '%stringResource%' or name like '%painterResource%' group by name order by ms desc limit 20")
print(json.dumps(res, default=str))
'''


def analyse_trace(trace: Path, pkg: str, out: Path) -> dict:
    if not TP_PYTHON or not Path(TP_PYTHON).exists():
        return {}
    script = out / "tp_query.py"
    script.write_text(TRACE_QUERY)
    try:
        r = subprocess.run([TP_PYTHON, "-I", str(script), str(trace), pkg], capture_output=True, text=True, timeout=600)
        if r.returncode != 0:
            log("trace analysis failed: " + r.stderr[-1500:])
            return {}
        res = json.loads(r.stdout.strip().splitlines()[-1])
        (out / "trace_summary.json").write_text(json.dumps(res, indent=1))
        return res
    except Exception as e:
        log(f"trace analysis failed: {e}")
        return {}


# ------------------------------------------------------------------------------------------- one build

def check_resources(apk: Path, names_file: Path, out: Path) -> dict:
    """Every Android resource the shared code resolves on Android is still in the shrunk APK."""
    if not names_file.exists():
        return {}
    dump = run([AAPT2, "dump", "resources", str(apk)], timeout=300)
    present = set(re.findall(r"resource 0x[0-9a-f]+ ([\w.]+/[\w.]+)", dump))
    wanted = [l.strip() for l in names_file.read_text().splitlines() if l.strip() and not l.startswith("#")]
    missing = [w for w in wanted if w not in present]
    (out / "shared_resources_check.txt").write_text(
        f"{len(wanted)} wanted, {len(missing)} missing\n" + "\n".join(missing))
    return {"wanted": len(wanted), "missing": missing}


def measure(label: str, folder: Path, out_root: Path, runs: int) -> dict:
    out = out_root / label
    out.mkdir(parents=True, exist_ok=True)
    apk = folder / "app.apk"
    pkg = package_of(apk)
    result: dict = {"label": label, "package": pkg, "apk": apk_facts(apk)}
    result["shared_resources"] = check_resources(apk, folder / "shared-android-resources.txt", out)
    log(f"== {label}: {pkg} {result['apk']}")

    adb("uninstall", pkg, check=False)
    adb("install", "-r", "-g", str(apk), timeout=600)
    component = launcher_of(pkg)
    result["component"] = component

    cold_start(component)
    time.sleep(10)
    screenshot(out / "0-first-start.png")
    force_stop(pkg)
    mark_setup_done(pkg, out)

    sh("logcat -c", check=False)
    try:
        cold_start(component)
    except Exception:
        save_app_log(pkg, out)
        raise
    time.sleep(SETTLE_AFTER_START + 3)
    screenshot(out / "1-home.png")
    nodes = ui_nodes(out / "1-home.xml")
    settings = find(nodes, "Settings", prefer_bottom=True)
    if settings is None:
        reason = save_app_log(pkg, out)
        raise RuntimeError(f"{label}: no Settings tab on screen ({reason}), see {out}/1-home.png and app.log")
    tap(settings)
    time.sleep(SETTLE_AFTER_TAP + 1)
    # the Settings tab opens on the profile page; the settings list itself is behind its "Open settings"
    # row, so that row is the tap measured as "Settings" and the tab itself is only the way there. The row
    # starts at the screen's bottom edge, under the gesture area, so the page is scrolled up first
    scroll_profile()
    nodes = ui_nodes(out / "2-settings-tab.xml")
    open_settings = find(nodes, "Open settings")
    if open_settings is not None:
        tap(open_settings)
        time.sleep(SETTLE_AFTER_TAP + 1)
        nodes = ui_nodes(out / "2-settings.xml")
    screenshot(out / "2-settings.png")
    appearance = find(nodes, "Appearance")
    if appearance is None:
        raise RuntimeError(f"{label}: no Appearance row in Settings, see {out}/2-settings.png")
    tap(appearance)
    time.sleep(SETTLE_AFTER_TAP + 1)
    screenshot(out / "3-appearance.png")
    taps = {"tab": settings, "settings": open_settings or settings, "appearance": appearance}
    result["taps"] = taps
    force_stop(pkg)

    # what a phone has a while after installing: the app compiled from its profile
    log(sh(f"cmd package compile -m speed-profile -f {pkg}", timeout=600).strip())

    rounds = []
    for i in range(runs + 2):
        force_stop(pkg)
        time.sleep(1.5)
        start = cold_start(component)
        time.sleep(SETTLE_AFTER_START)
        if taps["settings"] != taps["tab"]:
            # onto the profile page first, unmeasured: the open being timed is the settings list's
            tap(taps["tab"])
            time.sleep(SETTLE_AFTER_TAP)
            scroll_profile()
        gfx_reset(pkg)
        tap(taps["settings"])
        # the frames have to be drawn before gfxinfo can report them; the marker check comes after, so its
        # uiautomator dumps don't land inside the measured window
        time.sleep(SETTLE_AFTER_TAP)
        s = gfx_read(pkg)
        s["screen_seen"] = wait_for_label("Appearance", timeout=SETTLE_AFTER_TAP + 2)
        gfx_reset(pkg)
        tap(taps["appearance"])
        time.sleep(SETTLE_AFTER_TAP)
        a = gfx_read(pkg)
        a["screen_seen"] = wait_for_label("Accent colour", timeout=SETTLE_AFTER_TAP + 2, save=out / "5-appearance.xml")
        if i == runs + 1:
            screenshot(out / "4-last-round.png")
        # a round whose screen never opened says nothing about latency; keep the count honest
        if not (s["screen_seen"] and a["screen_seen"]):
            log(f"run {i - 1} rejected: screen_seen settings={s['screen_seen']} appearance={a['screen_seen']}")
            continue
        if i < 2:
            log(f"warm-up {i + 1}: start {start['total_ms']} ms")
            continue
        rounds.append({"start": start, "settings": s, "appearance": a})
        log(f"run {i - 1}: start {start['total_ms']} ms | settings {s.get('tap_to_frame_ms')} ms "
            f"(longest {s.get('longest_frame_ms')}, janky {s['janky_pct']}%) | appearance "
            f"{a.get('tap_to_frame_ms')} ms (longest {a.get('longest_frame_ms')}, janky {a['janky_pct']}%)")
    result["rounds"] = rounds

    trace = traced_round(pkg, component, taps, out)
    if trace:
        result["trace"] = analyse_trace(trace, pkg, out)
    force_stop(pkg)
    return result


# ------------------------------------------------------------------------------------------- summary

def med(values: list) -> float | None:
    values = [v for v in values if v is not None and v >= 0]
    return round(statistics.median(values), 1) if values else None


def summarise(results: list[dict]) -> tuple[dict, str]:
    rows = []
    for r in results:
        if "error" in r:
            rows.append({"label": r["label"], "error": r["error"]})
            continue
        rs = r["rounds"]
        row = {"label": r["label"], "runs": len(rs),
               "cold_start_ms": med([x["start"]["total_ms"] for x in rs])}
        for screen in ("settings", "appearance"):
            for key in ("tap_to_frame_ms", "post_tap_longest_ms", "janky_pct", "p90", "p99", "slow_ui"):
                row[f"{screen}_{key}"] = med([x[screen].get(key) for x in rs])
        row["cvr"] = f"{r['apk']['cvr_deflated']}/{r['apk']['cvr_files']} deflated"
        if r.get("shared_resources"):
            row["shared_res_missing"] = len(r["shared_resources"]["missing"])
        rows.append(row)

    lines = ["## Android speed A/B (emulator, x86_64 FOSS profileable, medians)", ""]
    lines.append("| build | cold start | Settings tap→frame | post-tap longest | janky % | p90 | p99 | Appearance tap→frame | post-tap longest | janky % | p90 | p99 |")
    lines.append("|---|---|---|---|---|---|---|---|---|---|---|---|")
    for row in rows:
        if "error" in row:
            lines.append(f"| {row['label']} | failed: {row['error'][:120]} |" + " |" * 10)
            continue
        def f(k, unit=" ms"):
            v = row.get(k)
            return "–" if v is None else f"{v:g}{unit}"
        lines.append(
            f"| {row['label']} | {f('cold_start_ms')} | {f('settings_tap_to_frame_ms')} | {f('settings_post_tap_longest_ms')} | "
            f"{f('settings_janky_pct', '%')} | {f('settings_p90')} | {f('settings_p99')} | {f('appearance_tap_to_frame_ms')} | "
            f"{f('appearance_post_tap_longest_ms')} | {f('appearance_janky_pct', '%')} | {f('appearance_p90')} | {f('appearance_p99')} |")
    lines.append("")
    for r in results:
        if "error" in r:
            continue
        a = r["apk"]
        lines.append(f"- {r['label']}: APK {a['size']:,} bytes, compose .cvr {a['cvr_deflated']}/{a['cvr_files']} deflated "
                     f"({a['cvr_bytes']:,} bytes), default strings {a['default_cvr']}")
        if r.get("shared_resources"):
            sr = r["shared_resources"]
            lines.append(f"  - shared code's Android resources in the shrunk APK: {sr['wanted'] - len(sr['missing'])}/{sr['wanted']}"
                         + (f", missing {sr['missing'][:10]}" if sr["missing"] else ""))
        cr = (r.get("trace") or {}).get("compose_resources") or []
        if cr:
            total = sum(x["ms"] or 0 for x in cr)
            lines.append(f"  - trace: {total:.1f} ms on the main thread inside compose resources calls "
                         + ", ".join(f"{x['name'][:60]} ×{x['n']} {x['ms']:.1f} ms" for x in cr[:4]))
    lines.append("")
    lines.append("tap→frame: from HandleInputStart of the tap to FrameCompleted of the frame that consumed it. "
                 "post-tap longest is the slowest frame in the same window and is reported apart, so jank after the "
                 "screen appears cannot pass for latency. A round is dropped when the destination's marker text "
                 "(Appearance) never appears, and a round with no input frame is reported as no data. Each round is a "
                 "fresh cold start, so every open is the first one in that process.")
    return {"rows": rows}, "\n".join(lines) + "\n"


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--apks", required=True, type=Path)
    p.add_argument("--out", required=True, type=Path)
    p.add_argument("--runs", type=int, default=10)
    args = p.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    order = [o["label"] for o in json.loads(os.environ.get("REFS", "[]"))]
    folders = {d.name.removeprefix("apk-"): d for d in sorted(args.apks.iterdir()) if (d / "app.apk").exists()}
    labels = [l for l in order if l in folders] + [l for l in folders if l not in order]

    sh("settings put global window_animation_scale 1", check=False)
    results = []
    for label in labels:
        try:
            results.append(measure(label, folders[label], args.out, args.runs))
        except Exception as e:
            log(f"{label} failed: {e}")
            results.append({"label": label, "error": str(e)})
        (args.out / "results.json").write_text(json.dumps(results, indent=1))

    summary, md = summarise(results)
    (args.out / "summary.json").write_text(json.dumps(summary, indent=1))
    (args.out / "summary.md").write_text(md)
    print(md)
    return 0 if all("error" not in r for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
