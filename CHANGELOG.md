# Changelog
All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


# 2.2.0 (Jan 2025)
New partial-hierarchy endpoint and CVE fixes.

### New Features
- New endpoint for loading part of the SNOMED hierarchy `/partial-hierarchy`, useful for some types of analytics.

### Fixes
- CVE-2024-51132(9.3)
- CVE-2024-55887(7.7)


## 2.1.1 (Nov 2024)
Minor security fix release.

### Fixes
- CVE-2024-51132


## 2.1.0 (Oct 2024)
Feature release adding TerminologyCapabilities and ValueSet validate-code operation.

### New Features
- Added ValueSet $validate-code operation
- Added TerminologyCapabilities (`/metadata?mode=terminology`)

### Improvements
- New capabilities added to Postman collection
- Add container name in Docker examples

### Fixes
- Fixed CapabilityStatement software version (`/metadata`)
- Fixed all new CVEs


## 2.0.0 Beta (Oct 2024)

### Breaking
- Upgrade Lucene to version 9.9.x
  - SNOMED CT must be imported again to create index in new format

### Improvements
- Updates for latest MLDS Syndication Feed format
- Check Lucene version compatibility during startup, fail with informative message
- Upgrade Spring Boot
- Upgrade many libraries 

### Fixes
- All reported CVEs fixed


## 1.4.0 Beta (May 2024)

### New Features
- ECL History Supplements
  - Enables including inactive concepts for longitudinal reporting. For example include active and inactive Asthma concepts like this: `<<  195967001 |Asthma|  {{ +HISTORY }}`
  - See: [ECL Guide, History Supplements](https://confluence.ihtsdotools.org/display/DOCECL/6.11+History+Supplements)

### Fixes
- Fixed selection of PT when a specific language but no specific dialect is requested. #7

## 1.3.0 Beta (April 2024)

### New Features
- Multiple language and dialect support for search and display
  - Includes configurable language specific character folding
  - Works with wildcard search

### Improvements
- Much better search ranking
  - First 100 results are sorted by shortest matching description term, like the full Snowstorm server
  - Works in any language and with wildcard search

### Fixes
- SNOMED to ICD-10 map equivalence corrected to 'relatedto', was 'unmatched'
- Docker java heap set to 1g minimum and maximum to prevent out of memory error during import
- CVE fixes, level 7 and above


## 1.2.0 Beta (Jan 2024)

### Improvements
- Reduce default ValueSet $expand results size to 100 for better performance
- Ability to load extension packages over REST API
- Parameterise import batch size
- Add Google App Engine config and warmup handler

### Fixes
- More robust parsing of syndication feed, to allow for inconsistent package size number formats
- CVE security fixes for level 7 and above
- Fix ValueSet $expand when multiple includes
- Fix $validate error: CodeSystem id format
- Remove compose section from ValueSet listing

## 1.1.1 Beta (November 2023)
Beta Release with syndication service integration and ability to load Edition packages via REST API.  
