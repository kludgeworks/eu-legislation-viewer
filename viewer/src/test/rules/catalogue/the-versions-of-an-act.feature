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

  Rule: today-is-the-day-in-brussels · Today is the day in Brussels
    The viewer takes today as the date in Brussels: the EU adopts its acts there, and the Official
    Journal dates them there. So near midnight, today can be another day for the reader.

    Example: Just after midnight in Brussels, in winter
      When the clock shows 23:30 UTC on 31 December 2025
      Then today is 1 January 2026

    Example: Just before midnight in Brussels, in summer
      When the clock shows 21:59 UTC on 30 June 2026
      Then today is 30 June 2026
