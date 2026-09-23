/**
 * Admin authentication for dashboard requests.
 *
 * Same-origin requests are sent with `X-Requested-With: XMLHttpRequest`, which makes the server answer 401 without a
 * Basic challenge (so the browser never shows its native login dialog). On a 401 the dashboard shows its own sign-in
 * modal and retries the request with the entered credentials. Credentials are kept in memory only (lost on reload).
 */

let authorizationHeader = null;
let pendingSignIn = null;

function isSameOrigin(url) {
	try {
		return new URL(url, window.location.href).origin === window.location.origin;
	} catch {
		return false;
	}
}

function withAuthHeaders(options) {
	const headers = new Headers(options.headers || {});
	headers.set('X-Requested-With', 'XMLHttpRequest');
	if (authorizationHeader) headers.set('Authorization', authorizationHeader);
	return { ...options, headers };
}

function modalElements() {
	const el = document.getElementById('adminSignInModal');
	if (!el) return null;
	return {
		el,
		form: el.querySelector('form'),
		username: el.querySelector('#adminSignInUsername'),
		password: el.querySelector('#adminSignInPassword'),
		error: el.querySelector('#adminSignInError'),
		submit: el.querySelector('#adminSignInSubmit')
	};
}

/**
 * Show the sign-in modal. `attempt(authorization)` performs the request with the given Authorization header; the modal
 * stays open (showing an error) while it returns 401. Resolves with the successful response, or null if cancelled.
 */
function signIn(attempt) {
	const m = modalElements();
	if (!m || typeof bootstrap === 'undefined') return Promise.resolve(null);
	const modal = bootstrap.Modal.getOrCreateInstance(m.el, { backdrop: 'static' });
	return new Promise(resolve => {
		let settled = false;
		const finish = value => {
			if (settled) return;
			settled = true;
			m.form.removeEventListener('submit', onSubmit);
			m.el.removeEventListener('hidden.bs.modal', onHidden);
			modal.hide();
			resolve(value);
		};
		const onHidden = () => finish(null);
		const onSubmit = async e => {
			e.preventDefault();
			m.error.classList.add('d-none');
			m.submit.disabled = true;
			const header = 'Basic ' + btoa(unescape(encodeURIComponent(m.username.value + ':' + m.password.value)));
			try {
				const res = await attempt(header);
				if (res.status === 401) {
					m.error.textContent = 'Invalid username or password.';
					m.error.classList.remove('d-none');
					m.password.select();
					return;
				}
				authorizationHeader = header;
				finish(res);
			} catch (err) {
				m.error.textContent = err.message || 'Request failed.';
				m.error.classList.remove('d-none');
			} finally {
				m.submit.disabled = false;
			}
		};
		m.form.addEventListener('submit', onSubmit);
		m.el.addEventListener('hidden.bs.modal', onHidden);
		m.password.value = '';
		m.error.classList.add('d-none');
		m.el.addEventListener('shown.bs.modal', () => {
			const backdrops = document.querySelectorAll('.modal-backdrop');
			if (backdrops.length > 1) backdrops[backdrops.length - 1].classList.add('admin-sign-in-backdrop');
			(m.username.value ? m.password : m.username).focus();
		}, { once: true });
		modal.show();
	});
}

/**
 * Drop-in replacement for fetch(). `doFetch` performs a single attempt (defaults to fetch) so callers such as
 * fetchWithTimeout get a fresh timeout per attempt, not one spanning the time the user spends in the sign-in modal.
 */
export async function authFetch(url, options = {}, doFetch = fetch) {
	if (!isSameOrigin(url)) return doFetch(url, options);
	const res = await doFetch(url, withAuthHeaders(options));
	if (res.status !== 401) return res;
	authorizationHeader = null;
	// Concurrent 401s share one modal; the others retry once it succeeds.
	if (pendingSignIn) {
		const signedIn = await pendingSignIn;
		return signedIn ? doFetch(url, withAuthHeaders(options)) : res;
	}
	let signedInResponse;
	pendingSignIn = signIn(header => {
		const opts = withAuthHeaders(options);
		opts.headers.set('Authorization', header);
		return doFetch(url, opts);
	}).then(r => (signedInResponse = r) != null);
	try {
		await pendingSignIn;
	} finally {
		pendingSignIn = null;
	}
	return signedInResponse || res;
}

/** Parse a response body as JSON, returning {} (or { message: text }) when it is not JSON. */
export async function readJsonSafe(res) {
	const text = await res.text().catch(() => '');
	if (!text) return {};
	try {
		return JSON.parse(text);
	} catch {
		return { message: text.trim() };
	}
}
