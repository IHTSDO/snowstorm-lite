import {
	AJAX_TIMEOUT_MS,
	FHIR_RESOURCE_DEFAULT_VERSION,
	VALUESET_CRITERIA_TYPES,
	VALUESET_DEFAULT_SYSTEM
} from './constants.js';
import { normalizeResourceStatus, slugifyResourceName } from './resourceFormHelpers.js';
import { fetchWithTimeout } from './http.js';

let valueSetCriteriaRowSeq = 0;

export function createValueSetCriteriaRow(overrides = {}) {
	valueSetCriteriaRowSeq += 1;
	return {
		id: String(valueSetCriteriaRowSeq),
		system: VALUESET_DEFAULT_SYSTEM,
		version: '',
		criteriaType: 'constraint',
		value: '',
		...overrides
	};
}

function criteriaTypeDef(criteriaType) {
	return VALUESET_CRITERIA_TYPES.find(t => t.id === criteriaType);
}

function parseConceptCodes(raw) {
	return String(raw || '')
		.split(/[\s,]+/)
		.map(c => c.trim())
		.filter(Boolean);
}

function rowHasContent(row) {
	if (!row) return false;
	if (row.criteriaType === 'concepts') return parseConceptCodes(row.value).length > 0;
	return String(row.value || '').trim() !== '';
}

function criteriaRowToFhir(row) {
	const system = String(row.system || '').trim();
	const item = { system };
	const version = String(row.version || '').trim();
	if (version) item.version = version;
	if (row.criteriaType === 'concepts') {
		const codes = parseConceptCodes(row.value);
		if (codes.length) item.concept = codes.map(code => ({ code }));
		return item;
	}
	const typeDef = criteriaTypeDef(row.criteriaType);
	if (!typeDef || !typeDef.property) return item;
	const value = String(row.value || '').trim();
	if (!value) return item;
	item.filter = [{ property: typeDef.property, op: typeDef.op, value }];
	return item;
}

function mapValidCriteriaRows(rows) {
	return (rows || [])
		.filter(row => String(row.system || '').trim() && rowHasContent(row))
		.map(criteriaRowToFhir);
}

function filterToCriteriaType(property, op) {
	if ((property === 'constraint' || property === 'expression') && op === '=') return 'constraint';
	if (property === 'concept' && op === 'is-a') return 'is-a';
	if (property === 'concept' && op === 'descendent-of') return 'descendent-of';
	if (property === 'concept' && op === 'in') return 'in';
	if (property === 'parent' && op === '=') return 'parent';
	return null;
}

function fhirConceptSetToBuilderRows(criteria) {
	const rows = [];
	let hasNestedValueSet = false;
	let skipped = 0;
	const system = criteria.system != null ? String(criteria.system) : VALUESET_DEFAULT_SYSTEM;
	const version = criteria.version != null ? String(criteria.version) : '';

	if (Array.isArray(criteria.valueSet) && criteria.valueSet.length) {
		hasNestedValueSet = true;
	}

	const concepts = Array.isArray(criteria.concept) ? criteria.concept : [];
	let codes = concepts.map(c => (c && c.code != null ? String(c.code).trim() : '')).filter(Boolean);
	if (!codes.length && Array.isArray(criteria.codes)) {
		codes = criteria.codes.map(c => String(c).trim()).filter(Boolean);
	}
	if (codes.length) {
		rows.push(createValueSetCriteriaRow({
			system,
			version,
			criteriaType: 'concepts',
			value: codes.join('\n')
		}));
	}

	for (const f of Array.isArray(criteria.filter) ? criteria.filter : []) {
		const property = f.property != null ? String(f.property) : '';
		const op = f.op != null ? String(f.op) : '';
		const criteriaType = filterToCriteriaType(property, op);
		const value = f.value != null ? String(f.value) : '';
		if (!criteriaType || !value.trim()) {
			skipped += 1;
			continue;
		}
		rows.push(createValueSetCriteriaRow({
			system,
			version,
			criteriaType,
			value
		}));
	}

	return { rows, skipped, hasNestedValueSet };
}

