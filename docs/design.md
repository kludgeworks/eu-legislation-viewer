<!-- SPDX-License-Identifier: MIT -->

# Design of the viewer

This document records the product decisions for the pages of the viewer. It is the shared
reference for design work and for reviews.

Each item has a status:

- **Convention**: the pages do this now. Change it only with a decision.
- **Open**: no decision yet. Do not treat it as a rule.

This document is in Simplified Technical English (ASD-STE100). Keep it so: short sentences, active
voice, one word for one thing.

## What is not in this document

- **Rules of the law.** The Business rules catalogue says what is true about the law: dates,
  versions, differences. This document says how a page shows it.
- **Token values and commands.** The source files have them. This document links to the files.
- **Decisions for one feature.** A design brief for that feature has them.

## The reader

**Convention.** The reader is a lawyer. The reader knows EU law and EUR-Lex. The reader does not
know this viewer.

Precision is more important than speed. The most important facts are dates: from when an act or a
provision applies, and until when.

## Reader tasks

**Convention.** A page helps the reader do one of these tasks.

| Task | Page |
| --- | --- |
| Find an act | Home |
| See the amending acts of an act, and their state on a day | History |
| See what one amending act changed, and from when each change applies | The page of an amending act |
| Read the law as it was on a day | Read |
| See if the text of that day is the law on that day | Read: the status lines and the marks in the text |
| See which acts changed one provision | Read: the history of a provision |
| Compare the wording on two days | Compare, from the history of a provision or from a period of the History |
| Find which act made a change, and from when it applies | Compare: the side note |
| Go to a different day | The form with the date, on History and on Read |

The amendment is the primary subject. A page shows what an amending act changed, and if each
change applies on a day. An act that has amending acts opens on History. An act that no act changed
opens on Read. The tab of the History has the label "Amending acts" and their number. The tab
does not operate if the act has no amending act and no corrigendum. If the act has no amending act
and has corrigenda, the tab gives their number also: "Amending acts (0) · Corrigenda (3)".

No tab opens Compare. The reader gets there from the history of a provision on Read, or from a
period of the History. Compare shows two days. It does not name versions, and it has no act filter.

The pages have no timeline and no calendar. A form with a date sets the day. The form does not
show which days have a change: the History lists them.

## Terms

**Convention.** Use one term for one thing, on the pages and in documents.

| Term | Meaning |
| --- | --- |
| act | A legal act, for example Regulation 2015/1998. Cite it by its number: "2015/1998". |
| amending act | An act that changes a different act. |
| corrigendum | A correction of an act. It is not an amending act. |
| version | The text of an act from one day. It is the act as published, or a consolidated version. The pages do not use this word. They say "text": "the text of 31 December 2021". |
| the law on a day | What applies on that day. It is not always the text of a version. |
| provision | A part of the text with its own place: an article, a paragraph, a point. |
| difference | A provision that an act inserted, deleted or changed between two versions. |
| the two days of a comparison | The days that the reader gave to Compare. From is the earlier day, To the later day. |
| steps | The changes of one provision between the two days of a comparison, oldest first. |
| side note | The note beside a difference. It names the act and the date. |
| status line | One line on the Read page about one act, where the text is not the law on the day. The order is: the act, its state, the date, the count of provisions. |
| marker | The sign in the text of Read where the text does not show a change: "Not in this text". |
| fold | A group of provisions that the page hides until the reader opens it. |
| outline | The list of the parts of the text, beside the text. |
| the day | The day that a page shows. The default is today. The address keeps a different day. |
| state | How an act or a change stands on the day: "Applies", "Applies in part", "In force, does not apply yet", "Not in force yet". |
| stage | A group of changes of one act that apply from the same day. |
| history | The list of the amending acts of an act, newest first. |
| history of a provision | The list of the acts that changed one provision, newest first. Read shows it under the provision. The ▼ mark of the provision opens it. A provision that has a history and no ▼ mark has a hollow sign, ▽, in the same place. |

Do not use the labels of EUR-Lex for amending acts (M1, M2). Name the act by its number.

The pages do not use these words in text that the reader sees: "late", "early", "included",
"version", "an act not known", "an act not named", "not included". Say what the reader sees. Examples:
"the text", "does not apply yet", "the data does not say which act changed this". The pages of the API
are not in this rule: `versions` is the name of a resource of the API.

## Principles

**Convention.**

1. **The legal text is the content.** All other elements are there to help the reader read it or
   compare it.
2. **Three layers stay apart.** The reader must see at once which layer an element is in:
   - the legal text
   - the differences: inserted, deleted and corrected words
   - the words of the viewer: side notes, warnings, labels, folds
3. **Show only what has legal effect.** Before you add a line or a label, ask if it changes what
   the reader does. Keep notes about how the viewer made the data to a minimum.
4. **Do not guess.** If the data does not name an act or a date, say so. For example: "The data
   does not say which act inserted this provision. It first appears in the text of 29 December
   2017." Do not hide the difference.
5. **Give the source of each date.** Show the act, its Official Journal reference and the clause
   that sets the date.
6. **Show the state of the page.** The reader must see the day, or the two days of a comparison.
7. **Give a limit a way out.** A period, a stage or a provision that limits a page shows a link
   back to all.
8. **A view only formats.** Rules about the law are domain methods with tests. See the
   package documents of the view packages.
9. **The tense obeys the day.** For today or a later day: "does not apply yet". For a day in the
   past: "did not apply yet".
10. **A corrigendum is not a row of the history.** It has its own list, because it has no day from
    which it applies.

## Colour

**Convention.** A colour has one meaning. Use the token, not a value. Each token has a light value
and a dark value. The tokens are in
[site.css](../viewer/src/main/resources/static/css/site.css).

