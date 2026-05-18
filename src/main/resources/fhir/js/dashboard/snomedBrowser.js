import { AJAX_TIMEOUT_MS } from './constants.js';
import { fetchWithTimeout, errorMessage } from './http.js';

export const SNOMED_SYSTEM_URI = 'http://snomed.info/sct';
export const SNOMED_ROOT_CONCEPT = '138875005';

const SEARCH_PAGE_SIZE = 50;
/** Match sct-browser-frontend searchPlugin: debounced input + minimum term length before searching. */
const SNOMED_SEARCH_DEBOUNCE_MS = 500;
const SNOMED_SEARCH_MIN_CHARS = 3;
/** Chunk size for ValueSet $expand when loading all direct children (internal paging, no UI limit). */
const HIERARCHY_EXPAND_CHUNK = 8000;
/** Codes per FHIR Batch Bundle for hierarchy hints (GET CodeSystem/$lookup per entry). */
const BATCH_LOOKUP_CHUNK = 80;
/** SNOMED CT description type identifier for FSN (Concepts.FSN). */
const SNOMED_FSN_DESCRIPTION_TYPE_ID = '900000000000003001';
/** Relationship type identifier for SNOMED “Is a” (proximal parent axioms vs defining attributes). */
const SNOMED_IS_A_RELATIONSHIP = '116680003';
/** How many children to show before collapsing with “more…”. */
const SNOMED_CHILD_PREVIEW_COUNT = 6;

/** Taxonomy column resize (desktop split layout). */
const SNOMED_TAXONOMY_PANE_WIDTH_STORAGE_KEY = 'snomed-mini-taxonomy-pane-px';
const SNOMED_TAXONOMY_PANE_MIN_PX = 180;
const SNOMED_TAXONOMY_PANE_MAX_PX = 960;
const SNOMED_DETAIL_PANE_MIN_PX = 220;

const FHIR_SNOMED_VERSION_DATE = /^https?:\/\/snomed\.info\/[xs]?sct\/(\d+)\/version\/(\d{8})/;

/** Restore saved taxonomy column width for the SNOMED mini-browser split pane. */
export function readStoredSnomedTaxonomyPaneWidthPx() {
	try {
		const v = parseInt(localStorage.getItem(SNOMED_TAXONOMY_PANE_WIDTH_STORAGE_KEY), 10);
		if (!Number.isFinite(v)) return null;
		if (v < SNOMED_TAXONOMY_PANE_MIN_PX || v > SNOMED_TAXONOMY_PANE_MAX_PX) return null;
		return v;
	} catch {
		return null;
	}
}

/**
 * Locate the selected concept in the expanded subtree and return breadcrumb ancestors from scope root downward.
 */
export function findDisplayedTreePath(root, targetCode) {
	const want = targetCode != null ? String(targetCode) : '';
	if (!root || !want) return null;
	function dfs(node) {
		if (!node) return null;
		if (String(node.code) === want) return [node];
		if (!node.expanded || !Array.isArray(node.children) || node.children.length === 0) return null;
		for (const c of node.children) {
			const sub = dfs(c);
			if (sub) return [node, ...sub];
		}
		return null;
	}
	return dfs(root);
}

/**
 * Build ancestor chain_codes from selected concept upward using each node's first parent (multiple parents: first only).
 * `nodes` is the flat GraphNode[] from POST /partial-hierarchy.
 */
export function snomedFirstParentChainUpFromPartialHierarchy(nodes, selectedCode) {
	const sel = selectedCode != null ? String(selectedCode).trim() : '';
	if (!sel || !Array.isArray(nodes)) return [];
	const byCode = new Map();
	for (const n of nodes) {
		if (!n || n.code == null) continue;
		byCode.set(String(n.code), n);
	}
	const chainUp = [];
	const seen = new Set();
	let cur = sel;
	while (cur && !seen.has(cur)) {
		seen.add(cur);
		chainUp.push(cur);
		const gn = byCode.get(cur);
		const pars = gn && Array.isArray(gn.parents) ? gn.parents : [];
		const next = pars.length ? String(pars[0]).trim() : '';
		cur = next || null;
	}
	return chainUp;
}

function splitCsvAtBraceDepth(segment) {
	const parts = [];
	let depth = 0;
	let start = 0;
	const s = String(segment || '');
	for (let i = 0; i < s.length; i++) {
		const ch = s[i];
		if (ch === '{') depth++;
		else if (ch === '}') depth = Math.max(0, depth - 1);
		else if (ch === ',' && depth === 0) {
			parts.push(s.slice(start, i).trim());
			start = i + 1;
		}
	}
	parts.push(s.slice(start).trim());
	return parts.filter(Boolean);
}