function fhirCriteriaListToBuilderRows(list) {
	const allRows = [];
	let skipped = 0;
	let hasNestedValueSet = false;
	for (const criteria of list || []) {
		const result = fhirConceptSetToBuilderRows(criteria);
		allRows.push(...result.rows);
		skipped += result.skipped;
		hasNestedValueSet = hasNestedValueSet || result.hasNestedValueSet;
	}
	return { rows: allRows, skipped, hasNestedValueSet };
}

export const dashboardValueSetUi = {
	valueSetCriteriaTypes: VALUESET_CRITERIA_TYPES,

	criteriaTypeDef(criteriaType) {
		return criteriaTypeDef(criteriaType);
	},

	onValueSetIncludeValueTab(row) {
		if (String(row.value || '').trim() !== '') return;
		const typeDef = criteriaTypeDef(row.criteriaType);
		const placeholder = typeDef && typeDef.valuePlaceholder;
		if (!placeholder) return;
		row.value = placeholder;
	},

	addValueSetInclude() {
		this.addValueSetIncludes.push(createValueSetCriteriaRow());
	},

	removeValueSetInclude(index) {
		if ((this.addValueSetIncludes || []).length <= 1) return;
		this.addValueSetIncludes.splice(index, 1);
	},

	addValueSetExclude() {
		this.addValueSetExcludes.push(createValueSetCriteriaRow());
	},

	removeValueSetExclude(index) {
		this.addValueSetExcludes.splice(index, 1);
	},

	isValueSetBuilderContentValid() {
		const includes = this.addValueSetIncludes || [];
		if (includes.length === 0) return false;
		return includes.every(row => String(row.system || '').trim() && rowHasContent(row));
	},

	isValueSetEditable(rowOrDetail) {
		if (!this.valueSetUpdateSupported || !rowOrDetail) return false;
		return !this._isImplicitSnomedResource(rowOrDetail);
	},

	populateBuilderFromValueSet(payload) {
		this.resetAddValueSetFields();
		this.addValueSetPayload = null;
		this.addValueSetUrl = payload.url != null ? String(payload.url) : '';
		this.addValueSetVersion = payload.version != null ? String(payload.version) : FHIR_RESOURCE_DEFAULT_VERSION;
		this.addValueSetTitle = payload.title != null ? String(payload.title) : '';
		this.addValueSetName = payload.name != null ? String(payload.name) : '';
		this.addValueSetDescription = payload.description != null ? String(payload.description) : '';
		this.addValueSetStatus = normalizeResourceStatus(payload.status);
		this.addValueSetExperimental = payload.experimental === true;
		this._addValueSetDerivedName = null;
		this._addValueSetDerivedUrl = null;

		const includeResult = fhirCriteriaListToBuilderRows(payload.compose?.include);
		const excludeResult = fhirCriteriaListToBuilderRows(payload.compose?.exclude);
		if (includeResult.hasNestedValueSet || excludeResult.hasNestedValueSet) {
			throw new Error('This ValueSet uses nested ValueSet references which cannot be edited in the builder.');
		}

		this.addValueSetIncludes = (includeResult.rows.length ? includeResult.rows : [createValueSetCriteriaRow()])
			.map(row => ({ ...row }));
		this.addValueSetExcludes = excludeResult.rows.map(row => ({ ...row }));

		const skipped = includeResult.skipped + excludeResult.skipped;
		this.addValueSetEditWarning = skipped
			? `${skipped} compose filter(s) could not be mapped to the builder and were omitted.`
			: null;
	},

	async openEditValueSet(id) {
		if (!id || !this.isValueSetEditable({ id })) return;
		this.addValueSetEditingLoading = true;
		this.addValueSetError = null;
		this.addValueSetEditWarning = null;
		try {
			const res = await fetchWithTimeout(
				this.fhirBaseUrl + '/ValueSet/' + encodeURIComponent(id),
				AJAX_TIMEOUT_MS
			);
			const data = await res.json();
			if (!res.ok) {
				const msg = data.issue && data.issue[0] && (data.issue[0].diagnostics || (data.issue[0].details && data.issue[0].details.text));
				throw new Error(msg || data.message || 'Failed to load ValueSet');
			}
			this.addValueSetEditingId = id;
			this.addValueSetInputMode = 'builder';
			this.showAddValueSetForm = true;
			this.tab = 'valueset';
			this.populateBuilderFromValueSet(data);
			const modalEl = document.getElementById('valuesetModal');
			const modal = bootstrap.Modal.getInstance(modalEl);
			if (modal) modal.hide();
			this.$nextTick(() => {
				this.addValueSetIncludes = this.addValueSetIncludes.map(row => ({ ...row }));
				this.addValueSetExcludes = this.addValueSetExcludes.map(row => ({ ...row }));
				if (this.$refs.addValueSetForm) {
					this.$refs.addValueSetForm.scrollIntoView({ behavior: 'smooth', block: 'start' });
				}
			});
		} catch (err) {
			this.addValueSetError = err.message || 'Failed to load ValueSet for editing';
			this.showAddValueSetForm = true;
			this.tab = 'valueset';
		} finally {
			this.addValueSetEditingLoading = false;
		}
	},

	toggleAddValueSetForm() {
		if (this.showAddValueSetForm) {
			this.clearAddValueSetForm();
			this.showAddValueSetForm = false;
		} else {
			this.clearAddValueSetForm();
			this.addValueSetInputMode = 'builder';
			this.showAddValueSetForm = true;
		}
	},

	onValueSetFileSelected(event) {
		this.addValueSetError = null;
		const file = event.target.files && event.target.files[0];
		if (!file) return;
		// Note: we deliberately do NOT clear event.target.value here, so the native control keeps showing the
		// chosen file name. Re-picking the same file still works because @click resets the input first.
		const reader = new FileReader();
		reader.onload = () => {
			this.parseAddValueSetJson((reader.result || '').trim());
		};
		reader.onerror = () => {
			this.addValueSetError = 'Failed to read file';
		};
		reader.readAsText(file);
	},

	/** Called on blur/change of the "Paste JSON" textarea, mirroring the file-upload parse-and-populate flow. */
	onValueSetJsonPasted() {
		const text = (this.addValueSetJson || '').trim();
		if (!text) {
			this.addValueSetPayload = null;
			return;
		}
		this.parseAddValueSetJson(text);
	},

	parseAddValueSetJson(text) {
		let payload;
		try {
			payload = JSON.parse(text);
		} catch (e) {
			this.resetAddValueSetFields();
			this.addValueSetError = 'Invalid JSON: ' + (e.message || 'parse error');
			return;
		}
		if (payload.resourceType !== 'ValueSet') {
			this.resetAddValueSetFields();
			this.addValueSetError = 'Resource must be a ValueSet (resourceType: "ValueSet")';
			return;
		}
		this.addValueSetPayload = payload;
		this.addValueSetUrl = payload.url != null ? String(payload.url) : '';
		this.addValueSetVersion = payload.version != null ? String(payload.version) : FHIR_RESOURCE_DEFAULT_VERSION;
		this.addValueSetTitle = payload.title != null ? String(payload.title) : '';
		const urlFromPayload = (this.addValueSetUrl || '').trim();
		if (!urlFromPayload) {
			this.addValueSetName = slugifyResourceName(this.addValueSetTitle);
		} else {
			const existingName = payload.name != null ? String(payload.name).trim() : '';
			this.addValueSetName = existingName || slugifyResourceName(this.addValueSetTitle);
		}
		this.addValueSetDescription = payload.description != null ? String(payload.description) : '';
		this.addValueSetStatus = normalizeResourceStatus(payload.status);
		this.addValueSetExperimental = payload.experimental === true;
		this._addValueSetDerivedName = null;
		this._addValueSetDerivedUrl = null;
		if (!(this.addValueSetUrl || '').trim()) this.syncValueSetUrlFromName();
		this.addValueSetError = null;
	},

	resetAddValueSetFields() {
		this.addValueSetPayload = null;
		this.addValueSetUrl = '';
		this.addValueSetVersion = FHIR_RESOURCE_DEFAULT_VERSION;
		this.addValueSetTitle = '';
		this.addValueSetName = '';
		this.addValueSetStatus = 'draft';
		this.addValueSetDescription = '';
		this.addValueSetExperimental = false;
		this._addValueSetDerivedName = '';
		this._addValueSetDerivedUrl = '';
	},

	resolvedAddValueSetName() {
		let n = (this.addValueSetName || '').trim();
		if (!n) n = slugifyResourceName((this.addValueSetTitle || '').trim());
		return n;
	},

	syncValueSetUrlFromName() {
		const name = this.resolvedAddValueSetName();
		if (!name) return;
		const derived = this.valueSetDefaultUrlPrefix + name;
		const current = (this.addValueSetUrl || '').trim();
		if (!current || this.addValueSetUrl === this._addValueSetDerivedUrl) {
			this.addValueSetUrl = derived;
			this._addValueSetDerivedUrl = derived;
		}
	},

	syncValueSetNameFromTitle() {
		const derived = slugifyResourceName(this.addValueSetTitle || '');
		const current = (this.addValueSetName || '').trim();
		if (!current || this.addValueSetName === this._addValueSetDerivedName) {
			this.addValueSetName = derived;
			this._addValueSetDerivedName = derived;
		}
		this.syncValueSetUrlFromName();
	},

	buildBuilderValueSetPayload() {
		const payload = {
			resourceType: 'ValueSet',
			url: (this.addValueSetUrl || '').trim(),
			version: (this.addValueSetVersion || '').trim(),
			status: this.addValueSetStatus || 'draft',
			compose: {
				include: mapValidCriteriaRows(this.addValueSetIncludes)
			}
		};
		const excludes = mapValidCriteriaRows(this.addValueSetExcludes);
		if (excludes.length) payload.compose.exclude = excludes;
		const title = (this.addValueSetTitle || '').trim();
		if (title) payload.title = title;
		const name = this.resolvedAddValueSetName();
		if (name) payload.name = name;
		const description = (this.addValueSetDescription || '').trim();
		if (description) payload.description = description;
		payload.experimental = !!this.addValueSetExperimental;
		if (this.addValueSetEditingId) payload.id = this.addValueSetEditingId;
		return payload;
	},

	async previewValueSetBuilder() {
		this.addValueSetBuilderPreviewError = null;
		this.addValueSetBuilderPreview = null;
		if (!this.isValueSetBuilderContentValid()) {
			this.addValueSetBuilderPreviewError = 'At least one include with system and content is required.';
			return;
		}
		this.addValueSetBuilderPreviewLoading = true;
		try {
			const valueSet = this.buildBuilderValueSetPayload();
			if (!(valueSet.url || '').trim()) valueSet.url = 'http://example.com/fhir/ValueSet/preview';
			if (!(valueSet.version || '').trim()) valueSet.version = 'preview';
			const res = await fetchWithTimeout(this.fhirBaseUrl + '/ValueSet/$expand', AJAX_TIMEOUT_MS, {
				method: 'POST',
				headers: { 'Content-Type': 'application/fhir+json' },
				body: JSON.stringify({
					resourceType: 'Parameters',
					parameter: [{ name: 'valueSet', resource: valueSet }]
				})
			});
			const data = await res.json();
			if (!res.ok) {
				const msg = data.issue && data.issue[0] && (data.issue[0].diagnostics || (data.issue[0].details && data.issue[0].details.text));
				throw new Error(msg || data.message || 'Failed to preview ValueSet');
			}
			const expansion = data.expansion || {};
			const contains = expansion.contains || [];
			this.addValueSetBuilderPreview = {
				total: expansion.total != null ? expansion.total : contains.length,
				concepts: contains.slice(0, 10).map(c => ({ code: c.code, display: c.display }))
			};
		} catch (err) {
			this.addValueSetBuilderPreviewError = err.message || 'Failed to preview ValueSet';
		} finally {
			this.addValueSetBuilderPreviewLoading = false;
		}
	},

	async submitAddValueSet() {
		this.addValueSetError = null;
		let payload;
		if (this.addValueSetInputMode === 'builder') {
			if (!(this.addValueSetUrl || '').trim()) {
				this.addValueSetError = 'URL is required.';
				return;
			}
			if (!(this.addValueSetVersion || '').trim()) {
				this.addValueSetError = 'Version is required.';
				return;
			}
			if (!this.isValueSetBuilderContentValid()) {
				this.addValueSetError = 'At least one include with system and content is required.';
				return;
			}
			payload = this.buildBuilderValueSetPayload();
		} else {
			if (!this.addValueSetPayload) return;
			const url = (this.addValueSetUrl || '').trim();
			const version = (this.addValueSetVersion || '').trim();
			if (!url || !version) {
				this.addValueSetError = 'URL and version are required.';
				return;
			}
			payload = JSON.parse(JSON.stringify(this.addValueSetPayload));
			payload.url = url;
			payload.version = version;
			const title = (this.addValueSetTitle || '').trim();
			if (title) payload.title = title;
			else delete payload.title;
			const name = this.resolvedAddValueSetName();
			if (name) payload.name = name;
			else delete payload.name;
			payload.status = this.addValueSetStatus;
			const desc = (this.addValueSetDescription || '').trim();
			if (desc) payload.description = desc;
			else delete payload.description;
			payload.experimental = !!this.addValueSetExperimental;
		}
		const editingId = this.addValueSetEditingId;
		this.addValueSetSaving = true;
		try {
			const res = await fetchWithTimeout(
				editingId
					? this.fhirBaseUrl + '/ValueSet/' + encodeURIComponent(editingId)
					: this.fhirBaseUrl + '/ValueSet',
				AJAX_TIMEOUT_MS,
				{
					method: editingId ? 'PUT' : 'POST',
					headers: { 'Content-Type': 'application/fhir+json' },
					body: JSON.stringify(payload)
				}
			);
			const data = await res.json().catch(() => ({}));
			if (!res.ok) {
				const msg = data.issue && data.issue[0] && (data.issue[0].diagnostics || data.issue[0].details && data.issue[0].details.text);
				throw new Error(msg || data.message || (editingId ? 'Failed to update ValueSet' : 'Failed to add ValueSet'));
			}
			this.clearAddValueSetForm();
			this.showAddValueSetForm = false;
			await this.loadValueSets();
			alert(editingId ? 'ValueSet updated successfully.' : 'ValueSet added successfully.');
		} catch (err) {
			this.addValueSetError = err.message || (editingId ? 'Failed to update ValueSet' : 'Failed to add ValueSet');
		} finally {
			this.addValueSetSaving = false;
		}
	},

	clearAddValueSetForm() {
		this.addValueSetJson = '';
		this.addValueSetError = null;
		this.addValueSetEditWarning = null;
		this.addValueSetEditingId = null;
		this.addValueSetInputMode = 'builder';
		this.resetAddValueSetFields();
		this.addValueSetIncludes = [createValueSetCriteriaRow()];
		this.addValueSetExcludes = [];
		this.addValueSetBuilderPreview = null;
		this.addValueSetBuilderPreviewError = null;
		// Reset the native file control so it returns to "No file chosen".
		if (this.$refs.valueSetFileInput) this.$refs.valueSetFileInput.value = '';
	}
};
