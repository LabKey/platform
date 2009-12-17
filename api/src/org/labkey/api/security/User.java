/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.security;

import org.apache.log4j.Logger;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.Container;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;


public class User extends UserPrincipal implements Serializable, Cloneable
{
    static Logger _log = Logger.getLogger(User.class);

    static int[] _noGroups = new int[0];
    static int[] _guestGroups = new int[]{Group.groupGuests};

    private String _firstName = null;
    private String _lastName = null;
    private String _displayName = null;
    protected int[] _groups = null;
    private Date _lastLogin = null;
    private User _impersonatingUser = null;
    private Container _impersonationProject = null;
    private boolean _active = false;

    public static final User guest = new GuestUser("guest");
    static final User search = new GuestUser("search");

    private static class GuestUser extends User
    {
        GuestUser(String name)
        {
            super(name, 0);
            _groups = _guestGroups;
        }

        @Override
        public boolean isGuest()
        {
            return true;
        }

        @Override
        public boolean isActive()
        {
            return true;
        }
    }


    public User()
    {
        super(typeUser);
    }


    public User(String email, int id)
    {
        super(email, id, typeUser);
    }


    public void setEmail(String email)
    {
        setName(email);
    }


    public void setFirstName(String firstName)
    {
        _firstName = firstName;
    }


    public String getFirstName()
    {
        return _firstName;
    }


    public void setLastName(String lastName)
    {
        _lastName = lastName;
    }


    public String getLastName()
    {
        return _lastName;
    }

    /**
     * Returns the display name of this user. Requires a ViewContext
     * in order to check if the current web browser is logged in.
     * We then filter the display name for guests, stripping out the @domain.com
     * if it is an email address.
     * @param context
     * @return The diplay name, possibly sanitized
     */
    public String getDisplayName(ViewContext context)
    {
        if (null == context || context.getUser().isGuest())
        {
            return UserManager.sanitizeEmailAddress(_displayName);
        }
        return _displayName;
    }

    public void setDisplayName(String displayName)
    {
        _displayName = displayName;
    }

    public int[] getGroups()
    {
        if (_groups == null)
            _groups = GroupManager.getAllGroupsForUser(this);
        return _groups;
    }


    public boolean isFirstLogin()
    {
        return (null == getLastLogin());
    }


    public String getEmail()
    {
        return getName();
    }


    public String getFullName()
    {
        return ((null == _firstName) ? "" : _firstName) + ((null == _lastName) ? "" : " " + _lastName);
    }


    public String getFriendlyName()
    {
        return _displayName;
    }

    public boolean equals(Object u)
    {
        if (!(u instanceof User))
            return false;
        boolean equal = getUserId() == ((User) u).getUserId();
        assert !equal || getName().equals(((User) u).getName());
        return equal;
    }


    public int hashCode()
    {
        return getUserId();
    }


    public String toString()
    {
        return getName();
    }


    // Don't stash in User... need to check dynamically in case user's groups have changed
    public boolean isAdministrator()
    {
        return isAllowedRoles() && isInGroup(Group.groupAdministrators);
    }

    public boolean isDeveloper()
    {
        return isAllowedRoles() && isInGroup(Group.groupDevelopers);
    }

    // Never allow global roles (site admin, developer, etc.) if user is being impersonated within a project  
    public boolean isAllowedRoles()
    {
        return !isImpersonated() || null == getImpersonationProject();
    }

    public boolean isInGroup(int group)
    {
        int i = Arrays.binarySearch(getGroups(), group);
        return i >= 0;
    }


    public boolean isGuest()
    {
        return false;
    }


    @Override
    public Object clone() throws CloneNotSupportedException
    {
        return super.clone();
    }

    public User cloneUser()
    {
        try
        {
            return (User) clone();
        }
        catch (CloneNotSupportedException e)
        {
            throw new RuntimeException(e);
        }
    }

    public Date getLastLogin()
    {
        return _lastLogin;
    }

    public void setLastLogin(Date lastLogin)
    {
        _lastLogin = lastLogin;
    }

    public User getImpersonatingUser()
    {
        return _impersonatingUser;
    }

    public void setImpersonatingUser(User impersonatingUser)
    {
        _impersonatingUser = impersonatingUser;
    }

    public boolean isImpersonated()
    {
        return null != _impersonatingUser;
    }

    public Container getImpersonationProject()
    {
        return _impersonationProject;
    }

    public void setImpersonationProject(Container impersonationProject)
    {
        _impersonationProject = impersonationProject;
    }

    public boolean isActive()
    {
        return _active;
    }

    public void setActive(boolean active)
    {
        _active = active;
    }


    public static User getSearchUser()
    {
        return search;
    }
}
