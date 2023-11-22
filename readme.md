# Snowstorm Lite FHIR Terminology Server _(Beta)_
A fast FHIR Terminology Server for SNOMED CT with a small memory footprint.

- [Use Case](#use-case)
- [Features](#features)
- [Limitations](#limitations)
- [Technical Details](#technical-details)
- [Quick Start](#quick-start)
- [Roadmap](#roadmap)

## Use Case
- Scaling architecture
- Supporting user data input with SNOMED CT driven search where responsiveness is critical
- Using SNOMED CT offline:
  - Locations with poor connectivity and limited machine memory
  - Trusted research environments that are not able to make external API requests
- _Not_ suitable as a national terminology server

## Features
- Host a single SNOMED CT Edition with incredible speed
- SNOMED query support using a subset of [ECL](http://snomed.org/ecl)
- Perfect for search
  - Most relevant results first
  - Supports terminology binding
- FHIR Terminology Operations
  - CodeSystem lookup
    - Including parents, children, designations, normal form
  - ValueSet expand using [SNOMED CT Implicit Value Sets](http://hl7.org/fhir/R4/snomedct.html#implicit)
    - SNOMED CT `isa` and `ecl` filters are supported
  - ConceptMap translate using [SNOMED CT Implicit Maps](http://hl7.org/fhir/R4/snomedct.html#implicit-cm)

## Limitations
- Only supports the FHIR API
- Only ValueSets support create/update/delete
- Only supports one snapshot of the SNOMED CT International Edition at this time
  - Support for other SNOMED CT editions and include multiple language search is planned

## Technical Details
- Only supports one SNOMED CT CodeSystem at a time
- Minimal memory footprint is perfect for autoscaling
  - After creating the index the app can run with just 500mb memory
- Self-contained application using Apache Lucene™
- Uses Spring Boot and HAPI FHIR Frameworks
- Requires JDK 17

## Quick Start
Choose an admin password and replace `yourAdminPassword` values in the following commands.

### Option 1: Using a SNOMED Syndication Service
Run Snowstorm Lite in your local Docker:
```
docker pull snomedinternational/snowstorm-lite:latest
docker run -i -t -p 8080:8080 snomedinternational/snowstorm-lite \
  --admin.password=yourAdminPassword \
  --syndicate --version-uri=http://snomed.info/sct/900000000000207008
```
Set `version-uri` to the URI of the SNOMED Edition to be loaded. See [SNOMED Edition URI Examples](docs/snomed-edition-uri-examples.md).

The console will ask for the syndication service username and password before downloading the relevant packages and building the index. By default the SNOMED International feed is used, this uses the same credentials as MLDS. The feel URL can be changed using the `syndication.url` configuration option.

Then Snowstorm Lite will be ready for use! The FHIR interface is here: http://localhost:8085/fhir.

### Option 2: Using a SNOMED Archive File
Run Snowstorm Lite in your local Docker:
```
docker pull snomedinternational/snowstorm-lite:latest
docker run -p 8080:8080 snomedinternational/snowstorm-lite \
  --admin.password=yourAdminPassword
```

Upload a SNOMED CT package (takes about 2 minutes):
```
curl -u admin:yourAdminPassword \
  --form file=@SnomedCT_InternationalRF2_PRODUCTION_20230131T120000Z.zip \
  --form version-uri="http://snomed.info/sct/900000000000207008/version/20230131" \
  http://localhost:8080/fhir-admin/load-package
```
Then Snowstorm Lite will be ready for use! The FHIR interface is here: http://localhost:8085/fhir.

It is possible to [import extension or derivative packages](docs/importing-extension-or-derivative-packages.md).

_It is also possible to [deploy as a Java application, without Docker](docs/running-with-java.md)._

## Roadmap
- ECL History Supplement feature (Q4 2023)
- Support for non-english SNOMED CT extensions (Q1 2024)

Full ECL support is not planned. Snowstorm Lite supports the most often used ECL features without the full complexity and memory demands of the complete ECL specification.
