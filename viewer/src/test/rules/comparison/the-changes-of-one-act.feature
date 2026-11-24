# SPDX-License-Identifier: MIT
Feature: The changes of one act
  The reader can limit a comparison to the changes of one act. The page of an amending act shows
  them.

  Rule: one-act-is-one-step · The changes of one act are one step
    Limited to one act, the changes are those between the first text that includes the act and
    the text just before it. So the changes of two acts that one text first includes are the same
    changes. The sidenotes tell them apart where they can (tags-else-cellar, acts-changed-together).

    Example: The changes of an act are those of the text that first includes it
      Given the comparison of the texts of 2015/1998 of 29 February 2016 and of 1 October 2026
      When the reader limits the comparison to 2026/449
      Then its changes are those from the text of 1 January 2026 to the text of 22 March 2026

    Example: Two acts that one text first includes have the same changes
      Given the comparison of the texts of 2015/1998 of 2 July 2020 and of 1 October 2026
      When the reader limits the comparison to 2019/413
      Then its changes are those from the text of 2 July 2020 to the text of 15 March 2021
      When the reader limits the comparison to 2021/255
      Then its changes are those from the text of 2 July 2020 to the text of 15 March 2021

  Rule: this-act-or-another · The changes that this act or another act made
    One of two or more acts can make a difference while the data does not say which act
    (no-act-named). Then the page of each of these acts lists the difference apart from its own
    changes. It also names the other acts.

    Background:
      Given the comparison of the texts of 2015/1998 of 2 July 2020 and of 15 March 2021

    Example: The page of one of two acts
      When the reader opens the page of 2021/255
      Then the page lists 3 differences apart, by this act or by 2019/413

    Example: The page of the other act
      When the reader opens the page of 2019/413
      Then the page lists 3 differences apart, by this act or by 2021/255

  Rule: chapter-the-annex-keeps-flat · The changes of an act in a chapter that the annex keeps flat
    Cellar can give a chapter of an annex as the place of a change, while the text keeps the
    chapter flat. Its points and its attachments then stand beside it, not in it, and the chapter
    has no difference of its own. The changes of the act there are the differences that the act
    made in the points and the attachments that the chapter holds.

    Example: An act changed an attachment of a chapter
      Given the comparison of the texts of 2015/1998 of 2 July 2020 and of 15 March 2021
      When the reader asks for the changes of 2019/413 at chapter 5
      Then its changes are 2 differences in Attachment 5-A

    @known-bug @ISS-272
    Example: An act changed an attachment of a chapter that Cellar names with its text
      Given the comparison of the texts of 2015/1998 of 2 July 2020 and of 15 March 2021
      When the reader asks for the changes of 2019/413 at chapter 3
      Then its changes are 1 difference in Attachment 3-B

  Rule: differences-at-a-place · The differences at the place of a change
    The differences at a place are the differences of its occurrences and of the occurrences in
    them. If there are none, it is the one difference of the nearest occurrence that holds one of
    the occurrences.

    Example: A chapter that an act inserted whole
      Given the comparison of the texts of 2018/1862 of 3 August 2021 and of 1 August 2022
      When the reader asks for the differences at chapter IXa
      Then they are the difference at chapter IXa

    Example: The differences in a provision
      Given the comparison of the texts of 767/2008 of 29 December 2017 and of 12 October 2025
      When the reader asks for the differences at article 6
      Then they are 2 differences in article 6

    Example: A provision with no difference of its own, in a provision that an act inserted whole
      Given the comparison of the texts of 767/2008 of 29 December 2017 and of 12 October 2025
      When the reader asks for the differences at article 45c paragraph 3
      Then they are the difference at article 45c

    Example: A provision in a chapter that an act inserted whole
      Given the comparison of the texts of 767/2008 of 29 December 2017 and of 12 October 2025
      When the reader asks for the differences at article 22b paragraph 18
      Then they are the difference at chapter IIIa
