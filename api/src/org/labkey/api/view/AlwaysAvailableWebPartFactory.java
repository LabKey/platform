/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;

/**
 * Base class for factories that produce webparts available in all folder types, regardless of which modules are enabled.
 * User: adam
 * Date: Oct 31, 2008
 */
public abstract class AlwaysAvailableWebPartFactory extends BaseWebPartFactory
{
    public AlwaysAvailableWebPartFactory(String name, boolean isEditable, boolean showCustomizeOnInsert, @NotNull String defaultLocation, String... additionaLocations)
    {
        super(name, isEditable, showCustomizeOnInsert, defaultLocation, additionaLocations);
    }

    public AlwaysAvailableWebPartFactory(String name, boolean isEditable, boolean showCustomizeOnInsert)
    {
        super(name, isEditable, showCustomizeOnInsert);
    }

    public AlwaysAvailableWebPartFactory(String name, @NotNull String defaultLocation, String... allowableLocations)
    {
        super(name, defaultLocation, allowableLocations);
    }

    public AlwaysAvailableWebPartFactory(String name)
    {
        super(name);
    }

    // Available in all folder types, as long as current location is default location

    @Override
    public final boolean isAvailable(Container c, String location)
    {
        return getAllowableLocations().contains(location);
    }
}
