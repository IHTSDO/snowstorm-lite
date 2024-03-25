package org.snomed.snowstormlite.service;

import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;

import java.util.Set;

public class TermSearchHelper {

	public static String foldTerm(String term, Set<Character> charactersNotFolded) {
		if (charactersNotFolded == null) {
			return term;
		}
		char[] chars = term.toLowerCase().toCharArray();
		char[] charsFolded = new char[chars.length * 2];

		// Fold all characters
		int charsFoldedOffset = 0;
		for (int i = 0; i < chars.length; i++) {
			if (charactersNotFolded.contains(chars[i])) {
				charsFolded[charsFoldedOffset] = chars[i];
			} else {
				int length = ASCIIFoldingFilter.foldToASCII(chars, i, charsFolded, charsFoldedOffset, 1);
				if (length != charsFoldedOffset + 1) {
					charsFoldedOffset = length - 1;
				}
			}
			charsFoldedOffset++;
		}
		return new String(charsFolded, 0, charsFoldedOffset);
	}

}