| Colour | Tokens | Meaning |
| --- | --- | --- |
| Green | `--accent…` | Navigation and selection: links, the selected version, the selected view. |
| Blue | `--ins-…` | Inserted words, and the side note of an inserted provision. |
| Red | `--del-…` | Deleted words, and the side note of a deleted provision. |
| Purple | `--corr-…` | A correction by a corrigendum. |
| Amber | `--warn-…` | A change does not apply on the day. On Compare, the day is the later day. |
| Grey | `--ink…`, `--muted…`, `--line…` | Text, less important text, and lines. A broken grey outline: the data does not give the state, or the text does not show a change (the marker). In the outline, a hollow dot is the sign of a marker. |

Colour is not the only signal. Inserted and deleted words are `ins` and `del` elements. An amber
rule has words.

**Open.**

- Blue is also the colour of the "arriving" state. Red is also the colour of a failure. Decide if
  these states get their own tokens.
- `--target`, `--cor-ink` and `--cor-line` have no use in the stylesheets. Delete them or use them.

## Layout and width

**Convention.**

- The layout adapts to the width of the window with grids and maximum widths. The stylesheets have
  no breakpoints for width.
- The legal text has a maximum line length. The text column does not grow with the window.
- A wide table scrolls in its own box. The page does not scroll sideways.
- Light theme and dark theme have the same layout.
- Motion stops for a reader who asks for reduced motion.

**Convention: responsive, not phone-specific.** A page must stay usable when the window gets
narrower or wider. Do not design a different layout for a phone.

**Open.** The widths for a review. The snapshot tool uses 1440 px only. A proposal: 1440 px and
1024 px.

## States

**Convention.** Each page that loads data has these states. Design all of them.

| State | What the reader sees |
| --- | --- |
| Preparing | The steps, the progress, and what the viewer knows about the act already. |
| Arriving | The frame of the page, with the text to come. |
| Ready | The text or the comparison. |
| Missing | The version does not exist in the language. The page offers a different language. |
| Failed | What failed, and a link to EUR-Lex. |
| Empty | No differences, or no results. The page says so in words. |

## Content that breaks layouts

**Convention.** Check a design against these.

- A long title: 2013/36 has a title of more than 40 words.
- Deep provisions: point 8.1.2.3(c) of the Annex to 2015/1998.
- Large tables with changed rows: the Annex to 2015/1998.
- Notes that provisions refer to.
- An annex that an act inserts whole.
- An act with many versions: 2015/1998 has 23.
- An act with one version: 2016/679.
- A language that a version does not have: Irish for 2015/1998 on 31 December 2021.

## Review scenarios

**Convention.** A review uses real acts. These addresses show the important cases. Add the address
of the viewer before each path.

| Case | Path |
| --- | --- |
| Home, with results | `/?q=aviation+security` |
| History, with stages and a day that the data does not give | `/eli/reg/2018/1862/eng/history?on=2022-08-01` |
| History, limited to a period | `/eli/reg_impl/2015/1998/eng/history?from=2019-01-01&to=2019-12-31` |
| An amending act with two stages | `/eli/reg/2018/1862/eng/history/32022R1190?on=2022-08-01` |
| An act that no act changed opens on Read | `/eli/reg/2016/679/eng` |
| Read the law on a day | `/eli/reg_impl/2015/1998/2021-12-31/eng` |
| Read, an act applies that the text does not have, with marks in the text | `/eli/reg_impl/2015/1998/2017-06-03/eng` |
| Read, the act itself does not apply yet | `/eli/reg/2016/679/2017-01-01/eng` |
| Read, provisions that do not apply yet | `/eli/reg/2008/767/2018-01-01/eng` |
| Read, date not confirmed | `/eli/reg/2018/1862/2026-11-17/eng` |
| Read, the history of a provision: the ▼ mark of Article 6(2) | `/eli/reg/2008/767/2026-11-17/eng#art_6` |
| Read, no text in the language | `/eli/reg_impl/2015/1998/2021-12-31/gle` |
| Compare, Changes | `/eli/reg_impl/2015/1998/eng/compare?from=2021-07-01&to=2021-12-31` |
| Compare, Whole text | the same, with `&view=whole` |
| Compare, Synopsis | the same, with `&view=synopsis` |
| Compare, a day on which an act applies that the text of the day does not have | `/eli/reg_impl/2015/1998/eng/compare?from=2016-02-29&to=2017-06-03` |
| Compare, changes that do not apply on the later day | `/eli/reg/2018/1862/eng/compare?from=2021-08-03&to=2022-08-01` |
| Compare, one provision: the wording on the two days and the steps | `/eli/reg/2008/767/eng/compare?from=2017-12-29&to=2026-06-12&unit=art_6%2Fpar_2` |
| Preparing | `/eli/reg/2010/185/eng`, the first time |

`mise run ui:snapshots` saves nine of these pages in the light theme and the dark theme. See
[snapshots.py](../tools/tasks/ui/snapshots.py). The saved pages have no scripts. Check interactions
in the viewer while it runs.

## Source files

| Subject | File |
| --- | --- |
| Colour tokens, the site bar, the theme | [site.css](../viewer/src/main/resources/static/css/site.css) |
| Home, Read, the cards | [catalogue.css](../viewer/src/main/resources/static/css/catalogue.css) |
| Compare | [comparison.css](../viewer/src/main/resources/static/css/comparison.css) |
| The legal text | [legaltext.css](../viewer/src/main/resources/static/css/legaltext.css) |
| Templates | [viewer/src/main/jte](../viewer/src/main/jte) |
| Scripts | [viewer/src/main/resources/static/js](../viewer/src/main/resources/static/js) |
| Snapshots for a review | [snapshots.py](../tools/tasks/ui/snapshots.py) |
