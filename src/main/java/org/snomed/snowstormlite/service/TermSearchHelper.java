package org.snomed.snowstormlite.service;

import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;

import java.util.Set;

/**
 * Term Search Helper - provides text processing utilities for multilingual terminology search.
 * 
 * This utility class handles character folding for search optimization while preserving
 * language-specific characters that carry semantic meaning. The folding process converts
 * accented characters to their ASCII equivalents (e.g., 'é' -> 'e') while preserving
 * characters that are semantically distinct in certain languages (e.g., 'ä', 'ö', 'å' in Swedish).
 * 
 * Key use cases:
 * - Index-time text processing: normalize terms for storage in Lucene index
 * - Query-time text processing: normalize search terms for matching
 * - Language-specific search: preserve important diacritics per language configuration
 * 
 * Python implementation considerations:
 * - Use unicodedata.normalize() for Unicode normalization
 * - Implement custom character mapping dictionaries for language-specific folding
 * - Consider using ftfy or unidecode libraries for advanced text normalization
 * - Use sets for fast character lookup in preservation logic
 */
public class TermSearchHelper {

	/**
	 * Folds a term by converting accented characters to ASCII equivalents while preserving
	 * language-specific characters that should not be folded.
	 * 
	 * Algorithm:
	 * 1. Convert entire term to lowercase for case-insensitive search
	 * 2. Create output buffer twice the size of input (folding can expand characters)
	 * 3. For each character:
	 *    - If character is in preservation set: copy unchanged
	 *    - Otherwise: apply Lucene ASCIIFoldingFilter transformation
	 * 4. Return normalized string with correct length
	 * 
	 * Character preservation examples:
	 * - Swedish: preserve 'å', 'ä', 'ö' as they represent distinct phonemes
	 * - Norwegian: preserve 'æ', 'ø', 'å' for semantic distinction
	 * - French: fold all accents as they don't change word meaning in search context
	 * 
	 * Performance considerations:
	 * - Uses char arrays for efficient processing
	 * - Single-pass algorithm minimizes memory allocations
	 * - Set lookup is O(1) for character preservation checks
	 * 
	 * Python implementation:
	 * ```python
	 * def fold_term(term: str, characters_not_folded: Set[str]) -> str:
	 *     if not characters_not_folded:
	 *         return term.lower()
	 *     
	 *     result = []
	 *     for char in term.lower():
	 *         if char in characters_not_folded:
	 *             result.append(char)
	 *         else:
	 *             # Use unicodedata.normalize('NFD', char) then filter
	 *             folded = unidecode.unidecode(char)
	 *             result.append(folded)
	 *     
	 *     return ''.join(result)
	 * ```
	 * 
	 * @param term The input term to be folded
	 * @param charactersNotFolded Set of characters that should be preserved (not folded)
	 * @return The folded term with ASCII equivalents and preserved characters
	 */
	public static String foldTerm(String term, Set<Character> charactersNotFolded) {
		// If no preservation set provided, return term as-is (no folding needed)
		if (charactersNotFolded == null) {
			return term;
		}
		
		// Convert to lowercase for case-insensitive processing
		char[] chars = term.toLowerCase().toCharArray();
		
		// Create output buffer with double capacity (folding can expand characters)
		char[] charsFolded = new char[chars.length * 2];

		// Process each character with preservation logic
		int charsFoldedOffset = 0;
		for (int i = 0; i < chars.length; i++) {
			if (charactersNotFolded.contains(chars[i])) {
				// Preserve language-specific characters unchanged
				charsFolded[charsFoldedOffset] = chars[i];
			} else {
				// Apply Lucene ASCII folding transformation
				// This converts accented characters to their ASCII base form
				int length = ASCIIFoldingFilter.foldToASCII(chars, i, charsFolded, charsFoldedOffset, 1);
				
				// Handle cases where folding expands character count
				if (length != charsFoldedOffset + 1) {
					charsFoldedOffset = length - 1;
				}
			}
			charsFoldedOffset++;
		}
		
		// Return string with correct length (trimming unused buffer space)
		return new String(charsFolded, 0, charsFoldedOffset);
	}

}
