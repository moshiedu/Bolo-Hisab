# বলো হিসাব (Bolo Hisab)

An offline Bangla voice ledger for shopkeepers. Say one sentence and it becomes a ledger entry:

> রহিম ২ কেজি চাল ১২০ টাকা আর ১ লিটার তেল ২০০ টাকা, ১০০ দিয়েছে
>
> → credit sale to রহিম · চাল 2 kg ৳120 · তেল 1 L ৳200 · total ৳320 · paid ৳100 · due ৳220

Everything runs on the phone: speech recognition, parsing and storage. Nothing is saved until the shopkeeper confirms it on the review card.

## Open and run

1. Open the `BoloHisab` folder in Android Studio and let Gradle sync.
2. The first sync downloads, once:
   - the sherpa-onnx speech engine AAR (~40 MB) into `app/libs/` (done in `settings.gradle.kts`);
   - the Bangla streaming Zipformer model (~90 MB) into the `asr_model` Play Asset Delivery pack, `asr_model/src/main/assets/models/asr-bn/` (Gradle task `:asr_model:fetchAsrModel`, runs before `preBuild`; a model downloaded by older builds into `app/src/main/assets` is moved there).

Debug builds carry the model inside the APK. Release builds are meant for Play: the model arrives as a fast-follow pack right after install. A release APK installed outside Play has no voice model (typing still works); test release builds through Play internal testing or `bundletool build-apks --local-testing`.
3. Run the `app` configuration on a phone or an x86_64 emulator.

With no model on the device, the app still works by typing (keyboard icon, top right), and the three example sentences on the empty home screen run through the same parser.

Toolchain: AGP 8.13, Gradle 8.14.5, Kotlin 2.2.20, compileSdk/targetSdk 36, minSdk 26. The Gradle daemon runs on JetBrains JDK 21 (`gradle/gradle-daemon-jvm.properties`), downloaded automatically on first build; Android Studio needs a Kotlin plugin that supports Kotlin 2.2. Android Studio's AGP Upgrade Assistant can move it to AGP 9 later.

## Modules

| Module | What it does |
| --- | --- |
| `:core:nlu` | Pure Kotlin. Bangla number normalisation (সাড়ে তিনশো → 350), units, fuzzy customer matching, and the ledger grammar that turns a sentence into an `EntryDraft` or a question. No Android dependency; tests run on the JVM in seconds. |
| `:core:data` | Room + SQLCipher. Money is stored as `Long` poisha. Entries are append-only (soft delete for undo), and a customer's balance is `SUM(balance_delta)`. The database passphrase is wrapped by an Android Keystore key. |
| `:core:voice` | sherpa-onnx streaming recognizer on a dedicated thread, with hold-to-talk, live partial text and a level meter. It finds the model in app storage first, then in assets. |
| `:app` | Compose + Material 3 UI, Hilt, type-safe navigation: home with a hold-to-talk mic, confirm card, customers list, customer ledger with a WhatsApp/SMS reminder. |

## How a sentence is understood

1. The speech model writes Bangla text. The Bengali Zipformer always outputs Bengali script (Whisper small mixes in Devanagari).
2. `BanglaNumbers` turns number words into values: দেড়শো, এক হাজার দুইশো পঞ্চাশ, দুইটা.
3. `LedgerParser` finds the customer (handling case endings, name parts and ASR slips), splits the sentence into clauses, and gives each number a role: quantity, price, paid, credit or total. It then decides between cash sale, credit, payment and expense.
4. Fields it is unsure of are returned in `EntryDraft.uncertain`, and the confirm card tints them amber.

## Tests

```
./gradlew :core:nlu:test :core:data:testDebugUnitTest :app:testDebugUnitTest
```

Without the Android SDK, the pure-Kotlin parts (parser, typing help, lexicon files, PIN and backup code) run with:

```
./gradlew -p tools/jvm-check test
```

- `LedgerParserTest` and `SentenceCorpusTest`: real shop sentences, the latter read from `test_sentences.csv`.
- `PhoneticTypingTest`, `LearningTest`: Banglish typing, suggestions, and on-device learning.
- `LexiconFilesTest`: checks the team-edited lexicon CSVs (see [docs/lexicon.md](docs/lexicon.md)).
- `BanglaNumbersTest`, `ReviewStateTest`, `PinHasherTest`, `PinLockoutTest`, `BackupCryptoTest`, `BackupModelsTest`.

## Next milestones

Open issues, device tests and team to-dos: [TODO.md](TODO.md).

- Record 300 real sentences from 10 shops into `test_sentences.csv` and measure the unedited-save rate (target 9 in 10).
- Ship the model as a Play Asset Delivery pack, so hotwords work for everyone without the 90 MB copy behind the Settings toggle.
- Fine-tune the speech model on regional recordings (dialect support on the voice side).
- Optional FunctionGemma fallback for sentences the rules miss.
- AdMob and Play Billing (Remove Ads / Pro).
