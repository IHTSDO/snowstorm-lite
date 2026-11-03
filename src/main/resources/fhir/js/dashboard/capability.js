import { AJAX_TIMEOUT_MS, SYNDICATION_TIMEOUT_MS } from './constants.js';
import { fetchWithTimeout } from './http.js';

export const dashboardCapability = {
	/** Check whether any SNOMED CT CodeSystem version is loaded on the server. */
	async refreshSnomedLoaded() {
		try {
			const res = await fetchWithTimeout(this.fhirBaseUrl + '/CodeSystem', AJAX_TIMEOUT_MS);
			if (!res.ok) return;
			const data = await res.json();
			this.snomedLoaded = (data.entry || []).some(e => String(e.resource?.url || '').includes('http://snomed.info/sct'));
		} catch (err) {
			// Leave unknown; the nav link stays enabled
		}
	},

	async loadCapabilityStatement() {
		this.refreshSnomedLoaded();
		try {
			const res = await fetchWithTimeout(this.fhirBaseUrl + '/metadata', AJAX_TIMEOUT_MS);
			if (res.ok) {
				this.capabilityStatement = await res.json();
			}
		} catch (err) {
			// Silently fail - assume all operations supported if metadata unavailable
		} finally {
			await this.checkSyndicationAvailability();
		}
	},

	async checkSyndicationAvailability() {
		try {
			const res = await fetchWithTimeout(this.syndicationUrl('snomed-editions'), SYNDICATION_TIMEOUT_MS, {
				method: 'OPTIONS'
			});
			this.syndicationAvailable = res.ok;
		} catch (err) {
			this.syndicationAvailable = false;
		}
		if (!this.syndicationAvailable && this.section === 'syndication') {
			this.section = 'resources';
			this.tab = 'codesystem';
			this.setHash();
			this.loadTabIfNeeded();
		}
	}
};
