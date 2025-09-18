# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Snowstorm Lite is a fast FHIR Terminology Server for SNOMED CT with a small memory footprint. It's a Java Spring Boot application that uses Apache Lucene for indexing and search, with HAPI FHIR for the FHIR API implementation.

## Build and Development Commands

### Maven Commands
- **Build**: `mvn clean package` - Builds the executable JAR
- **Test**: `mvn test` - Runs all unit tests
- **Docker Build**: `mvn jib:dockerBuild` - Builds Docker image locally
- **Security Check**: `mvn dependency-check:check` - Runs OWASP dependency vulnerability scan

### Running the Application

#### Development Mode
```bash
# Run from JAR (requires Java 17)
java -jar target/snowstorm-lite-*.jar

# With memory constraints for development
java -Xmx1g -jar target/snowstorm-lite-*.jar

# With admin password
java -jar target/snowstorm-lite-*.jar --admin.password=yourPassword
```

#### Loading SNOMED CT Data
```bash
# Load from file
java -jar target/snowstorm-lite-*.jar \
  --load=path/to/SnomedCT_RF2_file.zip \
  --version-uri=http://snomed.info/sct/900000000000207008/version/20240101

# Load via syndication
java -jar target/snowstorm-lite-*.jar \
  --syndicate \
  --version-uri=http://snomed.info/sct/900000000000207008/version/20240101
```

### Docker Commands
```bash
# Run with Docker
docker run -p 8080:8080 --name=snowstorm-lite \
  -v snowstorm-lite-volume:/app/lucene-index \
  snomedinternational/snowstorm-lite \
  --index.path=lucene-index/data \
  --admin.password=yourPassword
```

## Architecture Overview

### Core Components

**Main Application** (`SnowstormLiteApplication.java`)
- Spring Boot entry point that delegates to `AppSetupService` for initialization

**Service Layer** (`src/main/java/org/snomed/snowstormlite/service/`)
- `AppSetupService` - Handles application initialization, data loading via syndication or file upload
- `CodeSystemService` - Manages SNOMED CT CodeSystem operations
- `ValueSetService` - Handles ValueSet operations and ECL evaluation
- `CodeSystemRepository` - Lucene-based repository for SNOMED concepts and relationships
- `IndexIOProvider` - Manages Lucene index read/write operations

**FHIR Layer** (`src/main/java/org/snomed/snowstormlite/fhir/`)
- `CodeSystemProvider` - FHIR CodeSystem resource operations (lookup, search)
- `ValueSetProvider` - FHIR ValueSet resource operations (expand, validate)
- `ConceptMapProvider` - FHIR ConceptMap operations for SNOMED implicit maps
- `AdminController` - Admin endpoints for package loading

**Data Import** (`src/main/java/org/snomed/snowstormlite/snomedimport/`)
- RF2 file processing and Lucene index creation
- Syndication client for automated SNOMED package downloads

**Domain Models** (`src/main/java/org/snomed/snowstormlite/domain/`)
- `Concept`, `Description`, `Relationship` - Core SNOMED entities
- Lucene document mapping and search result handling

### Key Technologies
- **Spring Boot 3.x** with Spring Security for authentication
- **HAPI FHIR** for FHIR R4 resource handling and REST endpoints
- **Apache Lucene** for full-text search and terminology indexing
- **SNOMED ECL Parser** for Expression Constraint Language support

### Configuration

**Application Properties** (`src/main/resources/application.properties`)
- Admin credentials (default: admin/snowstormLITE)
- Lucene index path (default: lucene-index)
- FHIR ConceptMap configurations for SNOMED implicit mappings
- Language dialect configurations for international support
- Import batch size and memory optimization settings

**Security**
- Basic authentication with configurable admin credentials
- FHIR endpoints are read-only except for admin operations
- Admin operations: `/fhir-admin/load-package` for data upload

### Memory Optimization
- Designed to run with minimal memory (500MB after index creation)
- Import batch size limited to 40,000 concepts for 1GB memory environments
- Lucene index stored separately from application memory

### Testing
- Unit tests in `src/test/java/` 
- Test configuration uses separate Lucene index (`test-lucene-index`)
- Tests cover ECL parsing, ValueSet expansion, CodeSystem operations

## Development Notes

### ECL Support
- Supports subset of Expression Constraint Language (ECL) for SNOMED queries
- Located in `src/main/java/org/snomed/snowstormlite/service/ecl/`
- Not full ECL specification - optimized for performance and memory efficiency

### FHIR Implicit Maps
- Supports SNOMED CT implicit ConceptMaps as defined in FHIR R4 specification
- Configured via application properties with refset IDs and equivalence mappings
- Examples: SNOMED to ICD-10, replacement relationships, alternatives

### Language Support
- Configurable language dialect support via language reference sets
- Character folding configuration for international search
- Default English dialect: US English (900000000000509007)

### Data Loading Patterns
1. **Syndication**: Automated download from SNOMED International MLDS
2. **File Upload**: Manual RF2 archive upload via REST API or command line
3. **Extension Support**: Can load Extension packages on top of International Edition