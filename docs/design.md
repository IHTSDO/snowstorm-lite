# Technical Design Document
## Snowstorm Lite FHIR Terminology Server

### Document Information
- **Version**: 1.0
- **Date**: 2025-01-23
- **Product**: Snowstorm Lite v2.3.0
- **Status**: Final

---

## 1. System Architecture Overview

### 1.1 High-Level Architecture

Snowstorm Lite is designed as a self-contained, stateless Spring Boot application that provides FHIR R4 terminology services for SNOMED CT. The architecture prioritizes minimal memory footprint, fast search performance, and horizontal scalability.

```
┌─────────────────────────────────────────────────────────────┐
│                    FHIR R4 REST API Layer                  │
├─────────────────────────────────────────────────────────────┤
│  CodeSystem  │  ValueSet   │ ConceptMap  │    Admin        │
│  Provider    │  Provider   │ Provider    │  Controller     │
├─────────────────────────────────────────────────────────────┤
│              Business Logic Service Layer                   │
├─────────────────────────────────────────────────────────────┤
│ CodeSystem │ ValueSet │    ECL         │   Import        │
│ Service    │ Service  │ Constraint     │   Service       │
│            │          │ Language       │                 │
├─────────────────────────────────────────────────────────────┤
│                Repository/Data Access Layer                 │
├─────────────────────────────────────────────────────────────┤
│ CodeSystem │ ValueSet │              │   Index I/O      │
│ Repository │Repository│   Term Search │   Provider       │
│            │          │   Helper      │                 │
├─────────────────────────────────────────────────────────────┤
│                   Apache Lucene Index                      │
├─────────────────────────────────────────────────────────────┤
│  Concept    │  Code     │   ValueSet   │   Relationship  │
│  Documents  │  System   │   Documents  │   Attributes    │
│             │  Docs     │              │                 │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 Core Components

#### 1.2.1 FHIR API Layer
- **CodeSystemProvider**: Handles FHIR CodeSystem operations ($lookup, $subsumes)
- **ValueSetProvider**: Manages ValueSet CRUD and operations ($expand, $validate-code)
- **ConceptMapProvider**: Supports SNOMED CT implicit ConceptMap translations
- **AdminController**: Administrative operations for package loading
- **CapabilityStatementCustomizer**: Exposes supported FHIR operations

#### 1.2.2 Service Layer
- **CodeSystemService**: Business logic for concept operations and subsumption testing
- **ValueSetService**: ValueSet expansion, validation, and term searching
- **ExpressionConstraintLanguageService**: ECL parsing and query building
- **ImportService**: RF2 file processing and Lucene index creation
- **AppSetupService**: Application initialization and data loading coordination

#### 1.2.3 Repository Layer
- **CodeSystemRepository**: Lucene-based concept storage and retrieval
- **ValueSetRepository**: Custom ValueSet persistence in Lucene
- **IndexIOProvider**: Manages Lucene IndexReader/IndexWriter lifecycle
- **TermSearchHelper**: Text processing and character folding utilities

---

## 2. Data Model and Storage

### 2.1 Lucene Index Structure

#### 2.1.1 Document Types
The Lucene index contains three primary document types, distinguished by the `_type` field:

1. **Concept Documents** (`concept`)
2. **CodeSystem Documents** (`code-system`)
3. **ValueSet Documents** (`vs`)

#### 2.1.2 Concept Document Schema

##### Core Identity Fields
```java
// Document type discriminator
_type: "concept" (StringField, Store.YES)

// Concept identification
id: "123456789" (StringField, Store.YES)
active: "1" | "0" (StringField, Store.YES)
effective_time: "20240101" (StringField, Store.YES)
module: "900000000000207008" (StringField, Store.YES)
defined: "1" | "0" (StringField, Store.YES)
```

##### Hierarchy and Relationship Fields
```java
// Direct relationships
parents: ["parent1", "parent2", ...] (StringField, Store.YES, multi-valued)
ancestors: ["anc1", "anc2", ...] (StringField, Store.YES, multi-valued)
children: ["child1", "child2", ...] (StringField, Store.YES, multi-valued)

// Attribute relationships by type
at_363698007: ["target1", "target2", ...] (StringField, Store.NO, multi-valued)
at_any: ["all_targets", ...] (StringField, Store.NO, multi-valued)

// Serialized relationship data
rel_stored: "0{363698007=113331007}|1{116676008=72704001}" (StoredField, Store.YES)
```

##### Reference Set and Mapping Fields
```java
// Reference set membership
membership: ["refset1", "refset2", ...] (StringField, Store.YES, multi-valued)

// Concept mappings (serialized)
mapping: ["map_data1", "map_data2", ...] (StringField, Store.YES, multi-valued)
```

##### Term and Description Fields
```java
// Language-specific searchable terms (folded)
term.en: "diabetes mellitus disorder" (TextField, Store.YES)
term.es: "trastorno de diabetes mellitus" (TextField, Store.YES)
term.{lang}: "..." (TextField, Store.YES)

// Serialized description data
term_stored: [
  "fsn|en|Diabetes mellitus (disorder)",
  "syn|en|Diabetes mellitus|900000000000509007",
  "syn|es|Diabetes mellitus|450828004"
] (StoredField, Store.YES, multi-valued)
```

##### Sorting and Performance Fields
```java
// Active status for sorting (1=active, 0=inactive)
active_sort: 1 (NumericDocValuesField)

