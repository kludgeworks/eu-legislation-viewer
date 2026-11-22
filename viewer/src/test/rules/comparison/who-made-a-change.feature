# SPDX-License-Identifier: MIT
Feature: Who made a change, and from when
  What the sidenote of a difference says: which act made the change, in which order, and from
  when it applies.

  Rule: R-WHO-02 The tags of an occurrence
    An occurrence's tags are its own, each once and in order. An occurrence without tags takes
    those of the nearest occurrence around it that has some.

    Example: A point has a tag of its own
      Given the consolidated version of 2015/1998 of 1 October 2026
      When the reader asks for the tags of point 8.1.2.3(c)
      Then the tags are
        | act      | did      | taken from |
        | 2026/449 | replaced |            |

    Example: A point has several tags, each once and in the order in which they stand
      Given the consolidated version of 2015/1998 of 1 October 2026
      When the reader asks for the tags of point 6.8.3.1
      Then the tags are
        | act       | did      | taken from |
        | 2017/815  | replaced |            |
        | 2022/1174 | deleted  |            |

    Example: A point without tags takes the tag of the point that an act replaced whole
      Given the consolidated version of 2015/1998 of 22 March 2026
      When the reader asks for the tags of point 1.2.2.3(a)
      Then the tags are
        | act      | did      | taken from    |
        | 2026/449 | replaced | point 1.2.2.3 |
