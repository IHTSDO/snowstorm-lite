import { AJAX_TIMEOUT_MS, VALUESET_CRITERIA_TYPES, VALUESET_DEFAULT_SYSTEM } from './constants.js';
import { normalizeResourceStatus, slugifyResourceName } from './resourceFormHelpers.js';
import { fetchWithTimeout } from './http.js';

let valueSetCriteriaRowSeq = 0;

export function createValueSetCriteriaRow() {
	valueSetCriteriaRowSeq += 1;
	return {
		id: String(valueSetCriteriaRowSeq),
		system: VALUESET_DEFAULT_SYSTEM,
		version: '',
		criteriaType: 'constraint',
		value: ''
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
		this.addValueSetVersion = payload.version != null ? String(payload.version) : '';
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
		this.addValueSetVersion = '';
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
		this.addValueSetSaving = true;
		try {
			const res = await fetchWithTimeout(this.fhirBaseUrl + '/ValueSet', AJAX_TIMEOUT_MS, {
				method: 'POST',
				headers: { 'Content-Type': 'application/fhir+json' },
				body: JSON.stringify(payload)
			});
			const data = await res.json().catch(() => ({}));
			if (!res.ok) {
				const msg = data.issue && data.issue[0] && (data.issue[0].diagnostics || data.issue[0].details && data.issue[0].details.text);
				throw new Error(msg || data.message || 'Failed to add ValueSet');
			}
			this.clearAddValueSetForm();
			this.showAddValueSetForm = false;
			await this.loadValueSets();
			alert('ValueSet added successfully.');
		} catch (err) {
			this.addValueSetError = err.message || 'Failed to add ValueSet';
		} finally {
			this.addValueSetSaving = false;
		}
	},

	clearAddValueSetForm() {
		this.addValueSetJson = '';
		this.addValueSetError = null;
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
