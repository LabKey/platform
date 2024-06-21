/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.json.JSONString;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PHI;
import org.labkey.api.security.impersonation.ImpersonationContext;
import org.labkey.api.security.impersonation.NotImpersonatingContext;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.AnalystPermission;
import org.labkey.api.security.permissions.ApplicationAdminPermission;
import org.labkey.api.security.permissions.BrowserDeveloperPermission;
import org.labkey.api.security.permissions.CanImpersonateSiteRolesPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.PlatformDeveloperPermission;
import org.labkey.api.security.permissions.SiteAdminPermission;
import org.labkey.api.security.permissions.TrustedPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.Serializable;
import java.util.Date;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Represents a user in the LabKey system, typically tied to a specific individual, but see {@link GuestUser} for a
 * catch-all implementation representing anonymous users.
 */
public class User extends UserPrincipal implements Serializable, Cloneable, JSONString
{
    private String _firstName = null;
    private String _lastName = null;
    private Integer _createdBy;
    private Date _created;
    private String _displayName = null;
    protected PrincipalArray _groups = null;
    private Date _lastLogin = null;
    private Date _lastActivity = null;
    private boolean _active = false;
    private Date _expirationDate = null;
    private String _phone;
    private String _mobile;
    private String _pager;
    private String _im;
    private String _description;

    private GUID _entityId;
    private ActionURL _avatarUrl;
    private boolean _system = false;

    private ImpersonationContext _impersonationContext = NotImpersonatingContext.get();

    public static final User guest = new GuestUser("guest", "guest");

    // 'nobody' is a guest user who cannot be assigned permissions
    public static final User nobody = new LimitedUser(guest)
    {
        @Override
        public boolean isGuest()
        {
            return true;
        }
    };

    private static User adminServiceUser;

    /** Returns an App Admin user suitable for operational processes (bootstrapping servers, for example). */
    public static synchronized User getAdminServiceUser()
    {
        if (adminServiceUser == null)
        {
            adminServiceUser = new AdminServiceUser();
        }
        return adminServiceUser;
    }

    private static class AdminServiceUser extends LimitedUser
    {
        AdminServiceUser()
        {
            super(new User("@serviceUserAdmin", User.guest.getUserId()), SiteAdminRole.class);
            setActive(true);
            setPrincipalType(PrincipalType.SERVICE);
        }

