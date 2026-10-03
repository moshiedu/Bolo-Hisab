# Editing the Bolo Hisab lexicon (CSV)

The words, sentences and dialect forms that Bolo Hisab knows come from CSV files. Edit them in
Excel or Google Sheets. The app reads them directly, so no code changes are needed.

| File | Where | Ships in the app |
| --- | --- | --- |
| `words.csv` | `core/nlu/src/main/resources/com/bolohisab/nlu/lexicon/` | yes |
| `phrases.csv` | same folder | yes |
| `next_words.csv` | same folder | yes |
| `dialect.csv` | same folder | yes |
| `test_sentences.csv` | `core/nlu/src/test/resources/com/bolohisab/nlu/` | no (tests only) |

## Workflow

1. Import the file into a sheet: *File → Import* in Google Sheets, or open it in Excel as UTF-8.
2. Edit rows. Keep the header row and the column names exactly as they are.
3. Export as CSV (*Download → .csv* in Sheets, or *CSV UTF-8* in Excel) and replace the file.
4. Run `./gradlew :core:nlu:test`. The tests list every problem with its file and line number:
   duplicates, a missing Bangla word, a malformed alias, a dialect word that clashes with grammar,
   or a sentence the parser now gets wrong.

In every file:
- A blank row is ignored.
- A row whose first cell starts with `#` is a comment.
- The `note` column is for people; the app ignores it.
- Several values in one cell are separated with `|`.

## words.csv: words the typing help suggests

| Column | Meaning | Example |
| --- | --- | --- |
| `word` | The Bangla word or short name, in standard spelling | `পেঁয়াজ` |
| `aliases` | English or Banglish spellings people type for it, separated by `\|` | `onion\|peyaj\|piyaj` |
| `category` | Group, for your own sorting: `money`, `grocery`, `pharmacy`, `hardware`, `unit`, `question_time`, `people`, `shop_cost`, `number` | `grocery` |
| `note` | Free text | |

You don't need to list every Banglish spelling. Loose spellings already match by sound
("piyaj", "peyaj"). Use aliases for English words ("onion") and for spellings that sound
different from the Bangla.

## phrases.csv: whole sentences

| Column | Meaning | Example |
| --- | --- | --- |
| `phrase` | Two or more Bangla words | `আজ মোট বিক্রি কত` |
| `category` | `question`, `entry`, `expense` or `item` | `question` |

When someone types the first word, the rest of the sentence is offered. Only add a sentence the
app understands: if it's a ledger sentence, also add it to `test_sentences.csv`.

## next_words.csv: what usually comes next

| Column | Meaning | Example |
| --- | --- | --- |
| `after` | One Bangla word, or `<number>` (after any number) or `<customer>` (after a customer's name) | `কেজি` |
| `next` | A word or short phrase offered after it | `চাল` |

Write one row for each pair. **Row order is rank order**: the first `after কেজি` row is offered
first. On every phone, these hints are combined with what that shop has typed itself.

## dialect.csv: regional and colloquial words

| Column | Meaning | Example |
| --- | --- | --- |
| `word` | The regional form, as speech recognition writes it | `টেঁয়া` |
| `standard` | The standard Bangla it means | `টাকা` |
| `region` | `colloquial`, `chattogram`, `sylhet`, `noakhali`, `barishal`, … | `chattogram` |

The word is replaced **everywhere** before parsing. Only add a word that has a single meaning in
a ledger. Leave out words that are also names or ordinary words (for example টিয়া is also a
parrot and a girl's name). The tests reject words that are already grammar, unit or number words.

## test_sentences.csv: real shop sentences

Every row is checked against the parser on each test run. Collect sentences exactly as
shopkeepers say or type them.

| Column | Meaning | Example |
| --- | --- | --- |
| `sentence` | The sentence, unchanged | `মিলির কাছে আড়াইশো টাকা বাকি` |
| `type` | `cash_sale`, `credit_sale`, `payment`, `expense`, `due_question`, `sales_question`, `top_debtors` or `unrecognized` | `credit_sale` |
| `customer` | The expected customer name; `-` for none | `মিলি` |
| `total` | Expected total in taka | `250` |
| `paid` | Expected paid amount in taka | `0` |
| `items` | Expected number of item lines | `0` |
| `note` | Where it came from, the region, etc. | `Mirpur grocery, voice` |

Leave a cell blank to skip that check. The known customers in tests are রহিম, আব্দুল করিম,
মিলি and জাফর. Any other name is treated as a new customer.

A failing row isn't always a mistake in the sheet. It may be a sentence the parser can't handle
yet, which tells us what to work on next. Keep the row and mark it in `note`.
