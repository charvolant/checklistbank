DROP TABLE IF EXISTS ${avroTable};
CREATE EXTERNAL TABLE ${avroTable}
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
      {"name":"nomenclaturalStatusKey","type":["null",{"type":"array", "items":"int"}],"default":null},
      {"name":"threatStatusKey","type":["null",{"type":"array", "items":"int"}],"default":null},
      {"name":"rankKey","type":["int", "null"]},
      {"name":"habitatKey","type":["null",{"type":"array", "items":"int"}],"default":null},
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
      {"name":"isSynonym","type":["boolean","null"]},
      {"name":"extinct","type":["boolean","null"]},
      {"name":"description","type":["null",{"type":"array", "items":"string"}],"default":null},
      {"name":"vernacularName","type":["null",{"type":"array", "items":"string"}],"default":null},
      {"name":"vernacularLang","type":["null",{"type":"array", "items":"string"}],"default":null},
      {"name":"vernacularNameLang","type":["null",{"type":"array", "items":"string"}],"default":null},
      {"name":"higherTaxonKey","type":["null",{"type":"array", "items":"int"}],"default":null},
      {"name":"issues","type":["null",{"type":"array", "items":"int"}],"default":null}]
}')
LOCATION '/checklistbank/solr/dev/';