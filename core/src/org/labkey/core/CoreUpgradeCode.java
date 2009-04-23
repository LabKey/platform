/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.UnexpectedException;

import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;

/**
 * User: adam
 * Date: Nov 21, 2008
 * Time: 9:57:49 PM
 */
@SuppressWarnings({"UnusedDeclaration"})
public class CoreUpgradeCode implements UpgradeCode
{
    // Invoked by core-8.10-8.20.sql
    public void bootstrapDevelopersGroup(ModuleContext moduleContext)
    {
        GroupManager.bootstrapGroup(Group.groupDevelopers, "Developers", GroupManager.PrincipalType.ROLE);
    }

    // Invoked by core-8.10-8.20.sql
    public void migrateLdapSettings(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        try
        {
            Map<String, String> props = AppProps.getInstance().getProperties(ContainerManager.getRoot());
            String domain = props.get("LDAPDomain");

            if (null != domain && domain.trim().length() > 0)
            {
                PropertyManager.PropertyMap map = PropertyManager.getWritableProperties("LDAPAuthentication", true);
                map.put("Servers", props.get("LDAPServers"));
                map.put("Domain", props.get("LDAPDomain"));
                map.put("PrincipalTemplate", props.get("LDAPPrincipalTemplate"));
                map.put("SASL", props.get("LDAPAuthentication"));
                PropertyManager.saveProperties(map);
                saveAuthenticationProviders(true);
            }
            else
            {
                saveAuthenticationProviders(false);
            }
        }
        catch (SQLException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }


    // Invoked by core-8.20-8.30.sql
    public void migrateLookAndFeelSettings(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        PropertyManager.PropertyMap configProps = PropertyManager.getWritableProperties(-1, ContainerManager.getRoot().getId(), "SiteConfig", true);
        PropertyManager.PropertyMap lafProps = PropertyManager.getWritableProperties(-1, ContainerManager.getRoot().getId(), "LookAndFeel", true);

        for (String settingName : new String[] {"systemDescription", "systemShortName", "themeName", "folderDisplayMode",
                "navigationBarWidth", "logoHref", "themeFont", "companyName", "systemEmailAddress", "reportAProblemPath"})
        {
            migrateSetting(configProps, lafProps, settingName);
        }

        PropertyManager.saveProperties(configProps);
        PropertyManager.saveProperties(lafProps);
    }

    public void installDefaultQcValues()
    {
        try
        {
            // Need to insert standard QC values for the root
            Container rootContainer = ContainerManager.getRoot();
            String rootContainerId = rootContainer.getId();
            Map<String,String> qcMap = QcUtil.getDefaultQcValues();
            TableInfo qcValuesTable = CoreSchema.getInstance().getTableInfoQcValues();
            for(Map.Entry<String,String> qcEntry : qcMap.entrySet())
            {
                Map<String,Object> params = new HashMap<String,Object>();
                params.put("Container", rootContainerId);
                params.put("QcValue", qcEntry.getKey());
                params.put("Label", qcEntry.getValue());

                Table.insert(null, qcValuesTable, params);
            }
        }
        catch (SQLException se)
        {
            UnexpectedException.rethrow(se);
        }
    }


    private void saveAuthenticationProviders(boolean enableLdap)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties("Authentication", true);
        String activeAuthProviders = map.get("Authentication");

        if (null == activeAuthProviders)
            activeAuthProviders = "Database";

        if (enableLdap)
        {
            if (!activeAuthProviders.contains("LDAP"))
                activeAuthProviders = activeAuthProviders + ":LDAP";
        }
        else
        {
            activeAuthProviders = activeAuthProviders.replaceFirst("LDAP:", "").replaceFirst(":LDAP", "").replaceFirst("LDAP", "");
        }

        map.put("Authentication", activeAuthProviders);
        PropertyManager.saveProperties(map);
    }


    private void migrateSetting(PropertyManager.PropertyMap configProps, PropertyManager.PropertyMap lafProps, String propertyName)
    {
        lafProps.put(propertyName, configProps.get(propertyName));
        configProps.remove(propertyName);
    }
}
