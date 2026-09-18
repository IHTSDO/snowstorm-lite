import { parseEclStringToModel, convertEclModelToString } from './eclApi.js';
import {
	clearHistorySupplement,
	createEmptyAttribute,
	createEmptySubExpression,
	detectEclModelType,
	eclModelTypeLabel,
	ECL_BUILDER_EXAMPLES,
	ECL_COMPOUND_MODES,
	ECL_EXPRESSION_COMPARISON_OPS,
	ECL_HISTORY_PROFILES,
	ECL_HISTORY_PROFILES_WITH_VALUES,
	ECL_OPERATORS,
	ECL_OPERATORS_WITH_VALUES,
	ensureHistorySupplement,
	normalizeHistoryProfile,
	normalizeSubExpressionOperator,
	sanitizeEclModelForApi,
	getCompoundMembers,
	getCompoundMode,
	getRootAttributesFromRefined,
	refinementAttributes,
	setCompoundMode,
	subExpressionSummary
} from './eclModel.js';

function normalizeAttribute(attr) {
	if (!attr.attributeName) {
		attr.attributeName = createEmptySubExpression();
	}
	if (!attr.value) {
		attr.value = createEmptySubExpression({ wildcard: true });
	}
	return attr;
}

function normalizeParsedModel(model) {
	if (!model || typeof model !== 'object') return model;
	const type = detectEclModelType(model);
	if (type === 'refined') {
		for (const attr of getRootAttributesFromRefined(model)) {
			normalizeAttribute(attr);
		}
	}
	return sanitizeEclModelForApi(model);
}

