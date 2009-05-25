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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.*;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.*;
import org.labkey.api.security.roles.ProjectAdminRole;
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
            if (null != role.getSourceModule())
                props.put("sourceModule", role.getSourceModule().getName());

            List<String> permissions = new ArrayList<String>();
            for(Class<? extends Permission> permClass : role.getPermissions())
            {
                Permission perm = RoleManager.getPermission(permClass);
                _allPermissions.add(perm);
                permissions.add(perm.getUniqueName());
            }

            props.put("permissions", permissions);

            List<Integer> excludedPrincipals = new ArrayList<Integer>();
            for(UserPrincipal principal : role.getExcludedPrincipals())
            {
                excludedPrincipals.add(principal.getUserId());
            }
            props.put("excludedPrincipals", excludedPrincipals);

            return props;
        }

        public Map<String,Object> getPermissionProps(Permission perm)
        {
            Map<String,Object> props = new HashMap<String,Object>();
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
    public static class GetSecurableResourcesAction extends ApiAction<GetSecurableResourcesForm>
    {
        private boolean _includeSubfolders = false;

        public ApiResponse execute(GetSecurableResourcesForm form, BindException errors) throws Exception
        {
            _includeSubfolders = form.isIncludeSubfolders();
            Container container = getViewContext().getContainer();
            return new ApiSimpleResponse("resources", getResourceProps(container));
        }

        protected Map<String,Object> getResourceProps(SecurableResource resource)
        {
            Map<String,Object> props = new HashMap<String,Object>();
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
            return props;
        }

        protected List<Map<String,Object>> getChildrenProps(SecurableResource resource)
        {
            List<Map<String,Object>> childProps = new ArrayList<Map<String,Object>>();
            for(SecurableResource child : resource.getChildResources(getViewContext().getUser()))
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

            //special cases for relevant roles:
            // - don't include project admin if this is not a project
            if(!container.isProject())
                relevantRoles.remove(RoleManager.getRole(ProjectAdminRole.class).getUniqueName());

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
    public static class SavePolicyAction extends MutatingApiAction<SavePolicyForm>
    {
        protected enum RoleModification
        {
            Added,
            Removed
        }

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

            //get the existing policy so we can audit how it's changed
            SecurityPolicy oldPolicy = SecurityManager.getPolicy(resource);

            //create the policy from the props (will throw if invalid)
            SecurityPolicy policy = SecurityPolicy.fromMap(form.getProps(), resource);

            //save it
            SecurityManager.savePolicy(policy);

            //audit log
            writeToAuditLog(oldPolicy, policy);

            return new ApiSimpleResponse("success", true);
        }

        protected void writeToAuditLog(SecurityPolicy oldPolicy, SecurityPolicy newPolicy)
        {
            SecurableResource resource = newPolicy.getResource();

            //if moving from inherted to not-inherited, just log the new role assignments
            if (!(oldPolicy.getResource().getResourceId().equals(newPolicy.getResource().getResourceId())))
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

            Container c = getViewContext().getContainer();
            AuditLogEvent event = new AuditLogEvent();
            event.setComment(sb.toString());
            event.setContainerId(c.getId());
            event.setProjectId(c.getProject() != null ? c.getProject().getId() : null);
            event.setCreatedBy(getViewContext().getUser());
            event.setEntityId(resource.getResourceId());
            event.setEventType(GroupManager.GROUP_AUDIT_EVENT);
            AuditLogService.get().addEvent(event);
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public static class DeletePolicyAction extends MutatingApiAction<PolicyIdForm>
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
            event.setCreatedBy(getViewContext().getUser());
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
    public static class CreateGroupAction extends MutatingApiAction<NameForm>
    {
        public ApiResponse execute(NameForm form, BindException errors) throws Exception
        {
            Container container = getViewContext().getContainer();
            if(!container.isRoot() && !container.isProject())
                throw new IllegalArgumentException("You may not create groups at the folder level. Call this API at the project or root level.");

            String name = StringUtils.trimToNull(form.getName());
            if(null == name)
                throw new IllegalArgumentException("You must specify a name parameter!");

            Group newGroup = SecurityManager.createGroup(getViewContext().getContainer().getProject(), name);
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
    public static class DeleteGroupAction extends MutatingApiAction<IdForm>
    {
        public ApiResponse execute(IdForm form, BindException errors) throws Exception
        {
            if(form.getId() < 0)
                throw new IllegalArgumentException("You must specify an id parameter!");

            Group group = SecurityManager.getGroup(form.getId());
            if(null == group || !getViewContext().getContainer().getId().equals(group.getContainer()))
                throw new IllegalArgumentException("Group id " + form.getId() + " does not exist within this container!");

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
            if(!getViewContext().getContainer().getId().equals(group.getContainer()))
                throw new IllegalArgumentException("Group id " + form.getGroupId() + " does not exist within this container!");
            return group;
        }

        public UserPrincipal getPrincipal(int principalId)
        {
            UserPrincipal principal = SecurityManager.getPrincipal(principalId);
            if(null == principal)
                throw new IllegalArgumentException("Invalid principal id (" + principalId + ")");
            return principal;
        }

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
                SecurityManager.addMember(group, principal);
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
            if(group.isAdministrators() && SecurityManager.getGroupMembers(group).size() == 1)
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

}