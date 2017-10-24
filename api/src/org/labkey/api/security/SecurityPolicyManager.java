/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbScope;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Handles persistence and loading of {@link SecurityPolicy} information over {@link SecurableResource}s.
 * Caches for performance reasons.
 * User: adam
 * Date: 7/6/12
 */
public class SecurityPolicyManager
{
    private static final Logger logger = Logger.getLogger(SecurityPolicyManager.class);
    private static final CoreSchema core = CoreSchema.getInstance();
    private static final Cache<String, SecurityPolicy> CACHE = new DatabaseCache<>(core.getSchema().getScope(), CacheManager.UNLIMITED, "SecurityPolicies");

    @NotNull
    public static SecurityPolicy getPolicy(@NotNull SecurableResource resource)
    {
        return getPolicy(resource, true);
    }

    /**
     *  Retrieve the SecurityPolicy directly associated with this resource, if any. Does not check inheritance.
     *  @return The SecurityPolicy, if one exists, otherwise null
     */
    @Nullable
    public static SecurityPolicy getPolicy(@NotNull Container c, @NotNull String resourceId)
    {
        return CACHE.get(cacheKey(resourceId), null, (key, argument) -> {
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

    public static void savePolicy(@NotNull MutableSecurityPolicy policy)
    {
        DbScope scope = core.getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            //if the policy to save has a version, check to see if it's the current one
            //(note that this may be a new policy so there might not be an existing one)
            SecurityPolicyBean currentPolicyBean = new TableSelector(core.getTableInfoPolicies())
                    .getObject(policy.getResourceId(), SecurityPolicyBean.class);

            if (null != currentPolicyBean && null != policy.getModified() &&
                    0 != policy.getModified().compareTo(currentPolicyBean.getModified()))
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
                UserPrincipal principal = SecurityManager.getPrincipal(assignment.getUserId());
                if (principal == null)
                    logger.info("Principal " + assignment.getUserId() + " no longer in database. Removing from policy.");
                else
                    Table.insert(null, table, assignment);
            }

            //commit transaction
            transaction.commit();
        }
        //remove the resource-oriented policy from cache
        remove(policy);
        notifyPolicyChange(policy.getResourceId());
    }

    public static void notifyPolicyChange(String objectID)
    {
        // UNDONE: generalize cross manager/module notifications
        ContainerManager.notifyContainerChange(objectID);
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
        try (DbScope.Transaction transaction = core.getSchema().getScope().ensureTransaction())
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
        if (resources.size() == 0 || principals.size() == 0)
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


    // Remove all assignments and policies on the Containers childre
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
}
