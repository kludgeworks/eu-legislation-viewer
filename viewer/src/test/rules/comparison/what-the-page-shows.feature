# SPDX-License-Identifier: MIT
Feature: What the summary and the legend of a comparison say
  The summary says how many provisions differ, and names the act that made them if one act made
  them all. The legend names the edits that the page shows.

  Rule: R-SHOW-06 The act that made every difference
    If one known act made every difference, the summary names it. If a difference has no known act,
    or more than one act made the differences, the summary names no act.

    Example: One act made every difference
      Given the page shows these differences
        | kind     | inserted words | deleted words | made by  |
        | changed  | 3              | 1             | 2026/449 |
        | inserted | 12             | 0             | 2026/449 |
      Then the summary names 2026/449

    Example: Two acts made the differences
      Given the page shows these differences
        | kind    | inserted words | deleted words | made by  |
        | changed | 3              | 1             | 2026/449 |
        | changed | 2              | 0             | 2019/103 |
      Then the summary names no act

    Example: Two acts made one difference together
      Given the page shows these differences
        | kind    | inserted words | deleted words | made by               |
        | changed | 3              | 1             | 2019/103 and 2019/1583 |
      Then the summary names no act

    Example: One difference has no known act
      Given the page shows these differences
        | kind    | inserted words | deleted words | made by      |
        | changed | 3              | 1             | 2026/449     |
        | deleted | 0              | 40            | no known act |
      Then the summary names no act

  Rule: R-SHOW-07 The legend names only the edits that the page shows
    The legend names inserted words, deleted words and the words of a correction, each only if the
    page shows some. An inserted occurrence shows all its words inserted, and a deleted occurrence
    shows all its words deleted.

    Example: Only inserted words
      Given the page shows these differences
        | kind    | inserted words | deleted words | made by  |
        | changed | 3              | 0             | 2026/449 |
      Then the legend shows
        | inserted words | deleted words | a correction |
        | yes            | no            | no           |

    Example: Inserted and deleted words
      Given the page shows these differences
        | kind    | inserted words | deleted words | made by  |
        | changed | 3              | 2             | 2026/449 |
      Then the legend shows
        | inserted words | deleted words | a correction |
        | yes            | yes           | no           |

    Example: An inserted occurrence and a deleted occurrence
      Given the page shows these differences
        | kind     | inserted words | deleted words | made by  |
        | inserted | 0              | 0             | 2026/449 |
        | deleted  | 0              | 0             | 2019/103 |
      Then the legend shows
        | inserted words | deleted words | a correction |
        | yes            | yes           | no           |

    Example: A correction
      Given the page shows these differences
        | kind    | inserted words | deleted words | made by                   |
        | changed | 1              | 1             | Corrigendum 1 to 2017/815 |
      Then the legend shows
        | inserted words | deleted words | a correction |
        | no             | no            | yes          |

  Rule: R-SHOW-17 The words of a correction are only a correction
    The inserted and deleted words of a correction count as the words of a correction, not as
    inserted or deleted words.

    Example: A correction with inserted words, and an amendment with deleted words
      Given the page shows these differences
        | kind    | inserted words | deleted words | made by                   |
        | changed | 4              | 0             | Corrigendum 1 to 2017/815 |
        | changed | 0              | 2             | 2026/449                  |
      Then the legend shows
        | inserted words | deleted words | a correction |
        | no             | yes           | yes          |
