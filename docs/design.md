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
| See if the text of that day is the law on that day | Read: the status lines and the divergence notes |
| See which acts changed one provision | Read: the history of a provision |
| Compare the wording on two days | Compare, from the history of a provision, or from the day control with two days |
| Find which act made a change, and from when it applies | Compare: the sidenote |
| Go to a different day, or to two days | The day control, on each page of an act |

The amendment is the primary subject. A page shows what an amending act changed, and if each
change applies on a day. An act that has amending acts opens on History. An act that no act changed
opens on Read. The tab of the History has the label "Amending acts" and their number. The tab
does not operate if the act has no amending act and no corrigendum. If the act has no amending act
and has corrigenda, the tab gives their number also: "Amending acts (0) · Corrigenda (3)".

No tab opens Compare. The reader gets there from the history of a provision on Read, or from the
day control with two days. Compare shows two days. It does not name versions, and it has no act
filter.

The pages have no timeline. The day control sets the day of a page:

- It is one field. The reader types a day in it, or opens its calendar.
- The calendar opens on one day. On History, Read and Compare, a switch lets the reader select two
  days. The page of an amending act takes one day only.
- With two days, History lists the amending acts between the days, with their state on the later
  day, and a button opens Compare. Read goes to Compare. The acts between two days are the same
  on History and on Compare.
- With one day on Compare, the page goes to History.
- A dot marks each day from which an amending act, or a part of one, applies. The hover of the
  day names its acts, three at most, and then says how many more there are.
- The days between two days have no shade. The day of the page has a fill. The first of two days
  has an outline.
- "Previous change" and "Next change" move to the nearest day with a dot. With two days, they
  also set the day box that has the frame to that day. The frame stays on that box, so the
  reader can go from change to change.
- Without scripts, the control is a form with date boxes.

## Terms

**Convention.** Use one term for one thing, on the pages and in documents.

Each module has its own terms. The shared terms mean the same in every module. Each group below
says which module owns its terms. A term that is not settled yet is in the list of open terms of
its module.

### Shared terms

| Term | Meaning |
| --- | --- |
| act | A legal act, for example Regulation 2015/1998. Cite it by its number: "2015/1998". |
| amending act | An act that changes a different act. |
| corrigendum | A correction of an act. It is not an amending act. |
| version | The text of an act from one day. It is the act as published, or a consolidated version. The pages do not use this word. They say "text": "the text of 31 December 2021". |
| provision | A part of the act that has legal effect and its own place: an article, a paragraph, a point, an annex or a part of an annex. A citation or a recital is not a provision. |
| place | A subdivision of an act, named in words as the Joint Practical Guide names it (15.4). For example: "Article 3(1)(a)", "point 8.1.2.3(c)". A subparagraph is not named, and the annex is not named if the act has one annex. Cellar codes a place as a location, and the text knows it by an eId. |
| the day | The day that a page shows. The default is today. The address keeps a different day. |
| the law on a day | What applies on that day. It is not always the text of a version. |

### Legal text (module `legaltext`, and the converter)

| Term | Meaning |
| --- | --- |
| occurrence | A subdivision as the text of one version has it, known by its eId. A provision has an occurrence in each text that has it. |
| tag | The sign ▼ in a consolidated text that names the act that gave a provision its wording, and what the act did. EUR-Lex calls these signs tags: B, M1, C1, A1. |
| modification | What an act did to an occurrence in one step: inserted, deleted, replaced, renumbered, amended or corrected. A tag names the act and its modification. Akoma Ntoso calls it a textual modification. |
| footnote | A note of the text that a reference in the text points to. The notes are at the end of the text, or in a table. The word "note" alone is not a term: say footnote, sidenote or divergence note. |

### Comparison (module `comparison`)

