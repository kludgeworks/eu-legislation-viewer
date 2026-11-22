# SPDX-License-Identifier: MIT
Feature: The neighbours of a difference
  The Synopsis shows the occurrences near a difference that are the same in both texts: the
  neighbours. Each neighbour says which act last made it as it is.

  Rule: R-SHOW-10 A neighbour has its tags as an occurrence does
    A neighbour's tags are found as for any occurrence (R-WHO-02): its own tags, or else those of
    the nearest occurrence around it that has some.

    Example: A neighbour without tags takes the tag of the point that an act replaced whole
      Given the consolidated version of 2015/1998 of 22 March 2026
      When the reader asks for the tags of the neighbour point 1.2.2.3(a)
      Then the tags are
        | act      | modification | taken from    |
        | 2026/449 | replaced     | point 1.2.2.3 |
