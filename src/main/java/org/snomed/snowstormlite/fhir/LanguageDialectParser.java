package org.snomed.snowstormlite.fhir;

import org.snomed.snowstormlite.config.LanguageDialectAliasConfiguration;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Long.parseLong;

@Component
public class LanguageDialectParser {

	private static final Pattern LANGUAGE_PATTERN = Pattern.compile("([a-z]{2})");
	private static final Pattern LANGUAGE_AND_REFSET_PATTERN = Pattern.compile("([a-z]{2})-x-([\\d\\-]{6,20})");
	private static final Pattern LANGUAGE_AND_DIALECT_PATTERN = Pattern.compile("([a-z]{2})-([a-z]{2})");
	private static final Pattern LANGUAGE_AND_DIALECT_AND_CONTEXT_PATTERN = Pattern.compile("([a-z]{2})-([a-z]{2})-([a-z]+)");
	private static final Pattern LANGUAGE_AND_DIALECT_AND_REFSET_PATTERN = Pattern.compile("([a-z]{2})-([a-z]{2,5})-x-([\\d\\-]{6,20})");

	@Value("${search.dialect.en-default}")
	private Long defaultEnglishLangRefset;

	public List<LanguageDialect> parseDisplayLanguageWithDefaultFallback(String displayLanguageParam, String acceptLanguageHeader) {
		String displayLanguage = displayLanguageParam != null && !displayLanguageParam.isEmpty() ? displayLanguageParam : acceptLanguageHeader;
		List<LanguageDialect> languageDialects = parseAcceptLanguageHeader(displayLanguage);
		languageDialects.add(new LanguageDialect("en", defaultEnglishLangRefset));
		return languageDialects;
	}

	public List<LanguageDialect> parseAcceptLanguageHeader(String acceptLanguageHeader) {
		// en-ie-x-21000220103;q=0.8,en-US;q=0.5
		List<LanguageDialect> languageDialects = new ArrayList<>();

		if (acceptLanguageHeader == null) {
			return languageDialects;
		}

		acceptLanguageHeader = acceptLanguageHeader.replaceAll("\\s+", "");
		String[] acceptLanguageList = acceptLanguageHeader.toLowerCase().split(",");
		for (String acceptLanguage : acceptLanguageList) {
			if (acceptLanguage.isEmpty()) {
				continue;
			}

			String languageCode;
			Long languageReferenceSet = null;

			String[] valueAndWeight = acceptLanguage.split(";");
			// We don't use the weight, just take the value
			String value = valueAndWeight[0];

			Matcher matcher = LANGUAGE_PATTERN.matcher(value);
			if (matcher.matches()) {
				languageCode = matcher.group(1);
			} else if ((matcher = LANGUAGE_AND_REFSET_PATTERN.matcher(value)).matches()) {
				languageCode = matcher.group(1);
				languageReferenceSet = parseLong(matcher.group(2).replace("-", ""));
			} else if ((matcher = LANGUAGE_AND_DIALECT_PATTERN.matcher(value)).matches() || (matcher = LANGUAGE_AND_DIALECT_AND_CONTEXT_PATTERN.matcher(value)).matches()) {
				languageCode = matcher.group(1);
				languageReferenceSet = LanguageDialectAliasConfiguration.instance().findRefsetForDialect(value);
			} else if ((matcher = LANGUAGE_AND_DIALECT_AND_REFSET_PATTERN.matcher(value)).matches()) {
				languageCode = matcher.group(1);
				languageReferenceSet = parseLong(matcher.group(3).replace("-", ""));
			} else {
				throw new IllegalArgumentException("Unexpected value within Accept-Language request header '" + value + "'.");
			}
			
			LanguageDialect languageDialect = new LanguageDialect(languageCode, languageReferenceSet);
			if (!languageDialects.contains(languageDialect)) {
				//Would normally use a Set here, but the order may be important
				languageDialects.add(languageDialect);
			}
		}
		return languageDialects;
	}

}
