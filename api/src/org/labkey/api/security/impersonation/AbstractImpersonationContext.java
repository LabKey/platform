/*
 * Copyright (c) 2015-2019 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.PermissionsContext;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ImpersonatePrivilegedSiteRolesPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.stream.Stream;

///
/// The ability to impersonate users, groups, and roles is a powerful and useful security feature of LabKey.
/// Administrators use impersonation to review and troubleshoot their permissions configurations. Developers, testers,
/// and automated tests use impersonation widely to develop and validate LabKey features. This comment documents the
/// rules we've developed over the years to ensure impersonation is useful and secure.
///
/// ### Guiding Principles
/// Before delving into details, here are some guiding principles we've tried to follow while designing the
/// impersonation features:
/// - Those who can impersonate are already trusted, high-level administrators with wide-ranging permissions.
/// - We strive for flexibility in allowing impersonations. Any restrictions should be there for a good reason.
/// - Starting and stopping impersonation is recorded in the audit log. All key actions taken while impersonating are
///   recorded in the audit log with a clear indication of who was impersonating at the time.
/// - Escalation of permissions is prevented in the vast majority of most cases. We intentionally allow limited
///   exceptions to this and accept the audit log record of impersonators' actions as mitigation for this.
///
/// ### Three Impersonation Options
/// - Impersonating a user effectively turns the admin into that user. However, the impersonating admin is listed in
///   all audit log entries associated with their actions while impersonating. Admins can't impersonate inactive users
///   or themselves.
/// - Impersonating a group means the admin is still themselves (they'll see their own profile, updates will be tagged
///   with their user ID, etc.), but they are stripped of all role assignments except for the roles granted to three
///   groups: Guests, All Site Users, and the impersonated group. This provides the ability to test the effects of
///   a single group's role assignments in isolation. Admins can't impersonate the Guests group; they can just log out
///   to see the site from a guest's perspective.
/// - Impersonating roles means the admin is still themselves, but they are stripped of all role assignments except
///   the role(s) selected during impersonation. Unlike user and group impersonation (which allow a single principal
///   to be impersonated at a time), admins can impersonate multiple roles. They can also impersonate roles and
///   subsequently adjust that impersonation, adding and removing roles from the impersonation session. Note that many
///   roles don't include read permission; the impersonate roles dialog will encourage (though not require) the
///   addition of a read-granting role if the current selection lacks read permission.
///
///  ### Three Root Roles Can Impersonate
/// - Three roles that are assigned at the root that can impersonate: Site Administrator, Impersonating Troubleshooter,
///   and Application Admin.
/// - Site Administrators and Impersonating Troubleshooters are all-powerful and already have (or can instantly give
///   themselves) any permission in the system. Other than the exceptions mentioned above, they can impersonate any
///   user, group, or role in the system without restriction.
/// - Application Admins can impersonate the same users and groups as the other two root roles. They can impersonate
///   any role except for three especially powerful roles that are designated as "privileged": Site Administrator,
///   Platform Developer, and Impersonating Troubleshooter. Application Admins can't impersonate privileged roles
///   directly, and when they impersonate a user or group, any privileged roles granted to that principal are filtered
///   out during permission checks. As such, an Application Admin can't take on permissions that are exclusive to a
///   Site Administrator or a Platform Developer through impersonation.
/// - These three root roles can impersonate from the root, in which case they have the option to impersonate any
///   user, any site group, or any site role. They can also impersonate from a project or folder, in which case they
///   can impersonate any user, any site group, any project group from that project, or any role that's applicable to
///   the current folder. When they impersonate, they are free to navigate to any container allowed by their new
///   impersonated permissions.
/// - Troubleshooters and other root roles can't impersonate.
///
/// ### Project Administrators Can Also Impersonate
/// - Project Administrators can impersonate, but with less freedom than the root roles. Project Administrators are
///   granted most permissions in the system, but only in the context of their project, so their impersonation
///   power is limited accordingly.
/// - Unlike the other three impersonating roles, Project Administrators are completely restricted to the project
///   where impersonation starts. While impersonating, they can't navigate to any other project or the root, no
///   matter what their new impersonation permissions grant.
/// - Project Administrators can impersonate any user with read permissions in the project. This isn't a security
///   restriction, since Project Administrators can grant any user read permissions in their project and impersonate
///   them. This limit is a convenience to focus on just the users with access to that project. Some deployments are
///   partitioned with disjoint sets of users in each project; showing all users would be confusing and inconvenient.
///   Also, it's not very useful to impersonate a user who lacks read permissions.
/// - Project Administrators can impersonate any project group in that project or any site group where they are
///   already a member (though see the first "Considerations" point below).
/// - Project Administrators can impersonate any role that's applicable to the current folder. That means they can't
///   impersonate site roles because they can't impersonate from the root.
/// - Like Application Admins, Project Administrators can't impersonate privileged roles; these roles are filtered
///   out when impersonating a user or group that has them.
/// - Folder Administrators have wide-ranging permissions in their folder, but they can't impersonate.
///
/// ### Miscellaneous Details
/// - A handful of operations are prohibited while impersonating. These include creating API keys, applying an
///   electronic signature, and impersonating (no chaining of impersonations).
/// - The four admins who can impersonate have automatically been granted the vast majority of permissions in the
///   system, but there are some permissions that must be granted explicitly, even to high-level admins. Areas where
///   admins don't automatically receive permissions include PHI reading and adjudication. For these types of
///   permissions, an admin could escalate their permissions while impersonating, receiving permissions they lack
///   normally (although they could easily grant these permissions to themselves). Also, Project Administrators can
///   impersonate an Application Admin and gain at least one new permission within the project (update user profiles);
///   project sandboxing means they can't take any action as an Application Admin in the root or other projects. In
///   both escalation cases, the audit log tags all actions they take with the impersonating admin's user id.
/// - The UI and the API disallow impersonating site roles and project/folder roles at the same time (though see the
///   second "Considerations" point below).
///
/// ### Considerations
/// - Eliminate the requirement that Project Administrators must be a member of a site group in order to impersonate
///   it? Project Administrators can already impersonate any user (who could be a member of any group) and we already
///   filter out the privileged roles, so this restriction is hard to justify.
/// - Allow impersonation of site roles from the project and eliminate the prohibition on impersonating site roles
///   and project/folder roles at the same time? Admins can already impersonate users and groups that are granted a
///   mix of roles, and we already filter out the privileged roles, so this restriction seems arbitrary and hard to
///   justify. We would eliminate the isApplicable() check on roles, which would also make it easier to impersonate
///   roles with special requirements, such as EHR- or study-related roles. If we took this step, we could likely
///   combine the permissions-checking methods in RoleImpersonationContextFactory (getValidImpersonationRoles() and
///   verifyPermissions()).
///

public abstract class AbstractImpersonationContext implements PermissionsContext
{
    private final User _adminUser;
    private final @Nullable Container _project;
    @JsonIgnore // Can't be handled by remote pipelines
    private final ActionURL _returnUrl;
    private final ImpersonationContextFactory _factory;

    protected AbstractImpersonationContext(User adminUser, @Nullable Container project, ActionURL returnUrl, ImpersonationContextFactory factory)
    {
        _adminUser = adminUser;
        _project = project;
        _returnUrl = returnUrl;
        _factory = factory;
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
    public final ActionURL getReturnUrl()
    {
        return _returnUrl;
    }

    @Override
    public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
    {
        ActionURL url = PageFlowUtil.urlProvider(LoginUrls.class).getStopImpersonatingURL(c, user.getPermissionsContext().getReturnUrl());
        NavTree stop = new NavTree("Stop Impersonating", url).usePost();
        menu.addChild(stop);
    }

    @Override
    public ImpersonationContextFactory getFactory()
    {
        return _factory;
    }

    /**
     * @return A set of roles with the privileged roles filtered out if the impersonating admin user isn't allowed them
     */
    protected Stream<Role> getFilteredRoles(Stream<Role> roles)
    {
        if (getAdminUser() != null && !getAdminUser().hasRootPermission(ImpersonatePrivilegedSiteRolesPermission.class))
            return roles.filter(role -> !role.isPrivileged());

        return roles;
    }
}
