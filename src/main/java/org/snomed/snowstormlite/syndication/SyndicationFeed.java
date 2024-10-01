package org.snomed.snowstormlite.syndication;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.List;

@XmlRootElement(name = "feed")
public class SyndicationFeed {

	private String title;

	private List<SyndicationFeedEntry> entries;

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	@XmlElement(name="entry", type = SyndicationFeedEntry.class)
	public List<SyndicationFeedEntry> getEntries() {
		return entries;
	}

	public void setEntries(List<SyndicationFeedEntry> entries) {
		this.entries = entries;
	}
}
