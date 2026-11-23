# SPDX-License-Identifier: MIT
Feature: Who made a change, and from when
  What the sidenote of a difference says: which act made the change, in which order, and from
  when it applies.

  Rule: only-acts-between-the-texts · Only acts between the two texts
    A sidenote names only the acts between the two texts: the acts that the texts after the earlier
    text, up to the later text, include. It reads the tags of the occurrence in the later text and
    Cellar's amendments of the occurrence. An act that the earlier text includes already is not
    named, unless a part of it applies between the two texts (act-named-for-a-part).

    Example: A tag names an act that the earlier text includes already
      Given the comparison of the texts of 2015/1998 of 31 December 2021 and of 1 January 2026
      And the difference at the changed Appendix 3-B, indent 8
      When the reader asks who made it
      Then the steps are
        | text of       | modification | acts      |
        | 4 April 2022  | amended      | 2022/421  |
        | 26 May 2024   | amended      | 2024/1255 |
        | 21 May 2025   | amended      | 2025/920  |
      But 2019/413 is not named

  @revision-2
  Rule: cellars-amendments-that-count · Which of Cellar's amendments count
    A sidenote reads only the amendments of the acts between the two texts that change the text in
    the language of the page. An amendment that applies only after the later text is left out: it
    is a later part of the act, which the later text does not have. A deletion or a repeal is found
    on the earlier text, or else on the later text, which keeps the deleted occurrence. An addition
    or a replacement is found on the later text.

    Example: The amendments of the acts between the two texts
      Given the comparison of the texts of 2015/1998 of 2 July 2020 and of 15 March 2021
      Then Cellar's amendments that count are those of 2019/413 and 2021/255

    Example: An act that changes only other languages
      Given the comparison of the texts of 2015/1998 of 29 February 2016 and of 7 June 2017
      Then Cellar's amendments that count are those of 2017/815
      And 2017/837 is between the two texts, but none of its amendments count

    Example: An amendment that applies only after the later text
      Given the comparison of the texts of 2015/1998 of 2 July 2020 and of 15 March 2021
      Then Cellar's amendment of 2021/255 at point 6.6.1.3 does not count: it applies from 1 July 2021
      And Cellar's amendment of 2021/255 at point 6.1.4 does not count: it applies from 1 March 2022
      But Cellar's amendment of 2021/255 at point 6.8.5.7 counts

    Example: A repeal is found on the earlier text
      Given the comparison of the texts of 2015/1998 of 29 February 2016 and of 1 January 2026
      Then Cellar's repeals of 2017/815 at point 6.5.5 and point 6.5.6 are found on the earlier text

    Example: A repeal of a point that the earlier text does not have yet
      Given the comparison of the texts of 2015/1998 of 15 November 2015 and of 15 March 2021
      Then Cellar's repeal of 2021/255 at point 6.8.5.5 is found at point 6.8.5.5 of the later text

  Rule: tags-of-an-occurrence · The tags of an occurrence
    An occurrence's tags are its own, each once and in order. An occurrence without tags takes
    those of the nearest occurrence around it that has some.

    Example: A point has a tag of its own
      Given the consolidated version of 2015/1998 of 1 October 2026
      When the reader asks for the tags of point 8.1.2.3(c)
      Then the tags are
        | act      | modification | taken from |
        | 2026/449 | replaced     |            |

    Example: A point has several tags, each once and in the order in which they stand
      Given the consolidated version of 2015/1998 of 1 October 2026
      When the reader asks for the tags of point 6.8.3.1
      Then the tags are
        | act       | modification | taken from |
        | 2017/815  | replaced     |            |
        | 2022/1174 | deleted      |            |

    Example: A point without tags takes the tag of the point that an act replaced whole
      Given the consolidated version of 2015/1998 of 22 March 2026
      When the reader asks for the tags of point 1.2.2.3(a)
      Then the tags are
        | act      | modification | taken from    |
        | 2026/449 | replaced     | point 1.2.2.3 |

  Rule: who-deleted-an-occurrence · Who deleted an occurrence
    The later text no longer has a deleted occurrence, so its own tags cannot say who deleted it. A
    consolidated text often keeps a deleted provision, marked as deleted under the tag of the act
    that deleted it: that act deleted it. If the text does not keep it, the act that replaced the
    occurrence around it whole deleted it with the rest. If neither, no tag names the act.

    Example: The later text keeps the deleted point, marked as deleted by its act
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      And the difference at the deleted Article 67(1)(e)
      Then the tags of the difference are
        | act                 | modification | taken from |
        | Directive 2024/1619 | deleted      |            |

    Example: An act replaced the point around it whole
      Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 22 March 2026
      And the difference at the deleted point 12.4.2.1(d)
      Then the tags of the difference are
        | act      | modification | taken from   |
        | 2026/449 | replaced     | point 12.4.2.1 |

    Example: Nothing in the later text names the act
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      And the difference at the deleted Article 23(1)(a)
      Then the difference has no tags

  Rule: tags-else-cellar · The tags, else Cellar, name the acts
    For each text, the tags decide which acts are named. Cellar names the acts only of a text whose
    acts no tag names.

    Example: Cellar also names an act of the text that the tag names
      Given the comparison of the texts of 2015/1998 of 29 February 2016 and of 1 October 2026
      And the difference at the inserted point 6.0.4
      When the reader asks who made it
      Then the steps are
        | text of       | modification | acts     |
        | 15 March 2021 | inserted     | 2021/255 |
      But 2019/413 is not named

    Example: Cellar names one act
      Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 22 March 2026
      And the difference at the deleted point 9.1.3.6
      When the reader asks who made it
      Then the steps are
        | text of       | modification | acts     |
        | 22 March 2026 | deleted      | 2026/449 |

    Example: Cellar names two acts
      Given the comparison of the texts of 2015/1998 of 31 December 2021 and of 1 January 2026
      And the difference at the deleted point 6.5.4
      When the reader asks who made it
      Then the steps are
        | text of     | modification | acts      |
        | 1 July 2022 | replaced     | 2022/1174 |
        | 26 May 2024 | deleted      | 2024/1255 |

  Rule: acts-changed-together · Acts that changed an occurrence together
    Acts whose tags say that they replaced the same occurrence changed it together. They are one
    step, in the first text that includes all of them.

    Example: Two acts replaced a point together
      Given the comparison of the texts of 2015/1998 of 16 February 2020 and of 31 December 2021
      And the difference at the changed point 11.1.2
      When the reader asks who made it
      Then the steps are
        | text of          | modification | acts                  |
        | 2 July 2020      | replaced     | 2020/910              |
        | 31 December 2021 | replaced     | 2019/103 and 2019/1583 |

  Rule: no-act-named · Where nothing names the act
    Where no tag and no amendment in Cellar names an act for a difference, the sidenote does not
    guess. It names the text where the change first appears, and the acts that this text
    consolidates, but no act as the one that made the change. That text is the later text of the
    first pair of neighbouring texts, after the earlier day and up to the later day, whose
    comparison has the difference. A pair whose texts are not both built is passed over. But if
    two acts or more that are not corrigenda are between the two texts, and one text first
    includes them all, one of them made the change: the sidenote names them, and says that the
    data does not say which.

    Example: Two acts that one text first includes, and nothing says which made the change
      Given the comparison of the texts of 2015/1998 of 2 July 2020 and of 15 March 2021
      When the reader asks who made the differences that no act is named for
      Then 3 differences are made by one of 2019/413 and 2021/255, and the data does not say which

    Example: The later text follows the earlier text
      Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 22 March 2026
      And the difference at the deleted point 12.9.3.14
      When the reader asks who made it
      Then no act is named
      When the reader asks where the change first appears
      Then it first appears in the text of 22 March 2026, which consolidates 2026/449 and Corrigendum 1 to 2026/449

    Example: The first pair of neighbouring texts whose comparison has the difference
      Given the comparison of the texts of 2015/1998 of 1 January 2026 and of 1 October 2026
      And the difference at the deleted point 12.9.3.14
      When the reader asks who made it
      Then no act is named
      When the reader asks where the change first appears
      Then it first appears in the text of 22 March 2026, which consolidates 2026/449 and Corrigendum 1 to 2026/449

    Example: A pair whose texts are not both built is passed over
      Given the comparison of the texts of 2015/1998 of 31 December 2021 and of 22 March 2026
      And only the texts of 31 December 2021, 1 January 2026 and 22 March 2026 are built
      And the difference at the deleted point 12.9.3.14
      When the reader asks who made it
      Then no act is named
      When the reader asks where the change first appears
      Then it first appears in the text of 22 March 2026, which consolidates 2026/449 and Corrigendum 1 to 2026/449

  Rule: amendments-around-and-inside · Amendments of the occurrences around and inside
    The amendments of an occurrence in Cellar include those of an occurrence around it. For an
    occurrence that was inserted or deleted whole, they also include those of the occurrences in
    it. A changed occurrence does not take those of the occurrences in it, because they differ on
    their own. A point numbered under a point, which the text keeps beside it, counts as in that
    point.

    Example: A point takes the amendment of the point around it
      Given the comparison of the texts of 2015/1998 of 15 November 2015 and of 16 February 2020
      And the difference at the inserted point 11.2.3.8(h)
      When the reader asks who made it
      Then the steps are
        | text of     | modification | acts     |
        | 7 June 2017 | inserted     | 2017/815 |

    Example: A point that the text keeps beside its point takes that point's amendment
      Given the comparison of the texts of 2015/1998 of 15 November 2015 and of 31 December 2021
      And the difference at the changed point 3.0.6
      When the reader asks who made it
      Then the steps are
        | text of       | modification | acts     |
        | 7 June 2017   | replaced     | 2017/815 |
        | 15 March 2021 | replaced     | 2019/413 |

    Example: An inserted point takes the amendments of the points in it
      Given the comparison of the texts of 2015/1998 of 15 November 2015 and of 1 January 2026
      And the difference at the inserted point 6.8.7
      When the reader asks who made it
      Then the steps are
        | text of       | modification | acts     |
        | 15 March 2021 | inserted     | 2021/255 |
        | 1 April 2023  | amended      | 2023/566 |

    Example: A changed point does not take the amendments of the points in it
      Given the comparison of the texts of 2015/1998 of 15 November 2015 and of 16 February 2020
      And the difference at the changed point 12.0
      When the reader asks who made it
      Then the steps are
        | text of          | modification | acts     |
        | 16 February 2020 | replaced     | 2020/111 |
      But 2019/103 is not named

  Rule: corrigendum-corrects · A corrigendum corrects
    A difference that only corrigenda made is a correction, not an amendment. A difference that an
    amending act made, alone or together with a corrigendum, is not a correction. Nor is a
    difference that no known act made.

    Scenario Outline: <case>
      Given a difference made by "<made by>"
      Then the difference <is> a correction

      Examples:
        | case                                         | made by                                                 | is     |
        | One corrigendum made it                      | Corrigendum 1 to 2017/815                               | is     |
        | Two corrigenda made it                       | Corrigendum 1 to 2017/815 and Corrigendum 4 to 2017/815 | is     |
        | An amending act made it with a corrigendum   | 2019/103 and Corrigendum 1 to 2017/815                  | is not |
        | An amending act made it                      | 2019/103                                                | is not |
        | No known act made it                         | no known act                                            | is not |

  Rule: one-step-for-each-text · One step for each text
    The acts are in steps, one step for each text that first includes them, oldest first.

    Example: A point that one act inserted and another act replaced
      Given the comparison of the texts of 2015/1998 of 29 February 2016 and of 1 October 2026
      And the difference at the inserted point 12.14.1
      When the reader asks who made it
      Then the steps are
        | text of         | modification | acts     |
        | 1 February 2019 | inserted     | 2019/103 |
        | 1 April 2023    | replaced     | 2023/566 |

  Rule: tagged-act-was-last · The act that the tags name was last
    The act that the tags name was the last act to change the occurrence. So it comes after the
    acts that only Cellar names, even if an earlier text includes it.

    Example: The tagged act comes after an act that only Cellar names
      Given the comparison of the texts of 2015/1998 of 15 November 2015 and of 31 December 2021
      And the difference at the changed point 4.0.3
      When the reader asks who made it
      Then the steps are
        | text of          | modification | acts     |
        | 15 March 2021    | replaced     | 2019/413 |
        | 31 December 2021 | replaced     | 2019/103 |

  Rule: modification-of-each-step · The modification of each step
    A sidenote names each act with what it did to the occurrence in its step: its modification.
    The first step of an inserted occurrence inserted it, and the last step of a deleted
    occurrence deleted it. A corrigendum corrected it. Any other step replaced or renumbered the
    occurrence, as the act's tag says, or else amended it: words were inserted in, or deleted
    from, an occurrence that is still there.

    Scenario Outline: <case>
      Given a difference where the occurrence was <kind>
      And a step by "<by>" whose tag says <tag>
      When the step is the <position> step of the difference
      Then the modification is <modification>

      Examples:
        | case                                                  | kind     | by            | tag        | position | modification |
        | The first step of an inserted occurrence              | inserted | an act        | replaced   | first    | inserted     |
        | A later step of an inserted occurrence                | inserted | an act        | replaced   | last     | replaced     |
        | The last step of a deleted occurrence                 | deleted  | an act        | replaced   | last     | deleted      |
        | An earlier step of a deleted occurrence               | deleted  | an act        | renumbered | first    | renumbered   |
        | A step that replaced the occurrence                   | changed  | an act        | replaced   | only     | replaced     |
        | A step that renumbered the occurrence                 | changed  | an act        | renumbered | only     | renumbered   |
        | Words deleted from an occurrence still there          | changed  | an act        | deleted    | only     | amended      |
        | Words inserted in an occurrence still there           | changed  | an act        | inserted   | only     | amended      |
        | A corrigendum corrected the occurrence                | changed  | a corrigendum | replaced   | only     | corrected    |
        | A corrigendum, even in the first step of an insertion | inserted | a corrigendum | inserted   | first    | corrected    |

  Rule: modification-without-an-act · The modification where no act is known
    Where no act is known to have made a difference, its modification is what happened to the
    occurrence: an inserted occurrence was inserted, a deleted occurrence was deleted, and an
    occurrence in both texts whose wording changed was amended. The sidenote that names the
    version where such a change first appears says the same.

    Scenario Outline: An occurrence that was <kind>
      Given a difference where the occurrence was <kind>
      When no act is known to have made it
      Then the modification is <modification>

      Examples:
        | kind     | modification |
        | inserted | inserted     |
        | deleted  | deleted      |
        | changed  | amended      |

  Rule: act-named-for-a-part · An act that an earlier text includes, named for a part
    An act that an earlier text includes already can change the text again, when a later part of
    it applies. It is named for a change if its tag is on the occurrence and one of its dates falls
    after the earlier day, up to the later day. The change applies from that day.

    Example: A directive that applies in part from the day of the later text
      Given the comparison of the texts of Directive 2013/36 of 17 January 2025 and of 11 January 2026
      And the difference at the inserted Article 3(1)(9a)
      When the reader asks who made it
      Then the steps are
        | text of         | modification | acts               |
        | 11 January 2026 | inserted     | Directive 2024/1619 |
      And Directive 2024/1619 is named for its part that applies from 11 January 2026

  Rule: modification-from-cellar · The modification that Cellar gives
    Where the text has no tag for an act, the role that Cellar gives the act's amendment of the
    occurrence says what the act did: a replacement replaced it, an addition inserted it, a
    deletion or a repeal deleted it, and an amendment amended it.

    Scenario Outline: Cellar's code <code>
      When Cellar types the amendment with the code <code>
      Then the modification is <modification>

      Examples:
        | code   | role             | modification |
        | R      | replacement      | replaced     |
        | R/PART | part replacement | replaced     |
        | J      | addition         | inserted     |
        | C      | completion       | inserted     |
        | DEL    | deletion         | deleted      |
        | SU     | abolition        | deleted      |
        | A      | repeal           | deleted      |
        | AP     | partial repeal   | deleted      |
        | M      | amendment        | amended      |

  Rule: act-changes-only-what-the-law-has · An act changes only what the law has
    An act can change an occurrence only while the law has it: up to the act that deleted it, as
    Cellar records it. So an act that changed the occurrence around a deleted occurrence after the
    deletion did not change it. A consolidated text can still print an occurrence that an act
    deleted: the texts of 2015/1998 from 2020 to 2021 print points 6.5.5 and 6.5.6, which
    2017/815 deleted.

    Example: A point that an act deleted takes no later amendment of the point around it
      Given the comparison of the texts of 2015/1998 of 15 November 2015 and of 1 January 2026
      And the difference at the deleted point 6.5.5
      When the reader asks who made it
      Then the steps are
        | text of     | modification | acts     |
        | 7 June 2017 | deleted      | 2017/815 |
