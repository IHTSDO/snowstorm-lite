package org.snomed.snowstormlite.service;

import info.debatty.java.stringsimilarity.Levenshtein;
import org.apache.logging.log4j.util.Strings;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.config.LanguageCharacterFoldingConfiguration;
import org.snomed.snowstormlite.domain.*;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSet;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSetCompose;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSetCriteria;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSetFilter;
import org.snomed.snowstormlite.fhir.FHIRConstants;
import org.snomed.snowstormlite.fhir.FHIRHelper;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRConstants.*;
import static org.snomed.snowstormlite.fhir.FHIRHelper.exception;
import static org.snomed.snowstormlite.fhir.FHIRHelper.mutuallyExclusive;
import static org.snomed.snowstormlite.util.CollectionUtils.orEmpty;

@Service
public class ValueSetService {

	// Constant to help with "?fhir_vs=refset"
	public static final String REFSETS_WITH_MEMBERS = "Refsets";

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private ExpressionConstraintLanguageService eclService;

	@Autowired
	private IndexIOProvider indexIOProvider;

	@Autowired
	private ValueSetRepository valueSetRepository;

	@Autowired
	private LanguageCharacterFoldingConfiguration languageCharacterFoldingConfiguration;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public FHIRValueSet find(String url, String version) throws IOException {
		return valueSetRepository.findValueSet(url, version);
	}

	public FHIRValueSet findById(String id) throws IOException {
		return valueSetRepository.findValueSetById(id);
	}

	public List<FHIRValueSet> findAll() throws IOException {
		return valueSetRepository.findAll().stream()
				.sorted(Comparator.comparing(FHIRValueSet::getName, Comparator.nullsFirst(String::compareTo))
						.thenComparing(FHIRValueSet::getUrl)
						.thenComparing(FHIRValueSet::getVersion, Comparator.nullsFirst(Comparator.reverseOrder())))
				.toList();
	}

	public synchronized FHIRValueSet createOrUpdateValueset(ValueSet valueSetUpdate) throws IOException {
		FHIRValueSet internalValueSet = new FHIRValueSet();
		if (Strings.isBlank(valueSetUpdate.getUrl()) || Strings.isBlank(valueSetUpdate.getVersion())) {
			throw FHIRHelper.exception("ValueSet url and version are mandatory", OperationOutcome.IssueType.INVARIANT, 400);
		}
		if (valueSetUpdate.getId() != null && valueSetUpdate.getId().startsWith("ValueSet/")) {
			valueSetUpdate.setId(valueSetUpdate.getId().replace("ValueSet/", ""));
		}
		valueSetUpdate.setExpansion(null);
		ValueSet.ValueSetComposeComponent compose = valueSetUpdate.getCompose();
		for (ValueSet.ConceptSetComponent include : orEmpty(compose.getInclude())) {
			FHIRHelper.assertEqualOrThrowNotSupported("http://snomed.info/sct", include.getSystem(), "All ValueSet compose items must use the system 'http://snomed.info/sct'");
			FHIRHelper.assertTrueOrThrowNotSupported(include.getValueSet().isEmpty(), "Nested ValueSets are not supported.");
		}
		for (ValueSet.ConceptSetComponent exclude : orEmpty(compose.getExclude())) {
			FHIRHelper.assertEqualOrThrowNotSupported("http://snomed.info/sct", exclude.getSystem(), "All ValueSet compose items must use the system 'http://snomed.info/sct'");
			FHIRHelper.assertTrueOrThrowNotSupported(exclude.getValueSet().isEmpty(), "Nested ValueSets are not supported.");
		}

		// Attempt find existing by url and version
		FHIRValueSet existingVS = valueSetRepository.findValueSet(valueSetUpdate.getUrl(), valueSetUpdate.getVersion());
		if (existingVS != null) {
			// Found
			if (valueSetUpdate.getId() != null && !valueSetUpdate.getId().equals(existingVS.getId())) {
				throw FHIRHelper.exception("A ValueSet with the same url and version already exists with a different id.", OperationOutcome.IssueType.INVARIANT, 400);
			}
		} else {
			if (valueSetUpdate.getId() != null) {
				// Attempt find existing by id
				existingVS = valueSetRepository.findValueSetById(valueSetUpdate.getId());
				if (existingVS != null) {
					throw exception("A ValueSet with the same id already exists with a different url and version.", OperationOutcome.IssueType.INVARIANT, 400);
				}
			}
		}
		if (existingVS != null) {
			logger.info("Updating ValueSet URL:'{}', version:'{}'", valueSetUpdate.getUrl(), valueSetUpdate.getVersion());
		} else {
			logger.info("Creating ValueSet URL:'{}', version:'{}'", valueSetUpdate.getUrl(), valueSetUpdate.getVersion());
			existingVS = new FHIRValueSet();
			existingVS.setUrl(valueSetUpdate.getUrl());
			existingVS.setVersion(valueSetUpdate.getVersion());
			existingVS.setId(valueSetUpdate.getId());
		}
		if (existingVS.getId() == null) {
			existingVS.setId(UUID.randomUUID().toString());
		}
		existingVS.setName(valueSetUpdate.getName());
		existingVS.setTitle(valueSetUpdate.getTitle());
		if (valueSetUpdate.getStatus() != null) {
			existingVS.setStatus(valueSetUpdate.getStatus().toString());
		} else {
			existingVS.setStatus(null);
		}
		if (valueSetUpdate.hasExperimental()) {
			existingVS.setExperimental(valueSetUpdate.getExperimental());
		}
		existingVS.setDescription(valueSetUpdate.getDescription());
		FHIRValueSet composeUpdate = new FHIRValueSet(valueSetUpdate);
		existingVS.setCompose(composeUpdate.getCompose());

		valueSetRepository.save(existingVS);

		return internalValueSet;
	}

