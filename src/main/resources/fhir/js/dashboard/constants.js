export const AJAX_TIMEOUT_MS = 60000;
export const SYNDICATION_TIMEOUT_MS = 10000;
/** FHIR `PublicationStatus` values, shared by every resource's Add form (ConceptMap, ValueSet, ...). */
export const FHIR_PUBLICATION_STATUSES = ['draft', 'active', 'retired', 'unknown'];
export const CONCEPTMAP_DEFAULT_URL_PREFIX = 'http://example.com/fhir/ConceptMap/';
export const VALUESET_DEFAULT_URL_PREFIX = 'http://example.com/fhir/ValueSet/';
export const VALUESET_DEFAULT_SYSTEM = 'http://snomed.info/sct';
/** Criteria types supported by Snowstorm Lite ValueSet compose (maps to filter property/op or explicit concepts). */
export const VALUESET_CRITERIA_TYPES = [
	{
		id: 'constraint',
		label: 'ECL expression',
		property: 'constraint',
		op: '=',
		valuePlaceholder: '<< 404684003 |Clinical finding|',
		multiline: true
	},
	{
		id: 'is-a',
		label: 'Is-a (include self)',
		property: 'concept',
		op: 'is-a',
		valuePlaceholder: '404684003',
		multiline: false
	},
	{
		id: 'descendent-of',
		label: 'Descendent-of',
		property: 'concept',
		op: 'descendent-of',
		valuePlaceholder: '404684003',
		multiline: false
	},
	{
		id: 'in',
		label: 'Refset membership',
		property: 'concept',
		op: 'in',
		valuePlaceholder: '900000000000455006',
		multiline: false
	},
	{
		id: 'concepts',
		label: 'Enumerated list of concept codes',
		valuePlaceholder: 'One code per line or comma-separated',
		multiline: true
	},
	{
		id: 'parent',
		label: 'Direct children',
		property: 'parent',
		op: '=',
		valuePlaceholder: '404684003',
		multiline: false
	},
	{
		id: 'constraint-not',
		label: 'ECL expression (not)',
		property: 'constraint',
		op: '!=',
		valuePlaceholder: '<< 404684003 |Clinical finding|',
		multiline: true
	},

];
/** Default `group.source` when the uploaded ConceptMap omits it (placeholder URI). */
export const CONCEPTMAP_DEFAULT_GROUP_SOURCE = 'http://example.com/fhir/CodeSystem/local-finding-codes';
/** Must match {@code FHIRConstants.CODE_SYSTEM_AVAILABLE_CONTENT_LANGUAGES_EXTENSION} (single CodeSystem read). */
export const CODESYSTEM_AVAILABLE_CONTENT_LANGUAGES_EXTENSION =
	'http://snomed.info/fhir/StructureDefinition/codesystem-availableLanguages';
