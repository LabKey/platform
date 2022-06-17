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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.FolderDisplayMode;
import org.labkey.api.view.ActionURL;

import java.util.Collection;

import static org.labkey.api.settings.LookAndFeelProperties.APPLICATION_MENU_DISPLAY_MODE;
import static org.labkey.api.settings.LookAndFeelProperties.COMPANY_NAME_PROP;
import static org.labkey.api.settings.LookAndFeelProperties.CUSTOM_LOGIN_PROP;
import static org.labkey.api.settings.LookAndFeelProperties.CUSTOM_WELCOME_PROP;
import static org.labkey.api.settings.LookAndFeelProperties.DATE_PARSING_MODE;
import static org.labkey.api.settings.LookAndFeelProperties.DISCUSSION_ENABLED_PROP;
import static org.labkey.api.settings.LookAndFeelProperties.FOLDER_DISPLAY_MODE;
import static org.labkey.api.settings.LookAndFeelProperties.HELP_MENU_ENABLED_PROP;
import static org.labkey.api.settings.LookAndFeelProperties.LOGO_HREF_PROP;
import static org.labkey.api.settings.LookAndFeelProperties.REPORT_A_PROBLEM_PATH_PROP;
import static org.labkey.api.settings.LookAndFeelProperties.SUPPORT_EMAIL;
import static org.labkey.api.settings.LookAndFeelProperties.SYSTEM_DESCRIPTION_PROP;
import static org.labkey.api.settings.LookAndFeelProperties.SYSTEM_EMAIL_ADDRESS_PROP;
import static org.labkey.api.settings.LookAndFeelProperties.SYSTEM_SHORT_NAME_PROP;
import static org.labkey.api.settings.LookAndFeelProperties.THEME_NAME_PROP;

/**
 * User: adam
 * Date: Aug 1, 2008
 * Time: 9:35:40 PM
 */

// Handles all the properties that can be set at the project or site level
public class WriteableLookAndFeelProperties extends WriteableFolderLookAndFeelProperties
{
    private final boolean isRoot;

    WriteableLookAndFeelProperties(Container c)
    {
        super(c);
        isRoot = c.isRoot();
    }

    @Override
    public void clear(boolean hasAdminOpsPerm)
    {
        String systemEmailAddress = getProperties().get(SYSTEM_EMAIL_ADDRESS_PROP);
        String customLogin = getProperties().get(CUSTOM_LOGIN_PROP);

        getProperties().clear();

        // retain the admin ops related properties if the user doesn't have permissions to reset them
        if (isRoot || !hasAdminOpsPerm)
            getProperties().put(SYSTEM_EMAIL_ADDRESS_PROP, systemEmailAddress);
        if (!hasAdminOpsPerm)
            getProperties().put(CUSTOM_LOGIN_PROP, customLogin);
    }

    public void setFolderDisplayMode(FolderDisplayMode folderDisplayMode)
    {
        storeStringValue(FOLDER_DISPLAY_MODE, folderDisplayMode.toString());
    }

    public void setApplicationMenuDisplayMode(FolderDisplayMode displayMode)
    {
        storeStringValue(APPLICATION_MENU_DISPLAY_MODE, displayMode.toString());
    }

    public void setDateParsingMode(DateParsingMode dateParsingMode)
    {
        storeStringValue(DATE_PARSING_MODE, dateParsingMode.toString());
    }

    public void setHelpMenuEnabled(boolean enabled)
    {
        storeBooleanValue(HELP_MENU_ENABLED_PROP, enabled);
    }

    public void setDiscussionEnabled(boolean enabled)
    {
        storeBooleanValue(DISCUSSION_ENABLED_PROP, enabled);
    }

    public void setSupportEmail(@Nullable String email)
    {
        storeStringValue(SUPPORT_EMAIL, email);
    }

    public void setThemeName(String themeName)
    {
        storeStringValue(THEME_NAME_PROP, themeName);
    }

    public void clearThemeName()
    {
        remove(THEME_NAME_PROP);
    }

    public void setLogoHref(String logoHref)
    {
        storeStringValue(LOGO_HREF_PROP, logoHref);
    }

    public void setSystemDescription(String systemDescription)
    {
        storeStringValue(SYSTEM_DESCRIPTION_PROP, systemDescription);
    }

    public void setSystemShortName(String systemShortName)
    {
        storeStringValue(SYSTEM_SHORT_NAME_PROP, systemShortName);
    }

    public void setCompanyName(String companyName)
    {
        storeStringValue(COMPANY_NAME_PROP, companyName);
    }

    public void setSystemEmailAddress(ValidEmail systemEmail)
    {
        storeStringValue(SYSTEM_EMAIL_ADDRESS_PROP, systemEmail.getEmailAddress());
    }

    public void setReportAProblemPath(String reportAProblemPath)
    {
        storeStringValue(REPORT_A_PROBLEM_PATH_PROP, reportAProblemPath);
    }

    public void setCustomLogin(String customLogin)
    {
        storeStringValue(CUSTOM_LOGIN_PROP, customLogin);
    }

    public void setCustomWelcome(String customWelcome)
    {
        storeStringValue(CUSTOM_WELCOME_PROP, customWelcome);
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

    public static void populateLookAndFeelWithStartupProps()
    {
        // populate look and feel settings with values read from startup properties as appropriate for prop modifier and isBootstrap flag
        // expects startup properties formatted like: LookAndFeelSettings.systemDescription;startup=Test Server Description
        // for a list of recognized look and feel setting properties refer to: LookAndFeelProperties.java
        Collection<StartupPropertyEntry> startupProps = ModuleLoader.getInstance().getConfigProperties(StartupPropertyEntry.SCOPE_LOOK_AND_FEEL_SETTINGS);
        WriteableLookAndFeelProperties writeable = LookAndFeelProperties.getWriteableInstance(ContainerManager.getRoot());
        startupProps
                .forEach(prop -> writeable.storeStringValue(prop.getName(), prop.getValue()));
        writeable.save();
    }

    @Override
    public void save()
    {
        super.save();
        LookAndFeelProperties.clearCaches();
    }
}
