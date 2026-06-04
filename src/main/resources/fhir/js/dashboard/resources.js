import { AJAX_TIMEOUT_MS } from './constants.js';
import { fetchWithTimeout, errorMessage } from './http.js';
import { normalizeRow, dedupeCodeSystemsByUrlVersion } from './resourceTransforms.js';

export const dashboardResources = {
	copyUrl(url) {
		navigator.clipboard.writeText(url);
		this.copiedUrl = url;
		setTimeout(() => { this.copiedUrl = null; }, 1500);
	},

	async loadCodeSystems() {
		this.loadingCodesystems = true;
		this.errorCodesystems = null;
		this.codeSystemWarnings = [];
		let res;
		try {
			res = await fetchWithTimeout(this.fhirBaseUrl + '/CodeSystem', AJAX_TIMEOUT_MS);
			const data = await res.json();
			if (!res.ok) throw new Error(data.message || 'Failed to load CodeSystems');
			if (data.entry && data.entry.length > 0) {
				const rows = data.entry.map(e => normalizeRow(e.resource));
				const deduped = dedupeCodeSystemsByUrlVersion(rows);
				this.codeSystems = deduped.kept;
				this.codeSystemWarnings = deduped.warnings;
			} else {
				this.codeSystems = [];
			}
		} catch (err) {
			this.errorCodesystems = errorMessage(err, 'CodeSystems', res);
			this.codeSystems = [];
		} finally {
			this.loadingCodesystems = false;
		}
	},

	async loadValueSets() {
		this.loadingValueSets = true;
		this.errorValueSets = null;
		this.fullValueSetCache = null;
		let res;
		try {
			res = await fetchWithTimeout(this.fhirBaseUrl + '/ValueSet', AJAX_TIMEOUT_MS);
			const data = await res.json();
			if (!res.ok) throw new Error(data.message || 'Failed to load ValueSets');
			if (data.entry && data.entry.length > 0) {
				this.valueSets = data.entry.map(e => normalizeRow(e.resource));
			} else {
				this.valueSets = [];
			}
		} catch (err) {
			this.errorValueSets = errorMessage(err, 'ValueSets', res);
			this.valueSets = [];
		} finally {
			this.loadingValueSets = false;
		}
	},

	onValueSetFileSelected(event) {
		this.addValueSetError = null;
		const file = event.target.files && event.target.files[0];
		if (!file) return;
		const reader = new FileReader();
		reader.onload = () => {
			this.addValueSetJson = reader.result || '';
		};
		reader.onerror = () => {
			this.addValueSetError = 'Failed to read file';
		};
		reader.readAsText(file);
		event.target.value = '';
	},

	async submitAddValueSet() {
		const jsonStr = this.addValueSetJson.trim();
		if (!jsonStr) return;
		this.addValueSetError = null;
		this.addValueSetSaving = true;
		let payload;
		try {
			payload = JSON.parse(jsonStr);
		} catch (e) {
			this.addValueSetError = 'Invalid JSON: ' + (e.message || 'parse error');
			this.addValueSetSaving = false;
			return;
		}
		if (payload.resourceType !== 'ValueSet') {
			this.addValueSetError = 'Resource must be a ValueSet (resourceType: "ValueSet")';
			this.addValueSetSaving = false;
			return;
		}
		try {
			const res = await fetchWithTimeout(this.fhirBaseUrl + '/ValueSet', AJAX_TIMEOUT_MS, {
				method: 'POST',
				headers: { 'Content-Type': 'application/fhir+json' },
				body: JSON.stringify(payload)
			});
			const data = res.ok ? await res.json().catch(() => ({})) : await res.json().catch(() => ({}));
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
	},

	async loadConceptMaps() {
		this.loadingConceptMaps = true;
		this.errorConceptMaps = null;
		this.fullConceptMapCache = null;
		let res;
		try {
			res = await fetchWithTimeout(this.fhirBaseUrl + '/ConceptMap', AJAX_TIMEOUT_MS);
			const data = await res.json();
			if (!res.ok) throw new Error(data.message || 'Failed to load ConceptMaps');
			if (data.entry && data.entry.length > 0) {
				this.conceptMaps = data.entry.map(e => normalizeRow(e.resource));
			} else {
				this.conceptMaps = [];
			}
		} catch (err) {
			this.errorConceptMaps = errorMessage(err, 'ConceptMaps', res);
			this.conceptMaps = [];
		} finally {
			this.loadingConceptMaps = false;
		}
	},

	async _fetchAllResources(resourceType, extraParams = '') {
		const all = [];
		let url = this.fhirBaseUrl + '/' + resourceType + '?_count=200' + extraParams;
		while (url) {
			const res = await fetchWithTimeout(url, AJAX_TIMEOUT_MS);
			if (!res.ok) break;
			const data = await res.json();
			if (data.entry) {
				for (const e of data.entry) {
					if (e.resource) all.push(e.resource);
				}
			}
			const next = (data.link || []).find(l => l.relation === 'next');
			url = next ? next.url : null;
		}
		return all;
	},

	/** SNOMED implicit ValueSets/ConceptMaps have synthetic ids that cannot be fetched by GET /{type}/{id}. */
	_isImplicitSnomedResource(r) {
		const id = (r && r.id) || '';
		const url = (r && r.url) || '';
		return id.startsWith('snomed_implicit_') ||
			(url.includes('snomed.info/sct') && (url.includes('?fhir_cm=') || url.includes('?fhir_vs=')));
	},

	async _getFullValueSets() {
		if (this.fullValueSetCache) return this.fullValueSetCache;
		const ids = (await this._fetchAllResources('ValueSet'))
			.filter(vs => !this._isImplicitSnomedResource(vs))
			.map(vs => vs.id).filter(Boolean);
		const full = await Promise.all(
			ids.map(id =>
				fetchWithTimeout(this.fhirBaseUrl + '/ValueSet/' + id, AJAX_TIMEOUT_MS)
					.then(r => r.ok ? r.json() : null).catch(() => null)
			)
		);
		this.fullValueSetCache = full.filter(Boolean);
		return this.fullValueSetCache;
	},

	async _getFullConceptMaps() {
		if (this.fullConceptMapCache) return this.fullConceptMapCache;
		const ids = (await this._fetchAllResources('ConceptMap'))
			.filter(cm => !this._isImplicitSnomedResource(cm))
			.map(cm => cm.id).filter(Boolean);
		const full = await Promise.all(
			ids.map(id =>
				fetchWithTimeout(this.fhirBaseUrl + '/ConceptMap/' + id, AJAX_TIMEOUT_MS)
					.then(r => r.ok ? r.json() : null).catch(() => null)
			)
		);
		this.fullConceptMapCache = full.filter(Boolean);
		return this.fullConceptMapCache;
	},

	async _deleteOne(resourceType, id) {
		const res = await fetchWithTimeout(
			this.fhirBaseUrl + '/' + resourceType + '/' + id,
			AJAX_TIMEOUT_MS,
			{ method: 'DELETE' }
		);
		if (!res.ok) {
			const data = await res.json().catch(() => ({}));
			const msg = data.issue && data.issue[0] && (data.issue[0].diagnostics || (data.issue[0].details && data.issue[0].details.text));
			throw new Error(msg || data.message || 'Failed to delete ' + resourceType + '/' + id);
		}
	},

	async confirmDeleteCodeSystem(id, title, url) {
		this.deleteCodeSystemId = id;
		this.deleteCodeSystemTitle = title;
		this.deleteCodeSystemUrl = url;
		this.deleteCodeSystemError = null;
		this.deleteCodeSystemDependents = null;
		this.loadingDeleteDependents = true;
		bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteCodeSystemModal')).show();
		try {
			const [fullValueSets, fullConceptMaps] = await Promise.all([
				this._getFullValueSets(),
				this._getFullConceptMaps()
			]);
			const depValueSets = fullValueSets.filter(vs => {
				const includes = (vs.compose && vs.compose.include) || [];
				return includes.some(inc => inc.system === url);
			}).map(vs => ({ id: vs.id, title: vs.title || vs.name || vs.id }));
			const depConceptMaps = fullConceptMaps.filter(cm => {
				return (cm.group || []).some(g => g.source === url || g.target === url);
			}).map(cm => ({ id: cm.id, title: cm.title || cm.name || cm.id }));
			this.deleteCodeSystemDependents = { valueSets: depValueSets, conceptMaps: depConceptMaps };
		} catch (err) {
			this.deleteCodeSystemError = err.message || 'Error analysing dependencies';
		} finally {
			this.loadingDeleteDependents = false;
		}
	},

	async executeDeleteCodeSystem() {
		if (!this.deleteCodeSystemId) return;
		this.deletingCodeSystem = true;
		this.deleteCodeSystemError = null;
		try {
			const deps = this.deleteCodeSystemDependents || { valueSets: [], conceptMaps: [] };
			for (const cm of deps.conceptMaps) await this._deleteOne('ConceptMap', cm.id);
			for (const vs of deps.valueSets) await this._deleteOne('ValueSet', vs.id);
			await this._deleteOne('CodeSystem', this.deleteCodeSystemId);
			bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteCodeSystemModal')).hide();
			await Promise.all([this.loadCodeSystems(), this.loadValueSets(), this.loadConceptMaps()]);
		} catch (err) {
			this.deleteCodeSystemError = err.name === 'AbortError' ? 'Request timed out. Please try again.' : (err.message || 'Failed to delete');
		} finally {
			this.deletingCodeSystem = false;
		}
	},

	async confirmDeleteValueSet(id, title, url) {
		this.deleteValueSetId = id;
		this.deleteValueSetTitle = title;
		this.deleteValueSetUrl = url;
		this.deleteValueSetError = null;
		this.deleteValueSetDependents = null;
		this.loadingDeleteDependents = true;
		bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteValueSetModal')).show();
		try {
			const fullConceptMaps = await this._getFullConceptMaps();
			const depConceptMaps = fullConceptMaps.filter(cm => {
				return (cm.group || []).some(g => g.source === url || g.target === url);
			}).map(cm => ({ id: cm.id, title: cm.title || cm.name || cm.id }));
			this.deleteValueSetDependents = { conceptMaps: depConceptMaps };
		} catch (err) {
			this.deleteValueSetError = err.message || 'Error analysing dependencies';
		} finally {
			this.loadingDeleteDependents = false;
		}
	},

	async executeDeleteValueSet() {
		if (!this.deleteValueSetId) return;
		this.deletingValueSet = true;
		this.deleteValueSetError = null;
		try {
			const deps = this.deleteValueSetDependents || { conceptMaps: [] };
			for (const cm of deps.conceptMaps) await this._deleteOne('ConceptMap', cm.id);
			await this._deleteOne('ValueSet', this.deleteValueSetId);
			bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteValueSetModal')).hide();
			await Promise.all([this.loadValueSets(), this.loadConceptMaps()]);
		} catch (err) {
			this.deleteValueSetError = err.name === 'AbortError' ? 'Request timed out. Please try again.' : (err.message || 'Failed to delete');
		} finally {
			this.deletingValueSet = false;
		}
	},

	async executeDeleteAll() {
		this.deletingAll = true;
		this.deleteAllError = null;
		this.deleteAllProgress = 'Fetching resources…';
		try {
			const isSnomed = url => (url || '').includes('http://snomed.info/sct');
			const [allCMs, allVSs, allCSs] = await Promise.all([
				this._fetchAllResources('ConceptMap'),
				this._fetchAllResources('ValueSet'),
				this._fetchAllResources('CodeSystem')
			]);
			const customCMs = allCMs.filter(r => !isSnomed(r.url));
			const customVSs = allVSs.filter(r => !isSnomed(r.url));
			const customCSs = allCSs.filter(r => !isSnomed(r.url));
			for (let i = 0; i < customCMs.length; i++) {
				this.deleteAllProgress = `Deleting ConceptMaps… ${i + 1}/${customCMs.length}`;
				await this._deleteOne('ConceptMap', customCMs[i].id);
			}
			for (let i = 0; i < customVSs.length; i++) {
				this.deleteAllProgress = `Deleting ValueSets… ${i + 1}/${customVSs.length}`;
				await this._deleteOne('ValueSet', customVSs[i].id);
			}
			for (let i = 0; i < customCSs.length; i++) {
				this.deleteAllProgress = `Deleting CodeSystems… ${i + 1}/${customCSs.length}`;
				await this._deleteOne('CodeSystem', customCSs[i].id);
			}
			bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteAllModal')).hide();
			this.deleteAllProgress = null;
			await Promise.all([this.loadCodeSystems(), this.loadValueSets(), this.loadConceptMaps()]);
			if (typeof this.loadCustomResourceCounts === 'function') {
				this.loadCustomResourceCounts();
			}
		} catch (err) {
			this.deleteAllError = err.name === 'AbortError' ? 'Request timed out. Please try again.' : (err.message || 'Failed to delete');
			this.deleteAllProgress = null;
		} finally {
			this.deletingAll = false;
		}
	},

	async resetSnomedCodeSystem() {
		this.resettingSnomed = true;
		this.resetSnomedError = null;
		try {
			const res = await fetch(new URL('/fhir-admin/clear-snomed', window.location.origin).href, { method: 'POST' });
			if (!res.ok) {
				const data = await res.json().catch(() => ({}));
				const msg = data.issue && data.issue[0] && (data.issue[0].diagnostics || (data.issue[0].details && data.issue[0].details.text));
				throw new Error(msg || `Failed to reset SNOMED CT (HTTP ${res.status})`);
			}
			bootstrap.Modal.getOrCreateInstance(document.getElementById('resetSnomedModal')).hide();
			await Promise.all([this.loadCodeSystems(), this.loadValueSets(), this.loadConceptMaps()]);
		} catch (err) {
			this.resetSnomedError = err.message || 'Failed to reset SNOMED CT';
		} finally {
			this.resettingSnomed = false;
		}
	}
};
