# Bolo Hisab (বলো হিসাব) — notes for Claude

Offline Bangla voice ledger for Bangladeshi shopkeepers. **Talk-first khata is the key selling point**:
one spoken sentence → many items → confirm card → saved. Nothing is saved without the confirm card.

## Product scope (current, ahead of the plan doc)
`Bangla Voice Ledger — Validation & Product Plan.md` is out of date. After comparing with TallyKhata
before v1, scope was widened: stock/inventory, customer profiles (address, photo) and the rest of
the khata basics ship in v1, with voice-first entry as the differentiator.

## Input help (key differentiator alongside voice)
- **Banglish typing** (`:core:nlu` `typing/`): Avro-style. `AvroPhonetic` is the raw transliteration;
  `PhoneticKey` is a loose sound key so "chal"/"taka"/"rohim" reach চাল/টাকা/রহিম; `TypingDictionary`
  holds the Bolo Hisab vocabulary, sentences ("আজ মোট বিক্রি কত") and English aliases ("kg", "oil"),
  plus the shop's customers/products/past items; `PhoneticSuggester` ranks them. Space/comma commits
  the highlighted whole-word match, never a completion. UI: the strip inside `VoiceOutlinedTextField`
  (`typing = TypingContext.*` per field), toggle in Settings.
- **Lexicon CSVs** (team-edited in spreadsheets; guide: `docs/lexicon.md`):
  `core/nlu/src/main/resources/com/bolohisab/nlu/lexicon/` `words.csv`, `phrases.csv`,
  `next_words.csv` (`<number>`, `<customer>` slots, row order = rank), `dialect.csv`; parser corpus
  `core/nlu/src/test/resources/com/bolohisab/nlu/test_sentences.csv` runs as `SentenceCorpusTest`.
  `LexiconFilesTest` validates every file. Grow vocabulary and dialects there, not in code.
- **Learning** (on device, encrypted DB tables `typing_choices`, `word_usage`, `word_pairs`,
  `corrections`; `LearningRepository`): whole-word picks per Banglish spelling, word/pair usage from
  confirmed entries, and voice fixes learned from confirm-card edits (`Corrections.learn`). Corrections
  are guarded: heard word only, sound-alike only, never grammar/unit/number words or an existing
  customer's name. Everything is reviewable/deletable in Settings and included in backup (v3).
- **Dialects** (`Dialect.kt`, data in `dialect.csv`): variant → standard word map run before parsing. Only add
  words with a single unambiguous ledger meaning, from field data. Voice-side dialect support needs
  ASR fine-tuning on regional recordings; this map covers the text side.

## Modules
- `:core:nlu` — pure Kotlin, no Android. `LedgerParser` (sentence → `EntryDraft` / `LedgerQuery`),
  `BanglaNumbers`, `CustomerMatcher`, `ItemMatcher`, `Lexicon`. Unsure fields go in `EntryDraft.uncertain`.
- `:core:data` — Room + SQLCipher (key wrapped by Android Keystore), DataStore prefs, encrypted backup.
- `:core:voice` — sherpa-onnx streaming Zipformer, single dedicated thread.
- `:app` — Compose + Material 3, Hilt, type-safe nav. Tabs: Ledger, Customers, Reports, Stock, Settings.

## Rules that must hold
- Money is `Long` poisha (`Poisha`), never floating point.
- Entries are never hard-deleted (`deleted_at`); balance = `SUM(balance_delta)` of live entries.
- Multi-step writes (customer + entry + items + stock) go through `db.withTransaction`.
- `undo`/`restore` are idempotent — stock must never move twice for one entry.
- Room migrations are additive only; a shopkeeper's ledger must survive every update.
- Backup format is versioned (`BACKUP_FORMAT_VERSION`); new fields need defaults so old files decode.
- Every user-facing string exists in both `values/` (Bangla, default) and `values-en/`.
- UI: tight, professional Material 3; amber tint marks fields the parser was unsure of.

## Testing
`./gradlew :core:nlu:test :core:data:testDebugUnitTest :app:testDebugUnitTest`.
In cloud sessions `dl.google.com`, GitHub release downloads and foojay are blocked, so Android modules
cannot build there; pure-JVM code (nlu, `PinHasher`, `PinLockout`, `BackupCrypto`, `BackupModels`)
can be tested with a scratch Kotlin/JVM Gradle project pointing at those sources.
