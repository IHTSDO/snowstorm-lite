export const ECL_BUILDER_EXAMPLES = [
	{
		id: 'descendants-or-self',
		name: 'Descendants or self',
		ecl: '<< 404684003 |Clinical finding|'
	},
	{
		id: 'descendants-inactive',
		name: 'Descendants or self including inactive',
		ecl: '<< 404684003 |Clinical finding| {{ +HISTORY-MAX }}'
	},
	{
		id: 'compound-minus',
		name: 'Compound',
		ecl: '<< 71388002 |Procedure (procedure)| MINUS << 225288009 |Environmental care procedure (procedure)|'
	},
	{
		id: 'refined',
		name: 'Refined',
		ecl: '< 404684003 |Clinical finding| : 116676008 |Associated morphology| = << 409774005 |Inflammatory morphology|'
	},
	{
		id: 'member-of-refset',
		name: 'Member of refset',
		ecl: '^ 723264001 |Lateralizable body structure reference set|'
	}
];

export const ECL_OPERATOR_NONE_LABEL = '(none — specific concept)';

export const ECL_OPERATORS = [
	{ value: '', label: ECL_OPERATOR_NONE_LABEL },
	{ value: 'descendantof', label: '< Descendants of' },
	{ value: 'descendantorselfof', label: '<< Descendants or self of' },
	{ value: 'childof', label: '<! Children of' },
	{ value: 'childorselfof', label: '<<! Children or self of' },
	{ value: 'ancestorof', label: '> Ancestors of' },
	{ value: 'ancestororselfof', label: '>> Ancestors or self of' },
	{ value: 'parentof', label: '>! Parents of' },
	{ value: 'parentorselfof', label: '>>! Parents or self of' },
	{ value: 'memberOf', label: '^ Member of refset' }
];

export const ECL_OPERATORS_WITH_VALUES = ECL_OPERATORS.filter(op => op.value);

const ECL_OPERATOR_VALUE_SET = new Set(ECL_OPERATORS_WITH_VALUES.map(op => op.value));

export const ECL_HISTORY_PROFILE_DEFAULT_LABEL = 'Default (MAX)';

export const ECL_HISTORY_PROFILES = [
	{ value: '', label: ECL_HISTORY_PROFILE_DEFAULT_LABEL },
	{ value: 'MIN', label: 'MIN' },
	{ value: 'MAX', label: 'MAX' }
];

export const ECL_HISTORY_PROFILES_WITH_VALUES = ECL_HISTORY_PROFILES.filter(hp => hp.value);

const ECL_HISTORY_PROFILE_VALUE_SET = new Set(ECL_HISTORY_PROFILES_WITH_VALUES.map(hp => hp.value));

export function normalizeSubExpressionOperator(value) {
	if (value == null || value === '') {
		return null;
	}
	const str = String(value);
	if (str === ECL_OPERATOR_NONE_LABEL) {
		return null;
	}
	return ECL_OPERATOR_VALUE_SET.has(str) ? str : null;
}

export function normalizeHistoryProfile(value) {
	if (value == null || value === '') {
		return null;
	}
	const str = String(value);
	if (str === ECL_HISTORY_PROFILE_DEFAULT_LABEL) {
		return null;
	}
	return ECL_HISTORY_PROFILE_VALUE_SET.has(str) ? str : null;
}

function looksLikeSubExpression(node) {
	return node
		&& typeof node === 'object'
		&& 'operator' in node
		&& ('conceptId' in node || 'term' in node || 'wildcard' in node);
}

export function sanitizeEclModelForApi(model) {
	if (!model || typeof model !== 'object') {
		return model;
	}
	if (Array.isArray(model)) {
		for (const item of model) {
			sanitizeEclModelForApi(item);
		}
		return model;
	}
	if (looksLikeSubExpression(model)) {
		model.operator = normalizeSubExpressionOperator(model.operator);
		if (model.historySupplement) {
			model.historySupplement.historyProfile = normalizeHistoryProfile(model.historySupplement.historyProfile);
		}
	}
	for (const value of Object.values(model)) {
		if (value && typeof value === 'object') {
			sanitizeEclModelForApi(value);
		}
	}
	return model;
}

export const ECL_EXPRESSION_COMPARISON_OPS = [
	{ value: '=', label: '=' },
	{ value: '!=', label: '!=' }
];

export const ECL_COMPOUND_MODES = [
	{ value: 'and', label: 'AND (conjunction)' },
	{ value: 'or', label: 'OR (disjunction)' },
	{ value: 'minus', label: 'MINUS (exclusion)' }
];

export function detectEclModelType(model) {
	if (!model || typeof model !== 'object') return 'unknown';
	if (model.dottedAttributes != null) return 'dotted';
	if (model.eclRefinement != null) return 'refined';
	if (model.conjunctionExpressionConstraints != null
		|| model.disjunctionExpressionConstraints != null
		|| model.exclusionExpressionConstraints != null) {
		return 'compound';
	}
	if (model.subRefinement != null
		|| model.conjunctionSubRefinements != null
		|| model.disjunctionSubRefinements != null) {
		return 'refinement';
	}
	if (model.subAttributeSet != null
		|| model.conjunctionAttributeSet != null
		|| model.disjunctionAttributeSet != null) {
		return 'attributeSet';
	}
	if (model.attribute != null || model.attributeSet != null) {
		return 'subAttributeSet';
	}
	if (model.attributeName != null || model.expressionComparisonOperator != null) {
		return 'attribute';
	}
	return 'sub';
}

