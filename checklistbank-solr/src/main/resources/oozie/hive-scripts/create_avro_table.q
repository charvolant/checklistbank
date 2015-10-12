DROP TABLE IF EXISTS ${avroTable};
CREATE TABLE ${avroTable}
ROW FORMAT SERDE
'org.apache.hadoop.hive.serde2.avro.AvroSerDe'
STORED AS INPUTFORMAT
'org.apache.hadoop.hive.ql.io.avro.AvroContainerInputFormat'
OUTPUTFORMAT
'org.apache.hadoop.hive.ql.io.avro.AvroContainerOutputFormat'
TBLPROPERTIES (
'avro.schema.literal'='{
  "namespace":"org.gbif.checklistbank.index.model",
  "name":"NameUsageAvro",
  "type":"record",
  "fields":
    [{"name":"key","type":["int","null"]},
        {"name":"nubKey","type":["int", "null"]},
        {"name":"datasetKey","type":["string","null"]},
        {"name":"parentKey","type":["int", "null"]},
        {"name":"parent","type":["string", "null"]},
        {"name":"acceptedKey","type":["int", "null"]},
        {"name":"accepted","type":["string", "null"]},
        {"name":"basionymKey","type":["int", "null"]},
        {"name":"basionym","type":["string", "null"]},
        {"name":"scientificName","type":["string", "null"]},
        {"name":"canonicalName","type":["string", "null"]},
        {"name":"nameType","type":["int", "null"]},
        {"name":"authorship","type":["string", "null"]},
        {"name":"taxonomicStatusKey","type":["int", "null"]},
        {"name":"nomenclaturalStatusKey","type":[{"type":"array", "items":"int"}, "null"]},
        {"name":"threatStatusKey","type":[{"type":"array", "items":"int"}, "null"]},
        {"name":"rankKey","type":["int", "null"]},
        {"name":"habitatKey","type":[{"type":"array", "items":"int"}, "null"]},
        {"name":"publishedIn","type":["string", "null"]},
        {"name":"accordingTo","type":["string", "null"]},
        {"name":"kingdomKey","type":["int", "null"]},
        {"name":"kingdom","type":["string", "null"]},
        {"name":"phylumKey","type":["int", "null"]},
        {"name":"phylum","type":["string", "null"]},
        {"name":"classKey","type":["int", "null"]},
        {"name":"clazz","type":["string", "null"]},
        {"name":"orderKey","type":["int", "null"]},
        {"name":"order","type":["string", "null"]},
        {"name":"familyKey","type":["int", "null"]},
        {"name":"family","type":["string", "null"]},
        {"name":"genusKey","type":["int", "null"]},
        {"name":"genus","type":["string", "null"]},
        {"name":"subgenusKey","type":["int", "null"]},
        {"name":"subgenus","type":["string", "null"]},
        {"name":"speciesKey","type":["int", "null"]},
        {"name":"species","type":["string", "null"]},
        {"name":"numDescendants","type":["int", "null"]},
        {"name":"sourceId","type":["string", "null"]},
        {"name":"isSynonym","type":"boolean"},
        {"name":"extinct","type":"boolean"},
        {"name":"description","type":[{"type":"array", "items":"string"}, "null"]},
        {"name":"vernacularName","type":[{"type":"array", "items":"string"}, "null"]},
        {"name":"vernacularLang","type":[{"type":"array", "items":"string"}, "null"]},
        {"name":"vernacularNameLang","type":[{"type":"array", "items":"string"}, "null"]},
        {"name":"higherTaxonKey","type":[{"type":"array", "items":"int"}, "null"]},
        {"name":"issues","type":[{"type":"array", "items":"int"}, "null"]}]
}');
