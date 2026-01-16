# Snowstorm Lite FHIR Terminology Server
A fast FHIR Terminology Server for SNOMED CT with a small memory footprint.

- [Use Case](#use-case)
- [Features](#features)
- [Limitations](#limitations)
- [Technical Details](#technical-details)
- [Quick Start](#quick-start)
- [MCP (Model Context Protocol) Support](#mcp-model-context-protocol-support)
- [Try the API with Postman](#postman)
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
  - Multiple language support with configurable character folding
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
If you have access to the SNOMED International MLDS service then Snowstorm Lite can download a release automatically from there.

Run Snowstorm Lite in your local Docker:
```
docker pull snomedinternational/snowstorm-lite:latest
docker run -i -t -p 8080:8080 --name=snowstorm-lite \
  -v snowstorm-lite-volume:/app/lucene-index \
  snomedinternational/snowstorm-lite \
  --index.path=lucene-index/data \
  --admin.password=yourAdminPassword \
  --syndicate --version-uri=http://snomed.info/sct/900000000000207008
```
Set `version-uri` to the URI of the SNOMED Edition to be loaded. See [SNOMED Edition URI Examples](docs/snomed-edition-uri-examples.md).

The console will ask for the syndication service username and password before downloading the relevant packages and building the index. By default the SNOMED International MLDS feed is used, this uses the same credentials as MLDS. The feed URL can be changed using the `syndication.url` configuration option.

Then Snowstorm Lite will be ready for use! The FHIR interface is here: http://localhost:8080/fhir.

### Option 2: Using a SNOMED Archive File
If you have access to a SNOMED CT Edition release archive this can be imported.

Run Snowstorm Lite in your local Docker:
```
docker pull snomedinternational/snowstorm-lite:latest
docker run -p 8080:8080 --name=snowstorm-lite \
  -v snowstorm-lite-volume:/app/lucene-index \
  snomedinternational/snowstorm-lite \
  --index.path=lucene-index/data \
  --admin.password=yourAdminPassword
```

Upload a SNOMED CT package:
```
curl -u admin:yourAdminPassword \
  --form file=@SnomedCT_InternationalRF2_PRODUCTION_20250101T120000Z.zip \
  --form version-uri="http://snomed.info/sct/900000000000207008/version/20250101" \
  http://localhost:8080/fhir-admin/load-package
```
---
Importing a SNOMED CT release takes about 5 minutes.

When the import is complete Snowstorm Lite will be ready for use! The FHIR interface is here: http://localhost:8080/fhir.

It is possible to [import extension or derivative packages](docs/importing-extension-or-derivative-packages.md).

_It is also possible to [deploy as a Java application, without Docker](docs/running-with-java.md)._

## MCP (Model Context Protocol) Support

Snowstorm Lite now supports the Model Context Protocol, allowing AI assistants like Claude to interact with SNOMED CT terminology data through standardized tools.

### Available MCP Tools

1. **lookup_snomed_code** - Retrieve detailed information about a SNOMED CT concept
   - Parameters: `code` (required), `language` (optional, default: "en")
   - Returns: Complete concept details including display name, FSN, descriptions, parents, children, relationships

2. **search_snomed_codes** - Search concepts by code prefix or description text
   - Parameters: `query` (required), `activeOnly` (optional, default: true), `language` (optional), `limit` (optional, max: 100)
   - Returns: List of matching concepts with display names and active status

3. **validate_snomed_code** - Verify if a code exists and check its active status
   - Parameters: `code` (required)
   - Returns: Validation result with existence and active status

4. **check_snomed_subsumption** - Check hierarchical relationships between concepts
   - Parameters: `codeA` (required), `codeB` (required)
   - Returns: Subsumption outcome ("subsumes", "subsumed-by", "equivalent", "not-subsumed")

### Configuration

The MCP server is enabled by default and accessible via SSE (Server-Sent Events) transport. Configure in `application.properties`:

```properties
spring.ai.mcp.server.enabled=true
spring.ai.mcp.server.name=Snowstorm Lite SNOMED CT Server
```

### Connecting an MCP Client

1. Start Snowstorm Lite (see Quick Start above)
2. Configure your MCP client to connect to: `http://localhost:8080/sse`
   - For HTTPS (via nginx): `https://localhost/sse`
3. The client will discover available tools automatically

**Note**: The MCP server uses two endpoints:
- `/sse` - For receiving server-sent events (this is the connection URL for MCP clients)
- `/mcp/message` - For sending messages to the server (used internally by the client)

### Example Usage (via Claude)

```
User: Look up SNOMED CT code 73211009
Claude: [Uses lookup_snomed_code tool]
Result: 73211009 - Diabetes mellitus (disorder)
        Active concept with FSN, synonyms, parents, and relationships...

User: Search for heart attack concepts
Claude: [Uses search_snomed_codes tool with query="heart attack"]
Result: Found 15 matching concepts including:
        - 22298006 - Myocardial infarction (disorder)
        - 57054005 - Acute myocardial infarction (disorder)
        ...
```

## Postman
This Postman collection allows you to try the various API functions of the Snowstorm Lite server. It's similar to a Swagger UI.  
You will need a local Snowstorm Lite instance running.  
  
[![Run in Postman](https://run.pstmn.io/button.svg)](https://app.getpostman.com/run-collection/26915017-9ebeee28-e786-4722-a768-730b26ba4da7?action=collection%2Ffork&source=rip_markdown&collection-url=entityId%3D26915017-9ebeee28-e786-4722-a768-730b26ba4da7%26entityType%3Dcollection%26workspaceId%3D283ac96f-72e6-436f-9f4b-c67af5d038a8#?env%5BLocalhost%20Port%208080%5D=W3sia2V5IjoidXJsIiwidmFsdWUiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJlbmFibGVkIjp0cnVlLCJ0eXBlIjoiZGVmYXVsdCIsInNlc3Npb25WYWx1ZSI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MCIsInNlc3Npb25JbmRleCI6MH1d)

## Roadmap
- Nothing currently planned.

Full ECL support is not planned. Snowstorm Lite supports the most often used ECL features without the full complexity and memory demands of the complete ECL specification.
