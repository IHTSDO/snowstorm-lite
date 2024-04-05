package org.snomed.snowstormlite.domain;

import java.util.List;

public class Concepts {

	public static final String FSN = "900000000000003001";
	public static final String SYNONYM = "900000000000013009";

	public static final String IS_A = "116680003";
	public static final String STATED_RELATIONSHIP = "900000000000010007";

	public static final String PREFERRED = "900000000000548007";
	public static final String ACCEPTABLE = "900000000000549004";
	public static final String DEFINED = "900000000000073002";
	public static final long REFERENCE_SET_ATTRIBUTE = 900000000000457003L;
	public static final long US_LANG_REFSET = 900000000000509007L;
	public static final List<LanguageDialect> DEFAULT_LANGUAGE = List.of(new LanguageDialect("en", US_LANG_REFSET));

	public static final String REFSET_SAME_AS_ASSOCIATION = "900000000000527005";
	public static final String REFSET_REPLACED_BY_ASSOCIATION = "900000000000526001";
	public static final String REFSET_WAS_A_ASSOCIATION = "900000000000528000";
	public static final String REFSET_PARTIALLY_EQUIVALENT_TO_ASSOCIATION = "1186924009";


}
