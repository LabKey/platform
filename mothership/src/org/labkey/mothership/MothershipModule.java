/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.Module;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.data.*;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.roles.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.mothership.query.MothershipSchema;

import java.sql.SQLException;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.util.Collections;
import java.beans.PropertyChangeEvent;

import junit.framework.TestCase;

/**
 * User: jeckels
 * Date: Apr 19, 2006
 */
public class MothershipModule extends DefaultModule
{
    public String getName()
    {
        return "Mothership";
    }

    public double getVersion()
    {
        return 9.10;
    }

    protected void init()
    {
        addController("mothership", MothershipController.class);
        MothershipSchema.register();
    }

    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    public boolean hasScripts()
    {
        return true;
    }

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
        super.afterUpdate(moduleContext);

        if (moduleContext.isNewInstall())
            bootstrap(moduleContext);
    }

    private void bootstrap(ModuleContext moduleContext)
    {
        try
        {
            Container c = ContainerManager.ensureContainer("/_mothership");
            Group mothershipGroup = SecurityManager.createGroup(c, "Mothership");
            SecurityPolicy policy = SecurityManager.getPolicy(c);
            Role noPermsRole = RoleManager.getRole(NoPermissionsRole.class);
            Role projAdminRole = RoleManager.getRole(ProjectAdminRole.class);
            policy.addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), noPermsRole);
            policy.addRoleAssignment(SecurityManager.getGroup(Group.groupUsers), noPermsRole);
            policy.addRoleAssignment(SecurityManager.getGroup(Group.groupAdministrators), projAdminRole);
            policy.addRoleAssignment(mothershipGroup, projAdminRole);
            SecurityManager.savePolicy(policy);

            SecurityManager.addMember(mothershipGroup, moduleContext.getUpgradeUser());

            Set<Module> modules = new HashSet<Module>(c.getActiveModules());
            modules.add(this);
            c.setActiveModules(modules);
            c.setDefaultModule(this);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(MothershipManager.get().getSchemaName());
    }

    public Set<DbSchema> getSchemasToTest()
    {
        Set<DbSchema> result = new HashSet<DbSchema>();
        result.add(MothershipManager.get().getSchema());
        return result;
    }

    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        Set<Class<? extends TestCase>> result = new HashSet<Class<? extends TestCase>>();
        result.add(ExceptionStackTrace.TestCase.class);
        return result;
    }

    public void startup(ModuleContext moduleContext)
    {
        ContainerManager.addContainerListener(new ContainerManager.ContainerListener()
        {
            public void containerCreated(Container c)
            {
            }

            public void containerDeleted(Container c, User user)
            {
                try
                {
                    MothershipManager.get().deleteForContainer(c);
                }
                catch (SQLException e)
                {
                    throw new RuntimeException("Delete failed", e);
                }
            }

            public void propertyChange(PropertyChangeEvent evt)
            {
            }
        });
    }
}
