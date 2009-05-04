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
import org.labkey.api.module.Module;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.*;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.ResultSetUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.*;

/**
 * User: adam
 * Date: Nov 21, 2008
 * Time: 9:57:49 PM
 */
@SuppressWarnings({"UnusedDeclaration"})
public class CoreUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(CoreUpgradeCode.class);

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
            Map<String,String> mvMap = MvUtil.getDefaultMvIndicators();
            TableInfo mvTable = CoreSchema.getInstance().getTableInfoMvIndicators();
            for(Map.Entry<String,String> qcEntry : mvMap.entrySet())
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

    //Not yet invoked--will call this once we have UI to edit new security policies
    public void migrateAcls(ModuleContext context)
    {
        int numAcls = 0;
        _log.info("Migrating existing ACLs to RoleAssignments...");

        TableInfo tableAcls = CoreSchema.getInstance().getTableInfoACLs();
        ResultSet rs = null;
        try
        {
            rs = Table.select(tableAcls, Table.ALL_COLUMNS, null, null);
            while(rs.next())
            {
                String containerId = rs.getString("Container");
                String objectId = rs.getString("ObjectId");
                ACL acl = new ACL(rs.getBytes("ACL"));
                int[] groups = acl.getAllGroups();
                int[] perms = acl.getAllPermissions();

                Container container = ContainerManager.getForId(containerId);
                if(null == container)
                {
                    _log.warn("Could not resolve ACL container id " + containerId + "! Skipping ACL.");
                    continue;
                }

                ACLConversionResource resource = new ACLConversionResource(container, objectId);
                ResourceSecurityPolicy policy = new ResourceSecurityPolicy(resource);
                for(int idx = 0; idx < groups.length; ++idx)
                {
                    Group group = SecurityManager.getGroup(groups[idx]);
                    if(null == group)
                    {
                        _log.warn("Could not resolve group id " + groups[idx] + " in ACL for object id " + objectId + "! Skipping group permissions.");
                        continue;
                    }
                    Role role = getRoleForPerms(perms[idx]);
                    if(null == role)
                    {
                        _log.warn("Unable to determine a role for permissions bits " + perms[idx] + " in ACL for object id " + objectId + "! Skipping role assignment.");
                        continue;
                    }

                    policy.addRoleAssignment(group, role);
                }

                SecurityManager.savePolicy(policy);

                ++numAcls;
            }
        }
        catch(SQLException e)
        {
            _log.error("SQLException while converting ACL to SecurityPolicy!", e);
            throw new RuntimeSQLException(e);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

        _log.info("Finished migrating " + numAcls + " ACLs to RoleAssignments.");
    }

    private Role getRoleForPerms(int perms)
    {
        if(SecurityManager.PermissionSet.ADMIN.getPermissions() == perms)
            return new SiteAdminRole();
        else if(SecurityManager.PermissionSet.EDITOR.getPermissions() == perms)
            return new EditorRole();
        else if(SecurityManager.PermissionSet.AUTHOR.getPermissions() == perms)
            return new AuthorRole();
        else if(SecurityManager.PermissionSet.READER.getPermissions() == perms)
            return new ReaderRole();
        else if(SecurityManager.PermissionSet.RESTRICTED_READER.getPermissions() == perms)
            return null; //NOTE: read-own is now implemented via the Owner contextual role
        else if(SecurityManager.PermissionSet.SUBMITTER.getPermissions() == perms)
            return new SubmitterRole();
        else if(SecurityManager.PermissionSet.NO_PERMISSIONS.getPermissions() == perms)
            return new NoPermissionsRole();
        else
            return null;
    }

    //used strictly for conversion of ACLs
    private static class ACLConversionResource implements SecurableResource
    {
        private Container _container;
        private String _resourceId;

        public ACLConversionResource(Container container, String objectId)
        {
            _resourceId = objectId;
            _container = container;
        }

        @NotNull
        public String getResourceId()
        {
            return _resourceId;
        }

        @NotNull
        public String getName()
        {
            return "";
        }

        @NotNull
        public String getDescription()
        {
            return "";
        }

        @NotNull
        public Set<Class<? extends Permission>> getRelevantPermissions()
        {
            return Collections.emptySet();
        }

        public Module getSourceModule()
        {
            return null;
        }

        public SecurableResource getParent()
        {
            return null;
        }

        @NotNull
        public Container getContainer()
        {
            return _container;
        }
    }
}
