/*
 * Copyright (c) 2007 LabKey Corporation
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

package org.labkey.wiki;

import org.labkey.api.data.Container;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.wiki.model.Wiki;

/**
 * Enacapsulates permission testing for wikis, handling the UPDATEOWN and DELETEOWN cases
 *
 * Extend this class to do other kinds of permission checking.
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Nov 7, 2007
 * Time: 4:01:58 PM
 */
public class BaseWikiPermissions
{
    private User _user;
    private Container _container;

    public BaseWikiPermissions(User user, Container container)
    {
        assert(null != user && null != container);
        _user = user;
        _container = container;
    }

    public boolean allowRead(Wiki wiki)
    {
        return _container.hasPermission(_user, ACL.PERM_READ) ||
                (_container.hasPermission(_user, ACL.PERM_READOWN) && userIsCreator(wiki));
    }

    public boolean allowInsert()
    {
        return _container.hasPermission(_user, ACL.PERM_INSERT);
    }

    public boolean allowUpdate(Wiki wiki)
    {
        return _container.hasPermission(_user, ACL.PERM_UPDATE) ||
                (_container.hasPermission(_user, ACL.PERM_UPDATEOWN) && userIsCreator(wiki));
    }

    public boolean allowDelete(Wiki wiki)
    {
        return _container.hasPermission(_user, ACL.PERM_DELETE) ||
                (_container.hasPermission(_user, ACL.PERM_DELETEOWN) && userIsCreator(wiki));
    }

    public boolean allowAdmin()
    {
        return _container.hasPermission(_user, ACL.PERM_ADMIN);
    }

    public boolean userIsCreator(Wiki wiki)
    {
        return wiki.getCreatedBy() == _user.getUserId();
    }
}
