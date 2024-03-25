package org.snomed.snowstormlite.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LanguageDialect {

	private String languageCode;
	private Long languageReferenceSet;

	public LanguageDialect() { }

	public LanguageDialect(String languageCode) {
		this.languageCode = languageCode;
	}

	public LanguageDialect(String languageCode, Long languageReferenceSet) {
		this.languageCode = languageCode;
		this.languageReferenceSet = languageReferenceSet;
	}

	public String getLanguageCode() {
		return languageCode;
	}

	public Long getLanguageReferenceSet() {
		return languageReferenceSet;
	}

	public static List<String> toLanguageCodes(List<LanguageDialect> languageDialects) {
		List<String> languageCodes = new ArrayList<>();
		if (languageDialects != null) {
			for (LanguageDialect languageDialect : languageDialects) {
				String code = languageDialect.getLanguageCode();
				if (code != null && !code.isEmpty()) {
					languageCodes.add(code);
				}
			}
		}
		return languageCodes;
	}

	public String toString() {
		String str = "";
		if (languageCode != null) {
			str = languageCode;
			if (languageReferenceSet != null) {
				str += " - ";
			}
		}

		if (languageReferenceSet != null) {
			str += languageReferenceSet;
		}
		return str;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LanguageDialect that = (LanguageDialect) o;
		return languageCode.equals(that.languageCode) &&
				Objects.equals(languageReferenceSet, that.languageReferenceSet);
	}

	@Override
	public int hashCode() {
		return Objects.hash(languageCode, languageReferenceSet);
	}
}
