/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.api.security.impersonation;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

/**
 * User: adam
 * Date: 11/9/11
 * Time: 10:53 AM
 */
public class ImpersonateGroupContext implements ImpersonationContext
{
    @Override
    public boolean isImpersonated()
    {
        return true;
    }

    @Override
    public boolean isAllowedRoles()
    {
        return false;
    }

    @Override
    public Container getStartingProject()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Container getImpersonationProject()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public User getImpersonatingUser()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getNavTreeCacheKey()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public URLHelper getReturnURL()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
