/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
package org.labkey.api.settings;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.settings.LookAndFeelProperties.Properties;
import org.labkey.api.util.FolderDisplayMode;
import org.labkey.api.view.ActionURL;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.labkey.api.settings.LookAndFeelProperties.Properties.*;

/**
 * User: adam
 * Date: Aug 1, 2008
 * Time: 9:35:40 PM
 */

// Handles all the properties that can be set at the project or site level
public class WriteableLookAndFeelProperties extends WriteableFolderLookAndFeelProperties
{
    public static final String SCOPE_LOOK_AND_FEEL_SETTINGS = "LookAndFeelSettings";

    private final boolean isRoot;

    WriteableLookAndFeelProperties(Container c)
    {
        super(c);
        isRoot = c.isRoot();
    }

    @Override
    public void clear(boolean hasAdminOpsPerm)
    {
        String emailAddress = getProperties().get(systemEmailAddress.name());
        String login = getProperties().get(customLogin.name());

        getProperties().clear();

        // retain the admin ops related properties if the user doesn't have permissions to reset them
        if (isRoot || !hasAdminOpsPerm)
            getProperties().put(systemEmailAddress.name(), emailAddress);
        if (!hasAdminOpsPerm)
            getProperties().put(customLogin.name(), login);
    }

    public void setFolderDisplayMode(FolderDisplayMode mode)
    {
        storeStringValue(folderDisplayMode, mode.toString());
    }

    public void setApplicationMenuDisplayMode(FolderDisplayMode displayMode)
    {
        storeStringValue(applicationMenuDisplayMode, displayMode.toString());
    }

    public void setDateParsingMode(DateParsingMode mode)
    {
        storeStringValue(dateParsingMode, mode.toString());
    }

    public void setHelpMenuEnabled(boolean enabled)
    {
        storeBooleanValue(helpMenuEnabled, enabled);
    }

    public void setDiscussionEnabled(boolean enabled)
    {
        storeBooleanValue(discussionEnabled, enabled);
    }

    public void setSupportEmail(@Nullable String email)
    {
        storeStringValue(supportEmail, email);
    }

    public void setThemeName(String name)
    {
        storeStringValue(themeName, name);
    }

    public void clearThemeName()
    {
        remove(themeName);
    }

    public void setLogoHref(String href)
    {
        storeStringValue(logoHref, href);
    }

    public void setSystemDescription(String description)
    {
        storeStringValue(systemDescription, description);
    }

    public void setSystemShortName(String shortName)
    {
        storeStringValue(systemShortName, shortName);
    }

    public void setCompanyName(String name)
    {
        storeStringValue(companyName, name);
    }

    public void setSystemEmailAddress(ValidEmail systemEmail)
    {
        storeStringValue(systemEmailAddress, systemEmail.getEmailAddress());
    }

    public void setReportAProblemPath(String path)
    {
        storeStringValue(reportAProblemPath, path);
    }

    public void setCustomLogin(String login)
    {
        storeStringValue(customLogin, login);
    }

    public void setCustomWelcome(String welcome)
    {
        storeStringValue(customWelcome, welcome);
    }

    public boolean isValidUrl(String url)
    {
        if (StringUtils.isEmpty(url))
            return true;

        try
        {
            ActionURL actionURL = new ActionURL(url);
            return !StringUtils.isEmpty(actionURL.getAction()) && !StringUtils.isEmpty(actionURL.getController());
        }
        catch (IllegalArgumentException x)
        {
            return false;
        }
    }

    private static class LookAndFeelStartupPropertyHandler extends StandardStartupPropertyHandler<Properties>
    {
        public LookAndFeelStartupPropertyHandler()
        {
            super(SCOPE_LOOK_AND_FEEL_SETTINGS, Properties.class);
        }

        @Override
        public void handle(Map<Properties, StartupPropertyEntry> map)
        {
            if (!map.isEmpty())
            {
                WriteableLookAndFeelProperties writeable = LookAndFeelProperties.getWriteableInstance(ContainerManager.getRoot());
                map.forEach((prop, entry) -> prop.save(writeable, entry.getValue()));
                writeable.save();
            }
        }
    }

    public static void populateLookAndFeelWithStartupProps()
    {
        // populate look and feel settings with values read from startup properties
        // expects startup properties formatted like: LookAndFeelSettings.systemDescription;startup=Test Server Description
        // for a list of recognized look and feel setting properties refer to the LookAndFeelProperties.Properties enum
        ModuleLoader.getInstance().handleStartupProperties(new LookAndFeelStartupPropertyHandler());
    }

    @Override
    public void save()
    {
        super.save();
        LookAndFeelProperties.clearCaches();
    }

    public static class TestCase extends Assert
    {
        private final static String TEST_SYSTEM_DESCRIPTION = "Test System Description";

        /**
         * Test that the Look And Feel settings can be configured from startup properties
         */
        @Test
        public void testStartupPropertiesForLookAndFeel()
        {
            // save the original Look And Feel server setting so that we can restore it when this test is done
            LookAndFeelProperties lookAndFeelProps = LookAndFeelProperties.getInstance(ContainerManager.getRoot());
            String originalSystemDescription = lookAndFeelProps.getDescription();

            // Handle the test properties to change the Look And Feel settings on the server
            ModuleLoader.getInstance().handleStartupProperties(new LookAndFeelStartupPropertyHandler(){
                @Override
                public @NotNull Collection<StartupPropertyEntry> getStartupPropertyEntries()
                {
                    return List.of(new StartupPropertyEntry("systemDescription", TEST_SYSTEM_DESCRIPTION, "startup", SCOPE_LOOK_AND_FEEL_SETTINGS));
                }

                @Override
                public boolean performChecks()
                {
                    return false;
                }
            });

            // now check that the expected change occurred to the Look And Feel settings on the server
            String newSystemDescription = lookAndFeelProps.getDescription();
            assertEquals("The expected change in Look And Feel settings was not found", TEST_SYSTEM_DESCRIPTION, newSystemDescription);

            // restore the Look And Feel server settings to how they were originally
            WriteableLookAndFeelProperties writeablelookAndFeelProps = LookAndFeelProperties.getWriteableInstance(ContainerManager.getRoot());
            writeablelookAndFeelProps.setSystemDescription(originalSystemDescription);
            writeablelookAndFeelProps.save();
        }
    }
}
