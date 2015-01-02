/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

/**
 * User: adam
 * Date: 3/28/2014
 * Time: 9:42 AM
 */
public abstract class AbstractImpersonationContext implements ImpersonationContext
{
    private final User _adminUser;
    private final @Nullable Container _project;
    private final URLHelper _returnURL;

    protected AbstractImpersonationContext(User adminUser, @Nullable Container project, URLHelper returnURL)
    {
        _adminUser = adminUser;
        _project = project;
        _returnURL = returnURL;
    }

    @Override
    public final boolean isImpersonating()
    {
        return true;
    }

    @Override
    public @Nullable Container getImpersonationProject()
    {
        return _project;
    }

    @Override
    public final User getAdminUser()
    {
        return _adminUser;
    }

    @Override
    public final URLHelper getReturnURL()
    {
        return _returnURL;
    }

    @Override
    public final void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
    {
        // If impersonating, we shouldn't be adding an impersonate menu
        throw new IllegalStateException("Shouldn't be adding an impersonate menu while impersonating!");
    }
}
