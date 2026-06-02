import { CONCEPTMAP_DEFAULT_GROUP_SOURCE } from './dashboard/constants.js';
import { dashboardCapability } from './dashboard/capability.js';
import { dashboardConceptMapUi } from './dashboard/conceptMapUi.js';
import { dashboardGetters } from './dashboard/getters.js';
import { dashboardModalDetail } from './dashboard/modalDetail.js';
import { dashboardResources } from './dashboard/resources.js';
import { dashboardRouting, getInitialFhirBaseUrl } from './dashboard/routing.js';
import {
	dashboardSnomedBrowser,
	readStoredSnomedTaxonomyPaneWidthPx,
	snomedBrowserGetters,
	SNOMED_ROOT_CONCEPT
} from './dashboard/snomedBrowser.js';
import { dashboardSyndication } from './dashboard/syndication.js';

function createDashboardState() {
	return {
		fhirBaseUrl: typeof window !== 'undefined' ? getInitialFhirBaseUrl() : 'http://localhost:8080/fhir',
		section: 'resources',
		tab: 'codesystem',
		codeSystems: [],
		valueSets: [],
		conceptMaps: [],
		editions: [],
		snomedCodeSystems: [],
		loadingCodesystems: false,
		loadingValueSets: false,
		loadingConceptMaps: false,
		loadingSyndication: false,
		loadingInstalledEditions: false,
		errorCodesystems: null,
		errorValueSets: null,
		errorConceptMaps: null,
		errorSyndication: null,
		errorInstalledEditions: null,
		codeSystemWarnings: [],
		showCodeSystemWarningsDetails: false,
		installState: {},
		sortKey: { codesystem: 'title', valueset: 'title', conceptmap: 'title', syndication: 'title' },
		sortAsc: { codesystem: true, valueset: true, conceptmap: true, syndication: true },
		tableFilter: { codesystem: '', valueset: '', conceptmap: '' },
		modalType: null,
		modalDetail: null,
		modalLoading: false,
		modalError: null,
		showAddValueSetForm: false,
		addValueSetJson: '',
		addValueSetError: null,
		addValueSetSaving: false,
		showAddConceptMapForm: false,
		addConceptMapPayload: null,
		addConceptMapUrl: '',
		addConceptMapVersion: '',
		addConceptMapTitle: '',
		addConceptMapName: '',
		addConceptMapStatus: 'draft',
		addConceptMapDescription: '',
		addConceptMapExperimental: false,
		addConceptMapGroupSources: [],
		conceptMapDefaultGroupSource: CONCEPTMAP_DEFAULT_GROUP_SOURCE,
		addConceptMapError: null,
		addConceptMapSaving: false,
		deleteConfirmId: null,
		deleteConfirmTitle: null,
		deletingConceptMap: false,
		deleteConceptMapError: null,
		fullValueSetCache: null,
		fullConceptMapCache: null,
		deleteCodeSystemId: null,
		deleteCodeSystemTitle: null,
		deleteCodeSystemUrl: null,
		deletingCodeSystem: false,
		deleteCodeSystemError: null,
		deleteCodeSystemDependents: null,
		loadingDeleteDependents: false,
		deleteValueSetId: null,
		deleteValueSetTitle: null,
		deleteValueSetUrl: null,
		deletingValueSet: false,
		deleteValueSetError: null,
		deleteValueSetDependents: null,
		deletingAll: false,
		deleteAllError: null,
		deleteAllProgress: null,
		copiedUrl: null,
		conceptMapTranslateExampleBusy: false,
		capabilityStatement: null,
		syndicationAvailable: true,
		txUrlPrompt: '',
		txUrlDialogError: null,
		routingInitialized: false,
		installTaskSnapshotByEditionId: {},
		pendingSyndicationEdition: null,
		syndicationDerivativeGroups: [],
		syndicationDerivativesLoading: false,
		syndicationDerivativesError: null,
		syndicationFeedUrl: null,
		syndicationFeedDefaultUrl: null,
		syndicationFeedUrlEditing: false,
		syndicationFeedUrlInput: '',
		syndicationFeedUrlSaving: false,
		syndicationFeedUrlError: null,
		snomedScopeInput: SNOMED_ROOT_CONCEPT,
		snomedScopeConceptId: SNOMED_ROOT_CONCEPT,
		/** '' = entire SNOMED; otherwise direct child of SNOMED root for subtree ISA search */
		snomedSearchScopeConceptId: '',
		snomedSearchScopeOptions: [],
		snomedTreeRoot: null,
		snomedBrowserInitialized: false,
		snomedHierarchyError: null,
		snomedSelectedCode: null,
		snomedDetail: null,
		snomedDetailLoading: false,
		snomedDetailError: null,
		snomedSearchQuery: '',
		snomedSearchLoading: false,
		snomedSearchError: null,
		snomedSearchResults: [],
		snomedSearchTotal: 0,
		snomedSearchOffset: 0,
		snomedBreadcrumbTrail: [],
		snomedConceptChildrenExpanded: false,
		snomedCodeDisplayCache: {},
		snomedEditionSummaryLine: '—',
		/** Set after dragging the taxonomy/detail splitter; null uses default flex sizing from CSS */
		snomedTaxonomyPaneWidthPx: readStoredSnomedTaxonomyPaneWidthPx(),
		snomedPaneDividerDragging: false
	};
}

document.addEventListener('alpine:init', () => {
	Alpine.data('dashboard', () => {
		// Do not spread dashboardGetters: object spread invokes getters and copies
		// snapshot values with the wrong `this`. Preserve accessors via descriptors.
		const component = {
			...createDashboardState(),
			...dashboardRouting,
			...dashboardCapability,
			...dashboardSyndication,
			...dashboardResources,
			...dashboardConceptMapUi,
			...dashboardModalDetail,
			...dashboardSnomedBrowser
		};
		return Object.defineProperties(component, {
			...Object.getOwnPropertyDescriptors(dashboardGetters),
			...Object.getOwnPropertyDescriptors(snomedBrowserGetters)
		});
	});
});
