/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
import org.labkey.api.util.FolderDisplayMode;
import org.labkey.api.view.ThemeFont;
import org.labkey.api.view.WebTheme;

/**
 * Stores configuration to control basic rendering of the overall page template. May be associated with the full install
 * or scoped to a specific project.
 *
 * User: adam
 * Date: Aug 1, 2008
 */
public class LookAndFeelProperties extends LookAndFeelFolderProperties
{
    static final String LOOK_AND_FEEL_SET_NAME = "LookAndFeel";

    protected static final String SYSTEM_DESCRIPTION_PROP = "systemDescription";
    protected static final String SYSTEM_SHORT_NAME_PROP = "systemShortName";
    protected static final String THEME_NAME_PROP = "themeName";
    protected static final String FOLDER_DISPLAY_MODE = "folderDisplayMode";
    protected static final String HELP_MENU_ENABLED_PROP = "helpMenuEnabled";
    protected static final String NAVIGATION_BAR_WIDTH = "navigationBarWidth";
    protected static final String LOGO_HREF_PROP = "logoHref";
    protected static final String THEME_FONT_PROP = "themeFont";

    protected static final String COMPANY_NAME_PROP = "companyName";
    protected static final String SYSTEM_EMAIL_ADDRESS_PROP = "systemEmailAddress";
    protected static final String SUPPORT_EMAIL = "supportEmail";
    protected static final String REPORT_A_PROBLEM_PATH_PROP = "reportAProblemPath";

    protected static final String DATE_PARSING_MODE = "dateParsingMode";

    private final Container _settingsContainer;

    public static LookAndFeelProperties getInstance(Container c)
    {
        return new LookAndFeelProperties(c);
    }

    public static WriteableLookAndFeelProperties getWriteableInstance(Container c)
    {
        if (c.isProject() || c.isRoot())
            return new WriteableLookAndFeelProperties(getSettingsContainer(c));
        else
            throw new IllegalStateException("Valid only with root or project");
    }

    public static WriteableFolderLookAndFeelProperties getWriteableFolderInstance(Container c)
    {
        if (c.isRoot())
            throw new IllegalStateException("Not valid with root");
        else
            return new WriteableFolderLookAndFeelProperties(c);
    }

    protected LookAndFeelProperties(Container c)
    {
        super(c);
        _settingsContainer = getSettingsContainer(c);
    }

    @Override
    protected String lookupStringValue(String name, @Nullable String defaultValue)
    {
        return lookupStringValue(_settingsContainer, name, defaultValue);
    }

    // TODO: Shouldn't this be static?
    public boolean hasProperties()
    {
        return !getProperties(_c).isEmpty();
    }

    public String getDescription()
    {
        return lookupStringValue(SYSTEM_DESCRIPTION_PROP, "");
    }

    public String getShortName()
    {
        return lookupStringValue(SYSTEM_SHORT_NAME_PROP, "LabKey Server");
    }

    public String getThemeName()
    {
        return lookupStringValue(THEME_NAME_PROP, WebTheme.DEFAULT.toString());
    }

    public String getThemeFont()
    {
        return lookupStringValue(THEME_FONT_PROP, ThemeFont.DEFAULT_THEME_FONT.getFriendlyName());
    }

    public FolderDisplayMode getFolderDisplayMode()
    {
        return FolderDisplayMode.fromString(lookupStringValue(FOLDER_DISPLAY_MODE, FolderDisplayMode.ALWAYS.toString()));
    }

    public boolean isHelpMenuEnabled()
    {
        return lookupBooleanValue(HELP_MENU_ENABLED_PROP, true);
    }

    public String getNavigationBarWidth()
    {
        return lookupStringValue(NAVIGATION_BAR_WIDTH, "146"); // TODO: Remove this property? There's no way to set it...
    }

    public String getLogoHref()
    {
        return lookupStringValue(LOGO_HREF_PROP, AppProps.getInstance().getHomePageUrl());
    }

    public String getCompanyName()
    {
        return lookupStringValue(COMPANY_NAME_PROP, "Demo Installation");
    }

    public String getSystemEmailAddress()
    {
        return lookupStringValue(SYSTEM_EMAIL_ADDRESS_PROP, "cpas@fhcrc.org");
    }

    public String getUnsubstitutedReportAProblemPath()
    {
        return lookupStringValue(REPORT_A_PROBLEM_PATH_PROP, "${contextPath}/project" + Container.DEFAULT_SUPPORT_PROJECT_PATH + "/begin.view");
    }

    public String getSupportEmail()
    {
        return lookupStringValue(SUPPORT_EMAIL, null);
    }

    public String getReportAProblemPath()
    {
        String path = getUnsubstitutedReportAProblemPath();

        if ("/dev/issues".equals(path))
        {
            path = "${contextPath}/issues/dev/issues/insert.view";
            WriteableLookAndFeelProperties writeable = getWriteableInstance(_c);
            writeable.setReportAProblemPath(path);
            writeable.save();
        }

        return path.replace("${contextPath}", AppProps.getInstance().getContextPath());
    }

    public boolean isMenuUIEnabled()
    {
        return true;
    }

    public boolean isShowMenuBar()
    {
        return isMenuUIEnabled() || getFolderDisplayMode().isShowInMenu();    
    }

    public DateParsingMode getDateParsingMode()
    {
        return DateParsingMode.fromString(lookupStringValue(DATE_PARSING_MODE, DateParsingMode.US.toString()));
    }

    public static Container getSettingsContainer(Container c)
    {
        if (null == c)
            return null;
        if (c.isRoot())
            return c;
        return c.getProject();
    }
}