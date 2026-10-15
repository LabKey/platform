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
package org.labkey.api.query.suggestions;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One option in the grid search menu, serialized as-is in the query-getFilterSuggestions.api response. Which fields are
 * set depends on the kind: FILTER has fieldKey and op, COMPOSE has fields and valueType, SEARCH has neither.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FilterSuggestion(
    @NotNull String kind,
    @NotNull String label,
    @NotNull String source,
    @NotNull String value,
    @Nullable String fieldKey,
    @Nullable String op,
    @Nullable List<ComposeField> fields,
    @Nullable String valueType,
    @Nullable Match match,
    @Nullable ColumnMatch colMatch
)
{
    public static final String KIND_COMPOSE = "compose";
    public static final String KIND_FILTER = "filter";
    public static final String KIND_SEARCH = "search";

    /** @param op the filter type's URL suffix, e.g. "eq" */
    public record ComposeField(@NotNull String fieldKey, @NotNull String op) {}

    /**
     * Where the label shows the search term, which the menu bolds.
     * @param end exclusive
     */
    public record Match(int start, int end) {}

    /**
     * Where the label shows a column's caption, which the client replaces with its own caption for the grid's column.
     * @param end exclusive
     * @param fieldKey the grid column as the request named it
     */
    public record ColumnMatch(int start, int end, @NotNull String fieldKey) {}

    /** @param verb joins the caption and the value, e.g. " is " */
    public static FilterSuggestion filter(SuggestionColumn column, String op, String value, String verb, String source, String term)
    {
        String labelPrefix = column.getCaption() + verb;
        Match match = null;
        int index = Strings.CI.indexOf(value, term);
        if (index >= 0)
            match = new Match(labelPrefix.length() + index, labelPrefix.length() + index + term.length());
        ColumnMatch colMatch = new ColumnMatch(0, column.getCaption().length(), column.getGridFieldKey());
        return new FilterSuggestion(KIND_FILTER, labelPrefix + value, source, value, column.getFieldKey(), op, null, null, match, colMatch);
    }

    /** @param fields ordered best-first; the first is selected when the filter modal opens */
    public static FilterSuggestion compose(List<ComposeField> fields, String value, String valueType, String label, @Nullable Match match, @Nullable ColumnMatch colMatch)
    {
        return new FilterSuggestion(KIND_COMPOSE, label, "compose", value, null, null, List.copyOf(fields), valueType, match, colMatch);
    }

    public static FilterSuggestion search(String value)
    {
        String prefix = "Search all columns for ";
        return new FilterSuggestion(KIND_SEARCH, prefix + value, "fallback", value, null, null, null, null, new Match(prefix.length(), prefix.length() + value.length()), null);
    }
}
