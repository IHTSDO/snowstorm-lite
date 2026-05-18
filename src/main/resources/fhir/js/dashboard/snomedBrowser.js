import { AJAX_TIMEOUT_MS } from './constants.js';
import { fetchWithTimeout, errorMessage } from './http.js';

export const SNOMED_SYSTEM_URI = 'http://snomed.info/sct';
export const SNOMED_ROOT_CONCEPT = '138875005';

const SEARCH_PAGE_SIZE = 50;
/** Chunk size for ValueSet $expand when loading all direct children (internal paging, no UI limit). */
const HIERARCHY_EXPAND_CHUNK = 8000;
/** Codes per FHIR Batch Bundle for hierarchy hints (GET CodeSystem/$lookup per entry). */
const BATCH_LOOKUP_CHUNK = 80;
/** SNOMED CT description type identifier for FSN (Concepts.FSN). */
const SNOMED_FSN_DESCRIPTION_TYPE_ID = '900000000000003001';

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
	},

	async refreshSnomedBrowser() {
		this.resetSnomedBrowserState();
		await this.initSnomedBrowserTab();
	},

	async initSnomedBrowserTab() {
		this.snomedHierarchyError = null;
		const v = this.validateSnomedConceptId(this.snomedScopeConceptId || SNOMED_ROOT_CONCEPT);
		if (!v.ok) {
			this.snomedHierarchyError = v.error;
			this.snomedBrowserInitialized = true;
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

	async fetchSnomedChildren(node) {
		if (!node) return;

		node.loading = true;
		node.childLoadError = null;
		let res;
		try {
			const url = snomedChildrenExpandUrl(node.code);
			node.children = [];
			const seen = new Set();
			let offset = 0;

			while (true) {
				const { rows } = await this.snomedPostExpand(url, '', offset, HIERARCHY_EXPAND_CHUNK);
				for (const r of rows) {
					if (!r.code || seen.has(r.code)) continue;
					seen.add(r.code);
					node.children.push(createEmptyTreeNode(r.code, r.display || r.code, r.inactive, true));
				}
				if (rows.length === 0) break;
				offset += rows.length;
				if (rows.length < HIERARCHY_EXPAND_CHUNK) break;
			}

			node.childrenLoaded = true;
			await this.enrichSnomedNodesBatch(node.children);
			sortSnomedChildrenByDisplay(node.children);
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
		if (this.snomedLimitSearchToSubtree) {
			const v = this.validateSnomedConceptId(this.snomedScopeConceptId);
			if (!v.ok) return null;
			return snomedIsaScopeUrl(v.id);
		}
		return snomedAllConceptsUrl();
	},

	async runSnomedSearch() {
		const url = this.searchImplicitUrl();
		if (!url) {
			this.snomedSearchError = 'Fix hierarchy scope before searching in subtree.';
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

	async selectSnomedConcept(code) {
		if (!code) return;
		const id = String(code).trim();
		this.snomedSelectedCode = id;
		this.snomedDetail = null;
		this.snomedDetailError = null;
		this.snomedDetailLoading = true;
		let res;
		try {
			this.snomedDetail = await this.snomedLookupConcept(id);
		} catch (err) {
			this.snomedDetailError = errorMessage(err, 'Concept detail', res);
		} finally {
			this.snomedDetailLoading = false;
		}
	}
};
