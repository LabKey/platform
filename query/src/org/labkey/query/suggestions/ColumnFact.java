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
package org.labkey.query.suggestions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** One cached, data-derived fact about a column. Immutable, so entries can be shared across requests. */
public sealed interface ColumnFact
{
    /**
     * @param min           Number or LocalDate; null when not requested or the column has no values
     * @param distinctCount null when not requested
     * @param rangeUnknown  the range held NaN or infinity, so range checks are skipped rather than excluding the column
     */
    record Stats(@Nullable Object min, @Nullable Object max, @Nullable Long distinctCount, boolean rangeUnknown) implements ColumnFact {}

    /** @param values null when the column has more than {@link ColumnFactsCache#DISTINCT_VALUE_LIMIT} distinct values */
    record Values(@Nullable List<String> values) implements ColumnFact
    {
        public Values
        {
            values = values == null ? null : List.copyOf(values);
        }

        public boolean isOverflow()
        {
            return values == null;
        }
    }

    record Shapes(@NotNull List<String> shapes) implements ColumnFact
    {
        public Shapes
        {
            shapes = List.copyOf(shapes);
        }
    }

    /** Remembered so a failing read (e.g. a missing query parameter) isn't retried on every keystroke. */
    record Failed(@NotNull String message) implements ColumnFact {}
}
