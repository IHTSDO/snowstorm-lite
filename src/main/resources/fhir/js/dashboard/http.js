import { authFetch } from './auth.js';

export function fetchWithTimeout(url, ms, options = {}) {
	return authFetch(url, options, (u, opts) => {
		const ctrl = new AbortController();
		const t = setTimeout(() => ctrl.abort(), ms);
		return fetch(u, { ...opts, signal: ctrl.signal }).finally(() => clearTimeout(t));
	});
}

export function errorMessage(err, label, res) {
	if (err.name === 'AbortError') return 'Request timed out. Please try again.';
	if (res) {
		if (res.status === 401) return 'Admin credentials required.';
		if (res.status === 404) return 'FHIR endpoint not found. Please check if the server is running.';
		if (res.status === 500) {
			const msg = err && err.message;
			if (msg && msg !== 'Failed to fetch' && !msg.startsWith('Error loading ')) {
				return msg;
			}
			return 'Server error. Please try again later.';
		}
	}
	return err.message || `Error loading ${label}`;
}
