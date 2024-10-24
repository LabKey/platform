/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
package org.labkey.core.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.Test;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.action.FormApiAction;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.GroupAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.security.ACL;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.Group;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.InvalidGroupMembershipException;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AddUserPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.DeleteUserPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.SeeGroupDetailsPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.permissions.UpdateUserPermission;
import org.labkey.api.security.permissions.UserManagementPermission;
import org.labkey.api.security.roles.ApplicationAdminRole;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.study.Dataset;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

/*
* User: Dave
* Date: May 12, 2009
* Time: 1:18:27 PM
*/

/**
 * Set of API actions registered with the SecurityController
 */
public class SecurityApiActions
{
    public static class GetGroupPermsForm
    {
        private boolean _includeSubfolders = false;

        public boolean isIncludeSubfolders()
        {
            return _includeSubfolders;
        }

        public void setIncludeSubfolders(boolean includeSubfolders)
        {
            _includeSubfolders = includeSubfolders;
        }
    }

    @RequiresPermission(SeeGroupDetailsPermission.class)
    public static class GetGroupPermsAction extends ReadOnlyApiAction<GetGroupPermsForm>
    {
        @Override
        public ApiResponse execute(GetGroupPermsForm form, BindException errors)
        {
            Container container = getContainer();

            ApiSimpleResponse response = new ApiSimpleResponse();

            //if the container is not the root container, get the set of groups
            //from the container's project and pass that down the recursion stack
            response.put("container", getContainerPerms(container,
                    SecurityManager.getGroups(container.getProject(), true),
                    form.isIncludeSubfolders()));

            return response;
        }

        protected Map<String, Object> getContainerPerms(Container container, List<Group> groups, boolean recurse)
        {
            Map<String, Object> containerPerms = new HashMap<>();
            containerPerms.put("path", container.getPath());
            containerPerms.put("id", container.getId());
            containerPerms.put("name", container.getName());
            containerPerms.put("isInheritingPerms", container.isInheritedAcl());
            containerPerms.put("groups", getGroupPerms(container, groups));

            if (recurse && container.hasChildren())
            {
                List<Map<String, Object>> childPerms = new ArrayList<>();
                for (Container child : container.getChildren())
                {
                    if (child.hasPermission(getUser(), ReadPermission.class))
                    {
                        childPerms.add(getContainerPerms(child,
                                child.isProject() ? SecurityManager.getGroups(child, true) : groups,
                                recurse));
                    }
                }

                containerPerms.put("children", childPerms);
            }

            return containerPerms;
        }

        protected List<Map<String, Object>> getGroupPerms(Container container, List<Group> groups)
        {
            if (null == groups)
                return null;

            List<Map<String, Object>> groupsPerms = new ArrayList<>();

            for (Group group : groups)
            {
                Map<String, Object> groupPerms = new HashMap<>();
                groupPerms.put("id", group.getUserId());
                groupPerms.put("name", SecurityManager.getDisambiguatedGroupName(group));
                groupPerms.put("type", group.getPrincipalType().getTypeChar());
                groupPerms.put("isSystemGroup", group.isSystemGroup());
                groupPerms.put("isProjectGroup", group.isProjectGroup());

                if (isIncludeAcls())
                {
                    int perms = container.getPermsAsOldBitMask(group);
                    groupPerms.put("permissions", perms);

                    SecurityManager.PermissionSet role = SecurityManager.PermissionSet.findPermissionSet(perms);
                    if (null != role)
                    {
                        groupPerms.put("role", role.toString());
                        groupPerms.put("roleLabel", role.getLabel());
                    }
                }

                //add effective roles
                List<String> effectiveRoleList = SecurityManager.getEffectiveRoles(container, group).map(Role::getUniqueName).toList();
                groupPerms.put("roles", effectiveRoleList);
                groupPerms.put("effectivePermissions", SecurityManager.getPermissionNames(container, group));

                if (container.hasPermission(getUser(), AdminPermission.class))
                {
                    List<Map<String, Object>> parentGroupInfos = new ArrayList<>();
                    group.getGroups().stream().forEach(parentGroupId -> {
                        Group parentGroup = SecurityManager.getGroup(parentGroupId);
                        if (parentGroup != null && parentGroup.getUserId() != group.getUserId())
                        {
                            parentGroupInfos.add(getGroupMap(parentGroup));
                        }
                    });
                    groupPerms.put("groups", parentGroupInfos);
                }

                groupsPerms.add(groupPerms);
            }

            return groupsPerms;
        }
    }

    private static boolean isIncludeAcls()
    {
        return OptionalFeatureService.get().isFeatureEnabled(ACL.RESTORE_USE_OF_ACLS);
    }

    private static Map<String, Object> getGroupMap(Group group)
    {
        Map<String, Object> groupInfo = new HashMap<>();
        groupInfo.put("id", group.getUserId());
        groupInfo.put("name", SecurityManager.getDisambiguatedGroupName(group));
        groupInfo.put("isProjectGroup", group.isProjectGroup());
        groupInfo.put("isSystemGroup", group.isSystemGroup());

        return groupInfo;
    }

    public static class GetUserPermsForm
    {
        private Integer _userId;
        private String _userEmail;
        private boolean _includeSubfolders = false;

        public Integer getUserId()
        {
            return _userId;
        }

        @SuppressWarnings("unused")
        public void setUserId(Integer userId)
        {
            _userId = userId;
        }

        public String getUserEmail()
        {
            return _userEmail;
        }

        @SuppressWarnings("unused")
        public void setUserEmail(String userEmail)
        {
            _userEmail = userEmail;
        }

        public boolean isIncludeSubfolders()
        {
            return _includeSubfolders;
        }

