<!-- SPDX-License-Identifier: MIT -->

# Design brief: the amendment comes first

Date: 17 November 2026. Source: the design interview of that day (questions 1 to 10).
Wireframes: https://claude.ai/artifact/53cLMhd81a7wBRhLhoEaNT

## 1. Reader task

The primary task changes.

- Before: compare the law on two days.
- Now: see what each amending act changed in an act, and if each change applies on a day.

| Task | Page |
| --- | --- |
| See the amending acts of an act, and their state on a day | History (the page of the act) |
| See what one amending act changed, and from when each change applies | The page of an amending act |
| Read the law on a day | Read |
| See which acts changed one provision | The history of a provision, on Read |
| Compare the wording on two days | Compare, from a provision or from the day control with two days |

## 2. Behaviour

### The day

1. Each page has one day. The default is today.
2. The reader can set the day. The address of the page keeps it.
3. Each line about a state gives its day.
4. The tense obeys the day. For today: "does not apply yet". For a day in the past: "did not apply yet".

### From the search to an act

5. An act that has amending acts opens on History.
6. An act that no act changed opens on Read.
   - The tab of the History has the label "Amending acts" and the number of amending acts, for example "Amending acts (4)".
   - For an act that no act changed, the tab says "Amending acts (0)". The tab is then grey and does not operate.
   - If such an act has corrigenda, the tab is a link, because the History also shows the corrigenda. The tab then gives their number also: "Amending acts (0) · Corrigenda (3)".

### History

7. One line gives the state of the act itself on the day, with the date and the clause.
8. A list shows the amending acts, newest first. Each row has:
   - the number of the act and its short title
   - a link to the Official Journal
   - the state on the day: "Applies", "Applies in part" or "In force, does not apply yet"
   - the date and the clause that sets it
   - the number of changed provisions, as a link to the page of that act
9. The state of an amending act comes from the states of its changes.
10. A row that applies in full has no amber.
11. The day control is one field. Its calendar opens on one day, and a switch lets the reader select two days. With two days, the list has the amending acts between the days, with their state on the later day. A link "Show all" goes back to one day.
12. With two days, a button opens Compare for the two days. The acts between two days are the same on History and on Compare.
13. A separate list below the history shows the corrigenda. Each line has the day of publication, the link to the Official Journal and the corrected provisions. The list shows only the corrigenda that change the language on the page.

### The page of an amending act

14. The title is "What 2022/1190 changed in 2018/1862".
15. A summary gives the days from which the changes apply, with a count for each day. Each day is a link. The link limits the page to the changes that apply from that day. The page shows the summary only if the changes apply from two or more days.
16. The changes are in the order of the text.
17. A change that does not apply on the day has an amber rule, the date and the clause.
18. A change that applies on the day has no mark.
19. If the data does not give the day from which a change applies, the page shows the possible days and the clause. It does not give one day.
20. The data can give two acts for a change and not say which one. Then the change shows on the page of each of the two acts, in a separate short list: "By this act or by 2019/413".
21. A corrigendum of the amending act shows on this page.

### Read

22. Status lines replace the notices. One line is for one act. The order in each line is: the act, its state, the date, the count. Example for 2018/1862 on 1 August 2022:

    > **On 1 August 2022.**
    > **2018/1862** applied in part. It applies in full from 7 March 2023 · Article 79(5).
    > **2022/1190** was in force and did not apply yet. It applies from 15 October 2026. 17 provisions, marked in the text.
    > **2021/1133** was in force. Article 6 of 2021/1133 sets the day from which it applies. The data does not give a date for that day. 3 provisions, not in this text.

23. A line shows only for these acts:
    - the act itself, if it does not apply in full
    - an amending act that is in force and does not apply in full
    - an amending act with changes that the text does not show