export const dashboardEclBuilderUi = {
	eclBuilderExamples: ECL_BUILDER_EXAMPLES,

	eclBuilderExampleOptionLabel(ex) {
		return ex ? `${ex.name} — ${ex.ecl}` : '';
	},

	eclOperators: ECL_OPERATORS,
	eclOperatorsWithValues: ECL_OPERATORS_WITH_VALUES,
	eclHistoryProfiles: ECL_HISTORY_PROFILES,
	eclHistoryProfilesWithValues: ECL_HISTORY_PROFILES_WITH_VALUES,
	eclExpressionComparisonOps: ECL_EXPRESSION_COMPARISON_OPS,
	eclCompoundModes: ECL_COMPOUND_MODES,

	isEclCriteriaType(criteriaType) {
		return criteriaType === 'constraint' || criteriaType === 'constraint-not';
	},

	eclBuilderModelType(model) {
		return detectEclModelType(model);
	},

	eclBuilderModelTypeLabel(model) {
		return eclModelTypeLabel(detectEclModelType(model));
	},

	eclBuilderSubSummary(model) {
		return subExpressionSummary(model);
	},

	eclBuilderCurrentModel() {
		if ((this.eclBuilderStack || []).length > 0) {
			return this.eclBuilderStack[this.eclBuilderStack.length - 1].model;
		}
		return this.eclBuilderModel;
	},

	eclBuilderBreadcrumbs() {
		const crumbs = [{ label: 'Root', index: -1 }];
		(this.eclBuilderStack || []).forEach((frame, index) => {
			crumbs.push({ label: frame.label, index });
		});
		return crumbs;
	},

	openEclBuilder(row) {
		this.eclBuilderTargetRow = row;
		this.eclBuilderText = String(row?.value || '');
		this.eclBuilderModel = null;
		this.eclBuilderStack = [];
		this.eclBuilderError = null;
		this.eclBuilderLoading = false;
		this.eclBuilderExampleKey = '';
		this.eclBuilderOpen = true;
		this.$nextTick(() => {
			const el = document.getElementById('eclBuilderModal');
			if (el) bootstrap.Modal.getOrCreateInstance(el).show();
			if (String(this.eclBuilderText || '').trim()) {
				this.parseEclBuilderText();
			}
		});
	},

	closeEclBuilder() {
		if (typeof this.eclTypeaheadClose === 'function') {
			this.eclTypeaheadClose();
		}
		this.eclBuilderOpen = false;
		this.eclBuilderTargetRow = null;
		this.eclBuilderModel = null;
		this.eclBuilderStack = [];
		this.eclBuilderError = null;
		this.eclBuilderLoading = false;
		this.eclBuilderExampleKey = '';
		const el = document.getElementById('eclBuilderModal');
		if (el) {
			const modal = bootstrap.Modal.getInstance(el);
			if (modal) modal.hide();
		}
	},

	eclBuilderPopTo(index) {
		if (typeof this.eclTypeaheadClose === 'function') {
			this.eclTypeaheadClose();
		}
		if (index < 0) {
			this.eclBuilderStack = [];
			return;
		}
		this.eclBuilderStack = (this.eclBuilderStack || []).slice(0, index + 1);
	},

	eclBuilderPushNested(label, model) {
		if (!model) return;
		this.eclBuilderStack = [...(this.eclBuilderStack || []), { label, model }];
	},

	async parseEclBuilderText() {
		this.eclBuilderError = null;
		const text = String(this.eclBuilderText || '').trim();
		if (!text) {
			this.eclBuilderError = null;
			this.eclBuilderModel = null;
			this.eclBuilderStack = [];
			return;
		}
		this.eclBuilderLoading = true;
		try {
			this.eclBuilderModel = normalizeParsedModel(await parseEclStringToModel(text));
			this.eclBuilderStack = [];
			await this.$nextTick();
		} catch (err) {
			this.eclBuilderError = err.message || 'Failed to parse ECL.';
			this.eclBuilderModel = null;
			this.eclBuilderStack = [];
		} finally {
			this.eclBuilderLoading = false;
		}
	},

	async applyEclBuilderToRow() {
		if (!this.eclBuilderTargetRow) return;
		this.eclBuilderError = null;
		this.eclBuilderLoading = true;
		try {
			let eclString;
			if (this.eclBuilderModel) {
				eclString = await convertEclModelToString(sanitizeEclModelForApi(this.eclBuilderModel));
			} else {
				eclString = String(this.eclBuilderText || '').trim();
			}
			if (!eclString) {
				throw new Error('ECL expression is empty.');
			}
			this.eclBuilderTargetRow.value = eclString;
			this.eclBuilderText = eclString;
			this.addValueSetBuilderPreview = null;
			this.addValueSetBuilderPreviewError = null;
			this.closeEclBuilder();
		} catch (err) {
			this.eclBuilderError = err.message || 'Failed to convert ECL model.';
		} finally {
			this.eclBuilderLoading = false;
		}
	},

	async eclBuilderLoadExample(exampleId) {
		const ex = ECL_BUILDER_EXAMPLES.find(e => e.id === exampleId);
		if (!ex) return;
		this.eclBuilderText = ex.ecl;
		await this.parseEclBuilderText();
		this.eclBuilderExampleKey = '';
	},

	eclBuilderOnSubOperatorChange(model, value) {
		const operator = normalizeSubExpressionOperator(value);
		model.operator = operator;
		if (operator) {
			model.wildcard = false;
		}
	},

	eclBuilderOnSubWildcardChange(model, checked) {
		model.wildcard = !!checked;
		if (checked) {
			model.conceptId = null;
			model.term = null;
		}
	},

	eclBuilderToggleHistory(model, enabled) {
		if (enabled) {
			ensureHistorySupplement(model);
		} else {
			clearHistorySupplement(model);
		}
	},

	eclBuilderOnHistoryProfileChange(model, value) {
		if (!model?.historySupplement) return;
		model.historySupplement.historyProfile = normalizeHistoryProfile(value);
	},

	eclBuilderCompoundMode(model) {
		return getCompoundMode(model);
	},

	eclBuilderCompoundMembers(model) {
		return getCompoundMembers(model) || [];
	},

	eclBuilderSetCompoundMode(model, mode) {
		setCompoundMode(model, mode);
	},

	eclBuilderAddCompoundMember(model) {
		const mode = getCompoundMode(model);
		if (mode === 'minus') {
			return;
		}
		const members = getCompoundMembers(model);
		if (members) {
			members.push(createEmptySubExpression({ operator: 'ancestororselfof' }));
		}
	},

	eclBuilderRemoveCompoundMember(model, index) {
		const members = getCompoundMembers(model);
		if (!members || members.length <= 2) return;
		members.splice(index, 1);
	},

	eclBuilderRefinedAttributes(model) {
		return getRootAttributesFromRefined(model);
	},

	eclBuilderAddRefinedAttribute(model) {
		if (!model.eclRefinement) {
			model.eclRefinement = { subRefinement: { eclAttributeSet: { subAttributeSet: { attribute: createEmptyAttribute() } } } };
			return;
		}
		const attrSet = model.eclRefinement.subRefinement?.eclAttributeSet;
		if (!attrSet) return;
		if (!attrSet.conjunctionAttributeSet) {
			attrSet.conjunctionAttributeSet = [];
		}
		attrSet.conjunctionAttributeSet.push({ attribute: createEmptyAttribute() });
	},

	eclBuilderRemoveRefinedAttribute(model, index) {
		const attrs = getRootAttributesFromRefined(model);
		if (attrs.length <= 1) return;
		if (index === 0) {
			const attrSet = model.eclRefinement.subRefinement.eclAttributeSet;
			const first = attrSet.conjunctionAttributeSet?.shift();
			if (first?.attribute) {
				attrSet.subAttributeSet.attribute = first.attribute;
			}
			return;
		}
		const attrSet = model.eclRefinement.subRefinement.eclAttributeSet;
		attrSet.conjunctionAttributeSet.splice(index - 1, 1);
	},

	eclBuilderRefinementAttributes(refinement) {
		return refinementAttributes(refinement);
	},

	eclBuilderAddRefinementAttribute(refinement) {
		if (!refinement.subRefinement?.eclAttributeSet) return;
		const attrSet = refinement.subRefinement.eclAttributeSet;
		if (!attrSet.conjunctionAttributeSet) attrSet.conjunctionAttributeSet = [];
		attrSet.conjunctionAttributeSet.push({ attribute: createEmptyAttribute() });
	},

	eclBuilderDottedAttributes(model) {
		return model.dottedAttributes || [];
	},

	eclBuilderAddDottedAttribute(model) {
		if (!model.dottedAttributes) model.dottedAttributes = [];
		model.dottedAttributes.push(createEmptySubExpression());
	},

	eclBuilderRemoveDottedAttribute(model, index) {
		if (!model.dottedAttributes || model.dottedAttributes.length <= 1) return;
		model.dottedAttributes.splice(index, 1);
	},

	eclBuilderUseSelectedConcept(model, field) {
		const code = this.snomedSelectedCode;
		if (!code || !model) return;
		if (field === 'conceptId') {
			model.conceptId = code;
			const display = this.snomedCodeDisplayCache?.[code];
			if (display) model.term = display;
		}
	},

	eclBuilderCanUseSelectedConcept() {
		return !!this.snomedSelectedCode;
	},

	eclBuilderEditNested(label, model) {
		this.eclBuilderPushNested(label, model);
	},

	eclBuilderEditSubExpression(model, label) {
		if (!model.nestedExpressionConstraint) {
			model.nestedExpressionConstraint = createEmptySubExpression();
		}
		this.eclBuilderEditNested(label || 'Nested expression', model.nestedExpressionConstraint);
	},

	eclBuilderClearNested(model) {
		delete model.nestedExpressionConstraint;
	},

	eclBuilderGetSubExpression(model) {
		return model.subexpressionConstraint || model.subExpressionConstraint || null;
	},

	eclBuilderEditFocus(model, label) {
		const sub = this.eclBuilderGetSubExpression(model);
		if (sub) this.eclBuilderEditNested(label || 'Focus expression', sub);
	}
};