/** Parse defining attribute triples out of `$lookup.normalForm` (with terms); mirrors {@link org.snomed.snowstormlite.service.NormalFormBuilder}. */
export function parseDefiningRelationshipsFromNormalForm(normalForm) {
	const nf = String(normalForm || '').trim();
	if (!nf) return [];

	const sepIdx = nf.indexOf(':');
	if (sepIdx < 0) return [];

	const refinement = nf.slice(sepIdx + 1).trim().replace(/^:+/, '').trim();
	if (!refinement) return [];

	const clauses = splitCsvAtBraceDepth(refinement);
	const rows = [];

	for (let rawClause of clauses) {
		let clause = rawClause;
		while (clause.startsWith('{') && clause.endsWith('}')) {
			clause = clause.slice(1, -1).trim();
		}
		const inner = splitCsvAtBraceDepth(clause);
		const pieces = inner.length ? inner : [clause];
		for (const pieceRaw of pieces) {
			let piece = pieceRaw.trim().replace(/^[{]+/, '').replace(/[}]+$/, '').trim();
			if (!piece) continue;
			const eq = piece.indexOf('|=');
			if (eq === -1) continue;
			const lhs = piece.slice(0, eq).trim();
			const rhs = piece.slice(eq + 2).trim();
			const typeMatch = /^(\d+)\|([^|]*)$/.exec(lhs);
			if (!typeMatch) continue;
			const typeId = typeMatch[1];
			let typeDisplay = String(typeMatch[2] ?? '').trim() || typeId;
			if (typeId === SNOMED_IS_A_RELATIONSHIP || /^is\s*a$/i.test(typeDisplay.replace(/\.$/, '').trim())) {
				continue;
			}
			const codedTarget = /^(\d+)\|([^|]*)\|$/.exec(rhs);
			let targetCode = null;
			let targetRaw = rhs;
			let isConcrete = true;
			if (codedTarget) {
				isConcrete = false;
				targetCode = codedTarget[1];
				targetRaw = codedTarget[2]?.trim().length ? `${codedTarget[1]}|${codedTarget[2]}` : codedTarget[1];
			}
			rows.push({
				type: typeDisplay,
				typeId,
				targetCode,
				targetDisplay: codedTarget ? String(codedTarget[2] ?? '').trim() : '',
				concreteRaw: isConcrete ? rhs : '',
				targetRaw: isConcrete ? rhs : codedTarget ? String(codedTarget[2] ?? '').trim() : rhs,
				isConcrete
			});
		}
	}
	return rows;
}

function breadcrumbDisplayForNode(node) {
	if (!node) return '';
	return String(node.displayLabel || node.display || node.code || '').trim() || String(node.code);
}

function breadcrumbFsntOrDisplay(detail) {
	if (!detail) return '';
	const designations = detail.designations || [];
	const fsn = designations.find(d => {
		const u = String(d.use ?? '');
		return u.includes('Fully specified name') || u.includes(SNOMED_FSN_DESCRIPTION_TYPE_ID);
	});
	const fromDesig = fsn && String(fsn.value || '').trim();
	return fromDesig || String(detail.display || detail.code || '').trim();
}

function synonymLineFromDetail(detail) {
	const designations = detail?.designations;
	if (!Array.isArray(designations) || !designations.length) return '';
	const heroLc = breadcrumbFsntOrDisplay(detail).toLocaleLowerCase();
	const seen = new Set();
	const synonyms = [];
	for (const d of designations) {
		const u = String(d.use ?? '').toLowerCase();
		const isFsn =
			u.includes('fully specified name') ||
			u.includes(SNOMED_FSN_DESCRIPTION_TYPE_ID);
		if (isFsn) continue;
		const syn = String(d.value || '').trim();
		const lang = String(d.language || '').toLowerCase();
		if (!syn) continue;
		if (lang.length > 0 && !lang.startsWith('en')) continue;
		const key = syn.toLocaleLowerCase();
		if (heroLc && key === heroLc) continue;
		if (seen.has(key)) continue;
		seen.add(key);
		synonyms.push(syn);
	}
	return synonyms.slice(0, 12).join(', ');
}

function parseBatchDisplayMap(bundleJson) {
	const map = new Map();
	for (const e of bundleJson.entry || []) {
		const r = e.resource;
		if (r && r.resourceType === 'Parameters') {
			const parsed = parseLookupParameters(r);
			if (parsed.code) map.set(String(parsed.code), parsed.display || String(parsed.code));
		}
	}
	return map;
}

export function snomedChildrenExpandUrl(parentId) {
	return `${SNOMED_SYSTEM_URI}?fhir_vs=ecl/<!${parentId}`;
}

export function snomedIsaScopeUrl(scopeId) {
	return `${SNOMED_SYSTEM_URI}?fhir_vs=isa/${scopeId}`;
}

export function snomedAllConceptsUrl() {
	return `${SNOMED_SYSTEM_URI}?fhir_vs`;
}

function flattenContains(contains, out = []) {
	if (!contains || !Array.isArray(contains)) return out;
	for (const c of contains) {
		out.push(c);
		if (c.contains && c.contains.length) flattenContains(c.contains, out);
	}
	return out;
}

function expandParametersBody(url, filter, offset, count) {
	const parameter = [
		{ name: 'url', valueUri: url },
		{ name: 'count', valueInteger: count },
		{ name: 'offset', valueInteger: offset }
	];
	if (filter != null && String(filter).trim() !== '') {
		parameter.push({ name: 'filter', valueString: String(filter).trim() });
	}
	return JSON.stringify({ resourceType: 'Parameters', parameter });
}

function operationOutcomeMessage(data) {
	if (!data || typeof data !== 'object') return null;
	if (data.resourceType === 'OperationOutcome' && Array.isArray(data.issue)) {
		return data.issue.map(i => i.diagnostics || i.details?.text || i.code || '').filter(Boolean).join('; ') || null;
	}
	return data.message || null;
}

function createEmptyTreeNode(code, display, inactive = false, pendingHints = false) {
	return {
		code: String(code),
		display: display || String(code),
		inactive,
		sufficientlyDefined: null,
		displayLabel: null,
		expandable: null,
		hierarchyHintPending: pendingHints,
		expanded: false,
		loading: false,
		childrenLoaded: false,
		children: [],
		childLoadError: null
	};
}

function hierarchyDisplaySortKey(node) {
	const label = String(node.displayLabel || node.display || '').trim();
	return label || String(node.code);
}

/** Sort direct children by preferred display (FSN when enriched), then concept id. */
function sortSnomedChildrenByDisplay(nodes) {
	if (!Array.isArray(nodes) || nodes.length < 2) return;
	nodes.sort((a, b) => {
		const cmp = hierarchyDisplaySortKey(a).localeCompare(hierarchyDisplaySortKey(b), undefined, {
			sensitivity: 'base',
			numeric: true
		});
		if (cmp !== 0) return cmp;
		return String(a.code).localeCompare(String(b.code), undefined, { numeric: true });
	});
}

