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

import java.util.Collection;
import java.util.Map;

/**
 * Handles a startup property scope that, unlike a StartupStartupPropertyHandler with fixed property names, has ad hoc
 * property names, such as an email address or a group name. A single StartupProperty is provided in the constructor
 * for documentation purposes.
 */
public abstract class LenientStartupPropertyHandler<T extends StartupProperty> extends StartupPropertyHandler<T>
{
    private final T _property;

    protected LenientStartupPropertyHandler(String scope, T property)
    {
        super(scope, property.getClass().getName(), Map.of(scope, property));
        _property = property;
    }

    public T getProperty()
    {
        return _property;
    }

    // A collection of entries is passed instead of a map because there are likely multiple entries that all correspond
    // to the same StartupProperty.
    public abstract void handle(Collection<StartupPropertyEntry> entries);
}
