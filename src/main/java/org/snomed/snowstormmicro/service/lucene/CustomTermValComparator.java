package org.snomed.snowstormmicro.service.lucene;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.LeafFieldComparator;
import org.apache.lucene.search.Scorable;
import org.springframework.data.util.Pair;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CustomTermValComparator extends FieldComparator<String> implements LeafFieldComparator {

	private final Map<Integer, Integer> slotScores = new Int2IntOpenHashMap();
	private final Map<Integer, String> slotValues = new HashMap<>();

	private Integer topScore;
	private Integer bottomSlot;

	private LeafReader reader;

	@Override
	public int compare(int slot1, int slot2) {
		int i = slotScores.get(slot1).compareTo(slotScores.get(slot2));
		if (i == 0) {
			return slotValues.get(slot1).compareTo(slotValues.get(slot2));
		}
		return i;
	}

	@Override
	public void setTopValue(String value) {
		this.topScore = value.length();
	}

	@Override
	public String value(int slot) {
		return slotValues.get(slot);
	}

	@Override
	public LeafFieldComparator getLeafComparator(LeafReaderContext context) {
		reader = context.reader();
		return this;
	}

	@Override
	public void setBottom(int slot) {
		bottomSlot = slot;
	}

	@Override
	public int compareTop(int doc) throws IOException {
		int score = getScore(doc);
		return topScore.compareTo(score);
	}

	@Override
	public int compareBottom(int doc) throws IOException {
		int score = getScore(doc);
		return slotScores.get(bottomSlot).compareTo(score);
	}

	private int getScore(int doc) throws IOException {
		return getScoreAndValue(doc).getFirst();
	}

	private Pair<Integer, String> getScoreAndValue(int doc) throws IOException {
		Document document = reader.storedFields().document(doc, Collections.singleton("term_en"));
		String[] values = document.getValues("term_en");
		int score = 1000;
		String bestValue = "";
		for (String value : values) {
			if (value.length() < score) {
				score = value.length();
				bestValue = value;
			}
		}
		return Pair.of(score, bestValue);
	}

	@Override
	public void copy(int slot, int doc) throws IOException {
		Pair<Integer, String> scoreAndValue = getScoreAndValue(doc);
		slotScores.put(slot, scoreAndValue.getFirst());
		slotValues.put(slot, scoreAndValue.getSecond());
	}

	@Override
	public void setScorer(Scorable scorer) {
	}

}