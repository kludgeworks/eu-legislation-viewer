# SPDX-License-Identifier: MIT
Feature: Who made a change, and from when
  What the sidenote of a difference says: which act made the change, in which order, and from
  when it applies.

  Rule: R-WHO-02 The tags of an occurrence
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

  Rule: R-WHO-10 A corrigendum corrects
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

  Rule: R-WHO-13 The modification of each step
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

  Rule: R-WHO-14 The modification where no act is known
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

  Rule: R-WHO-23 The modification that Cellar gives
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