/** Sort related-concept id lists (parents / children) by resolved display label, then id. */
function sortSnomedRelatedConceptIdsByLabel(ids, conceptIdToLabel) {
	if (!Array.isArray(ids) || ids.length < 2) return;
	const labelFn = typeof conceptIdToLabel === 'function' ? conceptIdToLabel : () => '';
	ids.sort((a, b) => {
		const ca = String(a);
		const cb = String(b);
		const la = String(labelFn(ca) || ca).trim();
		const lb = String(labelFn(cb) || cb).trim();
		const cmp = la.localeCompare(lb, undefined, { sensitivity: 'base', numeric: true });
		if (cmp !== 0) return cmp;
		return ca.localeCompare(cb, undefined, { numeric: true });
	});
}

function partValue(part) {
	if (!part) return undefined;
	return (
		part.valueCode ??
		part.valueString ??
		part.valueBoolean ??
		part.valueInteger ??
		part.valueUri ??
		(part.valueCoding && (part.valueCoding.code || part.valueCoding.display))
	);
}

/** Parse CodeSystem $lookup Parameters JSON into a view-friendly shape */
export function parseLookupParameters(input) {
	const params = input.parameter || [];
	const out = {
		code: null,
		display: null,
		system: null,
		version: null,
		name: null,
		inactive: null,
		parents: [],
		children: [],
		designations: [],
		stringProps: {}
	};
	for (const p of params) {
		const name = p.name;
		if (name === 'code') out.code = p.valueCode ?? p.valueString ?? null;
		else if (name === 'display') out.display = p.valueString ?? null;
		else if (name === 'system') out.system = p.valueUri ?? p.valueString ?? null;
		else if (name === 'version') out.version = p.valueString ?? p.valueUri ?? null;
		else if (name === 'name') out.name = p.valueString ?? null;
		else if (name === 'designation') {
			const parts = p.part || [];
			const row = {};
			for (const pt of parts) {
				if (pt.name === 'language') row.language = partValue(pt);
				if (pt.name === 'use') row.use = pt.valueCoding?.display || pt.valueCoding?.code || null;
				if (pt.name === 'value') row.value = pt.valueString ?? null;
			}
			if (row.value) out.designations.push(row);
		} else if (name === 'property') {
			const parts = p.part || [];
			let propCode;
			let propVal;
			for (const pt of parts) {
				if (pt.name === 'code') propCode = partValue(pt);
				if (pt.name === 'value' || pt.name === 'valueCode' || pt.name === 'valueString' || pt.name === 'valueBoolean') {
					propVal = partValue(pt);
				}
			}
			if (propCode === 'parent' && propVal != null) out.parents.push(String(propVal));
			else if (propCode === 'child' && propVal != null) out.children.push(String(propVal));
			else if (propCode === 'inactive' && typeof propVal === 'boolean') out.inactive = propVal;
			else if (propCode && propVal != null && typeof propVal !== 'boolean') {
				out.stringProps[propCode] = String(propVal);
			} else if (propCode && typeof propVal === 'boolean') {
				out.stringProps[propCode] = propVal;
			}
		}
	}
	return out;
}

export function extractSnomedHierarchyHints(parsed) {
	const designations = parsed.designations || [];
	const fsnDesig = designations.find(d => {
		const u = String(d.use ?? '');
		return u.includes('Fully specified name') || u.includes(SNOMED_FSN_DESCRIPTION_TYPE_ID);
	});
	const displayLabel = (fsnDesig && fsnDesig.value) || parsed.display || parsed.code || '';
	const hasChildren = Array.isArray(parsed.children) && parsed.children.length > 0;
	const inactive = parsed.inactive;
	const sdRaw = parsed.stringProps && parsed.stringProps.sufficientlyDefined;
	let sufficientlyDefined;
	if (typeof sdRaw === 'boolean') sufficientlyDefined = sdRaw;
	else if (sdRaw === 'true') sufficientlyDefined = true;
	else if (sdRaw === 'false') sufficientlyDefined = false;
	return {
		hasChildren,
		displayLabel,
		inactive: typeof inactive === 'boolean' ? inactive : undefined,
		sufficientlyDefined
	};
}

function applyHierarchyHintsToNode(node, hints) {
	node.expandable = hints.hasChildren;
	node.displayLabel = hints.displayLabel || node.display;
	node.hierarchyHintPending = false;
	if (typeof hints.inactive === 'boolean') {
		node.inactive = hints.inactive;
	}
	if (typeof hints.sufficientlyDefined === 'boolean') {
		node.sufficientlyDefined = hints.sufficientlyDefined;
	}
}

function fallbackHierarchyHints(node) {
	return {
		hasChildren: true,
		displayLabel: node.display,
		inactive: node.inactive
	};
}

function chunkArray(arr, size) {
	const chunks = [];
	for (let i = 0; i < arr.length; i += size) chunks.push(arr.slice(i, i + size));
	return chunks;
}

function batchLookupBundleBody(codes) {
	return {
		resourceType: 'Bundle',
		type: 'batch',
		entry: codes.map(code => ({
			request: {
				method: 'GET',
				url: `CodeSystem/$lookup?system=${encodeURIComponent(SNOMED_SYSTEM_URI)}&code=${encodeURIComponent(String(code))}`
			}
		}))
	};
}

function parseBatchLookupHintMap(bundleJson) {
	const map = new Map();
	for (const e of bundleJson.entry || []) {
		const r = e.resource;
		if (r && r.resourceType === 'Parameters') {
			const parsed = parseLookupParameters(r);
			if (parsed.code) map.set(String(parsed.code), extractSnomedHierarchyHints(parsed));
		}
	}
	return map;
}