| Term | Meaning |
| --- | --- |
| comparison | How the text of one day differs from the text of another day. The reader asks for it on Compare. |
| the two days of a comparison | The days that the reader gave to Compare. From is the earlier day, To the later day. |
| difference | A subdivision that differs between the two texts of a comparison: inserted, deleted, or with changed wording of its own. It is a provision, a citation or a recital. |
| edits | The inserted and deleted words of a difference. Blue shows an insertion and red a deletion. |
| run | A sequence of words of a difference that is the same in both texts, inserted or deleted. The inserted and deleted runs are the edits. |
| pairing | Which occurrence of the later text is which occurrence of the earlier text. An occurrence that an act renumbered or moved pairs with the occurrence that it was. |
| steps | The changes of one provision between the two days of a comparison, oldest first. |
| sidenote | The note beside a difference. It names the act that made the difference, and the date. Where no known act made it, the sidenote says only the modification: "Inserted", "Deleted" or "Amended" (R-WHO-14). |
| neighbour | An occurrence near a difference that is the same in both texts. Synopsis shows it for context. |
| Differences, Whole text, Synopsis | The three views of a comparison. Differences shows only the differences. Whole text shows the later text with its edits in place. Synopsis shows the two texts side by side, with neighbours. |

The date line of a sidenote says from when the change of each act applies (R-WHO-15 to
R-WHO-20). It names the act, its Official Journal reference, the date, and the clause that sets
the date. The clause links to EUR-Lex. The forms:

| Case | Words |
| --- | --- |
| One day | "2017/815 · OJ L 122, 13.5.2017 · applies from 1 June 2017 · Article 2 of 2017/815" |
| Only its entry into force | "2019/1583 · OJ L 246, 26.9.2019 · applies from 31 December 2021, its entry into force · Article 2 of 2019/1583, as amended by 2020/910" |
| In parts | "2026/449 · OJ L, 2026/449, 2.3.2026 · applies in parts: from 22 March 2026, its entry into force; in part from 1 October 2026 · Article 2 of 2026/449 says which part from when" |
| A clause sets the day | "2019/413 · OJ L 73, 15.3.2019 · applies from a date set in Article 3 of 2019/413" |
| A directive in force | "Dir. 2002/73/EC · OJ L 269, 5.10.2002 · in force from 5 October 2002 · Article 3 of Dir. 2002/73/EC" |
| A correction | "Corrigendum 1 to 2019/103 corrects 2019/103 from 31 December 2021 · first in the text of 31 December 2021" |
| Nothing is known | no date line |

The first line of a sidenote names the acts that made the difference, one step for each text,
oldest first (R-WHO-01 to R-WHO-12, R-WHO-22). Each step says its modification and its acts. The
first word has a capital letter. The steps after it start with "then" and a lower-case word. The
forms:

| Case | Words |
| --- | --- |
| One act | "Replaced by 2026/449" |
| Several steps | "Inserted by 2019/103, then replaced by 2023/566" |
| Acts of one step | "Replaced by 2019/103 and 2019/1583 together" |
| Acts of one step, with the comparison limited to one of them | "Replaced by 2019/1583, together with 2019/103" |
| Three or more acts | "2017/815, 2019/103 and 2019/1583" |

Where nothing names an act (R-WHO-07), the first line names the text where the change first
appears, and the acts that this text consolidates. It does not name an act as the one that made
the change. The page says "text", not "version". If the text consolidates no act, the words
", which consolidates …" are left out. The forms:

| Case | Words |
| --- | --- |
| The text where the change first appears | "Deleted in the text of 22 March 2026, which consolidates 2026/449 and Corrigendum 1 to 2026/449" |
| Nothing shows where it appears | "The data does not say which act amended this." The verb is the modification of the difference: inserted, deleted or amended (R-WHO-14). |

Synopsis shows the two texts side by side, provision by provision (R-SHOW-10). Near each
difference it shows its neighbours. Where more than three neighbours are in a row, it shows the
first and the last, and a fold holds the others. With three or fewer, it shows all of them. Beside
each neighbour, a line says what last made it as it is (R-WHO-21), with the date of that act. A
corrigendum is not named there. The forms:

| Case | Words |
| --- | --- |
| An act last changed it | "Last changed by 2020/910 (replaced) · applies from 2 July 2020, its entry into force · Article 5 of 2020/910" |
| No act changed it | "As in the original act · applies from 1 February 2016 · Article 2 of 2015/1998" |
| No act changed it, in a directive | "As in the original act · in force from 17 July 2013 · Article 164 of Dir. 2013/36/EU" |

