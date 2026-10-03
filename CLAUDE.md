# Bolo Hisab (বলো হিসাব) — notes for Claude

Offline Bangla voice ledger for Bangladeshi shopkeepers. **Talk-first khata is the key selling point**:
one spoken sentence → many items → confirm card → saved. Nothing is saved without the confirm card.

## Product scope (current, ahead of the plan doc)
`Bangla Voice Ledger — Validation & Product Plan.md` is out of date. After comparing with TallyKhata
before v1, scope was widened: stock/inventory, customer profiles (address, photo) and the rest of
the khata basics ship in v1, with voice-first entry as the differentiator.

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
