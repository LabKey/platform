/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.data.Container;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.*;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.springframework.validation.BindException;

import java.util.*;

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

    @RequiresPermissionClass(AdminPermission.class)
    public static class GetGroupPermsAction extends ApiAction<GetGroupPermsForm>
    {
        public ApiResponse execute(GetGroupPermsForm form, BindException errors) throws Exception
        {
            Container container = getViewContext().getContainer();

            ApiSimpleResponse response = new ApiSimpleResponse();

            //if the container is not the root container, get the set of groups
            //from the container's project and pass that down the recursion stack
            response.put("container", getContainerPerms(container,
                    org.labkey.api.security.SecurityManager.getGroups(container.getProject(), true),
                    form.isIncludeSubfolders()));

            return response;
        }

        protected Map<String,Object> getContainerPerms(Container container, Group[] groups, boolean recurse)
        {
            SecurityPolicy policy = container.getPolicy();
            Map<String,Object> containerPerms = new HashMap<String,Object>();
            containerPerms.put("path", container.getPath());
            containerPerms.put("id", container.getId());
            containerPerms.put("name", container.getName());
            containerPerms.put("isInheritingPerms", container.isInheritedAcl());
            containerPerms.put("groups", getGroupPerms(container, policy, groups));

            if(recurse && container.hasChildren())
            {
                List<Map<String,Object>> childPerms = new ArrayList<Map<String,Object>>();
                for(Container child : container.getChildren())
                    childPerms.add(getContainerPerms(child,
                            child.isProject() ? org.labkey.api.security.SecurityManager.getGroups(child, true) : groups,
                            recurse));

                containerPerms.put("children", childPerms);
            }

            return containerPerms;
        }

        protected List<Map<String,Object>> getGroupPerms(Container container, SecurityPolicy policy, Group[] groups)
        {
            if(null == policy)
                policy = container.getPolicy();

            if(null == groups)
                return null;

            List<Map<String,Object>> groupsPerms = new ArrayList<Map<String,Object>>();
            for(Group group : groups)
            {
                Map<String,Object> groupPerms = new HashMap<String,Object>();
                groupPerms.put("id", group.getUserId());
                groupPerms.put("name", SecurityManager.getDisambiguatedGroupName(group));
                groupPerms.put("type", group.getType());
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

    @RequiresPermissionClass(AdminPermission.class)
    public static class GetUserPermsAction extends ApiAction<GetUserPermsForm>
    {
        public ApiResponse execute(GetUserPermsForm form, BindException errors) throws Exception
        {
            //need either userid or user email name
            if(null == form.getUserId() && null == form.getUserEmail())
                throw new IllegalArgumentException("You must provide either a userId or a userEmail parameter!");

            User user = null;
            if(null != form.getUserId())
                user = UserManager.getUser(form.getUserId().intValue());
            if(null == user)
                user = UserManager.getUser(new ValidEmail(form.getUserEmail()));

            if(null == user)
                throw new IllegalArgumentException("No user found that matches specified userId or email address");

            ApiSimpleResponse response = new ApiSimpleResponse();

            Map<String,Object> userInfo = new HashMap<String,Object>();
            userInfo.put("userId", user.getUserId());
            userInfo.put("displayName", user.getDisplayName(getViewContext()));
            response.put("user", userInfo);

            response.put("container", getContainerPerms(getViewContext().getContainer(), user, form.isIncludeSubfolders()));
            return response;
        }

        protected Map<String,Object> getContainerPerms(Container container, User user, boolean recurse)
        {
            Map<String,Object> permsInfo = new HashMap<String,Object>();

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

            //add all groups the user belongs to in this container
            List<Group> groups = SecurityManager.getGroups(container, user);
            List<Map<String,Object>> groupsInfo = new ArrayList<Map<String,Object>>();
            for(Group group : groups)
            {
                Map<String,Object> groupInfo = new HashMap<String,Object>();
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

                groupsInfo.add(groupInfo);
            }
            permsInfo.put("groups", groupsInfo);

            //recurse children if desired
            if(recurse && container.hasChildren())
            {
                List<Map<String,Object>> childPerms = new ArrayList<Map<String,Object>>();
                for(Container child : container.getChildren())
                    childPerms.add(getContainerPerms(child, user, recurse));

                permsInfo.put("children", childPerms);
            }

            return permsInfo;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public static class GetGroupsForCurrentUserAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            List<Map<String,Object>> groupInfos = new ArrayList<Map<String,Object>>();
            //include both project and global groups
            List<Group> groups = SecurityManager.getGroups(getViewContext().getContainer(), getViewContext().getUser());
            for(Group group : groups)
            {
                Map<String,Object> groupInfo = new HashMap<String,Object>();
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
            User user = getViewContext().getUser();
            Container container = getViewContext().getContainer();

            //return similar info as is already exposed on LABKEY.user & LABKEY.Security.currentUser
            //so we can swap it out
            Map<String,Object> userInfo = new HashMap<String,Object>();
            userInfo.put("id", user.getUserId());
            userInfo.put("displayName", user.getDisplayName(getViewContext()));
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

    @RequiresPermissionClass(AdminPermission.class)
    public static class GetRolesAction extends ApiAction
    {
        private Set<Permission> _allPermissions = new HashSet<Permission>();

        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ArrayList<Map<String,Object>> rolesProps = new ArrayList<Map<String,Object>>();

            for(Role role : RoleManager.getAllRoles())
            {
                if(role.isAssignable())
                    rolesProps.add(getRoleProps(role));
            }

            List<Map<String,Object>> permsProps = new ArrayList<Map<String,Object>>();
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
            Map<String,Object> props = new HashMap<String,Object>();
            props.put("uniqueName", role.getUniqueName());
            props.put("name", role.getName());
            props.put("description", role.getDescription());
            props.put("sourceModule", role.getSourceModule().getName());

            List<String> permissions = new ArrayList<String>();
            for(Class<? extends Permission> permClass : role.getPermissions())
            {
                Permission perm = RoleManager.getPermission(permClass);
                _allPermissions.add(perm);
                permissions.add(perm.getUniqueName());
            }

            props.put("permissions", permissions);
            return props;
        }

        public Map<String,Object> getPermissionProps(Permission perm)
        {
            Map<String,Object> props = new HashMap<String,Object>();
            props.put("uniqueName", perm.getUniqueName());
            props.put("name", perm.getName());
            props.put("description", perm.getDescription());
            props.put("sourceModule", perm.getSourceModule().getName());
            return props;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public static class GetSecurableResourcesAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            Container container = getViewContext().getContainer();
            return new ApiSimpleResponse("resources", getResourceProps(container));
        }

        protected Map<String,Object> getResourceProps(SecurableResource resource)
        {
            Map<String,Object> props = new HashMap<String,Object>();
            props.put("id", resource.getResourceId());
            props.put("name", resource.getResourceName());
            props.put("description", resource.getResourceDescription());
            props.put("sourceModule", resource.getSourceModule().getName());
            props.put("children", getChildrenProps(resource));
            return props;
        }

        protected List<Map<String,Object>> getChildrenProps(SecurableResource resource)
        {
            List<Map<String,Object>> childProps = new ArrayList<Map<String,Object>>();
            for(SecurableResource child : resource.getChildResources(getViewContext().getUser()))
            {
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

            Container container = getViewContext().getContainer();
            User user = getViewContext().getUser();

            //resolve the resource
            SecurableResource resource = container.findSecurableResource(form.getResourceId(), user);
            if(null == resource)
                throw new IllegalArgumentException("The requested resource does not exist within this container!");

            //get the policy
            SecurityPolicy policy = SecurityManager.getPolicy(resource);

            ApiSimpleResponse resp = new ApiSimpleResponse("policy", policy.toMap());

            //add the relevant roles
            List<String> relevantRoles = new ArrayList<String>();
            Set<Class<? extends Permission>> resourcePerms = resource.getRelevantPermissions();
            for(Role role : RoleManager.getAllRoles())
            {
                if(!role.isAssignable())
                    continue;

                for(Class<? extends Permission> perm : role.getPermissions())
                {
                    if(resourcePerms.contains(perm))
                    {
                        relevantRoles.add(role.getUniqueName());
                        break;
                    }
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
    public static class SavePolicyAction extends ApiAction<SavePolicyForm>
    {
        public ApiResponse execute(SavePolicyForm form, BindException errors) throws Exception
        {
            Container container = getViewContext().getContainer();
            User user = getViewContext().getUser();

            //resolve the resource
            String resourceId = (String)form.getProps().get("resourceId");
            if(null == resourceId || resourceId.length() == 0)
                throw new IllegalArgumentException("You must include a resourceId as a top-level property!");

            SecurableResource resource = container.findSecurableResource(resourceId, user);
            if(null == resource)
                throw new IllegalArgumentException("No resource with the id '" + resourceId + "' was found in this container!");

            //create the policy from the props (will throw if invalid)
            SecurityPolicy policy = SecurityPolicy.fromMap(form.getProps(), resource);

            //save it
            SecurityManager.savePolicy(policy);

            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public static class DeletePolicyAction extends ApiAction<PolicyIdForm>
    {
        public ApiResponse execute(PolicyIdForm form, BindException errors) throws Exception
        {
            Container container = getViewContext().getContainer();
            User user = getViewContext().getUser();

            //resolve the resource
            String resourceId = form.getResourceId();
            if(null == resourceId || resourceId.length() == 0)
                throw new IllegalArgumentException("You must include a resourceId as a top-level property!");

            SecurableResource resource = container.findSecurableResource(resourceId, user);
            if(null == resource)
                throw new IllegalArgumentException("No resource with the id '" + resourceId + "' was found in this container!");

            SecurityManager.deletePolicy(resource);
            return new ApiSimpleResponse("success", true);
        }
    }

}