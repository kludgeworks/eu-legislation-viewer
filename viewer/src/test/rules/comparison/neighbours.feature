# SPDX-License-Identifier: MIT
Feature: The neighbours of a difference
  The Synopsis shows the occurrences near a difference that are the same in both texts: the
  neighbours. Each neighbour says which act last made it as it is.

  Rule: neighbour-tags · A neighbour has its tags as an occurrence does
    A neighbour's tags are found as for any occurrence (tags-of-an-occurrence): its own tags, or
    else those of the nearest occurrence around it that has some.

    Example: A neighbour without tags takes the tag of the point that an act replaced whole
      Given the consolidated version of 2015/1998 of 22 March 2026
      When the reader asks for the tags of the neighbour point 1.2.2.3 point (a)
      Then the tags are
        | act      | modification | taken from    |
        | 2026/449 | replaced     | point 1.2.2.3 |

  Rule: last-change-of-a-neighbour · What last made a neighbour as it is
    Beside a neighbour, the Synopsis says what last made it as it is. That is the act that its tags
    name and that was last (tagged-act-was-last), up to the later text. A corrigendum corrects and
    does not change the act (corrigendum-corrects), so it does not count. If no text up to the
    later text includes such an act, the neighbour is as in the original act. Its date comes from
    the dates of that act, or of the original act.

    Example: The act that the texts include last, among the acts of the tags
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 31 December 2021
      When the reader asks what last changed the neighbour point 11.1.12
      Then 2020/910 last changed it: replaced, dated from 2 July 2020

    Example: A provision with no tag of an amending act is as in the original act
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 31 December 2021
      When the reader asks what last changed the neighbour article 1
      Then it is as in the original act, dated from 1 February 2016

    Example: A corrigendum is left out
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      When the reader asks what last changed the neighbour article 162 paragraph 3
      Then it is as in the original act, dated from 17 July 2013
