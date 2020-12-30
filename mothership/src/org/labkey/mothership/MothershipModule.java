/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.mothership;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.AbstractMethodInfo;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.Group;
import org.labkey.api.security.InvalidGroupMembershipException;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MothershipReport;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.mothership.query.MothershipSchema;
import org.labkey.mothership.view.ExceptionListWebPart;

import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: jeckels
 * Date: Apr 19, 2006
 */
public class MothershipModule extends DefaultModule
{
    public static final String NAME = "Mothership";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Double getSchemaVersion()
    {
        return 21.000;
    }

    @Override
    protected void init()
    {
        addController("mothership", MothershipController.class);
        MothershipSchema.register(this);

        SqlDialect postgresDialect = SqlDialectManager.getFromDriverClassname(null, "org.postgresql.Driver");

        // Wire up JSON and JSONB data type support for Postgres, as described here:
        // https://www.postgresql.org/docs/9.5/functions-json.html
        // Ultimately we need to promote these out of the mothership module but this will enable it for JSON metric
        // reporting for now.

        // Pretend that the JSON operators are a function instead so that we don't need them to be fully supported
        // for query parsing

        if (MothershipManager.get().getDialect().isPostgreSQL())
        {
            QueryService.get().registerMethod("json_op", new AbstractMethodInfo(JdbcType.VARCHAR)
            {
                private final Set<String> ALLOWED_OPERATORS = Set.of("->", "->>", "#>", "#>>", "@>", "<@",
                        "?", "?|", "?&", "||", "-", "#-");

                @Override
                public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
                {
                    SQLFragment rawOperator = arguments[1];
                    String operatorRawString = rawOperator.getSQL();
                    if (!rawOperator.getParams().isEmpty() || !operatorRawString.startsWith("'") || !operatorRawString.endsWith("'"))
                    {
                        throw new QueryParseException("Unsupported JSON operator: " + rawOperator, null, 0, 0);
                    }

                    String strippedOperator = operatorRawString.substring(1, operatorRawString.length() - 1);
                    if (!ALLOWED_OPERATORS.contains(strippedOperator))
                    {
                        throw new QueryParseException("Unsupported JSON operator: " + rawOperator, null, 0, 0);
                    }

                    return new SQLFragment("(").append(arguments[0]).append(")").
                            append(strippedOperator).
                            append("(").append(arguments[2]).append(")");
                }


            }, JdbcType.VARCHAR, 3, 3);
        }

        QueryService.get().registerPassthroughMethod("to_json", null, JdbcType.OTHER, 1, 1, postgresDialect);
        QueryService.get().registerPassthroughMethod("to_jsonb", null, JdbcType.OTHER, 1, 1, postgresDialect);
        QueryService.get().registerPassthroughMethod("array_to_json", null, JdbcType.OTHER, 1, 2, postgresDialect);
        QueryService.get().registerPassthroughMethod("row_to_json", null, JdbcType.OTHER, 1, 2, postgresDialect);

        addJsonPassthroughMethod("build_array", JdbcType.OTHER, 1, Integer.MAX_VALUE, postgresDialect);
        addJsonPassthroughMethod("build_object", JdbcType.OTHER, 1, Integer.MAX_VALUE, postgresDialect);
        addJsonPassthroughMethod("object", JdbcType.OTHER, 1, 2, postgresDialect);

        addJsonPassthroughMethod("array_length", JdbcType.INTEGER, 1, 1, postgresDialect);
        addJsonPassthroughMethod("each", JdbcType.OTHER, 1, 1, postgresDialect);
        addJsonPassthroughMethod("each_text", JdbcType.OTHER, 1, 1, postgresDialect);
        addJsonPassthroughMethod("extract_path", JdbcType.OTHER, 2, Integer.MAX_VALUE, postgresDialect);
        addJsonPassthroughMethod("extract_path_text", JdbcType.VARCHAR, 2, Integer.MAX_VALUE, postgresDialect);
        addJsonPassthroughMethod("object_keys", JdbcType.OTHER, 1, 1, postgresDialect);
        addJsonPassthroughMethod("populate_record", JdbcType.OTHER, 2, 2, postgresDialect);
        addJsonPassthroughMethod("populate_recordset", JdbcType.OTHER, 2, 2, postgresDialect);
        addJsonPassthroughMethod("array_elements", JdbcType.OTHER, 1, 1, postgresDialect);
        addJsonPassthroughMethod("array_elements_text", JdbcType.VARCHAR, 1, 1, postgresDialect);
        addJsonPassthroughMethod("typeof", JdbcType.VARCHAR, 1, 1, postgresDialect);
        addJsonPassthroughMethod("to_record", JdbcType.OTHER, 1, 1, postgresDialect);
        addJsonPassthroughMethod("to_recordset", JdbcType.OTHER, 1, 1, postgresDialect);
        addJsonPassthroughMethod("strip_nulls", JdbcType.OTHER, 1, 1, postgresDialect);

        QueryService.get().registerPassthroughMethod("jsonb_set", null, JdbcType.OTHER, 3, 4, postgresDialect);
        QueryService.get().registerPassthroughMethod("jsonb_pretty", null, JdbcType.VARCHAR, 1, 1, postgresDialect);
    }

