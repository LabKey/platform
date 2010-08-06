/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
import org.labkey.api.data.*;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.ACL;
import org.labkey.api.security.Group;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.roles.*;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.core.login.DbLoginManager;
import org.labkey.core.login.LoginController;
import org.labkey.core.login.PasswordRule;

import java.sql.ResultSet;
import java.sql.SQLException;
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


    // Invoked by core-8.20-8.30.sql
    public void migrateLookAndFeelSettings(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        String rootId = getRootId();
        PropertyManager.PropertyMap configProps = PropertyManager.getWritableProperties(-1, getRootId(), "SiteConfig", true);
        PropertyManager.PropertyMap lafProps = PropertyManager.getWritableProperties(-1, getRootId(), "LookAndFeel", true);

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


    private void saveAuthenticationProviders(boolean enableLdap)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(0, getRootId(), "Authentication", true);
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

    //invoked by core-9.10-9.20.sql
    public void migrateAcls(ModuleContext context)
    {
        //8441: skip ACL migration if this is a brand-new install 
        if (context.isNewInstall())
            return;

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
            String rootId = getRootId();

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

                //insert into policies
                Table.execute(coreSchema, insertPolicySql,
                        new Object[]{objectId, resourceClass, containerId, now});

                ContainerType containerType;

                if (null == containerId)
                {
                    containerType = ContainerType.UNKNOWN;
                }
                else
                {
                    // Figure out the container type by reading properties directly from the database
                    String parentId = Table.executeSingleton(CoreSchema.getInstance().getSchema(), "SELECT Parent FROM core.Containers WHERE EntityId = ?", new Object[]{containerId}, String.class);

                    if (null == parentId)
                        containerType = ContainerType.ROOT;
                    else if (rootId.equals(parentId))
                        containerType = ContainerType.PROJECT;
                    else
                        containerType = ContainerType.FOLDER;
                }

                //iterate over all groups in the ACL, mapping the store perms to a role
                //and saving that role assignment to the new table
                for(int idx = 0; idx < groups.length; ++idx)
                {
                    //note that the old ACLs might contain groups that no longer exist
                    //the old binary format made it difficult to scrub when a group was deleted
                    //so check the group against the full list in memory and skip if not found
                    if(Arrays.binarySearch(userIds, groups[idx]) > 0)
                    {
                        Role role = getRoleForPerms(perms[idx], containerType, objectId);
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
                    //allow admins access to the object. The real equivalent is to assign
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

    private enum ContainerType { ROOT, PROJECT, FOLDER, UNKNOWN }

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

    private Role getRoleForPerms(int perms, ContainerType containerType, String objectId)
    {
        if(SecurityManager.PermissionSet.ADMIN.getPermissions() == perms)
        {
            if(ContainerType.UNKNOWN == containerType || ContainerType.ROOT == containerType)
                return new SiteAdminRole();
            else if(ContainerType.PROJECT == containerType)
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


    // invoked by prop-9.30-9.31.sql
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
    public void migrateEmailTemplates(ModuleContext context)
    {
        // Change the replacement delimeter character and change to a different PropertyManager node
        if (!context.isNewInstall())
        {
            EmailTemplateService.get().upgradeTo102();
        }
    }
}
