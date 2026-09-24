# Changelog
All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

# 2.8.0 (Unreleased)
Mini-browser Search and ECL tabs, draggable concepts, and a dashboard sign-in modal.

### New Features
- Mini-browser ECL tab: run ECL expressions with the ECL builder and browse paged results
- Drag concepts from the taxonomy, search results and relationship targets into ECL fields as `code |term|`
- Dashboard admin sign-in modal, replacing the browser's native Basic Auth dialog so admin actions work in embedded and preview browsers

### Improvements
- Mini-browser Taxonomy and Search tabs, with search results showing definition status and FSN
- Mini-browser relationships shown as one compact card per role group, with target definition status
- Mini-browser concept details: children list grows with the panel; non-functional star removed
- Dashboard admin requests return a readable message instead of a JSON parse error when credentials are missing

### Fixes
- Inactive concepts in ValueSet `$expand`, aligned with Snowstorm:
  - The ECL wildcard `*`, and so the implicit ValueSet of all of SNOMED CT (`?fhir_vs`), now matches active concepts only. Previously inactive concepts were included, e.g. in text searches.
  - The `activeOnly` parameter is now supported (it was accepted but ignored).
  - `ValueSet.compose.inactive = false` now excludes inactive concepts.
  - When inactive concepts are included, they sort after active ones in filtered searches.
- Fix stale CodeSystem and content languages after an import: requests made while importing could cache the previous state until restart (e.g. a newly installed language missing from the mini-browser language selector). The mini-browser also reloads its edition and languages after an installation or SNOMED reset
- Fix URL and version wrapping in dashboard resource tables
- Fix ECL builder error when the expression has no history supplement

# 2.7.0 (September 2026)
Dashboard ValueSet editor, ECL builder, and ECL utility endpoints.

### New Features
- Basic SNOMED ValueSet editor in the dashboard
- ValueSet compose builder with multiple includes/excludes, expansion preview, and raw JSON link
- ECL utility endpoints for JSON model conversion (`/util/ecl-string-to-model`, `/util/ecl-model-to-string`)
- Dashboard ECL builder with SNOMED concept typeahead, example templates, and structured concept/attribute editing

### Improvements
- Spring Boot dev reload: classpath resources under `src/main/resources` picked up on `spring-boot:run` without a full rebuild

### Fixes
- Fix MLDS syndication feed download truncated by `HttpURLConnection`
- Latest CVE fixes

# 2.6.0 (September 2026)
ECL nested concept-set fix and ValueSet expand search ranking improvement.

### Improvements
- ValueSet `$expand` filtered search now ranks results by the shortest matching description term, rather than preferring matches on the display term. This more closely matches [Snowstorm](https://github.com/ihtsdo/snowstorm) search ranking.

### Fixes
- Fix ECL hierarchy operators (`>`, `>!`, `<`, `<!`, etc.) on nested concept sets. Operators now apply to the resolved expression rather than returning members of the set; for example, `> ( >> 362969004 )` returns ancestors only. Fixes #6

# 2.5.2 (July 2026)

### Fixes
- Fix ValueSet `$expand` filtered search where the first result changed depending on the `count` parameter, by using a fixed relevance sort window (default 250, configurable via `search.valueset-expand.relevance-sort-window`)

# 2.5.1 (July 2026)

### New Features
- `/version` build-info endpoint

### Fixes
- #24 MCP server failed to expose SNOMED CT tools to clients; thanks to @federicomrossi for the fix
- Latest CVE fixes (CVSS level 7 and above)

# 2.5.0 (June 2026)
Dashboard and mini-browser enhancements, Settings page, and CodeSystem $validate-code.

### New Features
- CodeSystem $validate-code operation
- Settings page with SNOMED reset and persistent syndication feed configuration

### Improvements
- Dashboard delete support, content preview, and resource upload UX
- Mini-browser language switcher, FSN/PT toggle, and language-aware preferred terms
- Syndication feed URL control and REST feed-config API

### Fixes
- Syndication import when the same release is available as Edition and Extension package


# 2.4.0 (May 2026)
Big feature release with new Snowstorm Dashboard, syndication and support for FHIR ConceptMaps.

### New Features
- Add Snowstorm Dashboard with MLDS syndication support
- FHIR ConceptMap CRUD operations.
- Add support for [ECL dotted attributes](https://docs.snomed.org/snomed-ct-specifications/snomed-ct-expression-constraint-language/examples/6.2-refinements#dotted-attributes)

### Improvements
- Redirect `/` to `/fhir` (remove separate root landing page)

### Fixes
- Latest CVE fixes (CVSS level 7 and above)


# 2.3.0 (July 2025)
New feature release.

### New Features
- CodeSystem $subsumes operation
  - I'm not sure how we missed this one!

### Fixes
- Latest CVE fixes


# 2.2.2 (June 2025)
Fix out of memory issue. Increase max heap to 2g.

### Improvements
- Update Docker documentation to include persistent volume for Lucene data 

### Fixes
- Increase max heap size to 2g. Min is still set to 1g


# 2.2.1 (May 2025)
Bug fix release.

### Fixes
- #15 Fix selection of ValueSet version for expand
- #3 record membership of all refset types for query
- Latest CVE fixes


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
