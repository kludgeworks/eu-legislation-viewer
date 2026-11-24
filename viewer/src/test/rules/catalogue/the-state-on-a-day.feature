# SPDX-License-Identifier: MIT
Feature: The state on a day
  The legal state of an act, or of one change that an amending act makes, on a day: not in force,
  in force and does not apply yet, applies, applies in part, or not known. A change is an
  amendment that changes the words of the English text. These rules use only the catalogue: the
  dates of the acts and their amendments. They do not use the texts of the versions.

  Rule: state-on-a-day · The state of an act, or of one change, on a day
    Before the entry into force of the act, it is not in force. If it applies from one day, it
    applies from that day. Before that day, it is in force and does not apply yet. For one change,
    the day is the day that Cellar gives for the changed place, if the act can apply from it.

    If the act applies from more than one day, and Cellar gives no such day for the changed place,
    the change does not apply yet before the first of these days. It applies from the last of them,
    unless a clause sets one more day that Cellar does not give. Between them, the state is not
    known.

    In each other case the state is not known. For example, a clause sets the day by an event.

    Example: An act before its entry into force, and before its date of application
      Given the act 2016/679 as Cellar has it
      Then on 1 May 2016, 2016/679 is not in force
      And on 1 January 2017, 2016/679 is in force and does not apply yet
      And on 25 May 2018, 2016/679 applies

    Example: Two changes, from the days that Cellar gives for their places
      Given the act 2018/1862 as Cellar has it
      Then Cellar gives 1 August 2022 for the change of 2022/1190 at article 79 paragraph 7
      And on 1 August 2022, the change of 2022/1190 at article 79 paragraph 7 applies
      And Cellar gives 15 October 2026 for the change of 2022/1190 at article 24 paragraph 1
      And on 1 August 2022, the change of 2022/1190 at article 24 paragraph 1 is in force and does not apply yet
      And on 15 October 2026, the change of 2022/1190 at article 24 paragraph 1 applies

    Example: A day for the place that the act cannot apply from
      Given the act Directive 2013/36 as Cellar has it
      Then Cellar gives 16 January 2023 for the change of Directive 2022/2556 at article 85 paragraph 2
      And a provision of Directive 2022/2556 cannot apply from 16 January 2023
      And on 1 February 2023, the change of Directive 2022/2556 at article 85 paragraph 2 is in force and does not apply yet
      And on 17 January 2025, the change of Directive 2022/2556 at article 85 paragraph 2 applies

    Example: A change of an act that applies from more than one day
      Given the act Directive 2013/36 as Cellar has it
      Then a provision of Directive 2019/878 cannot apply from 27 June 2019
      And Cellar gives 27 June 2019 for the change of Directive 2019/878 at article 10
      And on 1 January 2020, the change of Directive 2019/878 at article 10 is in force and does not apply yet
      And on 1 January 2021, the change of Directive 2019/878 at article 10 has a state that is not known
      And on 1 January 2022, the change of Directive 2019/878 at article 10 applies

    Example: A clause sets one more day that Cellar does not give
      Given the act 767/2008 as Cellar has it
      Then 2021/1134 applies from more than one day: 2 August 2021, 3 August 2022 and 3 August 2023
      And a clause of 2021/1134 sets one more day that Cellar does not give
      And Cellar gives no day for the change of 2021/1134 at article 29
      And on 1 January 2024, the change of 2021/1134 at article 29 has a state that is not known

    Example: A clause sets the day by an event
      Given the act 2018/1862 as Cellar has it
      Then the date of application of 2021/1133 is a placeholder
      And Cellar gives no day for the change of 2021/1133 at article 18a
      And on 1 September 2021, the change of 2021/1133 at article 18a has a state that is not known

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
