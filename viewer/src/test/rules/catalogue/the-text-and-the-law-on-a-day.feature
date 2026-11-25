# SPDX-License-Identifier: MIT
Feature: The text and the law on a day
  Where the text that a reader gets on a day is not the law on that day, and why. The text is
  always a version of the act, unchanged. Where the text and the law differ, the act has a
  divergence. A divergence is one of these:
  - a pending amendment: the act applies, but the text does not include it
  - a prospective amendment: the text includes it, but it does not apply yet
  - an undated pending amendment: the act is in force, the text does not include it, and Cellar
    does not give the day from which it applies

  The changes are those to the English text. The texts are those that the converter has a sample
  of: a version without a sample has no built text.

  Rule: the-text-of-a-day-is-a-version · The text of a day is a version, unchanged
    For a day, the reader gets the newest version that starts on or before it. Before the first
    version of the act, the reader gets no version.

    Example: A day between two versions, and a day before the act
      Given the act 2015/1998 as Cellar has it
      Then on 3 June 2017, the reader gets the version of 29 February 2016
      And on 7 June 2017, the reader gets the version of 7 June 2017
      And on 1 January 2015, the reader gets no version

  Rule: the-version-date-of-a-change · The place date and the version date of a change
    The place date is the day that Cellar gives for the changed place. The version date is the
    first day of the first version whose text newly tags the place with the amending act. An
    occurrence tags the place if it is the occurrence of the place, or an occurrence in it. If no
    version tags the place so, the first version that tags an occurrence around it gives the
    version date.

    Example: A place that the text tags before Cellar's day
      Given the act 767/2008 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then Cellar gives 12 October 2025 for the change of 2017/2226 at article 10 paragraph 1 point (da)
      And 2017/2226 is a prospective amendment from the version of 29 December 2017, until it applies on 12 October 2025
      And its divergence is at article 10 paragraph 1 point (da)

    Example: A provision that an act deletes, which the text keeps until the deletion applies, and tags only around it
      Given the act 2018/1862 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then Cellar gives 15 October 2026 for the change of 2022/1190 at article 48 paragraph 9
      And 2022/1190 is a prospective amendment from the version of 1 August 2022, until it applies on 15 October 2026
      And its divergence is at article 48 paragraph 9

  Rule: a-place-date-the-act-can-apply-from · A place date counts only if the act can apply from it
    A place date counts if the act can apply from it: it is one of the dates of the act. A place
    date does not count in these conditions:
    - It is only the entry into force of the act, and the whole act applies from another day.
    - It is not a date of the act.

    Example: A place date that is only the entry into force
      Given the act 2015/1998 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then Cellar gives 11 January 2016 for the change of 2015/2426 at annex chapter 4 APP 4-B
      And a provision of 2015/2426 cannot apply from 11 January 2016
      And 2015/2426 applies from 29 February 2016
      And 2015/2426 makes no divergence

    Example: A place date that is not a date of the act
      Given the act 2015/1998 as Cellar has it
      Then Cellar gives 31 December 2020 for the change of 2019/1583 at annex point 11.1.2
      And a provision of 2019/1583 cannot apply from 31 December 2020

  Rule: the-text-agrees-with-the-law · When the text agrees with the law
    The text agrees with the law at a place if the place date counts and is the version date. The
    version date stands if the place date does not count.

    Example: A place date that is the version date
      Given the act 767/2008 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then Cellar gives 5 April 2010 for the change of 810/2009 at article 12.2
      And 810/2009 makes no divergence

    Example: Two places of an act that apply from the day of the version that tags them
      Given the act 767/2008 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then Cellar gives 29 December 2017 for the change of 2017/2226 at chapter III article 17a
      And 2017/2226 is a prospective amendment from the version of 29 December 2017, until it applies on 12 October 2025
      But its divergence is not at chapter III article 17a
      And its divergence is not at article 26 paragraph 3a
      And its divergence is at 14 places

  @revision-2
  Rule: pending-amendment · A pending amendment
    A place date before the version date makes a pending amendment. The text and the law differ
    from the place date to the day before the version date. A provision that the act adds is
    pending in the same way, also if the version before the version date does not have it.

    Example: An act that applies before the first version that includes it
      Given the act 2015/1998 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then Cellar gives 1 June 2017 for the change of 2017/815 at annex point 4.1.3.1
      And 2017/815 is a pending amendment from 1 June 2017, until the version of 7 June 2017 includes it
      And its divergence is at annex point 4.1.3.1
      And its divergence is at 57 places
      And on 31 May 2017, 2017/815 does not make the text and the law differ
      And on 1 June 2017, 2017/815 makes the text and the law differ
      And on 6 June 2017, 2017/815 makes the text and the law differ
      And on 7 June 2017, 2017/815 does not make the text and the law differ

    Example: A point that an act inserts
      Given the act 2015/1998 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then Cellar gives 1 June 2017 for the change of 2017/815 at annex point 4.1.3.4
      And the text of 29 February 2016 does not have the place of the change of 2017/815 at annex point 4.1.3.4
      And 2017/815 is a pending amendment from 1 June 2017, until the version of 7 June 2017 includes it
      And its divergence is at annex point 4.1.3.4

  Rule: prospective-amendment · A prospective amendment
    A place date after the version date makes a prospective amendment. The text and the law differ
    from the version date to the day before the place date.

    Example: A version that includes 14 places of an act years before they apply
      Given the act 767/2008 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then 2017/2226 is a prospective amendment from the version of 29 December 2017, until it applies on 12 October 2025
      And its divergence is at 14 places
      And on 28 December 2017, 2017/2226 does not make the text and the law differ
      And on 29 December 2017, 2017/2226 makes the text and the law differ
      And on 11 October 2025, 2017/2226 makes the text and the law differ
      And on 12 October 2025, 2017/2226 does not make the text and the law differ

  Rule: deferred-application-of-the-act · The act itself does not apply yet
    The deferred application of an act is a state of the act, not a divergence. In this period,
    the whole text of the act does not apply yet:
    - It starts on the entry into force of the act, or on its first version if that day is later.
    - It ends on the day before the whole act applies.

    Before its entry into force, the act is not in force (state-on-a-day), so it has no deferred
    application.

    Example: An act in force two years before it applies
      Given the act 2016/679 as Cellar has it
      Then on 1 May 2016, the act has no deferred application
      And on 4 May 2016, the act has no deferred application
      And on 24 May 2016, the act has a deferred application: it applies in full from 25 May 2018
      And on 1 January 2017, the act has a deferred application: it applies in full from 25 May 2018
      And on 25 May 2018, the act has no deferred application

  Rule: an-act-compared-as-a-whole · An act compared as a whole
    The viewer can compare an act as a whole. It does this if no provision of the act gives an
    answer. It compares the day from which the whole act applies with the first version that
    includes the act. A provision gives no answer if a version before the answer has no built
    text. Without the texts, no provision gives an answer.

    Example: An act whose provisions give no answer, because the viewer did not build a text
      Given the act 2015/1998 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then 2021/255 applies from 11 March 2021
      And 2021/255 is a pending amendment from 11 March 2021, until the version of 15 March 2021 includes it
      And its divergence is at no place

    Example: An act without the texts
      Given the act 2015/1998 as Cellar has it
      Then 2017/815 is a pending amendment from 1 June 2017, until the version of 7 June 2017 includes it
      And its divergence is at no place

  Rule: undated-pending-amendment · An undated pending amendment
    An act is an undated pending amendment from its entry into force if these conditions are true:
    - The act is in force.
    - No version includes the act.
    - Cellar does not give the day from which the act applies.

    The text of each day from the entry into force can lack the act. The viewer can tell this only
    after it builds the text of every version. Before that, it cannot tell that no version
    includes the act. If a version includes the act, the version date stands instead.

    Example: An act whose day of application a clause sets
      Given the act 2018/1862 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then the date of application of 2021/1133 is a placeholder
      And 2021/1133 is an undated pending amendment from 2 August 2021
      And its divergence is at article 50a
      And its divergence is at article 18a
      And its divergence is at article 44 paragraph 1 point (g)
      And its divergence is at 3 places

    Example: A text that the viewer does not build
      Given the act 2018/1862 as Cellar has it
      And the viewer builds its texts where the converter has a sample, except the text of 1 August 2022
      Then 2021/1133 makes no divergence

    Example: An act that a version includes
      Given the act 2015/1998 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then the date of application of 2019/413 is a placeholder
      And 2019/413 makes no divergence

  Rule: the-acts-that-made-a-text-in-the-language · The acts that made a text, in the language
    The acts that made the text of a version in a language are the acts and corrigenda with both
    properties:
    - The Publications Office lists them for the version.
    - They change the text of the version in that language.

    An act changes the text in a language unless each of its amendments is for other languages
    only. A corrigendum counts only if the Publications Office publishes it in that language.

    Example: An act that changes only the German text
      Given the act 2015/1998 as Cellar has it
      Then in English, no act made the text of 12 April 2022
      And in German, these acts made the text of 12 April 2022: 2022/463

    Example: Corrigenda published in some languages only
      Given the act 2015/1998 as Cellar has it
      Then in English, these acts made the text of 29 February 2016: 2015/2426
      And in Swedish, these acts made the text of 29 February 2016: Corrigendum 2 to 2015/1998, 2015/2426
      And in German, these acts made the text of 29 February 2016: Corrigendum 2 to 2015/1998, Corrigendum 4 to 2015/1998, 2015/2426

  Rule: where-the-text-of-a-day-is-not-the-law · The acts for which the text of a day is not the law
    The text of a day is not the law for these acts:
    - the act itself, while it is in force and does not apply in full (its deferred application)
    - a prospective amendment of the day: the text has its changes before they apply
    - a pending amendment of the day, dated or undated: the text does not show its changes

    No other rule decides this.

    Example: The act itself, an undated pending amendment and a prospective amendment
      Given the act 2018/1862 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then on 1 August 2022, the text is not the law for 2018/1862, 2021/1133, 2022/1190

    Example: A pending amendment
      Given the act 2015/1998 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then on 3 June 2017, the text is not the law for 2017/815
      And on 7 June 2017, the text is the law

    Example: The act itself only
      Given the act 2016/679 as Cellar has it
      Then on 1 January 2017, the text is not the law for 2016/679
