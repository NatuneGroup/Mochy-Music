# Mochy handoff (2026-11-22)

## Resume here

- Open `/root/natune-work/mochy-wt/integ`. The integration worktree is clean and matches the remote branch `kmp/phase0-foundation` at `9c03b096b9b917b4549916d388bae9dea9a3bfc8`. [Draft PR #32](https://github.com/NatuneGroup/Mochy-Music/pull/32)
- Android/desktop compilation and shared/provider tests were running at handoff. Check their results before changing the branch. iOS simulator and desktop packaging were skipped because the PR is a draft. Mark the PR ready after Fast checks pass to start those workflows. [Fast checks for `9c03b096`](https://github.com/NatuneGroup/Mochy-Music/actions/runs/45004305132)
- Run the signed compressed ARM64 comparison on Blacksmith for the current head. The command and size baseline are in **Latest** below. Review all exact-head checks before merging to `dev`. Keep Gradle builds, SDK downloads, and artifact downloads off the phone unless the operator requests one.
- The KMP conversion is still in progress. PR #32 shares preferences, theming, portable playback controls, and portions of the standalone UI and Room models. The full Room database, Android screen and account parity, and device checks still need work.

Repo: NatuneGroup/Mochy-Music. `dev` is integration, `main` is Stable. Local clone `/root/natune-work/Mochy`, worktrees in `/root/natune-work/mochy-wt/`.
Push dev with `refs/heads/dev:refs/heads/dev` (upstream has a `dev` tag; keep tag fetching off).
`gh pr edit` fails (Projects classic); use `gh api -X PATCH repos/NatuneGroup/Mochy-Music/pulls/N -f title=...`.

## Operator rules (also in memory)
- Treat it as my own repo: decide, commit, push, merge on green without asking.
- Never build locally or on the phone; Blacksmith CI only (~3,000 free minutes, macOS counts 40×). Batch CI; cancel iOS/Desktop-installer runs a change can't affect.
- Natune docs are the preferences (`/root/natune-work/natune-agent/AGENTS.md`, `CLAUDE.md`, `Natune/docs/engineering/`). Mochy AGENTS.md is reference only, but credits/licences always stay.
- No file-size limit. New .kt starts with `package`, no banner; comments say why; strings in res XML.
- Opus does critical work (playback, native, CI/signing, data, seams); Haiku 5.5 sub-agents do broad work; Opus reviews every Haiku diff.
- The operator doesn't read code: verify every idea for need and worth, push back when not worth it.
- iOS = one shared Compose UI (Metrolist approach); SwiftUI only for the shell + CarPlay templates.
- Excluded sources: TIDAL, Qobuz, Deezer, QQ, Amazon, ArchivePool, the shared Apple Music lyrics pool, Telegram channel streaming.

## Signing
- Release key made 2026-11-22: `/root/natune-signing/mochy/release.keystore` + `password.txt` (chmod 600, never print). Alias `mochy`, CN `Mochy (NatuneGroup)`, SHA-256 starts `DE:2B:8A:B0`.
- Repo secrets set: KEYSTORE_BASE64, KEY_ALIAS, STORE_PASSWORD, KEY_PASSWORD.
- The operator still needs to back up that folder off the phone (told them).
- Signed build: `gh workflow run build_pr.yml -R NatuneGroup/Mochy-Music --ref <branch> -f release=Foss` → artifact `mochy-arm64-Foss-release-<sha>`, checked for the repository key.
- The user reports installing the signed ARM64 FOSS build from run `45000052683` at `458264dd`. Later APKs signed with the same key can update it in place.

## State
- Merged into dev: #18, then #29 (batch: #19 KMP playback/Android Auto/CarPlay/preload, #20 upstream sync, #21 battery idle work, #22 desktop MPRIS, #23 player-style spine + Looper, #24–#28 Natune ports).
- #30 merged the ARM libopus decoder, signed release workflow and resource-string fixes. The decoder runs first when Efficient decoding is enabled. #31 merged the first accent picker fixes. See **Latest** below for verified runs and measurements.
- **Open: #32 `kmp/phase0-foundation`** in `/root/natune-work/mochy-wt/integ`. See **Latest** for the exact candidate and CI runs. This is the current integration worktree.

## Battery measurement
- Root bridge (the operator starts it: `su -c 'sh -c "$(cat /data/data/com.termux/files/home/.mochy-bridge.sh)"'`; read-only allowlist, stops itself after 2 h).
- My helper: `<scratchpad>/rootq.sh "<cmd>"`. If the scratchpad is gone, it just sends the command to the bridge; read `~/.mochy-bridge.sh` for the protocol. Never run destructive commands or read Mochy's private files.
- Method: sum utime+stime from `/proc/<pid>/stat` over 60 s, plus a per-thread diff of `/proc/<pid>/task/*/stat`.
- Baseline (old build): Mochy 21% of a core (MediaCodec_loop 556 cs/min, ExoPlayer:Playb 385, HwBinder 96). Metrolist 3%. Cause: `c2.android.opus.decoder` out of process; Jellyfin's FFmpeg has no opus/vorbis.
- If Mochy lands near Metrolist after #30, skip the lean-playback rework. Otherwise: build the effects chain only from effects that are switched on (9 processors + 3 sink wrappers today); one player per queue, second only during a crossfade.

## Pending
1. **Review the Haiku ports from #29** (just started). Each branch's own diff: `git diff <base> origin/<branch>`. Base = first commit of `git rev-list origin/<b> --not origin/port/kmp-playback-and-library origin/sync/upstream-main origin/perf/battery-idle-work origin/arch/player-style-spine d9e7110c`, parent.
   - content-and-privacy: 7 files, +89
   - lyrics-options: 8 files, +162
   - android-auto-extras: 11 files, +254
   - queue-and-playback: 12 files, +440
   - look-and-navigation: 14 files, +635
   Look for settings that are saved but never read, wrong defaults, strings outside XML, design-language drift. Fix in one follow-up PR.
2. **KMP Phase 0 (Metrolist approach):** PreferenceKeys → shared (same package), `PlaybackController` interface, JetBrains navigation-compose multiplatform, Room MusicDatabase → shared, strings → compose resources. Then components, screens (Settings, Search, Home, Album/Artist/Playlist, Library, Player), then iOS/desktop on the shared app.
3. **Skipped ports:** SimpMusic style, AI-content filter, account switcher, hide age-restricted, video/PiP, Listen Together, AI translation, Google Drive backup, theme creator (accent picker done).
4. **Small items:**
   - Desktop MPRIS Next/Previous via a shared UI hook.
   - AppSwitch not accented; a look module's accent only tints the player.
   - The ACTION_LIBRARY shortcut opens the Search tab.
   - Document CarPlay as the iOS system-only exception.
   - Settings index has a duplicate "YouTube Music" entry.

## Notes
`/tmp/claude-0/-root/92f84abd-a8c4-45b0-a05e-cc50ada42a6c/scratchpad/` holds settings-compare.md, kmp-gap.md, battery-audit.md, palette-analysis.md, decoding-analysis.md, port-brief.md. They may be cleared; regenerate if needed.
Full transcript: `/root/.claude-acct3/projects/-root/92f84abd-a8c4-45b0-a05e-cc50ada42a6c.jsonl`.

## Historical agent plan
Operator: "make a lot of haiku agents complete stuff"; app is sideloaded (no App Store), so iOS matters.
- Base branch `ci/agent-batch-base` (on top of #30, pushed):
  - iOS compile and Desktop installers skip draft PRs.
  - Fast checks also run `:shared:compileCommonMainKotlinMetadata` (first run will show whether that task name is right).
- Agents branch from it, push, and open DRAFT PRs into dev (Fast checks only). At most 2 CI rounds each.
- Brief: scratchpad port-brief.md. Update it: Natune docs instead of Mochy AGENTS.md, agents may push and open draft PRs, credits stay.
- KMP tracks:
  - K1 PreferenceKeys and pure constants → shared, same package.
  - K2 Room DB → shared (Room KMP, no schema change, same exported schema).
  - K3 ui/theme + pure components → shared.
  - K4 extend the existing shared `PlayerController` (queue, skip, shuffle/repeat) + an Android implementation over PlayerConnection; no second interface.
  - K5 Koin in shared + compose-resources string setup + multiplatform navigation dependency.
- Port tracks:
  - P1 SimpMusic player style (spine).
  - P2 theme creator (palette-analysis.md).
  - P3 hide age-restricted + AI-content filter.
  - P4 account switcher.
  - P5 video/PiP.
  - P6 AI lyrics translation (user-entered key).
  - Skip Google Drive backup (needs an OAuth client the operator creates) and Listen Together (needs a server).
- Opus reviews every diff, then integrates as one batch, like #29.

## Latest (2026-11-22, after PR #31)
- The earlier #30 and Phase 0 status above is historical. #30 merged into `dev` at `e794cbc8`. The string validation script was absent from CI because `.gitignore` ignores `*.py`. Commit `9b4135d5` tracks it explicitly. Fast checks `45001045454` and the post-merge full build `45001628216` passed.
- The signed ARM64 FOSS APK from run `45000052683` measured **18,251,769 bytes** unpacked from its Actions artifact. The user reports installing it. Two read-only one-minute measurements via the existing root bridge found 112% of one core during a period with active rendering and 35% during verified playback with the screen awake. Crossmix analysis accounted for about 15 percentage points of the latter. Use a matched playback comparison before attributing the difference to the Opus decoder.
- #31 merged at `fc5ef69b` after Fast checks `45001711197` passed. It wraps accent swatches on narrow panes, saves manual selections atomically, applies the accent to enabled switches, and decouples the app's album-art seed from the separate player theme setting. Device visual verification is pending.
- Metrolist is cloned at `/root/natune-work/ref/Metrolist` at `0e58b29`, with a shallow sparse checkout. Use `git show HEAD:<path>` for files outside the sparse checkout. Its shared database owns schema and migrations, with platform builders under each target, and its Compose hosts use common UI. Keep Mochy's own style and existing native playback engines.
- Working integration branch `kmp/phase0-foundation` lives at `/root/natune-work/mochy-wt/integ` and starts from `fc5ef69b`. It integrates the KMP preference keys, unchanged SearchHistory Room entity, shared stream-format badge and common playback controller with Android's native queue. The review added missing controller overrides and prevents idle Android collection of the new shared state. Work in this branch, since the original `/root/natune-work/Mochy` checkout predates several merged changes.
- The foundation adds Gradle subprojects `:shared:preferences` (key identities and defaults that are portable) and `:shared:theme` (Mochy's typography and full Material palette). Android keeps its palette source and dark styling, while desktop and iOS read the saved seed through their existing preferences store. Shared navigation and final CI status are recorded below as they finish.
- Removed generated ArchivePool dependency/build directories, the Gradle 9.3.1 distribution from this session, Mochy's generated local build cache and the obsolete 88 MiB debug APK. Roughly 1.4 GiB reclaimed. The compact reference APK, repository data, signing files, and user downloads remain available.
- The APK size target is about 10 MB. Use signed ARM64 CI and record the actual APK size separately from the compressed Actions artifact. The earlier compact package's largest entries were DEX (10.02 MB), compiled resources (3.49 MB) and native libraries (2.35 MB). Preserve media and platform support when trying smaller packaging.
- PR #32 is based on `fc5ef69b`. It adds desktop/iOS Settings with common accent, Pure black and codec badge preferences, makes Android's Library shortcut open Library, and applies the palette to Android settings and navigation. `0f057ec8` also gives the standalone app shell and its content the selected theme colors. `d78ebedf` moves `LyricsEntity`, `FormatEntity`, `PlayCountEntity` and `SetVideoIdEntity` to common code byte-for-byte. `9c03b096` routes Linux MPRIS Next through the actual persisted upcoming queue and Previous through recent playback or a restart of the current track.
- Initial Fast checks `45003150028` found missing generated Compose resource imports and a JVM-only regex option in common code. `14a2edb8` fixes both. Shared/provider tests passed on that commit, but Android compilation and the signed compressed APK failed on a missing `luminance` import. `2fe75988` restores that import. `git diff --check origin/dev..HEAD` and `python3 scripts/ci/check_string_resources.py` passed before `9c03b096` was pushed. The current remote checks are listed under **Resume here**.
- The build option `-Pmochy.compressNativeLibs=true` changes only how native files are packaged. It uses the same application ID and release key. Installer extraction increases installed disk use, so the normal APK keeps the existing format. The signed comparison command is `gh workflow run build_pr.yml -R NatuneGroup/Mochy-Music --ref kmp/phase0-foundation -f release=Foss -f compress_native_libs=true`.

### Remaining work

- Finish the `kmp/phase0-foundation` integration and verify one batched candidate through Blacksmith. Android's full Room database, account integrations and the remaining Android screens still need shared implementations. Desktop/iOS should keep their existing media engines and native integration. Test CarPlay on an Apple-provisioned build and test Android Auto on a vehicle or host emulator when one is available.
- Review #29 behavior live as well as preference wiring. The key audit reached the Android Auto settings, preload, navigation, playback and appearance readers. KMP app-wide behavior still needs UI and playback verification.
- Use Blacksmith for all builds. Avoid downloads of SDKs and local Gradle runs on this phone.
- The queue and Room worktrees under `/root/natune-work/mochy-wt/` contain `fcaadac2` and `b07cbd64`, respectively. Both are already integrated into PR #32. For iOS Next/Previous, a follow-up can share `QueueTransport` through `IosPlaybackRuntime` and connect it to the system remote commands on the main thread. Mochy's Room 2.8.4 Android DB v45, builder and migrations still live in the Android host. Preserve the database file and schema when moving the remaining code, and verify opening an existing v45 database. Metrolist's Room 3 migration code and newer schema use a different API contract.

## Resume here (Opus session, 2026-11-22 ~10:30)
- Reviewed #32: all 437 preference keys keep their stored names and enum values; the Room entity moves are byte-identical; the PlayerController seam matches Mochy's own shuffle/repeat/skip paths. Fixed: Android `PlayerConnection.release()` is now a no-op (b9a7cada), so a shared screen can't dispose the app's connection.
- #32 marked ready. Batched candidate on b9a7cada: Build PR signed release 45012551986, Fast checks 45012545693, iOS compile 45012637961 (dispatched by hand: the ready event raced the push), Desktop installers 45012642331. If all are green, merge #32 into dev and put the APK in /sdcard/Download/Mochy-release/.
- Haiku batch started from b9a7cada. Brief: /root/natune-work/agent-notes/port-brief.md. Each agent has its own worktree and opens a DRAFT PR (Fast checks only):
  - port/player-style-simp (p-simp)
  - port/theme-creator (p-theme)
  - port/content-filters (p-filter): hide only, never play age-restricted
  - port/account-switcher (p-account)
  - port/video-pip (p-video)
  - port/ai-lyrics-translation (p-translate)
  - kmp/shared-components (k-components): pure components → shared
  Opus reviews each diff, then integrates them as one batch (like #29).
- Still Opus's: move the full Room DB (v45, Room 2.8.4) to shared without changing the schema; Koin and shared navigation; then the screens.

- #32 MERGED at f87497c9 (all four checks green on b9a7cada). APK: /sdcard/Download/Mochy-release/Mochy-kmp-phase0.apk (18.3 MB).
- Content-filter agent stopped: Mochy already has an AI filter (DisableAiFilterKey + innertube looksAiGenerated, on by default, onboarding page), but only Home applies it and Settings has no row for it. Follow-up: add a Content settings row and apply it to search and radio. Age-restricted hide dropped (no list-level signal).

- Operator 2026-11-22: "leave size, just make the app on quality". The APK size work is parked (branch ci/apk-size-report: unmerged, report only). Priority: review + quality fixes.

## Resume here (Opus, 2026-11-22 ~11:40)
- Operator: "leave size, make the app on quality"; "no clones" (memory: no-duplicate-features). jarvis (ChatGPT Codex) posts reviews AS the operator's GitHub account. Verify each comment, fix the real ones, reply on the PR with what changed. A Monitor polls all open PRs for new comments.
- **integ/batch-2** (worktree mochy-wt/batch2): merged #35 kmp components, #33 themes, #34 Simp style, #36 AI translation settings, #37 PiP, #38 account switcher, #39 behaviour options, #40 iOS media controls. Fixes added on top:
  - Simp: colours follow the cover tones, like-button tap guard.
  - Themes: "tap to use, hold to edit" hint.
  - Accounts: toString no longer prints the cookie.
  - Mute recovery: one shared default (30%).
  Not yet a PR: when #41 and #43 are green, merge them in plus #42, open ONE ready PR into dev (runs iOS + installers), dispatch a signed release, merge on green, APK to Downloads.
- #41 kmp/resource-bridge: `ResourceBridge` in shared/build.gradle.kts. App strings and icons become Compose resources at build time (single source). Removed 15 copied strings.
- #43 kmp/db-schema-guard: exportSchema on, v45 baseline committed (24 tables, 3 views), and Fast checks fail when app/schemas changes.
- #42 kmp/shared-components-2: NavPill, AppSwitch (accent now follows LocalMochyAccent), Menu, smoothFrames moved to shared.
- KMP screen map: /root/natune-work/agent-notes/kmp-screens.md. Main blocker: all 52 screen ViewModels are @HiltViewModel. Next decision: DI for shared ViewModels (Koin, as Metrolist does).
- DB move plan, in stages, behind the schema guard:
  1. scalar entities
  2. java.time → epoch/kotlinx-datetime (9 entities, ~50 app files)
  3. 14 migrations onto SQLiteConnection
  4. 4 raw queries
  5. MusicDatabase wrapper expect/actual
  6. per-platform builder, same file mochy.db, v45
- Account switcher limitation: likes from two accounts mix. clearAllSyncedContent also wipes local likes, so it's not used.

- #44 (batch 2) MERGED at 2bb5429f after Fast checks, iOS compile, desktop installers and the signed release all passed. APK: /sdcard/Download/Mochy-release/Mochy-batch2.apk (19.5 MB; about +1 MB is the bridged strings packed twice until Android reads Res). No jarvis review arrived within ~1 h. Keep watching #44 and open PRs; fix comments in a follow-up PR.
- Next KMP step (Opus): DI for shared ViewModels (all 52 screen VMs are @HiltViewModel). Then DB stage 1 behind the schema guard.

## Resume here (Opus, 2026-11-22 ~13:00)
- jarvis findings on #32 and #44: all five real, all fixed in #45 (merged at e37b2071, green incl. iOS); replies posted on #44 and #32. My replies end with "— Claude Code" (the watcher skips those).
- Pending earlier: DI. Koin is deferred until the DB is in shared. Findings in agent-notes/kmp-screens.md: 54 VMs, 7 dependency-free; Context in 36; MusicDatabase in 32.
- Running agents:
  - kmp/db-datetime (worktree k-datetime). DB stage 2: entities → kotlinx-datetime 0.8.0, same UTC epoch millis, schema guard must stay green. Helpers in shared db/DbTime.kt. Draft PR, up to 5 CI rounds. REVIEW CAREFULLY: behaviour at every java.time call site.
  - Research: agent-notes/kmp-context-uses.md (what the VMs use Context for), to design the shared replacements.
- Next for Opus:
  1. Review and merge datetime.
  2. DB stage 3: move the entities and relation classes to shared (byte-identical), guard green.
  3. Migrations onto the SQLiteConnection API, with migration tests.
  4. Raw queries.
  5. MusicDatabase wrapper and per-platform builder.

## Resume here (Opus, 2026-11-22 ~15:30)
- Rule now: Haiku agents commit locally only, and Opus reads every diff before pushing (memory: review-every-subagent-diff). Re-arm the jarvis comment watcher whenever it expires.
- Open PRs:
  - #46 kmp/db-datetime: entities on kotlinx-datetime. I fixed the 38 caller sites myself. DbTimeCompatTest pins the stored millis and the java toString text.
  - #48 fix/jarvis-29-ports: the four #29 port bugs (duplicates, CarPlay reconnect, AA tap, SponsorBlock), plus UpcomingPreloader (the #47 follow-up).
  - #49 fix/jarvis-batch-3: reviews of #19.1, #22, #24, #37, #38, #39, #41 and #45.
- After CI is green: merge, then reply on each source PR (#19, #22, #24, #29, #37, #38, #39, #41, #45, #46, #47), ending replies with "— Claude Code".
- Still to do:
  - #19.2: CarPlay cold launch. Move Up Next restore, transport binding and track-end advance out of StandaloneMusicApp into one shared playback owner (IosPlaybackRuntime level). Opus does this.
  - The full #29 port review: SponsorBlock, car, queue rules and the settings rows are covered by jarvis; the rest still need reading. Then the DataStore injection for ViewModels (agent-notes/kmp-context-uses.md).

## Resume here (Opus, 2026-11-22 ~16:30)
- Merged today:
  - #45 (5 jarvis fixes)
  - #47 (preload on shuffle/repeat)
  - #48 (#29 port bugs, UpcomingPreloader)
  - #49 (9 review fixes)
  - #46 (Room entities on kotlinx-datetime; DbTimeCompatTest)
  - #50 (SharedQueue: Up next owned by IosPlaybackRuntime and the desktop app, not the screen)
- Every jarvis comment so far has a reply ending "— Claude Code".
- Next:
  1. The full read of #29's Haiku ports not flagged by jarvis: player styles/looper, accent colour, UI scale, system font, nav tabs, privacy, storage, lyrics switches, car settings and the rest. Look for bugs and for duplicates of existing features.
  2. KMP: inject DataStore<Preferences> into ViewModels (agent-notes/kmp-context-uses.md); then DB stage 3, moving entities to shared.

## Resume here (Opus, 2026-11-22 ~17:00)
- Operator's latest asks:
  - Port ALL the UI to shared (Metrolist-style), with a proper desktop port.
  - CI builds Android and Linux only for now: #52 (iOS compile and mac/win installers are manual; run iOS once per batch).
  - Use Opus-medium sub-agents for critical work and Haiku only for mechanical work (memory: review-every-subagent-diff).
- Brief for every port agent: agent-notes/kmp-port-brief.md.
- Running Opus-medium agents (they push draft PRs for CI and never merge; I review every line):
  - kmp/ui-kit (worktree kmp-uikit): shared rememberPreference via LocalAppPreferences (Android = the sealed context.dataStore), and the settings UI kit (SettingsGroup, SubSettingsScaffold, AppSwitch) moved to shared, same packages.
  - kmp/db-entities (worktree kmp-db): DB stage 3, entities and converters to shared commonMain, schema guard green.
- After those: Koin plus DataStore injection for ViewModels; navigation (Metrolist uses navigation3; decide); then screen waves (settings first), each wave Haiku with full review; desktop runs the shared screens.
- Merged since the last note:
  - #51: the review doc's four findings (AI translator provider isolation, preload gate and cancel, one in-flight lyrics fetch per song, PassPcm not exact on NaN) plus jarvis #49 (opaque account id). Replies are on #49 and #36.
- APK at /sdcard/Download/Mochy-release/Mochy-batch3.apk (dev c27d67fd, before #51).

## Resume here (Opus, 2026-11-22 ~19:00)
- Merged today:
  - #52 CI: PRs build Android and Linux only; iOS is run by hand once per batch.
  - #53 DB stage 3: entities and converters are in shared commonMain.
  - #54 shared rememberPreference/LocalAppPreferences/PreferencesCache (survives an unreadable store), SettingsGroup in shared.
  - #55, #56 jarvis: SharedFetches for lyrics; Custom AI model is migrated once.
- Running: Opus agent on kmp/fonts (worktree kmp-fonts). Shared font hook (InterFontFamily in shared), SettingsRows to shared, and the operator's ask: a custom font (.ttf/.otf, optional bold, copied to filesDir/fonts, reusing ActiveSkin's font loader).
- The operator expects the full UI port to be completed. Remaining, in order:
  1. SubSettingsScaffold: GlassKit/blur behind expect/actual, plus LocalPlayerAwareWindowInsets in shared.
  2. DB stages 4-6: DAO, then MusicDatabase and migrations, then per-platform builders (Room KMP like Metrolist).
  3. Koin plus shared ViewModels (54 HiltViewModels; Context mostly DataStore = inject the store).
  4. Navigation: Metrolist uses navigation3 (decide when you get there).
  5. Screen waves, settings first (agent-notes/kmp-screens.md); Opus for hard ones, Haiku for mechanical; full diff review.
  6. Desktop runs the shared app (replaces StandaloneMusicApp); then iOS.
- Also pending:
  - Finish the #29 Haiku-port review (list in the earlier note).
  - Fix the image cache slider label, which says "Unlimited" but the max is 2 GB.

## Resume here (Opus, 2026-11-22 ~00:15 IST; operator asleep, bridge down)
- Merged since the last note:
  - #57 shared font hook and custom font, plus #61 fixing jarvis's catch (the staged font file was deleted before install).
  - #58 image cache label shows 2 GB.
  - #59 DB stage 4: DAO Flow/suspend queries in shared SongDao/PlaylistDao/HistoryDao; app DatabaseDao extends them and keeps 138 blocking/raw/MediaMetadata members.
  - #60 jarvis perf: cache scan runs only while observed.
  - #62 CrossMix: one whole decode per song, background cpuset, faster decoder loop, no forced AUTO_CROSSFADE→CROSSMIX migration, battery note. Metrolist notes are in agent-notes/metrolist-perf.md.
  - #63 crash fix for Settings → Now Playing: Compose resources reject "24.0dip". ResourceBridge rewrites dip→dp; BridgedDrawablesTest checks every bridged icon.
- Batch5 release is building from dev, then goes to /sdcard/Download/Mochy-release/Mochy-batch5.apk (batch4 has the Now Playing crash and the broken custom font).
- Phone measurement (batch3/4, bridge):
  - Steady background playback: 8% of one core.
  - CrossMix analysis bursts: 12-23%.
  - PSS 247 MB.
  - Findings: /sdcard/Download/mochy-cpu-findings.md, https://files.catbox.moe/zfl2sy.md. Re-measure after batch5: bridge allowlist needs "top -b ..." and "logcat -d ..." with those flags first, and no pipes.
- Crash reports: Mochy's own handler logs under the tag MochyCrash (main buffer) and kills itself; the crash buffer won't have it.
- Gap: app/src/test is never run by CI. CrossmixAnalysisCacheTest (from #62) is unrun.
- Open product question for the operator: new installs still default to CROSSMIX (DefaultTransitionMode).
- Next port steps (unchanged): SubSettingsScaffold/GlassKit expect/actual; DB stage 5 (suspend versions of the 138 app DAO members, then MusicDatabase to shared); Koin; navigation; screen waves (Sonnet/Haiku; Opus only critical).
- 2026-11-23: Fast checks now run AnalysisQueueTest and CrossmixAnalysisCacheTest (app/src/test) in the compile job (#65). The full crossmix package ran 38 tests with 1 failure: CrossmixTest `everyday picks only, and blends stay short` (line 438, a blend longer than 16 s beat-matched / 8.5 s otherwise). It was already failing on dev; it's a pure planner test. Decide whether the planner or the rule is wrong (user setting allows CROSSMIX length 8-20 s) and then widen the CI filter to the whole package. Add testLogging exceptionFormat FULL to see the message.
- CrossmixTest failure message (probe #67, closed): "Filter sweep blends for 9.62 s". FilterSweep is not beat-matched and offers 8 or 16 beats (CrossmixStyles.kt:91); at 100 bpm 16 beats = 9.6 s, over the test's 8.5 s cap for unmatched blends. Likely fix: the planner clamps unmatched blend length to ≤ 8.5 s (rather than relaxing the test). Then widen the CI filter to the whole crossmix package.

## Resume here (Opus, 2026-11-23 early morning IST; operator asleep)
- Merged since ~00:15:
  - #63 dip fix (batch5).
  - #65 AnalysisQueue: skipped songs are dropped from CrossMix analysis.
  - #66 SubSettingsScaffold in shared: LocalSubSettingsChrome (Android: AppSubSettingsChrome with GlassKit; desktop/iOS: PlainSubSettingsChrome); LocalPlayerAwareWindowInsets in shared; BackPressHandler expect/actual (no-op on desktop/iOS).
  - #68 queue cleared → analysis pruned.
  - #69 CrossmixTest length caps checked on planned blends (forcedBlend's ~10 s is intended); Fast checks run the whole crossmix app test package.
  - #70 DB stage 5 (if its iOS compile passed): SuspendingDao (62 suspend twins, all SQL verified identical to dev), library sorts in shared (LibrarySort.kt, expect nameCollator).
- Next: DB stage 6a per agent-notes/kmp-db-stage6.md. Steps:
  - MochyDatabase interface.
  - SpeedDialDao to shared.
  - Second @Database for desktop/iOS in a nonAndroidMain source set.
  - Schema-diff CI step against app/schemas 45.json.
- Then: Koin; navigation; screen waves.
- Batch5 at /sdcard/Download/Mochy-release/Mochy-batch5.apk (dev 831817f2). It doesn't include #65/#68 (CrossMix queue). Build batch6 after the next merges.
- Open, from jarvis: #64 (CrossMix trace markers, draft). It conflicts with #65's AnalysisQueue placement; resolve when it's marked ready.
- Merge scripts must require every check to read "pass"; an empty checks result (network error) once let #68 through unchecked (its checks had passed).

## Resume here (2026-11-23, morning)
- Merged: #72 Koin (shared appModule gets Android's Hilt InternalDatabase + sealed dataStore; desktop/iOS `mochy.db` beside library.db; ArtistAlbumsViewModel is shared via koinViewModel), #73 JetBrains navigation-compose 2.9.0 (Android navigation 2.9.0 -> 2.9.1) + ComingSoonScreen shared, #74 bridge writes @android:color/white|transparent as values + BridgedDrawablesTest decodes every icon with decodeToImageVector, #75 shared Dialog.kt / SeekBarStyles / DefaultPlayerSeekBarSlider + platformCancelLabel/OkLabel/monotonicMillis + CMP material-icons-core 1.7.3, #76 AiTranslatorSettings + SuggestionRegionSheet shared.
- Open: #77 bridge drops android:tint="?attr/colorControlNormal" (unblocks sleep_timer, bluetooth, linear_scale, music_history).
- Batches: batch6 (69e692ce), batch7 (96c331b7, has Koin) in /sdcard/Download/Mochy-release/, signer de2b8ab0 verified. Operator to check Artist > Albums on batch7.
- Next: wave 3 = PlaybackSettings + ListenBrainzScreen (after #77; ScrobbleTimer DEFAULT_* split out, ScrobbleTimingGroup public); ProxySettings needs a common proxy-type enum + shared AppProxy check. Then desktop runs the shared settings.
- Desktop/iOS mochy.db has no migrations yet: move AllMigrations to shared before the next schema bump.

## Resume here (2026-11-23, afternoon): everything stopped by the operator
Nothing is running. All agents, monitors and wait-loops were stopped at the operator's request.

### Merged today
- #72: Koin
- #73: navigation-compose + ComingSoonScreen
- #74, #77: the bridge writes @android:color values and drops the ?attr/colorControlNormal tint; every bridged icon is decoded in tests
- #75: shared Dialog, SeekBar styles, material icons
- #76: AiTranslator + SuggestionRegionSheet
- #78: releases + updater (nightly on dev, stable on main, rising versionCode, Foss + Gms APKs; updater reads NatuneGroup/Mochy-Music; credit constants unchanged)

### Open PRs
- **#79** fix/release-script: commits .github/scripts/release_version.py, which the root .gitignore `*.py` rule had dropped. The first nightly failed without it (run 45167298231). Merge it, then check that the nightly publishes v8.4.3.x with foss and gms APKs.
- **#80** sync/upstream-1123: the upstream realidkroo merge (d027930d Chromecast + optimizations, 8.4.5, README). The agent was stopped while waiting on CI, so its report is missing. Check CI, the DB/schema handling (upstream touched SongEntity and DatabaseDao) and the conflict resolutions before merging.
- **#81** perf/android-shared-speed (draft): the A/B speed workflow only, not run yet.
- **#64** jarvis CrossMix trace markers (draft).

### batch7 lag (operator report: delay opening settings after a cold start)
Suspected causes, not yet measured:
1. CMP 1.10.3 `Res` strings and icons call `runBlocking` on the Android main thread (ResourceState.blocking.kt). `app_strings.commonMain.cvr` is 308 KB and DEFLATED, and every first string read inflates up to its offset (DefaultAndroidResourceReader.readPart). Vectors are parsed from XML text.
   - Fix idea: on Android, map shared Res to the app's own R resources by name; desktop/iOS keep Res. Watch out for R8 shrinkResources.
2. shared/build.gradle.kts has no composeCompiler block, so it lacks the app's stability conf and the no-source-info rule for release.
   - #66 (SubSettingsScaffold to shared) is the only shared move between batch5 and batch7.

### Sora (github.com/shizenworks/sora, remote `sora`, was 4nx3b/sora)
44 commits not in dev, +52.7k lines.
- **Wanted:** settings cards with live previews, settings reorg, liquid glass (vendored com/kyant/backdrop: check licence/credit), Flamingo/TikTok from Neiro, namida 1:1, Listen Together with chat (public relays), WebDAV/SMB, SoundCloud, lyrics sources sheet, nav bar dimensions, crash-log export, DB 45->46 artist.blockedAt (no schema JSON seen).
- **Declined:** Qobuz, Qobuz backup, Deezer modules (e52222f9). Muzo Data is likely the same category; unconfirmed.
- The two reviews were stopped before they wrote agent-notes/sora-review-*.md, so nothing was reviewed yet.
- Merge after #80, because both change the DB.

### Player styles still to port from Natune
Editorial (V10), Flamingo (compare with Sora's), BitChord, SpatialFlow, Metrolist KMP (check it isn't a duplicate of Mochy's METROLIST). Mochy already has TikTok, Looper, Simp, Apple Music, Namida and Metrolist.

### Operator rules learned today
- Speed must be measured identical on Android.
- At most 2 agents, but keep the jarvis watcher on.
- Big, complete PRs.
- The phone runs Termux, so avoid heavy local work.

### Stray shells on the phone
Still running from earlier, operator to decide:
- loops polling #70/#71 checks (pids 24805, 11577, 5650)
- an agent's `find /` scan (pids 8829, 8846)
