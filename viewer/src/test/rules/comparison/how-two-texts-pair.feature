# SPDX-License-Identifier: MIT
Feature: How the occurrences of two texts pair
  A comparison first finds which occurrence of the later text is which occurrence of the earlier
  text: the pairing. Then it compares each pair. It shows an occurrence of the later text that
  pairs with none as inserted. It shows an occurrence of the earlier text that pairs with none as
  deleted.

  @revision-2
  Rule: place-among-siblings · Occurrences pair by their place in the occurrence around them
    Two occurrences pair with their siblings: the occurrences in two occurrences that pair. Among
    siblings, an occurrence pairs by its place in the occurrence around it. This rule is not for an
    occurrence that a tag of a renumbering gave a new number.

    The place of the occurrence around it does not count. So the occurrences in a renumbered
    occurrence, or in an occurrence that moved, still pair.

    The words of the occurrence do not count. So an occurrence that an act replaced where it stands
    pairs with what it was. It shows as changed, under its own number, not as deleted and inserted.

    Example: The points of a point that moved
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 15 March 2021
      Then point 6.8.3.6 point (a) of the later text pairs with point 6.8.3.6 point (a) of the earlier text

    Example: A point that an act replaced
      Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 22 March 2026
      When the reader looks at the difference at the changed point 9.1.3.1
      Then the point has the same place in both texts
      And its tags name 2026/449, which replaced it

  Rule: renumbered-occurrence · A renumbered occurrence pairs by its words
    A tag of a renumbering can give an occurrence a new number. That occurrence pairs with the
    earlier occurrence of another number that says the same, without its number and its heading.
    The pairs keep their order. So a point inserted before renumbered points pairs with none, and the points after it
    pair with what they were.

    Example: A point inserted before renumbered points
      Given the texts of the Council Resolution on a Model Agreement for a Joint Investigation Team of 28 January 2022 and of 29 April 2026
      Then point 12 of the later text has no pair in the earlier text
      And point 13 of the later text pairs with point 12 of the earlier text
      And point 14 of the later text pairs with point 13 of the earlier text
      And point 15 of the later text pairs with point 14 of the earlier text

  Rule: moved-occurrence · An occurrence that moved
    An occurrence that moved into the occurrence before it, or out of it, pairs with what it was.
    The two need the same number. If neither has a number, they need words that are alike.

    An occurrence that moved further pairs with the one occurrence of the other text that meets
    these conditions:
    - It pairs with nothing else.
    - It has the same number.
    - It has the same or alike words.

    If more than one occurrence meets these conditions, the occurrence that moved pairs with none.
    An occurrence in an inserted or deleted occurrence shows whole with it, so it does not pair.

    Example: A point that moved into the point before it
      Given the comparison of the texts of 2015/1998 of 2 July 2020 and of 15 March 2021
      Then point 12.4.2.1 of the later text pairs with point 12.4.2.1 of the earlier text

    Example: An occurrence without a number that moved out of the occurrence before it
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 15 March 2021
      Then appendix 5-A indent 1 of the later text pairs with appendix 5-A indent 1 of the earlier text

    Example: A point that moved does not pair with a point of another number
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      Then article 66 paragraph 2 point (c) point (iii) of the later text has no pair in the earlier text

    Example: A point that moved further, into a list of the point before it
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 15 March 2021
      Then point 6.8.3.6 of the later text pairs with point 6.8.3.6 of the earlier text

    Example: A point in an inserted occurrence does not pair
      Given the comparison of the texts of 2015/1998 of 29 February 2016 and of 22 March 2026
      Then point 12.9.3.14 of the later text has no pair in the earlier text

    Example: Two points meet the conditions for a point that moved further
      Given the comparison of the texts of 2015/1998 of 29 February 2016 and of 16 February 2020
      Then point 6.8.5.1 point (a) of the later text has no pair in the earlier text

    Example: A point that moved further but says other things
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 15 March 2021
      Then point 6.8.3.7 of the later text has no pair in the earlier text

  Rule: blocks-pair-with-what-they-were · Blocks pair with what they were
    In a changed occurrence, the blocks that say the same pair first. Then the blocks that are
    alike pair. So a changed block pairs with the block that it was, not with a block inserted
    before it.

    Example: Two blocks inserted before a changed block
      Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 22 March 2026
      When the reader looks at the difference at the changed point 9.1.3.1
      Then its blocks show, in order: "the same, inserted, inserted, changed"

  Rule: table-row-by-row · A table compares row by row
    A table is not one run of words. It compares row by row, so a row inserted or deleted whole
    shows as a row.

    Example: Rows of a table replaced
      Given the comparison of the texts of 2015/1998 of 29 February 2016 and of 16 February 2020
      When the reader looks at the difference at the changed appendix 6-C4 part 3
      Then its rows show, in order: "inserted, inserted, inserted, deleted, deleted, deleted, deleted"
