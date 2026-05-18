export const AJAX_TIMEOUT_MS = 60000;
export const SYNDICATION_TIMEOUT_MS = 10000;
export const CONCEPT_MAP_STATUSES = ['draft', 'active', 'retired', 'unknown'];
export const CONCEPTMAP_DEFAULT_URL_PREFIX = 'http://example.com/fhir/ConceptMap/';
/** Default `group.source` when the uploaded ConceptMap omits it (placeholder URI). */
export const CONCEPTMAP_DEFAULT_GROUP_SOURCE = 'http://example.com/fhir/CodeSystem/local-finding-codes';
/** Must match {@code FHIRConstants.CODE_SYSTEM_AVAILABLE_CONTENT_LANGUAGES_EXTENSION} (single CodeSystem read). */
export const CODESYSTEM_AVAILABLE_CONTENT_LANGUAGES_EXTENSION =
	'http://snomed.info/fhir/StructureDefinition/codesystem-availableLanguages';
