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

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.Constants;
import org.labkey.api.admin.AdminBean;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.FolderDisplayMode;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SafeToRenderEnum;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Arrays;

import static org.labkey.api.settings.LookAndFeelProperties.Properties.*;

/**
 * Stores configuration to control basic rendering of the overall page template. May be associated with the full install
 * or scoped to a specific project.
 *
 * User: adam
 * Date: Aug 1, 2008
 */
public class LookAndFeelProperties extends LookAndFeelFolderProperties
{
    private static final Cache<Container, String> SHORT_NAME_CACHE = CacheManager.getBlockingCache(Constants.getMaxProjects(), CacheManager.YEAR, "Short name", null);

    // Defined in the same order they appear on the Site-level Look and Feel Settings page
    public enum Properties implements StartupProperty, SafeToRenderEnum
    {
        systemDescription("System description (used in emails)"),
        systemShortName("Header short name (appears in every page header and in emails)"),
        themeName("Server color schema"),
        folderDisplayMode("Show project and folder navigation. Valid values: " + Arrays.toString(FolderDisplayMode.values())),
        applicationMenuDisplayMode("Show application selection menu. Valid values: " + Arrays.toString(FolderDisplayMode.values())),
        helpMenuEnabled("Show LabKey Help menu item"),
        discussionEnabled("Enable object-level discussions"),
        logoHref("Logo link (specifies page to which header logo links)"),
        reportAProblemPath("Support link (specifies page where users can request support)"),
        supportEmail("Support email (shown to users if they don't have permission to see a page, or are having trouble logging in)"),

        systemEmailAddress("System email address (from address for system notification emails)"),
        companyName("Organization name (appears in notification emails sent by system)"),

        defaultDateFormat("Default display format for dates"){
            @Override
            public void save(WriteableLookAndFeelProperties writeable, String value)
            {
                writeable.setDefaultDateFormat(value); // Override to validate and use legacy property name
            }
        },
        defaultDateTimeFormat("Default display format for date-times"){
            @Override
            public void save(WriteableLookAndFeelProperties writeable, String value)
            {
                writeable.setDefaultDateTimeFormat(value); // Override to validate and use legacy property name
            }
        },
        defaultNumberFormat("Default display format for numbers"){
            @Override
            public void save(WriteableLookAndFeelProperties writeable, String value)
            {
                writeable.setDefaultNumberFormat(value); // Override to validate and use legacy property name
            }
        },

        dateParsingMode("Date parsing mode. Valid values: " + Arrays.toString(DateParsingMode.values())),
        extraDateParsingPattern("Additional parsing pattern for dates"),
        extraDateTimeParsingPattern("Additional parsing pattern for date-times"),

        restrictedColumnsEnabled("Restrict charting columns by measure and dimension flags"),

        customLogin("Alternative login page"),

        customWelcome("Alternative site welcome page");

        private final String _description;

        Properties(String description)
        {
            _description = description;
        }

        @Override
        public String getDescription()
        {
            return _description;
        }

        public void save(WriteableLookAndFeelProperties writeable, String value)
        {
            writeable.storeStringValue(name(), value);
        }
    }

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

    public static void clearCaches()
    {
        SHORT_NAME_CACHE.clear();
    }

    @Override
    protected String lookupStringValue(String name, @Nullable String defaultValue)
    {
        return lookupStringValue(_settingsContainer, name, defaultValue);
    }

    public String getDescription()
    {
        return lookupStringValue(systemDescription, "");
    }

    public String getUnsubstitutedShortName()
    {
        return lookupStringValue(systemShortName, "LabKey Server");
    }

    public String getShortName()
    {
        return SHORT_NAME_CACHE.get(_settingsContainer, null,
            (key, argument) -> StringExpressionFactory.create(getUnsubstitutedShortName()).eval(AdminBean.getPropertyMap()));
    }

    public String getThemeName()
    {
        return lookupStringValue(themeName, PageFlowUtil.DEFAULT_THEME_NAME);
    }

    public boolean isThemeNameInherited()
    {
        return isPropertyInherited(_settingsContainer, themeName.name());
    }

    public FolderDisplayMode getFolderDisplayMode()
    {
        return FolderDisplayMode.fromString(lookupStringValue(folderDisplayMode, FolderDisplayMode.ALWAYS.toString()));
    }

    public FolderDisplayMode getApplicationMenuDisplayMode()
    {
        return FolderDisplayMode.fromString(lookupStringValue(applicationMenuDisplayMode, FolderDisplayMode.ALWAYS.toString()));
    }

    public boolean isHelpMenuEnabled()
    {
        return lookupBooleanValue(helpMenuEnabled, true);
    }

    public boolean isDiscussionEnabled()
    {
        // Prefer correctly spelled property name, but fall-back to the old, misspelled one
        String enabled = lookupStringValue(discussionEnabled.name(), null);
        return enabled != null ? "TRUE".equalsIgnoreCase(enabled) : lookupBooleanValue("dicussionEnabled", true);
    }

    public String getUnsubstitutedLogoHref()
    {
        return lookupStringValue(logoHref, AppProps.getInstance().getHomePageUrl().replaceAll("^" + AppProps.getInstance().getContextPath(), "\\${contextPath}"));
    }

    public String getLogoHref()
    {
        return substituteContextPath(getUnsubstitutedLogoHref());
    }

    public String getCompanyName()
    {
        return lookupStringValue(companyName, "Demo Installation");
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
        String emailAddress = lookupStringValue(systemEmailAddress, "");
        if (emailAddress.isEmpty())
            LogManager.getLogger(this.getClass()).error(String.format("System Email Address became unset somehow. Visit '%s/admin-projectSettings.view' to fix it",
                    _settingsContainer.getTitle().isEmpty() ? "" : _settingsContainer.getPath()));
        return emailAddress;
    }

    /** Let callers peek if there's an address configured without logging an error */
    public boolean hasSystemEmailAddress()
    {
        return lookupStringValue(systemEmailAddress, null) != null;
    }

    public String getUnsubstitutedReportAProblemPath()
    {
        return lookupStringValue(reportAProblemPath, "${contextPath}" + ContainerManager.DEFAULT_SUPPORT_PROJECT_PATH + "/project-begin.view");
    }

    public String getSupportEmail()
    {
        return lookupStringValue(supportEmail, null);
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
        return lookupStringValue(customLogin, "login-login");
    }

    public String getCustomWelcome()
    {
        return lookupStringValue(customWelcome, null);
    }

    public DateParsingMode getDateParsingMode()
    {
        return DateParsingMode.fromString(lookupStringValue(dateParsingMode, DateParsingMode.US.toString()));
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
