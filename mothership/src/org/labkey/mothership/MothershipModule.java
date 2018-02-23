/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
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
import org.labkey.api.view.WebPartFactory;
import org.labkey.mothership.query.MothershipSchema;

import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: jeckels
 * Date: Apr 19, 2006
 */
public class MothershipModule extends DefaultModule
{
    public static final String NAME = "Mothership";

    public String getName()
    {
        return NAME;
    }

    public double getVersion()
    {
        return 18.10;
    }

    protected void init()
    {
        addController("mothership", MothershipController.class);
        MothershipSchema.register(this);
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
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

    public void doStartup(ModuleContext moduleContext)
    {
        MothershipReport.setShowSelfReportExceptions(true);

        ContainerManager.addContainerListener(new ContainerManager.AbstractContainerListener()
        {
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
