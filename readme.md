# Snowstorm Lite FHIR Terminology Server _(Beta)_
A fast FHIR Terminology Server for SNOMED CT with a small memory footprint.

## Features
- Host a single SNOMED CT Edition with incredible speed
- SNOMED query support using a subset of [ECL](http://snomed.org/ecl)
- Perfect for search
  - Most relevant results first
  - Supports terminology binding
- Read only at this time
- FHIR Terminology Operations
  - List CodeSystem
  - CodeSystem lookup
  - ValueSet expand using [SNOMED CT Implicit Value Sets](https://terminology.hl7.org/SNOMEDCT.html#snomed-ct-implicit-value-sets)
    - `isa` and `ecl` queries supported

## Technical Details
- Minimal memory footprint is perfect for autoscaling
  - Container runs well with just 500mb memory (1gb recommended)
- Self-contained application using Apache Lucene™
- Uses Spring Boot and HAPI FHIR Frameworks
- Requires JDK 17

## Guide
### Setup: Building the SNOMED CT index
Building the index takes about 2 minutes to run. 

You must have Java 17 installed and accessible on the command line. Use the `--load` parameter with the path to a SNOMED CT Edition RF2 archive 
and the `--version-uri` parameter with the URI of that SNOMED CT Edition version.

For example:
```
java -jar snowstorm-lite.jar \
  --load=my-release-files/SnomedCT_InternationalRF2_xxxxx.zip \
  --version-uri=http://snomed.info/sct/900000000000207008/version/20230731
```

This example has the correct `version-uri` for the July 2023 International Edition with module `900000000000207008` and version `20230731`. 
[See "URIs for Editions and Versions" in the SNOMED CT URI Standard](http://snomed.org/uri).

The index will be created in the `lucene-index` directory then the application will shutdown.

_Note: Building the index for the International Edition can happen in resource constrained environments, limit memory use to 1gb using java parameter `-Xmx1g`._

### Running the FHIR Terminology Server
Once the index is loaded start the FHIR server with 1gb of memory.
```
java -Xmx1g -jar snowstorm-lite.jar
```
