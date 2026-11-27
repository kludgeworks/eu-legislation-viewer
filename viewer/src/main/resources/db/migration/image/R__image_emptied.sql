-- SPDX-License-Identifier: MIT
-- Emptied for: a converted text saves the locations of its image files (2026-11-27).
-- Flyway runs this again each time its text changes. To empty the source mappings again, change
-- the line above to say why. The cached WebP files stay, because their key is the hash of the
-- source file.
do $$
begin
  if to_regclass('image_source') is not null then
    execute 'truncate table image_source';
  end if;
end $$;