export function eclModelTypeLabel(type) {
	switch (type) {
		case 'sub': return 'Expression';
		case 'refined': return 'Refined expression';
		case 'compound': return 'Compound expression';
		case 'dotted': return 'Dotted expression';
		case 'refinement': return 'Refinement';
		case 'attributeSet': return 'Attribute set';
		case 'subAttributeSet': return 'Attribute group member';
		case 'attribute': return 'Attribute';
		default: return 'Unknown';
	}
}

export function createEmptySubExpression(overrides = {}) {
	return {
		operator: null,
		conceptId: null,
		term: null,
		wildcard: false,
		returnAllMemberFields: false,
		...overrides
	};
}

export function createEmptyAttribute() {
	return {
		attributeName: createEmptySubExpression(),
		expressionComparisonOperator: '=',
		value: createEmptySubExpression({ wildcard: true })
	};
}

export function createEmptySubAttributeSet() {
	return {
		attribute: createEmptyAttribute()
	};
}

export function createEmptyAttributeSet() {
	return {
		subAttributeSet: createEmptySubAttributeSet()
	};
}

export function createEmptySubRefinement() {
	return {
		eclAttributeSet: createEmptyAttributeSet()
	};
}

export function createEmptyRefinement() {
	return {
		subRefinement: createEmptySubRefinement()
	};
}

export function createEmptyRefinedExpression() {
	return {
		subexpressionConstraint: createEmptySubExpression({ operator: 'descendantorselfof' }),
		eclRefinement: createEmptyRefinement()
	};
}

export function createEmptyCompoundExpression(mode = 'and') {
	if (mode === 'or') {
		return {
			disjunctionExpressionConstraints: [
				createEmptySubExpression({ operator: 'descendantorselfof' }),
				createEmptySubExpression({ operator: 'descendantorselfof' })
			]
		};
	}
	if (mode === 'minus') {
		return {
			exclusionExpressionConstraints: {
				first: createEmptySubExpression({ operator: 'ancestororselfof' }),
				second: createEmptySubExpression({ operator: 'ancestororselfof' })
			}
		};
	}
	return {
		conjunctionExpressionConstraints: [
			createEmptySubExpression({ operator: 'ancestororselfof' }),
			createEmptySubExpression({ operator: 'ancestororselfof' })
		]
	};
}

export function createEmptyDottedExpression() {
	return {
		subExpressionConstraint: createEmptySubExpression({ operator: 'descendantof' }),
		dottedAttributes: [createEmptySubExpression()]
	};
}

export function getCompoundMode(model) {
	if (model.disjunctionExpressionConstraints != null) return 'or';
	if (model.exclusionExpressionConstraints != null) return 'minus';
	return 'and';
}

export function getCompoundMembers(model) {
	if (model.conjunctionExpressionConstraints != null) {
		return model.conjunctionExpressionConstraints;
	}
	if (model.disjunctionExpressionConstraints != null) {
		return model.disjunctionExpressionConstraints;
	}
	return null;
}

export function setCompoundMode(model, mode) {
	const members = getCompoundMembers(model)
		|| (model.exclusionExpressionConstraints
			? [model.exclusionExpressionConstraints.first, model.exclusionExpressionConstraints.second]
			: [createEmptySubExpression(), createEmptySubExpression()]);
	delete model.conjunctionExpressionConstraints;
	delete model.disjunctionExpressionConstraints;
	delete model.exclusionExpressionConstraints;
	if (mode === 'or') {
		model.disjunctionExpressionConstraints = members.slice();
	} else if (mode === 'minus') {
		model.exclusionExpressionConstraints = {
			first: members[0] || createEmptySubExpression(),
			second: members[1] || createEmptySubExpression()
		};
	} else {
		model.conjunctionExpressionConstraints = members.slice();
	}
}

export function subExpressionSummary(model) {
	if (!model) return '(empty)';
	if (model.wildcard) {
		const op = model.operator ? ECL_OPERATORS.find(o => o.value === model.operator)?.label || model.operator : '';
		return op ? `${op} *` : '*';
	}
	const parts = [];
	if (model.operator) {
		const op = ECL_OPERATORS.find(o => o.value === model.operator);
		parts.push(op ? op.label.split(' ')[0] : model.operator);
	}
	if (model.conceptId) {
		parts.push(model.conceptId);
		if (model.term) parts.push(`|${model.term}|`);
	}
	return parts.length ? parts.join(' ') : '(expression)';
}

export function refinementAttributes(model) {
	const attrs = [];
	const sub = model?.subRefinement?.eclAttributeSet?.subAttributeSet;
	if (sub?.attribute) attrs.push(sub.attribute);
	const conjunction = model?.subRefinement?.eclAttributeSet?.conjunctionAttributeSet || [];
	for (const item of conjunction) {
		if (item?.attribute) attrs.push(item.attribute);
	}
	return attrs;
}

export function getRootAttributesFromRefined(model) {
	return refinementAttributes(model?.eclRefinement);
}

export function ensureHistorySupplement(model) {
	if (!model.historySupplement) {
		model.historySupplement = {};
	}
	return model.historySupplement;
}

export function clearHistorySupplement(model) {
	delete model.historySupplement;
}
