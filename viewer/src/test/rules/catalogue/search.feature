# SPDX-License-Identifier: MIT
Feature: Search
  Which act a citation that a reader types in the search names. A citation has the year and the
  number of an act, in either order. It can also name the kind of act. If the citation names one
  act, the search goes to that act. If not, the reader gets the list that the search finds.

  Rule: a-citation-goes-to-one-act · A citation goes to an act only if it names one act
    The acts that a citation can name are the acts whose year and number are its two numbers, in
    either order. Of these acts, the citation names only the acts of its kind and its subtype, if
    the reader typed them:
    - the kind: Regulation, Directive or Decision
    - the subtype: Implementing or Delegated

    If the citation names exactly one act, the search goes to that act. If it names two or more
    acts, or none, the search does not guess: the reader gets the list.

    Example: The year and the number in either order
      Given these acts have the year and the number of the citation:
        | act      | type |
        | 2016/679 | REG  |
      When the reader types "679/2016"
      Then the search goes to 2016/679

    Example: Two acts with the same year and number
      Given these acts have the year and the number of the citation:
        | act               | type |
        | 185/2010          | REG  |
        | Decision 2010/185 | DEC  |
      When the reader types "185/2010"
      Then the reader gets the list

    Example: The kind of act tells the two acts apart
      Given these acts have the year and the number of the citation:
        | act               | type |
        | 185/2010          | REG  |
        | Decision 2010/185 | DEC  |
      When the reader types "Decision 2010/185/EU"
      Then the search goes to Decision 2010/185

    Example: An implementing regulation
      Given these acts have the year and the number of the citation:
        | act       | type     |
        | 2015/1998 | REG_IMPL |
      When the reader types "Implementing Regulation (EU) 2015/1998"
      Then the search goes to 2015/1998

    Example: A delegated regulation
      Given these acts have the year and the number of the citation:
        | act      | type    |
        | 2019/945 | REG_DEL |
      When the reader types "Delegated Regulation (EU) 2019/945"
      Then the search goes to 2019/945

    Example: A subtype that the act does not have
      Given these acts have the year and the number of the citation:
        | act      | type |
        | 2016/679 | REG  |
      When the reader types "Implementing Regulation 2016/679"
      Then the reader gets the list

  Rule: a-year-of-two-digits · A year of two digits is before 2000
    An act from before 2000 can cite its year with two digits, as Directive 95/46/EC does. The
    first number is the year 19xx if these conditions are true:
    - The citation names the kind of act.
    - The first number has two digits, and the second number has fewer than four digits.
    - The first number is 50 or more.

    Example: A directive from 1995
      When the reader types "Directive 95/46/EC"
      Then its numbers are 1995 and 46

    Example: Without the kind of act, the year stays as the reader typed it
      When the reader types "95/46/EC"
      Then its numbers are 95 and 46

    Example: Words that are no citation
      When the reader types "GDPR"
      Then it is no citation
