## Search Ranking Guide
How Snowstorm Lite ranks text search results, and how to configure it.

Text search is a filtered ValueSet `$expand`, for example `filter=asthma` on the implicit ValueSet of all of SNOMED CT
(`http://snomed.info/sct?fhir_vs`). The same ranking is used by the dashboard mini browser and by the MCP search tool.

### Ranking
Concepts are ranked by their shortest description that matches the search, like the SNOMED CT Browser:
1. Active concepts first
2. Shortest matching description term, e.g. `TEE procedure` ranks _Transesophageal echocardiography_ high for `procedure`
3. Alphabetical order of that term

A description matches when every search word is the start of a word in that description, in one of the requested
languages (`displayLanguage` or `Accept-Language`; English is always included).

### Description sort index
Snowstorm Lite stores all descriptions of a concept in one Lucene document, so Lucene cannot sort concepts by their best
matching description on its own. The description sort index is a second, smaller Lucene index with one document per
active description, used only to rank filtered searches. With it:
- Ranking is exact across all matching concepts
- Paging is stable at any offset and the total is exact
- Memory use per search is small and does not grow with the number of matches

It is enabled by default. It lives in the `description-sort` subdirectory of the main index directory (`index.path`),
so it is kept in the same storage volume as the main index.

#### Lifecycle
- **Built automatically** after each SNOMED CT import, and at startup when it is missing or out of date, for example after
  upgrading from a version without it. The startup build runs in the background; no re-import is needed.
- **Deleted** when SNOMED CT is reset from the dashboard Settings page.
- **Versioned**: the index records the CodeSystem version it was built from. While it does not match the loaded
  CodeSystem (during an import, or while it is being built), filtered searches use the relevance sort window below.

#### Resources
Measured with the Argentina Edition (International plus Spanish, 2.7 million descriptions):

| | |
|---|---|
| Build time | ~30 seconds (also with `-Xmx1g`) |
| Disk | ~100 MB, about a third of the main index |
| `procedure` search, 50 results | ~0.08 seconds |

#### Disabling
```properties
search.description-sort-index.enabled=false
```
The index is then not built, and filtered searches use the relevance sort window. An existing `description-sort`
directory can be deleted.

### Relevance sort window
Without the description sort index (disabled, or not built yet) the first 100 results of a filtered search are ranked
approximately: Lucene orders the matches by the length of the preferred term and FSN, and the first results of that
order are re-sorted in memory by the shortest matching term. Only concepts inside that window can reach the top.

```properties
# Number of results re-sorted in memory (default 250)
search.valueset-expand.relevance-sort-window=250
```
Larger values improve the approximation but cost time and memory on every search: all concepts in the window are loaded
into memory. For example `procedure` takes ~0.03 s with 250 and ~0.3 s with 10,000, and memory use grows with each
concurrent search. Prefer the description sort index to raising this value.
