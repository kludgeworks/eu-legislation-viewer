-- SPDX-License-Identifier: MIT
-- Source mappings and LRU-bounded WebP renditions for Formex TIFF images.

create table image_source (
  text_id bigint not null,
  file_name text not null,
  manifestation_uri text not null,
  item_uri text not null,
  media_type text not null,
  rdf_sha1 text,
  source_sha256 bytea,
  gone_reason text,
  primary key (text_id, file_name)
);

comment on table image_source is
'Maps each image file of a stored text to its Cellar item, known digest, and permanent failure reason.';
comment on column image_source.text_id is 'The identity of the stored text (TextId).';
comment on column image_source.file_name is 'The Formex FILEREF name, for example L_2009330EN.01008102.tif.';
comment on column image_source.rdf_sha1 is 'The SHA-1 digest from Cellar metadata, if present.';
comment on column image_source.source_sha256 is 'The SHA-256 digest of the downloaded TIFF bytes, saved on the first read.';
comment on column image_source.gone_reason is 'Why the image cannot be served permanently, so Cellar is not asked again.';

create table image_rendition (
  source_sha256 bytea not null,
  variant text not null check (variant in ('PREVIEW', 'ORIGINAL')),
  recipe text not null,
  width integer not null,
  height integer not null,
  size_bytes integer not null,
  used_at timestamptz not null,
  webp bytea not null,
  primary key (source_sha256, variant, recipe)
);

-- already compressed WebP, so Postgres neither compresses it nor moves it again
alter table image_rendition alter column webp set storage external;

create index image_rendition_used_at on image_rendition (used_at asc);

comment on table image_rendition is
'Stored WebP images converted from Formex TIFF files, kept up to a size limit (used_at).';
comment on column image_rendition.variant is 'PREVIEW (at most 1600 px on the longest side) or ORIGINAL (full size).';
comment on column image_rendition.recipe is 'Version digest of the scaling and encoding code.';
comment on column image_rendition.used_at is 'When the stored image was last served.';
comment on index image_rendition_used_at is 'Orders stored images by least recent use for cache eviction.';