// Combined PT and FSN term lengths for relevance sorting
pt_term_len: 17025 (SortedNumericDocValuesField)
// Formula: (ptTermLength * 1000) + fsnTermLength
```

#### 2.1.3 CodeSystem Document Schema
```java
_type: "code-system" (StringField, Store.YES)
module: "900000000000207008" (StringField, Store.YES)
version_date: "20240101" (StringField, Store.YES)
last_updated: 1704067200000 (LongField, Store.YES)
```

#### 2.1.4 ValueSet Document Schema
```java
_type: "vs" (StringField, Store.YES)
id: "valueset-id" (StringField, Store.YES)
url: "http://example.org/ValueSet/example" (StringField, Store.YES)
version: "1.0.0" (StringField, Store.YES)
name: "ExampleValueSet" (StringField, Store.YES)
status: "active" (StringField, Store.YES)
exp: "0" | "1" (StringField, Store.YES)
desc: "Description text" (StringField, Store.YES)
serialised: "{\"resourceType\":\"ValueSet\",...}" (StringField, Store.YES)
```

### 2.2 Analyzer Configuration

#### 2.2.1 Text Analysis Strategy
- **Primary Analyzer**: StandardAnalyzer with empty stop word set
- **Character Folding**: Custom ASCII folding via `TermSearchHelper.foldTerm()`
- **Language-Specific Processing**: Configurable character preservation per language

#### 2.2.2 Character Folding Implementation
```java
public static String foldTerm(String term, Set<Character> charactersNotFolded) {
    // Convert to lowercase
    char[] chars = term.toLowerCase().toCharArray();
    char[] charsFolded = new char[chars.length * 2];
    
    // Apply ASCII folding while preserving language-specific characters
    int charsFoldedOffset = 0;
    for (int i = 0; i < chars.length; i++) {
        if (charactersNotFolded.contains(chars[i])) {
            // Preserve language-specific characters (e.g., å, ä, ö)
            charsFolded[charsFoldedOffset] = chars[i];
        } else {
            // Apply ASCII folding to other characters
            int length = ASCIIFoldingFilter.foldToASCII(chars, i, charsFolded, charsFoldedOffset, 1);
            if (length != charsFoldedOffset + 1) {
                charsFoldedOffset = length - 1;
            }
        }
        charsFoldedOffset++;
    }
    return new String(charsFolded, 0, charsFoldedOffset);
}
```

---

## 3. Search Architecture

### 3.1 Search Strategy Overview

Snowstorm Lite implements a multi-layered search strategy combining:
1. **Lucene-based term indexing** for text search
2. **ECL constraint evaluation** for hierarchical queries
3. **Relevance scoring** with custom sorting
4. **Multi-language support** with character folding

### 3.2 Term Search Implementation

#### 3.2.1 Search Query Construction
```java
// Base query structure for concept search
BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder()
    .add(new TermQuery(new Term("_type", "concept")), BooleanClause.Occur.MUST)
    .add(new TermQuery(new Term("active", "1")), BooleanClause.Occur.MUST);

// Add term filter if specified
if (termFilter != null && !termFilter.isBlank()) {
    BooleanQuery.Builder langBuilder = new BooleanQuery.Builder();
    
    // Process each search word
    for (String searchWord : searchWords) {
        String foldedSearchWord = foldTerm(searchWord, charactersNotFolded);
        
        // Add both fuzzy and wildcard queries
        langBuilder.add(new FuzzyQuery(new Term(termField, foldedSearchWord)), BooleanClause.Occur.MUST);
        langBuilder.add(new WildcardQuery(new Term(termField, foldedSearchWord + "*")), BooleanClause.Occur.MUST);
    }
    
    queryBuilder.add(langBuilder.build(), BooleanClause.Occur.MUST);
}
```

#### 3.2.2 Language-Specific Term Fields
```java
// Dynamic term field selection based on language dialects
for (LanguageDialect dialect : languageDialects) {
    String termField = "term." + dialect.getLanguageCode();
    Set<Character> charactersNotFolded = languageConfig.getCharactersNotFolded(dialect.getLanguageCode());
    
    // Build language-specific query
    BooleanQuery.Builder langBuilder = buildLanguageQuery(termField, searchTerms, charactersNotFolded);
    termBuilder.add(langBuilder.build(), BooleanClause.Occur.SHOULD);
}
```

#### 3.2.3 Relevance Scoring and Sorting
```java
// Multi-criteria sorting for optimal relevance
Sort sort = new Sort(
    // Active concepts first
    new SortedNumericSortField(FHIRConcept.FieldNames.ACTIVE_SORT, SortField.Type.INT, true),
    
    // Shorter terms first (PT length * 1000 + FSN length)
    new SortedNumericSortField(FHIRConcept.FieldNames.PT_AND_FSN_TERM_LENGTH, SortField.Type.INT),
    
    // Lucene relevance score
    SortField.FIELD_SCORE
);

