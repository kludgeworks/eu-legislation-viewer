# SPDX-License-Identifier: MIT
Feature: When an act applies
  When an act, or a provision of it, applies. These rules read the dates that Cellar types for
  each act: the dates of the act itself, of the acts that its versions include, and of the acts
  that amend it. Each date has these properties:
  - its kind: the entry into force, the application, or the day from which the Member States apply
    a directive
  - the clause that sets it

  A date can be for a part of the act only.

  Rule: whole-act-applies-from · The day from which a whole act applies
    An act applies from its date of application, or of taking effect, that is not for a part. If it
    has none, a directive applies from the day from which the Member States apply it, if Cellar
    gives one such day. If it has none, an act that is not a directive applies from its entry into
    force. A directive with only its entry into force has no such day: the Member States apply it
    through their own measures. If Cellar has a placeholder for the date, the act has no such day.

    Example: An act with a date of application for the whole act
      Given the act 2016/679 as Cellar has it
      Then 2016/679 enters into force on 24 May 2016
      And 2016/679 applies from 25 May 2018

    Example: A directive that the Member States apply from one day
      Given the act Directive 2013/36 as Cellar has it
      Then the Member States apply Directive 2022/2556 from 17 January 2025
      And Directive 2022/2556 applies from 17 January 2025

    Example: A directive that the Member States apply from more than one day
      Given the act Directive 2013/36 as Cellar has it
      Then the Member States apply Directive 2019/878 from 29 December 2020, 28 June 2021 and 1 January 2022
      And Directive 2019/878 has no day from which the whole act applies

    Example: An act that is not a directive, with only its entry into force
      Given the act 2015/1998 as Cellar has it
      Then the only date of 2017/837 is its entry into force, on 7 June 2017
      And 2017/837 applies from 7 June 2017

    Example: A directive with only its entry into force
      Given the act Directive 2013/36 as Cellar has it
      Then the only date of Directive 2015/2366 is its entry into force, on 12 January 2016
      And Directive 2015/2366 has no day from which the whole act applies

    Example: A placeholder for the date of application
      Given the act 2015/1998 as Cellar has it
      Then the date of application of 2019/413 is a placeholder
      And 2019/413 has no day from which the whole act applies

  Rule: date-for-a-part · A date for a part does not move the whole act
    A date for a part is an exception for that part only. The whole act applies from its own date.

    Example: An act whose points apply later than its entry into force
      Given the act 2015/1998 as Cellar has it
      Then 2021/255 has a date for a part on 1 July 2021
      And 2021/255 applies from 11 March 2021

    Example: An act with dates for parts before its date of application
      Given the act 2018/1862 as Cellar has it
      Then 2018/1862 has a date for a part on 28 December 2019
      And 2018/1862 applies from 7 March 2023

  Rule: a-day-the-act-can-apply-from · A provision applies only from a day that the act can apply from
    The act can apply from a day if the day is one of the dates of the act. The entry into force
    alone does not count if the whole act applies from another day, or from a day that Cellar does
    not give.

    Example: One of the act's dates
      Given the act 2015/1998 as Cellar has it
      Then a provision of 2021/255 can apply from 1 July 2021
      And a provision of 2021/255 can apply from 11 March 2021
      But a provision of 2021/255 cannot apply from 1 January 2022

    Example: Only the entry into force, and the whole act applies from another day
      Given the act 2015/1998 as Cellar has it
      Then 2015/1998 enters into force on 15 November 2015
      And 2015/1998 applies from 1 February 2016
      But a provision of 2015/1998 cannot apply from 15 November 2015

    Example: Only the entry into force, and the whole act applies from a day that Cellar does not give
      Given the act 2015/1998 as Cellar has it
      Then 2019/413 enters into force on 16 March 2019
      But a provision of 2019/413 cannot apply from 16 March 2019

  @revision-2
  Rule: a-later-act-amended-or-set-the-date · Dates that a later act amended or set are current
    Cellar updates the dates of an act in two cases:
    - A later act amends them.
    - A later act sets a day that a clause of the act leaves to it.

    The date names that later act. Cellar names the two in
    the same way. A later act that amends the act of the date amended the date. A later act that
    does not amend it set the day.

    Example: A date for a part that a later act amended
      Given the act 2015/1998 as Cellar has it
      Then 2019/103 has a date for a part on 31 December 2021
      And the date of 2019/103 of 31 December 2021 names 2020/910 as the act that amended it

    Example: The day from which the whole act applies, which a decision set
      Given the act 2018/1862 as Cellar has it
      Then 2018/1862 applies from 7 March 2023
      And the date of 2018/1862 of 7 March 2023 names Decision 2023/201 as the act that set its day

    Example: The day from which the changes of an amending act apply, which a decision set
      Given the act 2018/1862 as Cellar has it
      Then the date of 2022/1190 of 15 October 2026 names Decision 2026/2284 as the act that set its day

  @revision-2
  Rule: date-from-the-act · The date is the act's
    The date of a change comes from the dates of the act that made it. It never comes from the
    date of the text that first has the change. The first of these cases that is true for the act
    gives the date:
    1. Cellar has no day for the act's application. A clause of the act sets the day.
    2. The only date of the act is its entry into force. The act applies from that day. A
       directive is only in force from that day. The data does not say from when it applies: the
       Member States apply it through their own measures.
    3. The place of the change has one day for the change, and the act can apply from that day
       (a-day-the-act-can-apply-from). The change applies from that day.
    4. The act has one day of application, and that day is for the whole act. The change applies
       from that day.
    5. No other case is true. The act applies from more than one day: each day from which a part
       of it applies.

    If Cellar has no dates of the act, nothing tells from when the change applies.

    Example: Case 1: a clause sets the day
      Given the act 2015/1998 as Cellar has it
      Then a change of 2019/413 applies from a day that Article 3 of 2019/413 sets

    Example: Case 2: an act that has only its entry into force
      Given the act 2015/1998 as Cellar has it
      Then a change of 2019/1583 applies from 31 December 2021, its entry into force

    Example: Case 2: a directive that has only its entry into force
      Given the act Directive 1976/207 as Cellar has it
      Then a change of Directive 2002/73 is in force from 5 October 2002, and the data does not say from when it applies

    Example: Case 3: Cellar's day at the place is a day that the act can apply from
      Given the act 2015/1998 as Cellar has it
      And Cellar dates a change of 2026/449 at its place from 1 October 2026
      Then that change applies from 1 October 2026

    Example: Case 4: one day of application, before the text that has the change
      Given the act 2015/1998 as Cellar has it
      Then a change of 2017/815 applies from 1 June 2017

    Example: Case 5: Cellar's day at the place is not a day that the act can apply from
      Given the act 2015/1998 as Cellar has it
      And Cellar dates a change of 2026/449 at its place from 15 May 2026
      Then that change applies from more than one day
        | from            | what                 |
        | 22 March 2026   | its entry into force |
        | 1 October 2026  | a part               |

  Rule: a-date-checked-against-the-text · A date whose publication anchor contradicts Cellar
    When Cellar's stored date disagrees with the Official Journal publication date plus the days
    in Cellar's comment, only the enacted text settles it:
    - If the text agrees with the clause, the date comes from the clause and Journal.
    - If the text agrees with Cellar's stored date, Cellar's date stands.
    - If the text names another date or offset, the date comes from the text.
    - If no rule matches or no text is available, keep Cellar's date and mark it as "not confirmed".

    Example: Text agrees with the publication clause
      Given Cellar gives 6 July 2020 for 1 day after publication on 1 July 2020
      And the enacted text states 1 day after publication
      Then the resolved date is 2 July 2020 from clause_and_journal

    Example: Text agrees with Cellar's stored date over a wrong offset in Cellar's comment
      Given Cellar gives 14 June 2018 for 20 days after publication on 13 June 2018
      And the enacted text states 1 day after publication
      Then the resolved date is 14 June 2018 from cellar

    Example: Text states a fixed retroactive date instead of publication
      Given Cellar gives 18 February 2017 for 0 days after publication on 1 December 2017
      And the enacted text states the fixed date 18 February 2017
      Then the resolved date is 18 February 2017 from cellar

    Example: Text names a different fixed date than both Cellar and the clause
      Given Cellar gives 6 July 2020 for 1 day after publication on 1 July 2020
      And the enacted text states the fixed date 3 July 2020
      Then the resolved date is 3 July 2020 from text

    Example: Text gives no known rule or is unavailable
      Given Cellar gives 28 July 2017 for 20 days after publication on 13 July 2017
      And the enacted text gives no known rule
      Then the resolved date is 28 July 2017 from not_confirmed
