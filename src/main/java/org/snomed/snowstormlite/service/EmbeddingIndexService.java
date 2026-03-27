package org.snomed.snowstormlite.service;

import org.apache.lucene.document.*;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.*;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.snomed.snowstormlite.fhir.FHIRHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static java.lang.String.format;

@Service
@ConditionalOnProperty(name = "snowstorm.embeddings.enabled", havingValue = "true")
public class EmbeddingIndexService {

	public static final String DEFAULT_MODEL_ID = "default";
	private static final String DOC_TYPE = "concept_embedding";
	private static final String MODEL_ID = "embedding_model";
	private static final String CONCEPT_ID = "concept_id";
	private static final String VECTOR_FIELD_PREFIX = "embedding_vector_";
	private static final byte[] BINARY_MAGIC = new byte[]{'S', 'L', 'E', '1'};
	private static final int MAX_VECTOR_DIMENSION = 4096;
	private static final int MAX_BINARY_BATCH_SIZE = 200_000;

	@Autowired
	private IndexIOProvider indexIOProvider;

	public record EmbeddingInput(String conceptId, float[] vector) {}

	public record SemanticMatch(String conceptId, float score) {}

	public record SemanticSearchResult(long totalHits, List<SemanticMatch> matches) {}

	public synchronized int indexEmbeddings(String modelId, List<EmbeddingInput> embeddings, boolean replaceModel) throws IOException {
		String resolvedModelId = resolveModelId(modelId);
		if (embeddings == null || embeddings.isEmpty()) {
			throw FHIRHelper.exception("At least one embedding must be supplied.", OperationOutcome.IssueType.REQUIRED, 400);
		}

		Map<String, float[]> vectorsByConcept = new LinkedHashMap<>();
		int dimension = -1;
		for (EmbeddingInput embedding : embeddings) {
			if (embedding == null || embedding.conceptId() == null || embedding.conceptId().isBlank()) {
				throw FHIRHelper.exception("Each embedding must include a concept id.", OperationOutcome.IssueType.INVALID, 400);
			}
			if (embedding.vector() == null || embedding.vector().length == 0) {
				throw FHIRHelper.exception(
						format("Embedding for concept '%s' must have at least one dimension.", embedding.conceptId()),
						OperationOutcome.IssueType.INVALID,
						400);
			}
			if (dimension == -1) {
				dimension = embedding.vector().length;
			} else if (dimension != embedding.vector().length) {
				throw FHIRHelper.exception(
						format("All embeddings in a batch must have the same vector size. Found %s and %s.", dimension, embedding.vector().length),
						OperationOutcome.IssueType.INVALID,
						400);
			}
			vectorsByConcept.put(embedding.conceptId(), embedding.vector());
		}

		BooleanQuery.Builder deleteQueryBuilder = new BooleanQuery.Builder()
				.add(new TermQuery(new Term(QueryHelper.TYPE, DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(new TermQuery(new Term(MODEL_ID, resolvedModelId)), BooleanClause.Occur.MUST);

		if (!replaceModel) {
			deleteQueryBuilder.add(QueryHelper.termsQuery(CONCEPT_ID, vectorsByConcept.keySet()), BooleanClause.Occur.MUST);
		}
		indexIOProvider.deleteDocuments(deleteQueryBuilder.build());

		String vectorFieldName = getVectorFieldName(resolvedModelId);
		List<Document> docs = new ArrayList<>();
		for (Map.Entry<String, float[]> entry : vectorsByConcept.entrySet()) {
			Document embeddingDoc = new Document();
			embeddingDoc.add(new StringField(QueryHelper.TYPE, DOC_TYPE, Field.Store.NO));
			embeddingDoc.add(new StringField(MODEL_ID, resolvedModelId, Field.Store.NO));
			embeddingDoc.add(new StringField(CONCEPT_ID, entry.getKey(), Field.Store.YES));
			embeddingDoc.add(new KnnFloatVectorField(vectorFieldName, entry.getValue(), VectorSimilarityFunction.COSINE));
			docs.add(embeddingDoc);
		}

		try {
			indexIOProvider.writeDocuments(docs);
		} catch (IllegalArgumentException e) {
			throw FHIRHelper.exception(
					format("Embedding dimension mismatch for model '%s'. If dimensions changed, reindex with replace=true.", resolvedModelId),
					OperationOutcome.IssueType.INVALID,
					400,
					e);
		}
		return docs.size();
	}

	/*
	 Binary payload format (little-endian):
	 - 4 bytes magic: "SLE1"
	 - int32 vector dimension
	 - int32 record count
	 - repeated records:
	     - uint64 concept id
	     - float32[dimension] embedding vector
	 */
	public List<EmbeddingInput> parseBinaryEmbeddings(InputStream inputStream) {
		if (inputStream == null) {
			throw FHIRHelper.exception("Binary embedding payload is required.", OperationOutcome.IssueType.REQUIRED, 400);
		}

		try (DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(inputStream))) {
			byte[] magic = new byte[BINARY_MAGIC.length];
			try {
				dataInputStream.readFully(magic);
			} catch (EOFException e) {
				throw FHIRHelper.exception("Binary payload is too short. Expected header 'SLE1'.", OperationOutcome.IssueType.INVALID, 400, e);
			}
			if (!Arrays.equals(magic, BINARY_MAGIC)) {
				throw FHIRHelper.exception("Binary payload must start with header 'SLE1'.", OperationOutcome.IssueType.INVALID, 400);
			}

			int dimension = readIntLittleEndian(dataInputStream, "dimension");
			int count = readIntLittleEndian(dataInputStream, "count");
			if (dimension <= 0) {
				throw FHIRHelper.exception("Binary payload 'dimension' must be greater than zero.", OperationOutcome.IssueType.INVALID, 400);
			}
			if (dimension > MAX_VECTOR_DIMENSION) {
				throw FHIRHelper.exception(
						format("Binary payload 'dimension' exceeds the maximum of %s.", MAX_VECTOR_DIMENSION),
						OperationOutcome.IssueType.INVALID,
						400);
			}
			if (count <= 0) {
				throw FHIRHelper.exception("Binary payload 'count' must be greater than zero.", OperationOutcome.IssueType.INVALID, 400);
			}
			if (count > MAX_BINARY_BATCH_SIZE) {
				throw FHIRHelper.exception(
						format("Binary payload 'count' exceeds the maximum of %s records.", MAX_BINARY_BATCH_SIZE),
						OperationOutcome.IssueType.INVALID,
						400);
			}

			List<EmbeddingInput> embeddings = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				long conceptId = readLongLittleEndian(dataInputStream, format("concept id at record %s", i));
				if (conceptId <= 0) {
					throw FHIRHelper.exception(
							format("Binary payload concept id at record %s must be greater than zero.", i),
							OperationOutcome.IssueType.INVALID,
							400);
				}
				float[] vector = new float[dimension];
				for (int d = 0; d < dimension; d++) {
					vector[d] = Float.intBitsToFloat(readIntLittleEndian(dataInputStream, format("vector value at record %s dimension %s", i, d)));
				}
				embeddings.add(new EmbeddingInput(Long.toString(conceptId), vector));
			}

			if (dataInputStream.read() != -1) {
				throw FHIRHelper.exception("Binary payload contains trailing bytes after declared records.", OperationOutcome.IssueType.INVALID, 400);
			}
			return embeddings;
		} catch (IOException e) {
			throw FHIRHelper.exception("Unable to read binary embedding payload.", OperationOutcome.IssueType.INVALID, 400, e);
		}
	}

	public SemanticSearchResult semanticSearch(String modelId, float[] queryVector, Set<String> conceptIdFilter, int count) throws IOException {
		String resolvedModelId = resolveModelId(modelId);
		if (queryVector == null || queryVector.length == 0) {
			throw FHIRHelper.exception("A semantic query vector is required.", OperationOutcome.IssueType.REQUIRED, 400);
		}
		if (conceptIdFilter == null || conceptIdFilter.isEmpty() || count <= 0) {
			return new SemanticSearchResult(0, List.of());
		}

		BooleanQuery filter = new BooleanQuery.Builder()
				.add(new TermQuery(new Term(QueryHelper.TYPE, DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(new TermQuery(new Term(MODEL_ID, resolvedModelId)), BooleanClause.Occur.MUST)
				.add(QueryHelper.termsQuery(CONCEPT_ID, conceptIdFilter), BooleanClause.Occur.MUST)
				.build();

		KnnFloatVectorQuery semanticQuery = new KnnFloatVectorQuery(getVectorFieldName(resolvedModelId), queryVector, count, filter);
		TopDocs topDocs;
		try {
			topDocs = indexIOProvider.getIndexSearcher().search(semanticQuery, count);
		} catch (IllegalArgumentException e) {
			throw FHIRHelper.exception(
					format("Query vector dimension mismatch for model '%s'.", resolvedModelId),
					OperationOutcome.IssueType.INVALID,
					400,
					e);
		}

		StoredFields storedFields = indexIOProvider.getIndexSearcher().storedFields();
		List<SemanticMatch> matches = new ArrayList<>();
		for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
			String conceptId = storedFields.document(scoreDoc.doc).get(CONCEPT_ID);
			if (conceptId != null) {
				matches.add(new SemanticMatch(conceptId, scoreDoc.score));
			}
		}
		return new SemanticSearchResult(topDocs.totalHits.value, matches);
	}

	public float[] parseVector(String vectorString, String parameterName) {
		if (vectorString == null || vectorString.isBlank()) {
			throw FHIRHelper.exception(
					format("Parameter '%s' must contain a comma-separated float vector.", parameterName),
					OperationOutcome.IssueType.REQUIRED,
					400);
		}

		String normalized = vectorString.trim();
		if (normalized.startsWith("[") && normalized.endsWith("]")) {
			normalized = normalized.substring(1, normalized.length() - 1);
		}
		normalized = normalized.trim();
		if (normalized.isEmpty()) {
			throw FHIRHelper.exception(
					format("Parameter '%s' must contain at least one float value.", parameterName),
					OperationOutcome.IssueType.INVALID,
					400);
		}

		String[] tokens = normalized.contains(",") ? normalized.split("\\s*,\\s*") : normalized.split("\\s+");
		float[] vector = new float[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			String token = tokens[i].trim();
			if (token.isEmpty()) {
				throw FHIRHelper.exception(
						format("Parameter '%s' contains an empty vector entry.", parameterName),
						OperationOutcome.IssueType.INVALID,
						400);
			}
			try {
				vector[i] = Float.parseFloat(token);
			} catch (NumberFormatException e) {
				throw FHIRHelper.exception(
						format("Parameter '%s' contains non-numeric value '%s'.", parameterName, token),
						OperationOutcome.IssueType.INVALID,
						400,
						e);
			}
		}
		return vector;
	}

	public String resolveModelId(String modelId) {
		String resolvedModelId = modelId == null || modelId.isBlank() ? DEFAULT_MODEL_ID : modelId.trim();
		if (resolvedModelId.length() > 100) {
			throw FHIRHelper.exception("Model id must be 100 characters or less.", OperationOutcome.IssueType.INVALID, 400);
		}
		return resolvedModelId;
	}

	private String getVectorFieldName(String modelId) {
		String cleaned = modelId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
		if (cleaned.isBlank()) {
			cleaned = DEFAULT_MODEL_ID;
		}
		return VECTOR_FIELD_PREFIX + cleaned + "_" + Integer.toHexString(modelId.hashCode());
	}

	private int readIntLittleEndian(DataInputStream dataInputStream, String fieldName) throws IOException {
		try {
			return Integer.reverseBytes(dataInputStream.readInt());
		} catch (EOFException e) {
			throw FHIRHelper.exception(
					format("Binary payload ended while reading %s.", fieldName),
					OperationOutcome.IssueType.INVALID,
					400,
					e);
		}
	}

	private long readLongLittleEndian(DataInputStream dataInputStream, String fieldName) throws IOException {
		try {
			return Long.reverseBytes(dataInputStream.readLong());
		} catch (EOFException e) {
			throw FHIRHelper.exception(
					format("Binary payload ended while reading %s.", fieldName),
					OperationOutcome.IssueType.INVALID,
					400,
					e);
		}
	}
}