        @Override
        @JsonIgnore
        public Stream<Role> getAssignedRoles(SecurableResource resource)
        {
            return super.getAssignedRoles(resource);
        }
    }


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

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
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
        if (c != null && currentUser != null && SecurityManager.canSeeUserDetails(c, currentUser))
        {
            String result = getEmail();
            if (StringUtils.isNotBlank(getDisplayName(currentUser)))
                result += " (" + getDisplayName(currentUser) + ")";

            return result;
        }
        else
            return getDisplayName(currentUser);
    }


    @Override
    public PrincipalArray getGroups()
    {
        if (_groups == null)
            _groups = _impersonationContext.getGroups(this);
        return _groups;
    }

    public void refreshGroups()
    {
        _groups = null;
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

    /**
     * Does the user have the permission of the Site Administrator at the root container? This is NOT a check for AdminPermission.
     * @return boolean
     */
    public boolean hasSiteAdminPermission()
    {
        return hasRootPermission(SiteAdminPermission.class);
    }

    /**
     * Does the user have the permission of the Application Administrator at the root container? This is NOT a check for AdminPermission.
     * @return boolean
     */
    public boolean hasApplicationAdminPermission()
    {
        return hasRootPermission(ApplicationAdminPermission.class);
    }

    private static final Set<Class<? extends Permission>> TRUSTED_ANALYST = Set.of(AnalystPermission.class, TrustedPermission.class);
    private static final Set<Class<? extends Permission>> TRUSTED_BROWSER_DEV = Set.of(BrowserDeveloperPermission.class, TrustedPermission.class);

    // NOTE all PlatformDeveloper are TrustedAnalyst and all TrustedAnalysts are TrustedBrowserDev
    // Usually you should only have one of these tests
    public boolean isPlatformDeveloper()
    {
        return hasRootPermission(PlatformDeveloperPermission.class);
    }

    public boolean isTrustedAnalyst()
    {
        return hasRootPermissions(TRUSTED_ANALYST);
    }

    public boolean isAnalyst()
    {
        return hasRootPermission(AnalystPermission.class);
    }

    public boolean isTrustedBrowserDev()
    {
        return hasRootPermissions(TRUSTED_BROWSER_DEV);
    }

    public boolean isBrowserDev()
    {
        return hasRootPermission(BrowserDeveloperPermission.class);
    }

    /**
     * Check if the user has AdminPermission at the root container.
     * @return boolean
     */
    public boolean hasRootAdminPermission()
    {
        return hasRootPermission(AdminPermission.class);
    }

    /**
     * Check if the user has an explicit permissions at the root container.
     * Example: user.hasRootPermission(AdminOperationsPermission.class)
     * @return boolean
     */
    public boolean hasRootPermission(Class<? extends Permission> perm)
    {
        return ContainerManager.getRoot().hasPermission(this, perm);
    }

    public boolean hasRootPermissions(Set<Class<? extends Permission>> perms)
    {
        return ContainerManager.getRoot().hasPermissions(this, perms);
    }

    @Override
    public boolean isInGroup(int group)
    {
        return getGroups().contains(group);
    }

    @Override
    public Stream<Role> getAssignedRoles(SecurableResource resource)
    {
        return _impersonationContext.getAssignedRoles(this, resource);
    }

    public JSONObject getUserProps()
    {
        return User.getUserProps(this);
    }

    public final Stream<Role> getSiteRoles()
    {
        return _impersonationContext.getSiteRoles(this);
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

    public Date getLastActivity()
    {
        return _lastActivity;
    }

    public void setLastActivity(Date lastActivity)
    {
        _lastActivity = lastActivity;
    }

    void setImpersonationContext(ImpersonationContext impersonationContext)
    {
        _impersonationContext = impersonationContext;
    }

    public @NotNull ImpersonationContext getImpersonationContext()
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

    @Override
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
            search = new LimitedUser(new GuestUser("@search"), ReaderRole.class);
            search.setPrincipalType(PrincipalType.SERVICE);
        }
        return search;
    }

    public boolean isSearchUser()
    {
        return this == getSearchUser();
    }

    public boolean isServiceUser()
    {
        return this.getPrincipalType() == PrincipalType.SERVICE;
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

    public GUID getGUID()
    {
        return _entityId;
    }

    public String getEntityId()
    {
        return getGUID().toString();
    }

    public void setGUID(GUID entityId)
    {
        _entityId = entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = new GUID(entityId);
    }

    public ActionURL getAvatarUrl()
    {
        if (_avatarUrl == null && getGUID() != null)
        {
            ThumbnailService.ImageType imageType = ThumbnailService.ImageType.Large;
            Attachment attachment = AttachmentService.get().getAttachment(new AvatarThumbnailProvider(this), imageType.getFilename());
            if (attachment != null)
            {
                _avatarUrl = PageFlowUtil.urlProvider(UserUrls.class).getUserAttachmentDownloadURL(this, imageType.getFilename());
            }
        }

        return _avatarUrl;
    }

    public String getAvatarThumbnailPath()
    {
        return getAvatarUrl() != null ? getAvatarUrl().toString() : AvatarThumbnailProvider.THUMBNAIL_PATH;
    }

    public boolean isSystem()
    {
        return _system;
    }

    public void setSystem(boolean system)
    {
        _system = system;
    }

    public static JSONObject getUserProps(User user)
    {
        return getUserProps(user, user, null, false);
    }

    public static JSONObject getUserProps(User user, @Nullable Container container)
    {
        return getUserProps(user, user, container, true);
    }

    public static JSONObject getUserProps(User user, User currentUser, @Nullable Container container, boolean includePermissionProps)
    {
        JSONObject props = new JSONObject();

        props.put("id", user.getUserId());
        // check for permission to see user details, users can always see their own details
        if (container == null || currentUser.equals(user) || SecurityManager.canSeeUserDetails(container, currentUser))
        {
            props.put("email", user.getEmail());
            props.put("phone", user.getPhone());
        }
        props.put("displayName", user.getDisplayName(currentUser));
        props.put("avatar", user.getAvatarThumbnailPath());

        if (includePermissionProps)
        {
            boolean nonNullContainer = null != container;
            props.put("canInsert", nonNullContainer && container.hasPermission(user, InsertPermission.class));
            props.put("canUpdate", nonNullContainer && container.hasPermission(user, UpdatePermission.class));
            props.put("canUpdateOwn", nonNullContainer && container.hasPermission(user, UpdatePermission.class));
            props.put("canDelete", nonNullContainer && container.hasPermission(user, DeletePermission.class));
            props.put("canDeleteOwn", nonNullContainer && container.hasPermission(user, DeletePermission.class));
            props.put("isAdmin", nonNullContainer && container.hasPermission(user, AdminPermission.class));
            props.put("isRootAdmin", user.hasRootAdminPermission());
            props.put("isSystemAdmin", user.hasSiteAdminPermission());
            props.put("canImpersonateSiteRoles", user.hasRootPermission(CanImpersonateSiteRolesPermission.class));
            props.put("isGuest", user.isGuest());
            props.put("isDeveloper", user.isBrowserDev());
            props.put("isAnalyst", user.hasRootPermission(AnalystPermission.class));
            props.put("isTrusted", user.hasRootPermission(TrustedPermission.class));
            props.put("isSignedIn", 0 != user.getUserId() || !user.isGuest());
            props.put("isSystem", user.isSystem());

            // PHI level
            // CONSIDER: Only include maxAllowedPhi if {@link ComplianceFolderSettings#isPhiRolesRequired()}
            if (nonNullContainer)
            {
                PHI maxAllowedPhi = ComplianceService.get().getMaxAllowedPhi(container, user);
                props.put("maxAllowedPhi", maxAllowedPhi);
            }
        }

        return props;
    }

    public Date getExpirationDate()
    {
        return _expirationDate;
    }

    public void setExpirationDate(Date expirationDate)
    {
        _expirationDate = expirationDate;
    }

    @Override
    public String toJSONString()
    {
        return String.valueOf(getUserId());
    }
}
