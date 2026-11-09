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
package org.labkey.api.settings;

import org.jetbrains.annotations.Nullable;

public interface StartupProperty
{
    // As a convenience for enum implementations, where getPropertyName() simply returns Enum.name()
    default String name()
    {
        throw new IllegalStateException("Must override getPropertyName()");
    }

    // Implementations can override this to use an alternative property name (not name()) or to filter out specific
    // properties, such as when the startup property enum is serving multiple purposes. Returning null will omit the
    // property from startup property handling and documentation.
    default @Nullable String getPropertyName()
    {
        return name();
    }

    String getDescription();
}
