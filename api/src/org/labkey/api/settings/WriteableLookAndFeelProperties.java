/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import static org.labkey.api.settings.LookAndFeelProperties.*;

/**
 * User: adam
 * Date: Aug 1, 2008
 * Time: 9:35:40 PM
 */

// Handles all the properties that can be set at the project or site level
public class WriteableLookAndFeelProperties extends WriteableFolderLookAndFeelProperties
{
    boolean isRoot;
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

    // TODO: Remove this setting? There's no way to set it...
    public void setNavigationBarWidth(String width)
    {
        storeStringValue(NAVIGATION_BAR_WIDTH, width);
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

    public void setThemeFont(String themeFont)
    {
        storeStringValue(THEME_FONT_PROP, themeFont);
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
        {
            return true;
        }

        ActionURL actionURL = new ActionURL(url);
        if (StringUtils.isEmpty(actionURL.getAction()) || StringUtils.isEmpty(actionURL.getController()))
        {
            return false;
        }

        return true;
    }

    public static void populateLookAndFeelWithStartupProps()
    {
        final boolean isBootstrap = ModuleLoader.getInstance().isNewInstall();

        // populate look and feel settings with values read from startup properties as appropriate for prop modifier and isBootstrap flag
        // expects startup properties formatted like: LookAndFeelSettings.systemDescription;startup=Test Server Description
        // for a list of recognized look and feel setting properties refer to: LookAndFeelProperties.java
        Collection<ConfigProperty> startupProps = ModuleLoader.getInstance().getConfigProperties(ConfigProperty.SCOPE_LOOK_AND_FEEL_SETTINGS);
        WriteableLookAndFeelProperties writeable = LookAndFeelProperties.getWriteableInstance(ContainerManager.getRoot());
        startupProps
                .forEach(prop -> {
                    if (prop.getModifier() == ConfigProperty.modifier.startup || (isBootstrap && prop.getModifier() == ConfigProperty.modifier.bootstrap))
                    {
                        writeable.storeStringValue(prop.getName(), prop.getValue());
                    }
                });
        writeable.save();
    }
}
