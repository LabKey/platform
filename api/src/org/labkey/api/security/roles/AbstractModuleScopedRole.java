/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.api.security.roles;

import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.Permission;

/**
 * An {@link AbstractRole} that only shows up as an option when its providing module is enabled in the current container.
 * Created by Josh on 1/25/2016.
 */
public abstract class AbstractModuleScopedRole extends AbstractRole
{
    protected AbstractModuleScopedRole(String name, String description, Class<? extends Module> sourceModuleClass, Class<? extends Permission>... perms)
    {
        super(name, description, sourceModuleClass, perms);
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return resource instanceof Container && ((Container) resource).getActiveModules().contains(getSourceModule());
    }
}
