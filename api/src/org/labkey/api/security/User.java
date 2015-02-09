/*
 * Copyright (c) 2004-2015 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.impersonation.ImpersonationContext;
import org.labkey.api.security.impersonation.NotImpersonatingContext;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.DeveloperRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.GUID;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;


public class User extends UserPrincipal implements Serializable, Cloneable
{
    private String _firstName = null;
    private String _lastName = null;
    private Integer _createdBy;
    private String _displayName = null;
    protected int[] _groups = null;
    private Date _lastLogin = null;
    private boolean _active = false;
    private String _phone;
    private String _mobile;
    private String _pager;
    private String _im;
    private String _description;

    private GUID entityId;

    private ImpersonationContext _impersonationContext = new NotImpersonatingContext();

    public static final User guest = new GuestUser("guest", "guest");
    // Search user is guest plus Reader everywhere
    private static User search;

    public User()
    {
        super(PrincipalType.USER);
    }


    public User(String email, int id)
    {
        super(email, id, PrincipalType.USER);
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

    public Integer getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(Integer createdBy)
    {
        _createdBy = createdBy;
    }

    /**
     * Returns the display name of this user.
     * If currentUser is null or a guest, we filter the display name, stripping out the @domain.com
     * if it is an email address.
     * @return The display name, possibly sanitized
     */
    // TODO: Check that currentUser really is the current user. Add a property to User that is set only by AuthFilter,
    // and a couple other places where treating an arbitrary user as currentUser is valid (e.g., email notifications)
    public String getDisplayName(@Nullable User currentUser)
    {
        if (currentUser == search)
            return StringUtils.join(new String[] {_displayName, getEmail(), _firstName, _lastName}, " ");
        if (null == currentUser || currentUser.isGuest())
        {
            return UserManager.sanitizeEmailAddress(_displayName);
        }
        return _displayName;
    }

    public void setDisplayName(String displayName)
    {
        _displayName = displayName;
    }

    /**
     * Spec 22327 Implement consistent autocomplete behavior, completion values,
     * and redisplay of names across the application.
     *
     */
    public String getAutocompleteName(@Nullable Container c, @Nullable User currentUser)
    {
        if (c != null && currentUser != null && SecurityManager.canSeeEmailAddresses(c, currentUser))
        {
            String result = getEmail();
            if (StringUtils.isNotBlank(getDisplayName(currentUser)))
                result += " (" + getDisplayName(currentUser) + ")";

            return result;
        }
        else
            return getDisplayName(currentUser);
    }


    public int[] getGroups()
    {
        if (_groups == null)
            _groups = _impersonationContext.getGroups(this);
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

    // Is the user a Site Administrator? This is NOT a check for AdminPermission.
    public boolean isSiteAdmin()
    {
        return isAllowedGlobalRoles() && isInGroup(Group.groupAdministrators);
    }

    // Note: site administrators are always developers; see GroupManager.computeAllGroups().
    public boolean isDeveloper()
    {
        return isAllowedGlobalRoles() && isInGroup(Group.groupDevelopers);
    }

    public boolean isAllowedGlobalRoles()
    {
        return _impersonationContext.isAllowedGlobalRoles();
    }

    public boolean isInGroup(int group)
    {
        int i = Arrays.binarySearch(getGroups(), group);
        return i >= 0;
    }

    @Override
    public Set<Role> getContextualRoles(SecurityPolicy policy)
    {
        return _impersonationContext.getContextualRoles(this, policy);
    }

    // Return the usual contextual roles
    public Set<Role> getStandardContextualRoles()
    {
        Set<Role> roles = new HashSet<>();

        if (isSiteAdmin())
            roles.add(RoleManager.siteAdminRole);
        if (isDeveloper())
            roles.add(RoleManager.getRole(DeveloperRole.class));

        return roles;
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

    public void setImpersonationContext(ImpersonationContext impersonationContext)
    {
        _impersonationContext = impersonationContext;
    }

    public @NotNull
    ImpersonationContext getImpersonationContext()
    {
        return _impersonationContext;
    }

    public boolean isImpersonated()
    {
        return _impersonationContext.isImpersonating();
    }

    // @NotNull when isImpersonated() is true... returns the admin user, with all normal roles & groups
    public User getImpersonatingUser()
    {
        return _impersonationContext.getAdminUser();
    }

    public @Nullable Container getImpersonationProject()
    {
        return _impersonationContext.getImpersonationProject();
    }

    public boolean isActive()
    {
        return _active;
    }

    public void setActive(boolean active)
    {
        _active = active;
    }

    public static synchronized User getSearchUser()
    {
        if (search == null)
        {
            search = new LimitedUser(new GuestUser("search"), new int[0], Collections.singleton(RoleManager.getRole(ReaderRole.class)), false);
        }
        return search;
    }

    public boolean isSearchUser()
    {
        return this == getSearchUser();
    }

    public String getPhone()
    {
        return _phone;
    }

    public void setPhone(String phone)
    {
        _phone = phone;
    }

    public String getMobile()
    {
        return _mobile;
    }

    public void setMobile(String mobile)
    {
        _mobile = mobile;
    }

    public String getPager()
    {
        return _pager;
    }

    public void setPager(String pager)
    {
        _pager = pager;
    }

    public String getIM()
    {
        return _im;
    }

    public void setIM(String im)
    {
        _im = im;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public GUID getEntityId()
    {
        return entityId;
    }

    public void setEntityId(GUID entityId)
    {
        this.entityId = entityId;
    }

    public static JSONObject getUserProps(User user, @Nullable Container container)
    {
        JSONObject props = new JSONObject();

        props.put("id", user.getUserId());
        props.put("displayName", user.getDisplayName(user));
        props.put("email", user.getEmail());
        props.put("phone", user.getPhone());

        boolean nonNullContainer = null != container;
        props.put("canInsert", nonNullContainer && container.hasPermission(user, InsertPermission.class));
        props.put("canUpdate", nonNullContainer && container.hasPermission(user, UpdatePermission.class));
        props.put("canUpdateOwn", nonNullContainer && container.hasPermission(user, UpdatePermission.class));
        props.put("canDelete", nonNullContainer && container.hasPermission(user, DeletePermission.class));
        props.put("canDeleteOwn", nonNullContainer && container.hasPermission(user, DeletePermission.class));
        props.put("isAdmin", nonNullContainer && container.hasPermission(user, AdminPermission.class));
        props.put("isSystemAdmin", user.isSiteAdmin());
        props.put("isGuest", user.isGuest());
        props.put("isSignedIn", 0 != user.getUserId() || !user.isGuest());

        return props;
    }
}
