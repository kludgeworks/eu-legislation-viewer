# SPDX-License-Identifier: MIT
Feature: When an act applies
  When an act, or a provision of it, applies. These rules read the dates that Cellar types for
  each act: the dates of the act itself, of the acts that its versions include, and of the acts
  that amend it. A date has its kind (the entry into force, the application, the day from which
  the Member States apply a directive) and the clause that sets it. A date can be for a part of the
  act only.

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
    A day is one that the act can apply from if it is one of the act's dates. It is not, if it is
    only the entry into force and the whole act applies from another day, or from a day that Cellar
    does not give.

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

  Rule: a-later-act-moved-the-date · Dates that a later act moved are current
    Cellar updates an act's dates when a later act moves them. The date names the act that moved
    it.

    Example: A date for a part that a later act moved
      Given the act 2015/1998 as Cellar has it
      Then 2019/103 has a date for a part on 31 December 2021
      And the date of 2019/103 of 31 December 2021 names 2020/910 as the act that moved it
