# Bolo Hisab: open issues and to-do

Last updated: Oct 3, 2026. Branch `claude/gallant-pasteur-kypgn8`.
Tick an item when it's done, and add new ones at the end of their section.

## Open issues

### 1. "Better recognition of your names" can't be turned on (hotwords crash the speech engine)
- **What happens:** Settings → *কাস্টমার ও পণ্যের নাম ভালো চেনা*. Loading the speech model in hotword mode makes
  sherpa-onnx abort in native code (the process ends with no Java stack trace). The model *is* a
  BPE model (`▁` in `tokens.txt`, a `.vocab` file present), so the model type is not the cause.
- **Current state:** fails safely. `HotwordGuard` disables hotword mode after one crash, and the switch is
  hidden on that phone. Voice works normally, just without the name boost.
- **Needed to fix:** the native crash line. On a debug build:
  ```
  adb shell run-as com.bolohisab.app rm -f no_backup/asr-hotwords-disabled no_backup/asr-hotword-attempt
  adb logcat -c
  adb logcat -b main -b crash -v time > crash.txt
  ```
  Then turn the switch on (or reopen the app once) and look for `sherpa`, `Abort`, `F DEBUG`,
  `signal` or `HotwordGuard`. Also note the phone model and Android version.
- Code: `core/voice/.../HotwordGuard.kt`, `SherpaSpeechRecognizer.kt` (`ensureLoaded`), `AsrModelInstaller.kt`.

### 2. Android code from the latest changes has not been compiled or run on a device yet
Written in a cloud session without the Android SDK. After pulling, build and check:
- [ ] Gradle sync and debug build succeed (new `:asr_model` asset-pack module, `play asset-delivery 2.2.2`,
      `androidx.biometric 1.1.0`, `fragment-ktx 1.8.5`).
- [ ] `./gradlew :app:bundleRelease` succeeds and the base module does **not** contain the speech model.
- [ ] Room migration 4→5 runs on an existing install (learning tables), then commit the generated
      `core/data/schemas/com.bolohisab.data.db.LedgerDatabase/5.json`.
- [ ] `./gradlew :core:nlu:test :core:data:testDebugUnitTest :app:testDebugUnitTest` passes.

### 3. Android Studio shows "Unresolved reference 'AsyncImage'" (IDE only; the build works)
- Coil 3 is a Kotlin Multiplatform library, and the IDE's Kotlin plugin must support Kotlin 2.2.x.
- Fix: *Sync Project with Gradle Files* → *Invalidate Caches and Restart* → update Android Studio / enable K2 mode.

## To test on a device
- [ ] Banglish typing strip: suggestions appear; space and comma accept the highlighted word (check with Gboard).
- [ ] Confirm card: fix a misheard name, say it again, and it lands on the right customer. Check that it
      shows in *Settings → শেখা শব্দ ও সংশোধন* and can be deleted there.
- [ ] Fingerprint unlock: turn it on under App lock, leave the app and come back, and the prompt opens by itself.
- [ ] Spoken answers: "রহিমের কত বাকি" is read aloud (needs a Bangla TTS voice installed).
- [ ] Backup and restore, including customer photos (format v4), and restoring an older backup.
- [ ] Lost-key recovery screen: delete `shared_prefs/bolohisab_secure.xml` in Device Explorer and relaunch.
- [ ] Mic busy: start a call or voice recorder, then try the mic, and a clear message should appear (no hang).
- [ ] Release build through Play internal testing: the speech model arrives as the fast-follow pack.

## To do (team)
- [ ] Collect real shop sentences into `core/nlu/src/test/resources/com/bolohisab/nlu/test_sentences.csv`:
      30–50 per shop type (grocery, pharmacy, hardware) per region. Guide: `docs/lexicon.md`.
- [ ] Add missing goods, brands and units to `words.csv` (category `grocery`/`pharmacy`/`hardware`).
- [ ] Add dialect words to `dialect.csv`, only ones actually heard in shops, with their region.
- [ ] Record audio alongside the sentences if possible, for later speech-model fine-tuning (dialects).
- [ ] Measure the unedited-save rate on 300 real sentences from 10 shops (target 9 in 10).

## To do (development)
- [ ] Fix issue 1 once the crash log is available.
- [ ] Fix every sentence that fails in `SentenceCorpusTest` as the team adds data.
- [ ] Google Drive backup (planned Pro feature).
- [ ] Speech-model fine-tuning on regional recordings (voice-side dialect support).
- [ ] Cloud sessions: allow `dl.google.com`, `maven.google.com`, `github.com`,
      `objects.githubusercontent.com` and `api.foojay.io` in the environment's network settings, so
      Android modules build in cloud sessions too. The startup hook (`.claude/hooks/session-start.sh`)
      then installs the SDK automatically.
- [ ] Merge this branch into `main`, so the session hook and docs apply to everyone.

## Decisions needed
- [ ] Monetization: ads (AdMob placement), the Pro tier and its prices, distributor sponsorship.
      The plan document's *Monetization* and *Free vs Pro* sections are unchanged until this is decided.
- [ ] Whether to look for a different Bangla speech model if hotwords can't be made to work with the current one.

## Known limitations (by design)
- A release APK installed outside Play has no speech model (typing only). Debug builds and Play
  installs are fine; test release builds via Play internal testing or `bundletool build-apks --local-testing`.
- Fingerprint unlock accepts strong (Class 3) biometrics only; weak face unlock is not offered.
