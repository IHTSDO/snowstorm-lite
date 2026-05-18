import { AJAX_TIMEOUT_MS } from './constants.js';
import { fetchWithTimeout, errorMessage } from './http.js';

export const SNOMED_SYSTEM_URI = 'http://snomed.info/sct';
export const SNOMED_ROOT_CONCEPT = '138875005';

const CHILD_PAGE_SIZE = 100;
const SEARCH_PAGE_SIZE = 50;

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

function createEmptyTreeNode(code, display, inactive = false) {
	return {
		code: String(code),
		display: display || String(code),
		inactive,
		expanded: false,
		loading: false,
		loadingMore: false,
		childrenLoaded: false,
		children: [],
		nextChildOffset: 0,
		childTotal: null,
		childLoadError: null
	};
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

export const snomedBrowserGetters = {
	get snomedVisibleRows() {
		const out = [];
		const walk = (node, depth) => {
			if (!node) return;
			out.push({ node, depth });
			if (node.expanded && Array.isArray(node.children)) {
				for (const c of node.children) walk(c, depth + 1);
			}
		};
		if (this.snomedTreeRoot) walk(this.snomedTreeRoot, 0);
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
		this.snomedHierarchyError = null;
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
			this.snomedTreeRoot = createEmptyTreeNode(v.id, lookedUp.display || v.id, false);
			this.snomedBrowserInitialized = true;
		} catch {
			this.snomedTreeRoot = createEmptyTreeNode(v.id, v.id, false);
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
		if (!node || node.loading) return;
		if (!node.expanded) {
			node.expanded = true;
			if (!node.childrenLoaded) {
				await this.fetchSnomedChildPage(node, true);
			}
		} else {
			node.expanded = false;
		}
	},

	async fetchSnomedChildPage(node, reset) {
		if (!node) return;
		const offset = reset ? 0 : node.nextChildOffset;
		if (!reset && node.childrenLoaded && offset >= (node.childTotal ?? 0)) return;

		node.loading = !node.childrenLoaded || reset;
		if (!reset) node.loadingMore = true;
		node.childLoadError = null;
		let res;
		try {
			const { rows, total } = await this.snomedPostExpand(
				snomedChildrenExpandUrl(node.code),
				'',
				offset,
				CHILD_PAGE_SIZE
			);
			if (reset) {
				node.children = [];
			}
			const seen = new Set(node.children.map(c => c.code));
			for (const r of rows) {
				if (!r.code || seen.has(r.code)) continue;
				seen.add(r.code);
				node.children.push(createEmptyTreeNode(r.code, r.display || r.code, r.inactive));
			}
			node.childTotal = total;
			node.nextChildOffset = offset + rows.length;
			node.childrenLoaded = true;
		} catch (err) {
			node.childLoadError = errorMessage(err, 'SNOMED children', res);
		} finally {
			node.loading = false;
			node.loadingMore = false;
		}
	},

	async loadMoreSnomedChildren(node) {
		if (!node || node.loadingMore || node.loading) return;
		await this.fetchSnomedChildPage(node, false);
	},

	showSnomedExpandToggle(node) {
		if (!node.childrenLoaded) return true;
		return (node.childTotal ?? 0) > 0;
	},

	snomedHasMoreChildren(node) {
		return (
			node &&
			node.childrenLoaded &&
			node.childTotal != null &&
			node.nextChildOffset < node.childTotal
		);
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
