# SPDX-License-Identifier: MIT
Feature: From when a change applies
  The sidenote of a difference says, for each act that made it, from when the change applies. The
  date comes from the act's dates in Cellar.

  Rule: date-from-the-act · The date is the act's
    The date of a change comes from the dates of the act that made it. It never comes from the
    date of the text that first has the change.

    Example: A change applies from its act's date, before the text that has it
      Given the comparison of the texts of 2015/1998 of 29 February 2016 and of 7 June 2017
      When the reader asks from when the change of 2017/815 applies
      Then it applies from 1 June 2017
      And no act moved the date

  Rule: cellar-day-else-the-parts · Cellar's day for the occurrence, else the parts
    Cellar can give a day for the change at the occurrence. That day chooses the date if it is one
    of the act's dates and it is after From. A day that is not after From is stale: the earlier
    text does not have the change. If no day chooses the date, the act applies from more than one
    day, and the sidenote lists each day from which a part applies.

    Example: Cellar's day is one of the act's dates, after From
      Given the comparison of the texts of 2015/1998 of 22 March 2026 and of 1 October 2026
      And Cellar dates the change of 2026/449 at the occurrence from 1 October 2026
      When the reader asks from when the change of 2026/449 applies
      Then it applies from 1 October 2026

    Example: Cellar's day is not one of the act's dates
      Given the comparison of the texts of 2015/1998 of 22 March 2026 and of 1 October 2026
      And Cellar dates the change of 2026/449 at the occurrence from 15 May 2026
      When the reader asks from when the change of 2026/449 applies
      Then it applies from more than one day
        | from            | what                 |
        | 22 March 2026   | its entry into force |
        | 1 October 2026  | a part               |

    Example: Cellar's day is not after From, so it is stale
      Given the comparison of the texts of 2015/1998 of 1 July 2021 and of 31 December 2021
      And Cellar dates the change of 2019/103 at the occurrence from 1 February 2019
      When the reader asks from when the change of 2019/103 applies
      Then it applies from more than one day
        | from             | what                 |
        | 1 February 2019  | its entry into force |
        | 31 December 2021 | a part               |

  Rule: no-day-known · No day known
    If Cellar has no day for the act's application, a clause of the act sets the day, and the
    sidenote names the clause. If Cellar names no such clause, the sidenote says nothing of when the
    change applies.

    Example: A clause sets the day
      Given the comparison of the texts of 2015/1998 of 2 July 2020 and of 15 March 2021
      When the reader asks from when the change of 2019/413 applies
      Then it applies from a day that Article 3 of 2019/413 sets

    Example: An event sets the day, and Cellar names no clause
      Given the comparison of the texts of 917/2004 of 1 May 2004 and of 24 August 2004
      When the reader asks from when the change of 1484/2004 applies
      Then the sidenote says nothing of when it applies

  Rule: entry-into-force-only · Only an entry into force
    An act whose only date is its entry into force applies from that day. A directive can have
    only its entry into force. It is in force from that day. The data does not say from when it
    applies: the Member States apply it through their own measures. The acts that changed the clause that
    sets the date moved the date, and the sidenote names them.

    Example: An act applies from its entry into force, which another act moved
      Given the comparison of the texts of 2015/1998 of 1 July 2021 and of 31 December 2021
      When the reader asks from when the change of 2019/1583 applies
      Then it applies from 31 December 2021, its entry into force
      And 2020/910 moved the date

    Example: A directive that has only its entry into force
      Given the comparison of the texts of Directive 1976/207 of 12 February 1976 and of 5 October 2002
      When the reader asks from when the change of Directive 2002/73 applies
      Then it is in force from 5 October 2002, and the data does not say from when it applies

  Rule: corrigendum-corrects-from-the-start · A corrigendum corrects from the start
    A corrigendum corrects its act from the start. The start is the earliest day from which the
    act applies, or the entry into force if the act has no day of application. The sidenote also
    names the first text between the two days that has the corrigendum.

    Example: A corrigendum of an act with a day of application
      Given the comparison of the texts of 2015/1998 of 1 July 2021 and of 31 December 2021
      When the reader asks from when the change of Corrigendum 1 to 2019/103 applies
      Then it corrects 2019/103 from 31 December 2021
      And the first text between the two days that has it is of 31 December 2021

    Example: A corrigendum of an act that has only its entry into force
      Given the comparison of the texts of 2015/1998 of 1 April 2023 and of 26 May 2024
      When the reader asks from when the change of Corrigendum 1 to 2024/1255 applies
      Then it corrects 2024/1255 from 26 May 2024
      And the first text between the two days that has it is of 26 May 2024

  Rule: change-that-does-not-apply-yet · A change that does not apply yet on the day
    A text can include the change of an act before the change applies. On a day before the change
    applies, a difference says so if its occurrence has such a change
    (changes-that-do-not-apply-yet). The difference names the act and the day.

    A difference says nothing of it if its occurrence has no such change. For example, these
    changes apply on the day:
    - a change of the same act that applies already
    - a change by another act or by a corrigendum

    Background:
      Given the comparison of the texts of 767/2008 of 18 October 2013 and of 29 December 2017
      When the reader reads the comparison on 1 January 2018

    Example: A change that the later text includes before it applies
      Then at the inserted article 10 paragraph 1 point (da), the difference says that 2017/2226 does not apply yet on 1 January 2018
      And 14 of the 35 differences say that 2017/2226 does not apply yet

    Example: A change of the same act that applies already
      Then at the inserted article 17a, the difference says that each change applies

    Example: A correction by a corrigendum
      Then at the changed article 5 paragraph 1 point (a), the difference says that each change applies