export const snomedBrowserGetters = {
	get snomedVisibleRows() {
		const out = [];
		const walk = (node, depth, pathPrefix) => {
			if (!node) return;
			const pathKey = pathPrefix === '' ? String(node.code) : `${pathPrefix}/${node.code}`;
			out.push({ node, depth, pathKey });
			if (node.expanded && Array.isArray(node.children)) {
				for (const c of node.children) walk(c, depth + 1, pathKey);
			}
		};
		if (this.snomedTreeRoot) walk(this.snomedTreeRoot, 0, '');
		return out;
	},

	get snomedEditionChromeText() {
		const s = this.snomedEditionSummaryLine;
		return s && String(s).trim().length ? s : 'SNOMED CT edition';
	},

	get snomedChromeLanguage() {
		return 'FSN · en-US';
	},

	get snomedHeroFsn() {
		return breadcrumbFsntOrDisplay(this.snomedDetail);
	},

	get snomedHeroSynonymsText() {
		return synonymLineFromDetail(this.snomedDetail);
	},

	get snomedHeroSufficiencyMarker() {
		const sd = this.snomedDetail?.stringProps?.sufficientlyDefined;
		let suf;
		if (typeof sd === 'boolean') suf = sd;
		else if (sd === 'true') suf = true;
		else if (sd === 'false') suf = false;
		if (typeof suf !== 'boolean') return '\u00a0';
		return suf ? '\u2261' : '\u00a0';
	},

	get snomedDefiningRelationships() {
		const nf = this.snomedDetail?.stringProps?.normalForm;
		const raw = parseDefiningRelationshipsFromNormalForm(nf);
		const cache = this.snomedCodeDisplayCache || {};
		return raw.map(r => ({
			...r,
			targetDisplay:
				cache[r.targetCode || ''] ||
				(r.targetDisplay && r.targetDisplay.length ? r.targetDisplay : r.targetCode || r.targetRaw)
		}));
	},

	get snomedChildrenWrapClass() {
		const ids = this.snomedDetail?.children;
		const n = Array.isArray(ids) ? ids.length : 0;
		if (!this.snomedConceptChildrenExpanded && n > SNOMED_CHILD_PREVIEW_COUNT) {
			return 'snomed-mini-children-collapsed';
		}
		return '';
	},

	get snomedVisibleChildIdsPreview() {
		const ids = this.snomedDetail?.children;
		if (!Array.isArray(ids) || !ids.length) return [];
		if (this.snomedConceptChildrenExpanded) return ids;
		return ids.slice(0, SNOMED_CHILD_PREVIEW_COUNT);
	},

	get snomedRemainingChildCount() {
		const ids = this.snomedDetail?.children;
		if (!Array.isArray(ids) || !ids.length) return 0;
		const vis = SNOMED_CHILD_PREVIEW_COUNT;
		if (this.snomedConceptChildrenExpanded) return 0;
		return Math.max(0, ids.length - vis);
	},

	get snomedTaxonomyPaneInlineStyle() {
		const w = this.snomedTaxonomyPaneWidthPx;
		if (w == null || !Number.isFinite(Number(w))) return {};
		return {
			width: `${w}px`,
			flexGrow: '0',
			flexShrink: '0',
			flexBasis: `${w}px`,
			maxWidth: 'none'
		};
	}
};

