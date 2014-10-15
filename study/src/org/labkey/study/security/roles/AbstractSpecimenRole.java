/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
package org.labkey.study.security.roles;

import org.labkey.api.data.Container;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.AbstractRole;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudyModule;

/**
 * User: jeckels
 * Date: Feb 25, 2011
 */
public class AbstractSpecimenRole extends AbstractRole
{
    @SafeVarargs
    protected AbstractSpecimenRole(String name, String description, Class<? extends Permission>... perms)
    {
        super(name, description, StudyModule.class, perms);
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return super.isApplicable(policy,resource) && branchContainsStudy((Container)resource);
    }

    private boolean branchContainsStudy(Container container)
    {
        if (null != StudyService.get().getStudy(container))
            return true;

        for (Container child : container.getChildren())
        {
            if (branchContainsStudy(child))
                return true;
        }

        return false;
    }
}
