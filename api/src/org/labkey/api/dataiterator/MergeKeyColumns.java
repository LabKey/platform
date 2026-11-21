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
package org.labkey.api.dataiterator;

import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Input rows plus the columns that match each one to the existing row a MERGE will update */
public record MergeKeyColumns(DataIterator input, Set<String> columnNames)
{
    /** A table whose MERGE key is computed during import, after the before triggers fire */
    public interface Provider
    {
        /** @return the input with its key columns added, or null when the input can't identify existing rows */
        @Nullable MergeKeyColumns addMergeKeyColumns(DataIterator input, DataIteratorContext context);
    }
}
