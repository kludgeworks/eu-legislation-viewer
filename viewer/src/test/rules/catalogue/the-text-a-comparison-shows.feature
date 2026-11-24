# SPDX-License-Identifier: MIT
Feature: The text a comparison shows
  Which text a comparison shows for a day, and which of its changes do not apply on the day yet.
  The comparison of two days compares the texts that it shows for them. The texts are in English,
  and those that the converter has a sample of: a version without a sample has no built text.

  Rule: the-text-for-a-day · The text that a comparison shows for a day
    The comparison shows the text of the version that applies on the day. There is one exception:
    an amending act applies on the day, and that version does not include it. Then the comparison
    shows the text of the first version that includes the act.

    An act that no version includes does not change which text the comparison shows. A day before
    the first version has no text.

    Example: An act that applies before the first version that includes it
      Given the act 2015/1998 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then on 3 June 2017, the reader gets the version of 29 February 2016
      And 2017/815 is a pending amendment from 1 June 2017, until the version of 7 June 2017 includes it
      And for 3 June 2017, a comparison shows the text of 7 June 2017
      And the amending acts between 29 February 2016 and 3 June 2017 are 2017/815

    Example: A day on which the version that applies includes each act that applies
      Given the act 2015/1998 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then for 8 June 2017, a comparison shows the text of 7 June 2017

    Example: An act that no version includes
      Given the act 2018/1862 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then 2021/1133 is an undated pending amendment from 2 August 2021
      And for 1 September 2021, a comparison shows the text of 3 August 2021

    Example: A day before the first version
      Given the act 2015/1998 as Cellar has it
      Then for 1 January 2015, a comparison shows no text

  Rule: changes-that-do-not-apply-yet · Changes in a text that do not apply on the day
    A comparison can show a text with a change that does not apply on the day yet. That change
    comes from a prospective amendment of the day. An occurrence has such a change if a
    prospective amendment of the day changes one of these occurrences:
    - the occurrence itself
    - an occurrence that contains it
    - an occurrence in it

    The eIds tell which occurrence is in which. If no condition is true, the occurrence has no such
    change. A change at another occurrence beside it does not count.

    Example: A text that includes the changes of an act years before they apply
      Given the act 2018/1862 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then for 1 August 2022, a comparison shows the text of 1 August 2022
      And on 1 August 2022, the text of 1 August 2022 has a change that does not apply yet at 18 occurrences
      And on 1 August 2022, in the text of 1 August 2022, article 3 point 8 has a change of 2022/1190 that does not apply yet

    Example: An occurrence in a changed occurrence, and an occurrence that contains one
      Given the act 2018/1862 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then on 1 August 2022, in the text of 1 August 2022, article 48 paragraph 8 point (a) has a change of 2022/1190 that does not apply yet
      And on 1 August 2022, in the text of 1 August 2022, article 3 has a change of 2022/1190 that does not apply yet

    Example: A change of the same act that applies already
      Given the act 2018/1862 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then on 1 August 2022, the change of 2022/1190 at article 79 paragraph 7 applies
      And on 1 August 2022, in the text of 1 August 2022, article 79 paragraph 7 has no change that does not apply yet

    Example: A point beside a point with such a change
      Given the act 2018/1862 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then on 1 August 2022, in the text of 1 August 2022, article 3 has a change of 2022/1190 that does not apply yet
      And on 1 August 2022, in the text of 1 August 2022, article 3 point 1 has no change that does not apply yet

    Example: The day from which the changes apply
      Given the act 767/2008 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then on 1 January 2018, the text of 29 December 2017 has a change that does not apply yet at 14 occurrences
      And on 1 January 2018, in the text of 29 December 2017, article 10 paragraph 1 point (da) has a change of 2017/2226 that does not apply yet
      And on 12 October 2025, the text of 29 December 2017 has no change that does not apply yet
