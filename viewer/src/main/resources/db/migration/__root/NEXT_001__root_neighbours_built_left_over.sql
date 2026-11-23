-- SPDX-License-Identifier: MIT
-- v2026.11.17, which ran while v2026.11.18 deployed, could still publish NeighboursBuilt. Its
-- class is now ConsecutiveTextsBuilt, so a publication of it that is not completed moves to the new
-- names, as V2026_11_18_001 moved the ones before it.
update event_publication
set
  event_type
  = replace(event_type, '.catalogue.NeighboursBuilt', '.catalogue.ConsecutiveTextsBuilt'),
  listener_id
  = replace(listener_id, '.catalogue.NeighboursBuilt', '.catalogue.ConsecutiveTextsBuilt')
where event_type like '%.catalogue.NeighboursBuilt';