### Page words (all modules)

| Term | Meaning |
| --- | --- |
| summary | The line above the differences of a comparison: "No provisions differ", "1 provision differs" or "6 provisions differ". If one act made every difference, it adds the act: "by 2026/449" (R-SHOW-06). |
| fold | A group that the page hides until the reader opens it: provisions, the rows of a table, or a list of dates. |
| outline | The list of the parts of the text, beside the text. |

### Catalogue (module `catalogue`)

The terms of this module are not reviewed yet.

Open terms of this module:

- "step": the preparation of an act has steps, and the day picker steps from change to change.
  In the comparison module, "steps" are the changes of one provision only.
- "run": `AmendingView.Run` is a group of the changes of an amending act that have one state.
  In the comparison module, a run is a sequence of words. Call this one a group.

| Term | Meaning |
| --- | --- |
| state | How an act or a change stands on the day: "Applies", "Applies in part", "In force, does not apply yet", "Not in force yet". It is not a loading state of a page. |
| deferred application | The state of an act that is in force but does not apply on the day yet, in full or in part. The status line names the day from which it applies in full. |
| pending amendment | An amending act that applies on the day, but the text of the day does not include it. EUR-Lex calls these "pending amendments". If the data does not give the day from which it applies, it is an undated pending amendment. |
| prospective amendment | An amendment that the text of the day includes, but that does not apply on the day yet. |
| status line | One line on the Read page about one act, where the text is not the law on the day. The order is: the act, its state, the date, the count of provisions. |
| divergence note | The note beside the text of Read where the text is not the law on the day. A pending amendment gets a grey note: "Not in this text". A prospective amendment gets an amber bar and a note. |
| history | The list of the amending acts of an act, newest first. |
| history of a provision | The list of the acts that changed one provision, newest first. Read shows it under the provision. The ▼ tag of the provision opens it. A provision that has a history and no ▼ tag has a hollow sign, ▽, in the same place. |

Do not use the labels of EUR-Lex for amending acts (M1, M2). Name the act by its number.

The pages do not use these words in text that the reader sees: "late", "early", "included",
"version", "stage", "an act not known", "an act not named", "not included". Say what the reader sees. Examples:
"the text", "does not apply yet", "applies in parts", "the data does not say which act amended this". The pages of the API
are not in this rule: `versions` is the name of a resource of the API.

## Principles

**Convention.**

1. **The legal text is the content.** All other elements are there to help the reader read it or
   compare it.
2. **Three layers stay apart.** The reader must see at once which layer an element is in:
   - the legal text
   - the differences: inserted, deleted and corrected words
   - the words of the viewer: sidenotes, warnings, labels, folds
3. **Show only what has legal effect.** Before you add a line or a label, ask if it changes what
   the reader does. Keep remarks about how the viewer made the data to a minimum.
4. **Do not guess.** If the data does not name an act or a date, say so. For example: "The data
   does not say which act inserted this provision. It first appears in the text of 29 December
   2017." Do not hide the difference.
5. **Give the source of each date.** Show the act, its Official Journal reference and the clause
   that sets the date.
6. **Show the day of the page.** The reader must see the day, or the two days of a comparison.
7. **Give a limit a way out.** Two days, a day of application or a provision that limits a page
   shows a link back to all.
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
| Blue | `--ins-…` | Inserted words, and the sidenote of an inserted provision. |
| Red | `--del-…` | Deleted words, and the sidenote of a deleted provision. |
| Purple | `--corr-…` | A correction by a corrigendum: a difference that only corrigenda made (R-WHO-10). Its inserted words have a dotted underline and its deleted words are struck through, both purple, not blue and red. |
| Amber | `--warn-…` | A change does not apply on the day. On Compare, the day is the later day. |
| Grey | `--ink…`, `--muted…`, `--line…` | Text, less important text, and lines. A broken grey outline: the data does not give the state, or the text does not show a change (the divergence note of a pending amendment). In the outline, a hollow dot is the sign of that note. |