        @SuppressWarnings("unused")
        public void setIncludeSubfolders(boolean includeSubfolders)
        {
            _includeSubfolders = includeSubfolders;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetUserPermsAction extends ReadOnlyApiAction<GetUserPermsForm>
    {
        @Override
        public ApiResponse execute(GetUserPermsForm form, BindException errors) throws Exception
        {
            User currentUser = getUser();
            Container container = getContainer();

            //if user id and user email is null, assume current user
            User user;

            if (null != form.getUserId())
                user = UserManager.getUser(form.getUserId().intValue());
            else if (null != form.getUserEmail())
                user = UserManager.getUser(new ValidEmail(form.getUserEmail()));
            else
                user = currentUser;

            if (null == user)
                throw new IllegalArgumentException("No user found that matches specified userId or email address.");

            //if user is not current user, current user must have admin perms in container
            if (!user.equals(currentUser) && !container.hasPermission(currentUser, AdminPermission.class))
                throw new UnauthorizedException("You may not look at the permissions of users other than the current user.");

            ApiSimpleResponse response = new ApiSimpleResponse();

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", user.getUserId());
            userInfo.put("displayName", user.getDisplayName(currentUser));
            response.put("user", userInfo);

            response.put("container", getContainerPerms(getContainer(), user, form.isIncludeSubfolders()));
            return response;
        }

        protected Map<String, Object> getContainerPerms(Container container, User user, boolean recurse)
        {
            Map<String, Object> permsInfo = new HashMap<>();

            //add container info
            permsInfo.put("id", container.getId());
            permsInfo.put("name", container.getName());
            permsInfo.put("path", container.getPath());

            if (isIncludeAcls())
            {
                //add user's effective permissions
                int perms = container.getPermsAsOldBitMask(user);
                permsInfo.put("permissions", perms);

                //see if those match a given role name
                SecurityManager.PermissionSet role = SecurityManager.PermissionSet.findPermissionSet(perms);
                if (null != role)
                {
                    permsInfo.put("role", role.toString());
                    permsInfo.put("roleLabel", role.getLabel());
                }
                else
                {
                    permsInfo.put("role", "Mixed");
                    permsInfo.put("roleLabel", "(Mixed)");
                }
            }

            //effective roles
            List<String> effectiveRoles = SecurityManager.getEffectiveRoles(container, user).map(Role::getUniqueName).toList();
            permsInfo.put("roles", effectiveRoles);
            permsInfo.put("effectivePermissions", SecurityManager.getPermissionNames(container, user));

            //add all groups the user belongs to in this container
            List<Group> groups = SecurityManager.getGroups(container, user);
            List<Map<String, Object>> groupsInfo = new ArrayList<>();

            for (Group group : groups)
            {
                Map<String, Object> groupInfo = new HashMap<>();
                groupInfo.put("id", group.getUserId());
                groupInfo.put("name", SecurityManager.getDisambiguatedGroupName(group));

                if (isIncludeAcls())
                {
                    int groupPerms = container.getPermsAsOldBitMask(group);
                    groupInfo.put("permissions", groupPerms);

                    SecurityManager.PermissionSet groupRole = SecurityManager.PermissionSet.findPermissionSet(groupPerms);
                    if (null != groupRole)
                    {
                        groupInfo.put("role", groupRole.toString());
                        groupInfo.put("roleLabel", groupRole.getLabel());
                    }
                }

                //effective roles
                List<String> groupEffectiveRoles = SecurityManager.getEffectiveRoles(container, group).map(Role::getUniqueName).toList();
                groupInfo.put("roles", groupEffectiveRoles);
                groupInfo.put("effectivePermissions", SecurityManager.getPermissionNames(container, group));

                groupsInfo.add(groupInfo);
            }
            permsInfo.put("groups", groupsInfo);

            //recurse children if desired
            if (recurse && container.hasChildren())
            {
                List<Map<String, Object>> childPerms = new ArrayList<>();
                for (Container child : container.getChildren())
                {
                    if (child.hasPermission(getUser(), ReadPermission.class))
                    {
                        childPerms.add(getContainerPerms(child, user, recurse));
                    }
                }

                permsInfo.put("children", childPerms);
            }

            return permsInfo;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetGroupsForCurrentUserAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            List<Map<String, Object>> groupInfos = new ArrayList<>();
            //include both project and global groups
            List<Group> groups = SecurityManager.getGroups(getContainer(), getUser());
            for (Group group : groups)
            {
                groupInfos.add(getGroupMap(group));
            }

            return new ApiSimpleResponse("groups", groupInfos);
        }
    }

    @RequiresLogin
    @IgnoresTermsOfUse
    public static class EnsureLoginAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            User user = getUser();
            Container container = getContainer();

            //return similar info as is already exposed on LABKEY.user & LABKEY.Security.currentUser
            //so we can swap it out
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getUserId());
            userInfo.put("displayName", user.getDisplayName(user));
            userInfo.put("email", user.getEmail());
            userInfo.put("canInsert", container.hasPermission(user, InsertPermission.class) ? "true" : "false");
            userInfo.put("canUpdate", container.hasPermission(user, UpdatePermission.class) ? "true" : "false");
            userInfo.put("canUpdateOwn", container.hasPermission(user, UpdatePermission.class) ? "true" : "false");
            userInfo.put("canDelete", container.hasPermission(user, DeletePermission.class) ? "true" : "false");
            userInfo.put("canDeleteOwn", container.hasPermission(user, DeletePermission.class) ? "true" : "false");
            userInfo.put("isAdmin", container.hasPermission(user, AdminPermission.class) ? "true" : "false");

            return new ApiSimpleResponse("currentUser", userInfo);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetRolesAction extends ReadOnlyApiAction<Object>
    {
        private final Set<Permission> _allPermissions = new HashSet<>();

        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            ArrayList<Map<String, Object>> rolesProps = new ArrayList<>();

            for (Role role : RoleManager.getAllRoles())
            {
                if (role.isAssignable())
                    rolesProps.add(getRoleProps(role));
            }

            List<Map<String, Object>> permsProps = new ArrayList<>();
            for (Permission perm : _allPermissions)
            {
                permsProps.add(getPermissionProps(perm));
            }

            ApiSimpleResponse resp = new ApiSimpleResponse("roles", rolesProps);
            resp.put("permissions", permsProps);
            return resp;
        }

        public Map<String, Object> getRoleProps(Role role)
        {
            Map<String, Object> props = new HashMap<>();
            props.put("uniqueName", role.getUniqueName());
            props.put("name", role.getName());
            props.put("displayName", role.getDisplayName());
            props.put("description", role.getDescription());
            if (null != role.getSourceModule())
                props.put("sourceModule", role.getSourceModule().getName());

            List<String> permissions = new ArrayList<>();
            for (Class<? extends Permission> permClass : role.getPermissions())
            {
                Permission perm = RoleManager.getPermission(permClass);
                _allPermissions.add(perm);
                permissions.add(perm.getUniqueName());
            }

            props.put("permissions", permissions);

            List<Integer> excludedPrincipals = new ArrayList<>();
            for (UserPrincipal principal : role.getExcludedPrincipals())
            {
                excludedPrincipals.add(principal.getUserId());
            }
            props.put("excludedPrincipals", excludedPrincipals);
            props.put("privileged", role.isPrivileged());

            return props;
        }

        public Map<String, Object> getPermissionProps(Permission perm)
        {
            Map<String, Object> props = new HashMap<>();
            props.put("uniqueName", perm.getUniqueName());
            props.put("name", perm.getName());
            props.put("description", perm.getDescription());
            if (null != perm.getSourceModule())
                props.put("sourceModule", perm.getSourceModule().getName());
            return props;
        }
    }

    public static class GetSecurableResourcesForm
    {
        private boolean _includeSubfolders = false;
        private boolean _includeEffectivePermissions = false;

        public boolean isIncludeSubfolders()
        {
            return _includeSubfolders;
        }

        public void setIncludeSubfolders(boolean includeSubfolders)
        {
            _includeSubfolders = includeSubfolders;
        }

        public boolean isIncludeEffectivePermissions()
        {
            return _includeEffectivePermissions;
        }

