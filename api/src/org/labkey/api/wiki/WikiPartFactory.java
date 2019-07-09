/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.wiki;

import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;

public class WikiPartFactory
{
    public enum Privilege
    {
        ALL_USERS,
        ONLY_GUESTS,
        ONLY_REGISTERED_USERS
    }

    private final String _activeModuleName;
    private final WebPartFactory _factory;
    private final Privilege _privilege;

    public WikiPartFactory(WebPartFactory factory, Privilege privilege, String moduleName)
    {
        _factory = factory;
        _privilege = privilege;
        _activeModuleName = moduleName;
    }

    public WebPartFactory getWebPartFactory()
    {
        return _factory;
    }

    public boolean shouldInclude(ViewContext context)
    {
        User user = context.getUser();
        if (user == null)
            return false;

        if (Privilege.ONLY_GUESTS.equals(_privilege) && !user.isGuest())
            return false;
        if (Privilege.ONLY_REGISTERED_USERS.equals(_privilege) && user.isGuest())
            return false;
        if (_activeModuleName != null)
            return context.getContainer().hasActiveModuleByName(_activeModuleName);

        return true;
    }
}
