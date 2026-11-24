# SPDX-License-Identifier: MIT
Feature: The text and the law on a day
  Where the text that a reader gets on a day is not the law on that day, and why. The text is
  always a version of the act, unchanged. Where the text and the law differ, the act has a
  divergence: a pending amendment (the act applies, but the text does not include it), a
  prospective amendment (the text includes it, but it does not apply yet), or an undated pending
  amendment (the act is in force, the text does not include it, and Cellar does not give the day
  from which it applies). The changes are those to the English text. The texts are those that the
  converter has a sample of: a version without a sample has no built text.

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
      And its texts are built where the converter has a sample
      Then Cellar gives 12 October 2025 for the change of 2017/2226 at article 10 paragraph 1 point (da)
      And 2017/2226 is a prospective amendment from the version of 29 December 2017, until it applies on 12 October 2025
      And its divergence is at article 10 paragraph 1 point (da)

    Example: A provision that an act deletes, which the text keeps until the deletion applies, and tags only around it
      Given the act 2018/1862 as Cellar has it
      And its texts are built where the converter has a sample
      Then Cellar gives 15 October 2026 for the change of 2022/1190 at article 48 paragraph 9
      And 2022/1190 is a prospective amendment from the version of 1 August 2022, until it applies on 15 October 2026
      And its divergence is at article 48 paragraph 9

  Rule: a-place-date-the-act-can-apply-from · A place date counts only if the act can apply from it
    A place date counts if the act can apply from it: it is one of the dates of the act. It does
    not count if it is only the entry into force of the act, and the whole act applies from
    another day, or if it is not a date of the act.

    Example: A place date that is only the entry into force
      Given the act 2015/1998 as Cellar has it
      And its texts are built where the converter has a sample
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
      And its texts are built where the converter has a sample
      Then Cellar gives 5 April 2010 for the change of 810/2009 at article 12.2
      And 810/2009 makes no divergence

    Example: Two places of an act that apply from the day of the version that tags them
      Given the act 767/2008 as Cellar has it
      And its texts are built where the converter has a sample
      Then Cellar gives 29 December 2017 for the change of 2017/2226 at chapter III article 17a
      And 2017/2226 is a prospective amendment from the version of 29 December 2017, until it applies on 12 October 2025
      But its divergence is not at chapter III article 17a
      And its divergence is not at article 26 paragraph 3a
      And its divergence is at 14 places

  Rule: pending-amendment · A pending amendment
    A place date before the version date makes a pending amendment. The text and the law differ
    from the place date to the day before the version date.

    Example: An act that applies before the first version that includes it
      Given the act 2015/1998 as Cellar has it
      And its texts are built where the converter has a sample
      Then Cellar gives 1 June 2017 for the change of 2017/815 at annex point 4.1.3.1
      And 2017/815 is a pending amendment from 1 June 2017, until the version of 7 June 2017 includes it
      And its divergence is at annex point 4.1.3.1
      And its divergence is at 57 places
      And on 31 May 2017, 2017/815 does not make the text and the law differ
      And on 1 June 2017, 2017/815 makes the text and the law differ
      And on 6 June 2017, 2017/815 makes the text and the law differ
      And on 7 June 2017, 2017/815 does not make the text and the law differ

  Rule: a-provision-not-in-the-text-yet · A provision that the text does not have yet
    A provision that the act adds makes the text pending, also if the version before the version
    date does not have it.

    Example: A point that an act inserts
      Given the act 2015/1998 as Cellar has it
      And its texts are built where the converter has a sample
      Then Cellar gives 1 June 2017 for the change of 2017/815 at annex point 4.1.3.4
      And the text of 29 February 2016 does not have the place of the change of 2017/815 at annex point 4.1.3.4
      And 2017/815 is a pending amendment from 1 June 2017, until the version of 7 June 2017 includes it
      And its divergence is at annex point 4.1.3.4

  Rule: prospective-amendment · A prospective amendment
    A place date after the version date makes a prospective amendment. The text and the law differ
    from the version date to the day before the place date.

    Example: A version that includes 14 places of an act years before they apply
      Given the act 767/2008 as Cellar has it
      And its texts are built where the converter has a sample
      Then 2017/2226 is a prospective amendment from the version of 29 December 2017, until it applies on 12 October 2025
      And its divergence is at 14 places
      And on 28 December 2017, 2017/2226 does not make the text and the law differ
      And on 29 December 2017, 2017/2226 makes the text and the law differ
      And on 11 October 2025, 2017/2226 makes the text and the law differ
      And on 12 October 2025, 2017/2226 does not make the text and the law differ

  Rule: deferred-application-of-the-act · The act itself does not apply yet
    The whole text of an act does not apply yet from its first version to the day before the whole
    act applies. This is a state of the act, not a divergence: its application is deferred.

    Example: An act in force two years before it applies
      Given the act 2016/679 as Cellar has it
      Then on 1 May 2016, its application is not deferred
      And on 4 May 2016, its application is deferred until 25 May 2018, in full
      And on 1 January 2017, its application is deferred until 25 May 2018, in full
      And on 25 May 2018, its application is not deferred

  Rule: an-act-compared-as-a-whole · An act compared as a whole
    If none of the provisions of an act gives an answer, the day from which the whole act applies
    is compared with the first version that includes the act. A provision gives no answer if a
    version before the answer has no built text. Without the texts, no provision gives an answer.

    Example: An act whose provisions give no answer, because a text is not built
      Given the act 2015/1998 as Cellar has it
      And its texts are built where the converter has a sample
      Then 2021/255 applies from 11 March 2021
      And 2021/255 is a pending amendment from 11 March 2021, until the version of 15 March 2021 includes it
      And its divergence is at no place

    Example: An act without the texts
      Given the act 2015/1998 as Cellar has it
      Then 2017/815 is a pending amendment from 1 June 2017, until the version of 7 June 2017 includes it
      And its divergence is at no place

  Rule: undated-pending-amendment · An undated pending amendment
    An act in force that no version includes, and whose day of application Cellar does not give,
    is an undated pending amendment from its entry into force: the text of each day from then can
    lack it. Only once the text of every version is built: before that, the viewer cannot tell that
    no version includes it. If a version includes the act, the version date stands instead.

    Example: An act whose day of application a clause sets
      Given the act 2018/1862 as Cellar has it
      And its texts are built where the converter has a sample
      Then the date of application of 2021/1133 is a placeholder
      And 2021/1133 is an undated pending amendment from 2 August 2021
      And its divergence is at article 50a
      And its divergence is at article 18a
      And its divergence is at article 44 paragraph 1 point (g)
      And its divergence is at 3 places

    Example: A text that is not built
      Given the act 2018/1862 as Cellar has it
      And its texts are built where the converter has a sample, except the text of 1 August 2022
      Then 2021/1133 makes no divergence

    Example: An act that a version includes
      Given the act 2015/1998 as Cellar has it
      And its texts are built where the converter has a sample
      Then the date of application of 2019/413 is a placeholder
      And 2019/413 makes no divergence

  Rule: the-acts-that-made-a-text-in-the-language · The acts that made a text, in the language
    The acts that made the text of a version in a language are the acts and corrigenda that the
    Publications Office lists for the version and that change its text in that language. An act
    changes the text in a language unless each of its amendments is for other languages only. A
    corrigendum counts only if it is published in that language.

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
    They are the act itself, while it is in force and does not apply in full (its deferred
    application), and the amending acts that make the text and the law differ on the day: a
    prospective amendment, whose changes the text has before they apply, and a pending amendment,
    dated or undated, whose changes the text does not show. No other rule decides this.

    Example: The act itself, an undated pending amendment and a prospective amendment
      Given the act 2018/1862 as Cellar has it
      And its texts are built where the converter has a sample
      Then on 1 August 2022, the text is not the law for 2018/1862, 2021/1133, 2022/1190

    Example: A pending amendment
      Given the act 2015/1998 as Cellar has it
      And its texts are built where the converter has a sample
      Then on 3 June 2017, the text is not the law for 2017/815
      And on 7 June 2017, the text is the law

    Example: The act itself only
      Given the act 2016/679 as Cellar has it
      Then on 1 January 2017, the text is not the law for 2016/679
