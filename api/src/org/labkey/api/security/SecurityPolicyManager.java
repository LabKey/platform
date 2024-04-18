/*
 * Copyright (c) 2012-2018 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.Constants;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.GroupAuditProvider;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.CommitTaskOption;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.security.xml.GroupEnumType;
import org.labkey.security.xml.GroupRefType;
import org.labkey.security.xml.GroupRefsType;
import org.labkey.security.xml.UserRefType;
import org.labkey.security.xml.UserRefsType;
import org.labkey.security.xml.roleAssignment.RoleAssignmentType;
import org.labkey.security.xml.roleAssignment.RoleAssignmentsType;
import org.labkey.security.xml.roleAssignment.RoleRefType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * Handles persistence and loading of {@link SecurityPolicy} information over {@link SecurableResource}s.
 * Caches for performance reasons.
 */
public class SecurityPolicyManager
{
    private static final Logger logger = LogHelper.getLogger(SecurityPolicyManager.class, "Security policy information");
    private static final CoreSchema core = CoreSchema.getInstance();
    private static final BlockingCache<String, SecurityPolicy> CACHE;

    static
    {
        CACHE = DatabaseCache.get(core.getSchema().getScope(), Constants.getMaxContainers() * 3, "Security policies", (resourceId, argument) -> {
            Container c = (Container) argument;
            SecurityPolicyBean policyBean = new TableSelector(core.getTableInfoPolicies(), SimpleFilter.createContainerFilter(c), null).getObject(resourceId, SecurityPolicyBean.class);

            if (null != policyBean)
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ResourceId"), resourceId);
                Selector selector = new TableSelector(core.getTableInfoRoleAssignments(), filter, new Sort("UserId"));
                Collection<RoleAssignment> assignments = selector.getCollection(RoleAssignment.class);

                return new SecurityPolicy(policyBean.getResourceId(), policyBean.getResourceClass(), policyBean.getContainer().getId(), assignments, policyBean.getModified());
            }

            return null;
        });
    }

    @NotNull
    public static SecurityPolicy getPolicy(@NotNull SecurableResource resource)
    {
        return getPolicy(resource, true);
    }

    /**
     * Retrieve the SecurityPolicy directly associated with this resource, if any. Does not check inheritance.
     * @return The SecurityPolicy, if one exists, otherwise null
     */
    @Nullable
    public static SecurityPolicy getPolicy(@NotNull Container c, @NotNull String resourceId)
    {
        return CACHE.get(resourceId, c);
    }

    @NotNull
    public static SecurityPolicy getPolicy(@NotNull final SecurableResource resource, boolean findNearest)
    {
        SecurityPolicy policy = getPolicy(resource.getResourceContainer(), resource.getResourceId());

        if (null == policy && findNearest && resource.mayInheritPolicy())
        {
            SecurableResource parent = resource.getParentResource();
            if (null != parent)
                return getPolicy(parent, true);
        }

        return null != policy ? policy : new SecurityPolicy(resource, Collections.emptyList());
    }

    // Just for consistency
    public static String cacheKey(String resourceId)
    {
        return resourceId;
    }

    public static String cacheKey(SecurableResource resource)
    {
        return resource.getResourceId();
    }

    private static String cacheKey(SecurityPolicy policy)
    {
        return policy.getResourceId();
    }

    // Functionally identical to the method below, but tests should call this variant to ease validation of proper
    // permission checking in non-test code
    public static boolean savePolicyForTests(@NotNull MutableSecurityPolicy policy, @NotNull User user)
    {
        return savePolicy(policy, user);
    }

    // Preferred method: this one validates, creates audit events, and returns whether roles were changed
    public static boolean savePolicy(@NotNull MutableSecurityPolicy policy, @NotNull User user)
    {
        return savePolicy(policy, user, true);
    }

    public static boolean savePolicy(@NotNull MutableSecurityPolicy policy, @NotNull User user, boolean validateUsers)
    {
        Container c = ContainerManager.getForId(policy.getContainerId());
        if (null == c)
            throw new IllegalStateException("Container does not exist");

        SecurableResource resource = c.findSecurableResource(policy.getResourceId(), user);
        if (null == resource)
            throw new IllegalStateException("No resource with the id '" + policy.getResourceId() + "' was found in this container!");

        // Get the existing policy so we can audit how it's changed and check for unauthorized changes
        SecurityPolicy oldPolicy = SecurityPolicyManager.getPolicy(resource);
        SortedSet<RoleAssignment> savedAssignments = oldPolicy.getAssignments();
        SortedSet<RoleAssignment> updatedAssignments = policy.getAssignments();
        Set<Role> changedRoles = new HashSet<>();
        for (RoleAssignment r : savedAssignments)
            if (!updatedAssignments.contains(r))
                changedRoles.add(r.getRole());
        for (RoleAssignment r : updatedAssignments)
            if (!savedAssignments.contains(r))
                changedRoles.add(r.getRole());

        if (c.isRoot() && !user.hasSiteAdminPermission())
        {
            for (Role changedRole : changedRoles)
            {
                // AppAdmin cannot change assignments to privileged roles (e.g., Site Admin, Platform Developer, Impersonating Troubleshooter)
                if (changedRole.isPrivileged())
                    throw new UnauthorizedException("You do not have permission to modify the " + changedRole.getName() + " role.");
            }
        }

        savePolicyToDBAndValidate(policy, validateUsers);
        writeToAuditLog(c, user, resource, oldPolicy, policy);

        return !changedRoles.isEmpty();
    }

    private static void savePolicyToDBAndValidate(@NotNull MutableSecurityPolicy policy, boolean validateUsers)
    {
        DbScope scope = core.getSchema().getScope();

        try (Transaction transaction = scope.ensureTransaction())
        {
            //if the policy to save has a version, check to see if it's the current one
            //(note that this may be a new policy so there might not be an existing one)
            SecurityPolicyBean currentPolicyBean = new TableSelector(core.getTableInfoPolicies())
                    .getObject(policy.getResourceId(), SecurityPolicyBean.class);

            if (null != currentPolicyBean && null != policy.getModified() &&
                    // Use millisecond granularity; need to protect against comparing a posted Date (no nanos) against a stored Timestamp (with nanos)
                    currentPolicyBean.getModified().getTime() != policy.getModified().getTime())
            {
                throw new OptimisticConflictException("The security policy you are attempting to save" +
                " has been altered by someone else since you selected it.", Table.SQLSTATE_TRANSACTION_STATE, 0);
            }

            //normalize the policy to get rid of extraneous no perms role assignments
            policy.normalize();

            //save to policies table
            if (null == currentPolicyBean)
                Table.insert(null, core.getTableInfoPolicies(), policy.getBean());
            else
                Table.update(null, core.getTableInfoPolicies(), policy.getBean(), policy.getResourceId());

            TableInfo table = core.getTableInfoRoleAssignments();

            //delete all rows where resourceid = resource.getId()
            Table.delete(table, new SimpleFilter(FieldKey.fromParts("ResourceId"), policy.getResourceId()));

            //insert rows for the policy entries
            for (RoleAssignment assignment : policy.getAssignments())
            {
                if (validateUsers)
                {
                    UserPrincipal principal = SecurityManager.getPrincipal(assignment.getUserId());
                    if (principal == null)
                    {
                        logger.info("Principal " + assignment.getUserId() + " no longer in database. Removing from policy.");
                        continue;
                    }
                }
                Table.insert(null, table, assignment);
            }

            // Remove policy from cache immediately (before the last root admin check) and again after commit/rollback
            transaction.addCommitTask(() -> remove(policy), CommitTaskOption.IMMEDIATE, CommitTaskOption.POSTCOMMIT, CommitTaskOption.POSTROLLBACK);
            // Notify on commit
            transaction.addCommitTask(() -> notifyPolicyChange(policy.getResourceId()), CommitTaskOption.POSTCOMMIT);

            // Ensure at least one root admin will remain if attempting to modify the root container's policy
            if (policy.getResourceId().equals(ContainerManager.getRoot().getResourceId()))
                SecurityManager.ensureAtLeastOneRootAdminExists();

            transaction.commit();
        }
    }

    protected enum RoleModification
    {
        Added,
        Removed
    }

    private static void writeToAuditLog(Container c, @Nullable User user, SecurableResource resource, @Nullable SecurityPolicy oldPolicy, SecurityPolicy newPolicy)
    {
        //if moving from inherited to not-inherited, just log the new role assignments
        if (null == oldPolicy || !(oldPolicy.getResourceId().equals(newPolicy.getResourceId())))
        {
            GroupAuditProvider.GroupAuditEvent event = getGroupAuditEvent(c, resource);
            AuditLogService.get().addEvent(user, event);

            for (RoleAssignment newAsgn : newPolicy.getAssignments())
            {
                writeAuditEvent(c, user, newAsgn.getUserId(), newAsgn.getRole(), RoleModification.Added, resource);
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
                    writeAuditEvent(c, user, oldAsgn.getUserId(), oldAsgn.getRole(), RoleModification.Removed, resource);
                }
                else
                {
                    //else, user has been added
                    writeAuditEvent(c, user, newAsgn.getUserId(), newAsgn.getRole(), RoleModification.Added, resource);
                }
            }
            else if (!oldAsgn.getRole().equals(newAsgn.getRole()))
            {
                //if old role < new role, role has been removed
                if (oldAsgn.getRole().getUniqueName().compareTo(newAsgn.getRole().getUniqueName()) < 0)
                {
                    writeAuditEvent(c, user, oldAsgn.getUserId(), oldAsgn.getRole(), RoleModification.Removed, resource);
                }
                else
                {
                    //else, role has been added
                    writeAuditEvent(c, user, newAsgn.getUserId(), newAsgn.getRole(), RoleModification.Added, resource);
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
            writeAuditEvent(c, user, newAsgn.getUserId(), newAsgn.getRole(), RoleModification.Added, resource);
            newAsgn = newIter.hasNext() ? newIter.next() : null;
        }

        while (null != oldAsgn)
        {
            writeAuditEvent(c, user, oldAsgn.getUserId(), oldAsgn.getRole(), RoleModification.Removed, resource);
            oldAsgn = oldIter.hasNext() ? oldIter.next() : null;
        }
    }

    @NotNull
    private static GroupAuditProvider.GroupAuditEvent getGroupAuditEvent(Container c, SecurableResource resource)
    {
        SecurableResource parent = resource.getParentResource();
        String parentName = parent != null ? parent.getResourceName() : "root";
        GroupAuditProvider.GroupAuditEvent event = new GroupAuditProvider.GroupAuditEvent(c.getId(),
                "A new security policy was established for " +
                        resource.getResourceName() + ". It will no longer inherit permissions from " +
                        parentName);
        event.setResourceEntityId(resource.getResourceId());
        return event;
    }

    protected static void writeAuditEvent(Container c, User user, int principalId, Role role, RoleModification mod, SecurableResource resource)
    {
        UserPrincipal principal = SecurityManager.getPrincipal(principalId);
        if (null == principal)
            return;

        StringBuilder sb = new StringBuilder("The " + principal.getPrincipalType().getDescription().toLowerCase() + " ");
        sb.append(principal.getName());
        if (RoleModification.Added == mod)
            sb.append(" was assigned to the security role ");
        else
            sb.append(" was removed from the security role ");
        sb.append(role.getName());
        sb.append(".");

        GroupAuditProvider.GroupAuditEvent event = new GroupAuditProvider.GroupAuditEvent(c.getId(), sb.toString());
        event.setProjectId(c.getProject() != null ? c.getProject().getId() : null);
        if (principal.getPrincipalType() == PrincipalType.USER)
            event.setUser(principal.getUserId());
        else
            event.setGroup(principal.getUserId());
        event.setResourceEntityId(resource.getResourceId());

        AuditLogService.get().addEvent(user, event);
    }

    public static void notifyPolicyChange(String objectID)
    {
        // UNDONE: generalize cross manager/module notifications
        ContainerManager.notifyContainerChange(objectID, ContainerManager.Property.Policy);
    }

    public static void notifyPolicyChanges(List<String> objectIDs)
    {
        for (String objectID : objectIDs)
        {
            notifyPolicyChange(objectID);
        }
    }

    public static void deletePolicy(@NotNull SecurableResource resource)
    {
        try (Transaction transaction = core.getSchema().getScope().ensureTransaction())
        {
            //delete all rows where resourceid = resource.getResourceId()
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ResourceId"), resource.getResourceId());
            Table.delete(core.getTableInfoRoleAssignments(), filter);
            Table.delete(core.getTableInfoPolicies(), filter);

            //commit transaction
            transaction.commit();
        }

        //remove the resource-oriented policy from cache
        remove(resource);

        notifyPolicyChange(resource.getResourceId());
    }

    /**
     * Clears all role assignments for the specified principals for the specified resources.
     * After this call completes, all the specified principals will no longer have any role
     * assignments for the specified resources.
     * @param resources The resources
     * @param principals The principals
     */
    public static void clearRoleAssignments(@NotNull Set<SecurableResource> resources, @NotNull Set<UserPrincipal> principals)
    {
        if (resources.isEmpty() || principals.isEmpty())
            return;

        SQLFragment sql = new SQLFragment("DELETE FROM ");
        sql.append(core.getTableInfoRoleAssignments());
        sql.append(" WHERE ResourceId ");

        List<String> resourceIds = new ArrayList<>(resources.size());
        for (SecurableResource resource : resources)
        {
            resourceIds.add(resource.getResourceId());
        }
        core.getSqlDialect().appendInClauseSql(sql, resourceIds);

        sql.append(" AND UserId ");
        List<Integer> userIds = new ArrayList<>(principals.size());
        for (UserPrincipal principal : principals)
        {
            userIds.add(principal.getUserId());
        }
        core.getSqlDialect().appendInClauseSql(sql, userIds);

        new SqlExecutor(core.getSchema()).execute(sql);

        removeAll();

        for (SecurableResource resource : resources)
        {
            notifyPolicyChange(resource.getResourceId());
        }
    }


    public static void removeAll(Container c)
    {
        SqlExecutor executor = new SqlExecutor(core.getSchema());

        executor.execute(
                "DELETE FROM " + core.getTableInfoRoleAssignments() + " WHERE ResourceId IN (SELECT ResourceId FROM " +
                core.getTableInfoPolicies() + " WHERE Container = ?)",
                c);
        executor.execute(
                "DELETE FROM " + core.getTableInfoPolicies() + " WHERE Container = ?",
                c);

        removeAll();
    }


    // Remove all assignments and policies on the Container's children
    public static void removeAllChildren(Container c)
    {
        Set<Container> subtrees = ContainerManager.getAllChildren(c);
        StringBuilder sb = new StringBuilder();
        String comma = "";

        for (Container sub : subtrees)
        {
            sb.append(comma);
            sb.append("'");
            sb.append(sub.getId());
            sb.append("'");
            comma = ",";
        }

        new SqlExecutor(core.getSchema()).execute("DELETE FROM " + core.getTableInfoRoleAssignments() + "\n" +
                "WHERE ResourceId IN (SELECT ResourceId FROM " + core.getTableInfoPolicies() + " WHERE Container IN (" +
                sb.toString() + "))");
        new SqlExecutor(core.getSchema()).execute("DELETE FROM " + core.getTableInfoPolicies() + "\n" +
                "WHERE Container IN (" + sb.toString() + ")");

        removeAll();
    }


    private static void remove(SecurableResource resource)
    {
        CACHE.remove(cacheKey(resource));
    }


    private static void remove(SecurityPolicy policy)
    {
        CACHE.remove(cacheKey(policy));
    }

    /** Clear all cached SecurityPolicy instances */
    public static void removeAll()
    {
        CACHE.clear();
    }

    public static void exportRoleAssignments(SecurityPolicy policy, RoleAssignmentsType roleAssignments)
    {
        Map<String, Map<PrincipalType, List<UserPrincipal>>> map = policy.getAssignmentsAsMap();

        for (String roleName : map.keySet())
        {
            Map<PrincipalType, List<UserPrincipal>> assignees = map.get(roleName);
            RoleAssignmentType roleAssignment = roleAssignments.addNewRoleAssignment();
            RoleRefType role = roleAssignment.addNewRole();
            role.setName(roleName);
            if (assignees.get(PrincipalType.GROUP) != null)
            {
                GroupRefsType groups = roleAssignment.addNewGroups();
                for (UserPrincipal user : assignees.get(PrincipalType.GROUP))
                {
                    Group group = (Group) user;
                    GroupRefType groupRef = groups.addNewGroup();
                    groupRef.setName(group.getName());
                    groupRef.setType(group.isProjectGroup() ? GroupEnumType.PROJECT : GroupEnumType.SITE);
                }
            }
            if (assignees.get(PrincipalType.USER) != null)
            {
                UserRefsType users = roleAssignment.addNewUsers();
                for (UserPrincipal user : assignees.get(PrincipalType.USER))
                {
                    UserRefType userRef = users.addNewUser();
                    userRef.setName(user.getName());
                }
            }
        }
    }

    public static void importRoleAssignments(FolderImportContext ctx, MutableSecurityPolicy policy, RoleAssignmentsType assignments)
    {
        for (RoleAssignmentType assignmentXml : assignments.getRoleAssignmentArray())
        {
            Role role = RoleManager.getRole(assignmentXml.getRole().getName());
            if (role == null)
            {
                ctx.getLogger().warn("Invalid role name ignored: " + assignmentXml.getRole());
                continue;
            }
            try
            {
                if (assignmentXml.isSetGroups())
                {
                    for (GroupRefType groupRef : assignmentXml.getGroups().getGroupArray())
                    {
                        UserPrincipal principal = GroupManager.getGroup(ctx.getContainer(), groupRef.getName(), groupRef.getType());
                        if (principal == null)
                        {
                            ctx.getLogger().warn("Non-existent group in role assignment for role " + assignmentXml.getRole().getName() + " will be ignored: " + groupRef.getName());
                        }
                        else
                        {
                            policy.addRoleAssignment(principal, role);
                        }
                    }
                }
                if (assignmentXml.isSetUsers())
                {
                    for (UserRefType userRef : assignmentXml.getUsers().getUserArray())
                    {
                        try
                        {
                            ValidEmail validEmail = new ValidEmail(userRef.getName());
                            UserPrincipal principal = UserManager.getUser(validEmail);

                            if (principal == null)
                            {
                                ctx.getLogger().warn("Non-existent user in role assignment for role " + assignmentXml.getRole() + " will be ignored: " + userRef.getName());
                            }
                            else
                            {
                                policy.addRoleAssignment(principal, role);
                            }
                        }
                        catch (ValidEmail.InvalidEmailException e)
                        {
                            ctx.getLogger().error("Invalid email in role assignment for role " + assignmentXml.getRole());
                        }
                    }
                }
            }
            catch (IllegalArgumentException x)
            {
                // can happen if assignment contains invalid role (e.g. projectadmin for non-project folder)
                ctx.getLogger().warn(x.getMessage());
            }
        }
        SecurityPolicyManager.savePolicy(policy, ctx.getUser());
    }
}