// Additional post-processing for term length optimization
if (additionalSorting && termMatcher != null) {
    // Find shortest matching term per concept
    Comparator<FHIRDescription> descriptionComparator = Comparator
        .comparingInt(FHIRDescription::getTermLength)
        .thenComparing(FHIRDescription::getTerm)
        .thenComparing(d -> d.getConcept().getPT(displayLanguages));
    
    // Re-sort by shortest matching description
    conceptPage = sortByShortestMatchingTerm(conceptPage, termMatcher, descriptionComparator);
}
```

### 3.3 ECL Query Processing

#### 3.3.1 ECL Parser Integration
```java
// ECL parsing pipeline
public BooleanQuery.Builder getEclConstraints(String ecl) throws IOException {
    try {
        // Parse ECL expression into constraint tree
        SConstraint constraint = (SConstraint) eclQueryBuilder.createQuery(ecl);
        
        // Convert constraint tree to Lucene query
        return constraint.addQuery(new BooleanQuery.Builder(), this);
    } catch (ECLException eclException) {
        throw exception(format("ECL syntax error. %s", eclException.getMessage()), 
                       OperationOutcome.IssueType.INVARIANT, 400);
    }
}
```

#### 3.3.2 Constraint Type Implementation

##### Hierarchy Constraints
```java
// Descendants: < conceptId
public BooleanQuery.Builder addQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) {
    if (operator == Operator.descendant_of) {
        // Query ancestors field for the specified concept
        return builder.add(new TermQuery(new Term(FHIRConcept.FieldNames.ANCESTORS, conceptId)), BooleanClause.Occur.MUST);
    }
    
    // Descendants or self: << conceptId  
    if (operator == Operator.descendant_or_self_of) {
        BooleanQuery.Builder descendantBuilder = new BooleanQuery.Builder();
        descendantBuilder.add(new TermQuery(new Term(FHIRConcept.FieldNames.ID, conceptId)), BooleanClause.Occur.SHOULD);
        descendantBuilder.add(new TermQuery(new Term(FHIRConcept.FieldNames.ANCESTORS, conceptId)), BooleanClause.Occur.SHOULD);
        return builder.add(descendantBuilder.build(), BooleanClause.Occur.MUST);
    }
}
```

##### Attribute Constraints
```java
// Attribute refinement: concept : attribute = value
public BooleanQuery.Builder addQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) {
    String attributeField = "at_" + attributeType;
    
    if (operator == Operator.equals) {
        // Direct attribute value match
        return builder.add(new TermQuery(new Term(attributeField, targetValue)), BooleanClause.Occur.MUST);
    }
    
    if (operator == Operator.descendant_or_self_of) {
        // Attribute value or descendants
        Set<Long> targetConcepts = eclService.getConceptIds(valueConstraint);
        BooleanQuery.Builder valueBuilder = new BooleanQuery.Builder();
        for (Long conceptId : targetConcepts) {
            valueBuilder.add(new TermQuery(new Term(attributeField, conceptId.toString())), BooleanClause.Occur.SHOULD);
        }
        return builder.add(valueBuilder.build(), BooleanClause.Occur.MUST);
    }
}
```

##### Set Operations
```java
// Compound expressions: expressionA AND/OR/MINUS expressionB
public BooleanQuery.Builder addQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) {
    switch (operator) {
        case intersection:  // AND
            // Both constraints must match
            leftConstraint.addQuery(builder, eclService);
            rightConstraint.addQuery(builder, eclService);
            return builder;
            
        case union:  // OR
            // Either constraint can match
            BooleanQuery.Builder unionBuilder = new BooleanQuery.Builder();
            unionBuilder.add(leftConstraint.addQuery(new BooleanQuery.Builder(), eclService).build(), BooleanClause.Occur.SHOULD);
            unionBuilder.add(rightConstraint.addQuery(new BooleanQuery.Builder(), eclService).build(), BooleanClause.Occur.SHOULD);
            return builder.add(unionBuilder.build(), BooleanClause.Occur.MUST);
            
        case exclusion:  // MINUS
            // Left constraint must match, right constraint must not
            leftConstraint.addQuery(builder, eclService);
            builder.add(rightConstraint.addQuery(new BooleanQuery.Builder(), eclService).build(), BooleanClause.Occur.MUST_NOT);
            return builder;
    }
}
```

---

## 4. API Design

### 4.1 FHIR Endpoint Architecture

#### 4.1.1 CodeSystem Operations

##### Concept Lookup: `POST /fhir/CodeSystem/$lookup`
```java
@Operation(name="$lookup", idempotent=true)
public Parameters lookupImplicit(
    @OperationParam(name="code") CodeType code,
    @OperationParam(name="system") UriType system,
    @OperationParam(name="version") StringType version,
    @OperationParam(name="coding") Coding coding,
    @OperationParam(name="displayLanguage") String displayLanguage,
    @OperationParam(name="property") List<CodeType> propertiesType) {
    
    // Validate input parameters
    mutuallyExclusive("code", code, "coding", coding);
    FHIRCodeSystem codeSystem = getCodeSystemVersionOrThrow(system, version, coding);
    
    // Parse display language preferences
    List<LanguageDialect> languageDialects = languageDialectParser
        .parseDisplayLanguageWithDefaultFallback(displayLanguage, acceptLanguageHeader);
    
    // Perform lookup and return concept details
    return codeSystemService.lookup(codeSystem, recoverCode(code, coding), languageDialects);
}
```

##### Subsumption Testing: `POST /fhir/CodeSystem/$subsumes`
```java
@Operation(name="$subsumes", idempotent=true)
public Parameters subsumes(
    @OperationParam(name="codeA") CodeType codeA,
    @OperationParam(name="codeB") CodeType codeB,
    @OperationParam(name="system") UriType system,
    @OperationParam(name="version") StringType version,
    @OperationParam(name="codingA") Coding codingA,
    @OperationParam(name="codingB") Coding codingB) {
    
    // Validate exactly one code parameter per position
    requireExactlyOneOf("codeA", codeA, "codingA", codingA);
    requireExactlyOneOf("codeB", codeB, "codingB", codingB);
    
    // Extract codes and determine subsumption relationship
    String codeAValue = recoverCode(codeA, codingA);
    String codeBValue = recoverCode(codeB, codingB);
    
    return codeSystemService.subsumes(codeSystem, codeAValue, codeBValue);
}
```

#### 4.1.2 ValueSet Operations

##### ValueSet Expansion: `POST /fhir/ValueSet/$expand`
```java
@Operation(name = "$expand", idempotent = true)
public ValueSet expand(
    @OperationParam(name="url") UriType url,
    @OperationParam(name="filter") String filter,
    @OperationParam(name="offset") IntegerType offset,
    @OperationParam(name="count") IntegerType countType,
    @OperationParam(name="includeDesignations") BooleanType includeDesignationsType,
    @OperationParam(name="activeOnly") BooleanType activeType,
    @OperationParam(name="displayLanguage") String displayLanguage) {
    
    // Parse and validate parameters
    int offsetValue = offset != null ? offset.getValue() : 0;
    int countValue = countType != null ? countType.getValue() : 20;
    boolean includeDesignations = includeDesignationsType != null && includeDesignationsType.getValue();
    boolean activeOnly = activeType == null || activeType.getValue();
    
    // Parse language preferences
    List<LanguageDialect> languageDialects = languageDialectParser
        .parseDisplayLanguageWithDefaultFallback(displayLanguage, acceptLanguageHeader);
    
    // Perform expansion
    return valueSetService.expand(url.getValue(), filter, languageDialects, 
                                 includeDesignations, offsetValue, countValue);
}
```

##### Code Validation: `POST /fhir/ValueSet/$validate-code`
```java
@Operation(name="$validate-code", idempotent=true)
public Parameters validateCode(
    @OperationParam(name="url") UriType url,
    @OperationParam(name="code") String code,
    @OperationParam(name="system") UriType system,
    @OperationParam(name="display") String display,
    @OperationParam(name="coding") Coding coding,
    @OperationParam(name="displayLanguage") String displayLanguage) {
    
    // Determine code and system from parameters
    String codeValue = recoverCode(code, coding);
    String systemValue = recoverSystem(system, coding);
    
    // Parse language preferences
    List<LanguageDialect> languageDialects = languageDialectParser
        .parseDisplayLanguageWithDefaultFallback(displayLanguage, acceptLanguageHeader);
    
    // Validate code against ValueSet
    return valueSetService.validateCode(url.getValue(), codeValue, systemValue, 
                                       display, languageDialects);
}
```

#### 4.1.3 ConceptMap Operations

##### Translation: `POST /fhir/ConceptMap/$translate`
```java
@Operation(name="$translate", idempotent=true)
public Parameters translate(
    @OperationParam(name="url") UriType urlType,
    @OperationParam(name="code") String code,
    @OperationParam(name="system") String system,
    @OperationParam(name="target") String targetValueSet,
    @OperationParam(name="targetsystem") String targetSystem,
    @OperationParam(name="reverse") BooleanType reverse) throws IOException {
    
    // Validate required parameters
    required("url", urlType);
    required("code", code);
    required("system", system);
    
    // Extract SNOMED CT implicit ConceptMap configuration
    FHIRConceptMapImplicitConfig.ConceptMapConfig config = implicitConfig.getConceptMap(urlType.getValue());
    if (config == null) {
        throw exception("ConceptMap not found for URL: " + urlType.getValue(), 
                       OperationOutcome.IssueType.NOTFOUND, 404);
    }
    
    // Perform translation using SNOMED CT mappings
    return performTranslation(config, code, system, targetSystem, reverse);
}
```

#### 4.1.4 Administrative Operations

##### Package Loading: `POST /fhir-admin/load-package`
```java
@PostMapping(value = "load-package", consumes = "multipart/form-data")
public void loadPackage(
    @RequestParam(name = "version-uri") String versionUri, 
    @RequestParam MultipartFile[] file, 
    HttpServletResponse response) throws IOException {
    
    // Validate file upload
    if (file == null || file.length == 0) {
        error(new FHIRServerResponseException(400, "Missing file parameter.", new OperationOutcome()), response);
        return;
    }
    
    try {
        // Process uploaded files
        Set<InputStream> inputStreams = new HashSet<>();
        for (MultipartFile multipartFile : file) {
            File tempFile = File.createTempFile("snomed-archive-upload-" + UUID.randomUUID(), ".tgz");
            Files.copy(multipartFile.getInputStream(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            inputStreams.add(new FileInputStream(tempFile));
        }
        
        // Import SNOMED CT release
        importService.importReleaseStreams(inputStreams, versionUri);
        
    } catch (IOException | ReleaseImportException e) {
        error(new FHIRServerResponseException(500, "Failed to import SNOMED CT.", new OperationOutcome()), response);
    }
}
```

### 4.2 URL Pattern Design

#### 4.2.1 SNOMED CT Implicit ValueSets
```
// ECL-based ValueSets
http://snomed.info/sct?fhir_vs=ecl/{ecl_expression}
Examples:
- http://snomed.info/sct?fhir_vs=ecl/*                              // All concepts
- http://snomed.info/sct?fhir_vs=ecl/< 404684003                    // Clinical findings
- http://snomed.info/sct?fhir_vs=ecl/<< 73211009                    // Diabetes and subtypes
- http://snomed.info/sct?fhir_vs=ecl/^ 447562003                    // ICD-10 map refset members

// ISA-based ValueSets  
http://snomed.info/sct?fhir_vs=isa/{concept_id}
Examples:
- http://snomed.info/sct?fhir_vs=isa/404684003                      // Clinical finding subtypes
```

#### 4.2.2 SNOMED CT Implicit ConceptMaps
```
// Association reference sets
http://snomed.info/sct?fhir_cm={refset_id}
Examples:
- http://snomed.info/sct?fhir_cm=900000000000527005                 // SAME AS associations
- http://snomed.info/sct?fhir_cm=900000000000526001                 // REPLACED BY associations

// External mapping reference sets
- http://snomed.info/sct?fhir_cm=447562003                          // SNOMED CT to ICD-10
- http://snomed.info/sct?fhir_cm=446608001                          // SNOMED CT to ICD-O
```

---

## 5. Performance Optimization

### 5.1 Memory Management

#### 5.1.1 Index Design for Low Memory
```java
// Optimized field storage strategy
public static void addConceptToIndex(Document doc, FHIRConcept concept) {
    // Store only essential fields with Store.YES
    doc.add(new StringField(FHIRConcept.FieldNames.ID, concept.getConceptId(), Field.Store.YES));
    doc.add(new StringField(FHIRConcept.FieldNames.ACTIVE, concept.isActive() ? "1" : "0", Field.Store.YES));
    
    // Use DocValues for sorting without storing
    doc.add(new NumericDocValuesField(FHIRConcept.FieldNames.ACTIVE_SORT, concept.isActive() ? 1 : 0));
    
    // Store relationship data efficiently in single field
    doc.add(new StoredField(FHIRConcept.FieldNames.REL_STORED, serializeRelationships(concept)));
    
    // Index attribute fields without storage for search only
    for (FHIRRelationship rel : concept.getRelationships()) {
        doc.add(new StringField("at_" + rel.getType(), rel.getTarget().toString(), Field.Store.NO));
    }
}
```

#### 5.1.2 Batch Processing Configuration
```java
// Import batch size optimization for memory constraints
@Value("${import.batch-size:40}")
private int batchSizeThousands;

public void processConcepts(Stream<Concept> conceptStream) {
    final int batchSize = batchSizeThousands * 1000;
    
    conceptStream
        .filter(Concept::isActive)
        .collect(Collectors.groupingBy(concept -> concept.getId() / batchSize))
        .values()
        .forEach(batch -> {
            processBatch(batch);
            // Force garbage collection between batches
            System.gc();
        });
}
```

#### 5.1.3 IndexReader Lifecycle Management
```java
@Service
public class IndexIOProvider {
    private IndexSearcher indexSearcher;
    private DirectoryReader indexReader;
    
    @PostConstruct
    public void enableRead() throws IOException {
        Directory indexDirectory = FSDirectory.open(Paths.get(indexPath));
        indexReader = DirectoryReader.open(indexDirectory);
        indexSearcher = new IndexSearcher(indexReader);
    }
    
    // Lazy initialization to minimize memory usage
    public IndexSearcher getIndexSearcher() throws IOException {
        if (indexSearcher == null) {
            enableRead();
        }
        return indexSearcher;
    }
}
```

### 5.2 Search Performance

#### 5.2.1 Term Field Optimization
```java
// Pre-folded term indexing for faster search
public void indexDescriptions(Document doc, List<FHIRDescription> descriptions) {
    Map<String, Set<String>> termsByLanguage = new HashMap<>();
    
    for (FHIRDescription description : descriptions) {
        String language = description.getLang();
        Set<Character> charactersNotFolded = languageConfig.getCharactersNotFolded(language);
        
        // Pre-fold terms during indexing
        String foldedTerm = TermSearchHelper.foldTerm(description.getTerm(), charactersNotFolded);
        termsByLanguage.computeIfAbsent(language, k -> new HashSet<>()).add(foldedTerm);
        
        // Store serialized description data efficiently
        String serialized = serializeDescription(description);
        doc.add(new StoredField(FHIRConcept.FieldNames.TERM_STORED, serialized));
    }
    
    // Add language-specific term fields
    for (Map.Entry<String, Set<String>> entry : termsByLanguage.entrySet()) {
        String termField = "term." + entry.getKey();
        String combinedTerms = String.join(" ", entry.getValue());
        doc.add(new TextField(termField, combinedTerms, Field.Store.YES));
    }
}
```

#### 5.2.2 Relevance Scoring Optimization
```java
// Multi-level sorting for optimal search results
public TopDocs searchWithOptimalSorting(Query query, int maxResults) throws IOException {
    Sort sort = new Sort(
        // Primary: Active concepts first (stored as DocValues)
        new SortedNumericSortField(FHIRConcept.FieldNames.ACTIVE_SORT, SortField.Type.INT, true),
        
        // Secondary: Shorter terms first (pre-calculated term length)
        new SortedNumericSortField(FHIRConcept.FieldNames.PT_AND_FSN_TERM_LENGTH, SortField.Type.INT),
        
        // Tertiary: Lucene relevance score
        SortField.FIELD_SCORE
    );
    
    return indexSearcher.search(query, maxResults, sort, true);
}

// Pre-calculate term length during indexing
private void addTermLengthSortField(Document doc, FHIRConcept concept, List<LanguageDialect> defaultDialects) {
    String pt = concept.getPT(defaultDialects);
    String fsn = concept.getFSN();
    
    int ptLength = pt != null ? pt.length() : 999;
    int fsnLength = fsn != null ? fsn.length() : 999;
    
    // Combined length: PT length * 1000 + FSN length
    long combinedLength = (long) ptLength * 1000 + fsnLength;
    doc.add(new SortedNumericDocValuesField(FHIRConcept.FieldNames.PT_AND_FSN_TERM_LENGTH, combinedLength));
}
```

### 5.3 Caching Strategy

#### 5.3.1 CodeSystem Singleton Pattern
```java
@Service
public class CodeSystemRepository {
    private FHIRCodeSystem cachedCodeSystem;
    
    public FHIRCodeSystem getCodeSystem() {
        if (cachedCodeSystem == null) {
            cachedCodeSystem = loadCodeSystemFromIndex();
        }
        return cachedCodeSystem;
    }
    
    public void invalidateCodeSystemCache() {
        cachedCodeSystem = null;
    }
}
```

#### 5.3.2 ECL Query Compilation Caching
```java
@Service
public class ExpressionConstraintLanguageService {
    private final Map<String, SConstraint> constraintCache = new ConcurrentHashMap<>();
    
    public SConstraint getEclConstraintRaw(String ecl) {
        return constraintCache.computeIfAbsent(ecl, eclQueryBuilder::createQuery);
    }
    
    // Clear cache when index is updated
    public void clearConstraintCache() {
        constraintCache.clear();
    }
}
```

---

## 6. Security Architecture

### 6.1 Authentication and Authorization

#### 6.1.1 Basic Authentication Configuration
```java
@Configuration
@EnableWebSecurity
public class BasicAuthWebSecurityConfiguration {
    
    @Value("${admin.username}")
    private String adminUsername;
    
    @Value("${admin.password}")
    private String adminPassword;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/fhir/**").permitAll()           // Read-only FHIR operations
                .requestMatchers("/fhir-admin/**").authenticated() // Admin operations require auth
                .anyRequest().authenticated()
            )
            .httpBasic(withDefaults())
            .csrf().disable()
            .build();
    }
    
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.builder()
            .username(adminUsername)
            .password("{noop}" + adminPassword)  // No password encoding for simplicity
            .authorities("ADMIN")
            .build();
        return new InMemoryUserDetailsManager(admin);
    }
}
```

#### 6.1.2 Input Validation Framework
```java
// FHIR parameter validation using HAPI FHIR
public class FHIRHelper {
    
    public static void required(String paramName, Object param) {
        if (param == null) {
            throw exception(format("Parameter '%s' is required", paramName), 
                           OperationOutcome.IssueType.REQUIRED, 400);
        }
    }
    
    public static void mutuallyExclusive(String param1Name, Object param1, String param2Name, Object param2) {
        if (param1 != null && param2 != null) {
            throw exception(format("Parameters '%s' and '%s' are mutually exclusive", param1Name, param2Name), 
                           OperationOutcome.IssueType.INVARIANT, 400);
        }
    }
    
    public static void requireExactlyOneOf(String param1Name, Object param1, String param2Name, Object param2) {
        if ((param1 == null && param2 == null) || (param1 != null && param2 != null)) {
            throw exception(format("Exactly one of '%s' or '%s' must be provided", param1Name, param2Name), 
                           OperationOutcome.IssueType.REQUIRED, 400);
        }
    }
}
```

### 6.2 Error Handling and Logging

#### 6.2.1 Structured Error Responses
```java
public class FHIRServerResponseException extends RuntimeException {
    private final int statusCode;
    private final OperationOutcome operationOutcome;
    
    public FHIRServerResponseException(int statusCode, String message, OperationOutcome operationOutcome) {
        super(message);
        this.statusCode = statusCode;
        this.operationOutcome = operationOutcome;
    }
    
    // Convert to FHIR OperationOutcome response
    public void writeToResponse(HttpServletResponse response, FhirContext fhirContext) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/fhir+json");
        
        IParser jsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
        jsonParser.encodeResourceToWriter(operationOutcome, new OutputStreamWriter(response.getOutputStream()));
    }
}
```

#### 6.2.2 Security Logging Configuration
```properties
# Application logging configuration
logging.level.org.snomed.snowstormlite=INFO
logging.level.org.springframework.security=WARN
logging.level.org.springframework.web=INFO

# Log security events
logging.level.org.springframework.security.web.authentication=INFO
logging.level.org.springframework.security.web.access=WARN
```

---

## 7. Deployment Architecture

### 7.1 Container Design

#### 7.1.1 Docker Configuration
```dockerfile
# Multi-stage build for minimal image size
FROM amazoncorretto:17 AS builder
WORKDIR /app
COPY target/snowstorm-lite-*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM amazoncorretto:17
RUN yum install -y shadow-utils && \
    groupadd -r snowstorm && \
    useradd -r -g snowstorm -m -d /app snowstorm

WORKDIR /app
COPY --from=builder app/dependencies/ ./
COPY --from=builder app/spring-boot-loader/ ./
COPY --from=builder app/snapshot-dependencies/ ./
COPY --from=builder app/application/ ./

# Security and performance optimizations
USER snowstorm
EXPOSE 8080
VOLUME ["/app/lucene-index"]

# JVM optimizations for container environment
ENV JAVA_OPTS="-Xms1g -Xmx2g -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 7.1.2 Jib Maven Plugin Configuration
```xml
<plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <configuration>
        <container>
            <mainClass>org.snomed.snowstormlite.SnowstormLiteApplication</mainClass>
            <jvmFlags>
                <jvmFlag>-Xms1g</jvmFlag>
                <jvmFlag>-Xmx2g</jvmFlag>
                <jvmFlag>-Djava.security.egd=file:/dev/./urandom</jvmFlag>
            </jvmFlags>
            <ports>
                <port>8080</port>
            </ports>
            <volumes>
                <volume>/tmp</volume>
            </volumes>
            <workingDirectory>/app</workingDirectory>
        </container>
        <from>
            <image>amazoncorretto:17</image>
            <platforms>
                <platform>
                    <architecture>arm64</architecture>
                    <os>linux</os>
                </platform>
                <platform>
                    <architecture>amd64</architecture>
                    <os>linux</os>
                </platform>
            </platforms>
        </from>
    </configuration>
</plugin>
```

### 7.2 Configuration Management

#### 7.2.1 Environment-Specific Configuration
```yaml
# docker-compose.yml
version: '3.8'
services:
  snowstorm-lite:
    image: snomedinternational/snowstorm-lite:latest
    ports:
      - "8080:8080"
    environment:
      - ADMIN_PASSWORD=${ADMIN_PASSWORD:-snowstormLITE}
      - INDEX_PATH=lucene-index/data
      - SERVER_PORT=8080
      - SPRING_PROFILES_ACTIVE=production
    volumes:
      - snowstorm-lite-index:/app/lucene-index
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/fhir/metadata"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

volumes:
  snowstorm-lite-index:
```

#### 7.2.2 Kubernetes Deployment Configuration
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: snowstorm-lite
spec:
  replicas: 3
  selector:
    matchLabels:
      app: snowstorm-lite
  template:
    metadata:
      labels:
        app: snowstorm-lite
    spec:
      containers:
      - name: snowstorm-lite
        image: snomedinternational/snowstorm-lite:latest
        ports:
        - containerPort: 8080
        env:
        - name: ADMIN_PASSWORD
          valueFrom:
            secretKeyRef:
              name: snowstorm-lite-secret
              key: admin-password
        - name: INDEX_PATH
          value: "lucene-index/data"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        volumeMounts:
        - name: index-storage
          mountPath: /app/lucene-index
        livenessProbe:
          httpGet:
            path: /fhir/metadata
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /fhir/metadata
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
      volumes:
      - name: index-storage
        persistentVolumeClaim:
          claimName: snowstorm-lite-pvc
```

---

## 8. Monitoring and Observability

### 8.1 Health Checks and Metrics

#### 8.1.1 Spring Boot Actuator Configuration
```java
// Health indicator for index availability
@Component
public class IndexHealthIndicator implements HealthIndicator {
    
    @Autowired
    private IndexIOProvider indexIOProvider;
    
    @Override
    public Health health() {
        try {
            IndexSearcher searcher = indexIOProvider.getIndexSearcher();
            if (searcher != null && searcher.getIndexReader().numDocs() > 0) {
                return Health.up()
                    .withDetail("indexDocs", searcher.getIndexReader().numDocs())
                    .withDetail("indexPath", indexIOProvider.getIndexPath())
                    .build();
            } else {
                return Health.down()
                    .withDetail("reason", "Index not available or empty")
                    .build();
            }
        } catch (IOException e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

#### 8.1.2 Custom Metrics Configuration
```java
// Performance metrics collection
@Component
public class SearchMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Timer searchTimer;
    private final Counter searchCounter;
    
    public SearchMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.searchTimer = Timer.builder("snowstorm.search.duration")
            .description("Time taken for terminology searches")
            .register(meterRegistry);
        this.searchCounter = Counter.builder("snowstorm.search.requests")
            .description("Number of search requests")
            .register(meterRegistry);
    }
    
    public <T> T timeSearch(String operation, Supplier<T> searchOperation) {
        searchCounter.increment(Tags.of("operation", operation));
        return searchTimer.recordCallable(() -> searchOperation.get());
    }
}
```

### 8.2 Logging Strategy

#### 8.2.1 Structured Logging Configuration
```properties
# Logback configuration for structured logging
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n

# Application-specific logging levels
logging.level.org.snomed.snowstormlite.service=DEBUG
logging.level.org.snomed.snowstormlite.fhir=INFO
logging.level.org.apache.lucene=WARN
logging.level.ca.uhn.fhir=WARN

# Performance logging
logging.level.org.snomed.snowstormlite.service.ValueSetService=DEBUG
logging.level.org.snomed.snowstormlite.service.CodeSystemRepository=DEBUG
```

---

## 9. Testing Strategy

### 9.1 Unit Testing Architecture

#### 9.1.1 ECL Testing Framework
```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ECLTest {
    
    @Autowired
    private ValueSetService valueSetService;
    
    @Test
    void testECLHierarchyOperators() throws IOException {
        // Test descendants
        assertCodesEqual("[313005, 362969004]", getCodes("< 404684003 |Clinical finding|"));
        
        // Test descendants or self
        assertCodesEqual("[313005, 362969004, 404684003]", getCodes("<< 404684003 |Clinical finding|"));
        
        // Test children
        assertCodesEqual("[313005, 362969004]", getCodes("<! 404684003 |Clinical finding|"));
        
        // Test ancestors
        assertCodesEqual("[138875005, 404684003]", getCodes("> 362969004"));
    }
    
    @Test
    void testECLSetOperations() throws IOException {
        // Test intersection
        assertCodesEqual("[138875005]", getCodes(">> 900000000000441003 AND >> 362969004"));
        
        // Test union
        assertCodesEqual("[138875005, 362969004, 404684003, 900000000000441003]", 
                        getCodes(">> 900000000000441003 OR >> 362969004"));
        
        // Test exclusion
        assertCodesEqual("[900000000000441003]", 
                        getCodes(">> 900000000000441003 MINUS >> 362969004"));
    }
    
    @Test
    void testECLAttributeRefinement() throws IOException {
        // Test attribute constraint
        assertCodesEqual("[362969004]", 
                        getCodes("< 404684003 |Clinical finding| : 363698007 |Finding site| = 113331007 |Structure of endocrine system|"));
        
        // Test wildcard attribute
        assertCodesEqual("[362969004]", 
                        getCodes("< 404684003 |Clinical finding| : * = 113331007 |Structure of endocrine system|"));
    }
}
```

#### 9.1.2 Performance Testing Framework
```java
@Test
void testSearchPerformance() throws IOException {
    StopWatch stopWatch = new StopWatch();
    
    // Test term search performance
    stopWatch.start("term-search");
    ValueSet result = valueSetService.expand("http://snomed.info/sct?fhir_vs=ecl/*", 
                                           "diabetes", languageDialects, false, 0, 20);
    stopWatch.stop();
    
    // Test ECL search performance
    stopWatch.start("ecl-search");
    ValueSet eclResult = valueSetService.expand("http://snomed.info/sct?fhir_vs=ecl/<< 73211009", 
                                              null, languageDialects, false, 0, 100);
    stopWatch.stop();
    
    // Assert performance requirements
    assertThat(stopWatch.getLastTaskTimeMillis()).isLessThan(200);
    assertThat(result.getExpansion().getTotal()).isGreaterThan(0);
    assertThat(eclResult.getExpansion().getTotal()).isGreaterThan(0);
}
```

### 9.2 Integration Testing

#### 9.2.1 FHIR Compliance Testing
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FHIRComplianceTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testCodeSystemLookupOperation() {
        // Test FHIR CodeSystem $lookup operation
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", "73211009");
        params.add("system", "http://snomed.info/sct");
        
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/fhir/CodeSystem/$lookup", params, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/fhir+json");
        
        // Validate FHIR Parameters response structure
        Parameters parameters = fhirContext.newJsonParser().parseResource(Parameters.class, response.getBody());
        assertThat(parameters.getParameter("code")).isNotNull();
        assertThat(parameters.getParameter("display")).isNotNull();
    }
    
    @Test
    void testValueSetExpansionOperation() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("url", "http://snomed.info/sct?fhir_vs=ecl/<< 73211009");
        params.add("count", "10");
        
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/fhir/ValueSet/$expand", params, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        ValueSet valueSet = fhirContext.newJsonParser().parseResource(ValueSet.class, response.getBody());
        assertThat(valueSet.getExpansion().getContains()).isNotEmpty();
        assertThat(valueSet.getExpansion().getContains().size()).isLessThanOrEqualTo(10);
    }
}
```

---

## 10. Conclusion

This technical design document provides a comprehensive overview of the Snowstorm Lite FHIR Terminology Server architecture. The design prioritizes:

- **Performance**: Optimized Lucene indexing and search strategies
- **Memory Efficiency**: Minimal footprint design suitable for resource-constrained environments
- **FHIR Compliance**: Full adherence to FHIR R4 terminology operations
- **Scalability**: Stateless design enabling horizontal scaling
- **Maintainability**: Clean architecture with separation of concerns

The implementation leverages proven technologies (Spring Boot, Apache Lucene, HAPI FHIR) while providing specialized optimizations for SNOMED CT terminology services. The modular design ensures maintainability and extensibility for future enhancements while meeting the specific requirements of lightweight, high-performance terminology services.