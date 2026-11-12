-- SPDX-License-Identifier: MIT
-- Cellar's code M in fd_375 types an amendment: Amendment.Role.AMENDMENT.
alter table act_amendment drop constraint act_amendment_role_check;
alter table act_amendment add constraint act_amendment_kind_check check (
  kind in (
    'REPLACEMENT',
    'ADDITION',
    'DELETION',
    'REPEAL',
    'AMENDMENT',
    'EXTENSION',
    'SUSPENSION',
    'DEFERRAL',
    'OTHER'
  )
);
