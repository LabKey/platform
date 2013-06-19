/*
 * Copyright (c) 2010-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.files;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.data.Container;
import org.labkey.api.notification.EmailPref;
import org.labkey.api.notification.EmailPrefFilter;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.*;
import org.labkey.api.security.permissions.ReadPermission;

import java.util.ArrayList;
import java.util.List;

/**
 * User: klum
 * Date: Apr 23, 2010
 * Time: 11:56:15 AM
 */
public class FileContentEmailPrefFilter extends EmailPrefFilter
{
    private int _prefSelection;

    public FileContentEmailPrefFilter(int prefSelection)
    {
        _prefSelection = prefSelection;
    }

    @Override
    public EmailPref getEmailPref()
    {
        return new FileContentEmailPref();
    }

    @Override
    public EmailPref getDefaultEmailPref()
    {
        return new FileContentDefaultEmailPref();
    }

    @Override
    public User[] getUsers(Container c)
    {
        List<User> users = new ArrayList<>();

        for (User user : SecurityManager.getProjectUsers(c))
        {
            if (c.hasPermission(user, ReadPermission.class))
                users.add(user);
        }
        return users.toArray(new User[users.size()]);
    }

    @Override
    public boolean accept(String pref)
    {
        return (NumberUtils.toInt(pref) & _prefSelection) != 0;
    }
}
