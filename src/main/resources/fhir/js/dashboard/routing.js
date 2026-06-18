import { SETTINGS_STORAGE_KEYS, readStoredSetting } from './settings.js';

function fhirBaseUrlFromPageLocation() {
	const u = new URL(window.location.href);
	let path = u.pathname.replace(/\/index\.html$/i, '');
	path = path.replace(/\/+$/, '') || '/';
	return `${u.origin}${path}`.replace(/\/$/, '');
}

export function getInitialFhirBaseUrl() {
	// Precedence: explicit ?tx= deep link > stored setting > page location
	const tx = new URLSearchParams(window.location.search).get('tx');
	if (tx) {
		return tx.replace(/\/$/, '');
	}
	const stored = readStoredSetting(SETTINGS_STORAGE_KEYS.fhirBaseUrl);
	if (stored) {
		return stored.replace(/\/$/, '');
	}
	return fhirBaseUrlFromPageLocation();
}

export const dashboardRouting = {
	init() {
		this.fhirBaseUrl = getInitialFhirBaseUrl();
		this.settingsFhirUrlInput = this.fhirBaseUrl;
		if (!new URLSearchParams(window.location.search).get('tx')) {
			const url = new URL(window.location.href);
			url.searchParams.set('tx', this.fhirBaseUrl);
			history.replaceState(null, '', url.pathname + url.search + url.hash);
		}
		this.continueDashboardInit();
	},

	defaultFhirBaseUrl() {
		return fhirBaseUrlFromPageLocation();
	},

	continueDashboardInit() {
		this.loadCapabilityStatement();
		window.addEventListener('hashchange', () => this.initFromHash());
		this.initFromHash();
		this.routingInitialized = true;
	},

	refreshAfterTxUrlChange() {
		this.loadCapabilityStatement();
		this.codeSystems = [];
		this.valueSets = [];
		this.conceptMaps = [];
		this.editions = [];
		this.snomedCodeSystems = [];
		this.installState = {};
		this.installTaskSnapshotByEditionId = {};
		if (this._installationPollTaskIds) {
			this._installationPollTaskIds.clear();
		}
		if (typeof this.resetSnomedBrowserState === 'function') {
			this.resetSnomedBrowserState();
		}
		this.errorCodesystems = null;
		this.errorValueSets = null;
		this.errorConceptMaps = null;
		this.loadTabIfNeeded();
		this.loadSyndicationIfNeeded();
		this.loadSnomedMiniBrowserIfNeeded();
	},

	initFromHash() {
		const hash = window.location.hash.replace('#', '');
		if (!hash) {
			this.section = 'resources';
			this.tab = 'codesystem';
			this.setHash();
			this.loadCodeSystems();
			return;
		}
		const parts = hash.split('/');
		const sectionKey = parts[0];
		const tab = parts[1];

		if (sectionKey === 'snomed-mini-browser') {
			this.section = 'snomed-mini-browser';
			this.loadSnomedMiniBrowserIfNeeded();
			return;
		}

		if (sectionKey === 'resources') {
			this.section = 'resources';
			if (tab === 'snomed') {
				this.section = 'snomed-mini-browser';
				window.location.hash = 'snomed-mini-browser';
				this.loadSnomedMiniBrowserIfNeeded();
				return;
			}
			if (tab === 'codesystem' || tab === 'valueset' || tab === 'conceptmap') {
				this.tab = tab;
				this.loadTabIfNeeded();
			}
			return;
		}

		if (sectionKey === 'syndication') {
			this.section = 'syndication';
			this.loadSyndicationIfNeeded();
			return;
		}

		if (sectionKey === 'settings') {
			this.section = 'settings';
			this.loadSettingsIfNeeded();
			return;
		}

		if (sectionKey === 'upload-sct') {
			this.section = 'upload-sct';
		}
	},

	setHash() {
		if (this.section === 'resources') {
			window.location.hash = `resources/${this.tab}`;
		} else {
			window.location.hash = this.section;
		}
	},

	switchTab(t) {
		this.tab = t;
		this.setHash();
		this.loadTabIfNeeded();
	},

	loadTabIfNeeded() {
		if (this.tab === 'codesystem' && this.codeSystems.length === 0 && !this.loadingCodesystems) {
			this.loadCodeSystems();
		} else if (this.tab === 'valueset' && this.valueSets.length === 0 && !this.loadingValueSets) {
			this.loadValueSets();
		} else if (this.tab === 'conceptmap' && this.conceptMaps.length === 0 && !this.loadingConceptMaps) {
			this.loadConceptMaps();
		}
	},

	loadSnomedMiniBrowserIfNeeded() {
		if (this.section !== 'snomed-mini-browser') {
			return;
		}
		if (!this.snomedBrowserInitialized) {
			this.initSnomedBrowserTab();
		}
	},

	async loadSyndicationIfNeeded() {
		if (!this.syndicationAvailable) {
			return;
		}
		// Re-apply stored feed URL + username before loading, so the feed loads from the
		// configured server after a restart (the password is re-entered separately in Settings).
		if (!this._syndicationSettingsApplied) {
			this._syndicationSettingsApplied = true;
			if (typeof this.applyStoredSyndicationSettings === 'function') {
				await this.applyStoredSyndicationSettings();
			}
		}
		if (this.editions.length === 0 && !this.loadingSyndication) {
			this.loadSyndicationEditions();
		} else if (this.editions.length > 0) {
			this.syncActiveInstallationTasks();
		}
	}
};
