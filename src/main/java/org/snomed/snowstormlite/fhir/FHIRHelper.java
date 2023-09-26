package org.snomed.snowstormlite.fhir;

import org.hl7.fhir.r4.model.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRConstants.SNOMED_URI;
import static org.snomed.snowstormlite.fhir.FHIRConstants.SNOMED_URI_UNVERSIONED;

public class FHIRHelper {

	public static final Pattern SNOMED_URI_MODULE_PATTERN = Pattern.compile("http://snomed.info/sct/(\\d+)");
	public static final Pattern SNOMED_URI_MODULE_AND_VERSION_PATTERN = Pattern.compile("http://snomed.info/sct/(\\d+)/version/([\\d]{8})");
	private static final Pattern SCT_ID_PATTERN = Pattern.compile("sct_(\\d)+_(\\d){8}");

	public static void mutuallyExclusive(String param1Name, Object param1, String param2Name, Object param2) {
		if (param1 != null && param2 != null) {
			throw exception(format("Use one of '%s' or '%s' parameters.", param1Name, param2Name), OperationOutcome.IssueType.INVARIANT, 400);
		}
	}

	public static void notSupported(String paramName, Object obj) {
		notSupported(paramName, obj, null);
	}

	public static void notSupported(String paramName, Object obj, String additionalDetail) {
		if (obj != null) {
			String message = format("Input parameter '%s' is not supported%s", paramName, (additionalDetail == null ? "." : format(" %s", additionalDetail)));
			throw exception(message, OperationOutcome.IssueType.NOTSUPPORTED, 400);
		}
	}

	public static void requireExactlyOneOf(String param1Name, Object param1, String param2Name, Object param2, String param3Name, Object param3) {
		if (param1 == null && param2 == null && param3 == null) {
			throw exception(format("One of '%s' or '%s' or '%s' parameters must be supplied.", param1Name, param2Name, param3Name), OperationOutcome.IssueType.INVARIANT, 400);
		} else {
			mutuallyExclusive(param1Name, param1, param2Name, param2);
			mutuallyExclusive(param1Name, param1, param3Name, param3);
			mutuallyExclusive(param2Name, param2, param3Name, param3);
		}
	}

	public static void mutuallyRequired(String param1Name, Object param1, String param2Name, Object param2) {
		if (param1 != null && param2 == null) {
			throw exception(format("Input parameter '%s' can only be used in conjunction with parameter '%s'.",
					param1Name, param2Name), OperationOutcome.IssueType.INVARIANT, 400);
		}
	}