export const dashboardSnomedBrowser = {
	async snomedPostExpand(url, filter, offset, count) {
		const res = await fetchWithTimeout(`${this.fhirBaseUrl}/ValueSet/$expand`, AJAX_TIMEOUT_MS, {
			method: 'POST',
			headers: {
				Accept: 'application/fhir+json',
				'Content-Type': 'application/fhir+json'
			},
			body: expandParametersBody(url, filter, offset, count)
		});
		const data = await res.json().catch(() => ({}));
		if (!res.ok) {
			const msg = operationOutcomeMessage(data) || errorMessage(new Error(), 'ValueSet $expand', res);
			throw new Error(msg || 'ValueSet $expand failed');
		}
		const expansion = data.expansion || {};
		const total = typeof expansion.total === 'number' ? expansion.total : flattenContains(expansion.contains).length;
		const rows = flattenContains(expansion.contains).map(c => ({
			code: c.code != null ? String(c.code) : '',
			display: c.display != null ? String(c.display) : '',
			inactive: !!c.inactive
		}));
		return { rows, total };
	},

	async snomedPostBatchBundle(bundleObj) {
		const root = `${String(this.fhirBaseUrl || '').replace(/\/$/, '')}/`;
		const res = await fetchWithTimeout(root, AJAX_TIMEOUT_MS, {
			method: 'POST',
			headers: {
				Accept: 'application/fhir+json',
				'Content-Type': 'application/fhir+json'
			},
			body: JSON.stringify(bundleObj)
		});
		const data = await res.json().catch(() => ({}));
		if (!res.ok) {
			const msg = operationOutcomeMessage(data) || errorMessage(new Error(), 'FHIR Batch Bundle', res);
			throw new Error(msg || 'FHIR Batch Bundle failed');
		}
		if (data.resourceType !== 'Bundle') {
			throw new Error('Unexpected FHIR Batch response');
		}
		return data;
	},

	/** POST /partial-hierarchy (exposed under the FHIR base URL as /fhir/partial-hierarchy). */
	async snomedPostPartialHierarchy(codes) {
		const list = [...new Set((codes || []).map(c => String(c).trim()).filter(Boolean))];
		if (!list.length) return [];
		const res = await fetchWithTimeout(`${this.fhirBaseUrl}/partial-hierarchy`, AJAX_TIMEOUT_MS, {
			method: 'POST',
			headers: {
				Accept: 'application/json, application/fhir+json',
				'Content-Type': 'application/json'
			},
			body: JSON.stringify({
				system: SNOMED_SYSTEM_URI,
				codes: list,
				includeTerms: true
			})
		});
		const data = await res.json().catch(() => ({}));
		if (!res.ok) {
			const msg =
				operationOutcomeMessage(data) ||
				(typeof data?.message === 'string' && data.message) ||
				errorMessage(new Error(), 'partial-hierarchy', res);
			throw new Error(msg || 'partial-hierarchy failed');
		}
		if (!Array.isArray(data)) {
			throw new Error('Unexpected partial-hierarchy response');
		}
		return data;
	},

	async enrichSnomedNodesBatch(nodes) {
		const pending = (nodes || []).filter(n => n && n.hierarchyHintPending);
		if (!pending.length) return;
		for (const chunk of chunkArray(pending, BATCH_LOOKUP_CHUNK)) {
			const codes = chunk.map(n => n.code);
			let bundleResp;
			try {
				bundleResp = await this.snomedPostBatchBundle(batchLookupBundleBody(codes));
			} catch {
				for (const n of chunk) applyHierarchyHintsToNode(n, fallbackHierarchyHints(n));
				continue;
			}
			const hintsMap = parseBatchLookupHintMap(bundleResp);
			for (const n of chunk) {
				const h = hintsMap.get(String(n.code)) || fallbackHierarchyHints(n);
				applyHierarchyHintsToNode(n, h);
			}
		}
	},

	async snomedLookupConcept(code) {
		const qs = new URLSearchParams({
			system: SNOMED_SYSTEM_URI,
			code: String(code)
		});
		let res = await fetchWithTimeout(`${this.fhirBaseUrl}/CodeSystem/$lookup?${qs}`, AJAX_TIMEOUT_MS, {
			method: 'GET',
			headers: { Accept: 'application/fhir+json' }
		});
		let data = await res.json().catch(() => ({}));
		if (res.status === 405 || !res.ok) {
			const body = JSON.stringify({
				resourceType: 'Parameters',
				parameter: [
					{ name: 'system', valueUri: SNOMED_SYSTEM_URI },
					{ name: 'code', valueCode: String(code) }
				]
			});
			res = await fetchWithTimeout(`${this.fhirBaseUrl}/CodeSystem/$lookup`, AJAX_TIMEOUT_MS, {
				method: 'POST',
				headers: {
					Accept: 'application/fhir+json',
					'Content-Type': 'application/fhir+json'
				},
				body
			});
			data = await res.json().catch(() => ({}));
		}
		if (!res.ok) {
			const msg = operationOutcomeMessage(data) || errorMessage(new Error(), 'CodeSystem $lookup', res);
			throw new Error(msg || 'CodeSystem $lookup failed');
		}
		return parseLookupParameters(data);
	},

	validateSnomedConceptId(raw) {
		const s = String(raw || '').trim();
		if (!/^\d{6,18}$/.test(s)) {
			return { ok: false, error: 'Enter a valid SNOMED CT concept ID (6–18 digits).' };
		}
		return { ok: true, id: s };
	},

	resetSnomedBrowserState() {
		this.clearSnomedSearchDebounceTimer();
		this.snomedTreeRoot = null;
		this.snomedBrowserInitialized = false;
		this.snomedHierarchyError = null;
		this.snomedSelectedCode = null;
		this.snomedDetail = null;
		this.snomedDetailLoading = false;
		this.snomedDetailError = null;
		this.snomedSearchResults = [];
		this.snomedSearchTotal = 0;
		this.snomedSearchOffset = 0;
		this.snomedSearchError = null;
		this.snomedSearchLoading = false;
		this.snomedBreadcrumbTrail = [];
		this.snomedConceptChildrenExpanded = false;
		this.snomedCodeDisplayCache = {};
		this.snomedEditionSummaryLine = '—';
		this.snomedSearchScopeConceptId = '';
		this.snomedSearchScopeOptions = [];
	},

	async refreshSnomedBrowser() {
		this.resetSnomedBrowserState();
		await this.initSnomedBrowserTab();
	},

	async initSnomedBrowserTab() {
		this.loadSnomedEditionSummary();
		this.snomedHierarchyError = null;
		const v = this.validateSnomedConceptId(this.snomedScopeConceptId || SNOMED_ROOT_CONCEPT);
		if (!v.ok) {
			this.snomedHierarchyError = v.error;
			this.snomedBrowserInitialized = true;
			await this.loadSnomedSearchScopeOptions();
			return;
		}
		try {
			const lookedUp = await this.snomedLookupConcept(v.id);
			const hints = extractSnomedHierarchyHints(lookedUp);
			const root = createEmptyTreeNode(v.id, lookedUp.display || v.id, lookedUp.inactive === true, false);
			applyHierarchyHintsToNode(root, hints);
			this.snomedTreeRoot = root;
			this.snomedBrowserInitialized = true;
		} catch {
			const root = createEmptyTreeNode(v.id, v.id, false, false);
			applyHierarchyHintsToNode(root, fallbackHierarchyHints(root));
			this.snomedTreeRoot = root;
			this.snomedBrowserInitialized = true;
		}
		const hierRoot = this.snomedTreeRoot;
		if (hierRoot && hierRoot.expandable === true) {
			hierRoot.expanded = true;
			if (!hierRoot.childrenLoaded) await this.fetchSnomedChildren(hierRoot);
		}
		await this.loadSnomedSearchScopeOptions();
	},

	async applySnomedScope() {
		const v = this.validateSnomedConceptId(this.snomedScopeInput);
		if (!v.ok) {
			this.snomedHierarchyError = v.error;
			return;
		}
		this.snomedScopeInput = v.id;
		this.snomedScopeConceptId = v.id;
		this.snomedHierarchyError = null;
		this.snomedSelectedCode = null;
		this.snomedDetail = null;
		this.snomedDetailError = null;
		this.snomedBreadcrumbTrail = [];
		this.snomedTreeRoot = null;
		this.snomedBrowserInitialized = false;
		await this.initSnomedBrowserTab();
		this.setHash();
	},

	async resetSnomedScopeToRoot() {
		this.snomedScopeInput = SNOMED_ROOT_CONCEPT;
		this.snomedScopeConceptId = SNOMED_ROOT_CONCEPT;
		this.snomedHierarchyError = null;
		this.snomedSelectedCode = null;
		this.snomedDetail = null;
		this.snomedDetailError = null;
		this.snomedBreadcrumbTrail = [];
		this.snomedTreeRoot = null;
		this.snomedBrowserInitialized = false;
		await this.initSnomedBrowserTab();
		this.setHash();
	},

	async toggleSnomedNode(node) {
		if (!node || node.loading || node.hierarchyHintPending || node.expandable !== true) return;
		if (!node.expanded) {
			node.expanded = true;
			if (!node.childrenLoaded) {
				await this.fetchSnomedChildren(node);
			}
		} else {
			node.expanded = false;
		}
	},

	snomedTaxonomyPaneDividerDoubleClick() {
		this.snomedTaxonomyPaneWidthPx = null;
		try {
			localStorage.removeItem(SNOMED_TAXONOMY_PANE_WIDTH_STORAGE_KEY);
		} catch {
			/* ignore */
		}
	},

	snomedPaneDividerDragStart(event) {
		if (!event || event.button !== 0) return;
		event.preventDefault();
		const handleEl = event.currentTarget;
		const layout = handleEl && handleEl.closest && handleEl.closest('.snomed-mini-layout');
		if (!layout) return;
		const taxonomyPane = layout.querySelector('.snomed-taxonomy-pane');
		if (!taxonomyPane) return;

		const rect = layout.getBoundingClientRect();
		const startX = event.clientX;
		const startWidth = taxonomyPane.getBoundingClientRect().width;

		const clampWidth = raw => {
			const minW = SNOMED_TAXONOMY_PANE_MIN_PX;
			const maxW = Math.min(
				SNOMED_TAXONOMY_PANE_MAX_PX,
				Math.max(minW + 40, rect.width - SNOMED_DETAIL_PANE_MIN_PX)
			);
			return Math.round(Math.min(maxW, Math.max(minW, raw)));
		};

		this.snomedPaneDividerDragging = true;

		const onMove = ev => {
			const dx = ev.clientX - startX;
			this.snomedTaxonomyPaneWidthPx = clampWidth(startWidth + dx);
		};

		const onUp = () => {
			this.snomedPaneDividerDragging = false;
			document.removeEventListener('mousemove', onMove);
			document.removeEventListener('mouseup', onUp);
			try {
				const w = this.snomedTaxonomyPaneWidthPx;
				if (w != null && Number.isFinite(Number(w))) {
					localStorage.setItem(SNOMED_TAXONOMY_PANE_WIDTH_STORAGE_KEY, String(Math.round(Number(w))));
				}
			} catch {
				/* ignore */
			}
		};

		document.addEventListener('mousemove', onMove);
		document.addEventListener('mouseup', onUp);
	},

	async fetchSnomedChildren(node) {
		if (!node) return;

		node.loading = true;
		node.childLoadError = null;
		let res;
		try {
			const url = snomedChildrenExpandUrl(node.code);
			node.children = [];
			const collected = [];
			const seen = new Set();
			let offset = 0;

			while (true) {
				const { rows } = await this.snomedPostExpand(url, '', offset, HIERARCHY_EXPAND_CHUNK);
				for (const r of rows) {
					if (!r.code || seen.has(r.code)) continue;
					seen.add(r.code);
					collected.push(createEmptyTreeNode(r.code, r.display || r.code, r.inactive, true));
				}
				if (rows.length === 0) break;
				offset += rows.length;
				if (rows.length < HIERARCHY_EXPAND_CHUNK) break;
			}

			await this.enrichSnomedNodesBatch(collected);
			sortSnomedChildrenByDisplay(collected);
			node.children = collected;
			node.childrenLoaded = true;
		} catch (err) {
			node.childLoadError = errorMessage(err, 'SNOMED children', res);
		} finally {
			node.loading = false;
		}
	},

	snomedHierarchyGlyphMode(node) {
		if (!node) return 'leaf';
		if (node.loading && node.expandable === true) return 'pending';
		if (node.expandable === null || node.hierarchyHintPending) return 'hint-pending';
		if (!node.expandable) return 'leaf';
		return node.expanded ? 'expanded' : 'collapsed';
	},

	snomedHierarchyExpandEnabled(node) {
		return !!(node && node.expandable === true && !node.loading && !node.hierarchyHintPending);
	},

	searchImplicitUrl() {
		const scopeSel = String(this.snomedSearchScopeConceptId || '').trim();
		if (!scopeSel) return snomedAllConceptsUrl();
		const v = this.validateSnomedConceptId(scopeSel);
		return v.ok ? snomedIsaScopeUrl(v.id) : snomedAllConceptsUrl();
	},

	clearSnomedSearchDebounceTimer() {
		const t = this._snomedSearchDebounceTimer;
		if (t != null) {
			clearTimeout(t);
			this._snomedSearchDebounceTimer = null;
		}
	},

	/**
	 * Debounced auto-search while typing (sct-browser-style: ≥3 chars, 500ms after last input).
	 * Shorter queries clear the result list; explicit Search / Enter still uses {@link runSnomedSearch}.
	 */
	scheduleDebouncedSnomedSearch() {
		this.clearSnomedSearchDebounceTimer();
		const q = (this.snomedSearchQuery || '').trim();
		if (q.length < SNOMED_SEARCH_MIN_CHARS) {
			this.snomedSearchLoading = false;
			this.snomedSearchError = null;
			this.snomedSearchResults = [];
			this.snomedSearchTotal = 0;
			this.snomedSearchOffset = 0;
			return;
		}
		this._snomedSearchDebounceTimer = setTimeout(() => {
			this._snomedSearchDebounceTimer = null;
			void this.runSnomedSearch();
		}, SNOMED_SEARCH_DEBOUNCE_MS);
	},

	onSnomedSearchScopeChange() {
		this.clearSnomedSearchDebounceTimer();
		const q = (this.snomedSearchQuery || '').trim();
		if (q.length >= SNOMED_SEARCH_MIN_CHARS) {
			void this.runSnomedSearch();
		}
	},

	async loadSnomedSearchScopeOptions() {
		this.snomedSearchScopeOptions = [];
		try {
			const url = snomedChildrenExpandUrl(SNOMED_ROOT_CONCEPT);
			const byCode = new Map();
			let offset = 0;

			while (true) {
				const { rows } = await this.snomedPostExpand(url, '', offset, HIERARCHY_EXPAND_CHUNK);
				for (const r of rows) {
					if (!r.code || byCode.has(r.code)) continue;
					const disp = String(r.display || r.code || '').trim();
					byCode.set(r.code, disp || r.code);
				}
				if (rows.length === 0) break;
				offset += rows.length;
				if (rows.length < HIERARCHY_EXPAND_CHUNK) break;
			}

			const codes = [...byCode.keys()];
			const ptFromLookup = {};

			try {
				for (const chunk of chunkArray(codes, BATCH_LOOKUP_CHUNK)) {
					const bundleResp = await this.snomedPostBatchBundle(batchLookupBundleBody(chunk));
					const rowMap = parseBatchDisplayMap(bundleResp);
					for (const id of chunk) {
						const pt = rowMap.get(String(id));
						if (pt != null && String(pt).trim().length) {
							ptFromLookup[String(id)] = String(pt).trim();
						}
					}
				}
			} catch {
				/* keep expand-derived labels below */
			}

			const options = codes.map(code => ({
				code,
				display: ptFromLookup[code] || byCode.get(code) || code
			}));
			options.sort((a, b) =>
				a.display.localeCompare(b.display, undefined, { sensitivity: 'base', numeric: true })
			);
			this.snomedSearchScopeOptions = options;
			const cur = String(this.snomedSearchScopeConceptId || '').trim();
			if (cur && !options.some(o => String(o.code) === cur)) this.snomedSearchScopeConceptId = '';
		} catch {
			this.snomedSearchScopeOptions = [];
		}
	},

	async runSnomedSearch() {
		this.clearSnomedSearchDebounceTimer();
		if (this.section !== 'snomed-mini-browser') {
			return;
		}
		const url = this.searchImplicitUrl();
		if (!url) {
			this.snomedSearchError = 'Search scope unavailable.';
			return;
		}
		const q = (this.snomedSearchQuery || '').trim();
		if (!q) {
			this.snomedSearchError = 'Enter search text.';
			return;
		}
		this.snomedSearchLoading = true;
		this.snomedSearchError = null;
		this.snomedSearchOffset = 0;
		this.snomedSearchResults = [];
		this.snomedSearchTotal = 0;
		let res;
		try {
			const { rows, total } = await this.snomedPostExpand(url, q, 0, SEARCH_PAGE_SIZE);
			this.snomedSearchResults = rows;
			this.snomedSearchTotal = total;
			this.snomedSearchOffset = rows.length;
		} catch (err) {
			this.snomedSearchError = errorMessage(err, 'SNOMED search', res);
			this.snomedSearchResults = [];
		} finally {
			this.snomedSearchLoading = false;
		}
	},

	async loadMoreSnomedSearch() {
		const url = this.searchImplicitUrl();
		if (!url || this.snomedSearchLoading) return;
		const q = (this.snomedSearchQuery || '').trim();
		if (!q) return;
		if (this.snomedSearchOffset >= this.snomedSearchTotal) return;

		this.snomedSearchLoading = true;
		let res;
		try {
			const { rows } = await this.snomedPostExpand(url, q, this.snomedSearchOffset, SEARCH_PAGE_SIZE);
			const seen = new Set(this.snomedSearchResults.map(r => r.code));
			for (const r of rows) {
				if (!seen.has(r.code)) {
					seen.add(r.code);
					this.snomedSearchResults.push(r);
				}
			}
			this.snomedSearchOffset += rows.length;
		} catch (err) {
			this.snomedSearchError = errorMessage(err, 'SNOMED search', res);
		} finally {
			this.snomedSearchLoading = false;
		}
	},

	async loadSnomedEditionSummary() {
		try {
			const res = await fetchWithTimeout(
				`${this.fhirBaseUrl}/CodeSystem?url=${encodeURIComponent(SNOMED_SYSTEM_URI)}`,
				AJAX_TIMEOUT_MS,
				{ headers: { Accept: 'application/fhir+json' } }
			);
			if (!res.ok) throw new Error('CodeSystem search');
			const bundle = await res.json().catch(() => ({}));
			const all = (bundle.entry || []).map(e => e.resource).filter(Boolean);
			const cs = all.find(r => r.resourceType === 'CodeSystem' && r.url === SNOMED_SYSTEM_URI) || all.find(r => r.resourceType === 'CodeSystem');
			if (!cs) throw new Error('No CodeSystem');
			const title = String(cs.title || cs.name || 'SNOMED CT').trim();
			const ver = String(cs.version || '');
			let datePart = '';
			const m = ver.match(FHIR_SNOMED_VERSION_DATE);
			if (m && m[2]) {
				const d = m[2];
				datePart = `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6, 8)}`;
			}
			this.snomedEditionSummaryLine = datePart ? `${title} · ${datePart}` : `${title}`;
		} catch {
			this.snomedEditionSummaryLine = 'SNOMED CT';
		}
	},

	async updateSnomedBreadcrumbTrail(selectedCode) {
		const sel = String(selectedCode || '').trim();
		const root = this.snomedTreeRoot;
		if (!root || !sel) {
			this.snomedBreadcrumbTrail = [];
			return;
		}
		const pathNodes = findDisplayedTreePath(root, sel);
		if (pathNodes && pathNodes.length) {
			this.snomedBreadcrumbTrail = pathNodes.map(n => ({
				code: String(n.code),
				label: breadcrumbDisplayForNode(n)
			}));
			return;
		}
		const rootCr = {
			code: String(root.code),
			label: breadcrumbDisplayForNode(root)
		};
		const curLab =
			String(this.snomedDetail?.code) === sel ? breadcrumbFsntOrDisplay(this.snomedDetail) || sel : sel;
		if (sel === rootCr.code) {
			this.snomedBreadcrumbTrail = [rootCr];
			return;
		}
		let nodes;
		try {
			nodes = await this.snomedPostPartialHierarchy([sel]);
		} catch {
			this.snomedBreadcrumbTrail = [rootCr, { code: sel, label: curLab }];
			return;
		}
		if (!this.snomedCodeDisplayCache) this.snomedCodeDisplayCache = {};
		const merged = { ...this.snomedCodeDisplayCache };
		for (const n of nodes) {
			if (n && n.code != null && n.term != null && String(n.term).trim().length) {
				merged[String(n.code)] = String(n.term).trim();
			}
		}
		this.snomedCodeDisplayCache = merged;
		const byCode = new Map();
		for (const n of nodes) {
			if (n && n.code != null) byCode.set(String(n.code), n);
		}
		const chainUp = snomedFirstParentChainUpFromPartialHierarchy(nodes, sel);
		const rootCode = rootCr.code;
		const idx = chainUp.indexOf(rootCode);
		const codesDown =
			idx >= 0 ? chainUp.slice(0, idx + 1).reverse() : [rootCode, sel];
		const detail = this.snomedDetail;
		this.snomedBreadcrumbTrail = codesDown.map(c => {
			const codeStr = String(c);
			if (codeStr === String(root.code)) {
				return { code: codeStr, label: rootCr.label };
			}
			if (codeStr === sel && String(detail?.code) === sel) {
				const fromDetail = breadcrumbFsntOrDisplay(detail);
				return { code: codeStr, label: (fromDetail && String(fromDetail).trim()) || curLab };
			}
			const gn = byCode.get(codeStr);
			const fromTerm = gn && gn.term != null && String(gn.term).trim();
			return { code: codeStr, label: fromTerm || this.snomedLabelFor(codeStr) || codeStr };
		});
	},

	async warmSnomedDetailDisplays(ids) {
		const list = (ids || []).map(String).filter(Boolean);
		if (!list.length) return;
		if (!this.snomedCodeDisplayCache) this.snomedCodeDisplayCache = {};
		const want = [...new Set(list)].filter(c => /^\d{6,18}$/.test(c)).filter(c => {
			const v = this.snomedCodeDisplayCache[c];
			return !(typeof v === 'string' && v.trim().length);
		});
		if (!want.length) return;
		const merged = { ...this.snomedCodeDisplayCache };
		try {
			for (const chunk of chunkArray(want, BATCH_LOOKUP_CHUNK)) {
				const bundleResp = await this.snomedPostBatchBundle(batchLookupBundleBody(chunk));
				const partMap = parseBatchDisplayMap(bundleResp);
				for (const [k, v] of partMap) {
					if (v != null && String(v).trim().length) merged[String(k)] = String(v).trim();
				}
			}
			this.snomedCodeDisplayCache = merged;
		} catch {
			this.snomedCodeDisplayCache = merged;
		}
	},

	snomedLabelFor(conceptId) {
		const c = conceptId != null ? String(conceptId) : '';
		if (!c) return '';
		const d = this.snomedCodeDisplayCache && this.snomedCodeDisplayCache[c];
		return d != null && String(d).trim().length ? String(d).trim() : c;
	},

	async selectSnomedConcept(code) {
		if (!code) return;
		const id = String(code).trim();
		this.snomedConceptChildrenExpanded = false;
		this.snomedSelectedCode = id;
		this.snomedDetail = null;
		this.snomedDetailError = null;
		this.snomedDetailLoading = true;
		let res;
		try {
			const detail = await this.snomedLookupConcept(id);
			this.snomedDetail = detail;
			const relExtras = [];
			for (const r of parseDefiningRelationshipsFromNormalForm(detail?.stringProps?.normalForm || '')) {
				if (r.targetCode) relExtras.push(r.targetCode);
			}
			await this.warmSnomedDetailDisplays([...(detail.parents || []), ...(detail.children || []), ...relExtras]);
			if (Array.isArray(this.snomedDetail?.parents)) {
				sortSnomedRelatedConceptIdsByLabel(this.snomedDetail.parents, cid => this.snomedLabelFor(cid));
			}
			if (Array.isArray(this.snomedDetail?.children)) {
				sortSnomedRelatedConceptIdsByLabel(this.snomedDetail.children, cid => this.snomedLabelFor(cid));
			}
			await this.updateSnomedBreadcrumbTrail(id);
		} catch (err) {
			this.snomedDetailError = errorMessage(err, 'Concept detail', res);
			this.snomedDetail = null;
			this.snomedBreadcrumbTrail = [];
		} finally {
			this.snomedDetailLoading = false;
		}
	}
};
