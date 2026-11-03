import { checkAuthStatus, isAdmin, onAuthChange, signInAsAdmin, signOut } from './auth.js';

/** Alpine mixin exposing admin sign-in state for the top-right status indicator and admin-only UI gating. */
export const dashboardAuthStatus = {
	initAuthStatus() {
		this.isAdminUser = isAdmin();
		onAuthChange(admin => {
			this.isAdminUser = admin;
			this.leaveAdminOnlySectionIfSignedOut();
		});
		checkAuthStatus().then(admin => {
			this.isAdminUser = admin;
			this.leaveAdminOnlySectionIfSignedOut();
		});
	},

	/** Settings and Syndication are admin-only sections; bounce back to Resources if sign-out happens while viewing one. */
	leaveAdminOnlySectionIfSignedOut() {
		if (!this.isAdminUser && (this.section === 'settings' || this.section === 'syndication')) {
			this.section = 'resources';
			this.setHash();
		}
	},

	async signInAsAdminUser() {
		this.isAdminUser = await signInAsAdmin();
	},

	signOutAdmin() {
		signOut();
	}
};
