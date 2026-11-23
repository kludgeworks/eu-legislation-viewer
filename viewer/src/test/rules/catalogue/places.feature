# SPDX-License-Identifier: MIT
Feature: Places
  Which occurrences of a text the place of an amendment is. Cellar gives the place where an
  amendment acts, in its own codes: for example, the code for point 6.8.1.8 of the annex. The place
  is found among the occurrences of a text, by their eIds. The changes are those to the English
  text. The texts are those that the converter has a sample of.

  Rule: a-place-is-the-occurrences-it-names · A place is the occurrences of the text that it names
    A place is the occurrence of the text that it names. A part of an occurrence that the text does
    not number, such as a subparagraph or a sentence, is in the occurrence that holds it. A range of
    points is each occurrence from the first point to the last.

    Background:
      Given the act 2015/1998 as Cellar has it
      And its texts are built where the converter has a sample

    Example: A point that the text has
      Then in the text of 2 July 2020, the change of 2026/449 at point 6.8.1.8 is point 6.8.1.8

    Example: A subparagraph and a sentence that the text does not number
      Then in the text of 29 February 2016, the change of 2026/449 at point 12.6.2 is in point 12.6.2

    Example: A range of points
      Then in the text of 29 February 2016, the change of 2025/920 at point 12.4.2.3-12.4.2.5 is each occurrence from point 12.4.2.3 to point 12.4.2.5

  Rule: no-guess · No guess
    A place that finds no occurrence, or that finds several occurrences where it names one, finds
    none.

    Background:
      Given the act 2015/1998 as Cellar has it
      And its texts are built where the converter has a sample

    Example: A point that the text does not have yet
      Then in the text of 29 February 2016, the change of 2026/449 at point 6.8.1.8 is no occurrence

    Example: An indent that the text has twice
      Appendix 8-B has an indent 1 in two lists, so the place names neither.

      Then in the text of 31 December 2021, the change of 2022/1174 at Appendix 8-B, indent 1, point (b) is no occurrence

  Rule: a-place-the-text-does-not-have · A place that the text does not have
    A place that the text does not have is in the occurrence that holds it: a point 4.1.3.4 is in
    point 4.1.3. An occurrence whose number has a letter after it, which an act inserts after the
    occurrence of the number alone, is at that occurrence: an Article 18a is at Article 18. A place
    for which the text has no such occurrence is in no occurrence.

    Example: A point that an act inserts, in the text from before the act
      Given the act 2015/1998 as Cellar has it
      And its texts are built where the converter has a sample
      Then on 3 June 2017, the reader gets the version of 29 February 2016
      And in the text of 29 February 2016, the change of 2017/815 at point 4.1.3.4 is held by point 4.1.3

    Example: An article that an act inserts after the article of the number alone
      Given the act 2018/1862 as Cellar has it
      And its texts are built where the converter has a sample
      Then in the text of 3 August 2021, the change of 2021/1133 at Article 18a is held by Article 18

    Example: A chapter that an act inserts, which no occurrence of the text holds
      Given the act 2018/1862 as Cellar has it
      And its texts are built where the converter has a sample
      Then in the text of 3 August 2021, the change of 2022/1190 at Chapter IXa is held by no occurrence
