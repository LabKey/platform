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
    @Nullable String valueType
)
{
    public static final String KIND_COMPOSE = "compose";
    public static final String KIND_FILTER = "filter";
    public static final String KIND_SEARCH = "search";

    /** @param op the filter type's URL suffix, e.g. "eq" */
    public record ComposeField(@NotNull String fieldKey, @NotNull String op) {}

    public static FilterSuggestion filter(String fieldKey, String op, String value, String label, String source)
    {
        return new FilterSuggestion(KIND_FILTER, label, source, value, fieldKey, op, null, null);
    }

    /** @param fields ordered best-first; the first is selected when the filter modal opens */
    public static FilterSuggestion compose(List<ComposeField> fields, String value, String valueType, String label)
    {
        return new FilterSuggestion(KIND_COMPOSE, label, "compose", value, null, null, List.copyOf(fields), valueType);
    }

    public static FilterSuggestion search(String value)
    {
        return new FilterSuggestion(KIND_SEARCH, "Search all columns for \"" + value + "\"", "fallback", value, null, null, null, null);
    }
}
