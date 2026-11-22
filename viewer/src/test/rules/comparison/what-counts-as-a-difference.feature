# SPDX-License-Identifier: MIT
Feature: What counts as a difference, and how its edits show
  A comparison counts as a difference only a change of the words of the law. It shows the words
  that an act inserted and the words that it deleted: the edits.

  Rule: R-DIFF-01 One difference for each occurrence
    A difference is an occurrence that is inserted, deleted, or whose own words changed: its
    number, its heading, its blocks that are not in an occurrence of their own, and the footnotes
    that it refers to. An occurrence in it is a difference of its own. An inserted or deleted
    occurrence is one difference, with the occurrences in it.

    Example: A point does not differ where only a point in it changed
      Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 22 March 2026
      When the reader reads the difference at the changed point 12.6.2
      Then point 12.6 does not differ

    Example: An inserted point is one difference, with the points in it
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 31 December 2021
      When the reader reads the difference at the inserted point 6.8.7
      Then it shows the occurrences in it whole, and none of them is a difference of its own

  Rule: R-DIFF-02 The changes of the same acts are one difference
    A changed occurrence and the changed or deleted occurrences in it are one difference, shown
    whole, if the same acts made all of them. If not, the occurrence shows only its own content,
    and the occurrences in it that differ are differences of their own.

    Example: The same act changed a point and its points
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 31 December 2021
      When the reader reads the difference at the changed point 11.1.3
      Then it shows the occurrences in it whole, and none of them is a difference of its own

    Example: Other acts changed the occurrences in an appendix
      Given the comparison of the texts of 2015/1998 of 29 February 2016 and of 16 February 2020
      When the reader reads the difference at the changed Appendix 5-A
      Then it shows only its own content, and the occurrences in it that differ are differences of their own

  Rule: R-DIFF-03 A full stop after a number is no change
    "11.3" and "11.3." are the same number. The Publications Office sets the full stop. An
    occurrence whose number only gained or lost its full stop does not differ.

    Example: A point whose number lost its full stop
      Given the comparison of the texts of 2015/1998 of 2 July 2020 and of 31 December 2021
      Then point 11.3 does not differ

  Rule: R-DIFF-04 Spaces are no change
    A run of spaces, non-breaking spaces too, counts as one space. An occurrence whose only change
    is its spaces does not differ.

    Example: A point whose only change is its spaces
      Given the texts of Decision 2022/2319 of 16 December 2024 and of 26 March 2025
      Then Annex I, point 1 does not differ

  Rule: R-DIFF-20 The form of apostrophes, quotation marks and form blanks is no change
    The Publications Office chooses the form of an apostrophe or a quotation mark when it sets a
    text. No act changes it. So "institution's" and "institution’s" are the same, and an occurrence
    whose only change is this form does not differ. In a changed occurrence, a word whose only
    change is its apostrophe has no edit. A quotation mark alone keeps its form, so an opening mark
    pairs only with an opening mark.
    A form blank is the same kind of thing: an ellipsis that stands alone as a word, where a form
    leaves room to fill in. "Make: …" and "Make:" are the same.

    Example: A point whose only change is the form of its quotation marks
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      Then Article 3(1)(60) does not differ

    Example: A word whose only change is its apostrophe has no edit
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 15 March 2021
      When the reader reads the difference at the changed point 6.4.1.2(c)
      Then it does not edit "applicant’s"

    Example: A quotation mark pairs only with a quotation mark of its own form
      Given the comparison of the texts of 2015/1998 of 31 December 2021 and of 1 January 2026
      When the reader reads the difference at the changed point 11.0.2
      Then it inserts "’ and ‘recertification"

    Example: A form blank that only the earlier text prints
      Given the texts of 692/2008 of 4 February 2015 and of 1 January 2016
      Then Annex I, point 2.8 does not differ

  Rule: R-DIFF-05 A kept deletion is not words of the law
    The words that a consolidated text keeps of a deletion, marked as deleted, are not words of the
    law. So a word that an act deleted is deleted, though the text still keeps it.

    Example: A word deleted, which the later text keeps as a kept deletion
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      When the reader reads the difference at the changed Article 18(e)
      Then it deletes "or"

  Rule: R-DIFF-06 Footnotes belong to their occurrence
    A footnote is compared with the occurrence that refers to it, through the reference and not
    by its number. So an occurrence whose footnote has another number, with the same words, does
    not differ. A footnote that differs shows under its occurrence, with its edits.

    Example: An occurrence that refers to a footnote that was inserted
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 15 March 2021
      When the reader reads the difference at the changed point 6.3.1.5
      Then it shows the footnotes that it refers to, under it, with their edits

    Example: A footnote that has another number
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 15 March 2021
      Then point 6.8.1.1(b) does not differ, though its footnote has another number
      And its footnote is footnote 5 in the earlier text and footnote 7 in the later text

  Rule: R-DIFF-07 A preamble that only one text has is not compared
    The Publications Office publishes some consolidated texts without the preamble, though no act
    deleted it. So if only one text has a preamble, its citations and recitals are not compared.

    Example: A consolidated text without the preamble
      Given the comparison of the texts of 2015/1998 of 15 November 2015 and of 16 February 2020
      Then the later text has no preamble
      And no citation and no recital differs

  Rule: R-DIFF-08 A word has a letter or a digit
    Spaces and punctuation alone are not words. A difference counts only its words.

    Example: The words of an inserted paragraph
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      When the reader reads the difference at the inserted Article 100(3)
      Then it inserts 84 words

  Rule: R-DIFF-15 What the edits show
    A changed occurrence shows the words of the later text, and the words of the earlier text
    deleted where they stood. An inserted occurrence shows all its words inserted. A deleted
    occurrence shows all its words deleted, as the earlier text has them.

    Example: Words replaced in a point
      Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 22 March 2026
      When the reader reads the difference at the changed point 12.6.2
      Then it deletes "1 October 2025", and inserts "31 December 2026" just after it

    Example: An inserted paragraph
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      When the reader reads the difference at the inserted Article 100(3)
      Then all its words are inserted

    Example: A deleted point
      Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 22 March 2026
      When the reader reads the difference at the deleted point 12.9.3.14
      Then all its words are deleted

  Rule: R-DIFF-16 A replaced phrase
    A replaced phrase reads as one deletion and one insertion, not as a deletion and an insertion
    for each word. An inserted phrase stays whole, though a word of it is also in the words around
    it.

    Example: A phrase replaced in a point
      Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 22 March 2026
      When the reader reads the difference at the changed point 12.9.1.8
      Then it deletes "by use of free running or remote explosive scent tracing method"
      And it inserts "only for the methodology and the area(s) for which it has been approved"

    Example: An inserted phrase stays whole
      Given the comparison of the texts of 2015/1998 of 31 December 2021 and of 1 January 2026
      When the reader reads the difference at the changed Appendix 3-B, indent 9
      Then it inserts "Republic of Serbia, in regard to Belgrade Nikola Tesla Airport"

  Rule: R-DIFF-17 A formula is one piece
    A formula is compared whole, not symbol by symbol: its symbols alone do not read. An inserted
    formula shows inserted whole.

    Example: A formula inserted in an article
      Given the texts of Decision 2005/7 as adopted and of 15 October 2013
      When the reader reads the difference at the changed Article 2
      Then it inserts a formula whole

  Rule: R-DIFF-18 Deleted text stands where it was
    A deleted block or occurrence shows where it stood in the earlier text, before what was
    inserted in its place.

    Example: A deleted paragraph before the points inserted in its place
      Given the comparison of the texts of 2015/1998 of 29 February 2016 and of 16 February 2020
      When the reader reads the difference at the changed point 11.4.2
      Then it deletes "The results of testing shall be provided to the person and recorded, and may be taken into consideration as part of the recertification or reapproval process.", and inserts "(a)" just after it

    Example: A deleted point before the occurrence inserted in its place
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      When the reader reads the difference at the deleted Article 23(1)(e)
      Then the next difference is inserted, in Article 23(1)
