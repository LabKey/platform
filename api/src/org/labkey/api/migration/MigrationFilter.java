/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.api.migration;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.GUID;

/**
 * A MigrationFilter adds support for the named filter property in the migration configuration file. If present,
 * saveFilter() is called with the container guid and property value. Modules can register these to present
 * module-specific filters.
 */
public interface MigrationFilter
{
    String getName();

    // Implementations should validate guid nullity
    void saveFilter(@Nullable GUID guid, String value);
}
