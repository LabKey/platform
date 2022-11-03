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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.settings.AbstractWriteableSettingsGroup;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ExperimentalFeatureService;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.GUID;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User: adam
 * Date: Nov 21, 2008
 * Time: 9:57:49 PM
 */
public class CoreUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = LogManager.getLogger(CoreUpgradeCode.class);

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

    /**
     * Invoked at 21.004 to move the Default Domain (for user log in) from being stored in AppProps to PropertyManager
     */
    @SuppressWarnings("unused")
    @DeferredUpgrade
    public void migrateDefaultDomainSetting(ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            // Taken from AppPropsImpl
            final String DEFAULT_DOMAIN_PROP = "defaultDomain";
            final String SITE_CONFIG_NAME = "SiteConfig";

            String defaultDomain = (new AbstractWriteableSettingsGroup(){
                @Override
                protected String getGroupName()
                {
                    return SITE_CONFIG_NAME;
                }

                @Override
                protected String getType()
                {
                    return "site settings";
                }

                private String getDefaultDomain()
                {
                    return lookupStringValue(DEFAULT_DOMAIN_PROP, "");
                }
            }).getDefaultDomain();

            AuthenticationManager.setDefaultDomain(context.getUpgradeUser(), defaultDomain);
        }
    }

    /**
     * Invoked at 21.005 to set the projects that are excluded from project locking by default
     */
    @SuppressWarnings("unused")
    @DeferredUpgrade
    public void setDefaultExcludedProjects(ModuleContext context)
    {
        List<GUID> guids = Stream.of("home", "Shared")
            .map(ContainerManager::getForPath)
            .filter(Objects::nonNull)
            .map(Container::getEntityId)
            .collect(Collectors.toList());
        ContainerManager.setExcludedProjects(guids, () -> {});
    }

    /**
     * Invoked at 21.008 to save placeholder logos into CAS and SAML configurations
     */
    @SuppressWarnings("unused")
    @DeferredUpgrade
    public void savePlaceholderLogos(ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            AuthenticationManager.getActiveConfigurations(SSOAuthenticationConfiguration.class)
                .forEach(configuration -> configuration.savePlaceholderLogos(context.getUpgradeUser()));

            // Clear the image cache so the web server sends the new logos
            AttachmentCache.clearAuthLogoCache();
            // Bump the look & feel revision to force browsers to retrieve new logos
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
        }
    }

    /**
     * Invoked at 22.005 to turn on no-question-mark mode for all servers. Can be deleted once the experimental feature
     * is removed (22.12 or thereabouts).
     */
    @SuppressWarnings("unused")
    @DeferredUpgrade // Required because root container is created after all scripts run
    public void turnOnNoQuestionMarkMode(ModuleContext context)
    {
        ExperimentalFeatureService.get().setFeatureEnabled(AppProps.EXPERIMENTAL_NO_QUESTION_MARK_URL, true, null);
    }
}