	public static String recoverCode(CodeType code, Coding coding) {
		if (code == null && coding == null) {
			throw exception("Use either 'code' or 'coding' parameters, not both.", OperationOutcome.IssueType.INVARIANT, 400);
		} else if (code != null) {
			if (code.getCode().contains("|")) {
				throw exception("The 'code' parameter cannot supply a codeSystem. " +
						"Use 'coding' or provide CodeSystem in 'system' parameter.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
			}
			return code.getCode();
		}
		return coding.getCode();
	}

	public static boolean isSnomedUri(String codeSystemUri) {
		return codeSystemUri != null && codeSystemUri.startsWith(SNOMED_URI);
	}

	public static CodeSystemVersionParams getCodeSystemVersionParams(UriType codeSystemParam, StringType versionParam, Coding coding) {
		return getCodeSystemVersionParams(null, codeSystemParam, versionParam, coding);
	}

	public static CodeSystemVersionParams getCodeSystemVersionParams(IdType systemId, PrimitiveType<?> codeSystemParam, StringType versionParam, Coding coding) {
		return getCodeSystemVersionParams(systemId != null ? systemId.getIdPart() : null, codeSystemParam != null ? codeSystemParam.getValueAsString() : null,
				versionParam != null ? versionParam.toString() : null, coding);
	}

	public static CodeSystemVersionParams getCodeSystemVersionParams(String systemId, String codeSystemParam, String versionParam, Coding coding) {
		if (codeSystemParam != null && coding != null && coding.getSystem() != null && !codeSystemParam.equals(coding.getSystem())) {
			throw exception("Code system defined in system and coding do not match.", OperationOutcome.IssueType.CONFLICT, 400);
		}
		if (versionParam != null && coding != null && coding.getVersion() != null && !versionParam.equals(coding.getVersion())) {
			throw exception("Version defined in version and coding do not match.", OperationOutcome.IssueType.CONFLICT, 400);
		}

		String codeSystemUrl = null;
		if (codeSystemParam != null) {
			codeSystemUrl = codeSystemParam;
		} else if (coding != null) {
			codeSystemUrl = coding.getSystem();
		}
		if (codeSystemUrl == null && systemId == null) {
			throw exception("Code system not defined in any parameter.", OperationOutcome.IssueType.CONFLICT, 400);
		}

		String version = null;
		if (versionParam != null) {
			version = versionParam;
		} else if (coding != null) {
			version = coding.getVersion();
		}

		CodeSystemVersionParams codeSystemParams = new CodeSystemVersionParams(codeSystemUrl);
		if (version != null) {
			if ("*".equals(version)) {
				throw FHIRHelper.exception("Version '*' is not supported.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
			}
			if (codeSystemParams.isSnomed()) {
				// Parse module and version from snomed version URI
				// Either "http://snomed.info/sct/[sctid]" or "http://snomed.info/sct/[sctid]/version/[YYYYMMDD]"
				Matcher matcher;
				String versionWithoutParams = version.contains("?") ? version.substring(0, version.indexOf("?")) : version;
				if ((matcher = SNOMED_URI_MODULE_PATTERN.matcher(versionWithoutParams)).matches()) {
					codeSystemParams.setSnomedModule(matcher.group(1));
				} else if ((matcher = SNOMED_URI_MODULE_AND_VERSION_PATTERN.matcher(versionWithoutParams)).matches()) {
					if (codeSystemParams.isUnversionedSnomed()) {
						throw exception("A specific version can not be requested when using " +
								"the '" + SNOMED_URI_UNVERSIONED + "' code system.", OperationOutcome.IssueType.CONFLICT, 400);
					}
					codeSystemParams.setSnomedModule(matcher.group(1));
					codeSystemParams.setVersion(matcher.group(2));
				} else {
					throw exception(format("The version parameter for the '" + SNOMED_URI + "' system must use the format " +
							"'http://snomed.info/sct/[sctid]' or http://snomed.info/sct/[sctid]/version/[YYYYMMDD]. Version provided does not match: '%s'.", versionWithoutParams), OperationOutcome.IssueType.CONFLICT, 400);
				}
			} else {
				// Take version param literally
				codeSystemParams.setVersion(version);
			}
		}
		if (systemId != null) {
			if (codeSystemParams.isSnomed()) {
				Matcher idMatcher = SCT_ID_PATTERN.matcher(systemId);
				if (!idMatcher.matches()) {
					throw exception("SNOMED system and id specified but id does not match expected format " +
							"sct_[moduleId]_[YYYYMMDD].", OperationOutcome.IssueType.CONFLICT, 400);
				}
				String moduleFromId = idMatcher.group(1);
				String versionFromId = idMatcher.group(2);
				if (codeSystemParams.getSnomedModule() != null && !codeSystemParams.getSnomedModule().equals(moduleFromId)) {
					throw exception("SNOMED module in system id and uri do not match.", OperationOutcome.IssueType.CONFLICT, 400);
				}
				if (codeSystemParams.getVersion() != null && !codeSystemParams.getVersion().equals(versionFromId)) {
					throw exception("SNOMED version in system id and uri do not match.", OperationOutcome.IssueType.CONFLICT, 400);
				}
				// For SNOMED store the parsed module and version, not the id.
				codeSystemParams.setSnomedModule(moduleFromId);
				codeSystemParams.setVersion(versionFromId);
			} else {
				codeSystemParams.setId(systemId);
			}
		}

		return codeSystemParams;
	}

	public static FHIRServerResponseException exception(String message, OperationOutcome.IssueType issueType, int theStatusCode) {
		return exception(message, issueType, theStatusCode, null);
	}

	public static FHIRServerResponseException exception(String message, OperationOutcome.IssueType issueType, int theStatusCode, Throwable e) {
		OperationOutcome outcome = new OperationOutcome();
		OperationOutcome.OperationOutcomeIssueComponent component = new OperationOutcome.OperationOutcomeIssueComponent();
		component.setSeverity(OperationOutcome.IssueSeverity.ERROR);
		component.setCode(issueType);
		component.setDiagnostics(message);
		outcome.addIssue(component);
		return new FHIRServerResponseException(theStatusCode, message, outcome, e);
	}

	public static Parameters.ParametersParameterComponent createProperty(String propertyName, Object propertyValue, boolean isCode) {
		Parameters.ParametersParameterComponent property = new Parameters.ParametersParameterComponent().setName("property");
		property.addPart().setName("code").setValue(new CodeType(propertyName));
		final String propertyValueString = propertyValue == null ? "" : propertyValue.toString();
		if (isCode) {
			property.addPart().setName("value").setValue(new CodeType(propertyValueString));
		} else if (propertyValue instanceof Boolean) {
			property.addPart().setName("value").setValue(new BooleanType((Boolean) propertyValue));
			property.addPart().setName("valueBoolean").setValue(new BooleanType((Boolean) propertyValue));
		} else {
			StringType value = new StringType(propertyValueString);
			property.addPart().setName("value").setValue(value);
			property.addPart().setName("valueString").setValue(value);
		}
		return property;
	}

	private static String getTypeName(Object obj) {
		if (obj instanceof String) {
			return "valueString";
		} else if (obj instanceof Boolean) {
			return "valueBoolean";
		}
		return null;
	}

}
