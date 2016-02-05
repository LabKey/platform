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
import org.labkey.api.module.Module;
import org.labkey.api.protein.ProteomicsModule;

/**
 * Base class for factories that produce webparts available in all proteomics folder types, identified by having
 * at least one instance of {@link ProteomicsModule} enabled in the current container.
 * User: gktaylor
 * Date: 5 31, 2013
 */
public abstract class ProteomicsWebPartFactory extends BaseWebPartFactory
{
    public ProteomicsWebPartFactory(String name, @NotNull String defaultLocation, String... additionalLocations)
    {
        super(name, defaultLocation, additionalLocations);
    }

    public ProteomicsWebPartFactory(String name)
    {
        super(name);
    }

    /** Available in all proteomics folder types, as long as current location is default location */
    @Override
    public final boolean isAvailable(Container c, String location)
    {
        for (Module module1 : c.getActiveModules())
        {
            if (module1 instanceof ProteomicsModule)
                return getAllowableLocations().contains(location);
        }
        return false;
    }
}
