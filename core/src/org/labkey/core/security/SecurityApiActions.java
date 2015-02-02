/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.FormApiAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.InvalidGroupMembershipException;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.study.Dataset;
import org.labkey.api.util.HString;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @RequiresPermissionClass(ReadPermission.class)
    @RequiresLogin
    public static class GetGroupPermsAction extends ApiAction<GetGroupPermsForm>
    {
        public ApiResponse execute(GetGroupPermsForm form, BindException errors) throws Exception
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

        protected Map<String,Object> getContainerPerms(Container container, Group[] groups, boolean recurse)
        {
            SecurityPolicy policy = container.getPolicy();
            Map<String,Object> containerPerms = new HashMap<>();
            containerPerms.put("path", container.getPath());
            containerPerms.put("id", container.getId());
            containerPerms.put("name", container.getName());
            containerPerms.put("isInheritingPerms", container.isInheritedAcl());
            containerPerms.put("groups", getGroupPerms(container, policy, groups));

            if(recurse && container.hasChildren())
            {
                List<Map<String,Object>> childPerms = new ArrayList<>();
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

        protected List<Map<String, Object>> getGroupPerms(Container container, SecurityPolicy policy, Group[] groups)
        {
            if (null == policy)
                policy = container.getPolicy();

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

                int perms = policy.getPermsAsOldBitMask(group);
                groupPerms.put("permissions", perms);

                SecurityManager.PermissionSet role = SecurityManager.PermissionSet.findPermissionSet(perms);
                if(null != role)
                {
                    groupPerms.put("role", role.toString());
                    groupPerms.put("roleLabel", role.getLabel());
                }

                //add effective roles array
                Set<Role> effectiveRoles = policy.getEffectiveRoles(group);
                ArrayList<String> effectiveRoleList = new ArrayList<>();
                for (Role effectiveRole : effectiveRoles)
                {
                    effectiveRoleList.add(effectiveRole.getUniqueName());
                }
                groupPerms.put("roles", effectiveRoleList);
                groupPerms.put("effectivePermissions", container.getPolicy().getPermissionNames(group));

                groupsPerms.add(groupPerms);
            }

            return groupsPerms;
        }
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

        public void setUserId(Integer userId)
        {
            _userId = userId;
        }

        public String getUserEmail()
        {
            return _userEmail;
        }

        public void setUserEmail(String userEmail)
        {
            _userEmail = userEmail;
        }

        public boolean isIncludeSubfolders()
        {
            return _includeSubfolders;
        }

        public void setIncludeSubfolders(boolean includeSubfolders)
        {
            _includeSubfolders = includeSubfolders;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public static class GetUserPermsAction extends ApiAction<GetUserPermsForm>
    {
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
                throw new IllegalArgumentException("No user found that matches specified userId or email address");

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

            //add user's effective permissions
            SecurityPolicy policy = container.getPolicy();
            int perms = policy.getPermsAsOldBitMask(user);
            permsInfo.put("permissions", perms);

            //see if those match a given role name
            SecurityManager.PermissionSet role = SecurityManager.PermissionSet.findPermissionSet(perms);
            if(null != role)
            {
                permsInfo.put("role", role.toString());
                permsInfo.put("roleLabel", role.getLabel());
            }
            else
            {
                permsInfo.put("role", "Mixed");
                permsInfo.put("roleLabel", "(Mixed)");
            }

            //effective roles
            List<String> effectiveRoles = new ArrayList<>();
            for(Role effectiveRole : policy.getEffectiveRoles(user))
            {
                effectiveRoles.add(effectiveRole.getUniqueName());
            }
            permsInfo.put("roles", effectiveRoles);
            permsInfo.put("effectivePermissions", container.getPolicy().getPermissionNames(user));

            //add all groups the user belongs to in this container
            List<Group> groups = SecurityManager.getGroups(container, user);
            List<Map<String, Object>> groupsInfo = new ArrayList<>();

            for(Group group : groups)
            {
                Map<String,Object> groupInfo = new HashMap<>();
                groupInfo.put("id", group.getUserId());
                groupInfo.put("name", SecurityManager.getDisambiguatedGroupName(group));

                int groupPerms = policy.getPermsAsOldBitMask(group);
                groupInfo.put("permissions", groupPerms);

                SecurityManager.PermissionSet groupRole = SecurityManager.PermissionSet.findPermissionSet(groupPerms);
                if(null != groupRole)
                {
                    groupInfo.put("role", groupRole.toString());
                    groupInfo.put("roleLabel", groupRole.getLabel());
                }

                //effective roles
                List<String> groupEffectiveRoles = new ArrayList<>();
                for(Role effectiveRole : policy.getEffectiveRoles(group))
                {
                    groupEffectiveRoles.add(effectiveRole.getUniqueName());
                }
                groupInfo.put("roles", groupEffectiveRoles);
                groupInfo.put("effectivePermissions", container.getPolicy().getPermissionNames(group));


                groupsInfo.add(groupInfo);
            }
            permsInfo.put("groups", groupsInfo);

            //recurse children if desired
            if(recurse && container.hasChildren())
            {
                List<Map<String,Object>> childPerms = new ArrayList<>();
                for(Container child : container.getChildren())
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

    @RequiresPermissionClass(ReadPermission.class)
    public static class GetGroupsForCurrentUserAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            List<Map<String, Object>> groupInfos = new ArrayList<>();
            //include both project and global groups
            List<Group> groups = SecurityManager.getGroups(getContainer(), getUser());
            for(Group group : groups)
            {
                Map<String, Object> groupInfo = new HashMap<>();
                groupInfo.put("id", group.getUserId());
                groupInfo.put("name", SecurityManager.getDisambiguatedGroupName(group));
                groupInfo.put("isProjectGroup", group.isProjectGroup());
                groupInfo.put("isSystemGroup", group.isSystemGroup());
                groupInfos.add(groupInfo);
            }

            return new ApiSimpleResponse("groups", groupInfos);
        }
    }

    @RequiresLogin
    @IgnoresTermsOfUse
    public static class EnsureLoginAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
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

    @RequiresPermissionClass(ReadPermission.class)
    public static class GetRolesAction extends ApiAction
    {
        private Set<Permission> _allPermissions = new HashSet<>();

        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ArrayList<Map<String,Object>> rolesProps = new ArrayList<>();

            for(Role role : RoleManager.getAllRoles())
            {
                if(role.isAssignable())
                    rolesProps.add(getRoleProps(role));
            }

            List<Map<String,Object>> permsProps = new ArrayList<>();
            for(Permission perm : _allPermissions)
            {
                permsProps.add(getPermissionProps(perm));
            }

            ApiSimpleResponse resp = new ApiSimpleResponse("roles", rolesProps);
            resp.put("permissions", permsProps);
            return resp;
        }

        public Map<String,Object> getRoleProps(Role role)
        {
            Map<String,Object> props = new HashMap<>();
            props.put("uniqueName", role.getUniqueName());
            props.put("name", role.getName());
            props.put("description", role.getDescription());
            if (null != role.getSourceModule())
                props.put("sourceModule", role.getSourceModule().getName());

            List<String> permissions = new ArrayList<>();
            for(Class<? extends Permission> permClass : role.getPermissions())
            {
                Permission perm = RoleManager.getPermission(permClass);
                _allPermissions.add(perm);
                permissions.add(perm.getUniqueName());
            }

            props.put("permissions", permissions);

            List<Integer> excludedPrincipals = new ArrayList<>();
            for(UserPrincipal principal : role.getExcludedPrincipals())
            {
                excludedPrincipals.add(principal.getUserId());
            }
            props.put("excludedPrincipals", excludedPrincipals);

            return props;
        }

        public Map<String,Object> getPermissionProps(Permission perm)
        {
            Map<String,Object> props = new HashMap<>();
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

    @RequiresPermissionClass(ReadPermission.class)
    public static class GetSecurableResourcesAction extends ApiAction<GetSecurableResourcesForm>
    {
        private boolean _includeSubfolders = false;
        private boolean _includePermissions = false;

        public ApiResponse execute(GetSecurableResourcesForm form, BindException errors) throws Exception
        {
            _includeSubfolders = form.isIncludeSubfolders();
            _includePermissions = form.isIncludeEffectivePermissions();
            Container container = getContainer();
            return new ApiSimpleResponse("resources", getResourceProps(container));
        }

        protected Map<String,Object> getResourceProps(SecurableResource resource)
        {
            Map<String,Object> props = new HashMap<>();
            props.put("resourceClass", resource.getClass().getName());
            props.put("id", resource.getResourceId());
            props.put("name", resource.getResourceName());
            props.put("description", resource.getResourceDescription());
            if (null != resource.getSourceModule())
                props.put("sourceModule", resource.getSourceModule().getName());
            props.put("children", getChildrenProps(resource));

            SecurableResource parent = resource.getParentResource();
            if(null != parent)
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
                if (resource instanceof Dataset)
                {
                    Dataset ds = (Dataset)resource;
                    permissions = ds.getPermissions(user);
                }
                else
                {
                    SecurityPolicy policy = SecurityPolicyManager.getPolicy(resource);
                    permissions = policy.getPermissions(user);
                }
                
                for (Class<? extends Permission> permission : permissions)
                {
                    permNames.add(RoleManager.getPermission(permission).getUniqueName());
                }

                props.put("effectivePermissions", permNames);
            }

            return props;
        }

        protected List<Map<String,Object>> getChildrenProps(SecurableResource resource)
        {
            List<Map<String,Object>> childProps = new ArrayList<>();
            for(SecurableResource child : resource.getChildResources(getUser()))
            {
                if(_includeSubfolders || !(child instanceof Container))
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

    @RequiresPermissionClass(AdminPermission.class)
    public static class GetPolicyAction extends ApiAction<PolicyIdForm>
    {
        public ApiResponse execute(PolicyIdForm form, BindException errors) throws Exception
        {
            if(null == form.getResourceId())
                throw new IllegalArgumentException("You must supply a resourceId parameter!");

            Container container = getContainer();
            User user = getUser();

            //resolve the resource
            SecurableResource resource = container.findSecurableResource(form.getResourceId(), user);
            if(null == resource)
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
                resp.put("policy", mpolicy.toMap());
            }
            else
                resp.put("policy", policy.toMap());

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

    public static class SavePolicyForm implements CustomApiForm
    {
        private Map<String,Object> _props;

        public void bindProperties(Map<String, Object> props)
        {
            _props = props;
        }

        public Map<String,Object> getProps()
        {
            return _props;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    @CSRF
    public static class SavePolicyAction extends MutatingApiAction<SavePolicyForm>
    {
        protected enum RoleModification
        {
            Added,
            Removed
        }

        public ApiResponse execute(SavePolicyForm form, BindException errors) throws Exception
        {
            Container container = getContainer();
            User user = getUser();

            //resolve the resource
            String resourceId = (String)form.getProps().get("resourceId");
            if(null == resourceId || resourceId.length() == 0)
                throw new IllegalArgumentException("You must include a resourceId as a top-level property!");

            SecurableResource resource = container.findSecurableResource(resourceId, user);
            if(null == resource)
                throw new IllegalArgumentException("No resource with the id '" + resourceId + "' was found in this container!");

            //ensure that user has admin permission on resource
            if (!SecurityPolicyManager.getPolicy(resource).hasPermission(user, AdminPermission.class))
                throw new IllegalArgumentException("You do not have permission to modify the security policy for this resource!");

            //get the existing policy so we can audit how it's changed
            SecurityPolicy oldPolicy = SecurityPolicyManager.getPolicy(resource);
            MutableSecurityPolicy policy = null;

            //save it
            try
            {
                //create the policy from the props (will throw if invalid)
                policy = MutableSecurityPolicy.fromMap(form.getProps(), resource);
                SecurityPolicyManager.savePolicy(policy);
            }
            catch (Exception e)
            {
                errors.reject(null, e.getMessage());
            }

            //audit log
            writeToAuditLog(resource, oldPolicy, policy);

            return new ApiSimpleResponse("success", true);
        }

        protected void writeToAuditLog(SecurableResource resource, SecurityPolicy oldPolicy, SecurityPolicy newPolicy)
        {
            //if moving from inherted to not-inherited, just log the new role assignments
            if (!(oldPolicy.getResourceId().equals(newPolicy.getResourceId())))
            {
                SecurableResource parent = resource.getParentResource();
                String parentName = parent != null ? parent.getResourceName() : "root";
                AuditLogService.get().addEvent(getViewContext(), GroupManager.GROUP_AUDIT_EVENT,
                        resource.getResourceId(), "A new security policy was established for " +
                        resource.getResourceName() + ". It will no longer inherit permissions from " +
                        parentName);
                for (RoleAssignment newAsgn : newPolicy.getAssignments())
                {
                    writeAuditEvent(newAsgn.getUserId(), newAsgn.getRole(), RoleModification.Added, resource);
                }
                return;
            }

            Iterator<RoleAssignment> oldIter = oldPolicy.getAssignments().iterator();
            Iterator<RoleAssignment> newIter = newPolicy.getAssignments().iterator();
            RoleAssignment oldAsgn = oldIter.hasNext() ? oldIter.next() : null;
            RoleAssignment newAsgn = newIter.hasNext() ? newIter.next() : null;

            while (null != oldAsgn && null != newAsgn)
            {
                //if different users...
                if (oldAsgn.getUserId() != newAsgn.getUserId())
                {
                    //if old user < new user, user has been removed
                    if (oldAsgn.getUserId() < newAsgn.getUserId())
                    {
                        writeAuditEvent(oldAsgn.getUserId(), oldAsgn.getRole(), RoleModification.Removed, resource);
                    }
                    else
                    {
                        //else, user has been added
                        writeAuditEvent(newAsgn.getUserId(), newAsgn.getRole(), RoleModification.Added, resource);
                    }
                }
                else if (!oldAsgn.getRole().equals(newAsgn.getRole()))
                {
                    //if old role < new role, role has been removed
                    if (oldAsgn.getRole().getUniqueName().compareTo(newAsgn.getRole().getUniqueName()) < 0)
                    {
                        writeAuditEvent(oldAsgn.getUserId(), oldAsgn.getRole(), RoleModification.Removed, resource);
                    }
                    else
                    {
                        //else, role has been added
                        writeAuditEvent(newAsgn.getUserId(), newAsgn.getRole(), RoleModification.Added, resource);
                    }
                }

                //advance
                if (oldAsgn.compareTo(newAsgn) > 0)
                    newAsgn = newIter.hasNext() ? newIter.next() : null;
                else if (oldAsgn.compareTo(newAsgn) < 0)
                    oldAsgn = oldIter.hasNext() ? oldIter.next() : null;
                else
                {
                    newAsgn = newIter.hasNext() ? newIter.next() : null;
                    oldAsgn = oldIter.hasNext() ? oldIter.next() : null;
                }

            }

            //after the loop, we may still have remaining entries in either the old or new assignments
            while (null != newAsgn)
            {
                writeAuditEvent(newAsgn.getUserId(), newAsgn.getRole(), RoleModification.Added, resource);
                newAsgn = newIter.hasNext() ? newIter.next() : null;
            }

            while (null != oldAsgn)
            {
                writeAuditEvent(oldAsgn.getUserId(), oldAsgn.getRole(), RoleModification.Removed, resource);
                oldAsgn = oldIter.hasNext() ? oldIter.next() : null;
            }
        }

        protected void writeAuditEvent(int principalId, Role role, RoleModification mod, SecurableResource resource)
        {
            UserPrincipal principal = SecurityManager.getPrincipal(principalId);
            if(null == principal)
                return;

            StringBuilder sb = new StringBuilder("The user/group ");
            sb.append(principal.getName());
            if (RoleModification.Added == mod)
                sb.append(" was assigned to the security role ");
            else
                sb.append(" was removed from the security role ");
            sb.append(role.getName());
            sb.append(".");

            Container c = getContainer();
            AuditLogEvent event = new AuditLogEvent();
            event.setComment(sb.toString());
            event.setContainerId(c.getId());
            event.setProjectId(c.getProject() != null ? c.getProject().getId() : null);
            event.setCreatedBy(getUser());
            event.setIntKey1(principal.getUserId());
            event.setEntityId(resource.getResourceId());
            event.setEventType(GroupManager.GROUP_AUDIT_EVENT);
            AuditLogService.get().addEvent(event);
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    @CSRF
    public static class DeletePolicyAction extends MutatingApiAction<PolicyIdForm>
    {
        public ApiResponse execute(PolicyIdForm form, BindException errors) throws Exception
        {
            Container container = getContainer();
            User user = getUser();

            //resolve the resource
            String resourceId = form.getResourceId();
            if(null == resourceId || resourceId.length() == 0)
                throw new IllegalArgumentException("You must include a resourceId as a top-level property!");

            SecurableResource resource = container.findSecurableResource(resourceId, user);
            if(null == resource)
                throw new IllegalArgumentException("No resource with the id '" + resourceId + "' was found in this container!");

            //ensure that user has admin permission on resource
            if (!SecurityPolicyManager.getPolicy(resource).hasPermission(user, AdminPermission.class))
                throw new IllegalArgumentException("You do not have permission to delete the security policy for this resource!");

            SecurityPolicyManager.deletePolicy(resource);

            //audit log
            writeToAuditLog(resource);

            return new ApiSimpleResponse("success", true);
        }

        protected void writeToAuditLog(SecurableResource resource)
        {
            String parentResource = resource.getParentResource() != null ? resource.getParentResource().getResourceName() : "root";
            AuditLogEvent event = new AuditLogEvent();
            event.setComment("The security policy for " + resource.getResourceName() 
                    + " was deleted. It will now inherit the security policy of " +
                    parentResource);
            event.setContainerId(resource.getResourceContainer().getId());
            if(null != resource.getResourceContainer().getProject())
                event.setProjectId(resource.getResourceContainer().getProject().getId());
            event.setEventType(GroupManager.GROUP_AUDIT_EVENT);
            event.setCreatedBy(getUser());
            event.setEntityId(resource.getResourceId());
            AuditLogService.get().addEvent(event);
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

    @RequiresPermissionClass(AdminPermission.class)
    @CSRF
    public static class CreateGroupAction extends MutatingApiAction<NameForm>
    {
        public ApiResponse execute(NameForm form, BindException errors) throws Exception
        {
            Container container = getContainer();
            if(!container.isRoot() && !container.isProject())
                throw new IllegalArgumentException("You may not create groups at the folder level. Call this API at the project or root level.");

            String name = StringUtils.trimToNull(form.getName());
            if(null == name)
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
            AuditLogService.get().addEvent(getViewContext(), GroupManager.GROUP_AUDIT_EVENT, newGroup.getName(),
                    "A new security group named " + newGroup.getName() + " was created.");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    @CSRF
    public static class DeleteGroupAction extends MutatingApiAction<IdForm>
    {
        public ApiResponse execute(IdForm form, BindException errors) throws Exception
        {
            if(form.getId() < 0)
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
            AuditLogService.get().addEvent(getViewContext(), GroupManager.GROUP_AUDIT_EVENT, group.getName(),
                    "The security group named " + group.getName() + " was deleted.");
        }
    }

    @RequiresSiteAdmin
    @CSRF
    public static class DeleteUserAction extends MutatingApiAction<IdForm>
    {
        public ApiResponse execute(IdForm form, BindException errors) throws Exception
        {
            if(form.getId() < 0)
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
        private HString _newName;

        public int getId()
        {
            return _id;
        }

        public void setId(int id)
        {
            _id = id;
        }

        public HString getNewName()
        {
            return _newName;
        }

        public void setNewName(HString newName)
        {
            _newName = newName;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    @CSRF
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

        public ModelAndView getView(RenameForm form, BindException errors) throws Exception
        {
            group = getGroup(form);
            if (null == group)
            {
                throw new NotFoundException();
            }
            return new JspView<>(SecurityController.class, "renameGroup.jsp", group, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Permissions", new ActionURL(SecurityController.PermissionsAction.class, getContainer()));
            root.addChild("Manage Group", new ActionURL(SecurityController.GroupAction.class, getContainer()).addParameter("id",group.getUserId()));
            root.addChild("Rename Group: " + group.getName());
            return root;

        }

        @Override
        public ModelAndView handleRequest() throws Exception
        {
            return super.handleRequest();
        }
        

        public ApiResponse execute(RenameForm form, BindException errors) throws Exception
        {
            if (form.getId() < 0)
                throw new IllegalArgumentException("You must specify an id parameter!");

            group = getGroup(form);
            Container c = getContainer();
            if (null == group)
                throw new IllegalArgumentException("Group id " + form.getId() + " does not exist within this container!");

            String oldName = group.getName();
            try
            {
                SecurityManager.renameGroup(group, form.getNewName().toString(), getUser());
                writeToAuditLog(group, oldName);
            }
            catch (IllegalArgumentException x)
            {
                errors.reject(SpringActionController.ERROR_MSG, x.getMessage());    
                errors.rejectValue("newName", SpringActionController.ERROR_MSG, x.getMessage());
            }

            if (errors.getErrorCount() > 0)
                return null;

            // get the udpated group information with the new name
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
            AuditLogService.get().addEvent(getViewContext(), GroupManager.GROUP_AUDIT_EVENT, group.getName(),
                    "The security group named '" + oldName + "' was renamed to '" + group.getName() + "'.");

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
            if(null == group)
                throw new IllegalArgumentException("Invalid group id (" + form.getGroupId() + ")");
            Container c = getContainer();
            if ((c.isRoot() && null == group.getContainer()) || c.getId().equals(group.getContainer()))
                return group;
            throw new IllegalArgumentException("Group id " + form.getGroupId() + " does not exist within this container!");
        }

        public UserPrincipal getPrincipal(int principalId)
        {
            UserPrincipal principal = SecurityManager.getPrincipal(principalId);
            if(null == principal)
                throw new IllegalArgumentException("Invalid principal id (" + principalId + ")");
            return principal;
        }

        // Not used... TODO: Delete?
        protected void writeToAuditLog(Group group, UserPrincipal principal, MembershipModification mod)
        {
            StringBuilder sb = new StringBuilder("The user/group ");
            sb.append(principal.getName());
            sb.append(" was ");
            sb.append(mod.name());
            sb.append(" to the security group ");
            sb.append(group.getName());
            
            AuditLogService.get().addEvent(getViewContext(), GroupManager.GROUP_AUDIT_EVENT, group.getName(), sb.toString());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public static class AddGroupMemberAction extends BaseGroupMemberAction
    {
        public ApiResponse execute(GroupMemberForm form, BindException errors) throws Exception
        {
            Group group = getGroup(form);
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

    @RequiresPermissionClass(AdminPermission.class)
    public static class RemoveGroupMemberAction extends BaseGroupMemberAction
    {
        public ApiResponse execute(GroupMemberForm form, BindException errors) throws Exception
        {
            Group group = getGroup(form);

            //ensure there will still be someone in the admin group
            if (group.isAdministrators() && SecurityManager.getGroupMembers(group, MemberType.ACTIVE_AND_INACTIVE_USERS).size() == 1)
                throw new IllegalArgumentException("The system administrators group must have at least one member!");

            for (int id : form.getPrincipalIds())
            {
                UserPrincipal principal = getPrincipal(id);
                SecurityManager.deleteMember(group, principal);
                //group cache listener already writes to audit log
            }

            return new ApiSimpleResponse("removed", form.getPrincipalIds());
        }
    }

    public static class CreateNewUserForm
    {
        private HString _email;
        private boolean _sendEmail = true;

        public HString getEmail()
        {
            return _email;
        }

        public void setEmail(HString email)
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
    }

    @RequiresPermissionClass(AdminPermission.class)
    @CSRF
    public static class CreateNewUserAction extends MutatingApiAction<CreateNewUserForm>
    {
        public ApiResponse execute(CreateNewUserForm form, BindException errors) throws Exception
        {
            if (null == form.getEmail() || form.getEmail().trim().length() == 0)
                throw new IllegalArgumentException("You must specify a valid email address in the 'email' parameter!");

            //FIX: 8585 -- must have Admin perm on the project as well as the current container
            Container c = getContainer();
            if (!c.isRoot() && !c.getProject().hasPermission(getUser(), AdminPermission.class))
                throw new UnauthorizedException("You must be an administrator at the project level to add new users");

            ValidEmail email;

            try
            {
                email = new ValidEmail(form.getEmail().getSource().trim());
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                throw new IllegalArgumentException(e.getMessage());
            }

            String msg = SecurityManager.addUser(getViewContext(), email, form.isSendEmail(), null, null);
            User user = UserManager.getUser(email);
            if (null == user)
                throw new IllegalArgumentException(null != msg ? msg : "Error creating new user account.");

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("userId", user.getUserId());
            response.put("email", user.getEmail());
            if (null != msg)
                response.put("message", msg);

            return response;
        }
    }

}
