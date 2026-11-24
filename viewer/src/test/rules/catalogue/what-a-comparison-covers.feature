# SPDX-License-Identifier: MIT
Feature: What a comparison covers
  Which two versions a comparison is between, and which acts it shows. A comparison compares the
  law on two days, From and To, in a language. The days are those of the timeline of the act in
  the language. What the page says where a day has no version, and how the timeline moves the two
  days, is in docs/design.md.

  Rule: the-days-when-the-reader-gives-none · The two days when the reader gives none
    To is the first day of the version that applies on the day the reader asks, or of the latest
    version if none applies on that day. From is the first day of the version before it. If the
    version of To is the first version, From is its first day, and To is the first day of the
    version after it.

    Example: The version that applies, and the version before it
      Given the act 2015/1998 as Cellar has it
      When the reader gives no days on 24 November 2026
      Then the comparison is from 22 March 2026 to 1 October 2026

    Example: The first version applies
      Given the act 2018/1862 as Cellar has it
      When the reader gives no days on 1 January 2019
      Then the comparison is from 27 December 2018 to 11 June 2019

    Example: No version applies yet
      Given the act 2018/1862 as Cellar has it
      When the reader gives no days on 1 December 2018
      Then the comparison is from 3 August 2021 to 1 August 2022

  Rule: the-days-as-the-reader-gives-them · The days are kept as the reader gives them
    A day that the reader gives stays as given: it need not be the first day of a version. If From
    is after To, the two days change places. Without From, From is the first day of the version
    before the version that applies on To. Without To, To is as when the reader gives no days.

    Example: From after To
      Given the act 2015/1998 as Cellar has it
      When the reader gives From 3 June 2017 and To 29 February 2016
      Then the comparison is from 29 February 2016 to 3 June 2017

    Example: Only To
      Given the act 2015/1998 as Cellar has it
      When the reader gives only To 3 June 2017
      Then the comparison is from 15 November 2015 to 3 June 2017

    Example: Only From
      Given the act 2015/1998 as Cellar has it
      When the reader gives only From 29 February 2016, on 24 November 2026
      Then the comparison is from 29 February 2016 to 1 October 2026

  Rule: a-comparison-is-of-two-versions · A comparison is of two versions
    The law on any two days is compared at the first days of the versions that apply on them. A
    comparison is between the first days of two versions, the earlier first.

    Example: Two days compared at their versions
      Given the act 2015/1998 as Cellar has it
      Then the law on 3 June 2017 and the law on 1 January 2022 are compared at the versions of 29 February 2016 and of 31 December 2021

    Example: The first days of two versions
      Given the act 2015/1998 as Cellar has it
      Then a comparison is of the versions of 29 February 2016 and of 7 June 2017

    Example: A day that starts no version
      Given the act 2015/1998 as Cellar has it
      Then no comparison is at 3 June 2017 and 7 June 2017

    Example: The later version first
      Given the act 2015/1998 as Cellar has it
      Then no comparison is at 7 June 2017 and 29 February 2016

  Rule: one-version-a-day-in-each-language · One version a day in each language
    In one language, at most one version starts on a day. The original text and a consolidation
    can start on the same day in different languages. The version of a day is the one in the
    reader's language.

    Example: The original text and a consolidation of the same day
      Given the act Directive 2013/36 as Cellar has it
      Then in Croatian, the version of 17 July 2013 is the original text
      And in English, the version of 17 July 2013 is the consolidation

  Rule: the-acts-between-two-versions · The acts between two versions
    The acts between two versions are the acts that the versions after From, up to and with To,
    include, in the order of the versions. Corrigenda are among them. A version includes the acts
    that Cellar lists for it, and the acts that its own text tags first: no earlier version
    includes them, by Cellar's list or by the tags of an earlier text that is built. Where a text
    is not built, only Cellar's list counts for it.

    Example: An act that the text tags before Cellar lists it
      Given the act 767/2008 as Cellar has it
      And the viewer builds its texts where the converter has a sample
      Then Cellar lists Corrigendum 5 to 767/2008 for the version of 29 December 2017
      And the acts between the versions of 18 October 2013 and of 29 December 2017 include 2017/2226
      And the acts between the versions of 18 October 2013 and of 29 December 2017 include Corrigendum 5 to 767/2008

    Example: A text that is not built
      Given the act 767/2008 as Cellar has it
      Then the acts between the versions of 18 October 2013 and of 29 December 2017 do not include 2017/2226

  Rule: one-act-between-the-versions · The changes of one act
    A comparison can show the changes of one act only if that act is between its two versions.

    Example: An act between the versions, and one that is not
      Given the act 2015/1998 as Cellar has it
      Then a comparison of the versions of 29 February 2016 and of 7 June 2017 can show the changes of 2017/815 only
      But a comparison of the versions of 29 February 2016 and of 7 June 2017 cannot show the changes of 2021/255 only
