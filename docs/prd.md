# Product Requirements Document (PRD)
## Snowstorm Lite FHIR Terminology Server

### Document Information
- **Version**: 1.0
- **Date**: 2025-01-23
- **Product**: Snowstorm Lite v2.3.0
- **Status**: Final

---

## 1. Executive Summary

Snowstorm Lite is a fast, lightweight FHIR Terminology Server specifically designed for SNOMED CT with minimal memory footprint. It provides essential SNOMED CT terminology services through FHIR R4 APIs, optimized for resource-constrained environments, offline deployments, and scalable architectures requiring rapid terminology search and validation.

### 1.1 Key Value Propositions
- **Minimal Memory Footprint**: Operates with just 500MB memory after index creation
- **Rapid Search Performance**: Optimized Lucene-based indexing for sub-second responses
- **FHIR R4 Compliance**: Full compatibility with FHIR terminology operations
- **Multilingual Support**: Configurable language dialects and character folding
- **Offline Capability**: Self-contained deployment without external dependencies

---

## 2. Product Overview

### 2.1 Product Vision
To provide a high-performance, lightweight SNOMED CT terminology server that enables healthcare applications to integrate SNOMED CT terminology services with minimal infrastructure requirements while maintaining full FHIR compliance.

### 2.2 Target Users
- **Healthcare Application Developers**: Integrating SNOMED CT terminology into clinical applications
- **Healthcare IT Departments**: Deploying terminology services in resource-constrained environments
- **Research Organizations**: Supporting offline terminology research in trusted environments
- **Cloud Architects**: Implementing auto-scaling terminology services

### 2.3 Use Cases

#### Primary Use Cases
1. **Clinical Decision Support**: Real-time terminology validation and lookup in clinical applications
2. **Data Entry Assistance**: SNOMED CT concept search with auto-completion
3. **Offline Terminology Access**: Supporting healthcare applications in areas with limited connectivity
4. **Microservices Architecture**: Lightweight terminology service in containerized environments
5. **Research Environments**: Terminology support in air-gapped research systems

#### Secondary Use Cases
1. **Development and Testing**: Local SNOMED CT terminology server for application development
2. **Edge Computing**: Terminology services at the point of care
3. **Multi-tenant SaaS**: Shared terminology service across multiple applications

---

## 3. Functional Requirements

### 3.1 FHIR Terminology Operations

#### 3.1.1 CodeSystem Operations
**CR-001: CodeSystem Resource Management**
- **Description**: Support FHIR CodeSystem resource operations for SNOMED CT
- **Priority**: P0 (Critical)
- **Endpoints**:
  - `GET /fhir/CodeSystem` - Search CodeSystems
  - `GET /fhir/CodeSystem/{id}` - Read specific CodeSystem
  - `POST /fhir/CodeSystem/$lookup` - Lookup concept details
  - `POST /fhir/CodeSystem/$subsumes` - Test concept subsumption

