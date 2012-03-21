/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.FolderDisplayMode;

import java.sql.SQLException;

/**
 * User: adam
 * Date: Aug 1, 2008
 * Time: 9:35:40 PM
 */
public class WriteableLookAndFeelProperties extends LookAndFeelProperties
{
    WriteableLookAndFeelProperties(Container c) throws SQLException
    {
        super(c);
        makeWriteable(c);
    }

    // Make public
    public void save() throws SQLException
    {
        super.save();
    }

    public void clear()
    {
        getProperties().clear();
    }

    public void setFolderDisplayMode(FolderDisplayMode folderDisplayMode)
    {
        storeStringValue(FOLDER_DISPLAY_MODE, folderDisplayMode.toString());
    }

    public void setHelpMenuEnabled(boolean enabled)
    {
        storeBooleanValue(HELP_MENU_ENABLED_PROP, enabled);
    }

    public void setSupportEmail(@Nullable String email)
    {
        storeStringValue(SUPPORT_EMAIL, email);
    }

    public void setNavigationBarWidth(String width)
    {
        storeStringValue(NAVIGATION_BAR_WIDTH, width);
    }

    public void setThemeName(String themeName)
    {
        storeStringValue(THEME_NAME_PROP, themeName);
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

    public void setMenuUIEnabled(boolean enabled)
    {
        storeBooleanValue(MENU_UI_ENABLED_PROP, enabled);
    }
}
