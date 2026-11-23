-- SPDX-License-Identifier: MIT
-- The catalogue's event NeighboursBuilt is now ConsecutiveTextsBuilt: "neighbour" is the
-- comparison's word for an occurrence near a difference. Its fields did not change, so a
-- publication that is not completed yet keeps its event, under the new names of the class and of
-- its listener. AmendmentsChanged, now AmendmentsUpdated, has no listener, so it has no
-- publication.
update event_publication
set
  event_type
  = replace(event_type, '.catalogue.NeighboursBuilt', '.catalogue.ConsecutiveTextsBuilt'),
  listener_id
  = replace(listener_id, '.catalogue.NeighboursBuilt', '.catalogue.ConsecutiveTextsBuilt')
where event_type like '%.catalogue.NeighboursBuilt';
