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

import org.labkey.api.collections.LabKeyCollectors;

import java.util.Map;
import java.util.stream.Stream;

// Base class for startup property handlers that want to work with a map of property entries. Extracted from
// StandardStartupPropertyHandler to allow OptionalFeatureStartupPropertyHandler (which can't use an enum
// to provide its startup properties) to share implementation and use the existing handleStartupProperties()
// method. Note that we want StandardStartupPropertyHandler's type parameter (T) to extend Enum<T>, which is
// why we can't just throw a new constructor on that class.
public abstract class MapBasedStartupPropertyHandler<T extends StartupProperty> extends StartupPropertyHandler<T>
{
    public MapBasedStartupPropertyHandler(String scope, String startupPropertyClassName, Stream<T> properties)
    {
        super(scope, startupPropertyClassName, properties
            .filter(sp -> null != sp.getPropertyName())
            .collect(LabKeyCollectors.toCaseInsensitiveLinkedMap(StartupProperty::getPropertyName, sp->sp)));
    }

    public abstract void handle(Map<T, StartupPropertyEntry> properties);
}