        public void setIncludeEffectivePermissions(boolean includeEffectivePermissions)
        {
            _includeEffectivePermissions = includeEffectivePermissions;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetSecurableResourcesAction extends ReadOnlyApiAction<GetSecurableResourcesForm>
    {
        private boolean _includeSubfolders = false;
        private boolean _includePermissions = false;

        @Override
        public ApiResponse execute(GetSecurableResourcesForm form, BindException errors)
        {
            _includeSubfolders = form.isIncludeSubfolders();
            _includePermissions = form.isIncludeEffectivePermissions();
            Container container = getContainer();
            return new ApiSimpleResponse("resources", getResourceProps(container));
        }

        protected Map<String, Object> getResourceProps(SecurableResource resource)
        {
            Map<String, Object> props = new HashMap<>();
            props.put("resourceClass", resource.getClass().getName());
            props.put("id", resource.getResourceId());
            props.put("name", resource.getResourceName());
            props.put("description", resource.getResourceDescription());
            if (null != resource.getSourceModule())
                props.put("sourceModule", resource.getSourceModule().getName());
            props.put("children", getChildrenProps(resource));

            SecurableResource parent = resource.getParentResource();
            if (null != parent)
            {
                props.put("parentId", parent.getResourceId());
                props.put("parentContainerPath", parent.getResourceContainer().getPath());
            }
            if (_includePermissions)
            {
                User user = getUser();
                List<String> permNames = new ArrayList<>();

                //horrible, nasty, icky, awful HACK! See bug 8183.
                //Study datasets use special logic for determining read/write so we need to ask it directly.
                //this needs to be fixed in a future release so that we can treat all securable resources the same.
                Collection<Class<? extends Permission>> permissions;
                if (resource instanceof Dataset ds)
                {
                    permissions = ds.getPermissions(user);
                }
                else
                {
                    permissions = SecurityManager.getPermissions(resource, user, Set.of());
                }

                for (Class<? extends Permission> permission : permissions)
                {
                    permNames.add(RoleManager.getPermission(permission).getUniqueName());
                }

                props.put("effectivePermissions", permNames);
            }

            return props;
        }

        protected List<Map<String, Object>> getChildrenProps(SecurableResource resource)
        {
            List<Map<String, Object>> childProps = new ArrayList<>();
            for (SecurableResource child : resource.getChildResources(getUser()))
            {
                if (_includeSubfolders || !(child instanceof Container))
                    childProps.add(getResourceProps(child));
            }
            return childProps;
        }
    }

    public static class PolicyIdForm
    {
        private String _resourceId;

        public String getResourceId()
        {
            return _resourceId;
        }

        public void setResourceId(String resourceId)
        {
            _resourceId = resourceId;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class GetPolicyAction extends ReadOnlyApiAction<PolicyIdForm>
    {
        @Override
        public ApiResponse execute(PolicyIdForm form, BindException errors)
        {
            if (null == form.getResourceId())
                throw new IllegalArgumentException("You must supply a resourceId parameter!");

            Container container = getContainer();
            User user = getUser();

            //resolve the resource
            SecurableResource resource = container.findSecurableResource(form.getResourceId(), user);
            if (null == resource)
                throw new IllegalArgumentException("The requested resource does not exist within this container!");

            //get the policy
            SecurityPolicy policy = SecurityPolicyManager.getPolicy(resource);
            ApiSimpleResponse resp = new ApiSimpleResponse();

            //FIX: 8077 - if this is a subfolder and the policy is inherited from the project
            // assign all principals in the project admin role to the folder admin role
            if (!container.isRoot() && policy.getResourceId().equals(container.getProject().getResourceId()))
            {
                Role projAdminRole = RoleManager.getRole(ProjectAdminRole.class);
                Role fldrAdminRole = RoleManager.getRole(FolderAdminRole.class);

                MutableSecurityPolicy mpolicy = new MutableSecurityPolicy(policy);
                for (RoleAssignment ra : policy.getAssignments())
                {
                    if (ra.getRole().equals(projAdminRole))
                    {
                        UserPrincipal principal = SecurityManager.getPrincipal(ra.getUserId());
                        if (null != principal)
                            mpolicy.addRoleAssignment(principal, fldrAdminRole);
                    }
                }
                resp.put("policy", mpolicy.toJson());
            }
            else
                resp.put("policy", policy.toJson());

            List<String> relevantRoles = new ArrayList<>();

            if (container.isRoot() && !resource.equals(container))
            {
                // ExternalIndex case
                relevantRoles.add(RoleManager.getRole(ReaderRole.class).getUniqueName());
            }
            else
            {
                for (Role role : RoleManager.getAllRoles())
                {
                    if (role.isAssignable() && role.isApplicable(policy, resource))
                        relevantRoles.add(role.getUniqueName());
                }
            }

            resp.put("relevantRoles", relevantRoles);

            return resp;
        }
    }

    public static class SavePolicyForm extends SimpleApiJsonForm
    {
        public boolean isConfirm()
        {
            return getJsonObject().optBoolean("confirm");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class SavePolicyAction extends MutatingApiAction<SavePolicyForm>
    {
        @Override
        public ApiResponse execute(SavePolicyForm form, BindException errors)
        {
            Container container = getContainer();
            User user = getUser();
            JSONObject json = form.getJsonObject();

            //resolve the resource
            String resourceId = json.optString("resourceId", null);
            if (null == resourceId || resourceId.isEmpty())
                throw new IllegalArgumentException("You must include a resourceId as a top-level property!");

            SecurableResource resource = container.findSecurableResource(resourceId, user);
            if (null == resource)
                throw new IllegalArgumentException("No resource with the id '" + resourceId + "' was found in this container!");

            // Ensure that user has admin permission on resource
            if (!resource.hasPermission(user, AdminPermission.class))
                throw new IllegalArgumentException("You do not have permission to modify the security policy for this resource!");

            MutableSecurityPolicy policy = null;

            try
            {
                //create the policy from the props (will throw if invalid)
                policy = MutableSecurityPolicy.fromJson(json, resource);
            }
            catch (Exception e)
            {
                errors.reject(null, e.getMessage());
            }

            if (policy == null)
                throw new IllegalArgumentException("Unable to load policy from map.");

            //if root container permissions update, check for app admin removal
            if (container.isRoot() && resourceId.equals(container.getId()) && user.hasApplicationAdminPermission() && !user.hasSiteAdminPermission()
                    && !policy.hasRole(user, ApplicationAdminRole.class) && !form.isConfirm())
            {
                Map<String, Object> props = new HashMap<>();
                props.put("success", false);
                props.put("needsConfirmation", true);
                props.put("message", "If you remove your own user account from the Application Admin role, "
                    + "you will no longer have administrative privileges. Are you sure that you want to continue?");
                return new ApiSimpleResponse(props);
            }

            boolean hasChanges = false;

            if (!errors.hasErrors())
            {
                try
                {
                    // Save and audit
                    hasChanges = SecurityPolicyManager.savePolicy(policy, getUser());
                }
                catch (OptimisticConflictException e)
                {
                    errors.reject(null, e.getMessage());
                }
            }

            Map<String, Object> props = new HashMap<>();
            props.put("success", !errors.hasErrors());
            props.put("hasChanges", hasChanges);
            return new ApiSimpleResponse(props);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class DeletePolicyAction extends MutatingApiAction<PolicyIdForm>
    {
        @Override
        public ApiResponse execute(PolicyIdForm form, BindException errors)
        {
            Container container = getContainer();
            User user = getUser();

            //resolve the resource
            String resourceId = form.getResourceId();
            if (null == resourceId || resourceId.isEmpty())
                throw new IllegalArgumentException("You must include a resourceId as a top-level property!");

            SecurableResource resource = container.findSecurableResource(resourceId, user);
            if (null == resource)
                throw new IllegalArgumentException("No resource with the id '" + resourceId + "' was found in this container!");

            //ensure that user has admin permission on resource
            if (!resource.hasPermission(user, AdminPermission.class))
                throw new IllegalArgumentException("You do not have permission to delete the security policy for this resource!");

            SecurityPolicyManager.deletePolicy(resource);

            //audit log
            writeToAuditLog(resource);

            return new ApiSimpleResponse("success", true);
        }

        protected void writeToAuditLog(SecurableResource resource)
        {
            String parentResource = resource.getParentResource() != null ? resource.getParentResource().getResourceName() : "root";
            GroupAuditProvider.GroupAuditEvent event = new GroupAuditProvider.GroupAuditEvent(resource.getResourceContainer().getId(),
                    "The security policy for " + resource.getResourceName()
                            + " was deleted. It will now inherit the security policy of " +
                            parentResource);

            event.setResourceEntityId(resource.getResourceId());
            if (null != resource.getResourceContainer().getProject())
                event.setProjectId(resource.getResourceContainer().getProject().getId());

            AuditLogService.get().addEvent(getUser(), event);
        }
    }

    private static abstract class BaseUpdateAssignmentAction extends MutatingApiAction<RoleAssignmentForm>
    {
        @Override
        public void validateForm(RoleAssignmentForm form, Errors errors)
        {
            if (form == null)
            {
                errors.reject("invalidFormat", "Invalid format for request.  Please check your JSON syntax.");
            }
            else
            {
                validateAssignee(form, errors);
                validateRoleClass(form, errors);
            }
        }

        protected void validateAssignee(RoleAssignmentForm form, Errors errors)
        {
            if (null != form.getPrincipalId())
            {
                if (SecurityManager.getPrincipal(form.getPrincipalId()) == null)
                {
                    errors.reject("principalId", "No such user or group: " + form.getPrincipalId());
                }
            }
            else if (null != form.getEmail() && !form.getEmail().isEmpty())
            {
                try
                {
                    ValidEmail validEmail = new ValidEmail(form.getEmail());
                    if (UserManager.getUser(validEmail) == null)
                        errors.reject("email", "No such user: " + form.getEmail());
                }
                catch (InvalidEmailException e)
                {
                    errors.reject("email", "Invalid email: " + form.getEmail());
                }
            }
            else
                errors.reject("principal", "Must specify an email or user/group ID");
        }

        protected void validateRoleClass(RoleAssignmentForm form, Errors errors)
        {
            if (null == form.getRoleClassName())
                errors.reject("roleClassName", "Must specify a role to assign");
            else if (RoleManager.getRole(form.getRoleClassName()) == null)
                errors.reject("roleClassName", "No such role: " + form.getRoleClassName());
        }

        @Override
        public ApiResponse execute(RoleAssignmentForm form, BindException errors) throws Exception
        {
            Container container = getContainer();
            MutableSecurityPolicy policy = new MutableSecurityPolicy(SecurityPolicyManager.getPolicy(container));
            UserPrincipal principal = getUser(form);
            Role role = RoleManager.getRole(form.getRoleClassName());
            updateRoleAssignment(policy, principal, role);

            SavePolicyForm policyForm = new SavePolicyForm();
            JSONObject policyJson = policy.toJson();
            if (form.getConfirm() != null)
                policyJson.put("confirm", form.getConfirm());
            policyForm.bindJson(policyJson);
            SavePolicyAction savePolicyAction = new SavePolicyAction();
            savePolicyAction.setViewContext(getViewContext());

            return savePolicyAction.execute(policyForm, errors);
        }

        private UserPrincipal getUser(RoleAssignmentForm form) throws InvalidEmailException
        {
            if (null != form.getPrincipalId())
                return SecurityManager.getPrincipal(form.getPrincipalId());
            else
                return UserManager.getUser(new ValidEmail(form.getEmail()));
        }

        protected abstract void updateRoleAssignment(MutableSecurityPolicy policy, UserPrincipal principal, Role role);
    }

    @RequiresPermission(AdminPermission.class)
    @ApiVersion(16.1)
    public static class AddAssignmentAction extends BaseUpdateAssignmentAction
    {
        @Override
        public ApiResponse execute(RoleAssignmentForm form, BindException errors) throws Exception
        {
            return super.execute(form, errors);
        }

        @Override
        protected void updateRoleAssignment(MutableSecurityPolicy policy, UserPrincipal principal, Role role)
        {
            policy.addRoleAssignment(principal, role);
        }
    }

    @RequiresPermission(AdminPermission.class)
    @ApiVersion(16.1)
    public static class RemoveAssignmentAction extends BaseUpdateAssignmentAction
    {
        @Override
        public ApiResponse execute(RoleAssignmentForm form, BindException errors) throws Exception
        {
            return super.execute(form, errors);
        }

        @Override
        protected void updateRoleAssignment(MutableSecurityPolicy policy, UserPrincipal principal, Role role)
        {
            policy.removeRoleAssignment(principal, role);
        }
    }

    @RequiresPermission(AdminPermission.class)
    @ApiVersion(16.1)
    public static class ClearAssignedRolesAction extends BaseUpdateAssignmentAction
    {
        @Override
        public void validateForm(RoleAssignmentForm form, Errors errors)
        {
            form.setRoleClassName("org.labkey.api.security.roles.NoPermissionsRole");
            super.validateForm(form, errors);
        }

        @Override
        public ApiResponse execute(RoleAssignmentForm form, BindException errors) throws Exception
        {
            return super.execute(form, errors);
        }

        @Override
        protected void updateRoleAssignment(MutableSecurityPolicy policy, UserPrincipal principal, Role role)
        {
            policy.clearAssignedRoles(principal);
        }
    }

    public static class RoleAssignmentForm
    {
        private Integer principalId;
        private String email;
        private String roleClassName;
        private Boolean confirm;

        public Integer getPrincipalId()
        {
            return principalId;
        }

        @SuppressWarnings("unused")
        public void setPrincipalId(Integer principalId)
        {
            this.principalId = principalId;
        }

        public String getEmail()
        {
            return email;
        }

        @SuppressWarnings("unused")
        public void setEmail(String email)
        {
            this.email = email;
        }

        public String getRoleClassName()
        {
            return roleClassName;
        }

        @SuppressWarnings("unused")
        public void setRoleClassName(String roleClassName)
        {
            this.roleClassName = roleClassName;
        }

        public Boolean getConfirm()
        {
            return confirm;
        }

        @SuppressWarnings("unused")
        public void setConfirm(Boolean confirm)
        {
            this.confirm = confirm;
        }
    }

    public static class NameForm
    {
        private String _name;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }
    }

    public static class IdForm
    {
        private int _id = -1;

        public int getId()
        {
            return _id;
        }

        public void setId(int id)
        {
            _id = id;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class CreateGroupAction extends MutatingApiAction<NameForm>
    {
        @Override
        public ApiResponse execute(NameForm form, BindException errors)
        {
            Container container = getContainer();
            if (!container.isRoot() && !container.isProject())
                throw new IllegalArgumentException("You may not create groups at the folder level. Call this API at the project or root level.");

            String name = StringUtils.trimToNull(form.getName());
            if (null == name)
                throw new IllegalArgumentException("You must specify a name parameter!");

            Group newGroup = SecurityManager.createGroup(getContainer().getProject(), name);
            writeToAuditLog(newGroup);

            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("id", newGroup.getUserId());
            resp.put("name", newGroup.getName());
            return resp;
        }

        protected void writeToAuditLog(Group newGroup)
        {
            GroupAuditProvider.GroupAuditEvent event = new GroupAuditProvider.GroupAuditEvent(getContainer().getId(), "A new security group named " + newGroup.getName() + " was created.");
            event.setGroup(newGroup.getUserId());

            AuditLogService.get().addEvent(getUser(), event);
        }
    }

    @RequiresPermission(AdminPermission.class)
    @ApiVersion(16.1)
    @Marshal(Marshaller.Jackson)
    public static class BulkUpdateGroupAction extends MutatingApiAction<GroupForm>
    {
        private Group _group;

        @Override
        public void validateForm(GroupForm form, Errors errors)
        {
            if (form == null)
            {
                errors.reject("invalidFormat", "Invalid format for request.  Please check your JSON syntax.");
            }
            else
            {
                if (form.getGroupId() == null && form.getGroupName() == null)
                {
                    errors.reject("requiredError", "Group not specified");
                }
                else if (form.getGroupId() != null)
                {
                    _group = SecurityManager.getGroup(form.getGroupId());
                    if (_group == null)
                        errors.rejectValue("groupId", "invalidError", "Invalid group id " + form.getGroupId());
                }
                else // group name is not null
                {
                    String validationMsg = UserManager.validGroupName(form.getGroupName(), PrincipalType.GROUP);
                    if (validationMsg != null)
                    {
                        errors.rejectValue("groupName", "invalidError", validationMsg);
                    }
                    else
                    {
                        try
                        {
                            Integer groupId = SecurityManager.getGroupId(getContainer(), StringUtils.trimToNull(form.getGroupName()));
                            _group = SecurityManager.getGroup(groupId);
                        }
                        catch (NotFoundException e)
                        {
                            if (!form.getCreateGroup())
                            {
                                errors.rejectValue("groupName", "invalidError", "Group '" + form.getGroupName() + "' does not exist.  Specify 'createGroup': 'true' to have it created.");
                            }
                        }
                    }
                }

                if (form.getMembers() != null && !form.getMembers().isEmpty() && form.getMethod() == null)
                {
                    errors.rejectValue("method", "requiredError", "method not specified.");
                }
            }

        }

        @Override
        public ApiResponse execute(GroupForm form, BindException errors)
        {
            Container container = getContainer();
            if (!container.isRoot() && !container.isProject())
                throw new IllegalArgumentException("You may not create groups at the folder level. Call this API at the project or root level.");

            if (_group == null && getContainer().isRoot() && !getUser().hasRootPermission(UpdateUserPermission.class) )
            {
                throw new UnauthorizedException("You do not have permission to create site-wide groups.");
            }

            if (_group == null && form.getCreateGroup())
            {
                _group = SecurityManager.createGroup(getContainer().getProject(), form.getGroupName());
                writeToAuditLog(_group, this.getViewContext());
            }

            if (_group.getContainer() == null && !getUser().hasRootPermission(UpdateUserPermission.class))
            {
                throw new UnauthorizedException("You do not have permission to modify site-wide groups.");
            }

            SecurityController.verifyUserCanModifyGroup(_group, getUser());

            Map<String, String> memberErrors = new HashMap<>();
            List<User> newUsers = new ArrayList<>();
            Map<String, List<UserPrincipal>> members = new HashMap<>();
            if (form.getMembers() != null)
            {
                switch (form.getMethod())
                {
                    case add:
                    case replace:
                        addOrReplaceMembers(form, members, memberErrors, newUsers);
                        break;
                    case delete:
                        removeMembers(form, members, memberErrors);
                        break;
                }
            }
            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("id", _group.getUserId());
            resp.put("name", _group.getName());
            if (!newUsers.isEmpty())
            {
                List<Map<String, Object>> userList = new ArrayList<>();
                for (User user : newUsers)
                {
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("email", user.getEmail());
                    userData.put("userId", user.getUserId());
                    userList.add(userData);
                }
                resp.put("newUsers", userList);
            }
            if (!members.isEmpty())
            {
                Map<String, List<Map<String, Object>>> memberUpdates = new HashMap<>();
                for (String memberGroup : members.keySet())
                {
                    List<Map<String, Object>> memberData = new ArrayList<>();
                    for (UserPrincipal member : members.get(memberGroup))
                    {
                        Map<String, Object> userData = new HashMap<>();
                        if (member.getPrincipalType() == PrincipalType.USER)
                        {
                            userData.put("email", ((User) member).getEmail());
                            userData.put("userId", member.getUserId());
                        }
                        else
                        {
                            userData.put("name", member.getName());
                            userData.put("id", member.getUserId());
                        }

                        memberData.add(userData);
                    }
                    if (!memberData.isEmpty())
                        memberUpdates.put(memberGroup, memberData);
                }
                if (!memberUpdates.isEmpty())
                    resp.put("members", memberUpdates);
            }
            if (!memberErrors.isEmpty())
            {
                resp.put("errors", memberErrors);
            }
            return resp;
        }


        protected void writeToAuditLog(Group newGroup, ViewContext viewContext)
        {
            GroupAuditProvider.GroupAuditEvent event = new GroupAuditProvider.GroupAuditEvent(getContainer().getId(), "A new security group named " + newGroup.getName() + " was created.");
            event.setGroup(newGroup.getUserId());
            AuditLogService.get().addEvent(viewContext.getUser(), event);
        }

        private void removeMembers(GroupForm form, Map<String, List<UserPrincipal>> members, Map<String, String> memberErrors)
        {
            members.put("removed", new ArrayList<>());
            for (GroupMember member : form.getMembers())
            {
                try
                {
                    UserPrincipal principal = member.getUserPrincipal();

                    if (principal == null)
                    {
                        memberErrors.put(member.toString(), "Identifier for user or group not provided.");
                    }
                    else if (principal.isInGroup(_group.getUserId()))
                    {
                        SecurityManager.deleteMember(_group, principal);
                        members.get("removed").add(principal);
                    }
                }
                catch (Exception e)
                {
                    memberErrors.put(member.toString(), e.getMessage());
                }
            }
        }

        private void addOrReplaceMembers(GroupForm form, Map<String, List<UserPrincipal>> members, Map<String, String> memberErrors, List<User> newUsers)
        {
            List<UserPrincipal> originalMembers = new ArrayList<>(SecurityManager.getGroupMembers(_group, MemberType.ALL_GROUPS_AND_USERS));
            boolean doReplacement = form.getMethod() == MemberEditOperation.replace;
            if (doReplacement)
            {
                SecurityManager.deleteMembers(_group, originalMembers);
            }

            members.put("added", new ArrayList<>());
            for (GroupMember member : form.getMembers())
            {
                try
                {
                    UserPrincipal principal = member.getUserPrincipal();

                    if (principal == null && member.getEmail() != null) // create the user
                    {
                        try
                        {
                            SecurityManager.NewUserStatus status = SecurityManager.addUser(new ValidEmail(member.getEmail()), getUser());

                            User newUser = status.getUser();
                            newUser.setFirstName(member.getFirstName());
                            newUser.setLastName(member.getLastName());
                            if (member.getDisplayName() != null)
                                newUser.setDisplayName(member.getDisplayName());
                            newUser.setPhone(member.getPhone());
                            newUser.setMobile(member.getMobile());
                            newUser.setPager(member.getPager());
                            newUser.setIM(member.getIm());
                            newUser.setDescription(member.getDescription());

                            newUsers.add(status.getUser());
                            UserManager.updateUser(status.getUser(), newUser);
                            principal = newUser;
                        }
                        catch (SecurityManager.UserManagementException | InvalidEmailException  e)
                        {

                            memberErrors.put(member.getEmail(), e.getMessage());
                        }
                    }
                    if (principal != null)
                    {
                        try
                        {
                            SecurityManager.addMember(_group, principal);
                            if (!originalMembers.contains(principal))
                                members.get("added").add(principal);
                            else if (doReplacement)
                            {
                                originalMembers.remove(principal);  // originalMembers should end up with the members that were removed and not replaced
                            }
                        }
                        catch (InvalidGroupMembershipException e)
                        {
                            memberErrors.put(principal.getName(), e.getMessage());
                        }
                    }
                }
                catch (Exception e)
                {
                    memberErrors.put(member.toString(), e.getMessage());
                }
            }
            if (doReplacement && !originalMembers.isEmpty())
            {
                members.put("removed", originalMembers);
            }
        }
    }


    public enum MemberEditOperation {
        add,        // add the given members; do not fail if any already exist
        replace,    // replace the current entities with the new list (same as delete all then add)
        delete,     // delete the given members; does not fail if member does not exist in group; does not delete group if it becomes empty
    }

    public static class GroupMember
    {
        private String _email;
        private String _lastName;
        private String _firstName;
        private String _displayName;
        private String _phone;
        private String _mobile;
        private String _pager;
        private String _im;
        private String _description;
        private Integer _userId;
        private Integer _groupId;

        public String getDescription()
        {
            return _description;
        }

        @SuppressWarnings("unused")
        public void setDescription(String description)
        {
            _description = description;
        }

        public String getDisplayName()
        {
            return _displayName;
        }

        @SuppressWarnings("unused")
        public void setDisplayName(String displayName)
        {
            _displayName = displayName;
        }

        public String getEmail()
        {
            return _email;
        }

        @SuppressWarnings("unused")
        public void setEmail(String email)
        {
            _email = email;
        }

        public String getFirstName()
        {
            return _firstName;
        }

        @SuppressWarnings("unused")
        public void setFirstName(String firstName)
        {
            _firstName = firstName;
        }

        public Integer getGroupId()
        {
            return _groupId;
        }

        @SuppressWarnings("unused")
        public void setGroupId(Integer groupId)
        {
            _groupId = groupId;
        }

        public String getIm()
        {
            return _im;
        }

        @SuppressWarnings("unused")
        public void setIm(String im)
        {
            _im = im;
        }

        public String getLastName()
        {
            return _lastName;
        }

        @SuppressWarnings("unused")
        public void setLastName(String lastName)
        {
            _lastName = lastName;
        }

        public String getMobile()
        {
            return _mobile;
        }

        @SuppressWarnings("unused")
        public void setMobile(String mobile)
        {
            _mobile = mobile;
        }

        public String getPager()
        {
            return _pager;
        }

        @SuppressWarnings("unused")
        public void setPager(String pager)
        {
            _pager = pager;
        }

        public String getPhone()
        {
            return _phone;
        }

        @SuppressWarnings("unused")
        public void setPhone(String phone)
        {
            _phone = phone;
        }

        public Integer getUserId()
        {
            return _userId;
        }

        @SuppressWarnings("unused")
        public void setUserId(Integer userId)
        {
            _userId = userId;
        }

        public String toString()
        {
            if (getEmail() != null)
                return getEmail();
            else if (getUserId() != null)
                return "userId " + getUserId();
            else if (getGroupId() != null)
                return "groupId " + getGroupId();
            else return "Unknown";
        }

        public UserPrincipal getUserPrincipal() throws Exception
        {
            if (getUserId() != null)
            {
                if (getEmail() != null)
                {
                    throw new Exception("Specify either userId or email but not both.");
                }
                User user = UserManager.getUser(getUserId());
                if (user == null)
                {
                    throw new Exception("Invalid user id.  User must already exist when using id.");
                }
                return user;
            }
            else if (getEmail() != null)
            {
                ValidEmail email = new ValidEmail(getEmail());
                return UserManager.getUser(email);
            }
            else if (getGroupId() != null)
            {
                Group memberGroup = SecurityManager.getGroup(getGroupId());
                if (memberGroup == null)
                {
                   throw new Exception("Invalid group id.  Member groups must already exist.");
                }
                return memberGroup;
            }
            else
            {
                throw new Exception("No id, name or email specified");
            }
        }
    }

    @JsonIgnoreProperties("apiVersion")
    public static class GroupForm
    {
        private Integer _groupId;        // Nullable; used first as identifier for group;
        private String _groupName;      // Nullable; required for creating a group
        private List<GroupMember> _members;      // can be used to provide more data than just email address; can be empty
        private Boolean _createGroup = false;   // if true, the group should be created if it doesn't exist; otherwise the operation will fail if the group does not exist
        private MemberEditOperation _method; // indicates the action to be performed with the given users in this group

        public String getGroupName()
        {
            return _groupName;
        }

        @SuppressWarnings("unused")
        public void setGroupName(String groupName)
        {
            _groupName = groupName;
        }

        public Integer getGroupId()
        {
            return _groupId;
        }

        @SuppressWarnings("unused")
        public void setGroupId(Integer groupId)
        {
            _groupId = groupId;
        }

        public Boolean getCreateGroup()
        {
            return _createGroup;
        }

        @SuppressWarnings("unused")
        public void setCreateGroup(Boolean createGroup)
        {
            _createGroup = createGroup;
        }

        public List<GroupMember> getMembers()
        {
            return _members;
        }

        @SuppressWarnings("unused")
        public void setMembers(List<GroupMember> members)
        {
            _members = members;
        }

        public MemberEditOperation getMethod()
        {
            return _method;
        }

        @SuppressWarnings("unused")
        public void setMethod(MemberEditOperation method)
        {
            _method = method;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public static class DeleteGroupAction extends MutatingApiAction<IdForm>
    {
        @Override
        public ApiResponse execute(IdForm form, BindException errors)
        {
            if (form.getId() < 0)
                throw new IllegalArgumentException("You must specify an id parameter!");

            Group group = SecurityManager.getGroup(form.getId());
            Container c = getContainer();
            if (null == group || (c.isRoot() && null != group.getContainer()) || (!c.isRoot() && !c.getId().equals(group.getContainer())))
            {
                String containerInfo = "this container!";
                if (!c.isRoot())
                    containerInfo = "the " + c.getName() + " project!";
                throw new IllegalArgumentException("Group id " + form.getId() + " does not exist within " + containerInfo);
            }

            SecurityManager.deleteGroup(group);
            writeToAuditLog(group);

            return new ApiSimpleResponse("deleted", form.getId());
        }

        protected void writeToAuditLog(Group group)
        {
            GroupAuditProvider.GroupAuditEvent event = new GroupAuditProvider.GroupAuditEvent(getContainer().getId(), "The security group named " + group.getName() + " was deleted.");
            event.setGroup(group.getUserId());

            AuditLogService.get().addEvent(getUser(), event);
        }
    }

    @RequiresPermission(DeleteUserPermission.class)
    public static class DeleteUserAction extends MutatingApiAction<IdForm>
    {
        @Override
        public ApiResponse execute(IdForm form, BindException errors) throws Exception
        {
            if (form.getId() < 0)
                throw new IllegalArgumentException("You must specify an id parameter!");

            User user = UserManager.getUser(form.getId());
            if (null == user)
                throw new IllegalArgumentException("User id " + form.getId() + " does not exist");

            //Issue 21648: allow deleting of any user from client API when using the root container
            Container c = getContainer();
            if (!c.isRoot())
            {
                List<User> projectUsers = SecurityManager.getProjectUsers(c);
                if (!projectUsers.contains(user))
                    throw new IllegalArgumentException("User id " + form.getId() + " does not exist in the folder: " + c.getPath());
            }

            UserManager.deleteUser(user.getUserId());

            return new ApiSimpleResponse("deleted", form.getId());
        }
    }

    public static class RenameForm
    {
        private int _id = -1;
        private String _newName;

        public int getId()
        {
            return _id;
        }

        public void setId(int id)
        {
            _id = id;
        }

        public String getNewName()
        {
            return _newName;
        }

        public void setNewName(String newName)
        {
            _newName = newName;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public static class RenameGroupAction extends FormApiAction<RenameForm>
    {
        Group group;

        private Group getGroup(RenameForm form)
        {
            if (form.getId() < 0)
                return null;
            Group group = SecurityManager.getGroup(form.getId());
            Container c = getContainer();
            if (null == group || (c.isRoot() && null != group.getContainer()) || (!c.isRoot() && !c.getId().equals(group.getContainer())))
                return null;
            return group;
        }

        @Override
        public ModelAndView getView(RenameForm form, BindException errors)
        {
            group = getGroup(form);
            if (null == group)
            {
                throw new NotFoundException();
            }
            return new JspView<>("/org/labkey/core/security/renameGroup.jsp", group, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Permissions", new ActionURL(SecurityController.PermissionsAction.class, getContainer()));
            root.addChild("Manage Group", new ActionURL(SecurityController.GroupAction.class, getContainer()).addParameter("id",group.getUserId()));
            root.addChild("Rename Group: " + group.getName());
        }

        @Override
        public ApiResponse execute(RenameForm form, BindException errors)
        {
            if (form.getId() < 0)
                throw new IllegalArgumentException("You must specify an id parameter!");

            group = getGroup(form);
            if (null == group)
                throw new IllegalArgumentException("Group id " + form.getId() + " does not exist within this container!");

            String oldName = group.getName();
            try
            {
                SecurityManager.renameGroup(group, form.getNewName(), getUser());
                group.setName(form.getNewName());
                writeToAuditLog(group, oldName);
            }
            catch (IllegalArgumentException x)
            {
                errors.reject(ERROR_MSG, x.getMessage());
                errors.rejectValue("newName", ERROR_MSG, x.getMessage());
            }

            if (errors.getErrorCount() > 0)
                return null;

            // get the updated group information with the new name
            group = getGroup(form);

            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            resp.put("renamed", group.getUserId());
            resp.put("oldName", oldName);
            resp.put("newName", group.getName());
            return resp;
        }


        public void writeToAuditLog(Group group, String oldName)
        {
            GroupAuditProvider.GroupAuditEvent event = new GroupAuditProvider.GroupAuditEvent(getContainer().getId(), "The security group named '" + oldName + "' was renamed to '" + group.getName() + "'.");
            event.setGroup(group.getUserId());

            AuditLogService.get().addEvent(getUser(), event);
        }
    }

    public static class GroupMemberForm
    {
        private int _groupId = -1;
        private int[] _principalIds;

        public int getGroupId()
        {
            return _groupId;
        }

        public void setGroupId(int groupId)
        {
            _groupId = groupId;
        }

        public int[] getPrincipalIds()
        {
            return _principalIds;
        }

        public void setPrincipalIds(int[] principalIds)
        {
            _principalIds = principalIds;
        }
    }

    public static abstract class BaseGroupMemberAction extends MutatingApiAction<GroupMemberForm>
    {
        protected enum MembershipModification
        {
            Added,
            Removed
        }

        public Group getGroup(GroupMemberForm form)
        {
            Group group = SecurityManager.getGroup(form.getGroupId());
            if (null == group)
                throw new IllegalArgumentException("Invalid group id (" + form.getGroupId() + ")");
            Container c = getContainer();
            if ((c.isRoot() && null == group.getContainer()) || c.getId().equals(group.getContainer()))
                return group;
            throw new IllegalArgumentException("Group id " + form.getGroupId() + " does not exist within this container!");
        }

        public UserPrincipal getPrincipal(int principalId)
        {
            UserPrincipal principal = SecurityManager.getPrincipal(principalId);
            if (null == principal)
                throw new IllegalArgumentException("Invalid principal id (" + principalId + ")");
            return principal;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class AddGroupMemberAction extends BaseGroupMemberAction
    {
        @Override
        public ApiResponse execute(GroupMemberForm form, BindException errors)
        {
            Group group = getGroup(form);

            if (group != null)
                SecurityController.verifyUserCanModifyGroup(group, getUser());

            for (int id : form.getPrincipalIds())
            {
                UserPrincipal principal = getPrincipal(id);
                try
                {
                    SecurityManager.addMember(group, principal);
                }
                catch (InvalidGroupMembershipException e)
                {
                    errors.reject(null, e.getMessage());
                }
                //group cache listener already writes to audit log
            }
            return new ApiSimpleResponse("added", form.getPrincipalIds());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class RemoveGroupMemberAction extends BaseGroupMemberAction
    {
        @Override
        public ApiResponse execute(GroupMemberForm form, BindException errors)
        {
            Group group = getGroup(form);

            if (group != null)
                SecurityController.verifyUserCanModifyGroup(group, getUser());

            for (int id : form.getPrincipalIds())
            {
                UserPrincipal principal = getPrincipal(id);
                SecurityManager.deleteMember(group, principal);
                //group cache listener already writes to audit log
            }

            return new ApiSimpleResponse("removed", form.getPrincipalIds());
        }
    }

    public static class CreateNewUsersForm
    {
        private String _email; // supports a semicolon list of email addresses
        private boolean _sendEmail = true;
        private boolean _skipFirstLogin = false;
        private String _optionalMessage;

        public String getEmail()
        {
            return _email;
        }

        public void setEmail(String email)
        {
            _email = email;
        }

        public boolean isSendEmail()
        {
            return _sendEmail;
        }

        public void setSendEmail(boolean sendEmail)
        {
            _sendEmail = sendEmail;
        }

        public boolean isSkipFirstLogin()
        {
            return _skipFirstLogin;
        }

        public void setSkipFirstLogin(boolean skipFirstLogin)
        {
            _skipFirstLogin = skipFirstLogin;
        }

        public String getOptionalMessage()
        {
            return _optionalMessage;
        }

        public void setOptionalMessage(String optionalMessage)
        {
            _optionalMessage = optionalMessage;
        }
    }

    @RequiresPermission(AdminPermission.class)
    @ActionNames("CreateNewUsers, CreateNewUser")
    public static class CreateNewUsersAction extends MutatingApiAction<CreateNewUsersForm>
    {
        @Override
        public ApiResponse execute(CreateNewUsersForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<Map<String, Object>> responses = new ArrayList<>();
            List<HtmlString> htmlErrors = new ArrayList<>();

            //FIX: 8585 -- must have Admin perm on the project as well as the current container
            Container c = getContainer();
            if (!c.isRoot() && !c.getProject().hasPermission(getUser(), AdminPermission.class))
                throw new UnauthorizedException("You must be an administrator at the project level to add new users.");
            else if (!c.hasPermission(getUser(), AddUserPermission.class))
                throw new UnauthorizedException("You do not have permissions to create new users.");

            String[] rawEmails = form.getEmail() == null ? null : form.getEmail().split(";");
            List<String> invalidEmails = new ArrayList<>();
            List<ValidEmail> validEmails = SecurityManager.normalizeEmails(rawEmails, invalidEmails);

            if (!invalidEmails.isEmpty())
                throw new IllegalArgumentException("Invalid email address" + (invalidEmails.size() > 1 ? "es" : "") + ": " + PageFlowUtil.filter(StringUtils.join(invalidEmails)));
            if (validEmails.isEmpty())
                throw new IllegalArgumentException("You must specify a valid email address in the 'email' parameter!");

            for (ValidEmail email : validEmails)
            {
                HtmlString msg = SecurityManager.addUser(getViewContext(), email, form.isSendEmail(), form.getOptionalMessage());
                User user = UserManager.getUser(email);
                if (null == user)
                {
                    htmlErrors.add(null != msg ? msg : HtmlString.of("Error creating new user account: " + email));
                }
                else
                {
                    boolean isNew = !HtmlString.isBlank(msg);
                    HtmlString htmlMsg = HtmlString.isBlank(msg) ? HtmlString.of(email + " was already a registered system user.") : msg;

                    // Allow tests to create users that immediately register as having "logged in"
                    if (getUser().hasSiteAdminPermission() && form.isSkipFirstLogin())
                    {
                        user.setLastLogin(new Date());
                        UserManager.updateLogin(user);
                        UserManager.clearUserList();
                    }

                    // if only one user being added, write response properties to the top level as well for backwards compatibility
                    if (validEmails.size() == 1)
                    {
                        response.put("userId", user.getUserId());
                        response.put("email", user.getEmail());
                        response.put("message", htmlMsg);
                    }
                    responses.add(Map.of(
                        "userId", user.getUserId(),
                        "email", user.getEmail(),
                        "message", htmlMsg,
                        "isNew", isNew
                    ));
                }
            }

            if (!htmlErrors.isEmpty()) response.put("htmlErrors", htmlErrors);
            response.put("success", htmlErrors.isEmpty());
            response.put("users", responses);
            return response;
        }
    }

    /**
     * Invalidate existing password and send new password link
     */
    @RequiresPermission(UpdateUserPermission.class)
    public static class AdminRotatePasswordAction extends MutatingApiAction<SecurityController.EmailForm>
    {
        @Override
        public void validateForm(SecurityController.EmailForm form, Errors errors)
        {
            ValidEmail email;

            try
            {
                email = new ValidEmail(form.getEmail());

                // don't let non-site admin reset password of site admin
                User formUser = UserManager.getUser(email);
                if (null == formUser)
                    errors.rejectValue("email", ERROR_MSG, "User not found");
                else if (!getUser().hasSiteAdminPermission() && formUser.hasSiteAdminPermission())
                    errors.rejectValue("Email", "Can not reset password for a Site Admin user");
            }
            catch (InvalidEmailException e)
            {
                errors.rejectValue("Email", "Invalid user email");
            }
        }

        @Override
        public ApiSimpleResponse execute(SecurityController.EmailForm form, BindException errors)
        {
            try
            {
                ValidEmail email = new ValidEmail(form.getEmail());
                SecurityManager.adminRotatePassword(email, errors, getContainer(), getUser());
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                //Should be caught in api validation
                errors.addError(new LabKeyError(new Exception("Invalid email address." + e.getMessage(), e)));
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("success", !errors.hasErrors());
            if (errors.hasErrors())
                response.put("message", errors.getMessage());

            return response;
        }
    }

    @RequiresPermission(UserManagementPermission.class)
    public static class ListProjectGroupsAction extends ReadOnlyApiAction<ListGroupsForm>
    {
        @Override
        public ApiResponse execute(ListGroupsForm form, BindException errors)
        {
            boolean includeSiteGroups = form.getIncludeSiteGroups();
            List<Group> groups = SecurityManager.getGroups(getContainer(), includeSiteGroups);

            //Convert to a json convertible map
            List<Map<String, Object>> responseGroups = groups.stream().map(group -> {
                Map<String, Object> props = new HashMap<>();
                props.put("Name", group.getName());
                props.put("Id", group.getUserId());
                props.put("Container", group.getContainer());
                return props;
            }).collect(Collectors.toList());

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("groups", responseGroups);
            return response;
        }
    }

    public static class ListGroupsForm
    {
        private Boolean includeSiteGroups = false;

        public Boolean getIncludeSiteGroups()
        {
            return includeSiteGroups ;
        }

        public void setIncludeSiteGroups(Boolean includeSiteGroups)
        {
            this.includeSiteGroups = includeSiteGroups;
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        @Test
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.hasSiteAdminPermission());

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user, false,
                new GetUserPermsAction(),
                new GetGroupsForCurrentUserAction(),
                new GetRolesAction(),
                new GetSecurableResourcesAction()
            );

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                new GetGroupPermsAction(),
                new GetPolicyAction(),
                new SavePolicyAction(),
                new DeletePolicyAction(),
                new AddAssignmentAction(),
                new RemoveAssignmentAction(),
                new ClearAssignedRolesAction(),
                new CreateGroupAction(),
                new BulkUpdateGroupAction(),
                new DeleteGroupAction(),
                new RenameGroupAction(),
                new AddGroupMemberAction(),
                new RemoveGroupMemberAction(),
                new CreateNewUsersAction()
            );

            // @RequiresPermission(UserManagementPermission.class)
            assertForUserPermissions(user,
                new DeleteUserAction(),
                new AdminRotatePasswordAction(),
                new ListProjectGroupsAction()
            );
        }
    }
}
