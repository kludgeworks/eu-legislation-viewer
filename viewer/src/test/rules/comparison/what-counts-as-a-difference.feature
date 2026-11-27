# SPDX-License-Identifier: MIT
Feature: What counts as a difference, and how its edits show
  A comparison counts as a difference only a change of the words of the law. It shows the words
  that an act inserted and the words that it deleted: the edits.

  Rule: one-difference-for-each-occurrence · One difference for each occurrence
    A difference is an occurrence that the comparison shows as inserted or deleted, or whose own
    words changed. The own words of an occurrence are these:
    - its number
    - its heading
    - its blocks that are not in an occurrence of their own
    - the footnotes that it refers to

    An occurrence in it is a difference of its own. An inserted or deleted occurrence is one
    difference, with the occurrences in it.

    Example: A point does not differ where only a point in it changed
      Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 22 March 2026
      When the reader reads the difference at the changed point 12.6.2
      Then point 12.6 does not differ

    Example: An inserted point is one difference, with the points in it
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 31 December 2021
      When the reader reads the difference at the inserted point 6.8.7
      Then it shows the occurrences in it whole, and none of them is a difference of its own

  Rule: same-acts-one-difference · The changes of the same acts are one difference
    A changed occurrence and the changed or deleted occurrences in it are one difference, shown
    whole, if the same acts made all of them. If not, the occurrence shows only its own content,
    and the occurrences in it that differ are differences of their own.

    Example: The same act changed a point and its points
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 31 December 2021
      When the reader reads the difference at the changed point 11.1.3
      Then it shows the occurrences in it whole, and none of them is a difference of its own

    Example: Other acts changed the occurrences in an appendix
      Given the comparison of the texts of 2015/1998 of 29 February 2016 and of 16 February 2020
      When the reader reads the difference at the changed appendix 5-A
      Then it shows only its own content, and the occurrences in it that differ are differences of their own

  Rule: full-stop-after-a-number-is-no-change · A full stop after a number is no change
    "11.3" and "11.3." are the same number. The Publications Office sets the full stop. An
    occurrence whose number only gained or lost its full stop does not differ.

    Example: A point whose number lost its full stop
      Given the comparison of the texts of 2015/1998 of 2 July 2020 and of 31 December 2021
      Then point 11.3 does not differ

  Rule: spaces-are-no-change · Spaces are no change
    A run of spaces, non-breaking spaces too, counts as one space. An occurrence whose only change
    is its spaces does not differ.

    Example: A point whose only change is its spaces
      Given the texts of Decision 2022/2319 of 16 December 2024 and of 26 March 2025
      Then annex I point 1 does not differ

  Rule: form-of-apostrophes-and-quotes · The form of apostrophes, quotation marks and form blanks is no change
    The Publications Office chooses the form of an apostrophe or a quotation mark when it sets a
    text. No act changes it. So "institution's" and "institution’s" are the same, and an occurrence
    whose only change is this form does not differ. In a changed occurrence, a word whose only
    change is its apostrophe has no edit. A quotation mark alone keeps its form, so an opening mark
    pairs only with an opening mark.
    A form blank is the same kind of thing: an ellipsis that stands alone as a word, where a form
    leaves room to fill in. "Make: …" and "Make:" are the same.

    Example: A point whose only change is the form of its quotation marks
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      Then article 3 paragraph 1 point (60) does not differ

    Example: A word whose only change is its apostrophe has no edit
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 15 March 2021
      When the reader reads the difference at the changed point 6.4.1.2 point (c)
      Then it does not edit "applicant’s"

    Example: A quotation mark pairs only with a quotation mark of its own form
      Given the comparison of the texts of 2015/1998 of 31 December 2021 and of 1 January 2026
      When the reader reads the difference at the changed point 11.0.2
      Then it inserts "’ and ‘recertification"

    Example: A form blank that only the earlier text prints
      Given the texts of 692/2008 of 4 February 2015 and of 1 January 2016
      Then annex I point 2.8 does not differ

    @known-bug @ISS-273
    Example: A form blank at the end of a table cell
      Given the texts of 692/2008 of 4 February 2015 and of 1 January 2016
      Then annex I point 1.13.3 does not differ

  Rule: kept-deletion-is-not-words · A kept deletion is not words of the law
    A consolidated text can keep the words of a deletion, with the mark of a deletion. These words
    are not words of the law. So the comparison shows a word that an act deleted as deleted, though
    the text still keeps it.

    Example: A word deleted, which the later text keeps as a kept deletion
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      When the reader reads the difference at the changed article 18 point (e)
      Then it deletes "or"

  Rule: footnotes-belong-to-their-occurrence · Footnotes belong to their occurrence
    The comparison compares a footnote with the occurrence that refers to it, through the
    reference and not by its number. So an occurrence whose footnote has another number, with the same words, does
    not differ. A footnote that differs shows under its occurrence, with its edits.

    Example: An occurrence that refers to an inserted footnote
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 15 March 2021
      When the reader reads the difference at the changed point 6.3.1.5
      Then it shows the footnotes that it refers to, under it, with their edits

    Example: A footnote that has another number
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 15 March 2021
      Then point 6.8.1.1 point (b) does not differ, though its footnote has another number
      And its footnote is footnote 5 in the earlier text and footnote 7 in the later text

    @known-bug @ISS-271
    Example: The indents of a footnote whose number changed
      Given the texts of 692/2008 of 19 March 2013 and of 1 July 2013
      Then no indent of a footnote is a difference of its own

  Rule: preamble-of-one-text · The comparison does not compare a preamble that only one text has
    The Publications Office publishes some consolidated texts without the preamble, though no act
    deleted it. So if only one text has a preamble, the comparison does not compare its citations
    and recitals.

    Example: A consolidated text without the preamble
      Given the comparison of the texts of 2015/1998 of 15 November 2015 and of 16 February 2020
      Then the later text has no preamble
      And no citation and no recital differs

  Rule: what-a-word-is · A word has a letter or a digit
    Spaces and punctuation alone are not words. A difference counts only its words.

    Example: The words of an inserted paragraph
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      When the reader reads the difference at the inserted article 100 paragraph 3
      Then it inserts 84 words

  Rule: what-the-edits-show · What the edits show
    A changed occurrence shows the words of the later text, and the words of the earlier text
    deleted where they stood. An inserted occurrence shows all its words inserted. A deleted
    occurrence shows all its words deleted, as the earlier text has them.

    Example: Words replaced in a point
      Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 22 March 2026
      When the reader reads the difference at the changed point 12.6.2
      Then it deletes "1 October 2025", and inserts "31 December 2026" just after it

    Example: An inserted paragraph
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      When the reader reads the difference at the inserted article 100 paragraph 3
      Then it inserts all its words

    Example: A deleted point
      Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 22 March 2026
      When the reader reads the difference at the deleted point 12.9.3.14
      Then it deletes all its words

  Rule: replaced-phrase · A replaced phrase
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
      When the reader reads the difference at the changed appendix 3-B indent 9
      Then it inserts "Republic of Serbia, in regard to Belgrade Nikola Tesla Airport"

  Rule: formula-is-one-piece · A formula is one piece
    The comparison compares a formula whole, not symbol by symbol: its symbols alone do not read.
    An inserted formula shows inserted whole.

    Example: A formula inserted in an article
      Given the texts of Decision 2005/7 as adopted and of 15 October 2013
      When the reader reads the difference at the changed article 2
      Then it inserts a formula whole

  Rule: image-is-one-piece · An image is one piece
    The comparison reads an image as one piece. The piece is the file of the image. Thus a changed
    inline image shows whole as deleted, then whole as inserted.

    Example: An inline image in a paragraph has a different image file
      Given an article with inline image "old.tif"
      And a later article with inline image "new.tif"
      When the reader compares the two articles
      Then it deletes image "old.tif", and inserts image "new.tif" just after it

    Example: An inline image with the same image file stays, and a changed word beside it shows its edit
      Given an article with paragraph "See old" and inline image "same.tif"
      And a later article with paragraph "See new" and inline image "same.tif"
      When the reader compares the two articles
      Then it keeps image "same.tif"
      And it deletes "old"
      And it inserts "new"
      And it deletes and inserts nothing else

  Rule: image-shows-before-and-after · A changed image shows as deleted and inserted
    The comparison reads the file of a figure as one piece. When the image file changes, the old
    image shows as deleted and the new image shows as inserted, not edits in one image. When only
    the caption of a figure changes, the figure stays and its caption shows edits.

    Example: A figure in an article has a different image file
      Given an article with figure "old.tif" and caption "Diagram"
      And a later article with figure "new.tif" and caption "Diagram"
      When the reader compares the two articles
      Then it deletes image "old.tif"
      And it inserts image "new.tif"

    Example: A figure with the same file keeps the figure and edits its caption
      Given an article with figure "same.tif" and caption "Diagram 1"
      And a later article with figure "same.tif" and caption "Diagram 2"
      When the reader compares the two articles
      Then it keeps image "same.tif"
      And it deletes "1"
      And it inserts "2"

  Rule: block-with-a-figure-compares-by-its-pieces · A block that holds a figure compares by its pieces
    The comparison reads a block that holds a figure piece by piece. The figure stays in its
    place. The words around the figure show their edits word by word.

    Example: A figure in a quotation with only a changed caption edits its caption
      Given an article with a quotation of figure "same.tif" and caption "Diagram 1"
      And a later article with a quotation of figure "same.tif" and caption "Diagram 2"
      When the reader compares the two articles
      Then it keeps image "same.tif"
      And it deletes "1"
      And it inserts "2"

    Example: A list item holds a figure and words, and one word outside the figure changes
      Given an article with a list item holding figure "same.tif" and words "Keep old text."
      And a later article with a list item holding figure "same.tif" and words "Keep new text."
      When the reader compares the two articles
      Then it keeps image "same.tif"
      And it deletes "old"
      And it inserts "new"
      And it deletes and inserts nothing else

    Example: A table cell holds a figure, and only the caption changes
      Given an article with a table cell holding figure "same.tif" and caption "Diagram 1"
      And a later article with a table cell holding figure "same.tif" and caption "Diagram 2"
      When the reader compares the two articles
      Then it keeps image "same.tif"
      And it deletes "1"
      And it inserts "2"

    Example: A paragraph has no figure in the earlier text and has a figure in the later text
      Given an article with paragraph "First sentence."
      And a later article with paragraph "First sentence." and figure "added.tif"
      When the reader compares the two articles
      Then it inserts image "added.tif"
      And it does not edit "First sentence."

  Rule: deleted-text-stands-where-it-was · Deleted text stands where it was
    A deleted block or occurrence shows where it stood in the earlier text, before the insertion
    in its place. Each view of the comparison shows it there.

    Example: A deleted paragraph before the points inserted in its place
      Given the comparison of the texts of 2015/1998 of 29 February 2016 and of 16 February 2020
      When the reader reads the difference at the changed point 11.4.2
      Then it deletes
        """
        The results of testing shall be provided to the person and recorded, and may be taken into
        consideration as part of the recertification or reapproval process.
        """
      And it inserts "(a)" just after it

    Example: A deleted point before the occurrence inserted in its place
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      When the reader reads the difference at the deleted article 23 paragraph 1 point (e)
      Then the next difference is an inserted occurrence in article 23 paragraph 1

    Example: A deleted point that stood first in its paragraph
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      When the reader reads the difference at the deleted article 23 paragraph 1 point (a)
      Then it stands first in article 23 paragraph 1

    Example: A deleted point after the point before it
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      When the reader reads the difference at the deleted article 2 paragraph 5 point (24)
      Then it stands after article 2 paragraph 5 point (23)
