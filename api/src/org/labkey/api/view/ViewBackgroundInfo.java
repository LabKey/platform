/*
 * Copyright (c) 2005-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.writer.ContainerUser;

import java.io.Serializable;

/**
 * For use inside background threads with no request object, to scope them to a {@link Container}, {@link User}, etc.
 * Created: Oct 4, 2005
 * @author bmaclean
 */
public class ViewBackgroundInfo implements Serializable, ContainerUser
{
    // Helper variables stored for use outside LabKey Server context
    private String _containerId;
    private String _urlString;
    @Nullable
    private String _userEmail;
    private int _userId;

    // Not supported outside the LabKey Server context
    private transient Container _container;
    private transient User _user;
    private transient ActionURL _url;

    public ViewBackgroundInfo(Container c, User u, ActionURL h)
    {
        setContainer(c);
        setUser(u);
        setURL(h);
    }

    public String getContainerId()
    {
        return _containerId;
    }

    @Nullable
    public String getUserEmail()
    {
        return _userEmail;
    }

    public int getUserId()
    {
        return _userId;
    }

    public Container getContainer()
    {
        if (_container == null && _containerId != null)
            _container = ContainerManager.getForId(_containerId);
        return _container;
    }

    public void setContainer(Container container)
    {
        _containerId = (container == null ? null : container.getId());
        _container = container;
    }

    public User getUser()
    {
        if (_user == null)
            _user = UserManager.getUser(_userId);
        return _user;
    }

    private void setUser(User user)
    {
        if (user == null)
            user = UserManager.getGuestUser();

        // Leave _userEmail null if guest, #29159
        if (!user.isGuest())
            _userEmail = user.getEmail();

        _userId = user.getUserId();
        _user = user;
    }

    public ActionURL getURL()
    {
        if (_url == null && _urlString != null)
            _url = new ActionURL(_urlString);
        return _url;
    }

    public void setURL(ActionURL url)
    {
        _urlString = (url == null ? null : url.toString());
        _url = url;
    }
}