	public ValueSet findOrInferValueSet(String id, String url, ValueSet hapiValueSet) throws IOException {
		mutuallyExclusive("id", id, "url", url);
		mutuallyExclusive("id", id, "valueSet", hapiValueSet);
		mutuallyExclusive("url", url, "valueSet", hapiValueSet);

		if (id != null) {
			FHIRValueSet valueSet = valueSetRepository.findValueSetById(id);
			if (valueSet == null) {
				return null;
			}
			idUrlCrosscheck(id, url, valueSet);

			hapiValueSet = valueSet.toHapi();
		} else if (FHIRHelper.isSnomedUri(url) && url.contains("?fhir_vs")) {
			// Create snomed implicit value set
			hapiValueSet = createSnomedImplicitValueSet(url);
		} else if (hapiValueSet == null) {
			FHIRValueSet valueSet = valueSetRepository.findValueSet(url, null);
			if (valueSet != null) {
				hapiValueSet = 	valueSet.toHapi();
			}
		}
		return hapiValueSet;
	}

	/*
	 See https://www.hl7.org/fhir/snomedct.html#implicit
 	*/
	public ValueSet createSnomedImplicitValueSet(String url) {
		FHIRValueSetCriteria includeCriteria = new FHIRValueSetCriteria();
		includeCriteria.setSystem(url.startsWith(SNOMED_URI_UNVERSIONED) ? SNOMED_URI_UNVERSIONED : SNOMED_URI);
		String urlWithoutParams = url.substring(0, url.indexOf("?"));
		if (!urlWithoutParams.equals(includeCriteria.getSystem())) {
			includeCriteria.setVersion(urlWithoutParams);
		}

		FHIRValueSetFilter filter;
		String name;
		// Are we looking for all known refsets? Special case.
		if (url.endsWith("?fhir_vs=refset")) {
			name = "SNOMED CT Implicit ValueSet of all Reference Sets.";
			filter = new FHIRValueSetFilter("constraint", "=", REFSETS_WITH_MEMBERS);
		} else {
			if (url.endsWith(IMPLICIT_EVERYTHING)) {
				// Return all of SNOMED CT in this situation
				name = "SNOMED CT Implicit ValueSet of all concepts.";
				filter = new FHIRValueSetFilter("constraint", "=", "*");
			} else if (url.contains(IMPLICIT_ISA)) {
				name = "SNOMED CT Implicit ValueSet of concepts of a specific type.";
				String sctId = url.substring(url.indexOf(IMPLICIT_ISA) + IMPLICIT_ISA.length());
				filter = new FHIRValueSetFilter("concept", "is-a", sctId);
			} else if (url.contains(IMPLICIT_DESCENDANT_OF)) {
				name = "SNOMED CT Implicit ValueSet of concepts of a specific type, excluding the concept itself.";
				String sctId = url.substring(url.indexOf(IMPLICIT_DESCENDANT_OF) + IMPLICIT_DESCENDANT_OF.length());
				filter = new FHIRValueSetFilter("concept", "descendent-of", sctId);
			} else if (url.contains(IMPLICIT_REFSET)) {
				name = "SNOMED CT Implicit ValueSet using members of a Reference Set.";
				String sctId = url.substring(url.indexOf(IMPLICIT_REFSET) + IMPLICIT_REFSET.length());
				filter = new FHIRValueSetFilter("concept", "in", sctId);
			} else if (url.contains(IMPLICIT_ECL)) {
				name = "SNOMED CT Implicit ValueSet using ECL query.";
				String ecl = url.substring(url.indexOf(IMPLICIT_ECL) + IMPLICIT_ECL.length());
				filter = new FHIRValueSetFilter("constraint", "=", ecl);
			} else {
				throw exception("url is expected to include parameter with value: 'fhir_vs=ecl/'", OperationOutcome.IssueType.VALUE, 400);
			}
		}
		includeCriteria.setFilter(Collections.singletonList(filter));
		FHIRValueSetCompose compose = new FHIRValueSetCompose();
		compose.addInclude(includeCriteria);
		FHIRValueSet valueSet = new FHIRValueSet();
		valueSet.setName(name);
		valueSet.setUrl(url);
		valueSet.setCompose(compose);
		valueSet.setStatus(Enumerations.PublicationStatus.ACTIVE.toCode());
		return valueSet.toHapi();
	}