    private void addJsonPassthroughMethod(String name, JdbcType type, int minArgs, int maxArgs, SqlDialect dialect)
    {
        QueryService.get().registerPassthroughMethod("json_" + name, null, type, minArgs, maxArgs, dialect);
        QueryService.get().registerPassthroughMethod("jsonb_" + name, null, type, minArgs, maxArgs, dialect);
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return List.of(
            new BaseWebPartFactory("Exception List", false, false)
            {
                @Override
                public WebPartView<?> getWebPartView(@NotNull ViewContext portalCtx, Portal.@NotNull WebPart webPart)
                {
                    return new ExceptionListWebPart(portalCtx.getUser(), portalCtx.getContainer(), null);
                }
            }
        );
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            bootstrap(moduleContext);
    }

    private void bootstrap(ModuleContext moduleContext)
    {
        Container c = ContainerManager.ensureContainer(MothershipReport.CONTAINER_PATH);
        Group mothershipGroup = SecurityManager.createGroup(c, NAME);
        MutableSecurityPolicy policy = new MutableSecurityPolicy(c, SecurityPolicyManager.getPolicy(c));
        Role noPermsRole = RoleManager.getRole(NoPermissionsRole.class);
        Role projAdminRole = RoleManager.getRole(ProjectAdminRole.class);
        policy.addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), noPermsRole);
        policy.addRoleAssignment(SecurityManager.getGroup(Group.groupUsers), noPermsRole);
        policy.addRoleAssignment(SecurityManager.getGroup(Group.groupAdministrators), projAdminRole);
        policy.addRoleAssignment(mothershipGroup, projAdminRole);
        SecurityPolicyManager.savePolicy(policy);

        try
        {
            SecurityManager.addMember(mothershipGroup, moduleContext.getUpgradeUser());
        }
        catch (InvalidGroupMembershipException e)
        {
            // Not really possible, but just in case
            ExceptionUtil.logExceptionToMothership(null, e);
        }

        Set<Module> modules = new HashSet<>(c.getActiveModules(moduleContext.getUpgradeUser()));
        modules.add(this);
        c.setActiveModules(modules, moduleContext.getUpgradeUser());
        c.setDefaultModule(this);
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(MothershipManager.get().getSchemaName());
    }

    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        return PageFlowUtil.set(ExceptionStackTrace.TestCase.class);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        MothershipReport.setShowSelfReportExceptions(true);

        ContainerManager.addContainerListener(new ContainerManager.AbstractContainerListener()
        {
            @Override
            public void containerDeleted(Container c, User user)
            {
                MothershipManager.get().deleteForContainer(c);
            }
        });

        UserManager.addUserListener(new UserManager.UserListener()
        {
            @Override
            public void userAddedToSite(User user)
            {
            }

            @Override
            public void userDeletedFromSite(User user)
            {
                MothershipManager.get().deleteForUser(user);
            }

            @Override
            public void userAccountDisabled(User user)
            {
            }

            @Override
            public void userAccountEnabled(User user)
            {
            }

            @Override
            public void propertyChange(PropertyChangeEvent evt)
            {
            }
        });
    }
}
