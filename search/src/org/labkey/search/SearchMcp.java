/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.search;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.mcp.McpContext;
import org.labkey.api.mcp.McpService;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchService;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.util.Map;

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

    @Tool(description = "Search this LabKey server.  This may be useful for site navigation purposes.  When rendering results that use this tool present full URLs whenever relevant.")
    String siteSearch(
            @ToolParam(description = mdSearchHelp) String query,
            @ToolParam(required=false, description="comma separated list of categories, use category=navigation to find folders/projects/studies.  use the listSearchCategories tool to find other options.") String categories
    )
    {
        SearchService ss = SearchService.get();
        if (isBlank(query))
        {
            return new JSONObject(Map.of(
                    "success", Boolean.TRUE,
                    "hits", new JSONArray())).toString();
        }

        var list = PageFlowUtil.splitStringToValuesForImport(trimToEmpty(categories));

        McpContext context = McpContext.get();
        Path contextPath = AppProps.getInstance().getParsedContextPath();
        var options = new SearchService.SearchOptions.Builder(query,context.getUser(),context.getContainer())
                .limit(20)
                .scope(SearchScope.All);
        if (!list.isEmpty())
                options.categories(StringUtils.join(list,' '));

        try
        {
            JSONArray hits = new JSONArray();
            var searchResult = ss.search(options.build());
            for (var hit : searchResult.hits)
            {
                JSONObject o = new JSONObject();
                o.put("title", hit.title);
                o.put("container", hit.container);
                o.put("url", hit.fullHref(contextPath));
                o.put("summary", trimToEmpty(hit.summary));
                o.put("score", hit.score);
                o.put("identifiers", hit.identifiers);
                o.put("category", trimToEmpty(hit.category));
                hits.put(o);
            }
            return new JSONObject(Map.of(
                    "success", Boolean.TRUE,
                    "baseServerUrl", AppProps.getInstance().getBaseServerUrl(),
                    "hits", hits,
                    "totalHits", searchResult.totalHits
            )).toString();
        }
        catch (IOException io)
        {
            return new JSONObject(Map.of(
                    "success", Boolean.FALSE,
                    "error", io.getMessage()
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
