# SPDX-License-Identifier: MIT
Feature: The state on a day
  The legal state of an act, or of one change that an amending act makes, on a day. It is one of
  these states:
  - not in force
  - in force and does not apply yet
  - applies
  - applies in part
  - no longer in force
  - not known

  Each state has one rule. On a day, one rule is true and the others are not.

  The rules compare the day with these days:
  - the entry into force of the act, and its end of validity
  - the days from which it can apply. For an act on its own, this is the day for the whole act
    (whole-act-applies-from). For a change, date-from-the-act gives one day, more than one day, or
    no day.
  - for an act on its own, its dates for a part (date-for-a-part)

  The act is in force on a day that is not before its entry into force and not after its end of
  validity.

  A change is an amendment that changes the words of the English text. These rules use only the
  catalogue: the dates of the acts and their amendments. They do not use the texts of the
  versions.

  Rule: not-in-force · Before the entry into force
    Before the entry into force of the act, the act is not in force. A change of the act is not in
    force also.

    Example: An act before its entry into force
      Given the act 2016/679 as Cellar has it
      Then on 1 May 2016, 2016/679 is not in force

    Example: An act with parts, before its entry into force
      Given the act 2018/1862 as Cellar has it
      Then on 1 December 2018, 2018/1862 is not in force

    Example: A change of an amending act before its entry into force
      Given the act 2018/1862 as Cellar has it
      Then on 1 July 2022, the change of 2022/1190 at article 79 paragraph 7 is not in force

  Rule: no-longer-in-force · After the end of validity
    Cellar gives an end of validity for an act that a later act repealed, or that expired. After
    that day, the act is no longer in force. Only an act on its own has this state. The page names
    the act that repealed it, if Cellar names one.

    Example: An act that a later act repealed
      Given the act 185/2010 as Cellar has it
      Then on 14 November 2015, 185/2010 applies
      And on 15 November 2015, 185/2010 is no longer in force
      And 2015/1998 repealed 185/2010

  Rule: applies · On or after the last day from which it can apply
    The act is in force, and the day is on or after the last day from which it can apply. If it can
    apply from one day, that day is the last day. If a clause sets one more day that Cellar does
    not give, there is no last day.

    Example: An act from its date of application
      Given the act 2016/679 as Cellar has it
      Then on 25 May 2018, 2016/679 applies

    Example: An act with parts, from the day for the whole act
      Given the act 2018/1862 as Cellar has it
      Then 2018/1862 applies from 7 March 2023
      And on 7 March 2023, 2018/1862 applies

    Example: A change, from the day that Cellar gives for its place
      Given the act 2018/1862 as Cellar has it
      Then Cellar gives 1 August 2022 for the change of 2022/1190 at article 79 paragraph 7
      And on 1 August 2022, the change of 2022/1190 at article 79 paragraph 7 applies
      And Cellar gives 15 October 2026 for the change of 2022/1190 at article 24 paragraph 1
      And on 15 October 2026, the change of 2022/1190 at article 24 paragraph 1 applies

    Example: A change of an act that applies from more than one day, from the last of them
      Given the act Directive 2013/36 as Cellar has it
      Then on 1 January 2022, the change of Directive 2019/878 at article 10 applies

    Example: A change with a day for the place that the act cannot apply from
      Given the act Directive 2013/36 as Cellar has it
      Then on 17 January 2025, the change of Directive 2022/2556 at article 85 paragraph 2 applies

  Rule: does-not-apply-yet · In force, before the first day from which it can apply
    The act is in force, and the day is before the first day from which it can apply. For an act on
    its own, no date for a part is on or before the day.

    Example: An act before its date of application
      Given the act 2016/679 as Cellar has it
      Then on 1 January 2017, 2016/679 is in force and does not apply yet

    Example: A change before the day that Cellar gives for its place
      Given the act 2018/1862 as Cellar has it
      Then Cellar gives 15 October 2026 for the change of 2022/1190 at article 24 paragraph 1
      And on 1 August 2022, the change of 2022/1190 at article 24 paragraph 1 is in force and does not apply yet

    Example: A day for the place that the act cannot apply from
      Given the act Directive 2013/36 as Cellar has it
      Then Cellar gives 16 January 2023 for the change of Directive 2022/2556 at article 85 paragraph 2
      And a provision of Directive 2022/2556 cannot apply from 16 January 2023
      And on 1 February 2023, the change of Directive 2022/2556 at article 85 paragraph 2 is in force and does not apply yet

    Example: A change of an act that applies from more than one day, before the first of them
      Given the act Directive 2013/36 as Cellar has it
      Then a provision of Directive 2019/878 cannot apply from 27 June 2019
      And Cellar gives 27 June 2019 for the change of Directive 2019/878 at article 10
      And on 1 January 2020, the change of Directive 2019/878 at article 10 is in force and does not apply yet

  Rule: applies-in-part · A part applies before the whole act
    The act is in force. A date for a part is on or before the day, and the day is before the day
    for the whole act. Only an act on its own has this state. If the data does not tell the day
    for the whole act, a date for a part does not give this state.

    Example: An act with parts that apply before the whole act
      Given the act 2018/1862 as Cellar has it
      Then 2018/1862 has a date for a part on 28 December 2019
      And 2018/1862 applies from 7 March 2023
      And on 1 August 2022, 2018/1862 applies in part

  Rule: not-known · The viewer does not know the state
    The act is in force, and the data does not tell if it applies. The viewer does not know the
    state, and says so. This is true in each of these cases:
    - The data gives no day from which it can apply. For example, a clause sets the day by an
      event.
    - The day is on or after the first day from which it can apply, and before the last.
    - A clause sets one more day that Cellar does not give, and the day is on or after the first
      day.

    The viewer does not give a state that it must guess.

    Example: An act with a part that applies, and no day for the whole act
      Given the act 767/2008 as Cellar has it
      Then 767/2008 has a date for a part on 2 September 2008
      And the date of application of 767/2008 is a placeholder
      And on 1 January 2012, 767/2008 has a state that is not known

    Example: A clause sets the day by an event
      Given the act 2018/1862 as Cellar has it
      Then the date of application of 2021/1133 is a placeholder
      And Cellar gives no day for the change of 2021/1133 at article 18a
      And on 1 September 2021, the change of 2021/1133 at article 18a has a state that is not known

    Example: A change of an act that applies from more than one day, between the first and the last
      Given the act Directive 2013/36 as Cellar has it
      Then on 1 January 2021, the change of Directive 2019/878 at article 10 has a state that is not known

    Example: A clause sets one more day that Cellar does not give
      Given the act 767/2008 as Cellar has it
      Then 2021/1134 applies from more than one day: 2 August 2021, 3 August 2022 and 3 August 2023
      And a clause of 2021/1134 sets one more day that Cellar does not give
      And Cellar gives no day for the change of 2021/1134 at article 29
      And on 1 January 2024, the change of 2021/1134 at article 29 has a state that is not known

  Rule: how-an-amending-act-stands · How an amending act stands on a day
    An amending act stands as its changes stand. Before its entry into force, it is not in force.
    It applies if each change applies. It does not apply yet if each change does not apply yet. It
    applies in part if a change applies and a change does not apply yet. In each other mix, the
    state of the act is not known. An act for which Cellar lists no changed place counts as one
    change, with the state of the whole act.

    Example: An act before its entry into force
      Given the act 2018/1862 as Cellar has it
      When the reader asks how 2022/1190 stands on 1 July 2022
      Then as a whole it is not in force

    Example: Each change applies
      Given the act 2015/1998 as Cellar has it
      When the reader asks how 2017/815 stands on 1 January 2018
      Then 62 of its changes apply, 0 do not apply yet and 0 have a state that is not known
      And as a whole it applies

    Example: No change applies yet
      Given the act 767/2008 as Cellar has it
      When the reader asks how 2024/1356 stands on 1 January 2025
      Then 0 of its changes apply, 2 do not apply yet and 0 have a state that is not known
      And as a whole it is in force and does not apply yet

    Example: A change applies, and the others do not apply yet
      Given the act 2018/1862 as Cellar has it
      When the reader asks how 2022/1190 stands on 1 August 2022
      Then 1 of its changes apply, 18 do not apply yet and 0 have a state that is not known
      And as a whole it applies in part

    Example: Changes that apply, and changes whose state is not known
      Given the act 767/2008 as Cellar has it
      When the reader asks how 2021/1134 stands on 1 January 2024
      Then 34 of its changes apply, 0 do not apply yet and 126 have a state that is not known
      And as a whole it has a state that is not known

    Example: An act for which Cellar lists no changed place
      Given the act 917/2004 as Cellar has it
      Then Cellar lists no changed place for 2015/1366
      When the reader asks how 2015/1366 stands on 1 September 2015
      Then 1 of its changes apply, 0 do not apply yet and 0 have a state that is not known
      And as a whole it applies
