export const SETTINGS_STORAGE_KEYS = {
	fhirBaseUrl: 'dashboard.fhirBaseUrl',
	syndicationFeedUrl: 'dashboard.syndicationFeedUrl',
	syndicationFeedUsername: 'dashboard.syndicationFeedUsername'
};

export function readStoredSetting(key) {
	try {
		const v = localStorage.getItem(key);
		return v != null && v !== '' ? v : null;
	} catch {
		return null;
	}
}

export function writeStoredSetting(key, value) {
	try {
		if (value == null || value === '') {
			localStorage.removeItem(key);
		} else {
			localStorage.setItem(key, value);
		}
	} catch {
		// localStorage unavailable (private mode etc.) — settings just won't persist
	}
}

function isValidHttpUrl(raw) {
	let parsed;
	try {
		parsed = new URL(raw);
	} catch {
		return false;
	}
	return parsed.protocol === 'http:' || parsed.protocol === 'https:';
}

export const dashboardSettings = {
	openSettings() {
		this.section = 'settings';
		this.setHash();
		this.loadSettingsIfNeeded();
	},

	loadSettingsIfNeeded() {
		// FHIR API URL: always reflect the live value
		this.settingsFhirUrlInput = this.fhirBaseUrl;
		this.settingsFhirUrlError = null;
		this.settingsFhirUrlSuccess = null;
		if (!this.settingsFeedLoaded) {
			this.loadSyndicationFeedConfig();
		}
		this.loadCustomResourceCounts();
	},

	/** Count custom (non-SNOMED) FHIR resources for the Settings danger zone. */
	async loadCustomResourceCounts() {
		if (this.settingsCustomCountsLoading) return;
		this.settingsCustomCountsLoading = true;
		try {
			const isSnomed = url => (url || '').includes('http://snomed.info/sct');
			const [cms, vss, css] = await Promise.all([
				this._fetchAllResources('ConceptMap'),
				this._fetchAllResources('ValueSet'),
				this._fetchAllResources('CodeSystem')
			]);
			this.settingsCustomConceptMaps = cms.filter(r => !isSnomed(r.url)).length;
			this.settingsCustomValueSets = vss.filter(r => !isSnomed(r.url)).length;
			this.settingsCustomCodeSystems = css.filter(r => !isSnomed(r.url)).length;
			this.settingsCustomTotal = this.settingsCustomCodeSystems + this.settingsCustomValueSets + this.settingsCustomConceptMaps;
			this.settingsCustomCountsLoaded = true;
		} catch {
			// counts are informational; ignore transient errors
		} finally {
			this.settingsCustomCountsLoading = false;
		}
	},

	async loadSyndicationFeedConfig() {
		try {
			const res = await fetch('/syndication/feed-config');
			if (!res.ok) return;
			const data = await res.json();
			this.settingsFeedUrl = data.url || null;
			this.settingsFeedDefaultUrl = data.defaultUrl || null;
			this.settingsFeedUsername = data.username || '';
			this.settingsFeedPasswordSet = !!data.passwordSet;
			// Prefer locally-saved values (the user's intent, which survives server restarts);
			// fall back to the server's current values.
			const storedUrl = readStoredSetting(SETTINGS_STORAGE_KEYS.syndicationFeedUrl);
			const storedUsername = readStoredSetting(SETTINGS_STORAGE_KEYS.syndicationFeedUsername);
			this.settingsFeedUrlInput = storedUrl || this.settingsFeedUrl || '';
			this.settingsFeedUsernameInput = storedUsername != null ? storedUsername : this.settingsFeedUsername;
			this.settingsFeedPasswordInput = '';
			this.settingsFeedLoaded = true;
		} catch {
			// feed config display is informational; ignore transient errors
		}
	},

	saveFhirApiUrl() {
		const raw = (this.settingsFhirUrlInput || '').trim();
		this.settingsFhirUrlError = null;
		this.settingsFhirUrlSuccess = null;
		if (!raw) {
			this.settingsFhirUrlError = 'Please enter a FHIR Terminology Server URL.';
			return;
		}
		if (!isValidHttpUrl(raw)) {
			this.settingsFhirUrlError = 'Please enter a valid http or https URL (e.g. https://example.com/fhir).';
			return;
		}
		const parsed = new URL(raw);
		const base = `${parsed.origin}${parsed.pathname}`.replace(/\/$/, '');
		this.fhirBaseUrl = base;
		writeStoredSetting(SETTINGS_STORAGE_KEYS.fhirBaseUrl, base);
		const url = new URL(window.location.href);
		url.searchParams.set('tx', base);
		history.replaceState(null, '', url.pathname + url.search + url.hash);
		this.settingsFhirUrlInput = base;
		this.settingsFhirUrlSuccess = 'FHIR API URL updated.';
		this.refreshAfterTxUrlChange();
	},

	resetFhirApiUrl() {
		writeStoredSetting(SETTINGS_STORAGE_KEYS.fhirBaseUrl, null);
		const fallback = (typeof this.defaultFhirBaseUrl === 'function' ? this.defaultFhirBaseUrl() : null) || window.location.origin + '/fhir';
		this.settingsFhirUrlInput = fallback;
		this.saveFhirApiUrl();
	},

	async saveSyndicationFeedSettings() {
		const url = (this.settingsFeedUrlInput || '').trim();
		const username = (this.settingsFeedUsernameInput || '').trim();
		const password = this.settingsFeedPasswordInput; // do not trim — password may have spaces
		this.settingsFeedError = null;
		this.settingsFeedSuccess = null;
		if (!url) {
			this.settingsFeedError = 'Feed URL is required.';
			return;
		}
		if (!isValidHttpUrl(url)) {
			this.settingsFeedError = 'Please enter a valid http or https feed URL.';
			return;
		}
		this.settingsFeedSaving = true;
		try {
			// URL + username: not secrets, no auth required
			const res = await fetch('/syndication/feed-config', {
				method: 'PUT',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ url, username })
			});
			if (!res.ok) {
				const data = await res.json().catch(() => ({}));
				throw new Error(data.message || `Failed to update feed settings (HTTP ${res.status})`);
			}
			// Password: only sent when the user typed one. Requires admin auth (browser prompt).
			if (password) {
				const pwRes = await fetch('/syndication/feed-password', {
					method: 'PUT',
					headers: { 'Content-Type': 'application/json' },
					body: JSON.stringify({ password })
				});
				if (!pwRes.ok) {
					const data = await pwRes.json().catch(() => ({}));
					throw new Error(data.message || `Failed to update feed password (HTTP ${pwRes.status})`);
				}
			}
			// Persist URL + username only (never the password)
			writeStoredSetting(SETTINGS_STORAGE_KEYS.syndicationFeedUrl, url);
			writeStoredSetting(SETTINGS_STORAGE_KEYS.syndicationFeedUsername, username);
			this.settingsFeedUrl = url;
			this.settingsFeedUsername = username;
			if (password) {
				this.settingsFeedPasswordSet = true;
			}
			this.settingsFeedPasswordInput = '';
			this.settingsFeedSuccess = 'Syndication feed settings updated.';
			// Reload editions against the new feed
			this.editions = [];
			this.loadSyndicationEditions();
		} catch (err) {
			this.settingsFeedError = err.message || 'Failed to update feed settings';
		} finally {
			this.settingsFeedSaving = false;
		}
	},

	async resetSyndicationFeedUrlToDefault() {
		if (!this.settingsFeedDefaultUrl) return;
		this.settingsFeedUrlInput = this.settingsFeedDefaultUrl;
		await this.saveSyndicationFeedSettings();
	},

	/**
	 * Re-apply the locally-stored feed URL + username to the server so settings survive a server restart.
	 * Targets the no-auth feed-config endpoint (URL/username are not secrets), so this never triggers a
	 * Basic Auth prompt on load. The password is never stored and must be re-entered in Settings after a restart.
	 */
	async applyStoredSyndicationSettings() {
		const storedUrl = readStoredSetting(SETTINGS_STORAGE_KEYS.syndicationFeedUrl);
		const storedUsername = readStoredSetting(SETTINGS_STORAGE_KEYS.syndicationFeedUsername);
		if (!storedUrl && storedUsername == null) {
			return;
		}
		try {
			const body = {};
			if (storedUrl) body.url = storedUrl;
			if (storedUsername != null) body.username = storedUsername;
			await fetch('/syndication/feed-config', {
				method: 'PUT',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify(body)
			});
		} catch {
			// best-effort sync; ignore failures
		}
	}
};
