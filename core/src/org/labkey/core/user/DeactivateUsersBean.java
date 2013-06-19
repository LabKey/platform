/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
package org.labkey.core.user;

import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.*;

/*
* User: Dave
* Date: Nov 3, 2008
* Time: 11:35:35 AM
*/
public class DeactivateUsersBean
{
    private boolean _activate = false;
    private ActionURL _redirUrl;

    private List<User> _users = new ArrayList<>();

    public DeactivateUsersBean(boolean activate, ActionURL redirUrl)
    {
        _activate = activate;
        _redirUrl = null != redirUrl ? redirUrl : new UserController.UserUrlsImpl().getSiteUsersURL();
    }

    public boolean isActivate()
    {
        return _activate;
    }

    public List<User> getUsers()
    {
        return Collections.unmodifiableList(_users);
    }

    public void addUser(User user)
    {
        if(null != user && user.isActive() != _activate)
            _users.add(user);
    }

    public ActionURL getRedirUrl()
    {
        return _redirUrl;
    }
}