24. If more than three lines are necessary, the page shows three and a link "Show all".
25. In the text, a change that does not apply on the day has an amber rule and a short line with the act. The act is a link to its page.
26. Where the text does not show a change, one marker is at that place. It gives the provision and the act, with a link.
27. In the outline, a part that has a rule has an amber dot. A part that has a marker has the sign of the marker.
28. If the whole act does not apply on the day, the status line says so one time. There is no rule along the text and no second note.
29. A provision that an act changed has a link to its history. The history lists the acts that changed it, with the dates, and a link to each change.
30. If more than one act changed the provision, the history has a link "See how its wording changed".
31. If the data gives two acts and does not say which one: "The data does not say which act deleted this. It was 2019/413 or 2021/255." Each number is a link.
32. If the data gives no act: "The data does not say which act inserted Article 17a. It first appears in the text of 29 December 2017." A link opens that text on EUR-Lex.

### Compare

33. No tab opens Compare. The reader gets there from the history of a provision, or from the day control with two days, on History and on Read.
34. The reader gives two days. The page shows those two days.
35. One line gives the amending acts between the two days. Each act is a link.
36. Each difference has a side note with the act and the date. A change that does not apply on the second day has the amber rule.
37. For one provision, the page shows the wording on each day and a list of the steps between them.
38. The page has no versions, no act filter and no notice about days that no version shows.

### Words that the pages stop using

"late", "early", "included", "version", "an act not known", "an act not named", "not included".

## 3. Visual direction

| Element | Layer | Tokens |
| --- | --- | --- |
| Status lines, the history list, the list of corrigenda | Words of the viewer | `--ink…`, `--muted…`, `--line…` |
| The state "In force, does not apply yet" | Words of the viewer | `--warn-…` |
| The amber rule beside a change | Words of the viewer, beside the legal text | `--warn-…` |
| The marker where the text does not show a change | Words of the viewer | `--ink…` and `--line…`, with a broken outline and the words "Not in this text" |
| The dot in the outline, for a rule | Words of the viewer | `--warn-…`, with a text label for screen readers |
| The sign in the outline, for a marker | Words of the viewer | A hollow dot in `--ink…`, with a text label |
| Inserted and deleted words | Differences | `--ins-…`, `--del-…` |
| A correction by a corrigendum | Differences | `--corr-…` |
| Links, the selected tab, the selected day of application | Words of the viewer | `--accent…` |
| The day control, its calendar, "Show all" | Words of the viewer | `--ink…`, `--line…`, `--accent…` for the day of the page |

- Amber has one meaning: this change does not apply on the day.
- The marker means: the text does not show a change here. It is grey, not amber, because it has a different meaning.
- Compare keeps its three views: Changes, Whole text and Synopsis.
- The timeline and the calendar of versions go away. The day control replaces them. Its calendar marks the days with a change, not the days of the versions.
- The link with the arrow "↑" goes away.
- No link or button is in a `summary` element.

## 4. States

| State | What the reader sees |
| --- | --- |
| Preparing | The History shows the amending acts and their dates before the texts are ready. Counts that are not ready show as "counting". |
| Arriving | The frame of the page, the day and the status lines. The text comes after. |
| Ready | The page as in section 2. |
| Missing | The act does not have the day in the language. The page offers a different language. |
| Failed | What failed, and a link to EUR-Lex. |
| Empty | History with no amending act: the reader does not land there (step 6). If the act has a corrigendum and the reader opens the tab: "No act changed 2016/679.", then the corrigenda. If it has none, the tab does not operate. Two days with no act between them: "No amending act changed the act between these days.", with "Show all". A day of application with no change: the page does not show a link for it. Compare with no difference: "The wording is the same on the two days." |

## 5. Acceptance criteria

