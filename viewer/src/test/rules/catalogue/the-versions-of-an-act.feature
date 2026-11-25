# SPDX-License-Identifier: MIT
Feature: The versions of an act
  The versions of an act in a language: its original text, as the Official Journal published it,
  and its consolidations, in date order. Each version starts on its date and applies until the next
  version starts.

  Rule: the-original-text-of-an-act · The original text of an act
    The original text starts on the entry into force of the act. A treaty edition starts on its
    date of document, because each earlier edition is an act of its own.

    A consolidation can start before the entry into force, for example after a corrigendum. Then
    the original text starts on its date of document, if that day is before the consolidation. So
    the reader can compare the act as published with the corrected text.

    Example: An act whose first consolidation comes after its entry into force
      Given the act 2015/1998 as Cellar has it
      Then in English, the first version is the original text of 15 November 2015
      And the next version is the consolidation of 29 February 2016

    Example: A corrigendum before the entry into force
      Given the act 2020/1812 as Cellar has it
      Then in English, the first version is the original text of 1 December 2020
      And the next version is the consolidation of 2 December 2020
