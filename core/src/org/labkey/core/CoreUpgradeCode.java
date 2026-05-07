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

import jakarta.servlet.ServletContext;
import org.apache.logging.log4j.Logger;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.TestUpgradeCodeCounter;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.Directive;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.core.security.AllowedExternalResourceHosts;
import org.labkey.core.security.AllowedExternalResourceHosts.AllowedHost;

import java.util.Map;
import java.util.List;

import static org.labkey.api.security.AuthenticationManager.LOGIN_ATTEMPT_ENABLED_KEY;
import static org.labkey.api.security.AuthenticationManager.LOGIN_ATTEMPT_LIMIT_KEY;
import static org.labkey.api.security.AuthenticationManager.LOGIN_ATTEMPT_PERIOD_KEY;
import static org.labkey.api.security.AuthenticationManager.LOGIN_ATTEMPT_RESET_TIME_KEY;
import static org.labkey.api.settings.AbstractSettingsGroup.SITE_CONFIG_USER;

public class CoreUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = LogHelper.getLogger(CoreUpgradeCode.class, "Custom core upgrade steps");

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
        ModuleLoader.getInstance().handleUnknownModules();
    }

    /** Java upgrade method used for testing purposes (see PG and MSSQL InlineProcedureTestCase) */
    @SuppressWarnings({"UnusedDeclaration"})
    public void testUpgradeCode(ModuleContext moduleContext)
    {
        TestUpgradeCodeCounter.incrementCounter();
    }

    /**
     * Called from core-25.000-25.001.sql
     */
    @SuppressWarnings("unused")
    public static void migrateAllowedExternalConnectionHosts(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        // TODO: Remove getExternalSourceHosts() method when this upgrade code is deleted
        List<String> hosts = AppProps.getInstance().getExternalSourceHosts();
        List<AllowedHost> allowedHosts = hosts.stream()
            .map(host -> new AllowedHost(Directive.Connection, host))
            .toList();

        // No need to synchronize since upgrade is single-threaded
        AllowedExternalResourceHosts.saveAllowedHosts(allowedHosts, context.getUpgradeUser());
    }

    /**
     * Called from core-26.003-26.004.sql
     */
    @SuppressWarnings("unused")
    // @DeferredUpgrade ensures that the upgrade code method gets queued in the database so the listener eventually
    // gets registered and run, even in the case of an upgrade failure or server shutdown.
    @DeferredUpgrade
    public static void populateAttachmentParentTypeColumn(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        // This code needs to run after all @DeferredUpgrades are complete. In particular, it needs to wait for
        // ExperimentUpgradeCode.addRowIdToProvisionedDataClassTables() to restructure data class provisioned tables.
        ContextListener.addStartupListener(new StartupListener()
        {
            @Override
            public String getName()
            {
                return "Populate Attachment Parent Types";
            }

            @Override
            public void moduleStartupComplete(ServletContext servletContext)
            {
                for (AttachmentParentType type : AttachmentService.get().getAttachmentParentTypes())
                {
                    LOG.info("Populating attachment parent type for {}", type.getUniqueName());

                    SQLFragment updateSql = new SQLFragment("UPDATE ")
                        .append(CoreSchema.getInstance().getTableInfoDocuments())
                        .append(" SET ParentType = ?")
                        .add(type.getUniqueName())
                        .append(" WHERE ");
                    // TODO: This is the only caller of addWhereSql(), which can be removed when this upgrade code is deleted.
                    type.addWhereSql(updateSql, "Parent", "DocumentName");

                    new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(updateSql);
                }
            }
        });
    }

    /**
     * This is not invoked from any script yet. We want to make sure our orphaned attachment detection is well tested
     * and reviewed before blanket deleting them. TODO: Do not just call this from a script! We likely need to work
     * this code into a StartupListener like we do above. Or integrate them into one. Or something.
     */
    @SuppressWarnings("unused")
    @DeferredUpgrade // Need to execute this after AttachmentTypes are registered
    public static void deleteOrphanedAttachments(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        AttachmentService svc = AttachmentService.get();

        if (svc != null)
        {
            svc.deleteOrphanedAttachments();
        }
    }

    /**
     * Called from core-26.004-26.005.sql. Migrates login attempt settings that were previously stored in a
     * compliance module property group to the core authentication property category.
     */
    @SuppressWarnings("unused")
    @DeferredUpgrade // Make sure property schema is up-to-date before migrating settings
    public static void migrateLoginAttemptSettings(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        String complianceCategory = "complianceSettingPropLoginAttempt";
        String keyPrefix = complianceCategory + "/";
        Map<String, String> complianceProps = PropertyManager.getProperties(SITE_CONFIG_USER, ContainerManager.getRoot(), complianceCategory);

        // Important: The old compliance property set used a prefix with the property names. The new property set will not.
        String enabledVal = complianceProps.get(keyPrefix + "attemptEnabled");
        String limitVal   = complianceProps.get(keyPrefix + "attemptLimit");
        String periodVal  = complianceProps.get(keyPrefix + "attemptPeriod");
        String resetVal   = complianceProps.get(keyPrefix + "resetTime");

        if (enabledVal == null && limitVal == null && periodVal == null && resetVal == null)
        {
            // Nothing to migrate
            LOG.info("No existing unsuccessful login attempt settings were found");
        }
        else
        {
            LOG.info("Migrating existing unsuccessful login attempt settings: {}", complianceProps);
            User user = context.getUpgradeUser();

            try (Transaction t = DbScope.getLabKeyScope().beginTransaction())
            {
                // TODO: change saveAuthSetting() visibility back to private after deleting this upgrade code
                if (enabledVal != null)
                    AuthenticationManager.saveAuthSetting(user, LOGIN_ATTEMPT_ENABLED_KEY, Boolean.valueOf(enabledVal));
                if (limitVal != null)
                    AuthenticationManager.saveAuthSetting(user, LOGIN_ATTEMPT_LIMIT_KEY, limitVal, "set to " + limitVal);
                if (periodVal != null)
                    AuthenticationManager.saveAuthSetting(user, LOGIN_ATTEMPT_PERIOD_KEY, periodVal, "set to " + periodVal);
                if (resetVal != null)
                    AuthenticationManager.saveAuthSetting(user, LOGIN_ATTEMPT_RESET_TIME_KEY, resetVal, "set to " + resetVal);
                t.commit();
            }
        }
    }
}