1. **2018/1862, 1 August 2022, History.** The row of 2022/1190 says "In force, did not apply yet", with 15 October 2026 and the clause. The row of 2021/1133 says that Article 6 sets the day and that the data does not give a date for it.
2. **2018/1862, 1 August 2022, Read.** The page has the three status lines of step 22 and no other notice. Article 3, point 8 has an amber rule and names 2022/1190. A marker for Article 18a names 2021/1133. The outline has a dot on Article 3.
3. **2016/679, from the search.** The result opens Read. The tab says "Amending acts (0) · Corrigenda (n)", with the number of corrigenda of 2016/679 in English. It is a link.
4. **2016/679, 1 January 2017, Read.** One status line says that the act was in force and did not apply yet, and that it applies from 25 May 2018 · Article 99. The text has no rule along its length and no second note.
5. **2013/36, the page of 2019/878.** The summary lists each day of application with a count. Each day is a link that limits the page.
6. **767/2008, today, Read.** The history of Article 6(2) lists four acts and has the link to Compare: 2019/817 and 2021/1134 replaced the unit that holds the paragraph, and 2021/1152 and 2024/1356 replaced the paragraph. Compare shows the wording on the two days and the four steps.
7. **2015/1998, 3 June 2017.** Read has a status line for 2017/815 that says "not in this text". Compare from 29 February 2016 to 3 June 2017 shows these two days. No page says "No version shows it".
8. **2015/1998, the deletion by 2019/413 or 2021/255.** It shows on the page of each of the two acts under "By this act or by …". No page says "not known".
9. **767/2008, Article 17a.** Until ISS-200 has a solution, the provision gives the message of step 32.
10. **2016/679, History.** The corrigenda are in their own list, with the day of publication. They are not rows of the history.
11. **Tense.** The same page for today says "does not apply yet". For a day in the past it says "did not apply yet".
12. **Words.** No template has the words of the list "Words that the pages stop using" in text that the reader sees.
13. **All pages.** No page scrolls sideways in a narrower window. The light theme and the dark theme have the same layout. Each new control operates with the keyboard.

## 6. Assumptions

| Not decided | Who decides |
| --- | --- |
| The rule for the date of a changed place. ISS-195 decided it on 16 November 2026. The new pages use that rule for the state of each change. They do not add a second rule. | Decided: ISS-195 |
| Places that the viewer cannot map from Cellar to a provision. The spike of ISS-195 got no answer for 608 of 1,893 places. For these, the page shows the possible days and the clause (step 19). | The user |
| The rule for the text on a day that no version shows, for example 2015/1998 on 3 June 2017. | The Business rules catalogue, with a test |
| The rule for which acts get a status line on a day. | The Business rules catalogue, with a test |
| The solution for ISS-200. Until then, some changes have no act. | The user |
| The spike "read the amending act to find which act made a change". | The user |
| How the reader sets the day, and if the control marks the days with a change. | The builder |
| The addresses of the new pages. | The builder |
| The order of the work. A proposal: History and the page of an amending act first, then Read, then Compare. | The user |
| Values in brackets on the wireframes. They are not checked against the data. | The builder |
| No person watched a lawyer use the viewer. The tasks come from the intent of the user. | The user |

## 7. Changes to docs/design.md

- **Reader tasks.** Replace the table with the table of section 1. Replace "Comparison is the primary task" with: "The amendment is the primary subject. A page shows what an amending act changed, and if each change applies on a day."
- **Terms.** Delete "warning", "late, early" and "acts between them". Change "version": the viewer uses versions, and the pages do not show the word. Change "From, To" to "the two days of a comparison". Add:
  - the day: the day that a page shows
  - state: "Applies", "Applies in part", "In force, does not apply yet"
  - history: the list of the amending acts of an act
  - history of a provision: the list of the acts that changed one provision
  - status line: one line on Read about one act on the day
  - marker: the sign where the text does not show a change
- **Principle 4.** Replace the examples "an act not known" and "Date not confirmed" with the messages of steps 31 and 32.
- **Principle 6.** Replace with: "Show the state of the page. The reader must see the day, or the two days of a comparison."
- **Principle 7.** Replace with: "Give a limit a way out. Two days or a day of application that limits a page shows a link back to all."
- **New Convention.** The tense obeys the day.
- **New Convention.** A corrigendum is not a row of the history. It has its own list.
- **Colour.** Amber: "A change does not apply on the day." Delete "the text is not the law on the day, or an act filter is on". Grey: add "the marker where the text does not show a change".
- **States.** Add the Empty cases of section 4.
- **Review scenarios.** Replace the list with the acts and days of section 5.
