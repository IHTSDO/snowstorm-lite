package org.snomed.snowstormlite.service;

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
import org.snomed.snowstormlite.domain.Concepts;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.FHIRDescription;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSet;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSetCompose;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSetCriteria;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSetFilter;
import org.snomed.snowstormlite.fhir.FHIRConstants;
import org.snomed.snowstormlite.fhir.FHIRHelper;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public FHIRValueSet find(String url, String version) throws IOException {
		return valueSetRepository.findValueSet(url, version);
	}

	public FHIRValueSet findById(String id) throws IOException {
		return valueSetRepository.findValueSetById(id);
	}

	public List<FHIRValueSet> findAll() throws IOException {
		return valueSetRepository.findAll();
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
		if (existingVS == null) {
			existingVS = new FHIRValueSet();
			existingVS.setUrl(valueSetUpdate.getUrl());
			existingVS.setVersion(valueSetUpdate.getVersion());
			existingVS.setId(valueSetUpdate.getId());
		}
		if (existingVS.getId() == null) {
			existingVS.setId(UUID.randomUUID().toString());
		}
		existingVS.setName(valueSetUpdate.getName());
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
			if (url.endsWith("?fhir_vs")) {
				// Return all of SNOMED CT in this situation
				name = "SNOMED CT Implicit ValueSet of all concepts.";
				filter = new FHIRValueSetFilter("constraint", "=", "*");
			} else if (url.contains(IMPLICIT_ISA)) {
				name = "SNOMED CT Implicit ValueSet of concepts of a specific type.";
				String sctId = url.substring(url.indexOf(IMPLICIT_ISA) + IMPLICIT_ISA.length());
				filter = new FHIRValueSetFilter("concept", "is-a", sctId);
			} else if (url.contains(IMPLICIT_REFSET)) {
				name = "SNOMED CT Implicit ValueSet using members of a Reference Set.";
				String sctId = url.substring(url.indexOf(IMPLICIT_REFSET) + IMPLICIT_REFSET.length());
				filter = new FHIRValueSetFilter("concept", "in", sctId);
			} else if (url.contains(IMPLICIT_ECL)) {
				name = "SNOMED CT Implicit ValueSet using ECL query.";
				String ecl = url.substring(url.indexOf(IMPLICIT_ECL) + IMPLICIT_ECL.length());
				ecl = URLDecoder.decode(ecl, StandardCharsets.UTF_8);
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

	public ValueSet expand(String url, String termFilter, boolean includeDesignations, int offset, int count) throws IOException {
		ValueSet valueSet = createSnomedImplicitValueSet(url);
		return expand(new FHIRValueSet(valueSet), termFilter, includeDesignations, offset, count);
	}

	public ValueSet expand(FHIRValueSet internalValueSet, String termFilter, boolean includeDesignations, int offset, int count) throws IOException {
		IndexSearcher indexSearcher = indexIOProvider.getIndexSearcher();

		BooleanQuery.Builder valueSetExpandQuery = getValueSetExpandQuery(internalValueSet);
		if (termFilter != null && !termFilter.isBlank()) {
			addTermQuery(valueSetExpandQuery, termFilter);
		}
		BooleanQuery query = valueSetExpandQuery.build();
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

		for (FHIRConcept concept : conceptPage) {
			ValueSet.ValueSetExpansionContainsComponent component = new ValueSet.ValueSetExpansionContainsComponent()
					.setSystem(SNOMED_URI)
					.setCode(concept.getConceptId())
					.setDisplay(concept.getPT());
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
		return valueSet;
	}

	private BooleanQuery.@NotNull Builder getValueSetExpandQuery(FHIRValueSet valueSet) throws IOException {
		BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
		queryBuilder.add(new TermQuery(new Term(QueryHelper.TYPE, FHIRConcept.DOC_TYPE)), BooleanClause.Occur.MUST);

		FHIRValueSetCompose compose = valueSet.getCompose();
		for (FHIRValueSetCriteria include : orEmpty(compose.getInclude())) {
			BooleanQuery.Builder criteriaBuilder = getCriteriaQuery(include);
			queryBuilder.add(criteriaBuilder.build(), BooleanClause.Occur.MUST);
		}
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

	private void addTermQuery(BooleanQuery.Builder queryBuilder, String termFilter) {
		if (SnomedIdentifierHelper.isConceptId(termFilter)) {
			queryBuilder.add(new TermQuery(new Term(FHIRConcept.FieldNames.ID, termFilter)), BooleanClause.Occur.MUST);
			return;
		}
		boolean fuzzy = termFilter.lastIndexOf("~") == termFilter.length() - 1;
		if (fuzzy) {
			termFilter = termFilter.substring(0, termFilter.length() - 1);
		}

		List<String> searchTokens = analyze(termFilter);
		for (String searchToken : searchTokens) {
			if (fuzzy) {
				queryBuilder.add(new FuzzyQuery(new Term(FHIRConcept.FieldNames.TERM, searchToken)), BooleanClause.Occur.MUST);
			} else {
				queryBuilder.add(new WildcardQuery(new Term(FHIRConcept.FieldNames.TERM, searchToken + "*")), BooleanClause.Occur.MUST);
			}
		}
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
