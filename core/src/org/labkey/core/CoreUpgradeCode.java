/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.core;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Filter;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.core.login.DbLoginManager;
import org.labkey.core.login.LoginController;
import org.labkey.core.login.PasswordRule;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 21, 2008
 * Time: 9:57:49 PM
 */
public class CoreUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(CoreUpgradeCode.class);

    // We don't call ContainerManager.getRoot() during upgrade code since the container table may not yet match
    //  ContainerManager's assumptions. For example, older installations don't have a description column until
    //  the 10.1 scripts run (see #9927).
    private String getRootId()
    {
        try
        {
            return Table.executeSingleton(CoreSchema.getInstance().getSchema(), "SELECT EntityId FROM core.Containers WHERE Parent IS NULL", null, String.class);
        }
        catch (SQLException e)
        {
            return null;
        }
    }


    // Called on bootstrap... probably doesn't belong here...
    public void installDefaultMvIndicators()
    {
        try
        {
            // Need to insert standard MV indicators for the root -- okay to call getRoot() here, since it's called after upgrade.
            Container rootContainer = ContainerManager.getRoot();
            String rootContainerId = rootContainer.getId();
            TableInfo mvTable = CoreSchema.getInstance().getTableInfoMvIndicators();

            // If we already have any entries, skip this step
            Filter rootFilter = new SimpleFilter("Container", rootContainerId);
            ResultSet rs = Table.select(mvTable, Collections.singleton("MvIndicator"), rootFilter, null);
            try
            {
                if (rs.next())
                    return;
            }
            finally
            {
                rs.close();
            }

            for(Map.Entry<String,String> qcEntry : MvUtil.getDefaultMvIndicators().entrySet())
            {
                Map<String,Object> params = new HashMap<String,Object>();
                params.put("Container", rootContainerId);
                params.put("MvIndicator", qcEntry.getKey());
                params.put("Label", qcEntry.getValue());

                Table.insert(null, mvTable, params);
            }
        }
        catch (SQLException se)
        {
            UnexpectedException.rethrow(se);
        }
    }


    // invoked by prop-9.30-9.31.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void setPasswordStrengthAndExpiration(ModuleContext context)
    {
        // If upgrading an existing installation, make sure the settings don't change.  New installations will require
        // strong passwords.
        if (!context.isNewInstall())
        {
            LoginController.Config config = new LoginController.Config();
            config.setStrength(PasswordRule.Weak.toString());
            config.setExpiration(PasswordExpiration.Never.toString());
            DbLoginManager.saveProperties(config);
        }
    }

    // invoked by prop-10.20-10.21.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void migrateEmailTemplates(ModuleContext context)
    {
        // Change the replacement delimeter character and change to a different PropertyManager node
        if (!context.isNewInstall())
        {
            EmailTemplateService.get().upgradeTo102();
        }
    }

    // invoked by core-11.20-11.30.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void handleUnknownModules(ModuleContext context)
    {
        ModuleLoader.getInstance().handleUnkownModules();
    }
}