Colour is not the only signal. Inserted and deleted words are `ins` and `del` elements. An amber
bar has words.

**Open.**

- Blue is also the colour of the "arriving" loading state. Red is also the colour of a failure.
  Decide if these loading states get their own tokens.
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

## Loading states

**Convention.** Each page that loads data has these loading states. Design all of them.

| Loading state | What the reader sees |
| --- | --- |
| Preparing | The stages, the progress, and what the viewer knows about the act already. |
| Arriving | The frame of the page, with the text to come. |
| Ready | The text or the comparison. |
| Missing | The version does not exist in the language. The page offers a different language. |
| Failed | What failed, and a link to EUR-Lex. |
| Empty | No differences, or no results. The page says so in words. |

**Convention.** A page that waits for the Publications Office shows a second line after 15 s:
"The Publications Office is slow to answer." The line needs no script. If the wait stops, the
page shows the Failed loading state. Example: the home page, when it looks up an act that the catalogue
does not have yet (`catalogue/home.jte`).

**Convention.** An act's page whose act the catalogue does not have yet keeps its address. It
says "Looking up reg/2005/396 at the Publications Office…" at once, and the viewer looks the act
up in the background. When the act arrives, the page becomes the page of its address
(`catalogue/actlookup.jte`). If no act has the address, the page says "No act has this address",
with 404 and a link to Find an act (`catalogue/nosuchact.jte`). If the Publications Office does
not answer, the page shows the Failed loading state, with Try again.

## Content that breaks layouts

**Convention.** Check a design against these.

- A long title: 2013/36 has a title of more than 40 words.
- Deep provisions: point 8.1.2.3(c) of the Annex to 2015/1998.
- Large tables with changed rows: the Annex to 2015/1998.
- Footnotes that provisions refer to.
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
| History, with an act that applies in parts and a day that the data does not give | `/eli/reg/2018/1862/eng/history?on=2022-08-01` |
| History, two days | `/eli/reg_impl/2015/1998/eng/history?from=2020-07-02&to=2021-03-15` |
| An amending act that applies in parts, from two days | `/eli/reg/2018/1862/eng/history/32022R1190?on=2022-08-01` |
| An act that no act changed opens on Read | `/eli/reg/2016/679/eng` |
| Read the law on a day | `/eli/reg_impl/2015/1998/2021-12-31/eng` |
| Read, a pending amendment: an act applies that the text does not have, with divergence notes | `/eli/reg_impl/2015/1998/2017-06-03/eng` |
| Read, the act itself does not apply yet | `/eli/reg/2016/679/2017-01-01/eng` |
| Read, prospective amendments: provisions that do not apply yet | `/eli/reg/2008/767/2018-01-01/eng` |
| Read, an undated pending amendment | `/eli/reg/2018/1862/2026-11-17/eng` |
| Read, the history of a provision: the ▼ tag of Article 6(2) | `/eli/reg/2008/767/2026-11-17/eng#art_6` |
| Read, no text in the language | `/eli/reg_impl/2015/1998/2021-12-31/gle` |
| Compare, Differences | `/eli/reg_impl/2015/1998/eng/compare?from=2021-07-01&to=2021-12-31` |
| Compare, Whole text | the same, with `&view=whole` |
| Compare, Synopsis | the same, with `&view=synopsis` |
| Compare, a day on which an act applies that the text of the day does not have | `/eli/reg_impl/2015/1998/eng/compare?from=2016-02-29&to=2017-06-03` |
| Compare, changes that do not apply on the later day | `/eli/reg/2018/1862/eng/compare?from=2021-08-03&to=2022-08-01` |
| Compare, one provision: the wording on the two days and the steps | `/eli/reg/2008/767/eng/compare?from=2017-12-29&to=2026-06-12&occurrence=art_6%2Fpar_2` |
| Preparing | `/eli/reg/2010/185/eng`, the first time |

`mise run ui:snapshots` saves each of these pages, and Home before a search, in the light theme
and the dark theme. See
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
