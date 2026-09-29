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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.TableInfo;

import java.util.List;

/**
 * Adds table-specific knowledge to grid search suggestions, such as the name expressions of a sample type. Register
 * with {@link org.labkey.api.query.QueryService#registerFilterSuggestionProvider}.
 */
public interface FilterSuggestionProvider
{
    boolean handles(@NotNull TableInfo table);

    /** Called after the columns are classified from their metadata; may add facts to any of them. */
    void describe(@NotNull FilterSuggestionContext context, @NotNull List<SuggestionColumn> columns);

    /** Changes whenever the table's data changes; null means data-derived facts can't be cached for the table. */
    default @Nullable String getChangeToken(@NotNull TableInfo table)
    {
        return null;
    }
}