**CR-002: Concept Lookup Operation**
- **Description**: Retrieve detailed information about SNOMED CT concepts
- **Priority**: P0 (Critical)
- **Parameters**:
  - `code`: SNOMED CT concept ID
  - `system`: SNOMED CT URI (http://snomed.info/sct)
  - `version`: CodeSystem version URI
  - `displayLanguage`: Language for display terms
  - `property`: Requested concept properties
- **Response**: Concept details including parents, children, designations, normal form

**CR-003: Subsumption Testing**
- **Description**: Determine hierarchical relationships between SNOMED CT concepts
- **Priority**: P1 (High)
- **Parameters**:
  - `codeA`, `codeB`: Concepts to compare
  - `system`: SNOMED CT URI
  - `version`: CodeSystem version URI
- **Response**: Subsumption relationship (subsumes, subsumed-by, equivalent, not-subsumed)

#### 3.1.2 ValueSet Operations
**CR-004: ValueSet Resource Management**
- **Description**: Support FHIR ValueSet resource CRUD operations
- **Priority**: P0 (Critical)
- **Endpoints**:
  - `GET /fhir/ValueSet` - Search ValueSets
  - `GET /fhir/ValueSet/{id}` - Read specific ValueSet
  - `POST /fhir/ValueSet` - Create ValueSet
  - `PUT /fhir/ValueSet/{id}` - Update ValueSet
  - `DELETE /fhir/ValueSet/{id}` - Delete ValueSet

**CR-005: ValueSet Expansion Operation**
- **Description**: Expand SNOMED CT ValueSets using ECL expressions or explicit includes
- **Priority**: P0 (Critical)
- **Parameters**:
  - `url`: ValueSet canonical URL
  - `filter`: Term filter for search
  - `offset`, `count`: Pagination parameters
  - `includeDesignations`: Include concept descriptions
  - `activeOnly`: Filter active concepts only
  - `displayLanguage`: Language for display terms
- **Response**: Expanded ValueSet with matching concepts

**CR-006: ValueSet Validation Operation**
- **Description**: Validate codes against SNOMED CT ValueSets
- **Priority**: P0 (Critical)
- **Parameters**:
  - `url`: ValueSet canonical URL
  - `code`: Code to validate
  - `system`: SNOMED CT URI
  - `display`: Display term to validate
- **Response**: Validation result with outcome and display term

#### 3.1.3 ConceptMap Operations
**CR-007: ConceptMap Translation**
- **Description**: Support SNOMED CT implicit ConceptMaps for terminology mapping
- **Priority**: P1 (High)
- **Supported Maps**:
  - SNOMED CT association reference sets (SAME AS, REPLACED BY, etc.)
  - SNOMED CT to ICD-10 extended map
  - SNOMED CT to ICD-O simple map
  - CTV3 to SNOMED CT simple map
  - SNOMED CT to ICD-10-CM complex map
- **Parameters**:
  - `url`: ConceptMap canonical URL
  - `code`: Source code to translate
  - `system`: Source system URI
  - `target`: Target system URI
- **Response**: Translation results with equivalence information

### 3.2 Search and Query Capabilities

#### 3.2.1 Term-based Search
**CR-008: Multi-language Term Search**
- **Description**: Search SNOMED CT concepts by description terms
- **Priority**: P0 (Critical)
- **Features**:
  - Fuzzy matching with configurable similarity
  - Wildcard search support
  - ASCII folding with language-specific character preservation
  - Prefix matching for auto-completion
  - Relevance-based result ranking

**CR-009: Language Dialect Support**
- **Description**: Support multiple language dialects with appropriate reference sets
- **Priority**: P1 (High)
- **Supported Languages**:
  - English (US, GB, AU, CA, IE, NZ variants)
  - Spanish (ES, AR, UY)
  - French (FR, BE, CA)
  - German (DE)
  - Swedish (SV)
  - Norwegian (NB, NN)
  - Danish (DA)
  - Dutch (NL, BE)
  - Japanese (JA)
  - Chinese (ZH)
  - Estonian (ET)

#### 3.2.2 Expression Constraint Language (ECL)
**CR-010: ECL Query Support**
- **Description**: Support subset of SNOMED CT Expression Constraint Language
- **Priority**: P0 (Critical)
- **Supported ECL Constructs**:

##### Hierarchy Operators
- `*` - Wildcard (any concept)
- `conceptId` - Self (specific concept)
- `< conceptId` - Descendants
- `<< conceptId` - Descendants or self
- `<! conceptId` - Children
- `<<! conceptId` - Children or self
- `> conceptId` - Ancestors
- `>> conceptId` - Ancestors or self
- `>! conceptId` - Parents
- `>>! conceptId` - Parents or self

##### Set Operators
- `expressionA AND expressionB` - Intersection
- `expressionA OR expressionB` - Union
- `expressionA MINUS expressionB` - Difference

##### Reference Set Membership
- `^ refsetId` - Member of reference set

##### Attribute Refinement
- `concept : attribute = value` - Attribute constraint
- `concept : << attribute = value` - Attribute with descendants
- `concept : >> attribute = value` - Attribute with ancestors
- `concept : * = value` - Any attribute with value
- `concept : attribute = << value` - Attribute with descendant values

##### History Supplement
- `expression {{ +HISTORY }}` - Include historical associations

##### Unsupported ECL Features
- Attribute groups `{ ... }`
- Dotted expressions `concept.attribute`
- Concept filters `{{ C ... }}`
- Description filters `{{ D ... }}`
- Member filters `{{ M ... }}`
- Member field filters `[*]`

### 3.3 Data Import and Management

#### 3.3.1 SNOMED CT Package Import
**CR-011: RF2 Archive Import**
- **Description**: Import SNOMED CT RF2 release archives
- **Priority**: P0 (Critical)
- **Supported Formats**: ZIP archives containing RF2 snapshot files
- **Import Methods**:
  - Command line parameter (`--load`)
  - REST API upload (`POST /fhir-admin/load-package`)
  - Automated syndication download (`--syndicate`)

**CR-012: Extension Package Support**
- **Description**: Support SNOMED CT extension and derivative packages
- **Priority**: P1 (High)
- **Features**:
  - Multiple package import in single operation
  - Extension overlay on International Edition
  - Validation of package dependencies

#### 3.3.2 Syndication Service Integration
**CR-013: MLDS Syndication Support**
- **Description**: Automated download from SNOMED International MLDS
- **Priority**: P1 (High)
- **Features**:
  - Credential-based authentication
  - Version URI-based package selection
  - Automatic package download and import
  - HTTPS proxy support

### 3.4 Administrative Operations

#### 3.4.1 Package Management
**CR-014: Runtime Package Loading**
- **Description**: Load SNOMED CT packages during application runtime
- **Priority**: P0 (Critical)
- **Security**: Basic authentication required for admin operations
- **Endpoint**: `POST /fhir-admin/load-package`
- **Parameters**:
  - `version-uri`: SNOMED CT version URI
  - `file`: RF2 archive file(s)

#### 3.4.2 System Information
**CR-015: Capability Statement**
- **Description**: Expose FHIR capability statement with supported operations
- **Priority**: P1 (High)
- **Endpoint**: `GET /fhir/metadata`
- **Content**: FHIR CapabilityStatement resource describing supported operations

---

## 4. Non-Functional Requirements

### 4.1 Performance Requirements

**NFR-001: Memory Usage**
- **Requirement**: Maximum 500MB RAM after index creation
- **Rationale**: Enable deployment in resource-constrained environments
- **Measurement**: JVM heap size monitoring

**NFR-002: Search Response Time**
- **Requirement**: < 200ms for term searches (95th percentile)
- **Requirement**: < 100ms for concept lookups (95th percentile)
- **Rationale**: Support real-time clinical applications
- **Measurement**: API response time monitoring

**NFR-003: Index Creation Time**
- **Requirement**: < 5 minutes for International Edition import
- **Requirement**: < 2 minutes for extension package import
- **Rationale**: Minimize deployment time
- **Measurement**: Import operation duration

**NFR-004: Concurrent User Support**
- **Requirement**: Support 100 concurrent API requests
- **Rationale**: Multi-user application support
- **Measurement**: Load testing with concurrent requests

### 4.2 Scalability Requirements

**NFR-005: Horizontal Scaling**
- **Requirement**: Support stateless horizontal scaling
- **Rationale**: Auto-scaling architecture support
- **Implementation**: Read-only index, stateless operations

**NFR-006: Container Deployment**
- **Requirement**: Docker container < 2GB compressed size
- **Rationale**: Fast container startup and distribution
- **Measurement**: Docker image size analysis

### 4.3 Reliability Requirements

**NFR-007: Index Integrity**
- **Requirement**: Lucene index corruption recovery
- **Rationale**: Data integrity in production environments
- **Implementation**: Index validation on startup

**NFR-008: Error Handling**
- **Requirement**: Graceful error responses with FHIR OperationOutcome
- **Rationale**: Proper error communication to clients
- **Implementation**: Structured error responses

### 4.4 Security Requirements

**NFR-009: Authentication**
- **Requirement**: Basic authentication for administrative operations
- **Rationale**: Prevent unauthorized data modification
- **Implementation**: Spring Security with configurable credentials

**NFR-010: Input Validation**
- **Requirement**: Validate all API inputs against FHIR specifications
- **Rationale**: Prevent injection attacks and data corruption
- **Implementation**: HAPI FHIR validation framework

---

## 5. Technical Constraints

### 5.1 Platform Requirements
- **Java Runtime**: Java 17 or higher
- **Memory**: Minimum 1GB RAM for import, 500MB for operation
- **Storage**: 500MB-2GB depending on SNOMED CT edition size
- **Network**: Optional (for syndication download only)

### 5.2 Integration Constraints
- **FHIR Version**: FHIR R4 only
- **SNOMED CT Support**: Single CodeSystem per instance
- **Data Format**: RF2 snapshot files only
- **Architecture**: Embedded Lucene index (no external database)

### 5.3 Deployment Constraints
- **Container Runtime**: Docker or compatible OCI runtime
- **Java Application Server**: Embedded Tomcat (Spring Boot)
- **Operating System**: Linux, macOS, Windows (Java compatible)

---

## 6. Dependencies and Assumptions

### 6.1 External Dependencies
- **SNOMED International**: RF2 release files and syndication service access
- **HAPI FHIR**: FHIR R4 implementation framework
- **Apache Lucene**: Search index and query engine
- **Spring Boot**: Application framework and embedded server
- **SNOMED ECL Parser**: Expression Constraint Language parsing

### 6.2 Key Assumptions
- **Single Edition**: One SNOMED CT edition per application instance
- **Snapshot Only**: No support for delta or full RF2 files
- **Read-Heavy Workload**: Optimized for read operations over write operations
- **Terminology Focus**: SNOMED CT specific, not general terminology server
- **Memory Constraints**: Target environment has limited available memory

---

## 7. Success Criteria

### 7.1 Performance Metrics
- **Import Time**: International Edition import completes within 5 minutes
- **Memory Usage**: Stable operation under 500MB RAM
- **Response Time**: 95% of API calls respond within 200ms
- **Throughput**: Handle 1000+ requests per minute per instance

### 7.2 Functional Completeness
- **FHIR Compliance**: Pass FHIR R4 terminology operation validation tests
- **ECL Coverage**: Support 95% of common ECL expressions used in healthcare
- **Language Support**: Accurate search results in all configured language dialects
- **Data Integrity**: 100% accuracy in concept relationships and hierarchies

### 7.3 Operational Excellence
- **Deployment Success**: < 10 minutes from container start to ready state
- **Error Rate**: < 0.1% API error rate under normal conditions
- **Resource Efficiency**: 50% lower memory usage than comparable solutions
- **Documentation Completeness**: Full API documentation and deployment guides