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

import org.apache.log4j.Logger;
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
    protected static final String DISCUSSION_ENABLED_PROP = "dicussionEnabled";
    protected static final String NAVIGATION_BAR_WIDTH = "navigationBarWidth";
    protected static final String LOGO_HREF_PROP = "logoHref";
    protected static final String THEME_FONT_PROP = "themeFont";

    protected static final String COMPANY_NAME_PROP = "companyName";
    protected static final String SYSTEM_EMAIL_ADDRESS_PROP = "systemEmailAddress";
    protected static final String SUPPORT_EMAIL = "supportEmail";
    protected static final String REPORT_A_PROBLEM_PATH_PROP = "reportAProblemPath";

    protected static final String DATE_PARSING_MODE = "dateParsingMode";
    protected static final String CUSTOM_LOGIN_PROP = "customLogin";
    protected static final String CUSTOM_WELCOME_PROP = "customWelcome";

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

    private LookAndFeelProperties(Container c)
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

    public boolean isThemeNameInherited()
    {
        return isPropertyInherited(_settingsContainer, THEME_NAME_PROP);
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

    public boolean isDiscussionEnabled()
    {
        return lookupBooleanValue(DISCUSSION_ENABLED_PROP, true);
    }

    public String getNavigationBarWidth()
    {
        return lookupStringValue(NAVIGATION_BAR_WIDTH, "146"); // TODO: Remove this property? There's no way to set it...
    }

    public String getUnsubstitutedLogoHref()
    {
        return lookupStringValue(LOGO_HREF_PROP, AppProps.getInstance().getHomePageUrl().replaceAll("^" + AppProps.getInstance().getContextPath(), "\\${contextPath}"));
    }

    public String getLogoHref()
    {
        return substituteContextPath(getUnsubstitutedLogoHref());
    }

    public String getCompanyName()
    {
        return lookupStringValue(COMPANY_NAME_PROP, "Demo Installation");
    }

    /**
     * The "from" address to use when sending emails. Do not use for any other purpose and never suggest that LabKey users email
     * this address for any reason. The address might not be a functioning email address; e.g., "do_not_reply@labkey.org".
     *
     * @return Sender email address that should be used in this project
     */
    public String getSystemEmailAddress()
    {
        //initial login will be used as the default value. During setup user will be prompted to change.
        String systemEmailAddress = lookupStringValue(SYSTEM_EMAIL_ADDRESS_PROP, "");
        if (systemEmailAddress.isEmpty())
            Logger.getLogger(this.getClass()).error(String.format("System Email Address became unset somehow. Visit '%s/admin-projectSettings.view' to fix it",
                    _settingsContainer.getTitle().isEmpty() ? "" : _settingsContainer.getPath()));
        return systemEmailAddress;
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

        return substituteContextPath(path);
    }

    private String substituteContextPath(String path)
    {
        return path.replace("${contextPath}", AppProps.getInstance().getContextPath());
    }

    public String getCustomLogin()
    {
        return lookupStringValue(CUSTOM_LOGIN_PROP, "login-login");
    }

    public String getCustomWelcome()
    {
        return lookupStringValue(CUSTOM_WELCOME_PROP, null);
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