# SPDX-License-Identifier: MIT
Feature: The history of an act
  The history of an act:
  - which acts are in it, and in which order
  - its corrigenda
  - the days on which an act changes it
  - the changes of each amending act

  These rules read the versions and the amendments that Cellar gives, and the tags of the texts
  that the viewer builds.

  Rule: the-amending-acts-of-an-act · The amending acts of an act
    They are the acts that its versions in the language integrate, and the acts whose amendments
    Cellar annotates. An act can be in no version yet. The act itself and the corrigenda do not
    count: a corrigendum is not an amending act. An act that corrects only other languages does
    not count.

    Example: The amending acts of an act in English
      Given the act 2015/1998 as Cellar has it
      Then the amending acts of the act in English are, newest first:
        | 2026/449  |
        | 2025/920  |
        | 2024/2108 |
        | 2024/1255 |
        | 2023/566  |
        | 2022/1174 |
        | 2022/421  |
        | 2019/1583 |
        | 2021/255  |
        | 2020/910  |
        | 2020/111  |
        | 2019/413  |
        | 2019/103  |
        | 2018/55   |
        | 2017/815  |
        | 2015/2426 |

    Example: An act that corrects only other languages
      Given the act 2015/1998 as Cellar has it
      Then 2017/837 is not an amending act of the act in English
      But 2017/837 is an amending act of the act in Swedish
      And 2017/837 is an amending act of the act in Polish

    Example: An act that no version includes yet
      Given the act 2018/1862 as Cellar has it
      Then no version of the act includes 2021/1133
      But 2021/1133 is an amending act of the act in English

  Rule: the-order-of-the-amending-acts · The order of the amending acts
    An act is newer if its first day is later. The first day of an act is its first dated date,
    which is usually its entry into force.

    Example: An act with an older number that applies later
      Given the act 2015/1998 as Cellar has it
      Then the first day of 2019/1583 is 31 December 2021
      And the first day of 2021/255 is 11 March 2021
      And 2019/1583 is newer than 2021/255 in the history of the act in English

    Example: An act whose date of application is a placeholder
      Given the act 2015/1998 as Cellar has it
      Then the first day of 2019/413 is 16 March 2019

  Rule: the-changes-of-an-amending-act · The changes of an amending act, in the order of the text
    The changes of an amending act are its amendments that change the words in the language. They
    are in the order of the latest text that the viewer builds: a change has the position of its
    place in that text. A place that the text does not have has the position of the occurrence
    that holds it. While the viewer builds no text, the changes are in the order of the data.

    Example: The changes in the order of the text
      Given the act 2015/1998 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then the latest text that the viewer builds is the text of 1 October 2026
      And 2017/815 has 62 changes, and the first three, in the order of the text, are at:
        | annex point 1.0.3              |
        | annex point 1.1.3.1            |
        | annex point 1.2.6.2 paragraph 2 |

    Example: Places that the latest text does not have stand where the occurrence that holds them is
      Given the act 2015/1998 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then in the order of the text, these changes of 2017/815 follow one another:
        | annex point 6.4.1.6 paragraph 2 |
        | annex point 6.5.4                |
        | annex point 6.5.6                |
        | annex point 6.5.5                |
        | annex point 6.5.1                |
      And the text of 1 October 2026 does not have point 6.5.4, point 6.5.5 or point 6.5.6, and they are in point 6.5

    Example: The changes in the language
      Given the act 2015/1998 as Cellar has it
      Then 2017/837 has no changes in English
      But 2017/837 has changes in Polish

    Example: While the viewer builds no text
      Given the act 2015/1998 as Cellar has it
      Then 2017/815 has 62 changes, and the first three, in the order of the data, are at:
        | annex point 6.8.3.6 |
        | annex point 6.8.3.7 |
        | annex point 6.5.1   |

  Rule: the-corrigenda-of-an-act · The corrigenda of an act
    A corrigendum belongs to the act that it corrects: the act itself, or an amending act. A
    corrigendum is in only the languages that it corrects. So a corrigendum whose publication has
    no title in the language does not count.

    Example: A corrigendum of the act itself
      Given the act 767/2008 as Cellar has it
      Then the corrigenda of 767/2008 in English are Corrigendum 5 to 767/2008
      And the corrigenda of 767/2008 in German are none

    Example: A corrigendum of an amending act
      Given the act 767/2008 as Cellar has it
      Then the corrigenda of 2024/1356 in English are Corrigendum 1 to 2024/1356

    Example: A corrigendum that corrects only another language
      Given the act 2015/1998 as Cellar has it
      Then the corrigenda of 2017/815 in English are none
      But the corrigenda of 2017/815 in German are Corrigendum 1 to 2017/815

  Rule: the-two-texts-of-an-amending-act · The two texts that show the changes of an amending act
    The later text is the text of the first version in the language that integrates the act. The
    earlier text is the text of the version before it.

    Example: The texts of an act
      Given the act 2018/1862 as Cellar has it
      Then the changes of 2022/1190 show between the texts of 3 August 2021 and of 1 August 2022

    Example: Two acts that one version first integrates
      Given the act 2015/1998 as Cellar has it
      Then the changes of 2021/255 show between the texts of 2 July 2020 and of 15 March 2021
      And the changes of 2019/413 show between the texts of 2 July 2020 and of 15 March 2021

    Example: An act that a later version integrates
      Given the act 2015/1998 as Cellar has it
      Then the changes of 2026/449 show between the texts of 1 January 2026 and of 22 March 2026

    Example: An act that no version integrates
      Given the act 2018/1862 as Cellar has it
      Then no texts show the changes of 2021/1133

  Rule: the-occurrences-of-a-change · The occurrences of a change
    The occurrences of a change are those of the later text, if it has the place itself. If
    neither text has the place itself, they are the occurrences that the place is in.

    Example: The later text has the place
      Given the act 2018/1862 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then the occurrences of the change of 2022/1190 at article 79 paragraph 7 are article 79 paragraph 7 of the later text

    Example: Neither text has the place itself
      Given the act 767/2008 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then the changes of 810/2009 show between the texts of 2 September 2008 and of 5 April 2010
      And the occurrences of the change of 810/2009 at article 9.4 are article 9 point 4 of the later text

  Rule: the-other-acts-of-its-version · A change that one of two acts made
    The text tags most changes with the act that made them. If the first version with an act
    integrates other acts also, a change with no tag can be of each of them. The other acts are
    the acts of that version, without the corrigenda and without the act itself.

    Example: Two acts of one version
      Given the act 2015/1998 as Cellar has it
      Then the other acts of the first version with 2021/255 are 2019/413
      And the other acts of the first version with 2019/413 are 2021/255

    Example: A version with corrigenda
      Given the act 767/2008 as Cellar has it
      Then the first version with 2017/2226 is the version of 11 June 2019
      And the other acts of the first version with 2017/2226 are 2019/817

    Example: An act alone in its version
      Given the act 2018/1862 as Cellar has it
      Then the other acts of the first version with 2022/1190 are none

  Rule: the-history-of-a-provision · The acts that changed one provision
    A change at the occurrence of the provision is a change of the provision. So is a change at a
    part of it that the text does not number, and a change at an occurrence that holds the
    provision. A change at an occurrence in the provision is not in its history: that occurrence
    has its own history. An act that the text tags the occurrence with changed the provision, also
    if Cellar annotates no change of it there. The newest act is first.

    Background:
      Given the act 767/2008 as Cellar has it
      And the viewer builds its texts where the converter has a sample

    Example: The changes of a provision and of the occurrence that holds it
      Then in the text of 12 October 2025, the acts that changed article 6 paragraph 2 are, newest first, 2024/1356, 2021/1152, 2021/1134, 2019/817
      And 2021/1134 changed it at article 6
      And 2021/1152 changed it at article 6 paragraph 2

    Example: A change in the provision is not in its history
      Then in the text of 12 October 2025, 2021/1152 did not change article 6

    Example: A change at a part that the text does not number
      Then in the text of 12 October 2025, the acts that changed article 9 are, newest first, 810/2009
      And 810/2009 changed it at article 9.4

    Example: An act that the text tags the provision with
      Then in the text of 12 October 2025, the acts that changed article 10 paragraph 1 point (k) include 2017/2226
      And the text tags it with 2017/2226, which Cellar names no change of there

  Rule: a-provision-the-data-names-no-act-for · A provision that the data names no act for
    It is an occurrence that the act as published does not have, and that the data says nothing
    of. The data says nothing of an occurrence if both conditions are true:
    - The text tags no act at the occurrence, at an occurrence that holds it, or at an occurrence
      in it.
    - Cellar annotates no change at these occurrences.

    Only the outer occurrence counts. The viewer gives the first version whose text has it. If the
    viewer did not build the text of the act as published, no occurrence counts.

    Example: Two articles that the data names no act for
      Given the act 2018/1862 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then in the text of 1 August 2022, the data names no act for article 37a and article 37b
      And the first text with article 37a is the text of 1 August 2022

    Example: Without the text of the act as published
      Given the act 2018/1862 as Cellar has it
      And the viewer builds its texts where the converter has a sample, except the text of 27 December 2018
      Then in the text of 1 August 2022, the data names an act for each occurrence

  Rule: the-amending-acts-between-two-days · The amending acts between two days
    They are the amending acts that the text of the later day includes and the text of the earlier
    day does not, newest first. An act that no version includes is not between two days. If a day
    is before the first version of the act, there are none: the act has no text on that day. For
    one provision, they are those of them that are in the history of the provision.

    Example: Two acts between two days
      Given the act 2015/1998 as Cellar has it
      Then the amending acts between 2 July 2020 and 15 March 2021 are 2021/255, 2019/413

    Example: An act that no version includes is not between two days
      Given the act 2018/1862 as Cellar has it
      Then the amending acts between 3 August 2021 and 1 August 2022 are 2022/1190

    Example: A day before the first version
      Given the act 2015/1998 as Cellar has it
      Then the act has no amending acts between 1 January 2015 and 15 March 2021

    Example: The acts between two days that changed one provision
      Given the act 767/2008 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then of the amending acts between 29 December 2017 and 12 June 2026, those that changed article 6 paragraph 2 in the text of 12 October 2025 are 2024/1356, 2021/1152, 2021/1134, 2019/817

  Rule: the-days-of-change · The days on which an amending act changes the act
    The days on which an amending act changes the act:
    - An act that applies from one day changes the act on that day.
    - An act that applies from more than one day changes the act on each day from which a part of
      it applies. Its entry into force is such a day only if it is the first day from which a part
      applies.
    - An act in force changes the act on the day of its entry into force if Cellar does not tell
      from when it applies.
    - An act that applies from a day that Cellar does not know changes the act on no day.

    Example: The days of change of an act
      Given the act 2015/1998 as Cellar has it
      Then the act changes on 22 days
      And on 31 December 2021, these acts change the act: 2019/103, 2019/1583
      And 2017/815 changes the act on 1 June 2017
      And 2019/413 changes the act on no day

    Example: An act that applies from more than one day, from its entry into force
      Given the act 2015/1998 as Cellar has it
      Then 2021/255 changes the act on 11 March 2021, 15 March 2021, 1 July 2021 and 1 March 2022

    Example: An act that applies from more than one day, after its entry into force
      Given the act Directive 2013/36 as Cellar has it
      Then the entry into force of Directive 2024/1619 is 9 July 2024
      And Directive 2024/1619 changes the act on 29 July 2024, 11 January 2026, 11 July 2026 and 11 January 2027

    Example: An act in force, where Cellar does not tell from when it applies
      Given the act Directive 2013/36 as Cellar has it
      Then Directive 2015/2366 changes the act on 12 January 2016

  @revision-2
  Rule: cellars-amendments-that-count · Which of Cellar's amendments count
    A sidenote reads only some of Cellar's amendments. An amendment counts if both conditions are
    true:
    - An act between the two texts made the amendment.
    - The amendment changes the text in the language of the page.

    An amendment that applies only after the later text does not count. It is a later part of the
    act, and the later text does not have that part.

    The viewer finds each amendment on one of the two texts:
    - a deletion or a repeal: on the earlier text, else on the later text, which keeps the deleted
      occurrence
    - an addition or a replacement: on the later text

    Background:
      Given the act 2015/1998 as Cellar has it
      And the viewer builds its texts where the converter has a sample

    Example: The amendments of the acts between the two texts
      Given the two texts of 2 July 2020 and of 15 March 2021
      Then Cellar's amendments that count are those of 2019/413, 2021/255

    Example: An act that changes only other languages
      Given the two texts of 29 February 2016 and of 7 June 2017
      Then Cellar's amendments that count are those of 2017/815
      And 2017/837 is between the two texts, but none of its amendments count

    Example: An amendment that applies only after the later text
      Given the two texts of 2 July 2020 and of 15 March 2021
      Then Cellar's amendment of 2021/255 at annex point 6.6.1.3 does not count: it applies from 1 July 2021
      And Cellar's amendment of 2021/255 at annex point 6.1.4 does not count: it applies from 1 March 2022
      But Cellar's amendment of 2021/255 at annex point 6.8.5.7 counts

    Example: The viewer finds a repeal on the earlier text
      Given the two texts of 29 February 2016 and of 1 January 2026
      Then the viewer finds Cellar's repeals of 2017/815 at annex point 6.5.5 and annex point 6.5.6 on the earlier text

    Example: A repeal of a point that the earlier text does not have yet
      Given the two texts of 15 November 2015 and of 15 March 2021
      Then the viewer finds Cellar's repeal of 2021/255 at annex point 6.8.5.5 in point 6.8.5.5 of the later text
