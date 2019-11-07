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
package org.labkey.core;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.PlatformDeveloperRole;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.NetworkDriveProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.core.reports.ExternalScriptEngineDefinitionImpl;
import org.labkey.core.reports.ScriptEngineManagerImpl;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.core.reports.ScriptEngineManagerImpl.SCRIPT_ENGINE_MAP;

/**
 * User: adam
 * Date: Nov 21, 2008
 * Time: 9:57:49 PM
 */
public class CoreUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = Logger.getLogger(CoreUpgradeCode.class);

    // We don't call ContainerManager.getRoot() during upgrade code since the container table may not yet match
    // ContainerManager's assumptions. For example, older installations don't have a description column until
    // the 10.1 scripts run (see #9927).
    @SuppressWarnings("UnusedDeclaration")
    private String getRootId()
    {
        return new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT EntityId FROM core.Containers WHERE Parent IS NULL").getObject(String.class);
    }

    // Not currently invoked, but available for future scripts
    @SuppressWarnings({"UnusedDeclaration"})
    public void handleUnknownModules(ModuleContext context)
    {
        ModuleLoader.getInstance().handleUnkownModules();
    }

    /**
     * Invoked from 18.10-18.11 to migrate mapped drive settings to an encrypted property store.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void encryptMappedDrivePassword(final ModuleContext context)
    {
        if (!context.isNewInstall() && SystemUtils.IS_OS_WINDOWS)
        {
            WritableNetworkProps props = new WritableNetworkProps();

            String driveLetter = props.getStringValue(WritableNetworkProps.NETWORK_DRIVE_LETTER);
            String drivePath = props.getStringValue(WritableNetworkProps.NETWORK_DRIVE_PATH);
            String user = props.getStringValue(WritableNetworkProps.NETWORK_DRIVE_USER);
            String password = props.getStringValue(WritableNetworkProps.NETWORK_DRIVE_PASSWORD);

            if (StringUtils.isNotBlank(driveLetter) || StringUtils.isNotBlank(drivePath) || StringUtils.isNotBlank(user) || StringUtils.isNotBlank(password))
            {
                // we won't blow up on upgrade if the encryption key isn't specified but we will drop any
                // existing mapped drive settings and force them to re-add them
                if (Encryption.isMasterEncryptionPassPhraseSpecified())
                {
                    NetworkDriveProps.setNetworkDriveLetter(driveLetter);
                    NetworkDriveProps.setNetworkDrivePath(drivePath);
                    NetworkDriveProps.setNetworkDriveUser(user);
                    NetworkDriveProps.setNetworkDrivePassword(password);
                }
                else
                {
                    LOG.warn("Master encryption key not specified, unable to migrate saved network drive settings");
                }
                // clear out the legacy settings
                props.clearNetworkSettings();
                props.save(context.getUpgradeUser());
            }
        }
    }

    /**
     * Helper class to access legacy network settings so we can remove the old API methods immediately
     */
    private static class WritableNetworkProps extends WriteableAppProps
    {
        static final String NETWORK_DRIVE_LETTER = "networkDriveLetter";
        static final String NETWORK_DRIVE_PATH = "networkDrivePath";
        static final String NETWORK_DRIVE_USER = "networkDriveUser";
        static final String NETWORK_DRIVE_PASSWORD = "networkDrivePassword";

        public WritableNetworkProps()
        {
            super(ContainerManager.getRoot());
        }

        public void clearNetworkSettings()
        {
            remove(NETWORK_DRIVE_LETTER);
            remove(NETWORK_DRIVE_PATH);
            remove(NETWORK_DRIVE_USER);
            remove(NETWORK_DRIVE_PASSWORD);
        }

        public String getStringValue(String key)
        {
            return lookupStringValue(key, "");
        }
    }

    /**
     * Invoked from 18.21-18.22
     */
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void updateDevelopersGroup(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            // Add PlatformDeveloperRole to Developer group in "/"
            addDevRoleAssignment(ContainerManager.getRoot());
        }
    }

    private void addDevRoleAssignment(Container container)
    {
        MutableSecurityPolicy policy = new MutableSecurityPolicy(SecurityPolicyManager.getPolicy(container));
        policy.addRoleAssignment(SecurityManager.getGroup(Group.groupDevelopers), PlatformDeveloperRole.class);
        SecurityPolicyManager.savePolicy(policy, false);
    }

    /**
     * Invoked from 18.22-18.23 to migrate script engine configurations from the property story to a new table
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void migrateEngineConfigurations(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            LabkeyScriptEngineManager svc = ServiceRegistry.get().getService(LabkeyScriptEngineManager.class);
            if (svc instanceof ScriptEngineManagerImpl)
            {
                try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
                {
                    for (ExternalScriptEngineDefinition def : ((ScriptEngineManagerImpl)svc).getLegacyEngineDefinitions())
                    {
                        // re-save to the new table
                        svc.saveDefinition(context.getUpgradeUser(), def);
                    }
                    // delete all of the old external script engine configurations
                    PropertyManager.PropertyMap engines = PropertyManager.getProperties(SCRIPT_ENGINE_MAP);
                    for (String engine : engines.values())
                    {
                        // for each engine delete the configuration values
                        PropertyManager.PropertyMap props = PropertyManager.getProperties(engine);
                        props.delete();
                    }
                    if (engines.size() > 0)
                        engines.delete();
                    transaction.commit();
                }
            }
        }
    }


    /**
     * Invoked from 18.26-18.27 to explicitly assign PlatformDeveloperRole to the site.Developer group
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void migrateDeveloperRole(final ModuleContext context)
    {
        try
        {
            Container root = ContainerManager.getRoot();
            MutableSecurityPolicy policy = new MutableSecurityPolicy(root, ContainerManager.getRoot().getPolicy());
            Group devs = SecurityManager.getGroup(Group.groupDevelopers);
            if (null != devs)
                policy.addRoleAssignment(devs, PlatformDeveloperRole.class);
            SecurityPolicyManager.savePolicy(policy, false);
        }
        catch (ContainerManager.RootContainerException x)
        {
            /* pass */
        }
    }

    public void purgeDeveloperRole()
    {
        // delete "DeveloperRole" assignments that have been left scattered around
        new SqlExecutor(CoreSchema.getInstance().getSchema())
                .execute("DELETE FROM core.roleassignments WHERE role = 'org.labkey.api.security.roles.DeveloperRole'");
    }

    /**
     * Invoked from 18.33-18.34 to update script engine configurations so choose a site default when more than one is present (sandboxed vs non sandboxed)
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void migrateSiteDefaultEngines(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            LabkeyScriptEngineManager svc = ServiceRegistry.get().getService(LabkeyScriptEngineManager.class);
            if (svc != null)
            {
                List<ExternalScriptEngineDefinition> rDefs = svc.getEngineDefinitions(ExternalScriptEngineDefinition.Type.R);
                if (rDefs == null || rDefs.size() <= 1)
                    return;
                List<ExternalScriptEngineDefinition> siteDefaultRDefs = rDefs.stream().filter(ExternalScriptEngineDefinition::isDefault).collect(Collectors.toList());
                if (siteDefaultRDefs.size() <= 1)
                    return;

                // prior to 18.34, up to 2 site defaults are allowed: one for sandboxed and one for non sandboxed. Going forward, a single site default will be allowed.
                ExternalScriptEngineDefinition sandboxedSiteDefaultRDef = siteDefaultRDefs.stream().filter(ExternalScriptEngineDefinition::isSandboxed).findFirst().orElse(null);
                if (sandboxedSiteDefaultRDef instanceof ExternalScriptEngineDefinitionImpl)
                {
                    ExternalScriptEngineDefinitionImpl newDef = (ExternalScriptEngineDefinitionImpl) sandboxedSiteDefaultRDef;
                    newDef.setDefault(false);
                    newDef.updateConfiguration(); // update config json
                    svc.saveDefinition(context.getUpgradeUser(), newDef);
                }
            }
        }
    }

    /**
     * Invoked from 19.10-19.11 to encrypt RServe configuration passwords
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void encryptRServeConfigPassword(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            LabkeyScriptEngineManager svc = ServiceRegistry.get().getService(LabkeyScriptEngineManager.class);
            if (svc != null)
            {
                try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
                {
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("type"), ExternalScriptEngineDefinition.Type.R);
                    new TableSelector(CoreSchema.getInstance().getTableInfoReportEngines(),
                            PageFlowUtil.set("rowid", "configuration"),
                            filter, null).forEachMap(map -> {

                                Integer rowId = (Integer)map.get("rowid");
                                String config = (String)map.get("configuration");
                                encryptPassword(rowId, config);
                    });
                    transaction.commit();
                }
            }
        }
    }

    private void encryptPassword(Integer rowId, String configuration)
    {
        if (rowId != null && configuration != null)
        {
            ExternalScriptEngineDefinitionImpl def = new ExternalScriptEngineDefinitionImpl();
            try
            {
                // parse the JSON string but don't attempt to decrypt the password
                def.setConfiguration(configuration, false);
                if (def.isRemote() && def.getPassword() != null)
                {
                    if (!Encryption.isMasterEncryptionPassPhraseSpecified())
                    {
                        // if we can't encrypt the password, we will just blank out that field and force the user to set it manually through the UI
                        // once the encryption key is configured
                        LOG.warn("Master encryption key not specified, unable to migrate saved remote R engine password");
                        def.setPassword(null);
                    }
                    def.setChangePassword(true);
                    def.updateConfiguration();

                    User user = UserManager.getUser(def.getModifiedBy());
                    if (user == null || !user.isActive())
                        user = User.getSearchUser();

                    Table.update(user, CoreSchema.getInstance().getTableInfoReportEngines(), PageFlowUtil.map("configuration", def.getConfiguration()), rowId);
                }
            }
            catch (IOException e)
            {
                LOG.error("unable to encrypt saved remote R engine password for configuration: " + def.getName(), e);
            }
        }
    }

    /**
     * Invoked from 19.20-19.30 to move existing authentication property configurations to core.AuthenticationConfigurations table
     */
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void migrateAuthenticationConfigurations(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            migrateAuthenticationConfigurations(User.getSearchUser());
        }
    }

    public void migrateAuthenticationConfigurations(User user)
    {
        Set<String> active = AuthenticationManager.getActiveProviderNamesFromProperties();
        AuthenticationManager.getAllPrimaryProviders().forEach(provider->{
            try
            {
                provider.migrateOldConfiguration(active.contains(provider.getName()), user);
            }
            catch (Throwable t)
            {
                ExceptionUtil.logExceptionToMothership(null, t);
            }
        });
    }
}