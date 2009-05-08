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
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.roles.*;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.ResultSetUtil;
import org.apache.log4j.Logger;

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

    //invoked by core-9.13-9.14.sql
    public void migrateAcls(ModuleContext context)
    {
        int numAcls = 0;
        _log.info("Migrating existing ACLs to RoleAssignments...");

        ResultSet rs = null;
        String insertPolicySql = "insert into core.Policies(ResourceId,ResourceClass,Container,Modified) values(?,?,?,?)";
        String insertAssignmentSql = "insert into core.RoleAssignments(ResourceId,UserId,Role) values (?,?,?)";
        Role siteAdminRole = new SiteAdminRole();
        Date now = new Date();

        try
        {
            DbSchema coreSchema = CoreSchema.getInstance().getSchema();

            //delete any previously-migrated data in case we failed part way through
            Table.execute(coreSchema, "delete from core.RoleAssignments", null);
            Table.execute(coreSchema, "delete from core.Policies", null);

            //get all the user ids so that we can verify if a particular group id
            //still exists and ignore if not--returned array is sorted so we can
            //use Arrays.binarySearch()
            int[] userIds = getAllUserIds();

            rs = Table.executeQuery(coreSchema, "select * from core.ACLs", null);
            while(rs.next())
            {
                //NOTE: container id may be null in some old databases!
                //not sure exactly what this means, but we need to handle that case
                String containerId = rs.getString("Container");
                String objectId = rs.getString("ObjectId");
                String resourceClass = objectId.equals(containerId) ? Container.class.getName() : null;

                ACL acl = new ACL(rs.getBytes("ACL"));
                int[] groups = acl.getAllGroups();
                int[] perms = acl.getAllPermissions();
                boolean empty = true; //will be set to true if ACL contains a valid group id

                //get the container in order to determine if it's a project or folder
                Container container = null == containerId ? null : ContainerManager.getForId(containerId);

                //insert into policies
                Table.execute(coreSchema, insertPolicySql,
                        new Object[]{objectId, resourceClass, containerId, now});

                //iterate over all groups in the ACL, mapping the store perms to a role
                //and saving that role assignment to the new table
                for(int idx = 0; idx < groups.length; ++idx)
                {
                    //note that the old ACLs might contain groups that no longer exist
                    //the old binary format made it difficult to scrub when a group was deleted
                    //so check the group against the full list in memory and skip if not found
                    if(Arrays.binarySearch(userIds, groups[idx]) > 0)
                    {
                        Role role = getRoleForPerms(perms[idx], container, objectId);
                        if(null == role)
                        {
                            _log.warn("Unable to determine a role for permissions bits 0x" + Integer.toHexString(perms[idx])
                                    + " in ACL for object id " + objectId + " and group id " + groups[idx]
                                    + "! Skipping role assignment.");
                            continue;
                        }

                        Table.execute(coreSchema, insertAssignmentSql,
                                new Object[]{objectId, groups[idx], role.getUniqueName()});
                        empty = false;
                    }
                }

                if(empty)
                {
                    //if the acl is empty, that means no group still in existence has permissions to this object
                    //but parts of our code generally check explicitly for user.isAdmin() to
                    //allow admins access to the object. The real equivallent is to assign
                    //the administrators group to the site admin role for this object
                    //and that's all.
                    Table.execute(CoreSchema.getInstance().getSchema(), insertAssignmentSql,
                            new Object[]{objectId, Group.groupAdministrators, siteAdminRole.getUniqueName() });
                }

                ++numAcls;
                if((numAcls % 100) == 0)
                    _log.info("Migrated " + numAcls + " ACLs...");
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

    private int[] getAllUserIds() throws SQLException
    {
        Integer[] principalIds = Table.executeArray(CoreSchema.getInstance().getSchema(), "select UserId from core.Principals order by UserId", null, Integer.class);
        int[] ret = new int[principalIds.length];
        for(int idx = 0; idx < principalIds.length; ++idx)
        {
            ret[idx] = principalIds[idx].intValue();
        }
        return ret;
    }

    private Role getRoleForPerms(int perms, Container container, String objectId)
    {
        if(SecurityManager.PermissionSet.ADMIN.getPermissions() == perms)
        {
            if(null == container || container.isRoot())
                return new SiteAdminRole();
            else if(container.isProject())
                return new ProjectAdminRole();
            else
                return new FolderAdminRole();
        }
        else if(SecurityManager.PermissionSet.EDITOR.getPermissions() == perms)
            return new EditorRole();
        else if(SecurityManager.PermissionSet.AUTHOR.getPermissions() == perms || (ACL.PERM_READ | ACL.PERM_INSERT) == perms)
            return new AuthorRole();
        else if(SecurityManager.PermissionSet.READER.getPermissions() == perms)
            return new ReaderRole();
        else if(SecurityManager.PermissionSet.RESTRICTED_READER.getPermissions() == perms)
        {
            //HACK: this was never really implemented on the container itself,
            //but study used this to distinguish between "read-some" and "read-all"
            //In either case, we should migrate to a normal reader role.
            return new RestrictedReaderRole();
        }
        else if(SecurityManager.PermissionSet.SUBMITTER.getPermissions() == perms)
            return new SubmitterRole();
        else if(SecurityManager.PermissionSet.NO_PERMISSIONS.getPermissions() == perms)
            return new NoPermissionsRole();
        else if((ACL.PERM_UPDATE | ACL.PERM_READ) == perms) //special case for editable datasets
            return new EditorRole();
        else
            return null;
    }
    
}
