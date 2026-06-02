/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface ObjectReferencer
{
    default Collection<Long> getItemsWithReferences(Collection<Long> referencedRowIds, @NotNull String referencedSchemaName)
    {
        return getItemsWithReferences(referencedRowIds, referencedSchemaName, null);
    }

    @NotNull Collection<Long> getItemsWithReferences(Collection<Long> referencedRowIds, @NotNull String referencedSchemaName, @Nullable String referencedQueryName);

    @Nullable
    String getObjectReferenceDescription(Class<?> referencedClass);
}
