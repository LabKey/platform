package org.labkey.search;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.mcp.McpContext;
import org.labkey.api.mcp.McpService;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchService.SearchHit;
import org.labkey.api.search.SearchService.SearchResult;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class SearchMcp implements McpService.McpImpl
{
    final static String mdSearchHelp = """
             The search functionality is implemented by Lucene. The query syntax is

             Core Syntax Elements

             *   **Terms and Phrases**:
             *   A **term** is a single word (e.g., `error`).
             *   A **phrase** is a group of words surrounded by double quotes (e.g., `"network error"`), which searches for all words in the specified order.
             *   **Fields**: You can search specific fields using the format `fieldName:searchValue` (e.g., `title:malaria` or `user:"John Doe"`).
             *   **Grouping**: Use parentheses `()` to group clauses and control Boolean logic (e.g., `title:(apple OR pie) AND description:apple`).

             Boolean Operators

             Boolean operators must be in **ALL CAPS**.

             *   `AND` (`&&`, `+`): Requires both terms to be present (e.g., `wifi AND luxury` or `+wifi +luxury`).
             *   `OR` (`||`): Requires at least one term to be present (default operator if none is specified) (e.g., `wifi OR luxury`).
             *   `NOT` (`!`, `-`): Excludes documents that contain the term after the operator (e.g., `wifi NOT luxury` or `wifi -luxury`).

             Term Modifiers

             *   **Wildcard Searches**:
             *   `?` for a single character (e.g., `te?t` matches "test" or "text").
             *   `*` for multiple characters (zero or more) (e.g., `test*` matches "test", "tests", "tester").
             *   _Note_: You cannot use `*` or `?` as the first character of a search term.
             *   **Fuzzy Searches**: Use the tilde `~` symbol at the end of a single word to find terms with a similar spelling (e.g., `roam~` finds "foam" and "roams"). An optional number between 0 and 2 can specify the required similarity (default is 0.5).
             *   **Proximity Searches**: Use the tilde `~` at the end of a phrase to find words within a specific distance (e.g., `"jakarta apache"~10` finds "jakarta" and "apache" within 10 words of each other).
             *   **Range Searches**: Match documents whose field values are between a lower and upper bound.
             *   Inclusive (square brackets): `mod_date:[20020101 TO 20030101]`.
             *   Exclusive (curly brackets): `title:{Aida TO Carmen}`.
             *   One-sided range: `score:[2.5 TO *]`.
             *   **Boosting Terms**: Use the caret `^` symbol with a numerical boost factor to increase the relevance of a term (e.g., `jakarta^4 apache` makes "jakarta" more relevant).

             Escaping Special Characters

             To use a special character as part of your search text, escape it with a single backslash `\\`. Special characters include:
             `+ - && || ! ( ) { } [ ] ^ " ~ * ? : \\ /`
            """;

    private static final int MIN_RESULTS_THRESHOLD = 3;
    private static final int MAX_RESULTS = 20;
    private static final Set<String> LUCENE_OPERATORS = Set.of("AND", "OR", "NOT", "TO");
    private static final Pattern FIELD_QUERY_PATTERN = Pattern.compile("\\w+:");
    private static final Pattern QUOTED_PHRASE_PATTERN = Pattern.compile("\"[^\"]*\"");
    private static final Pattern TERM_PATTERN = Pattern.compile("[^\\s]+");

    @Tool(description = "Search this LabKey server.  This may be useful for site navigation purposes.  When rendering results that use this tool present full URLs whenever relevant.")
    String siteSearch(
            @ToolParam(description = mdSearchHelp) String query,
            @ToolParam(required=false, description="comma separated list of categories, use category=navigation to find folders/projects/studies.  use the listSearchCategories tool to find other options.") String categories,
            ToolContext toolContext
    )
    {
        SearchService ss = SearchService.get();
        if (isBlank(query))
        {
            return new JSONObject(Map.of(
                    "success", Boolean.TRUE,
                    "hits", new JSONArray())).toString();
        }

        var categoryList = PageFlowUtil.splitStringToValuesForImport(trimToEmpty(categories));
        String categoryStr = categoryList.isEmpty() ? null : StringUtils.join(categoryList, ' ');

        McpContext context = McpContext.fromToolContext(toolContext);
        if (null == context)
            context = McpContext.get();
        Path contextPath = AppProps.getInstance().getParsedContextPath();

        try
        {
            // Phase 1: Exact query (current behavior)
            var results = executeSearch(ss, query, categoryStr, context);

            // Phase 2: Wildcard fallback if too few results
            if (results.size() < MIN_RESULTS_THRESHOLD)
            {
                String wildcardQuery = buildWildcardQuery(query);
                if (null != wildcardQuery)
                {
                    var wildcardResults = executeSearch(ss, wildcardQuery, categoryStr, context);
                    results = mergeResults(results, wildcardResults);
                }
            }

            // Phase 3: Fuzzy fallback if still too few results
            if (results.size() < MIN_RESULTS_THRESHOLD)
            {
                String fuzzyQuery = buildFuzzyQuery(query);
                if (null != fuzzyQuery)
                {
                    var fuzzyResults = executeSearch(ss, fuzzyQuery, categoryStr, context);
                    results = mergeResults(results, fuzzyResults);
                }
            }

            // Convert to JSON, capped at MAX_RESULTS
            JSONArray hits = new JSONArray();
            long totalHits = 0;
            for (var hit : results.values())
            {
                if (hits.length() >= MAX_RESULTS)
                    break;
                JSONObject o = new JSONObject();
                o.put("title", hit.title);
                Container c = null;
                try
                {
                    c = ContainerManager.getForId(hit.container);
                }
                catch (Exception ignore)
                {
                }
                o.put("container", null != c ? c.getPath() : hit.container);
                o.put("url", null != c ? hit.normalizeHref(contextPath, c) : hit.fullHref(contextPath));
                o.put("summary", trimToEmpty(hit.summary));
                o.put("score", hit.score);
                o.put("identifiers", hit.identifiers);
                o.put("category", trimToEmpty(hit.category));
                hits.put(o);
                totalHits++;
            }
            return new JSONObject(Map.of(
                    "success", Boolean.TRUE,
                    "baseServerUrl", AppProps.getInstance().getBaseServerUrl(),
                    "hits", hits,
                    "totalHits", totalHits
            )).toString();
        }
        catch (Exception e)
        {
            return new JSONObject(Map.of(
                    "success", Boolean.FALSE,
                    "error", String.valueOf(e.getMessage())
            )).toString();
        }
    }

    /**
     * Execute a search and return results as an ordered map keyed by URL for deduplication.
     */
    private LinkedHashMap<String, SearchHit> executeSearch(SearchService ss, String query, String categoryStr, McpContext context) throws IOException
    {
        var options = new SearchService.SearchOptions.Builder(query, context.getUser(), context.getContainer())
                .limit(MAX_RESULTS)
                .scope(SearchScope.All);
        if (null != categoryStr)
            options.categories(categoryStr);

        SearchResult searchResult = ss.search(options.build());
        var map = new LinkedHashMap<String, SearchHit>();
        for (var hit : searchResult.hits)
            map.putIfAbsent(hit.url, hit);
        return map;
    }

    /**
     * Merge additional results into existing results, preserving order and deduplicating by URL.
     */
    private LinkedHashMap<String, SearchHit> mergeResults(LinkedHashMap<String, SearchHit> existing, LinkedHashMap<String, SearchHit> additional)
    {
        var merged = new LinkedHashMap<>(existing);
        for (var entry : additional.entrySet())
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        return merged;
    }

    /**
     * Build a wildcard query by appending * to each plain term.
     * Skips Lucene operators, quoted phrases, and field-specific queries.
     * Returns null if no modifications were made.
     */
    String buildWildcardQuery(String query)
    {
        return transformTerms(query, term ->
        {
            // Skip operators, field queries, and terms already using wildcards/fuzzy
            if (LUCENE_OPERATORS.contains(term) || FIELD_QUERY_PATTERN.matcher(term).find()
                    || term.contains("*") || term.contains("?") || term.contains("~"))
                return term;
            return term + "*";
        });
    }

    /**
     * Build a fuzzy query by appending ~1 to each plain term >= 3 chars.
     * Skips Lucene operators, quoted phrases, and field-specific queries.
     * Returns null if no modifications were made.
     */
    String buildFuzzyQuery(String query)
    {
        return transformTerms(query, term ->
        {
            if (LUCENE_OPERATORS.contains(term) || FIELD_QUERY_PATTERN.matcher(term).find()
                    || term.contains("*") || term.contains("?") || term.contains("~"))
                return term;
            if (term.length() < 3)
                return term;
            return term + "~1";
        });
    }

    /**
     * Transform non-quoted terms in a query string using the provided function.
     * Returns null if no terms were modified.
     */
    private String transformTerms(String query, java.util.function.UnaryOperator<String> transformer)
    {
        // Find all quoted phrases so we can skip them
        List<int[]> quotedRanges = new ArrayList<>();
        Matcher quotedMatcher = QUOTED_PHRASE_PATTERN.matcher(query);
        while (quotedMatcher.find())
            quotedRanges.add(new int[]{quotedMatcher.start(), quotedMatcher.end()});

        StringBuilder result = new StringBuilder();
        boolean modified = false;
        int pos = 0;

        Matcher termMatcher = TERM_PATTERN.matcher(query);
        while (termMatcher.find())
        {
            // Append any whitespace before this term
            if (termMatcher.start() > pos)
                result.append(query, pos, termMatcher.start());

            String term = termMatcher.group();
            boolean inQuote = quotedRanges.stream().anyMatch(r -> termMatcher.start() >= r[0] && termMatcher.end() <= r[1]);

            if (inQuote)
            {
                result.append(term);
            }
            else
            {
                String transformed = transformer.apply(term);
                if (!transformed.equals(term))
                    modified = true;
                result.append(transformed);
            }
            pos = termMatcher.end();
        }

        // Append any trailing content
        if (pos < query.length())
            result.append(query, pos, query.length());

        return modified ? result.toString() : null;
    }

    @Tool(description = "Semantic search over documents in the vector store. Use this for conceptual or natural-language queries when keyword search may not find relevant results.")
    String semanticSearch(
            @ToolParam(description = "A natural language query describing what you are looking for") String query,
            @ToolParam(required = false, description = "Maximum number of results to return (default 10)") Integer topK
    )
    {
        if (isBlank(query))
        {
            return new JSONObject(Map.of(
                    "success", Boolean.TRUE,
                    "results", new JSONArray())).toString();
        }

        VectorStore vs = McpService.get().getVectorStore();
        if (null == vs)
        {
            return new JSONObject(Map.of(
                    "success", Boolean.FALSE,
                    "error", "Vector store is not available. It may need to be populated first."
            )).toString();
        }

        int k = (null == topK || topK <= 0) ? 10 : Math.min(topK, 20);

        try
        {
            List<Document> docs = vs.similaritySearch(
                    SearchRequest.builder().query(query).topK(k).build()
            );

            JSONArray results = new JSONArray();
            for (Document doc : docs)
            {
                JSONObject o = new JSONObject();
                Map<String, Object> metadata = doc.getMetadata();
                o.put("title", metadata.getOrDefault("title", ""));
                o.put("source", metadata.getOrDefault("source", ""));
                // Include a content preview (first 500 chars)
                String content = doc.getText();
                if (null != content && content.length() > 500)
                    content = content.substring(0, 500) + "...";
                o.put("contentPreview", trimToEmpty(content));
                results.put(o);
            }

            return new JSONObject(Map.of(
                    "success", Boolean.TRUE,
                    "results", results,
                    "totalResults", docs.size()
            )).toString();
        }
        catch (Exception e)
        {
            return new JSONObject(Map.of(
                    "success", Boolean.FALSE,
                    "error", "Semantic search failed: " + e.getMessage()
            )).toString();
        }
    }

    @Tool(description = "Return list of valid categories for the siteSearch tool")
    String listSearchCategories()
    {
        JSONArray list = new JSONArray();
        for (var cat : SearchService.get().getAllCategories())
        {
            list.put(cat.getName());
        }
        return new JSONObject(Map.of(
                "success", Boolean.TRUE,
                "categories", list
        )).toString();
    }
}
