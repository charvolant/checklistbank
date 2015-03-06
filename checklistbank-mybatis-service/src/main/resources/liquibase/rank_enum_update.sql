-- update rank type according to extended enum
-- https://github.com/gbif/gbif-api/commit/980033118d1466aee0a7e581f7a7769d873fa8d9
DROP VIEW kname;
ALTER TABLE name_usage ALTER COLUMN rank type text;
ALTER TABLE typification ALTER COLUMN rank type text;
DROP TYPE rank;
CREATE TYPE rank AS ENUM ('DOMAIN', 'KINGDOM', 'SUBKINGDOM', 'SUPERPHYLUM', 'PHYLUM', 'SUBPHYLUM', 'SUPERCLASS', 'CLASS', 'SUBCLASS', 'SUPERORDER', 'ORDER', 'SUBORDER', 'INFRAORDER', 'SUPERFAMILY', 'FAMILY', 'SUBFAMILY', 'TRIBE', 'SUBTRIBE', 'SUPRAGENERIC_NAME', 'GENUS', 'SUBGENUS', 'SECTION', 'SUBSECTION', 'SERIES', 'SUBSERIES', 'INFRAGENERIC_NAME', 'SPECIES', 'INFRASPECIFIC_NAME', 'SUBSPECIES', 'INFRASUBSPECIFIC_NAME', 'VARIETY', 'SUBVARIETY', 'FORM', 'SUBFORM',  'PATHOVAR','BIOVAR','CHEMOVAR','MORPHOVAR','PHAGOVAR','SEROVAR','CHEMOFORM','FORMA_SPECIALIS','CULTIVAR_GROUP','CULTIVAR', 'STRAIN', 'INFORMAL', 'UNRANKED');
ALTER TABLE name_usage ALTER COLUMN rank type rank USING (rank::rank);
ALTER TABLE typification ALTER COLUMN rank type rank USING (rank::rank);
