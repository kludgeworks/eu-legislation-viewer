# Test SPARQL Queries

These queries can be executed directly at: https://publications.europa.eu/webapi/rdf/sparql

## 1. Test Basic Work Query

```sparql
PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

SELECT DISTINCT ?work ?title ?celex
WHERE {
  ?work cdm:resource_legal_id_celex "32015R1998"^^xsd:string ;
        cdm:work_title ?title .
  BIND("32015R1998" AS ?celex)
}
LIMIT 1
```

## 2. Test Full Work Properties

```sparql
PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

SELECT DISTINCT 
  ?work
  ?creationDate ?lastModificationDate 
  ?title ?version ?dateCreation ?dateDocument ?embargo ?createdBy ?idDocument
  ?sectorId ?eli ?inForce ?numberCorrigendum
WHERE {
  ?work cdm:resource_legal_id_celex "32015R1998"^^xsd:string .
  
  # CMR metadata
  OPTIONAL { ?work cmr:creationDate ?creationDate }
  OPTIONAL { ?work cmr:lastModificationDate ?lastModificationDate }
  
  # Work properties
  ?work cdm:work_title ?title .
  OPTIONAL { ?work cdm:work_version ?version }
  OPTIONAL { ?work cdm:work_date_creation ?dateCreation }
  OPTIONAL { ?work cdm:work_date_document ?dateDocument }
  OPTIONAL { ?work cdm:work_embargo ?embargo }
  OPTIONAL { ?work cdm:work_created_by_agent ?createdBy }
  OPTIONAL { ?work cdm:work_id_document ?idDocument }
  
  # Legal Resource properties
  OPTIONAL { ?work cdm:resource_legal_id_sector ?sectorId }
  OPTIONAL { ?work cdm:resource_legal_eli ?eli }
  OPTIONAL { ?work cdm:resource_legal_in_force ?inForce }
  OPTIONAL { ?work cdm:resource_legal_number_corrigendum ?numberCorrigendum }
}
```

## 3. Test Expression Query

```sparql
PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>

SELECT DISTINCT ?expression ?language ?title
WHERE {
  ?work cdm:resource_legal_id_celex "32015R1998"^^xsd:string .
  ?expression cdm:expression_belongs_to_work ?work ;
              cmr:lang ?language .
  OPTIONAL { ?expression cdm:expression_title ?title }
}
ORDER BY ?language
```

## 4. Find Recent Works

```sparql
PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

SELECT DISTINCT ?work ?celex ?title ?dateDocument
WHERE {
  ?work a cdm:work ;
        cdm:resource_legal_id_celex ?celex ;
        cdm:work_title ?title ;
        cdm:work_date_document ?dateDocument .
  
  FILTER(?dateDocument >= "2024-01-01"^^xsd:date)
}
ORDER BY DESC(?dateDocument)
LIMIT 10
```