import { errorMessage } from './http.js';
import { snomedAllConceptsUrl } from './snomedBrowser.js';

export const ECL_TYPEAHEAD_MIN_CHARS = 3;
export const ECL_TYPEAHEAD_DEBOUNCE_MS = 300;
export const ECL_TYPEAHEAD_COUNT = 20;

export const eclConceptTypeaheadState = {
	eclTypeaheadKey: null,
	eclTypeaheadResults: [],
	eclTypeaheadLoading: false,
	eclTypeaheadOpen: false,
	eclTypeaheadError: null,
	_eclTypeaheadDebounceTimer: null,
	_eclTypeaheadRequestId: 0,
	_eclTypeaheadBlurTimer: null
};

export const dashboardEclConceptTypeahead = {
	eclTypeaheadFieldKey(...parts) {
		return parts.filter(p => p != null && p !== '').join('-');
	},

	eclTypeaheadSubKey(field) {
		return this.eclTypeaheadFieldKey('sub', this.eclBuilderStack?.length ?? 0, field);
	},

	eclTypeaheadAttrKey(aIdx, part, field) {
		return this.eclTypeaheadFieldKey('attr', aIdx, part, field);
	},

	eclTypeaheadIsOpen(key) {
		return this.eclTypeaheadOpen
			&& this.eclTypeaheadKey === key
			&& (this.eclTypeaheadLoading || this.eclTypeaheadResults.length > 0 || !!this.eclTypeaheadError);
	},

	eclTypeaheadClearDebounceTimer() {
		const t = this._eclTypeaheadDebounceTimer;
		if (t != null) {
			clearTimeout(t);
			this._eclTypeaheadDebounceTimer = null;
		}
	},

	eclTypeaheadClearBlurTimer() {
		const t = this._eclTypeaheadBlurTimer;
		if (t != null) {
			clearTimeout(t);
			this._eclTypeaheadBlurTimer = null;
		}
	},

	eclTypeaheadClearResults() {
		this.eclTypeaheadResults = [];
		this.eclTypeaheadLoading = false;
		this.eclTypeaheadOpen = false;
		this.eclTypeaheadError = null;
	},

	eclTypeaheadClose() {
		this.eclTypeaheadClearDebounceTimer();
		this.eclTypeaheadClearBlurTimer();
		this.eclTypeaheadKey = null;
		this.eclTypeaheadClearResults();
	},

	eclTypeaheadOnInput(key, targetModel, event) {
		if (!targetModel) return;
		const query = String(event?.target?.value ?? '').trim();
		this.eclTypeaheadKey = key;
		this.eclTypeaheadClearDebounceTimer();
		this.eclTypeaheadClearBlurTimer();

		if (query.length < ECL_TYPEAHEAD_MIN_CHARS) {
			this.eclTypeaheadClearResults();
			return;
		}

		this.eclTypeaheadScheduleSearch(key, query, targetModel);
	},

	eclTypeaheadScheduleSearch(key, query, targetModel) {
		this.eclTypeaheadClearDebounceTimer();
		this._eclTypeaheadDebounceTimer = setTimeout(() => {
			this._eclTypeaheadDebounceTimer = null;
			void this.eclTypeaheadRunSearch(key, query, targetModel);
		}, ECL_TYPEAHEAD_DEBOUNCE_MS);
	},

	async eclTypeaheadRunSearch(key, query, targetModel) {
		if (!targetModel || !query || query.length < ECL_TYPEAHEAD_MIN_CHARS) {
			return;
		}

		this.eclTypeaheadKey = key;
		this.eclTypeaheadResults = [];
		this.eclTypeaheadError = null;
		this.eclTypeaheadLoading = true;
		this.eclTypeaheadOpen = false;

		const requestId = ++this._eclTypeaheadRequestId;

		try {
			const { rows } = await this.snomedPostExpand(
				snomedAllConceptsUrl(),
				query,
				0,
				ECL_TYPEAHEAD_COUNT
			);
			if (requestId !== this._eclTypeaheadRequestId || this.eclTypeaheadKey !== key) {
				return;
			}
			this.eclTypeaheadResults = rows;
			this.eclTypeaheadOpen = true;
		} catch (err) {
			if (requestId !== this._eclTypeaheadRequestId || this.eclTypeaheadKey !== key) {
				return;
			}
			this.eclTypeaheadResults = [];
			this.eclTypeaheadError = errorMessage(err, 'SNOMED search');
			this.eclTypeaheadOpen = true;
		} finally {
			if (requestId === this._eclTypeaheadRequestId && this.eclTypeaheadKey === key) {
				this.eclTypeaheadLoading = false;
			}
		}
	},

	eclTypeaheadOnFocus(key, targetModel, event) {
		if (!targetModel) return;
		this.eclTypeaheadClearBlurTimer();
		this.eclTypeaheadKey = key;
		const query = String(event?.target?.value ?? '').trim();
		if (query.length >= ECL_TYPEAHEAD_MIN_CHARS
			&& (this.eclTypeaheadResults.length || this.eclTypeaheadError)) {
			this.eclTypeaheadOpen = true;
		} else if (query.length >= ECL_TYPEAHEAD_MIN_CHARS) {
			this.eclTypeaheadScheduleSearch(key, query, targetModel);
		}
	},

	eclTypeaheadOnBlur() {
		this.eclTypeaheadClearBlurTimer();
		this._eclTypeaheadBlurTimer = setTimeout(() => {
			this._eclTypeaheadBlurTimer = null;
			this.eclTypeaheadOpen = false;
		}, 150);
	},

	eclTypeaheadSelect(row, targetModel) {
		if (!row || !targetModel) return;
		targetModel.conceptId = row.code;
		targetModel.term = row.display || null;
		if (row.code && row.display) {
			if (!this.snomedCodeDisplayCache) {
				this.snomedCodeDisplayCache = {};
			}
			this.snomedCodeDisplayCache[row.code] = row.display;
		}
		this.eclTypeaheadClose();
	}
};
