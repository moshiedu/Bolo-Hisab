# Bangla Voice Ledger — Validation & Product Plan

Sep 28, 2026 · @Moshiur Rahman

## Verdict

Build it, but as a Bangladesh-first, fully offline Bangla voice ledger where one spoken sentence records many items. Plan revenue around ads and business deals, not in-app subscriptions from Bangladeshi shopkeepers (see Monetization).

The market is real and the voice gap is still open in Bangladesh. The two local leaders are free, typing-based ledgers. India already has Hindi voice ledgers, which proves the behaviour but also shows the window will not stay open long.

| App | Market | Voice entry | Offline | Scale |
| --- | --- | --- | --- | --- |
| [TallyKhata](https://www.tallykhata.com/en/khata-features/) | Bangladesh | None found; voice is only a payment-received alert on its QR product | Cloud account, restored by phone login | 5M+ users, 1M+ monthly active |
| [Hishabee](https://www.hishabee.io/) | Bangladesh | None found | Yes, full features | 1M+ merchants |
| [VoiceKhata](https://voicekhata.com/features/) | India | Yes, Hindi and Hinglish | Offline-first, syncs to its servers | Not published |
| [PocketLedger](https://play.google.com/store/apps/details?id=com.pocketledger.app) | India | Yes, Hindi and English | Works offline, syncs | Not published |
| [Voice-Khata prototype](https://github.com/sagorbro005/Voice-Khata) | Bangladesh | Yes, Bangla, multi-item | Server-side parsing | GitHub project only |

TallyKhata's own site puts Bangladesh at 12 million small businesses. The strongest remaining gap is the one a [Telugu voice-ledger builder](https://github.com/notsravan/dukaan-saathi) named: most voice ledgers take one fact per sentence and need internet. One sentence, many items, no network is the wedge.

Not yet validated: whether shopkeepers will switch from TallyKhata, and what they complain about today. Play Store review pages did not load for this research, so review mining and 10 shop interviews are still open (see Risks).

## Status update (Oct 3, 2026)

After comparing with TallyKhata before the first release, v1 scope was widened: the khata basics that shopkeepers expect (stock, customer profiles) ship in v1, and voice-first entry stays the differentiator. Typing became a second differentiator.

| Area | State in the app |
| --- | --- |
| Voice entry, confirm card, four entry types, voice questions (answered on screen and aloud) | Built |
| Customer ledger with address and photo, tagada share | Built |
| Stock (products, restock, low-stock alerts, automatic stock moves on sales) | Built (was out of scope in the original plan) |
| Reports with PDF export | Built |
| Encrypted backup file (ledger, stock, history, learning, photos) | Built; Google Drive backup not yet |
| App lock | PIN with escalating lockout and fingerprint unlock built |
| Banglish (Avro-style) typing with ledger vocabulary, sentences and the shop's own names | Built |
| On-device learning from typing picks and confirm-card corrections | Built |
| Dialect words mapped to standard Bangla before parsing | Built (text side); voice-side needs regional recordings |
| Lexicon and test sentences as team-edited CSVs | Built (`docs/lexicon.md`) |
| Speech model via Play Asset Delivery | Not yet; hotwords meanwhile need an opt-in 90 MB copy |
| Monetization (ads, Pro, sponsorship) | Not built; the sections below are unchanged pending decisions |

## Monetization

Charging Bangladeshi shopkeepers inside the app is the weak point: it must go through Google Play Billing, and most of them cannot pay there. Revenue therefore rests on ads first, business deals second, and Play purchases from the minority who can pay.

- **Play Billing is mandatory.** Google's [Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738) requires it for ad-free versions, extra features and "financial management software". Apps may not steer users to bKash or any other payment method through buttons, links or webviews.
- **Bangladesh has no exemption.** Alternative billing is only offered in the EEA, UK, Australia, Brazil, Indonesia, Japan, South Africa, South Korea, India and the US ([Google](https://support.google.com/googleplay/android-developer/answer/13821247)).
- **Few can pay on Play.** Google Play does not accept bKash or Nagad; users generally need an international or virtual card ([nsave, April 2026](https://www.nsave.com/bangladesh/google-pay)).
- **Even the leader struggles.** A TallyKhata employee review on [Glassdoor](https://www.glassdoor.com/Reviews/TallyKhata-Reviews-E5750256.htm) says the company was still working toward a sustainable revenue model.

| Stream | Market | How it works | Caveat |
| --- | --- | --- | --- |
| Ads (AdMob) | Bangladesh, India | Native ads in reports and the customer list; rewarded ads to unlock an extra PDF export. Never on the record or confirm screens | Low eCPM, but a ledger is opened many times a day |
| Pro via Play Billing | Card holders, India (UPI on Play), diaspora | One-time "Remove ads" plus a yearly Pro | Small share of Bangladeshi users |
| Business sponsorship | Bangladesh | A distributor or wholesaler pays you to give Pro to its retailer network, invoiced outside the app | Confirm with Play policy before signing a deal |
| India launch | India | Hindi model in phase 2; UPI works on Play and India has alternative billing | Crowded, but offline multi-item entry is still a gap |

## Product

Working name **Bolo Hisab (বলো হিসাব)**, with the promise "বলুন, হিসাব হয়ে যাবে — ইন্টারনেট ছাড়াই" (speak, and it's recorded, no internet). The target is a Bangladeshi grocery, pharmacy or hardware shopkeeper on a 4–6 GB phone who keeps baki in a paper khata today.

The MVP does one job better than anyone: record a sale, a credit or a payment by one spoken sentence, offline, and keep each customer's balance right.

1. **Voice entry, multi-item.** Hold the mic and say "রহিম ২ কেজি চাল ১২০ টাকা আর ১ লিটার তেল ২০০ টাকা, ১০০ দিয়েছে". The app fills customer, items, total ৳320, paid ৳100 and due ৳220.
2. **Confirm card before save.** Every entry lands on an editable card; nothing is saved unheard or unseen. Optional spoken read-back.
3. **Customer ledger.** Running balance, full history per customer, add a customer by voice or from contacts.
4. **Four entry types.** Cash sale, baki (credit), joma (collection) and expense.
5. **Voice questions.** "রহিমের কত বাকি?" and "আজ মোট বিক্রি কত?" answered from the local database, on screen and aloud.
6. **Tagada reminders.** Share a customer's due summary to WhatsApp, IMO or SMS through the share sheet, no SMS permission needed.
7. **Reports.** Daily and monthly totals, PDF export to share or print.
8. **Backup.** Encrypted local backup file, plus optional Google Drive backup.
9. **App lock.** PIN or fingerprint.
10. **Typing fallback.** Every voice action has a two-tap manual path.

11. **Banglish typing.** Type "rohim 2 kg chal" and pick রহিম, কেজি, চাল from an Avro-style strip that knows ledger words, whole sentences and the shop's own customers and products; it learns each shop's picks and corrections.
12. **Stock.** Products with stock levels and low-stock alerts; sales move stock automatically. (Added to v1 after comparing with TallyKhata.)

Left out of v1: invoices, staff accounts, payments/QR and multi-device sync.

## Free vs Pro

Voice entry stays free and unlimited, because TallyKhata and Hishabee are free and the wedge must not sit behind a paywall. Pro sells convenience and a cleaner screen.

| Feature | Free | Pro |
| --- | --- | --- |
| Voice and typed entries, customers | Unlimited | Unlimited |
| Ads | Native ads in reports and lists | None |
| PDF exports | 1 a day, more by watching a rewarded ad | Unlimited, with shop name and logo |
| Backup | Manual encrypted local file | Automatic Google Drive backup |
| Reports | Daily and monthly totals | Plus profit, expense and top-debtor reports |
| Shops | 1 | 2 |

Starting prices to test: Remove Ads ৳299 one-time, Pro ৳599 a year (₹149 and ₹499 in India). Treat them as a first guess and adjust after the first 1,000 installs.

## On-device voice pipeline

Everything runs offline: a 90 MB Bangla speech model, then plain rules, small enough to target 4 GB phones. The small language model is an optional helper for messy sentences, never a requirement.

&#91;embedded content: offline voice pipeline · 7 steps, 1 decision\]

Every path ends at the confirm card, so a wrong parse costs one tap, not a wrong balance.

- **Speech model.** Use sherpa-onnx with the Bengali streaming Zipformer released in February 2026. A developer [measured it](https://dev.to/devksarkar/i-tried-to-build-a-bengali-voice-dialer-for-android-here-is-what-actually-happened-and-how-i-40ak) at about 90 MB, under a second for a 3-second command on a Pixel 10's CPU, and always in Bengali script. Speed on a budget phone is not yet measured. Whisper failed the same test: whisper-small wrote Bengali in Devanagari, and the 988 MB turbo model took 80 seconds through whisper.cpp.
- **Customer names.** sherpa-onnx supports [hotwords](https://k2-fsa.github.io/sherpa/onnx/hotwords/index.html) for transducer models when decoding with modified\_beam\_search. The app passes customers, products, past items and the goods from `words.csv` on every recording. Hotwords need the model on disk, so until it ships as an asset pack they are behind an opt-in Settings toggle that copies the model (about 90 MB).
- **Number fixer.** Hand-written Bangla inverse text normalization: number words and compounds (একশো পঞ্চাশ), fractions (দেড়, আড়াই, সাড়ে), units (কেজি, লিটার, পিস, হালি, ডজন), টাকা, and Bengali digits ০–৯. This is the part to unit-test hardest.
- **Rule parser.** A keyword grammar: entry type from words like বাকি, দিল/দিয়েছে, জমা, খরচ; each item as quantity, unit, name, price. Fuzzy-match names against the customer list.
- **FunctionGemma fallback (v1.1).** Google's 270M function-calling model is about [283 MB](https://soniqo.audio/guides/function-calls) and emits a strict call grammar. Out of the box it scored only 58% in one [developer test](https://medium.com/google-developer-experts/on-device-function-calling-with-functiongemma-39f7407e5d83), rising sharply after fine-tuning on a few hundred examples. It is English-tuned, so fine-tune it on synthetic Bangla ledger sentences and ship it as an optional download.
- **Accuracy gate before launch.** Record 300 real sentences from 10 shopkeepers. Target: 9 in 10 entries saved with no edits.

## Android architecture

A standard modern stack, with no server needed for v1: the ledger, the speech model and the parser all live on the phone.

&#91;embedded content: module layout · 3 layers, 9 modules\]

The record module is the product; the other screens exist to serve it.

- **UI.** Kotlin, Jetpack Compose, Material 3, single activity, type-safe Navigation Compose. MVVM with one immutable UiState per screen and unidirectional events.
- **DI and async.** Hilt, Coroutines and Flow; the ASR runs on a dedicated single-thread dispatcher so the UI never stalls.
- **Storage.** Room encrypted with SQLCipher. Store money as Long in poisha, never Double. Every entry is append-only with an edit history, so balances are auditable.
- **Speech.** The sherpa-onnx Android AAR (JNI). Build for arm64-v8a and armeabi-v7a, to cover older budget phones.
- **Model delivery.** Ship the \~90 MB Bangla model as an install-time Play Asset Delivery pack, so voice works offline from the first launch. FunctionGemma arrives later as an on-demand pack.
- **Background work.** WorkManager for the Pro Drive backup and model downloads only.
- **Testing.** `test_sentences.csv`, real sentences with expected type, customer, total and paid, drives parser tests (`SentenceCorpusTest`); the lexicon CSVs are validated by `LexiconFilesTest`. Pure-Kotlin tests also run without the Android SDK (`./gradlew -p tools/jvm-check test`).
- **Backend.** None in v1. Add a small Laravel + MySQL service only when business sponsorship needs sponsor codes and a dashboard.

## Screens and UX rules

Seven screens, built around one thumb-reachable mic button. A shopkeeper should be able to record a sale while holding goods in the other hand.

| Screen | Shows | Key interaction |
| --- | --- | --- |
| Home (Today) | Today's sales, baki given, collected; recent entries | Hold the large mic button at the bottom centre |
| Listening sheet | Live partial transcript and a level meter | Release to parse; swipe down to cancel |
| Confirm card | Customer chip, item rows, total, paid, due, entry type | Save, edit a field, or say হ্যাঁ; unsure fields tinted amber |
| Customers | All customers, highest due first, search | Tap for detail; long-press to call |
| Customer detail | Balance header and entry timeline | Tagada share, call, add entry for this customer |
| Reports | Day and month totals | Export PDF |
| Settings | Language, digits, lock, backup, Pro | One screen, no nesting |

- **Bangla first.** Bangla UI by default with an English switch; Noto Sans Bengali; choice of Bengali or Latin digits.
- **Nothing auto-saves.** Every entry passes the confirm card, and a 5-second Undo follows every save.
- **Built for a shop counter.** 48 dp minimum targets, high contrast for daylight, haptic tick when the mic opens and closes.
- **Ads stay out of the way.** Never on Home's mic area, the listening sheet or the confirm card.
- **Fast on a 4 GB phone.** Cold start under 1.5 s; load the speech model in the background right after launch so the first tap on the mic is instant.

## Roadmap

Eleven weeks to a Bangladesh launch for one developer, with the speech-and-parsing core built and proven before any ledger screens.

&#91;embedded content: roadmap · 5 phases, 3 gates\]

If a gate fails, stop and fix that phase; do not carry a weak parser into the ledger build. The last gate is Google's own rule: new personal developer accounts need a closed test with at least 12 testers opted in for 14 days before production.

After launch: v1.1 adds the FunctionGemma fallback and Drive backup. v2 adds a Hindi model ([IndicConformer](https://huggingface.co/meetsync/indic-conformer-onnx-sherpa) or [Vosk Hindi](https://github.com/alphacep/vosk-api)) for India, where UPI works on Play.

## Risks and next validation steps

The biggest risk is not technical: a funded incumbent adding voice, or Bangladeshi revenue staying too thin. Speed to a working, accurate product is the main defence.

| Risk | Mitigation |
| --- | --- |
| TallyKhata or Hishabee add voice entry | Ship in 11 weeks; win on offline, multi-item accuracy and a cleaner app |
| VoiceKhata expands to Bengali; its site already lists Bengali among its UI languages | Own Bangladeshi phrasing, units and dialects that an Indian team is unlikely to prioritise |
| Speech errors in noisy shops or regional dialects | Test corpus from several districts, hotwords, confirm card, typing fallback |
| Bangladeshi revenue too thin | Ads plus sponsorship first; India with a Hindi model in v2 |
| Sponsorship deal conflicts with Play policy | Get a policy read before signing anything |
| Shopkeepers fear losing their khata | Encrypted backup, PDF export and an edit history on every entry |

Do these before writing production code:

- [ ] Read the 1–3 star reviews of TallyKhata and Hishabee in the Play Store app and list the top complaints.
- [ ] Visit 10 shops with a clickable prototype; ask each owner to speak 30 real entries.
- [ ] Run the Bengali Zipformer on those recordings on a 4 GB phone and measure accuracy.
- [ ] Ask 3 distributors whether they would pay to give the app to their retailers.

## Sources

- [Google Play Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738)
- [Google Play user choice billing, eligible countries](https://support.google.com/googleplay/android-developer/answer/13821247)
- [nsave: Google Pay and Play payments in Bangladesh, April 2026](https://www.nsave.com/bangladesh/google-pay)
- [TallyKhata: khata features and market size](https://www.tallykhata.com/en/khata-features/)
- [TallyKhata on Uptodown: 5M+ users, QR voice alerts](https://tallykhata.en.uptodown.com/android)
- [TallyKhata employee reviews, Glassdoor](https://www.glassdoor.com/Reviews/TallyKhata-Reviews-E5750256.htm)
- [Hishabee](https://www.hishabee.io/)
- [VoiceKhata features](https://voicekhata.com/features/)
- [PocketLedger on Google Play](https://play.google.com/store/apps/details?id=com.pocketledger.app)
- [Voice-Khata, Bangla prototype on GitHub](https://github.com/sagorbro005/Voice-Khata)
- [dukaan-saathi, Telugu voice ledger on GitHub](https://github.com/notsravan/dukaan-saathi)
- [Bengali voice dialer on Android: Whisper vs Zipformer](https://dev.to/devksarkar/i-tried-to-build-a-bengali-voice-dialer-for-android-here-is-what-actually-happened-and-how-i-40ak)
- [sherpa-onnx hotwords documentation](https://k2-fsa.github.io/sherpa/onnx/hotwords/index.html)
- [FunctionGemma 270M, Soniqo guide](https://soniqo.audio/guides/function-calls)
- [On-device function calling with FunctionGemma, Medium](https://medium.com/google-developer-experts/on-device-function-calling-with-functiongemma-39f7407e5d83)
- [IndicConformer ONNX for sherpa-onnx](https://huggingface.co/meetsync/indic-conformer-onnx-sherpa)
- [Vosk speech recognition](https://github.com/alphacep/vosk-api)
