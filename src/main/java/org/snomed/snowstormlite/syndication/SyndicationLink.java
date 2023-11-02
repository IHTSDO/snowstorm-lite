package org.snomed.snowstormlite.syndication;

import javax.xml.bind.annotation.XmlAttribute;

public class SyndicationLink {

	private String rel;
	private String type;
	private String href;
	private Integer length;

	@XmlAttribute
	public String getRel() {
		return rel;
	}

	public void setRel(String rel) {
		this.rel = rel;
	}

	@XmlAttribute
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	@XmlAttribute
	public String getHref() {
		return href;
	}

	public void setHref(String href) {
		this.href = href;
	}

	@XmlAttribute
	public Integer getLength() {
		return length;
	}

	public void setLength(Integer length) {
		this.length = length;
	}
}
