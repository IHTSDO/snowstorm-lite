import { AJAX_TIMEOUT_MS } from './constants.js';
import { fetchWithTimeout } from './http.js';

async function readErrorMessage(res) {
	const text = await res.text();
	if (!text) return `Request failed (${res.status})`;
	try {
		const json = JSON.parse(text);
		if (json.message) return json.message;
		if (json.eclString !== undefined) return text;
	} catch (_) {
		// plain text error body
	}
	return text;
}

export async function parseEclStringToModel(ecl) {
	const url = new URL('/util/ecl-string-to-model', window.location.origin).href;
	const res = await fetchWithTimeout(url, AJAX_TIMEOUT_MS, {
		method: 'POST',
		headers: { 'Content-Type': 'text/plain' },
		body: ecl
	});
	if (!res.ok) {
		throw new Error(await readErrorMessage(res));
	}
	return res.json();
}

export async function convertEclModelToString(model) {
	const url = new URL('/util/ecl-model-to-string', window.location.origin).href;
	const res = await fetchWithTimeout(url, AJAX_TIMEOUT_MS, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify(model)
	});
	if (!res.ok) {
		throw new Error(await readErrorMessage(res));
	}
	const data = await res.json();
	if (!data || data.eclString == null) {
		throw new Error('Invalid response from ECL model converter.');
	}
	return String(data.eclString);
}