	public ValueSet expand(String url, String termFilter, List<LanguageDialect> displayLanguages,
						   boolean includeDesignations, int offset, int count) throws IOException {

		ValueSet valueSet = createSnomedImplicitValueSet(url);
		return expand(new FHIRValueSet(valueSet), termFilter, displayLanguages, includeDesignations, Collections.emptyList(), offset, count, null).getFirst();
	}

	public Pair<ValueSet, List<FHIRConcept>> expand(FHIRValueSet internalValueSet, String termFilter, List<LanguageDialect> displayLanguages,
						   boolean includeDesignations, List<String> requestedProperties, int offset, int count, Set<Coding> codingsToValidate) throws IOException {

		int originalCount = count;
		int originalOffset = offset;

		// Apply additional sorting for the first 100 results
		boolean additionalSorting = offset < 100;
		if (additionalSorting) {
			offset = 0;
			count = originalOffset + originalCount;
			if (count < 100) {
				count = 100;
			}
		}

		IndexSearcher indexSearcher = indexIOProvider.getIndexSearcher();
		BooleanQuery.Builder valueSetExpandQuery = getValueSetExpandQuery(internalValueSet);

		if (codingsToValidate != null) {
			Set<String> codes = codingsToValidate.stream().filter(coding -> SNOMED_URI.equals(coding.getSystem())).map(Coding::getCode).collect(Collectors.toSet());
			valueSetExpandQuery.add(QueryHelper.termsQuery(FHIRConcept.FieldNames.ID, codes), BooleanClause.Occur.MUST);
		}

		Function<FHIRDescription, Boolean> termMatcher = null;
		if (termFilter != null && !termFilter.isBlank()) {
			termMatcher = addTermQuery(termFilter, displayLanguages, valueSetExpandQuery);
		}
		Query query = valueSetExpandQuery.build();
		Sort sort = new Sort(
				new SortedNumericSortField(FHIRConcept.FieldNames.ACTIVE_SORT, SortField.Type.INT, true),
				new SortedNumericSortField(FHIRConcept.FieldNames.PT_AND_FSN_TERM_LENGTH, SortField.Type.INT),
				SortField.FIELD_SCORE);
		TopDocs queryResult = indexSearcher.search(query, offset + count, sort, true);

		List<ValueSet.ValueSetExpansionContainsComponent> contains = new ArrayList<>();
		int offsetReached = 0;

		List<FHIRConcept> conceptPage = new ArrayList<>();
		StoredFields storedFields = indexSearcher.storedFields();
		for (ScoreDoc scoreDoc : queryResult.scoreDocs) {
			if (offsetReached < offset) {
				offsetReached++;
				continue;
			}
			FHIRConcept concept = codeSystemRepository.getConceptFromDoc(storedFields.document(scoreDoc.doc));
			conceptPage.add(concept);
			if (conceptPage.size() == count) {
				break;
			}
		}

		// Sort again by shortest matching term
		if (additionalSorting && termMatcher != null) {
			Map<FHIRDescription, FHIRConcept> termToConceptMap = new HashMap<>();
			Comparator<FHIRDescription> descriptionComparator = Comparator
					.comparingInt(FHIRDescription::getTermLength)
					.thenComparing(FHIRDescription::getTerm)
					.thenComparing(d -> d.getConcept().getPT(displayLanguages));

			for (FHIRConcept concept : conceptPage) {
				concept.getDescriptions().forEach(d -> d.setConcept(concept));
				Optional<FHIRDescription> shortest = concept.getDescriptions().stream()
                        .filter(termMatcher::apply)
						.min(descriptionComparator);
                shortest.ifPresent(fhirDescription -> termToConceptMap.put(fhirDescription, concept));
			}
			List<FHIRDescription> allConceptShortestTerms = new ArrayList<>(termToConceptMap.keySet());
			allConceptShortestTerms.sort(descriptionComparator);
			conceptPage = allConceptShortestTerms.stream().map(termToConceptMap::get).toList();
		}

		if (additionalSorting) {
			int a = conceptPage.size();
			int b = originalOffset + originalCount;
			int min = Math.min(a, b);
			if (min > 0 && originalOffset < min) {
				conceptPage = conceptPage.subList(originalOffset, min);
			} else {
				conceptPage = new ArrayList<>();
			}
		}

		for (FHIRConcept concept : conceptPage) {
			ValueSet.ValueSetExpansionContainsComponent component = new ValueSet.ValueSetExpansionContainsComponent()
					.setSystem(SNOMED_URI)
					.setCode(concept.getConceptId())
					.setDisplay(concept.getPT(displayLanguages));
			if (!concept.isActive()) {
				component.setInactive(true);
			}
			if (includeDesignations) {
				for (FHIRDescription description : concept.getDescriptions()) {
					boolean fsn = description.isFsn();
					component.addDesignation()
							.setLanguageElement(new CodeType(description.getLang()))
							.setUse(new Coding(SNOMED_URI, fsn ? Concepts.FSN : Concepts.SYNONYM, fsn ? "Fully specified name" : "Synonym"))
							.setValue(description.getTerm());
				}
			}
			if (requestedProperties.contains("inactive")) {
				Extension extension = component.addExtension().setUrl("http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property");
				extension.addExtension("code", new CodeType("inactive"));
				extension.addExtension("value", new BooleanType(!concept.isActive()));
			}
			if (requestedProperties.contains("parent")) {
				for (String parentCode : concept.getParentCodes()) {
					Extension extension = component.addExtension().setUrl("http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property");
					extension.addExtension("code", new CodeType("parent"));
					extension.addExtension("value", new CodeType(parentCode));
				}
			}
			if (requestedProperties.contains("sufficientlyDefined")) {
				Extension extension = component.addExtension().setUrl("http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property");
				extension.addExtension("code", new CodeType("sufficientlyDefined"));
				extension.addExtension("value", new BooleanType(concept.isDefined()));
			}
			contains.add(component);
		}

		ValueSet valueSet = internalValueSet.toHapi();
		valueSet.setCompose(null);
		valueSet.setCopyright(FHIRConstants.SNOMED_VALUESET_COPYRIGHT);
		ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
		expansion.setIdentifier(UUID.randomUUID().toString());
		expansion.setTimestamp(new Date());
		expansion.setTotal((int) queryResult.totalHits.value);
		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("version")).setValue(new UriType(codeSystem.getSystemAndVersionUri())));
		expansion.setContains(contains);
		valueSet.setExpansion(expansion);
		return Pair.of(valueSet, conceptPage);
	}

	private BooleanQuery.@NotNull Builder getValueSetExpandQuery(FHIRValueSet valueSet) throws IOException {
		BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
		queryBuilder.add(new TermQuery(new Term(QueryHelper.TYPE, FHIRConcept.DOC_TYPE)), BooleanClause.Occur.MUST);

		FHIRValueSetCompose compose = valueSet.getCompose();
		BooleanQuery.Builder orBuilder = new BooleanQuery.Builder();
		for (FHIRValueSetCriteria include : orEmpty(compose.getInclude())) {
			BooleanQuery.Builder criteriaBuilder = getCriteriaQuery(include);
			orBuilder.add(criteriaBuilder.build(), BooleanClause.Occur.SHOULD);
		}
		queryBuilder.add(orBuilder.build(), BooleanClause.Occur.MUST);
		for (FHIRValueSetCriteria exclude : orEmpty(compose.getExclude())) {
			BooleanQuery.Builder criteriaBuilder = getCriteriaQuery(exclude);
			queryBuilder.add(criteriaBuilder.build(), BooleanClause.Occur.MUST_NOT);
		}

		return queryBuilder;
	}

	@NotNull
	private BooleanQuery.Builder getCriteriaQuery(FHIRValueSetCriteria includeOrExcludeCriteria) throws IOException {
		BooleanQuery.Builder criteriaBuilder = new BooleanQuery.Builder();
		List<String> codes = orEmpty(includeOrExcludeCriteria.getCodes());
		if (!codes.isEmpty()) {
			criteriaBuilder.add(QueryHelper.termsQuery(FHIRConcept.FieldNames.ID, codes), BooleanClause.Occur.SHOULD);
		}
		for (FHIRValueSetFilter filter : orEmpty(includeOrExcludeCriteria.getFilter())) {
			String property = filter.getProperty();
			String op = filter.getOp();
			String ecl;
			boolean includeEcl = true;
			if (("constraint".equals(property) || "expression".equals(property)) && ("=".equals(op) || "!=".equals(op))) {
				ecl = filter.getValue();
				if ("!=".equals(op)) {
					includeEcl = false;
				}
			} else if ("concept".equals(property) && "is-a".equals(op)) {
				ecl = format("<<%s", filter.getValue());
			} else if ("concept".equals(property) && "descendent-of".equals(op)) {
				ecl = format("<%s", filter.getValue());
			} else if ("parent".equals(property) && "=".equals(op)) {
				ecl = format("<!%s", filter.getValue());
			} else if ("concept".equals(property) && "in".equals(op)) {
				ecl = format("^%s", filter.getValue());
			} else {
				throw FHIRHelper.exceptionNotSupported(format("Filter with property '%s' and operator '%s' is not supported.", property, op));
			}
			BooleanQuery.Builder eclQueryBuilder = eclService.getEclConstraints(ecl);
			if (includeEcl) {
				criteriaBuilder.add(eclQueryBuilder.build(), BooleanClause.Occur.SHOULD);
			} else {
				criteriaBuilder.add(eclQueryBuilder.build(), BooleanClause.Occur.MUST_NOT);
			}
		}
		return criteriaBuilder;
	}

	public void deleteById(String id) throws IOException {
		valueSetRepository.deleteById(id);
	}

	private Function<FHIRDescription, Boolean> addTermQuery(String termFilter, List<LanguageDialect> languageDialects, BooleanQuery.Builder queryBuilder) {
		Set<String> languageCodes = languageDialects.stream().map(LanguageDialect::getLanguageCode).collect(Collectors.toSet());
		if (SnomedIdentifierHelper.isConceptId(termFilter)) {
			queryBuilder.add(new TermQuery(new Term(FHIRConcept.FieldNames.ID, termFilter)), BooleanClause.Occur.MUST);
			return null;
		}
		boolean fuzzy = termFilter.lastIndexOf("~") == termFilter.length() - 1;
		if (fuzzy) {
			termFilter = termFilter.replace("~", "");
		}

		List<String> searchTokens = analyze(termFilter);
		Map<String, List<Function<String, Boolean>>> languageResultFilterMap = new HashMap<>();
		Levenshtein levenshteinFuzzyFilter = new Levenshtein();
		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		for (String languageCode : languageCodes) {
			BooleanQuery.Builder langBuilder = new BooleanQuery.Builder();
			String termField = CodeSystemRepository.getTermField(languageCode);
			Set<Character> charactersNotFolded = languageCharacterFoldingConfiguration.getCharactersNotFolded(languageCode);
			List<Function<String, Boolean>> wordFilters = languageResultFilterMap.computeIfAbsent(languageCode, i -> new ArrayList<>());
			for (String searchWord : searchTokens) {
				String foldedSearchWord = TermSearchHelper.foldTerm(searchWord, charactersNotFolded);
				if (fuzzy) {
					langBuilder.add(new FuzzyQuery(new Term(termField, foldedSearchWord)), BooleanClause.Occur.MUST);
					wordFilters.add(word -> levenshteinFuzzyFilter.distance(searchWord, word, 2) <= 2);
				} else {
					langBuilder.add(new WildcardQuery(new Term(termField, foldedSearchWord + "*")), BooleanClause.Occur.MUST);
					Pattern pattern = Pattern.compile(format("%s.*", foldedSearchWord.toLowerCase()));
					wordFilters.add(word -> pattern.matcher(word).matches());
				}
			}
			builder.add(langBuilder.build(), BooleanClause.Occur.SHOULD);
		}
		queryBuilder.add(builder.build(), BooleanClause.Occur.MUST);

		return description -> {
			List<Function<String, Boolean>> wordFilters = languageResultFilterMap.getOrDefault(description.getLang(), Collections.emptyList());
			Set<Character> charactersNotFolded = languageCharacterFoldingConfiguration.getCharactersNotFolded(description.getLang());

			Set<String> foldedTermWords = analyze(description.getTerm()).stream()
					.map(termWord -> TermSearchHelper.foldTerm(termWord, charactersNotFolded))
					.collect(Collectors.toSet());

			for (Function<String, Boolean> wordFilter : wordFilters) {
				boolean filterMatch = false;
				for (String foldedTermWord : foldedTermWords) {
					if (wordFilter.apply(foldedTermWord)) {
						filterMatch = true;
						break;
					}
				}
				if (!filterMatch) {
					return false;
				}
			}

			return true;
		};
	}

	private void idUrlCrosscheck(String id, String url, FHIRValueSet valueSet) {
		if (url != null && !url.equals(valueSet.getUrl())) {
			throw exception(format("The requested ValueSet URL '%s' does not match the URL '%s' of the ValueSet found using identifier '%s'.",
					url, valueSet.getUrl(), id), OperationOutcome.IssueType.INVALID, 400);
		}
	}

	private List<String> analyze(String text) {
		List<String> result = new ArrayList<>();
		try (StandardAnalyzer standardAnalyzer = new StandardAnalyzer(CharArraySet.EMPTY_SET)) {
			TokenStream tokenStream = standardAnalyzer.tokenStream("contents", text);
			CharTermAttribute attr = tokenStream.addAttribute(CharTermAttribute.class);
			tokenStream.reset();
			while (tokenStream.incrementToken()) {
				result.add(attr.toString());
			}
		} catch (IOException e) {
			logger.error("Failed to analyze text {}", text, e);
		}
		return result;
	}

}
